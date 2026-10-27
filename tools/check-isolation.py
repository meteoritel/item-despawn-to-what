#!/usr/bin/env python3
"""依赖方向校验：平台隔离与端隔离。

扫描仓库内所有 Java 源文件，按 import 语句判定依赖违规：

1) 平台隔离：加载器 API（net.fabricmc.*、net.neoforged.*）只允许出现在平台源集
   fabric/ 与 neoforge/ 中；
2) 跨加载器依赖：fabric/ 不得 import net.neoforged.*，neoforge/ 不得 import
   net.fabricmc.*；
3) 端隔离：客户端类只允许出现在客户端路径，即路径含 /client/ 的源码；其它路径不得
   import net.minecraft.client.* 与本模组 .client. 包；
4) 开发源集隔离：src/main 不得 import core.debug 与 probe 实现包。
5) 编辑业务隔离：core/service 与 core/catalog 不得 import core/network、
   CustomPacketPayload 或网络 codec；语义模型由业务包持有。
6) 编辑注册入口遍历 RuleEditChannels，平台 adapter 仅使用声明中的类型和处理器。
7) 契约只依赖自身，模型只依赖契约与自身；类型装配器位于 core/type。
8) client/edit 只依赖 UI kit 的公开契约，表单控件留在 client/ui。

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
IMPORT_RE = re.compile(r"^\s*import\s+(?:static\s+)?([A-Za-z_][A-Za-z0-9_.]*(?:\*)?)\s*;")
PROJECT_CORE = "com.meteorite.itemdespawntowhat.core."
PROJECT_UI = "com.meteorite.itemdespawntowhat.client.ui."
REGISTRAR_DIRECTIONS = {
    "fabric/src/main/java/com/meteorite/itemdespawntowhat/network/registrar/RuleEditPayloadRegistrar.java": ("serverbound", "clientbound"),
    "fabric/src/main/java/com/meteorite/itemdespawntowhat/client/network/FabricRuleEditClientRegistrar.java": ("clientbound",),
    "neoforge/src/main/java/com/meteorite/itemdespawntowhat/network/registrar/RuleEditPayloadRegistrar.java": ("serverbound", "clientbound"),
}


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
        if rel.endswith("/core/service/BuiltinTypeRegistries.java"):
            violations.append((rel, 1, "类型装配归属", "内置类型装配位于 core/type"))
        if rel in REGISTRAR_DIRECTIONS:
            for direction in REGISTRAR_DIRECTIONS[rel]:
                if not re.search(r"RuleEditChannels\." + direction + r"\(\)\.forEach\(", text):
                    violations.append((rel, 1, "通道声明消费", "注册入口须遍历 RuleEditChannels." + direction + "()"))
            for member in ("type", "codec", "handler"):
                if "channel." + member + "()" not in text:
                    # Fabric 公共入口只注册 S2C 类型，客户端入口仅消费处理器；各自核对实际负责的能力。
                    if member == "codec" and "/client/" in rel:
                        continue
                    if member == "handler" and "binding.deliver(channel, payload)" in text:
                        continue
                    violations.append((rel, 1, "通道声明消费", "adapter 须消费 channel." + member + "()"))
            for line_no, line in enumerate(text.splitlines(), 1):
                if re.search(r"\b\w*Payload\.(?:TYPE|STREAM_CODEC|CHANGE_SET|SNAPSHOT|SOURCE_VIEW)\b", line):
                    violations.append((rel, line_no, "通道声明消费", "载荷类型与 codec 统一在 RuleEditChannels 声明"))
        for line_no, line in enumerate(text.splitlines(), 1):
            match = IMPORT_RE.match(line)
            if not match:
                continue
            target = match.group(1)
            import_total += 1
            if "/core/api/" in rel and target.startswith(PROJECT_CORE) and not target.startswith(PROJECT_CORE + "api."):
                violations.append((rel, line_no, "契约依赖方向", target))
            if "/core/model/" in rel and target.startswith(PROJECT_CORE) and not target.startswith((
                    PROJECT_CORE + "api.", PROJECT_CORE + "model.")):
                violations.append((rel, line_no, "模型依赖方向", target))
            if "/client/edit/" in rel and target.startswith(PROJECT_UI) and not target.startswith(PROJECT_UI + "kit."):
                violations.append((rel, line_no, "编辑数据分层", target))
            if target == PROJECT_CORE + "service.BuiltinTypeRegistries":
                violations.append((rel, line_no, "类型装配归属", target))
            if ("/core/service/" in rel or "/core/catalog/" in rel) and target.startswith((
                    "com.meteorite.itemdespawntowhat.core.network.",
                    "net.minecraft.network.protocol.common.custom.",
                    "net.minecraft.network.codec.")):
                violations.append((rel, line_no, "编辑业务隔离", target))
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
        print("修正方式：平台 API 回到对应源集，客户端依赖回到客户端路径；编辑业务返回语义模型，由 transport 完成载荷映射。")
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
