#!/usr/bin/env python3
"""从 GUI 用例声明生成或只读检查指南受管块，同时离线校验现有用例。"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
from case_contract import document  # noqa: E402
from case_files import load_cases  # noqa: E402
from toolslib.checks import CheckResult, Finding, execute  # noqa: E402
from toolslib.repo import repo_root  # noqa: E402

START = "<!-- gui-case-contract:start -->"
END = "<!-- gui-case-contract:end -->"


def generate(root: Path, check_only: bool) -> CheckResult:
    load_cases(root, root / "tools/gui-probes/cases", [])
    path = root / "docs/guides/gui-probes.md"
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


def main() -> int:
    """显式输入根目录用于离线校验，默认从工具位置定位仓库。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", help="仓库根目录；默认从工具位置定位")
    parser.add_argument("--check", action="store_true", help="只读检查，不改文件")
    args = parser.parse_args()
    return execute(lambda: generate(repo_root(args.root), args.check))


if __name__ == "__main__":
    raise SystemExit(main())
