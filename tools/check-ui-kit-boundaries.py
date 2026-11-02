#!/usr/bin/env python3
"""检查 UI kit 的原始文本依赖边界与逐目录公开角色登记。"""
from __future__ import annotations

import argparse
import os
import re
from dataclasses import replace
from pathlib import Path

from java_references import Source, TypeIndex, boundary_lines
from toolslib.checks import CheckResult, Finding, configure_console, execute
from toolslib.repo import collect_files, repo_root

KIT_PATH = "common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit"
KIT_PACKAGE = "com.meteorite.itemdespawntowhat.client.ui.kit"
API_PACKAGE = KIT_PACKAGE + ".api"
INTERNAL_PACKAGE = KIT_PACKAGE + ".internal"
ALLOWED_PREFIXES = ("java.", "javax.", "net.minecraft.", "org.jetbrains.", "org.joml.", "org.lwjgl.")
ALLOWED_CLASSES = frozenset("com.mojang.blaze3d." + name for name in (
    "platform.Lighting", "vertex.PoseStack", "vertex.VertexConsumer", "systems.RenderSystem",
    "vertex.BufferBuilder", "vertex.ByteBufferBuilder", "vertex.DefaultVertexFormat", "vertex.MeshData",
    "vertex.VertexBuffer", "vertex.VertexFormat", "vertex.VertexSorting"))
ALLOWED_CLASS_KEYS = frozenset(name.casefold() for name in ALLOWED_CLASSES)
FORBIDDEN_PREFIXES = ("net.fabricmc.", "net.neoforged.", "com.google.gson.")
FQ_PREFIXES = ("com.meteorite.", "com.mojang.blaze3d.", *FORBIDDEN_PREFIXES)
ROLE = re.compile(r"<li>\{@code ([A-Za-z_][A-Za-z0-9_]*)\}: (host|library|example)(?:；([^<]*))?</li>")


def source_index(root: Path, kit: Path, files: list[Path]) -> tuple[list[Source], TypeIndex]:
    """共用仓库类型索引；显式外部 kit 路径也纳入引用解析。"""
    sources = [Source.read(path, root) for path in collect_files(root, ".java")]
    known = {source.path for source in sources}
    sources.extend(Source.read(path, kit) for path in files if path not in known)
    return sources, TypeIndex(sources)


def production_consumers(sources: list[Source], index: TypeIndex, kit: Path) -> dict[str, set[str]]:
    """按源码类型引用核对 kit 外 main 消费；dev、probe 与注释不计生产消费。"""
    consumers: dict[str, set[str]] = {}
    for source in sources:
        if (source.path.is_relative_to(kit) or source.source_set != "main"
                or source.module not in {"common", "fabric", "neoforge"}):
            continue
        for _, name in index.references(source):
            consumers.setdefault(name, set()).add(source.relative)
    return consumers


def declaration_type(header: str) -> str:
    """字段初值属于 implementation，参数与注解括号中的内容仍属于声明。"""
    depth = 0
    for position, char in enumerate(header):
        if char in "([":
            depth += 1
        elif char in ")]":
            depth -= 1
        elif char == "=" and depth == 0:
            return header[:position]
    return header


def exported_headers(source: Source):
    """有限词法识别导出声明，包含接口隐式 public、嵌套类型与 record 组件。"""
    stack: list[tuple[str, bool, str]] = []
    start = 0
    for token in re.finditer(r"[{};]", source.code):
        header = source.code[start:token.start()]
        parent_kind, parent_exported, parent_type = stack[-1] if stack else ("root", True, "")
        if token[0] == "}":
            if stack:
                stack.pop()
        elif parent_kind == "body":
            if token[0] == "{":
                stack.append(("body", False, ""))
        else:
            declaration = re.search(r"\b(class|interface|enum|record)\s+[A-Za-z_$][\w$]*", header)
            explicit = re.search(r"\b(public|protected)\b", header) is not None
            implicit = parent_type == "interface" and not re.search(r"\bprivate\b", header)
            exported = parent_exported and (explicit or implicit)
            if exported:
                line = source.code.count(chr(10), 0, start) + 1
                yield line, declaration_type(header)
            if token[0] == "{":
                stack.append(("type", exported, declaration[1]) if declaration else ("body", False, ""))
        start = token.end()


def check_exports(sources: list[Source], index: TypeIndex, kit: Path, result: CheckResult) -> None:
    """宿主只引用 api；api 的父类型和公开/protected 声明保留在导出面内。"""
    for source in sources:
        if not source.path.is_relative_to(kit):
            for line, target in index.references(source):
                if target.startswith(KIT_PACKAGE + ".") and not target.startswith(API_PACKAGE + "."):
                    result.findings.append(Finding(source.relative, "宿主只允许引用 kit.api", target, line))
        elif source.package == API_PACKAGE or source.package.startswith(API_PACKAGE + "."):
            for start_line, header in exported_headers(source):
                declaration = replace(source, code=header, imports=[(0, name) for _, name in source.imports])
                for line, target in index.references(declaration):
                    if line > 0 and target.startswith(INTERNAL_PACKAGE + "."):
                        result.findings.append(Finding(source.relative, "公开声明泄漏 internal 类型", target, start_line + line - 1))


