#!/usr/bin/env python3
"""依赖方向校验：平台隔离与端隔离。

扫描仓库内所有 Java 源文件，按 import 语句判定四类违规：

1) 平台隔离：加载器 API（net.fabricmc.*、net.neoforged.*）只允许出现在平台源集
   fabric/ 与 neoforge/ 中；
2) 跨加载器依赖：fabric/ 不得 import net.neoforged.*，neoforge/ 不得 import
   net.fabricmc.*；
3) 端隔离：客户端类只允许出现在客户端路径，即路径含 /client/ 的源码；其它路径不得
   import net.minecraft.client.* 与本模组 .client. 包；
4) 开发源集隔离：src/main 不得 import core.debug 与 probe 实现包。

只判定 import 语句：注释与 javadoc 中的全限定名不构成编译期依赖，不计入违规。

退出码：0 = 通过；1 = 存在违规；2 = 配置错误（未找到 Java 源文件）。
用法：python tools/check-isolation.py [--root <仓库根目录>]
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
# 平台源集到各自加载器 API 前缀的映射：加载器 API 只允许出现在这里
PLATFORM_LOADERS = {"fabric": "net.fabricmc.", "neoforge": "net.neoforged."}
# 判定为客户端类的 import 前缀
CLIENT_IMPORT_PREFIXES = ("net.minecraft.client.", "com.meteorite.itemdespawntowhat.client.")
IMPORT_RE = re.compile(r"^\s*import\s+(?:static\s+)?([A-Za-z_][A-Za-z0-9_.]*)\s*;")


def collect_sources(root: Path) -> list[Path]:
    sources: list[Path] = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in EXCLUDED_DIRS]
        for name in filenames:
            if name.endswith(".java"):
                sources.append(Path(dirpath) / name)
    return sorted(sources)


def is_client_path(rel: str) -> bool:
    """判断源文件是否属于客户端侧：路径含 /client/。"""
    return "/client/" in "/" + rel


def check(root: Path) -> int:
    sources = collect_sources(root)
    if not sources:
        print("[配置错误] 未找到任何 Java 源文件：" + str(root))
        return 2

    violations: list[tuple[str, int, str, str]] = []
    import_total = 0
    modules: set[str] = set()
    for source in sources:
        rel = source.relative_to(root).as_posix()
        module = rel.split("/", 1)[0] if "/" in rel else ""
        modules.add(module)
        client_path = is_client_path(rel)
        text = source.read_text(encoding="utf-8", errors="replace")
        for line_no, line in enumerate(text.splitlines(), 1):
            match = IMPORT_RE.match(line)
            if not match:
                continue
            target = match.group(1)
            import_total += 1
            if '/src/main/' in rel and ('.core.debug.' in target or '.probe.' in target):
                violations.append((rel, line_no, "开发源集隔离", target))
            loader = None
            for name, prefix in PLATFORM_LOADERS.items():
                if target.startswith(prefix):
                    loader = name
                    break
            if loader is not None:
                if module not in PLATFORM_LOADERS:
                    violations.append((rel, line_no, "平台隔离", target))
                elif module != loader:
                    violations.append((rel, line_no, "跨加载器依赖", target))
                continue
            if target.startswith(CLIENT_IMPORT_PREFIXES) and not client_path:
                violations.append((rel, line_no, "端隔离", target))

    print("[信息] 扫描文件：" + str(len(sources)) + " 个，import：" + str(import_total) + " 条")
    print("[信息] 顶层模块：" + "、".join(sorted(m for m in modules if m)))
    if violations:
        print("[违规] 共 " + str(len(violations)) + " 处：")
        for rel, line_no, rule, detail in violations:
            print("  " + rel + ":" + str(line_no) + " [" + rule + "] " + detail)
        print("修正方式：平台 API 回到 fabric/ 与 neoforge/，客户端依赖回到客户端路径，或改为经窄接口与静态 sink 注入。")
        return 1

    print("[通过] 依赖方向检查通过：0 处违规。")
    return 0


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description="依赖方向校验：平台隔离与端隔离")
    parser.add_argument("--root", default=None, help="仓库根目录；默认取本脚本的上一级目录")
    args = parser.parse_args()
    root = Path(args.root).resolve() if args.root else Path(__file__).resolve().parent.parent
    if not root.is_dir():
        print("[配置错误] 根目录不存在：" + str(root))
        return 2
    return check(root)


if __name__ == "__main__":
    sys.exit(main())
