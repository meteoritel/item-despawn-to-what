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
import traceback

LIB_DIR = os.path.dirname(os.path.abspath(__file__))
TOOLS_DIR = os.path.dirname(os.path.dirname(LIB_DIR))
REPO_ROOT = os.path.dirname(TOOLS_DIR)
LANG_DIR = os.path.join(REPO_ROOT, "common", "src", "main", "resources", "assets",
                        "itemdespawntowhat", "lang")
PRELUDE_PATH = os.path.join(LIB_DIR, "prelude.js")
# prelude 里 __gp.__version 必须与这里一致：版本不同说明会话里装的是旧原语，需要重装。
PRELUDE_VERSION = 8


sys.path.insert(0, LIB_DIR)
import autojs  # noqa: E402
import report  # noqa: E402
import sheet  # noqa: E402


class ProbeError(RuntimeError):
    pass


def load_lang():
    """读取两份语言文件：期望文案只从语言文件取，脚本不重复硬编码。"""
    langs = {}
    for name in ("en_us", "zh_cn"):
        with open(os.path.join(LANG_DIR, name + ".json"), encoding="utf-8") as handle:
            langs[name] = json.load(handle)
    return langs


def unwrap(raw):
    """折叠 MCP 返回：可能是字符串、也可能带 value 的字典，两种都还原成 eval 结果。"""
    if isinstance(raw, dict):
        for key in ("value", "result", "text"):
            if key in raw:
                raw = raw[key]
                break
    # eval 外壳本身会 JSON.stringify 一次，纯字符串结果因此多一层引号：逐层剥到非字符串为止。
    # 标量（true/false/null/数字）同样是合法 JSON，布尔与计数检查依赖这一步还原成 Python 值。
    while isinstance(raw, str):
        text = raw.strip()
        if not text:
            return raw
        try:
            raw = json.loads(text)
        except json.JSONDecodeError:
            return raw
    return raw


def clamp(value, low, high):
    return max(low, min(high, value))


def key_signature(key):
    """布局指纹的比较口径：屏幕、逻辑尺寸、节点哈希，加上缩放与帧缓冲尺寸。

    ADR-0022 的三档缩放下逻辑尺寸都是 320x240，只比逻辑尺寸无法区分档位切换，
    所以缩放与帧缓冲必须一起参与比较。
    """
    if not isinstance(key, dict):
        return None
    return (key.get("s"), key.get("w"), key.get("h"), key.get("k"),
            key.get("scale"), key.get("fbw"), key.get("fbh"))


