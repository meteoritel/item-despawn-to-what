"""校验共用序列声明，列出可选计划及所有场景的覆盖归属。"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "common/src/dev/resources/idtw-debug"


def catalog():
    root = json.loads((ROOT / "sequences/catalog.json").read_text(encoding="utf-8"))
    if root["schema_version"] != 1:
        raise ValueError("unsupported sequence version")
    entries = {}
    scenes = set(json.loads((ROOT / "scenarios/manifest.json").read_text(encoding="utf-8")))
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
    args = parser.parse_args()
    entries, scenes, coverage = catalog()
    if args.select and args.select not in entries and args.select not in scenes:
        parser.error("unknown sequence/scenario: " + args.select)
    if args.write_docs:
        path = ROOT.parents[4] / "docs/guides/backend-probes.md"
        start, end = "<!-- probe-catalog:start -->", "<!-- probe-catalog:end -->"
        lines = [start, "| 序列 | 用途 | 直接步骤 |", "|---|---|---|"]
        for key, entry in entries.items():
            steps = ["sequence:" + step["sequence"] if "sequence" in step else step["kind"] + ":" + step["id"] for step in entry["steps"]]
            lines.append("| " + key + " | " + entry["description"] + " | " + ", ".join(steps) + " |")
        lines += [end]
        body = path.read_text(encoding="utf-8")
        if start in body:
            a, b = body.index(start), body.index(end) + len(end)
            body = body[:a] + "\n".join(lines) + body[b:]
        else:
            body += "\n## 序列目录\n\n" + "\n".join(lines) + "\n"
        path.write_text(body, encoding="utf-8")
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
    raise SystemExit(main())
