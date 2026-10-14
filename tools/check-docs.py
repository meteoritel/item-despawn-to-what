#!/usr/bin/env python3
"""文档链接与禁用路径校验。

扫描仓库内所有 Markdown 文档，检查两类问题：
1) 相对链接必须可解析（悬空链接即违规）：直接命中目标文件，或补 `.md` 后命中
   （`docs/wiki/` 的页间链接是 GitHub Wiki 页名写法，不带扩展名）；
2) 现役文档不得出现禁用路径（归档路径：.local/archive、docs/archive）。

退出码：0 = 通过；1 = 存在违规；2 = 配置错误（未找到 Markdown 文档）。
用法：python tools/check-docs.py [--root <仓库根目录>]
"""
from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path

EXCLUDED_DIRS = {
    "build", ".gradle", ".git", ".local", ".scratch", "node_modules",
    "run", "runs", "logs", ".idea",
}
FORBIDDEN_PATHS = (".local/archive", "docs/archive")
LINK_RE = re.compile(r"\[[^\]]*\]\(([^)\s]+)\)")
EXTERNAL_PREFIXES = ("http://", "https://", "mailto:", "#", "tel:")
FENCE = chr(96) * 3


def collect_docs(root: Path) -> list[Path]:
    docs: list[Path] = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in EXCLUDED_DIRS]
        for name in filenames:
            if name.endswith(".md"):
                docs.append(Path(dirpath) / name)
    return sorted(docs)


def link_exists(base: Path, clean: str) -> bool:
    """判断相对链接目标是否存在。

    GitHub Wiki 的页间链接写成不带扩展名的页面名，因此直接命中失败时再补 `.md` 试一次。
    """
    if (base / clean).resolve().exists():
        return True
    if clean.endswith(".md"):
        return False
    return (base / f"{clean}.md").resolve().exists()


def check(root: Path) -> int:
    docs = collect_docs(root)
    if not docs:
        print(f"[配置错误] 未找到任何 Markdown 文档：{root}")
        return 2

    violations: list[tuple[str, int, str, str]] = []
    link_total = 0
    for doc in docs:
        rel = doc.relative_to(root).as_posix()
        text = doc.read_text(encoding="utf-8", errors="replace")
        in_fence = False
        for line_no, line in enumerate(text.splitlines(), 1):
            if line.lstrip().startswith(FENCE):
                in_fence = not in_fence
                continue
            if in_fence:
                continue
            for forbidden in FORBIDDEN_PATHS:
                if forbidden in line:
                    violations.append((rel, line_no, "禁用路径", forbidden))
            for match in LINK_RE.finditer(line):
                target = match.group(1).strip()
                if not target or target.startswith(EXTERNAL_PREFIXES):
                    continue
                clean = target.split("#", 1)[0]
                if not clean:
                    continue
                link_total += 1
                if not link_exists(doc.parent, clean):
                    violations.append((rel, line_no, "悬空链接", target))

    print(f"[信息] 扫描文档：{len(docs)} 个，相对链接：{link_total} 条")
    if violations:
        print(f"[违规] 共 {len(violations)} 处：")
        for rel, line_no, kind, detail in violations:
            print(f"  {rel}:{line_no} [{kind}] {detail}")
        print("修正方式：改指现行文档、删除失效指针，或把已过期文档移出仓库（归档目录不得被现役文档引用）。")
        return 1

    print("[通过] 文档链接与禁用路径检查通过：0 处违规。")
    return 0


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description="文档链接与禁用路径校验")
    parser.add_argument("--root", default=None, help="仓库根目录；默认取本脚本的上一级目录")
    args = parser.parse_args()
    root = Path(args.root).resolve() if args.root else Path(__file__).resolve().parent.parent
    if not root.is_dir():
        print(f"[配置错误] 根目录不存在：{root}")
        return 2
    return check(root)


if __name__ == "__main__":
    sys.exit(main())
