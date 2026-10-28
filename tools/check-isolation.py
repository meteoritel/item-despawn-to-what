#!/usr/bin/env python3
"""校验平台、端、开发源集与业务依赖方向，以及编辑注册和写权限结构。

静态引用覆盖 import、全限定名和按源码索引可确定的同包类型；忽略注释及字面量。
退出码：0 通过；1 存在违规；2 仓库配置错误。
"""
from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path

from java_references import Source, TypeIndex

EXCLUDED_DIRS = {
    "build", ".gradle", ".git", ".local", ".scratch", "node_modules",
    "run", "runs", "logs", ".idea",
}
# 平台源集到各自加载器 API 前缀的映射：加载器 API 只允许出现在这里
PLATFORM_LOADERS = {"fabric": "net.fabricmc.", "neoforge": "net.neoforged."}
# 判定为客户端类的 import 前缀
CLIENT_IMPORT_PREFIXES = ("net.minecraft.client.", "com.meteorite.itemdespawntowhat.client.")
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
    parsed = [Source.read(source, root) for source in sources]
    index = TypeIndex(parsed)
    by_path = {source.path: source for source in parsed}
    import_total = sum(len(source.imports) for source in parsed)
    modules: set[str] = set()
    for source in sources:
        rel = source.relative_to(root).as_posix()
        module = rel.split("/", 1)[0] if "/" in rel else ""
        modules.add(module)
        client_path = is_client_path(rel)
        entry = by_path[source]
        code = entry.code
        if rel.endswith("/core/service/BuiltinTypeRegistries.java"):
            violations.append((rel, 1, "类型装配归属", "内置类型装配位于 core/type"))
        if rel in REGISTRAR_DIRECTIONS:
            for direction in REGISTRAR_DIRECTIONS[rel]:
                if not re.search(r"RuleEditChannels\s*\.\s*" + direction + r"\s*\(\s*\)", code):
                    violations.append((rel, 1, "通道声明消费", "注册入口须遍历 RuleEditChannels." + direction + "()"))
            for member in ("type", "codec", "handler"):
                if not re.search(r"\.\s*" + member + r"\s*\(\s*\)", code):
                    # Fabric 公共入口只注册 S2C 类型，客户端入口仅消费处理器；各自核对实际负责的能力。
                    if member == "codec" and "/client/" in rel:
                        continue
                    if member == "handler" and re.search(r"\.\s*deliver\s*\(", code):
                        continue
                    violations.append((rel, 1, "通道声明消费", "adapter 须消费 channel." + member + "()"))
            for line_no, line in enumerate(code.splitlines(), 1):
                if re.search(r"\b\w*Payload\.(?:TYPE|STREAM_CODEC|CHANGE_SET|SNAPSHOT|SOURCE_VIEW)\b", line):
                    violations.append((rel, line_no, "通道声明消费", "载荷类型与 codec 统一在 RuleEditChannels 声明"))
        # screen 负责组装表单，不再声明业务字段；form 的数值子控件适配另有职责。
        if "/client/ui/screen/" in rel and source.parent.name == "screen":
            for match in re.finditer(r"\bEditorField\.\s*\w+\s*\(", code):
                violations.append((rel, code.count("\n", 0, match.start()) + 1, "业务字段归属", "固定字段声明由 client/edit 提供"))
        # 编辑页面依赖装配产物与明确协作者，字段配置使用句柄，活草稿仅在会话回调内提供。
        if "/client/" in rel:
            if source.name in {"RuleEditorInfoSection.java", "RuleEditorInputSection.java",
                               "RuleEditorTriggerSection.java", "RuleEditorResultsSection.java"}:
                for match in re.finditer(r"\bRuleEditorEditSections\s+\w+", code):
                    violations.append((rel, code.count("\n", 0, match.start()) + 1,
                                       "分区依赖方向", "分区只依赖明确协作者，不持有组合容器"))
            if "/client/ui/screen/" in rel:
                for match in re.finditer(r"\.draft\s*\(", code):
                    violations.append((rel, code.count("\n", 0, match.start()) + 1,
                                       "会话写权限", "只读查询使用会话值，写入使用 apply 回调"))
            if source.name == "EditSession.java":
                for match in re.finditer(r"\b(?:public|protected)\s+RuleDraft\s+\w+\s*\(", code):
                    violations.append((rel, code.count("\n", 0, match.start()) + 1,
                                       "会话写权限", "会话不公开活草稿 getter"))
            if source.name == "FormView.java":
                old_config = (r"(?:setRowAction|addRowAction|setRowHeight|hideRowLabel|setRowVisible|"
                              r"pairItemCountRows|setRowVisibility|suppressRowTooltip|suppressRowHintTooltip|"
                              r"setRowDefaultValue|setRowSliderWindow|setItemCountReferences|setCatalogFilter|"
                              r"catalogFilter|pendingValue)")
                for match in re.finditer(r"\bpublic\s+(?:@Nullable\s+)?[\w<>?]+\s+" + old_config + r"\s*\(String\s", code):
                    violations.append((rel, code.count("\n", 0, match.start()) + 1,
                                       "字段行身份", "行配置通过 FormField，不使用路径或字段名寻址"))
        for line_no, target in index.references(entry):
            if entry.source_set == "main" and index.development_only(entry, target):
                violations.append((rel, line_no, "开发源集类型归属", target))
            if "/core/load/" in rel and target.startswith(PROJECT_CORE + "model."):
                violations.append((rel, line_no, "加载层依赖方向", target))
            lower = ("api", "config", "load", "model", "registry", "extension", "type", "runtime", "state", "service", "catalog")
            if any("/core/" + package + "/" in rel for package in lower):
                if target.startswith(tuple(PROJECT_CORE + package + "." for package in ("command", "debug", "network", "host"))):
                    violations.append((rel, line_no, "使用方与宿主方向", target))
            if "/core/api/" in rel and target.startswith(PROJECT_CORE) and not target.startswith(PROJECT_CORE + "api."):
                violations.append((rel, line_no, "契约依赖方向", target))
            if "/core/model/" in rel and target.startswith(PROJECT_CORE) and not target.startswith((
                    PROJECT_CORE + "api.", PROJECT_CORE + "model.")):
                violations.append((rel, line_no, "模型依赖方向", target))
            if "/client/edit/" in rel and target.startswith(PROJECT_UI) and not target.startswith(PROJECT_UI + "kit."):
                violations.append((rel, line_no, "编辑数据分层", target))
            if "/client/net/" in rel and target.startswith((
                    "com.meteorite.itemdespawntowhat.client.edit.", PROJECT_UI)):
                violations.append((rel, line_no, "工作区依赖方向", target))
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