class Probe:
    """一次运行内复用的游戏进程句柄：原语常驻，每条表达式只发一次小往返。"""

    KEY_SUFFIX = ("var t = __gp.runtime();"
                  "k.fps = t.fps; k.scale = t.scale; k.fbw = t.fbWidth; k.fbh = t.fbHeight;")

    def __init__(self, run_dir, timeout_ms=90000, settle_timeout_ms=1500):
        self.run_dir = os.path.abspath(run_dir).replace("\\", "/")
        self.timeout_ms = timeout_ms
        self.settle_timeout_ms = settle_timeout_ms
        with open(PRELUDE_PATH, encoding="utf-8") as handle:
            self.prelude = handle.read()
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
        """发一段原始 Rhino 脚本，记录 RTT 与次数，返回折叠后的结果。"""
        started = time.perf_counter()
        raw = autojs.eval_js(code, side="client",
                             timeout_ms=timeout_ms or self.timeout_ms)
        self.eval_count += 1
        self.rtts.append((time.perf_counter() - started) * 1000.0)
        return unwrap(raw)

    def evaluate(self, expression):
        # 求值结果统一用 JSON.stringify 收口：跨进程只能拿文本，对象要显式序列化才不会退化成 toString。
        body = "JSON.stringify((function(){return (" + expression + ");})())"
        return self._eval_raw("__gp.begin();\n" + body)

    def install(self):
        """确认会话里装的是当前版本的 prelude；未安装、版本不符或 RunDir 变了就重装。"""
        status_expr = (
            "JSON.stringify((function(){"
            "var out = {status: (typeof __gp) + '|'"
            " + (typeof __gpRunDir === 'string' ? __gpRunDir : '') + '|'"
            " + (typeof __gp === 'object' && __gp && __gp.__version ? String(__gp.__version) : '0')};"
            "if (typeof __gp === 'object' && __gp) {"
            " try { out.env = " + env_snapshot_js() + "; } catch (e) {}"
            "}"
            "return out;})())")
        probe_info = self._eval_raw(status_expr)
        self._absorb_env(probe_info)
        expected = "object|%s|%d" % (self.run_dir, PRELUDE_VERSION)
        if isinstance(probe_info, dict) and probe_info.get("status") == expected:
            return False
        code = ("var __gpRunDir = " + json.dumps(self.run_dir) + ";\n"
                + self.prelude
                + "\nJSON.stringify((function(){return {installed: true, version: __gp.__version,"
                " env: " + env_snapshot_js() + "};})());")
        installed = self._eval_raw(code, timeout_ms=max(self.timeout_ms, 30000))
        self._absorb_env(installed)
        if not isinstance(installed, dict) or installed.get("version") != PRELUDE_VERSION:
            raise ProbeError("prelude 安装失败: %r" % (installed,))
        return True

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

    def perform(self, expression):
        """求值一个动作表达式，同一 eval 里取回动作后的布局指纹，作为 settle 的起点。"""
        code = ("__gp.begin(); JSON.stringify((function(){"
                "var r = (" + expression + ");"
                "var k = __gp.probeKey(); " + self.KEY_SUFFIX +
                "return {r: r, k: k};})())")
        value = self._eval_raw(code)
        if not isinstance(value, dict) or not isinstance(value.get("k"), dict):
            raise ProbeError("动作求值返回异常: %r" % (value,))
        before = self.last_key
        self._remember_key(value["k"])
        return value.get("r"), before

    def _sample_key(self):
        """一次布局指纹采样。"""
        code = ("__gp.begin(); JSON.stringify((function(){"
                "var k = __gp.probeKey(); " + self.KEY_SUFFIX + "return k;})())")
        key = self._eval_raw(code)
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

    def batch(self, expressions):
        """一次 eval 批量求值多条表达式，同时取回批次后的布局指纹。"""
        payload = json.dumps([str(expression) for expression in expressions])
        code = ("__gp.begin(); JSON.stringify((function(){"
                "var items = __gp.evalAll(" + payload + ");"
                "var k = __gp.probeKey(); " + self.KEY_SUFFIX +
                "return {items: items, k: k};})())")
        value = self._eval_raw(code)
        if not isinstance(value, dict) or not isinstance(value.get("items"), list):
            raise ProbeError("批量求值返回异常: %r" % (value,))
        if len(value["items"]) != len(expressions):
            raise ProbeError("批量求值条数不符: 期望 %d 实际 %d"
                             % (len(expressions), len(value["items"])))
        self._remember_key(value.get("k"))
        return value["items"]

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


def env_snapshot_js():
    """环境快照表达式：窗口度量、会话状态与运行时信息，一次求值一起取回。"""
    return ("{window: __gp.window(), state: __gp.state(), runtime: __gp.runtime()}")


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


def reach_editor(probe, world_name, deadline_seconds=180):
    """把客户端推到「进入世界 + 编辑器打开」；每次只推进一个界面，让每个界面真实渲染过。

    编辑器就绪的那一次求值顺手带回环境快照，省掉一条单独的 eval。
    """
    deadline = time.time() + deadline_seconds
    selector = json.dumps(world_name) if world_name else "undefined"
    log = []
    while time.time() < deadline:
        payload = probe._eval_raw(
            "JSON.stringify((function(){"
            "var m = __gp.menuStep(" + selector + ");"
            "if (m && m.done) {"
            " var e = __gp.editorStep();"
            " if (e && e.done) { return {menu: m, editor: e, env: " + env_snapshot_js() + "}; }"
            " return {menu: m, editor: e};"
            "}"
            "return {menu: m};})())")
        if isinstance(payload, dict) and "env" in payload:
            probe._absorb_env(payload)
            return log
        log.append(payload)
        time.sleep(0.4)
    raise ProbeError("未能在 %ss 内进入编辑器: %s"
                     % (deadline_seconds, json.dumps(log[-6:], ensure_ascii=False)))


def finish_check(check, result, langs):
    """把一条批量求值结果折算成检查记录；期望值逻辑与单条求值时一致。"""
    name = check["name"]
    if "expected_lang" in check:
        key = check["expected_lang"]
        lang = check.get("lang", "zh_cn")
        # 期望文案必须两份语言文件都有：缺一边说明 i18n 没同步，这条本身就该失败。
        missing = [code for code in ("en_us", "zh_cn") if key not in langs[code]]
        if missing:
            return {"name": name, "expected": key,
                    "actual": "语言文件缺少 key: " + ", ".join(missing), "pass": False}
        expected = langs[lang][key]
    else:
        expected = check.get("expected")
    if not result.get("ok"):
        return {"name": name, "expected": expected,
                "actual": "eval 失败: %s" % result.get("error"), "pass": False}
    actual = result.get("value")
    passed = actual == expected
    if not passed and check.get("contains"):
        passed = str(expected) in str(actual)
    record = {"name": name, "expected": expected, "actual": actual, "pass": passed}
    if check.get("soft"):
        record["soft"] = True
    return record


