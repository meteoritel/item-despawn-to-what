"""校验入口的结构化结果、终端呈现与退出码。"""
from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

from .repo import repo_root


@dataclass(frozen=True)
class Finding:
    """规则发现的一个问题；位置可指向文件、目录或 jar 条目。"""
    location: str
    rule: str
    detail: str
    line: int | None = None

    def render(self) -> str:
        """保留调用者可直接定位的路径、行号与规则名。"""
        location = self.location + (f":{self.line}" if self.line is not None else "")
        return f"  {location} [{self.rule}] {self.detail}"


@dataclass
class CheckResult:
    """规则结果与扫描摘要；没有终端副作用。"""
    findings: list[Finding] = field(default_factory=list)
    summaries: list[str] = field(default_factory=list)
    success: str = "检查通过：0 处违规。"
    repair: str = ""


def configure_console() -> None:
    """中文诊断以 UTF-8 输出，管道与 Windows 终端保持同一编码。"""
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")


def render(result: CheckResult) -> int:
    """渲染统一校验结果，违规返回 1，通过返回 0。"""
    for summary in result.summaries:
        print(f"[信息] {summary}")
    if result.findings:
        print(f"[违规] 共 {len(result.findings)} 处：")
        for finding in result.findings:
            print(finding.render())
        if result.repair:
            print("修正方式：" + result.repair)
        return 1
    print("[通过] " + result.success)
    return 0


def execute(operation: Callable[[], CheckResult]) -> int:
    """运行已解析参数的校验；必需输入或配置错误返回 2。"""
    configure_console()
    try:
        return render(operation())
    except (OSError, ValueError) as failure:
        print(f"[配置错误] {failure}", file=sys.stderr)
        return 2


def run(checker: Callable[[Path], CheckResult], description: str) -> int:
    """标准 --root 校验入口；规则函数只接收根目录并返回结果。"""
    configure_console()
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument("--root", help="仓库根目录；默认从工具位置定位")
    args = parser.parse_args()
    return execute(lambda: checker(repo_root(args.root)))
