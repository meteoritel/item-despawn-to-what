#!/usr/bin/env python3
"""从运行声明导出的 JSON 更新或校验双语配置参考块；不解析 Java 源码。"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

TICK = chr(96)
LANGUAGES = {
    "zh_cn": {
        "page": "zh-07-server-config.md",
        "headers": ["字段", "作用", "类型 / 范围", "缺省值", "什么时候需要改"],
        "types": {"integer": "整数", "boolean": "布尔", "string": "字符串", "object": "对象"},
        "fallback": "不填写时跟随 ", "nested": "见子字段", "ticks": "刻", "seconds": "秒", "us": "微秒",
    },
    "en_us": {
        "page": "en-07-server-config.md",
        "headers": ["Field", "What It Does", "Type / Range", "Default", "When to Change It"],
        "types": {"integer": "Integer", "boolean": "Boolean", "string": "String", "object": "Object"},
        "fallback": "Absent: follows ", "nested": "See sub-fields", "ticks": "ticks", "seconds": "seconds", "us": "microseconds",
    },
}


def read_json(path: Path) -> dict:
    def unique(pairs: list[tuple]) -> dict:
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"{path}: 重复键 {key}")
            result[key] = value
        return result
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique)


def field_paths(fields: list[dict], prefix: str = "") -> set[str]:
    result: set[str] = set()
    for field in fields:
        path = prefix + field["key"]
        if path in result:
            raise ValueError(f"声明中重复字段路径: {path}")
        result.add(path)
        result.update(field_paths(field.get("children", []), path + "."))
    return result


def code(value: str) -> str:
    return TICK + value + TICK


def table(fields: list[dict], messages: dict, words: dict, prefix: str = "") -> str:
    headers = words["headers"][:4] if prefix else words["headers"]
    lines = ["| " + " | ".join(headers) + " |", "| " + " | ".join("---" for _ in headers) + " |"]
    for field in fields:
        path = prefix + field["key"]
        message = messages[path]
        kind = words["types"][field["type"]]
        if "min" in field:
            kind += f" [{field['min']}, {field['max']}]"
        for suffix in ("ticks", "seconds", "us"):
            if field["key"].endswith("_" + suffix):
                kind += " " + words[suffix]
        if "fallback" in field:
            default = words["fallback"] + code(field["fallback"])
        elif field["type"] == "object":
            default = words["nested"]
        else:
            default = code(json.dumps(field["default"], ensure_ascii=False))
        cells = [code(field["key"]), message["description"], kind, default]
        if not prefix:
            cells.append(message["advice"])
        lines.append("| " + " | ".join(cell.replace("|", "&#124;") for cell in cells) + " |")
    return "\n".join(lines)


def replace_block(source: str, name: str, body: str, page: Path) -> str:
    begin = f"<!-- config-reference:{name}:begin -->"
    end = f"<!-- config-reference:{name}:end -->"
    if source.count(begin) != 1 or source.count(end) != 1:
        raise ValueError(f"{page}: {name} 的受管块标记必须各出现一次")
    left = source.index(begin) + len(begin)
    right = source.index(end)
    if left > right:
        raise ValueError(f"{page}: {name} 的受管块标记顺序错误")
    return source[:left] + "\n" + body + "\n" + source[right:]


def generate(root: Path, schema: dict, check: bool) -> int:
    fields = schema["fields"]
    expected = field_paths(fields)
    nested = [field for field in fields if "children" in field]
    if len(nested) != 1 or nested[0]["key"] != "nearby_products":
        raise ValueError("配置参考页需要 nearby_products 嵌套对象声明")
    updates = []
    for language, words in LANGUAGES.items():
        messages = read_json(root / "tools/config-docs/messages" / (language + ".json"))
        if set(messages) != expected:
            missing = sorted(expected - set(messages))
            extra = sorted(set(messages) - expected)
            raise ValueError(f"{language}: 缺少说明 {missing}; 多余说明 {extra}")
        page = root / "docs/wiki" / words["page"]
        source = page.read_text(encoding="utf-8")
        default_json = json.dumps(schema["defaults"], ensure_ascii=False, indent=2)
        blocks = {
            "defaults": TICK * 3 + "json\n" + default_json + "\n" + TICK * 3,
            "fields": table(fields, messages, words),
            "nearby-products": table(nested[0]["children"], messages, words, "nearby_products."),
        }
        rendered = source
        for name, body in blocks.items():
            rendered = replace_block(rendered, name, body, page)
        if rendered != source:
            updates.append((page, rendered))
    if check:
        for page, _ in updates:
            print(f"[违规] 配置参考内容过期: {page}")
        if updates:
            print("请先导出配置事实，再运行 tools/gen-config-reference.py 更新双语受管块。")
            return 1
    else:
        for page, content in updates:
            page.write_text(content, encoding="utf-8", newline="\n")
            print(f"[更新] {page}")
    print("[通过] 配置参考内容、声明字段与双语说明一致")
    return 0


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--schema", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    root = args.root.resolve()
    schema = args.schema or root / "build/config-docs/schema.json"
    try:
        return generate(root, read_json(schema), args.check)
    except (OSError, ValueError, KeyError, TypeError) as failure:
        print(f"[错误] {failure}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
