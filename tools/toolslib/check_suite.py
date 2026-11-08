"""源码检查的固定执行清单；构建产物由 Gradle 专用门禁准备和校验。"""
import argparse
from pathlib import Path
import subprocess
import sys
from time import perf_counter

from .checks import configure_console
from .repo import repo_root

CHECKS = (
    ("docs", "check-docs.py", ()),
    ("isolation", "check-isolation.py", ()),
    ("i18n", "check-i18n.py", ()),
    ("ui-kit", "check-ui-kit-boundaries.py", ()),
    ("gui-cases", "gui-probes/gen-case-reference.py", ("--check",)),
    ("probe-catalog", "probe-catalog.py", ("--check-docs",)),
)


def main() -> int:
    """逐项等待检查，首个失败原样返回；选择仅筛选固定清单。"""
    configure_console()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", help="仓库根目录；默认从工具位置定位")
    parser.add_argument("--only", action="append", choices=[entry[0] for entry in CHECKS],
                        help="只执行指定检查，可重复；仍按固定顺序执行")
    args = parser.parse_args()
    try:
        root = repo_root(args.root)
        tools = Path(__file__).resolve().parents[1]
        selected = set(args.only) if args.only else {entry[0] for entry in CHECKS}
        for name, script, options in CHECKS:
            if name not in selected:
                continue
            print(f"[开始] {name}", flush=True)
            started = perf_counter()
            result = subprocess.run(
                [sys.executable, "-u", "-X", "utf8", str(tools / script), "--root", str(root), *options],
                cwd=root, shell=False,
            )
            print(f"[结果] {name} exit={result.returncode} 耗时={perf_counter() - started:.2f}s", flush=True)
            if result.returncode:
                print(f"[失败] {name}；后续检查未执行。", flush=True)
                return result.returncode
    except (OSError, ValueError) as failure:
        print(f"[配置错误] {failure}", file=sys.stderr)
        return 2
    print("[通过] 所选源码机械校验全部通过。", flush=True)
    return 0
