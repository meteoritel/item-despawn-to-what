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
TOOLS_DIR = os.path.dirname(os.path.dirname(LIB_DIR))
REPO_ROOT = os.path.dirname(TOOLS_DIR)
LANG_DIR = os.path.join(REPO_ROOT, "common", "src", "main", "resources", "assets",
                        "itemdespawntowhat", "lang")
PRELUDE_PATH = os.path.join(LIB_DIR, "prelude.js")
# prelude 里 __gp.__version 必须与这里一致：版本不同说明会话里装的是旧原语，需要重装。
PRELUDE_VERSION = 9


sys.path.insert(0, LIB_DIR)
import autojs  # noqa: E402
import report  # noqa: E402
import sheet  # noqa: E402
from record import RunRecord, FILES, case_verdict  # noqa: E402


class ProbeError(RuntimeError):
    pass


def load_lang():
    """读取两份语言文件：期望文案只从语言文件取，脚本不重复硬编码。"""
    langs = {}
    for name in ("en_us", "zh_cn"):
        with open(os.path.join(LANG_DIR, name + ".json"), encoding="utf-8") as handle:
            langs[name] = json.load(handle)
        dev = Path(REPO_ROOT) / "common/src/dev/resources/assets/idtw_probe/lang" / (name + ".json")
        langs[name].update(json.loads(dev.read_text(encoding="utf-8")))
    return langs


def unwrap(raw):
    """折叠 MCP 返回：可能是字符串、也可能带 value 的字典，两种都还原成 eval 结果。"""
    if isinstance(raw, dict):
        for key in ("value", "result", "text"):
            if key in raw:
                raw = raw[key]
                break
    # 驱动统一输出一次 JSON.stringify；只解码传输层，保留 "null"、"true" 和数字文本。
    if isinstance(raw, str):
        try:
            return json.loads(raw)
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


def reach(probe, target, world=None):
    """每条用例通过公开输入路径重建自己的起点。"""
    deadline = time.monotonic() + target.get("timeout_seconds", 180)
    target = dict(target, world=target.get("world", world))
    recent = []
    while time.monotonic() < deadline:
        value = probe.evaluate("__gp.reachStep(%s)" % json.dumps(target))
        recent.append(value)
        if isinstance(value, dict) and value.get("done"):
            return
        time.sleep(0.2)
    raise ProbeError("导航超时 target=%r recent=%r state=%r" % (target, recent[-5:], probe.evaluate("__gp.state()")))


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
    return probe.evaluate("__gp.read(%s)" % json.dumps(query, ensure_ascii=False))


def assertion_query(check):
    kind = check["kind"]
    if kind in ("text_present", "text_absent", "text_count"):
        return {"source": "count", "selector": check["selector"]}
    if kind == "node_enabled":
        return {"source": "enabled", "selector": check["selector"]}
    if kind == "geometry_inside":
        return {"source": "rect", "selector": check["selector"]}
    return check["query"]


def json_equal(actual, expected):
    """JSON number 共用数值语义，集合内部同样区分 boolean 与 number。"""
    if isinstance(actual, bool) or isinstance(expected, bool): return type(actual) is type(expected) and actual == expected
    if isinstance(actual, (int, float)) and isinstance(expected, (int, float)): return actual == expected
    if type(actual) is not type(expected): return False
    if isinstance(actual, list): return len(actual) == len(expected) and all(json_equal(a, b) for a, b in zip(actual, expected))
    if isinstance(actual, dict): return actual.keys() == expected.keys() and all(json_equal(actual[key], expected[key]) for key in actual)
    return actual == expected


def finish_check(check, result, langs, state):
    """唯一断言解释器保留 JSON 原生期望与实际值。"""
    kind = check["kind"]
    expected = state.get(check["expected_capture"]) if "expected_capture" in check else check.get("expected")
    if "expected_lang" in check:
        key = check["expected_lang"]
        for language in ("en_us", "zh_cn"):
            if key not in langs[language]:
                raise ProbeError("语言文件缺少 key: " + key)
        expected = result.get("language_value")
    if kind == "text_present": expected = {"min": 1}
    elif kind == "text_absent": expected = 0
    elif kind == "geometry_inside": expected = True
    elif kind == "value_changed": expected = {"different_from": state.get(check["capture"])}
    actual = result.get("value")
    passed = False
    if result.get("ok"):
        if kind == "text_present": passed = actual >= 1
        elif kind == "geometry_inside":
            w = actual["window"]
            passed = actual["x"] >= 0 and actual["y"] >= 0 and actual["x"] + actual["width"] <= w["logicalWidth"] and actual["y"] + actual["height"] <= w["logicalHeight"]
        elif kind == "value_changed": passed = not json_equal(actual, state.get(check["capture"]))
        else: passed = json_equal(actual, expected)
    record = {"name": check["name"], "kind": kind, "target": check.get("selector", check.get("query")),
              "expected": expected, "actual": actual, "pass": passed}
    if not result.get("ok"): record["error"] = result.get("error")
    return record


