"""GUI 用例声明、连接前校验、断言取数/判定与文档投影。"""
from __future__ import annotations

import json
import math
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Callable


class ContractError(ValueError):
    """包含用例、步骤与字段路径的契约错误。"""


@dataclass(frozen=True)
class Field:
    kind: str
    required: bool = False
    minimum: float | None = None
    maximum: float | None = None


S = Field("string")
B = Field("boolean")
POSITIVE = Field("integer", minimum=1)
SELECTOR = Field("selector", True)
WINDOW = {"width": Field("integer", True, 1), "height": Field("integer", True, 1), "scale": Field("integer", minimum=0)}
STARTS = {
    "editor": {"new_rule": B},
    "workbench": {},
    "scene": {"scene": Field("string", True)},
}
SCENES = ("gallery", "interactions", "previews", "kit_showcase")
ACTIONS = {
    "click": {"selector": SELECTOR},
    "type": {"text": S, "unique_text": B},
    "clear": {},
    "key": {"code": Field("integer", True), "modifiers": Field("integer", minimum=0)},
    "resize": WINDOW,
    "scroll": {"selector": SELECTOR, "delta": Field("number", True)},
    "drag": {"selector": SELECTOR, "from": Field("number", True), "to": Field("number", True), "axis": S},
    "press": {"selector": SELECTOR, "at": Field("number", True), "axis": S},
    "release": {},
}
QUERIES = {name: {} for name in ("screen_title", "window", "layout", "workspace")}
QUERIES.update({name: {"selector": Field("selector")} for name in ("nodes", "count", "text", "enabled", "rect")})
SCOPE = {"kind": S, "lang": Field("language"), "text": S}
SELECTOR_FIELDS = {**SCOPE, "lang_any": Field("languages"), "text_any": Field("strings"),
                   "enabled": B, "within": Field("scope"), "has_text": S}


def json_equal(actual, expected):
    """JSON number 数值相等；boolean 与 number 始终区分。"""
    if isinstance(actual, bool) or isinstance(expected, bool):
        return type(actual) is type(expected) and actual == expected
    if isinstance(actual, (int, float)) and isinstance(expected, (int, float)):
        return actual == expected
    if type(actual) is not type(expected):
        return False
    if isinstance(actual, list):
        return len(actual) == len(expected) and all(json_equal(a, b) for a, b in zip(actual, expected))
    if isinstance(actual, dict):
        return actual.keys() == expected.keys() and all(json_equal(actual[key], expected[key]) for key in actual)
    return actual == expected


def geometry_inside(actual, expected):
    window = actual["window"]
    return actual["x"] >= 0 and actual["y"] >= 0 and actual["x"] + actual["width"] <= window["logicalWidth"] and actual["y"] + actual["height"] <= window["logicalHeight"]


@dataclass(frozen=True)
class Assertion:
    source: str | None
    compare: Callable
    description: str
    fixed_expected: object = None
    expected_required: bool = False
    changed: bool = False


ASSERTIONS = {
    "text_present": Assertion("count", lambda actual, expected: actual >= 1, "节点数量至少一项", {"min": 1}),
    "text_absent": Assertion("count", json_equal, "节点数量为零", 0),
    "text_count": Assertion("count", json_equal, "节点数量精确相等", expected_required=True),
    "node_enabled": Assertion("enabled", json_equal, "boolean 启用态精确相等", expected_required=True),
    "geometry_inside": Assertion("rect", geometry_inside, "原始矩形位于逻辑窗口内", True),
    "value_equals": Assertion(None, json_equal, "JSON 类型与值相等", expected_required=True),
    "value_changed": Assertion(None, lambda actual, expected: not json_equal(actual, expected["different_from"]), "与本例 capture 不同", changed=True),
}


def _fail(path, message):
    raise ContractError(f"{path}: {message}")


