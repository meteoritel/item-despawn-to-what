"""GUI 探针驱动：连接真实客户端，逐步执行用例步骤，汇总证据并生成报告。

驱动只做三件事：把客户端推到用例需要的前置状态、按步骤驱动真实输入、把结果落成证据。
所有断言都在用例文件里声明，期望值来自语言文件或稳定的状态契约；驱动不替用例决定期望值。

客户端原语在会话内只安装一次：之后每次求值只发表达式本身，往返从「每次重装整份 prelude」
降到一段小脚本。动作之后不再固定 sleep，改为轮询布局指纹判断界面是否稳定；截图前只隔两帧，
保证 takeScreenshot 拿到的是新帧缓冲。每条 eval 记录 RTT 与次数，运行结束汇总成 metrics
写进 gui-summary.json，终端同时打印一行。
"""

import argparse
import json
import math
import os
import sys
import time
from pathlib import Path

LIB_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, LIB_DIR)
import autojs  # noqa: E402
import report  # noqa: E402
import sheet  # noqa: E402
from client import Client, ProbeError, REPO_ROOT, TOOLS_DIR  # noqa: E402
from record import RunRecord, FILES, case_verdict  # noqa: E402
from case_contract import assertion_query, finish_check, json_equal, validate_case, check_local_handlers  # noqa: E402

LANG_DIR = REPO_ROOT / "common/src/main/resources/assets/itemdespawntowhat/lang"


def load_lang():
    """读取两份语言文件：期望文案只从语言文件取，脚本不重复硬编码。"""
    langs = {}
    for name in ("en_us", "zh_cn"):
        with open(os.path.join(LANG_DIR, name + ".json"), encoding="utf-8") as handle:
            langs[name] = json.load(handle)
        dev = Path(REPO_ROOT) / "common/src/dev/resources/assets/idtw_probe/lang" / (name + ".json")
        langs[name].update(json.loads(dev.read_text(encoding="utf-8")))
    return langs


def clamp(value, low, high):
    return max(low, min(high, value))


def key_signature(key):
    """比较客户端声明的稳定签名，具体字段由 prelude 管理。"""
    return tuple(key["signature"]) if isinstance(key, dict) and isinstance(key.get("signature"), list) else None