def perform_action(probe, action):
    action = dict(action)
    if action.get("unique_text"):
        action["text"] = "probe_" + str(time.time_ns())
        action.pop("unique_text")
    value, before = probe.perform("__gp.action(%s)" % json.dumps(action, ensure_ascii=False))
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
                        if "expected_lang" in check: result["language_value"] = probe.evaluate("__gp.lang(%s)" % json.dumps(check["expected_lang"]))
                    except Exception as failure:
                        result = {"ok": False, "error": str(failure)}
                    outcome = finish_check(check, result, langs, state)
                    if outcome["pass"] or time.monotonic() >= deadline: break
                    time.sleep(0.05)
                checks.append(outcome)
            elif "shot" in step:
                probe.wait_frame()
                value = probe.evaluate("__gp.shot(%s)" % json.dumps(step["shot"]))
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
    langs = load_lang()
    assertion_kinds = {"text_present", "text_absent", "text_count", "node_enabled", "geometry_inside", "value_equals", "value_changed"}
    actions = {"click", "type", "clear", "key", "resize", "scroll", "drag", "press", "release"}
    sources = {"screen_title", "window", "layout", "workspace", "nodes", "count", "text", "enabled", "rect"}
    def validate_keys(value):
        if isinstance(value, dict):
            for key, item in value.items():
                if key in ("lang", "expected_lang"):
                    if any(item not in langs[language] for language in langs): raise ProbeError("语言 key 缺失: " + item)
                elif key == "lang_any":
                    for item_key in item:
                        if any(item_key not in langs[language] for language in langs): raise ProbeError("语言 key 缺失: " + item_key)
                else: validate_keys(item)
        elif isinstance(value, list):
            for item in value: validate_keys(item)
    for case in cases:
        target = case.get("start", {})
        if target.get("kind") not in ("editor", "workbench", "scene") or target.get("kind") == "scene" and target.get("scene") not in ("gallery", "interactions", "previews"):
            raise ProbeError("未知 start target: " + case["name"])
        captures = set()
        for step in case["steps"]:
            kinds = set(step) & {"action", "capture", "assert", "wait_for", "shot", "dump"}
            if len(kinds) != 1 or set(step) & {"do", "expr", "check"}: raise ProbeError("未知步骤: " + str(step))
            if "action" in step and step["action"].get("kind") not in actions: raise ProbeError("未知动作: " + str(step))
            check = step.get("assert", step.get("wait_for"))
            if check:
                if check.get("kind") not in assertion_kinds: raise ProbeError("未知断言: " + str(check))
                if "expected_capture" in check and check["expected_capture"] not in captures: raise ProbeError("缺少 capture: " + check["expected_capture"])
                if check["kind"] == "value_changed" and check.get("capture") not in captures: raise ProbeError("缺少 capture: " + str(check.get("capture")))
                if check["kind"] in ("value_equals", "text_count", "node_enabled") and "expected" not in check and "expected_lang" not in check and "expected_capture" not in check: raise ProbeError("断言缺少期望: " + str(check))
            query = step.get("query", (check or {}).get("query"))
            if query and query.get("source") not in sources: raise ProbeError("未知 query: " + str(query))
            if "capture" in step: captures.add(step["capture"])
            validate_keys(step)
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
        probe.world = args.world
        phases["setup_ms"] = int((time.perf_counter() - setup_started) * 1000)
        env = build_env(probe)
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

    metrics = build_metrics(probe, phases, case_metrics, int((time.perf_counter()-started)*1000))
    record = RunRecord(run_dir, metadata, env, results, metrics, setup_error)
    shots = None
    if results:
        sheet_started = time.perf_counter()
        try:
            shots = sheet.build(record)
            record.derived = shots
            print("[shots] %d 张 %dms" % (len(record.screenshots()), shots["sheet_ms"]))
        except Exception as failure:
            record.artifact_errors.append({"role":"sheet", "error":str(failure)})
            print("[shots] 拼图失败: " + str(failure))
        phases["sheet_ms"] = int((time.perf_counter()-sheet_started)*1000)
    metrics.update(build_metrics(probe, phases, case_metrics, int((time.perf_counter()-started)*1000)))
    report_started = time.perf_counter()
    try:
        document = report.render_report(record, shots)
        (record.directory / FILES["report"]).write_text(document, encoding="utf-8")
    except Exception as failure:
        record.artifact_errors.append({"role":"report", "error":str(failure)})
    metrics["phases"]["report_ms"] = int((time.perf_counter()-report_started)*1000)
    metrics["total_ms"] = int((time.perf_counter()-started)*1000)
    record.save()
    summary = record.summary()
    print("verdict=%s 用例 %d/%d 细项 %d/%d"
          % (summary["verdict"], summary["passed"], summary["total_cases"],
             summary["passed_checks"], summary["total_checks"]))
    print("[metrics] total=%.1fs evals=%d rtt_avg=%.1fms settle=%dms shots=%d"
          % (metrics["total_ms"] / 1000.0, metrics["evals"]["count"],
             metrics["evals"]["rtt_avg_ms"], metrics["settle"]["waited_ms"],
             metrics["shots"]["count"]))
    for artifact_error in record.artifact_errors:
        print("[artifact-error] " + json.dumps(artifact_error, ensure_ascii=False))
    print("报告: " + os.path.join(run_dir, "gui-report.html"))
    if setup_error:
        return 1
    return 0 if summary["verdict"] == "pass" else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