def shot_expression(name, rects):
    """截图表达式：一次求值同时拿截图元数据与元素矩形叠层。"""
    name_json = json.dumps(name)
    if rects:
        return ("(function(){var s = __gp.shot(%s); s.rects = %s; return s;})()"
                % (name_json, rects))
    return "__gp.shot(%s)" % name_json


def run_case(probe, case, langs, settle_timeout_ms):
    started = time.perf_counter()
    case_settle = case.get("settle") or {}
    settle_enabled = case_settle.get("enabled", True)
    settle_limit = case_settle.get("timeout_ms", settle_timeout_ms)
    checks = []
    artifacts = []
    pending = []          # 连续的 check/shot/dump 合成一次 eval，保持用例里的顺序
    pending_shot = False
    evals_started = probe.eval_count
    stats = {"settle_ms": 0, "shot_ms": 0, "shots": 0}
    error = None

    def flush():
        nonlocal pending, pending_shot
        if not pending:
            return
        items = pending
        pending = []
        if pending_shot:
            # 截图取上一帧帧缓冲，先在渲染线程外侧等两帧。
            probe.wait_frame()
            pending_shot = False
        try:
            values = probe.batch([item["expr"] for item in items])
        except Exception:
            # 慢路径：批量求值整体失败时逐条重跑，避免一条坏表达式连坐整批检查。
            values = []
            for item in items:
                try:
                    values.append({"ok": True, "value": probe.evaluate(item["expr"])})
                except Exception as failure:
                    values.append({"ok": False, "error": str(failure)})
        for item, result in zip(items, values):
            kind = item["kind"]
            if kind == "check":
                checks.append(finish_check(item["check"], result, langs))
            elif kind == "shot":
                if not result.get("ok"):
                    raise ProbeError("截图失败: " + str(result.get("error")))
                value = result.get("value") or {}
                path = value.get("path") or os.path.join(probe.run_dir, item["name"])
                if not os.path.exists(path):
                    raise ProbeError("截图没有落盘: " + path)
                with open(path, "rb") as handle:
                    png_bytes = handle.read()
                shot_ms = int(value.get("shot_ms") or 0)
                stats["shots"] += 1
                stats["shot_ms"] += shot_ms
                probe.shot_count += 1
                probe.shot_ms += shot_ms
                artifacts.append({
                    "kind": "screenshot", "name": item["name"], "path": path,
                    "png_bytes": png_bytes, "rects": value.get("rects"),
                    "thumb": value.get("thumb"), "width": value.get("width"),
                    "height": value.get("height"), "scale": value.get("scale"),
                })
            elif kind == "dump":
                if not result.get("ok"):
                    raise ProbeError("导出证据失败: " + str(result.get("error")))
                value = result.get("value") or {}
                artifacts.append({"kind": "json", "name": item["name"],
                                  "path": value.get("path")
                                  or os.path.join(probe.run_dir, item["name"])})
            else:
                raise ProbeError("未知步骤类型: " + str(kind))

    def settle_after(before, limit):
        settled = probe.settle(before_key=before, timeout_ms=limit)
        stats["settle_ms"] += settled["waited_ms"]

    try:
        window = case.get("window")
        if window:
            probe.perform("__gp.resize(%d, %d, %d)"
                          % (window["width"], window["height"], window["scale"]))
            if settle_enabled:
                settle_after(None, settle_limit)
        for step in case.get("steps", []):
            step_settle = step.get("settle", None)
            if "do" in step:
                flush()
                before = probe.last_key
                probe.perform(step["do"])
                want_settle = settle_enabled if step_settle is None else bool(step_settle)
                if want_settle:
                    limit = settle_limit
                    if isinstance(step_settle, dict) and step_settle.get("timeout_ms"):
                        limit = step_settle["timeout_ms"]
                    settle_after(before, limit)
            elif "wait_ms" in step:
                # 只有动画需要固定等待时用例才保留这一步，其余动作靠自适应 settle 收敛。
                flush()
                time.sleep(step["wait_ms"] / 1000.0)
            elif "shot" in step:
                pending.append({"kind": "shot", "name": step["shot"],
                                "expr": shot_expression(step["shot"], step.get("rects"))})
                pending_shot = True
            elif "dump" in step:
                pending.append({"kind": "dump", "name": step["dump"],
                                "expr": "__gp.dumpJson(%s, %s)"
                                        % (json.dumps(step["dump"]), step["expr"])})
            elif "check" in step:
                pending.append({"kind": "check", "check": step["check"],
                                "expr": step["check"]["expr"]})
            else:
                raise ProbeError("未知步骤: %s" % json.dumps(step, ensure_ascii=False))
        flush()
    except Exception as failure:
        error = "%s: %s" % (type(failure).__name__, failure)
        checks.append({"name": "用例执行未中断", "expected": "无异常",
                       "actual": error + " | " + traceback.format_exc(limit=2).replace("\n", " "),
                       "pass": False})
    if error:
        verdict = "error"
    elif any(not check["pass"] and not check.get("soft") for check in checks):
        verdict = "fail"
    elif checks:
        verdict = "pass"
    else:
        verdict = "skip"
    result = {"name": case["name"], "verdict": verdict, "detail": case.get("description", ""),
              "checks": checks, "artifacts": artifacts,
              "duration_ms": int((time.perf_counter() - started) * 1000)}
    metrics = {"name": case["name"], "duration_ms": result["duration_ms"],
               "evals": probe.eval_count - evals_started, "checks": len(checks),
               "shots": stats["shots"], "settle_ms": stats["settle_ms"],
               "shot_ms": stats["shot_ms"]}
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
    parser.add_argument("--skip-setup", action="store_true", help="跳过进入世界与打开编辑器")
    parser.add_argument("--timeout-ms", type=int, default=90000)
    parser.add_argument("--settle-timeout-ms", type=int, default=1500,
                        help="自适应 settle 的等待上限（毫秒）")
    args = parser.parse_args(argv)

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
    results = []
    case_metrics = []
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
        if not args.skip_setup:
            reach_editor(probe, args.world)
        phases["setup_ms"] = int((time.perf_counter() - setup_started) * 1000)
        env = build_env(probe)
        cases = load_cases(args.cases, args.only)
        if not cases:
            raise ProbeError("没有找到用例: " + args.cases)
        cases_started = time.perf_counter()
        for case in cases:
            result, metrics = run_case(probe, case, langs, args.settle_timeout_ms)
            results.append(result)
            case_metrics.append(metrics)
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
        print("[error] 前置阶段失败: " + setup_error)

    shots = None
    if results:
        sheet_started = time.perf_counter()
        try:
            shots = sheet.build(run_dir, results)
            print("[shots] %d 张 %dms" % (shots["count"], shots["sheet_ms"]))
            if shots.get("contact_sheet"):
                print("contact sheet: " + shots["contact_sheet"])
        except Exception as failure:
            # 拼图只是证据整理：失败时照常出报告，但要在终端留痕。
            shots = None
            print("[shots] 拼图失败: %s: %s" % (type(failure).__name__, failure))
        phases["sheet_ms"] = int((time.perf_counter() - sheet_started) * 1000)

    # 先渲染一次量出 report_ms，再用最终 metrics 重渲染：报告里的总耗时不漏掉报告本身。
    report_started = time.perf_counter()
    metrics = build_metrics(probe, phases, case_metrics,
                            int((time.perf_counter() - started) * 1000))
    document, summary = report.render_report(metadata, results, env,
                                             shots=shots, metrics=metrics)
    phases["report_ms"] = int((time.perf_counter() - report_started) * 1000)
    metrics = build_metrics(probe, phases, case_metrics,
                            int((time.perf_counter() - started) * 1000))
    document, summary = report.render_report(metadata, results, env,
                                             shots=shots, metrics=metrics)
    summary["setup_error"] = setup_error
    summary["duration_seconds"] = round(metrics["total_ms"] / 1000.0, 1)
    summary["metrics"] = metrics
    summary["shots"] = {"count": (shots or {}).get("count", 0),
                        "contact_sheet": (shots or {}).get("contact_sheet"),
                        "list": (shots or {}).get("list_path")}
    with open(os.path.join(run_dir, "gui-report.html"), "w", encoding="utf-8") as handle:
        handle.write(document)
    with open(os.path.join(run_dir, "gui-summary.json"), "w", encoding="utf-8") as handle:
        json.dump(summary, handle, ensure_ascii=False, indent=2)
    with open(os.path.join(run_dir, "run-metadata.json"), "w", encoding="utf-8") as handle:
        json.dump({"metadata": metadata, "env": env, "metrics": metrics},
                  handle, ensure_ascii=False, indent=2)
    print("verdict=%s 用例 %d/%d 细项 %d/%d"
          % (summary["verdict"], summary["passed"], summary["total_cases"],
             summary["passed_checks"], summary["total_checks"]))
    print("[metrics] total=%.1fs evals=%d rtt_avg=%.1fms settle=%dms shots=%d"
          % (metrics["total_ms"] / 1000.0, metrics["evals"]["count"],
             metrics["evals"]["rtt_avg_ms"], metrics["settle"]["waited_ms"],
             metrics["shots"]["count"]))
    print("报告: " + os.path.join(run_dir, "gui-report.html"))
    if setup_error:
        return 1
    return 0 if summary["verdict"] == "pass" else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