class Probe:
    """一次运行内复用的游戏进程句柄：原语常驻，每条表达式只发一次小往返。"""

    def __init__(self, run_dir, timeout_ms=90000, settle_timeout_ms=1500):
        self.run_dir = os.path.abspath(run_dir).replace("\\", "/")
        self.timeout_ms = timeout_ms
        self.settle_timeout_ms = settle_timeout_ms
        self.client = Client(self.run_dir, self._eval_raw)
        self.env = {}
        self.eval_count = 0
        self.rtts = []
        self.settle_count = 0
        self.settle_waited_ms = 0
        self.settle_polls = 0
        self.settle_timeouts = 0
        self.shot_count = 0
        self.shot_ms = 0
        self.fps = 0
        self.last_key = None

    # ---- 求值 ----

    def _eval_raw(self, code, timeout_ms=None):
        """发一段原始 Rhino 脚本，记录 RTT 与次数，返回传输层的原生 value。"""
        started = time.perf_counter()
        raw = autojs.eval_js(code, side="client",
                             timeout_ms=timeout_ms or self.timeout_ms)
        self.eval_count += 1
        self.rtts.append((time.perf_counter() - started) * 1000.0)
        return raw

    def evaluate(self, verb, payload=None):
        return self.client.invoke(verb, payload)

    def install(self):
        """握手和安装由协议 adapter 负责，环境与 RTT 仍纳入本次运行。"""
        hello, installed = self.client.install(self.timeout_ms)
        self._absorb_env(hello)
        return installed

    def _absorb_env(self, payload):
        """从安装/状态求值里取出环境快照，省掉一次单独的 eval。"""
        if not isinstance(payload, dict):
            return
        env = payload.get("env")
        if isinstance(env, dict):
            self.env = env
            fps = (env.get("runtime") or {}).get("fps")
            if fps:
                self.fps = fps

    def perform(self, action):
        """动作只发送一次，客户端在同一次求值里返回布局指纹。"""
        value = self.client.invoke("action", action)
        if not isinstance(value, dict) or not isinstance(value.get("k"), dict):
            raise ProbeError("动作求值返回异常: %r" % (value,))
        before = self.last_key
        self._remember_key(value["k"])
        return value.get("r"), before

    def _sample_key(self):
        key = self.client.invoke("sample")
        if isinstance(key, dict):
            self._remember_key(key)
            return key
        return None

    def _remember_key(self, key):
        if not isinstance(key, dict):
            return
        self.last_key = key
        fps = key.get("fps")
        if fps:
            self.fps = fps

    # ---- 自适应 settle ----

    def frame_ms(self):
        fps = self.fps if self.fps and self.fps > 0 else 60
        return 1000.0 / fps

    def poll_interval_ms(self):
        """轮询间隔：半帧起步，限在 15-60ms，避免高频 eval 抢渲染线程。"""
        return clamp(self.frame_ms() / 2.0, 15.0, 60.0)

    def wait_frame(self, frames=2):
        """截图前隔开两帧：takeScreenshot 取的是上一帧已完成的帧缓冲。"""
        delay_ms = max(math.ceil(frames * self.frame_ms()), 16)
        time.sleep(delay_ms / 1000.0)
        return delay_ms

    def settle(self, before_key=None, timeout_ms=None):
        """轮询布局指纹直到稳定。

        动作立即改变了界面时，期望再取到一次相同采样；动作没有立即改变界面时，
        先给两帧异步窗口，窗口内仍无变化就认为该动作没有可见效果（例如把窗口设成当前尺寸）。
        """
        timeout_ms = self.settle_timeout_ms if timeout_ms is None else timeout_ms
        started = time.perf_counter()
        last = self.last_key if self.last_key is not None else self._sample_key()
        before_signature = key_signature(before_key)
        seen_change = before_signature is None or before_signature != key_signature(last)
        interval_ms = self.poll_interval_ms()
        grace_ms = 2.0 * self.frame_ms()
        polls = 0
        timed_out = False
        while True:
            elapsed_ms = (time.perf_counter() - started) * 1000.0
            if elapsed_ms >= timeout_ms:
                timed_out = True
                break
            if seen_change:
                time.sleep(interval_ms / 1000.0)
            else:
                # 还没有看到任何变化：先按两帧粒度观察是否异步生效。
                time.sleep(max(grace_ms - elapsed_ms, 0) / 1000.0)
            current = self._sample_key()
            polls += 1
            current_signature = key_signature(current)
            if not seen_change:
                if current_signature != key_signature(last):
                    seen_change = True
                    last = current
                    continue
                break
            if current_signature == key_signature(last):
                break
            last = current
        waited_ms = int((time.perf_counter() - started) * 1000)
        self.settle_count += 1
        self.settle_waited_ms += waited_ms
        self.settle_polls += polls
        if timed_out:
            self.settle_timeouts += 1
        return {"waited_ms": waited_ms, "polls": polls, "timeout": timed_out,
                "changed": seen_change}

    # ---- metrics ----

    def rtt_stats(self):
        if not self.rtts:
            return {"count": 0, "rtt_avg_ms": 0.0, "rtt_max_ms": 0.0, "rtt_total_ms": 0.0}
        return {
            "count": self.eval_count,
            "rtt_avg_ms": round(sum(self.rtts) / len(self.rtts), 1),
            "rtt_max_ms": round(max(self.rtts), 1),
            "rtt_total_ms": round(sum(self.rtts), 1),
        }


def wait_for_client(deadline_seconds):
    """客户端起来前 MCP 必然连不上，连不上是等待的一部分，不当失败。"""
    started = time.perf_counter()
    deadline = time.time() + deadline_seconds
    last_error = None
    while time.time() < deadline:
        try:
            autojs.health()
            return (time.perf_counter() - started) * 1000.0
        except Exception as error:
            last_error = error
            time.sleep(0.5)
    raise ProbeError("客户端 MCP 在 %ss 内没有就绪: %s" % (deadline_seconds, last_error))


