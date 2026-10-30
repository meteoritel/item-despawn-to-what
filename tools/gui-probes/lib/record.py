"""GUI 运行记录持有判定与原始证据，HTML 和拼图只消费其投影。"""
import json
from pathlib import Path

FILES = {"report": "gui-report.html", "summary": "gui-summary.json",
         "record": "gui-run.json", "metadata": "run-metadata.json",
         "sheet": "gui-contact-sheet.png", "visual": "visual-summary.md"}


def case_verdict(checks, error=None):
    if error:
        return "error"
    if not checks:
        return "skip"
    return "pass" if all(check.get("pass") is True for check in checks) else "fail"


class RunRecord:
    def __init__(self, directory, metadata, env, cases, metrics, setup_error=None):
        self.directory = Path(directory)
        self.metadata, self.env, self.cases, self.metrics = metadata, env, cases, metrics
        self.setup_error = setup_error
        self.artifact_errors = []
        self.derived = {}

    def screenshots(self):
        return [artifact for case in self.cases for artifact in case["artifacts"]
                if artifact["kind"] == "screenshot"]

    def summary(self):
        counts = {key: 0 for key in ("passed", "warned", "failed", "skipped", "errors")}
        keys = {"pass": "passed", "fail": "failed", "error": "errors", "skip": "skipped"}
        rows, total, passed = [], 0, 0
        for case in self.cases:
            verdict = case["verdict"]
            counts[keys[verdict]] += 1
            checks = case["checks"]
            good = sum(check.get("pass") is True for check in checks)
            total += len(checks)
            passed += good
            rows.append({"name": case["name"], "verdict": verdict, "passed_checks": good, "total_checks": len(checks)})
        verdict = "pass" if self.cases and not self.setup_error and all(c["verdict"] == "pass" for c in self.cases) else "fail"
        return {"verdict": verdict, **counts, "total_cases": len(rows), "total_checks": total,
                "passed_checks": passed, "cases": rows, "setup_error": self.setup_error,
                "artifact_errors": self.artifact_errors, "metrics": self.metrics,
                "duration_seconds": round(self.metrics["total_ms"] / 1000, 1),
                "outputs": {key: str(self.directory / name) for key, name in FILES.items()},
                "shots": {"count": len(self.screenshots()), "contact_sheet": self.derived.get("contact_sheet"),
                          "list": self.derived.get("list_path")}}

    def save(self):
        value = {"format": "idtw-gui-run-v1", "metadata": self.metadata, "environment": self.env,
                 "cases": self.cases, "summary": self.summary()}
        for role, data in (("record", value), ("summary", value["summary"]),
                           ("metadata", {"metadata": self.metadata, "env": self.env, "metrics": self.metrics})):
            (self.directory / FILES[role]).write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
