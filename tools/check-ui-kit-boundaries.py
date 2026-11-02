#!/usr/bin/env python3
"""检查 UI kit 的原始文本依赖边界与逐目录公开角色登记。"""
from __future__ import annotations

import argparse
import os
import re
from pathlib import Path

from java_references import boundary_lines
from toolslib.checks import CheckResult, Finding, configure_console, execute
from toolslib.repo import repo_root

KIT_PATH = "common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit"
KIT_PACKAGE = "com.meteorite.itemdespawntowhat.client.ui.kit"
ALLOWED_PREFIXES = ("java.", "javax.", "net.minecraft.", "org.jetbrains.", "org.joml.", "org.lwjgl.")
ALLOWED_CLASSES = frozenset("com.mojang.blaze3d." + name for name in (
    "platform.Lighting", "vertex.PoseStack", "vertex.VertexConsumer", "systems.RenderSystem",
    "vertex.BufferBuilder", "vertex.ByteBufferBuilder", "vertex.DefaultVertexFormat", "vertex.MeshData",
    "vertex.VertexBuffer", "vertex.VertexFormat", "vertex.VertexSorting"))
ALLOWED_CLASS_KEYS = frozenset(name.casefold() for name in ALLOWED_CLASSES)
FORBIDDEN_PREFIXES = ("net.fabricmc.", "net.neoforged.", "com.google.gson.")
FQ_PREFIXES = ("com.meteorite.", "com.mojang.blaze3d.", *FORBIDDEN_PREFIXES)
ROLE = re.compile(r"<li>\{@code ([A-Za-z_][A-Za-z0-9_]*)\}: (host|library|example)</li>")


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
    directories = dict.fromkeys(path.parent for path in files)
    imports = 0
    for path in files:
        # 允许显式 kit 路径位于仓库之外，位置仍以相对根路径表示。
        relative = Path(os.path.relpath(path, root)).as_posix()
        for line in boundary_lines(path.read_text(encoding="utf-8"), prefixes=FQ_PREFIXES, raw=True):
            if line.public_type:
                public_types.append((path.parent, line.public_type))
            if line.imported:
                imports += 1
            for target in (line.imported,) if line.imported else line.qualified:
                if path.parent == kit and target.startswith(KIT_PACKAGE + ".preview."):
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
            if key not in public_types:
                result.findings.append(Finding(label, "公开面", "过期登记"))
    unregistered = 0
    for directory, name in public_types:
        if (str(directory).casefold(), name.casefold()) not in roles:
            unregistered += 1
            result.findings.append(Finding(str(directory) + "|" + name, "公开面", "角色缺失"))
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
