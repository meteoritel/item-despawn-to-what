"""GUI 运行记录拥有判定、指标及产物生命周期；消费者读取不可变快照。"""
from __future__ import annotations

import copy
import json
import time
from dataclasses import dataclass
from pathlib import Path

FILES = {"report": "gui-report.html", "summary": "gui-summary.json",
         "record": "gui-run.json", "metadata": "run-metadata.json",
         "sheet": "gui-contact-sheet.png", "visual": "visual-summary.md"}
VERDICTS = {"pass": ("通过", "passed"), "fail": ("失败", "failed"),
            "skip": ("跳过", "skipped"), "error": ("异常", "errors")}
VERDICT_LABELS = {key: value[0] for key, value in VERDICTS.items()}


def require_verdict(value):
    if not isinstance(value, str) or value not in VERDICTS:
        raise ValueError(f"未知 GUI verdict：{value!r}")
    return value


def case_verdict(checks, error=None):
    if error:
        return "error"
    if not checks:
        return "skip"
    return "pass" if all(check.get("pass") is True for check in checks) else "fail"


@dataclass(frozen=True)
class RecordSnapshot:
    """JSON 文本是不可变快照；每个消费者取得自己的结构化副本。"""
    directory: Path
    payload: str

    def data(self):
        return json.loads(self.payload)

    def screenshots(self):
        return [artifact for case in self.data()["cases"] for artifact in case["artifacts"]
                if artifact["kind"] == "screenshot"]


class RunRecord:
    __slots__ = ("_directory", "_metadata", "_env", "_cases", "_case_metrics", "_metrics",
                 "_setup_error", "_artifact_errors", "_derived", "_finalization")

    def __init__(self, directory, metadata, env=None, cases=(), metrics=None, setup_error=None):
        self._directory = Path(directory)
        self._metadata = copy.deepcopy(metadata)
        self._env = copy.deepcopy(env or {})
        self._cases, self._case_metrics = [], []
        self._metrics = copy.deepcopy(metrics or {"total_ms": 0, "phases": {}})
        self._setup_error = setup_error
        self._artifact_errors, self._derived, self._finalization = [], {}, {}
        for case in cases:
            self.add_case(case)

    @property
    def directory(self):
        return self._directory

    def add_case(self, case, metrics=None):
        require_verdict(case["verdict"])
        self._cases.append(copy.deepcopy(case))
        if metrics is not None:
            self._case_metrics.append(copy.deepcopy(metrics))

    def case_metrics(self):
        return copy.deepcopy(self._case_metrics)

    def set_environment(self, env):
        self._env = copy.deepcopy(env)

    def fail_setup(self, error):
        self._setup_error = str(error)

    def complete_run(self, metrics):
        """一次接收采集指标；后续派生阶段的指标由记录自身封装。"""
        self._metrics = copy.deepcopy(metrics)

    def screenshots(self):
        return copy.deepcopy([artifact for case in self._cases for artifact in case["artifacts"]
                              if artifact["kind"] == "screenshot"])

    def artifact_failed(self, role, error, *, phase=None, evidence_id=None):
        value = {"role": role, "error": str(error), "phase": phase or role}
        if evidence_id is not None:
            value["id"] = evidence_id
        self._artifact_errors.append(value)

    def accept_derived(self, result):
        self._derived = copy.deepcopy(result)
        for error in result.get("artifact_errors", []):
            self.artifact_failed(error["role"], error["error"], phase="sheet", evidence_id=error.get("id"))

    def summary(self):
        counts = {key: 0 for key in ("passed", "warned", "failed", "skipped", "errors")}
        rows, total, passed = [], 0, 0
        for case in self._cases:
            verdict = require_verdict(case["verdict"])
            counts[VERDICTS[verdict][1]] += 1
            checks = case["checks"]
            good = sum(check.get("pass") is True for check in checks)
            total += len(checks)
            passed += good
            rows.append({"name": case["name"], "verdict": verdict, "passed_checks": good, "total_checks": len(checks)})
        verdict = "pass" if self._cases and not self._setup_error and all(c["verdict"] == "pass" for c in self._cases) else "fail"
        return copy.deepcopy({"verdict": verdict, **counts, "total_cases": len(rows), "total_checks": total,
                "passed_checks": passed, "cases": rows, "setup_error": self._setup_error,
                "artifact_errors": self._artifact_errors, "metrics": self._metrics,
                "duration_seconds": round(self._metrics["total_ms"] / 1000, 1),
                "outputs": {key: str(self.directory / name) for key, name in FILES.items()},
                "shots": {"count": len(self.screenshots()), "contact_sheet": self._derived.get("contact_sheet"),
                          "list": self._derived.get("list_path")}})

    def snapshot(self):
        value = {"format": "idtw-gui-run-v1", "metadata": self._metadata, "environment": self._env,
                 "cases": self._cases, "summary": self.summary()}
        return RecordSnapshot(self.directory, json.dumps(value, ensure_ascii=False))

    def save(self):
        """按角色原子替换 JSON；派生写入失败时已有原始记录仍可恢复。"""
        value = self.snapshot().data()
        value["finalization"] = copy.deepcopy(self._finalization)
        self.directory.mkdir(parents=True, exist_ok=True)
        for role, data in (("record", value), ("summary", value["summary"]),
                           ("metadata", {"metadata": self._metadata, "env": self._env, "metrics": self._metrics,
                                         "finalization": self._finalization})):
            path = self.directory / FILES[role]
            temporary = path.with_name(path.name + ".tmp")
            temporary.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
            temporary.replace(path)

    def derive_outputs(self, sheet_builder, report_renderer):
        """先保存原始证据，再接收派生产物；产物失败沿用独立于运行 verdict 的策略。"""
        started = time.perf_counter()
        run_ms = self._metrics["total_ms"]
        self.save()
        shots = None
        if self._cases:
            sheet_started = time.perf_counter()
            try:
                shots = sheet_builder(self.snapshot())
                self.accept_derived(shots)
            except Exception as failure:
                self.artifact_failed("sheet", failure)
            self._metrics["phases"]["sheet_ms"] = int((time.perf_counter() - sheet_started) * 1000)
        # 所有共享视图在 HTML 派生前冻结同一指标，避免包含自身耗时的循环。
        self._metrics["total_ms"] = run_ms + int((time.perf_counter() - started) * 1000)
        self._metrics["phases"]["report_ms"] = 0
        self.save()
        report_started = time.perf_counter()
        try:
            document = report_renderer(self.snapshot(), shots)
            (self.directory / FILES["report"]).write_text(document, encoding="utf-8")
        except Exception as failure:
            self.artifact_failed("report", failure)
        self._finalization = {"report_render_write_ms": int((time.perf_counter() - report_started) * 1000),
                              "wall_total_ms": run_ms + int((time.perf_counter() - started) * 1000)}
        self.save()
