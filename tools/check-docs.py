#!/usr/bin/env python3
"""文档链接与禁用路径校验。

扫描仓库内所有 Markdown 文档，检查三类问题：
1) 相对链接必须可解析（悬空链接即违规）：直接命中目标文件，或补 `.md` 后命中
   （`docs/wiki/` 的页间链接是 GitHub Wiki 页名写法，不带扩展名）；
2) 现役文档不得出现禁用路径（归档路径：.local/archive、docs/archive）；
3) 现役文档不得反向指向 AGENTS.md：指针方向只能由 AGENTS.md 单向发出，
   其它文档以链接、路径或依赖方式指回即违规。

退出码：0 = 通过；1 = 存在违规；2 = 配置错误（未找到 Markdown 文档）。
用法：python tools/check-docs.py [--root <仓库根目录>]
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

from toolslib.checks import CheckResult, Finding, run
from toolslib.repo import collect_files

FORBIDDEN_PATHS = (".local/archive", "docs/archive")
# 指针方向只由 AGENTS.md 单向发出：该文件自身豁免，其它文档出现其文件名即违规。
POINTER_ORIGIN = "AGENTS.md"
LINK_RE = re.compile(r"\[[^\]]*\]\(([^)\s]+)\)")
EXTERNAL_PREFIXES = ("http://", "https://", "mailto:", "#", "tel:")
FENCE = chr(96) * 3


def collect_docs(root: Path) -> list[Path]:
    return collect_files(root, ".md")

def link_exists(base: Path, clean: str) -> bool:
    """判断相对链接目标是否存在。

    GitHub Wiki 的页间链接写成不带扩展名的页面名，因此直接命中失败时再补 `.md` 试一次。
    """
    if (base / clean).resolve().exists():
        return True
    if clean.endswith(".md"):
        return False
    return (base / f"{clean}.md").resolve().exists()


def check(root: Path) -> CheckResult:
    docs = collect_docs(root)
    if not docs:
        raise ValueError(f"未找到任何 Markdown 文档：{root}")

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
            if rel != POINTER_ORIGIN and POINTER_ORIGIN in line:
                violations.append((rel, line_no, "反向指针", POINTER_ORIGIN))
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

    return CheckResult(
        [Finding(rel, kind, detail, line) for rel, line, kind, detail in violations],
        [f"扫描文档：{len(docs)} 个，相对链接：{link_total} 条"],
        "文档链接、禁用路径与指针方向检查通过：0 处违规。",
        "改指现行文档、删除失效指针，或把已过期文档移出仓库（归档目录不得被现役文档引用）；"
        "反向指针删除该提及，需要长期保留的规则写进 AGENTS.md 自身。",
    )



def main() -> int:
    return run(check, '文档链接与禁用路径校验')

if __name__ == "__main__":
    sys.exit(main())