def reach(probe, target, world=None):
    """每条用例通过公开输入路径重建自己的起点。"""
    deadline = time.monotonic() + target.get("timeout_seconds", 180)
    target = dict(target, world=target.get("world", world))
    recent = []
    while time.monotonic() < deadline:
        value = probe.evaluate("reach", target)
        recent.append(value)
        if isinstance(value, dict) and value.get("done"):
            return
        time.sleep(0.2)
    raise ProbeError("导航超时 target=%r recent=%r state=%r" % (target, recent[-5:], probe.evaluate("environment")["state"]))


class CaseState:
    """跨步骤只持有 JSON 值，缺少前置采样即失败。"""
    def __init__(self):
        self.values = {}

    def capture(self, name, value):
        if value is None:
            raise ProbeError("capture 没有观测值: " + name)
        self.values[name] = value

    def get(self, name):
        if name not in self.values:
            raise ProbeError("缺少 capture: " + name)
        return self.values[name]

    def clear(self):
        self.values.clear()


def read_query(probe, query):
    return probe.evaluate("read", query)


def perform_action(probe, action):
    action = dict(action)
    if action.get("unique_text"):
        action["text"] = "probe_" + str(time.time_ns())
        action.pop("unique_text")
    value, before = probe.perform(action)
    if isinstance(value, dict) and value.get("handled") is False and action["kind"] != "release":
        raise ProbeError("输入未处理: %r => %r" % (action, value))
    return before


def create_rule(probe):
    perform_action(probe, {"kind": "click", "selector": {"kind": "CONTROL", "lang": "gui.itemdespawntowhat.edit.button.new"}})
    probe.settle()
    perform_action(probe, {"kind": "click", "selector": {"kind": "TEXT_INPUT"}})
    perform_action(probe, {"kind": "clear"})
    perform_action(probe, {"kind": "type", "unique_text": True})
    perform_action(probe, {"kind": "click", "selector": {"kind": "CONTROL", "lang": "gui.itemdespawntowhat.edit.button.confirm"}})
    probe.settle()


def run_case(probe, case, langs, settle_timeout_ms):
    started = time.perf_counter()
    state = CaseState()
    checks, artifacts = [], []
    evals_started = probe.eval_count
    stats = {"settle_ms": 0, "shot_ms": 0, "shots": 0}
    def register_artifact(artifact):
        artifact.update(id=f"{case['name']}/{len(artifacts) + 1}", case=case["name"], index=len(artifacts) + 1)
        artifacts.append(artifact)

    error = None
    pointer_held = False
    settle_limit = (case.get("settle") or {}).get("timeout_ms", settle_timeout_ms)
    try:
        window = case.get("window")
        if window:
            perform_action(probe, dict(window, kind="resize"))
            probe.settle()
        reach(probe, case["start"], getattr(probe, "world", None))
        if case["start"].get("new_rule"):
            create_rule(probe)
        for index, step in enumerate(case["steps"], 1):
            if "action" in step:
                action = step["action"]
                if action["kind"] == "press": pointer_held = True
                before = perform_action(probe, action)
                if action["kind"] == "release": pointer_held = False
                if step.get("settle", True): stats["settle_ms"] += probe.settle(before, settle_limit)["waited_ms"]
            elif "capture" in step:
                state.capture(step["capture"], read_query(probe, step["query"]))
            elif "assert" in step or "wait_for" in step:
                check = step.get("assert", step.get("wait_for"))
                deadline = time.monotonic() + (step.get("timeout_ms", 2500) / 1000 if "wait_for" in step else 0)
                while True:
                    try:
                        value = read_query(probe, assertion_query(check))
                        result = {"ok": True, "value": value}
                        if "expected_lang" in check: result["language_value"] = probe.evaluate("language", check["expected_lang"])
                    except Exception as failure:
                        result = {"ok": False, "error": str(failure)}
                    outcome = finish_check(check, result, langs, state)
                    if outcome["pass"] or time.monotonic() >= deadline: break
                    time.sleep(0.05)
                checks.append(outcome)
            elif "shot" in step:
                probe.wait_frame()
                value = probe.evaluate("screenshot", step["shot"])
                path = value["path"]
                if not os.path.isfile(path) or os.path.getsize(path) == 0: raise ProbeError("截图未落盘: " + path)
                artifact = dict(value, kind="screenshot", name=step["shot"], step=index)
                register_artifact(artifact)
                stats["shots"] += 1; stats["shot_ms"] += value["shot_ms"]
                probe.shot_count += 1; probe.shot_ms += value["shot_ms"]
            elif "dump" in step:
                value = read_query(probe, step["query"])
                path = os.path.join(probe.run_dir, step["dump"])
                with open(path, "w", encoding="utf-8") as handle: json.dump(value, handle, ensure_ascii=False, indent=2)
                register_artifact({"kind":"json", "name":step["dump"], "path":path, "step":index})
            else: raise ProbeError("未知步骤: %r" % step)
    except Exception as failure:
        error = "%s: %s" % (type(failure).__name__, failure)
        checks.append({"name":"用例执行完整", "kind":"execution", "expected":"completed", "actual":error, "pass":False})
    finally:
        state.clear()
        if pointer_held:
            try:
                perform_action(probe, {"kind": "release"})
            except Exception as failure:
                cleanup_error = "%s: %s" % (type(failure).__name__, failure)
                error = error or cleanup_error
                checks.append({"name":"释放用例拖动", "kind":"cleanup", "expected":"released", "actual":cleanup_error, "pass":False})
    result = {"name": case["name"], "verdict": case_verdict(checks, error), "detail":case.get("description", ""),
              "checks": checks, "artifacts":artifacts, "duration_ms":int((time.perf_counter()-started)*1000)}
    metrics = {"name":case["name"], "duration_ms":result["duration_ms"], "evals":probe.eval_count-evals_started,
               "checks":len(checks), "shots":stats["shots"], "settle_ms":stats["settle_ms"], "shot_ms":stats["shot_ms"]}
    return result, metrics