def check(root: Path, kit_path: str = KIT_PATH) -> CheckResult:
    """逐行检查原始文本，公开角色从原始 Javadoc 读取。"""
    kit = Path(kit_path)
    kit = (kit if kit.is_absolute() else root / kit).resolve()
    if not kit.is_dir():
        raise ValueError(f"kit 目录不存在：{kit}")
    files = sorted(kit.rglob("*.java"), key=lambda path: path.name.casefold())
    if not files:
        raise ValueError(f"kit 目录下没有 .java 文件：{kit}")
    result = CheckResult(repair="修正 kit 依赖边界及同目录 package-info.java 的角色登记。")
    public_types = []
    sources, index = source_index(root, kit, files)
    by_path = {source.path: source for source in sources}
    directories = dict.fromkeys(path.parent for path in files)
    imports = 0
    for path in files:
        package = by_path[path].package
        # 允许显式 kit 路径位于仓库之外，位置仍以相对根路径表示。
        relative = Path(os.path.relpath(path, root)).as_posix()
        for line in boundary_lines(path.read_text(encoding="utf-8"), prefixes=FQ_PREFIXES, raw=True):
            if line.public_type and not package.startswith(INTERNAL_PACKAGE + "."):
                public_types.append((path.parent, line.public_type))
                if not package.startswith(API_PACKAGE + "."):
                    result.findings.append(Finding(relative, "公开入口须位于 kit.api 能力包", line.public_type, line.number))
            if line.imported:
                imports += 1
            for target in (line.imported,) if line.imported else line.qualified:
                preview = (API_PACKAGE + ".preview.", INTERNAL_PACKAGE + ".preview.")
                if not (package + ".").startswith(preview) and target.startswith(preview):
                    result.findings.append(Finding(relative, "基础 kit 反向引用 preview", target, line.number))
                if target == KIT_PACKAGE or target.startswith(KIT_PACKAGE + "."):
                    continue
                if line.imported:
                    reason = next(("禁用依赖 " + prefix for prefix in FORBIDDEN_PREFIXES if target.startswith(prefix)), None)
                    if reason is None and target.startswith("com.meteorite."):
                        reason = "本模组非 kit 包"
                    if reason is None and target.casefold() not in ALLOWED_CLASS_KEYS and not target.startswith(ALLOWED_PREFIXES):
                        reason = "白名单外 import"
                    detail = line.text.strip()
                else:
                    if target.casefold() in ALLOWED_CLASS_KEYS:
                        continue
                    reason = "本模组非 kit 包的全限定引用" if target.startswith("com.meteorite.") else "禁用依赖的全限定引用"
                    detail = target
                if reason:
                    result.findings.append(Finding(relative, reason, detail, line.number))
    roles = {}
    notes = {}
    for directory in directories:
        package_info = directory / "package-info.java"
        if not package_info.is_file():
            result.findings.append(Finding(str(directory), "公开面", "缺少 package-info.java"))
            continue
        for match in ROLE.finditer(package_info.read_text(encoding="utf-8")):
            key = (directory, match[1])
            identity = str(directory).casefold(), match[1].casefold()
            label = str(directory) + "|" + match[1]
            if identity in roles:
                result.findings.append(Finding(label, "公开面", "角色重复"))
            else:
                roles[identity] = match[2]
                notes[identity] = match[3] or ""
            if key not in public_types:
                result.findings.append(Finding(label, "公开面", "过期登记"))
    unregistered = 0
    for directory, name in public_types:
        if (str(directory).casefold(), name.casefold()) not in roles:
            unregistered += 1
            result.findings.append(Finding(str(directory) + "|" + name, "公开面", "角色缺失"))
    check_exports(sources, index, kit, result)
    consumers = production_consumers(sources, index, kit)
    for directory, name in public_types:
        identity = str(directory).casefold(), name.casefold()
        role = roles.get(identity)
        package = by_path[directory / (name + ".java")].package
        hosts = consumers.get(package + "." + name, set())
        label = str(directory) + "|" + name
        if role == "host" and not hosts:
            result.findings.append(Finding(label, "消费角色", "host 缺少 kit 外生产源码引用"))
        elif role in {"library", "example"} and hosts:
            result.findings.append(Finding(label, "消费角色", "存在生产消费：" + "、".join(sorted(hosts))))
        if role == "library" and ("用途：" not in notes[identity] or "当前无生产引用" not in notes[identity]):
            result.findings.append(Finding(label, "公开面", "library 须说明用途与当前无生产引用"))
    result.summaries = [f"扫描目录：{kit}", f"扫描文件：{len(files)} 个，import：{imports} 条",
                        f"顶层 public 类型：{len(public_types)} 个，package-info 已登记：{len(public_types) - unregistered} 个"]
    result.success = f"kit 依赖边界检查通过：{len(files)} 个文件，{len(public_types)} 个顶层 public 类型全部登记，0 处违规。"
    return result


def main() -> int:
    """支持公共 --root 与 --kit-path 的显式路径。"""
    configure_console()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root")
    parser.add_argument("--kit-path", default=KIT_PATH)
    args = parser.parse_args()
    return execute(lambda: check(repo_root(args.root), args.kit_path))


if __name__ == "__main__":
    raise SystemExit(main())
