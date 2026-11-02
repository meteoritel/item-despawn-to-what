"""校验共用序列声明，列出可选计划及所有场景的覆盖归属。"""
import argparse
import json
import sys
from pathlib import Path

from toolslib.repo import repo_root
from toolslib.checks import configure_console


class SelectionError(ValueError):
    """用户选择的序列或场景不存在。"""


class CatalogError(ValueError):
    """目录声明自身存在错误。"""


def catalog(root=None):
    resources = repo_root(root) / "common/src/dev/resources/idtw-debug"
    try:
        return _read_catalog(resources)
    except (ValueError, KeyError, TypeError) as failure:
        raise CatalogError(f"{resources}: {failure}") from failure


def _read_catalog(resources):
    root = json.loads((resources / "sequences/catalog.json").read_text(encoding="utf-8"))
    if root["schema_version"] != 1:
        raise ValueError("unsupported sequence version")
    entries = {}
    scenes = set(json.loads((resources / "scenarios/manifest.json").read_text(encoding="utf-8")))
    for entry in root["sequences"]:
        key = entry["id"]
        if key in entries:
            raise ValueError("duplicate sequence: " + key)
        entries[key] = entry
    world = {"damage", "loading", "budget", "state", "save", "restart"}

    def expand(key, visiting=()):
        if key in visiting:
            raise ValueError("sequence cycle: " + " -> ".join((*visiting, key)))
        entry = entries[key]
        if entry["quantity"] not in ("none", "fixed", "ceiling") or not 1 <= entry["rounds"] <= 3:
            raise ValueError("invalid parameters: " + key)
        if not 0 <= entry["default_entities"] <= 1000 or not 0 <= entry["gap_seconds"] <= 30:
            raise ValueError("invalid quantity/gap: " + key)
        if entry.get("restart") != bool(entry.get("resume")) and (entry.get("restart") or entry.get("resume")):
            raise ValueError("restart/resume mismatch: " + key)
        refs = []
        for step in entry["steps"] + entry.get("resume", []):
            if "sequence" in step:
                refs.extend(expand(step["sequence"], (*visiting, key)))
                continue
            kind, name = step["kind"], step["id"]
            if kind == "world":
                if name not in world:
                    raise ValueError("unknown world step: " + name)
            else:
                ref = ("bench/" if kind == "bench" else "run/") + name
                if kind not in ("scene", "bench") or ref not in scenes:
                    raise ValueError("unknown scene: " + ref)
                refs.append(ref)
            if "seconds" in step and not 10 <= step["seconds"] <= 300:
                raise ValueError("duration out of range")
            if "entities" in step and step["entities"] != "$entities" and not 0 <= step["entities"] <= 1000:
                raise ValueError("quantity out of range")
        if len(refs) * entry["rounds"] > 256:
            raise ValueError("expanded plan too large")
        return refs

    coverage = {key: expand(key) for key in entries}
    return entries, scenes, coverage


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--list", action="store_true")
    parser.add_argument("--coverage", action="store_true")
    parser.add_argument("--select")
    parser.add_argument("--write-docs", action="store_true")
    parser.add_argument("--check-docs", action="store_true")
    parser.add_argument("--root", help="显式仓库根目录")
    args = parser.parse_args()
    project = repo_root(args.root)
    entries, scenes, coverage = catalog(project)
    if args.select and args.select not in entries and args.select not in scenes:
        raise SelectionError("unknown sequence/scenario: " + args.select)
    if args.write_docs or args.check_docs:
        path = project / "docs/guides/backend-probes.md"
        start, end = "<!-- probe-catalog:start -->", "<!-- probe-catalog:end -->"
        lines = [start, "| 序列 | 用途 | 直接步骤 |", "|---|---|---|"]
        for key, entry in entries.items():
            steps = ["sequence:" + step["sequence"] if "sequence" in step else step["kind"] + ":" + step["id"] for step in entry["steps"]]
            lines.append("| " + key + " | " + entry["description"] + " | " + ", ".join(steps) + " |")
        lines += [end]
        body = path.read_bytes().decode("utf-8")
        if body.count(start) != 1 or body.count(end) != 1:
            raise CatalogError(f"{path}: 缺少唯一受管块")
        a, b = body.index(start), body.index(end) + len(end)
        if b - len(end) < a:
            raise CatalogError(f"{path}: 受管块结束标记位于开始标记之前")
        newline = "\r\n" if "\r\n" in body else "\n"
        expected = body[:a] + newline.join(lines) + body[b:]
        if expected != body:
            if args.check_docs:
                print("[目录文档过期] 运行 python tools/probe-catalog.py --write-docs", file=sys.stderr)
                return 1
            path.write_bytes(expected.encode("utf-8"))
    if args.list:
        for key, entry in entries.items():
            print(f"{key}: {entry['description']}")
        print("Single scenario: " + ", ".join(sorted(scenes)))
    if args.coverage:
        for scene in sorted(scenes):
            owners = [key for key, refs in coverage.items() if scene in refs]
            print(f"{scene}: {', '.join(owners) or 'manual'}; selectable")
    return 0


if __name__ == "__main__":
    configure_console()
    try:
        code = main()
    except SelectionError as failure:
        print(f"[选择错误] {failure}", file=sys.stderr)
        code = 2
    except CatalogError as failure:
        print(f"[目录内容错误] {failure}", file=sys.stderr)
        code = 1
    except (OSError, ValueError) as failure:
        print(f"[输入错误] {failure}", file=sys.stderr)
        code = 2
    raise SystemExit(code)
