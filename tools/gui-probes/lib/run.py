"""GUI 探针驱动：连接真实客户端，逐步执行用例步骤，汇总证据并生成报告。

驱动只做三件事：把客户端推到用例需要的前置状态、按步骤驱动真实输入、把结果落成证据。
所有断言都在用例文件里声明，期望值来自语言文件或稳定的状态契约；驱动不替用例决定期望值。
每个 eval 都是独立一次往返：阻塞动作（等进世界、等重绘）放在驱动侧，不在游戏线程里等。
"""

import argparse
import json
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

sys.path.insert(0, LIB_DIR)
import autojs  # noqa: E402
import report  # noqa: E402


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


class Probe:
    """一次运行内复用的游戏进程句柄：每条表达式都带上同一份原语与证据目录。"""

    def __init__(self, run_dir, timeout_ms=90000):
        self.run_dir = os.path.abspath(run_dir).replace("\\", "/")
        self.timeout_ms = timeout_ms
        with open(PRELUDE_PATH, encoding="utf-8") as handle:
            self.prelude = handle.read()

    def evaluate(self, expression):
        # 求值结果统一用 JSON.stringify 收口：跨进程只能拿文本，对象要显式序列化才不会退化成 toString。
        body = "JSON.stringify((function(){return (" + expression + ");})())"
        code = ("var __gpRunDir = " + json.dumps(self.run_dir) + ";\n"
                + self.prelude + "\n" + body)
        return unwrap(autojs.eval_js(code, side="client", timeout_ms=self.timeout_ms))


def wait_for_client(deadline_seconds):
    """客户端起来前 MCP 必然连不上，连不上是等待的一部分，不当失败。"""
    deadline = time.time() + deadline_seconds
    last_error = None
    while time.time() < deadline:
        try:
            autojs.health()
            return
        except Exception as error:
            last_error = error
            time.sleep(2)
    raise ProbeError("客户端 MCP 在 %ss 内没有就绪: %s" % (deadline_seconds, last_error))


def reach_editor(probe, world_name, deadline_seconds=180):
    """把客户端推到「进入世界 + 编辑器打开」；每次只推进一个界面，让每个界面真实渲染过。"""
    deadline = time.time() + deadline_seconds
    log = []
    while time.time() < deadline:
        selector = json.dumps(world_name) if world_name else "undefined"
        menu = probe.evaluate("__gp.menuStep(" + selector + ")")
        if isinstance(menu, dict) and menu.get("done"):
            editor = probe.evaluate("__gp.editorStep()")
            log.append({"step": "editor", "result": editor})
            if isinstance(editor, dict) and editor.get("done"):
                return log
        else:
            log.append({"step": "menu", "result": menu})
        time.sleep(1.5)
    raise ProbeError("未能在 %ss 内进入编辑器: %s"
                     % (deadline_seconds, json.dumps(log[-6:], ensure_ascii=False)))


def run_check(probe, check, langs):
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
    try:
        actual = probe.evaluate(check["expr"])
    except Exception as error:
        return {"name": name, "expected": expected, "actual": "eval 失败: %s" % error, "pass": False}
    passed = actual == expected
    if not passed and check.get("contains"):
        passed = str(expected) in str(actual)
    record = {"name": name, "expected": expected, "actual": actual, "pass": passed}
    if check.get("soft"):
        record["soft"] = True
    return record