def load_cases(cases_dir, only):
    cases = []
    for entry in sorted(os.listdir(cases_dir)):
        if not entry.endswith(".json"):
            continue
        with open(os.path.join(cases_dir, entry), encoding="utf-8") as handle:
            case = json.load(handle)
        case.setdefault("name", os.path.splitext(entry)[0])
        if only and case["name"] not in only:
            continue
        cases.append(case)
    missing = set(only) - {case["name"] for case in cases}
    if missing: raise ProbeError("未知 --only: " + ", ".join(sorted(missing)))
    check_local_handlers(Path(LIB_DIR) / "prelude.js")
    langs = load_lang()
    for case in cases:
        validate_case(case, langs)
    return cases


def build_metrics(probe, phases, case_metrics, total_ms):
    """汇总全链路 metrics：phases / evals / settle / 每用例耗时与 eval 数。"""
    if probe is None:
        evals = {"count": 0, "rtt_avg_ms": 0.0, "rtt_max_ms": 0.0, "rtt_total_ms": 0.0}
        settle = {"count": 0, "waited_ms": 0, "polls": 0, "timeouts": 0}
        shots = shot_ms = 0
    else:
        evals = probe.rtt_stats()
        settle = {"count": probe.settle_count, "waited_ms": probe.settle_waited_ms,
                  "polls": probe.settle_polls, "timeouts": probe.settle_timeouts}
        shots = probe.shot_count
        shot_ms = probe.shot_ms
    return {
        "total_ms": total_ms,
        "phases": {
            "install_ms": phases.get("install_ms", 0),
            "wait_client_ms": phases.get("wait_client_ms", 0),
            "setup_ms": phases.get("setup_ms", 0),
            "cases_ms": phases.get("cases_ms", 0),
            "sheet_ms": phases.get("sheet_ms", 0),
            "report_ms": phases.get("report_ms", 0),
        },
        "evals": evals,
        "settle": settle,
        "shots": {"count": shots, "shot_ms": shot_ms},
        "cases": case_metrics,
    }


def build_env(probe):
    """环境块：窗口度量与会话状态，沿用原报告的键名，另附运行时帧率。"""
    snapshot = probe.env if probe is not None and isinstance(probe.env, dict) else {}
    env = {}
    window = snapshot.get("window")
    if isinstance(window, dict):
        env.update(window)
    state = snapshot.get("state")
    if isinstance(state, dict):
        env.update({key: value for key, value in state.items() if key not in env})
    fps = (snapshot.get("runtime") or {}).get("fps")
    if fps:
        env["fps"] = fps
    return env


