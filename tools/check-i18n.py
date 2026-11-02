#!/usr/bin/env python3
"""语言文件 key 同步校验。

含 en_us.json 或 zh_cn.json 的每个 assets/<命名空间>/lang/ 目录必须两者齐全，且顶层
key 集合完全一致；任一侧缺少文件或存在独有 key 即违规。不含这两种语言文件的 lang
目录不在校验范围内。

退出码：0 = 通过；1 = 存在违规；2 = 配置错误（未找到待校验的语言目录）。
用法：python tools/check-i18n.py [--root <仓库根目录>]
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

from toolslib.checks import CheckResult, Finding, run
from toolslib.repo import collect_files

LOCALES = ("en_us", "zh_cn")


def collect_lang_dirs(root: Path) -> dict[str, dict[str, str]]:
    """收集 assets/<命名空间>/lang 目录到 locale -> 相对路径的映射。"""
    groups: dict[str, dict[str, str]] = {}
    for path in collect_files(root, ".json"):
        rel = path.relative_to(root).as_posix()
        parts = rel.split("/")
        if len(parts) >= 4 and parts[-2] == "lang" and parts[-4] == "assets":
            groups.setdefault("/".join(parts[:-1]), {})[parts[-1][:-len(".json")]] = rel
    return groups


def load_keys(path: Path) -> set[str]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError("顶层不是 JSON 对象")
    return set(data.keys())


def check(root: Path) -> CheckResult:
    groups = collect_lang_dirs(root)
    targets = {
        directory: files
        for directory, files in groups.items()
        if any(locale in files for locale in LOCALES)
    }
    if not targets:
        raise ValueError("未找到语言文件：" + str(root) + "/**/assets/*/lang/*.json")

    violations: list[tuple[str, str, str]] = []
    key_counts: list[str] = []
    for directory in sorted(targets):
        files = targets[directory]
        missing = [locale for locale in LOCALES if locale not in files]
        if missing:
            for locale in missing:
                violations.append((directory, "缺文件", "缺少 " + locale + ".json"))
            continue
        keys = {}
        for locale in LOCALES:
            try:
                keys[locale] = load_keys(root / files[locale])
                key_counts.append(locale + " " + str(len(keys[locale])) + " key")
            except ValueError as failure:
                violations.append((files[locale], "非法语言文件", str(failure)))
        if len(keys) != len(LOCALES):
            continue
        for locale in LOCALES:
            other = LOCALES[0] if locale == LOCALES[1] else LOCALES[1]
            only_here = sorted(keys[locale] - keys[other])
            if only_here:
                violations.append((
                    files[locale],
                    "key 不一致",
                    "仅 " + locale + " 有 " + str(len(only_here)) + " 个：" + "、".join(only_here),
                ))

    return CheckResult(
        [Finding(location, kind, detail) for location, kind, detail in violations],
        ["语言目录：" + str(len(targets)) + " 个"]
        + (["key 数量：" + "；".join(key_counts)] if key_counts else []),
        "语言文件 key 同步检查通过：0 处违规。",
        "把缺失的 key 与文案补齐到另一语言文件，保持两套语言文件的 key 集合一致。",
    )



def main() -> int:
    return run(check, '语言文件 key 同步校验')

if __name__ == "__main__":
    sys.exit(main())