def run_case(probe, case, langs):
    started = time.time()
    checks = []
    artifacts = []
    error = None
    try:
        window = case.get("window")
        if window:
            probe.evaluate("__gp.resize(%d, %d, %d)"
                           % (window["width"], window["height"], window["scale"]))
            time.sleep(case.get("settle_ms", 600) / 1000.0)
        for step in case.get("steps", []):
            if "do" in step:
                probe.evaluate(step["do"])
            elif "wait_ms" in step:
                time.sleep(step["wait_ms"] / 1000.0)
            elif "shot" in step:
                name = step["shot"]
                probe.evaluate("__gp.shot(%s)" % json.dumps(name))
                path = os.path.join(probe.run_dir, name)
                artifact = {"kind": "screenshot", "name": name, "path": path}
                if os.path.exists(path):
                    with open(path, "rb") as handle:
                        artifact["png_bytes"] = handle.read()
                else:
                    raise ProbeError("截图没有落盘: " + path)
                if step.get("rects"):
                    artifact["rects"] = probe.evaluate(step["rects"])
                artifacts.append(artifact)
            elif "dump" in step:
                name = step["dump"]
                probe.evaluate("__gp.dumpJson(%s, %s)"
                               % (json.dumps(name), step["expr"]))
                artifacts.append({"kind": "json", "name": name, "path": os.path.join(probe.run_dir, name)})
            elif "check" in step:
                checks.append(run_check(probe, step["check"], langs))
            else:
                raise ProbeError("未知步骤: %s" % json.dumps(step, ensure_ascii=False))
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
    return {"name": case["name"], "verdict": verdict, "detail": case.get("description", ""),
            "checks": checks, "artifacts": artifacts,
            "duration_ms": int((time.time() - started) * 1000)}


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


def main(argv):
    parser = argparse.ArgumentParser(description="GUI 探针驱动")
    parser.add_argument("--run-dir", required=True, help="本次运行的证据目录")
    parser.add_argument("--cases", default=os.path.join(TOOLS_DIR, "gui-probes", "cases"))
    parser.add_argument("--only", action="append", default=[], help="只跑指定用例名，可重复")
    parser.add_argument("--world", default=None, help="要进入的存档名；省略则选列表第一个")
    parser.add_argument("--wait-client", type=int, default=240, help="等待 MCP 就绪的秒数")
    parser.add_argument("--skip-setup", action="store_true", help="跳过进入世界与打开编辑器")
    parser.add_argument("--timeout-ms", type=int, default=90000)
    args = parser.parse_args(argv)

    run_dir = os.path.abspath(args.run_dir)
    os.makedirs(run_dir, exist_ok=True)
    langs = load_lang()
    started = time.time()
    metadata = {"title": "GUI 探针报告", "started_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
                "run_dir": run_dir, "harness": "AutoJS + tools/gui-probes"}
    env = {}
    results = []
    setup_error = None
    wait_for_client(args.wait_client)
    probe = Probe(run_dir, args.timeout_ms)
    try:
        if not args.skip_setup:
            reach_editor(probe, args.world)
        env = probe.evaluate("__gp.window()")
        state = probe.evaluate("JSON.stringify(__gp.state())")
        if isinstance(env, dict):
            env.update({key: value for key, value in state.items() if key not in env})
        cases = load_cases(args.cases, args.only)
        if not cases:
            raise ProbeError("没有找到用例: " + args.cases)
        for case in cases:
            result = run_case(probe, case, langs)
            results.append(result)
            print("[%s] %s" % (result["verdict"], result["name"]))
            for check in result["checks"]:
                if not check["pass"]:
                    print("    x %s: 期望 %r 实际 %r"
                          % (check["name"], check["expected"], check["actual"]))
    except Exception as failure:
        setup_error = "%s: %s" % (type(failure).__name__, failure)
        print("[error] 前置阶段失败: " + setup_error)

    document, summary = report.render_report(metadata, results, env)
    summary["setup_error"] = setup_error
    summary["duration_seconds"] = round(time.time() - started, 1)
    with open(os.path.join(run_dir, "gui-report.html"), "w", encoding="utf-8") as handle:
        handle.write(document)
    with open(os.path.join(run_dir, "gui-summary.json"), "w", encoding="utf-8") as handle:
        json.dump(summary, handle, ensure_ascii=False, indent=2)
    with open(os.path.join(run_dir, "run-metadata.json"), "w", encoding="utf-8") as handle:
        json.dump({"metadata": metadata, "env": env}, handle, ensure_ascii=False, indent=2)
    print("verdict=%s 用例 %d/%d 细项 %d/%d"
          % (summary["verdict"], summary["passed"], summary["total_cases"],
             summary["passed_checks"], summary["total_checks"]))
    print("报告: " + os.path.join(run_dir, "gui-report.html"))
    if setup_error:
        return 1
    return 0 if summary["verdict"] == "pass" else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