def main(argv):
    parser = argparse.ArgumentParser(description="GUI 探针驱动")
    parser.add_argument("--run-dir", required=True, help="本次运行的证据目录")
    parser.add_argument("--cases", default=os.path.join(TOOLS_DIR, "gui-probes", "cases"))
    parser.add_argument("--only", action="append", default=[], help="只跑指定用例名，可重复")
    parser.add_argument("--world", default=None, help="要进入的存档名；省略则选列表第一个")
    parser.add_argument("--wait-client", type=int, default=240, help="等待 MCP 就绪的秒数")
    parser.add_argument("--timeout-ms", type=int, default=90000)
    parser.add_argument("--settle-timeout-ms", type=int, default=1500,
                        help="自适应 settle 的等待上限（毫秒）")
    parser.add_argument("--validate", action="store_true", help="离线校验用例与目标")
    args = parser.parse_args(argv)
    cases = load_cases(args.cases, args.only)
    if not cases: raise ProbeError("没有匹配用例")
    if args.validate:
        print("validated %d cases" % len(cases))
        return 0

    run_dir = os.path.abspath(args.run_dir)
    os.makedirs(run_dir, exist_ok=True)
    langs = load_lang()
    started = time.perf_counter()
    metadata = {
        "title": "GUI 探针报告",
        "started_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "run_dir": run_dir,
        "harness": "AutoJS + tools/gui-probes",
    }
    env = {}
    record = RunRecord(run_dir, metadata)
    setup_error = None
    phases = {}
    probe = None
    try:
        phases["wait_client_ms"] = int(wait_for_client(args.wait_client))
        probe = Probe(run_dir, args.timeout_ms, args.settle_timeout_ms)
        install_started = time.perf_counter()
        probe.install()
        phases["install_ms"] = int((time.perf_counter() - install_started) * 1000)
        setup_started = time.perf_counter()
        probe.world = args.world
        phases["setup_ms"] = int((time.perf_counter() - setup_started) * 1000)
        env = build_env(probe)
        record.set_environment(env)
        cases_started = time.perf_counter()
        for case in cases:
            result, metrics = run_case(probe, case, langs, args.settle_timeout_ms)
            record.add_case(result, metrics)
            print("[%s] %s %dms evals=%d"
                  % (result["verdict"], result["name"], metrics["duration_ms"],
                     metrics["evals"]))
            for check in result["checks"]:
                if not check["pass"]:
                    print("    x %s: 期望 %r 实际 %r"
                          % (check["name"], check["expected"], check["actual"]))
        phases["cases_ms"] = int((time.perf_counter() - cases_started) * 1000)
    except Exception as failure:
        setup_error = "%s: %s" % (type(failure).__name__, failure)
        record.fail_setup(setup_error)
        print("[error] 前置阶段失败: " + setup_error)

    record.complete_run(build_metrics(probe, phases, record.case_metrics(), int((time.perf_counter()-started)*1000)))
    record.derive_outputs(sheet.build, report.render_report)
    summary = record.summary()
    metrics = summary["metrics"]
    print("[shots] %d 张 %dms" % (summary["shots"]["count"], metrics["phases"]["sheet_ms"]))
    print("verdict=%s 用例 %d/%d 细项 %d/%d"
          % (summary["verdict"], summary["passed"], summary["total_cases"],
             summary["passed_checks"], summary["total_checks"]))
    print("[metrics] total=%.1fs evals=%d rtt_avg=%.1fms settle=%dms shots=%d"
          % (metrics["total_ms"] / 1000.0, metrics["evals"]["count"],
             metrics["evals"]["rtt_avg_ms"], metrics["settle"]["waited_ms"],
             metrics["shots"]["count"]))
    for artifact_error in summary["artifact_errors"]:
        print("[artifact-error] " + json.dumps(artifact_error, ensure_ascii=False))
    print("报告: " + os.path.join(run_dir, "gui-report.html"))
    if setup_error:
        return 1
    return 0 if summary["verdict"] == "pass" else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