def _field(value, field, path, langs):
    kind = field.kind
    if kind in ("selector", "scope", "query"):
        if kind == "query":
            _variant(value, "source", QUERIES, path, langs)
        else:
            _shape(value, SELECTOR_FIELDS if kind == "selector" else SCOPE, path, langs)
        return
    valid = {"string": lambda: isinstance(value, str), "language": lambda: isinstance(value, str),
             "boolean": lambda: type(value) is bool, "integer": lambda: type(value) is int,
             "number": lambda: type(value) in (int, float) and math.isfinite(value),
             "strings": lambda: isinstance(value, list) and all(isinstance(v, str) for v in value),
             "languages": lambda: isinstance(value, list) and all(isinstance(v, str) for v in value),
             "json": lambda: True}[kind]()
    if not valid:
        _fail(path, "需要 " + kind)
    if kind == "json":
        try:
            json.dumps(value, allow_nan=False)
        except (ValueError, TypeError):
            _fail(path, "需要 JSON 原生值")
    if field.minimum is not None and value < field.minimum or field.maximum is not None and value > field.maximum:
        _fail(path, f"数值超出范围 {field.minimum}..{field.maximum}")
    if kind in ("language", "languages"):
        for key in [value] if kind == "language" else value:
            if any(key not in entries for entries in langs.values()):
                _fail(path, "语言 key 缺失: " + key)


def _shape(value, fields, path, langs):
    if not isinstance(value, dict):
        _fail(path, "需要 object")
    unknown = value.keys() - fields.keys()
    if unknown:
        _fail(path, "未知字段 " + ", ".join(sorted(unknown)))
    for key, field in fields.items():
        if key not in value:
            if field.required:
                _fail(path + "." + key, "缺少必需字段")
        else:
            _field(value[key], field, path + "." + key, langs)


def _variant(value, key, registry, path, langs, common=None):
    if not isinstance(value, dict) or not isinstance(value.get(key), str) or value[key] not in registry:
        _fail(path + "." + key, "未知名称 " + repr(value.get(key) if isinstance(value, dict) else value))
    _shape(value, {key: Field("string", True), **(common or {}), **registry[value[key]]}, path, langs)


def validate_case(case, langs):
    """连接前递归校验所有步骤，capture 引用按执行顺序对账。"""
    path = str(case.get("name", "<unnamed>")) if isinstance(case, dict) else "<case>"
    if not isinstance(case, dict) or not isinstance(case.get("steps"), list):
        _fail(path + ".steps", "需要 array")
    head = {k: v for k, v in case.items() if k not in ("start", "window", "settle", "steps")}
    _shape(head, {"name": Field("string", True), "description": S}, path, langs)
    _variant(case.get("start"), "kind", STARTS, path + ".start", langs,
             {"world": S, "timeout_seconds": POSITIVE})
    target = case["start"]
    if target["kind"] == "scene" and target["scene"] not in SCENES:
        _fail(path + ".start.scene", "未知 scene " + target["scene"])
    if "window" in case:
        _shape(case["window"], WINDOW, path + ".window", langs)
    if "settle" in case:
        _shape(case["settle"], {"timeout_ms": POSITIVE}, path + ".settle", langs)
    captures = set()
    for index, step in enumerate(case["steps"], 1):
        at = f"{path}.steps[{index}]"
        if not isinstance(step, dict):
            _fail(at, "需要 object")
        kinds = step.keys() & {"action", "capture", "assert", "wait_for", "shot", "dump"}
        if len(kinds) != 1:
            _fail(at, "步骤必须且只能声明一种操作")
        kind = next(iter(kinds))
        allowed = {kind}
        if kind == "action":
            allowed.add("settle")
            _variant(step[kind], "kind", ACTIONS, at + ".action", langs)
            action = step[kind]
            if action["kind"] in ("drag", "press") and action.get("axis", "x") not in ("x", "y"):
                _fail(at + ".action.axis", "axis must be x or y")
            if action["kind"] == "type" and "text" not in action and action.get("unique_text") is not True:
                _fail(at + ".action.text", "type 需要 text 或 unique_text=true")
            if "settle" in step:
                _field(step["settle"], B, at + ".settle", langs)
        elif kind in ("capture", "dump"):
            allowed.add("query")
            _field(step[kind], S, at + "." + kind, langs)
            _field(step.get("query"), Field("query"), at + ".query", langs)
            if kind == "capture":
                captures.add(step[kind])
        elif kind == "shot":
            _field(step[kind], S, at + ".shot", langs)
        else:
            allowed.add("timeout_ms")
            if "timeout_ms" in step:
                _field(step["timeout_ms"], POSITIVE, at + ".timeout_ms", langs)
            check = step[kind]
            if not isinstance(check, dict) or not isinstance(check.get("kind"), str) or check["kind"] not in ASSERTIONS:
                _fail(at + "." + kind + ".kind", "未知断言")
            assertion = ASSERTIONS[check["kind"]]
            fields = {"name": Field("string", True), "kind": Field("string", True),
                      "expected": Field("json"), "expected_lang": Field("language"), "expected_capture": S,
                      "timeout_ms": POSITIVE}
            fields["selector" if assertion.source else "query"] = SELECTOR if assertion.source else Field("query", True)
            if assertion.changed:
                fields["capture"] = Field("string", True)
            _shape(check, fields, at + "." + kind, langs)
            expected_sources = check.keys() & {"expected", "expected_lang", "expected_capture"}
            if len(expected_sources) > 1 or assertion.expected_required and not expected_sources:
                _fail(at + "." + kind, "期望来源必须唯一；该断言需要期望值")
            if check["kind"] == "node_enabled" and "expected" in check:
                _field(check["expected"], B, at + "." + kind + ".expected", langs)
            if check["kind"] == "text_count" and "expected" in check:
                _field(check["expected"], Field("integer", minimum=0), at + "." + kind + ".expected", langs)
            for key in ("expected_capture", "capture"):
                if key in check and check[key] not in captures:
                    _fail(at + "." + kind + "." + key, "缺少前置 capture " + check[key])
        if step.keys() - allowed:
            _fail(at, "未知字段 " + ", ".join(sorted(step.keys() - allowed)))


