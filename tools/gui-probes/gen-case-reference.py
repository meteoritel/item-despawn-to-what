#!/usr/bin/env python3
"""从 GUI 用例声明生成或只读检查指南受管块，同时离线校验现有用例。"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
from client import REPO_ROOT  # noqa: E402
from case_contract import document  # noqa: E402
from run import load_cases  # noqa: E402
from toolslib.checks import CheckResult, Finding, execute  # noqa: E402

START = "<!-- gui-case-contract:start -->"
END = "<!-- gui-case-contract:end -->"


def generate(check_only):
    load_cases(REPO_ROOT / "tools/gui-probes/cases", [])
    path = REPO_ROOT / "docs/guides/gui-probes.md"
    original = path.read_bytes().decode("utf-8")
    if original.count(START) != 1 or original.count(END) != 1:
        raise ValueError("GUI 指南缺少唯一契约受管块")
    before, remaining = original.split(START)
    _, after = remaining.split(END)
    newline = "\r\n" if "\r\n" in original else "\n"
    expected = before + START + newline + document().replace("\n", newline) + END + after
    result = CheckResult(success="GUI 用例、handler 声明与指南契约一致。")
    if expected != original:
        if check_only:
            result.findings.append(Finding("docs/guides/gui-probes.md", "GUI 契约", "受管块过期；运行 python tools/gui-probes/gen-case-reference.py"))
        else:
            path.write_bytes(expected.encode("utf-8"))
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="只读检查，不改文件")
    args = parser.parse_args()
    raise SystemExit(execute(lambda: generate(args.check)))
