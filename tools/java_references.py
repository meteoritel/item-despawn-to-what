"""Java 静态引用的有限词法识别；按源文件和实际 sourceSet 解析本仓类型。"""
from __future__ import annotations

import re
from bisect import bisect_right
from dataclasses import dataclass
from pathlib import Path

NON_CODE = re.compile(r'"""(?:\\.|(?!""")[\s\S])*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/')
PACKAGE = re.compile(r"\bpackage\s+([\w.]+)\s*;")
IMPORT = re.compile(r"\bimport\s+(?:static\s+)?([\w.]+(?:\*)?)\s*;")
NAME = re.compile(r"\b[A-Za-z_$][\w$]*(?:\s*\.\s*[A-Za-z_$][\w$]*)*")
DECLARATION = re.compile(r"[{}]|\b(?:class|interface|enum|record)\s+([A-Za-z_$][\w$]*)")
PREFIXES = ("com.meteorite.itemdespawntowhat.", "net.fabricmc.", "net.neoforged.", "net.minecraft.")


def code_only(text: str) -> str:
    """保留行号和列位置，将注释与所有 Java 字面量内容替换为空格。"""
    return NON_CODE.sub(lambda match: re.sub(r"[^\n]", " ", match.group()), text)


@dataclass
class Source:
    path: Path
    relative: str
    code: str
    package: str
    module: str
    source_set: str
    imports: list[tuple[int, str]]
    declarations: tuple[str, ...]

    @classmethod
    def read(cls, path: Path, root: Path) -> Source:
        relative = path.relative_to(root).as_posix()
        code = code_only(path.read_text(encoding="utf-8"))
        package_match = PACKAGE.search(code)
        package = package_match.group(1) if package_match else ""
        source_set = re.match(r"(common|fabric|neoforge)/src/([^/]+)/java/", relative)
        module, kind = source_set.groups() if source_set else (relative.split("/", 1)[0], "tool")
        lines = [0] + [match.end() for match in re.finditer("\n", code)]
        imports = [(bisect_right(lines, match.start()), match.group(1)) for match in IMPORT.finditer(code)]
        depth = 0
        declarations = []
        for match in DECLARATION.finditer(code):
            if match.group() == "{":
                depth += 1
            elif match.group() == "}":
                depth -= 1
            elif depth == 0:
                declarations.append(package + "." + match.group(1))
        return cls(path, relative, code, package, module, kind, imports, tuple(declarations))


class TypeIndex:
    """索引本仓顶层类型；嵌套类型、静态成员依赖归到声明它们的顶层类型。"""

    def __init__(self, sources: list[Source]):
        self.types: dict[str, list[Source]] = {}
        for source in sources:
            for name in source.declarations:
                self.types.setdefault(name, []).append(source)

    def owner_name(self, name: str) -> str:
        candidate = name.removesuffix(".*")
        while "." in candidate:
            if candidate in self.types:
                return candidate
            candidate = candidate.rsplit(".", 1)[0]
        return name

    @staticmethod
    def visible(source: Source, target: Source) -> bool:
        if target.module == "common" and target.source_set == "main":
            return True
        if source.module == target.module and target.source_set == "main":
            return True
        if source.source_set in {"dev", "probe"}:
            return (target.module == "common" and target.source_set == "dev") or (
                source.module == target.module and source.source_set == target.source_set)
        return source.source_set == "tool" and target.source_set == "tool"

    def development_only(self, source: Source, target: str) -> bool:
        declarations = self.types.get(self.owner_name(target), [])
        visible = [item for item in declarations if self.visible(source, item)]
        return bool(declarations) and not visible and all(item.source_set != "main" for item in declarations)

    def references(self, source: Source) -> list[tuple[int, str]]:
        references = {(line, self.owner_name(name)) for line, name in source.imports}
        imports = {name.rsplit(".", 1)[-1]: name for _, name in source.imports if not name.endswith(".*")}
        wildcards = [name[:-2] for _, name in source.imports if name.endswith(".*")]
        import_spans = [(match.start(), match.end()) for match in IMPORT.finditer(source.code)]
        package = PACKAGE.search(source.code)
        lines = [0] + [match.end() for match in re.finditer("\n", source.code)]
        for match in NAME.finditer(source.code):
            line = bisect_right(lines, match.start())
            if any(start <= match.start() < end for start, end in import_spans) or (
                    package and package.start() <= match.start() < package.end()):
                continue
            name = re.sub(r"\s+", "", match.group())
            if name.startswith(PREFIXES) or self.owner_name(name) in self.types:
                references.add((line, self.owner_name(name)))
                continue
            first = name.split(".", 1)[0]
            if first in imports:
                references.add((line, self.owner_name(imports[first])))
                continue
            candidates = [source.package + "." + first] + [prefix + "." + first for prefix in wildcards]
            candidates = [candidate for candidate in candidates if candidate in self.types]
            visible = [candidate for candidate in candidates if any(self.visible(source, item) for item in self.types[candidate])]
            for candidate in visible or candidates:
                references.add((line, candidate))
        return sorted(references)