def assertion_query(check):
    assertion = ASSERTIONS[check["kind"]]
    return {"source": assertion.source, "selector": check["selector"]} if assertion.source else check["query"]


def finish_check(check, result, langs, state):
    assertion = ASSERTIONS[check["kind"]]
    expected = state.get(check["expected_capture"]) if "expected_capture" in check else check.get("expected")
    if "expected_lang" in check:
        _field(check["expected_lang"], Field("language"), check["name"] + ".expected_lang", langs)
        expected = result.get("language_value")
    if not assertion.expected_required:
        expected = {"different_from": state.get(check["capture"])} if assertion.changed else assertion.fixed_expected
    actual = result.get("value")
    passed = bool(result.get("ok")) and assertion.compare(actual, expected)
    record = {"name": check["name"], "kind": check["kind"], "target": check.get("selector", check.get("query")),
              "expected": expected, "actual": actual, "pass": passed}
    if not result.get("ok"):
        record["error"] = result.get("error")
    return record


def client_contract():
    """仅导出声明数据；客户端执行仍由 JavaScript handler 持有。"""
    return {"actions": sorted(ACTIONS), "queries": sorted(QUERIES), "starts": sorted(STARTS), "scenes": sorted(SCENES)}


def check_handlers(contract):
    expected = client_contract()
    if contract != expected:
        raise ContractError(f"客户端 handler 与声明不一致：expected={expected!r}, actual={contract!r}")


def check_local_handlers(path: Path):
    """读取 JS 的实际 handler 注册键，在连接 MCP 前对账。"""
    text = path.read_text(encoding="utf-8")
    actual = {}
    for key, marker in (("actions", "ACTION_HANDLERS"), ("queries", "QUERY_HANDLERS"), ("starts", "START_HANDLERS"), ("scenes", "SCENE_HANDLERS")):
        match = re.search(r"var " + marker + r" = \{(.*?)^  \};", text, re.MULTILINE | re.DOTALL)
        if not match:
            raise ContractError("客户端缺少 handler 注册表 " + marker)
        actual[key] = sorted(re.findall(r"^    ([a-z_]+):", match[1], re.MULTILINE))
    check_handlers(actual)


def document():
    """从同一字段与断言声明生成指南受管内容。"""
    lines = ["| 类别 | 名称 | 字段/判定 |", "|---|---|---|"]
    for category, registry in (("start", STARTS), ("action", ACTIONS), ("query", QUERIES)):
        for name, fields in registry.items():
            detail = ", ".join(key + ("*" if field.required else "") + ": " + field.kind for key, field in fields.items()) or "无附加字段"
            lines.append(f"| {category} | {name} | {detail} |")
    lines += [f"| assertion | {name} | {assertion.description} |" for name, assertion in ASSERTIONS.items()]
    lines += ["", "星号表示必需字段。start 公共字段为 world、timeout_seconds；scene 取值：" + "、".join(SCENES) + "。",
              "selector 字段：" + "、".join(SELECTOR_FIELDS) + "；within 仅含 kind/lang/text。",
              "wait_for 的轮询期限由步骤级 timeout_ms 控制，默认 2500ms；断言对象内的同名兼容字段保留校验，不改变该期限。",
              "expected、expected_lang、expected_capture 是互斥期望来源；capture 引用必须在同例前序步骤中存在。"]
    return "\n".join(lines) + "\n"
