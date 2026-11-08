"""后端探针领域产物：原始身份、阶段命名、读取与验证后归档。"""
from __future__ import annotations

import argparse
import json
import shutil
import sys
from pathlib import Path

from toolslib.checks import configure_console

RAW_FORMAT = "idtw-backend-probe-v1"
PERFORMANCE_FORMAT = "idtw-performance-report-v1"
PHASES = ("prepare", "resume")
LATEST_REPORT = "probe-report.json"


def phase_filename(phase):
    if phase not in PHASES:
        raise ValueError(f"未知报告阶段：{phase!r}")
    return phase + "-report.json"


def read_report(path: Path, *, phase=None, suite=None, run_id=None):
    """原始 JSON 先验证身份，保留生产者的结果与性能数据。"""
    try:
        report = json.loads(path.read_text(encoding="utf-8-sig"))
    except json.JSONDecodeError as failure:
        raise ValueError(f"{path}: JSON 无效：{failure}") from failure
    if not isinstance(report, dict):
        raise ValueError(f"{path}: 报告需要 JSON object")
    expected = {"format": RAW_FORMAT}
    if phase is not None:
        expected["phase"] = phase
    if suite is not None:
        expected["suite"] = suite
    if run_id is not None:
        expected["run_id"] = run_id
    for key, value in expected.items():
        if report.get(key) != value:
            raise ValueError(f"{path}: {key} 身份不匹配，expected={value!r}, actual={report.get(key)!r}")
    if report.get("phase") not in PHASES:
        raise ValueError(f"{path}: 未知 phase {report.get('phase')!r}")
    for key in ("suite", "run_id"):
        if not isinstance(report.get(key), str) or not report[key]:
            raise ValueError(f"{path}: 缺少非空身份字段 {key}")
    return report


def read_reports(directory: Path):
    """每个运行目录独立校验；prepare/resume 只配对同一 suite 和 run_id。"""
    prepare = directory / phase_filename("prepare")
    if not prepare.is_file():
        raise ValueError(f"缺少阶段报告：{prepare}")
    first = read_report(prepare, phase="prepare")
    reports = [("prepare", first)]
    resume = directory / phase_filename("resume")
    if resume.is_file():
        reports.append(("resume", read_report(resume, phase="resume", suite=first["suite"], run_id=first["run_id"])))
    elif first.get("verdict") == "RESTART_REQUIRED":
        raise ValueError(f"重启序列缺少阶段报告：{resume}")
    return reports


def archive_phase(directory: Path, phase, suite, run_id):
    """验证完成后才复制，错误身份不会覆盖已归档阶段。"""
    name = phase_filename(phase)
    source = directory / LATEST_REPORT
    read_report(source, phase=phase, suite=suite, run_id=run_id)
    for other in PHASES:
        existing = directory / phase_filename(other)
        if existing.exists():
            read_report(existing, phase=other, suite=suite, run_id=run_id)
    target = directory / name
    temporary = target.with_name(target.name + ".tmp")
    shutil.copyfile(source, temporary)
    temporary.replace(target)
    return target


def main():
    configure_console()
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    archive = commands.add_parser("archive", help="验证身份后保存阶段报告")
    archive.add_argument("--directory", type=Path, required=True)
    archive.add_argument("--phase", choices=PHASES, required=True)
    archive.add_argument("--suite", required=True)
    archive.add_argument("--run-id", required=True)
    args = parser.parse_args()
    try:
        target = archive_phase(args.directory, args.phase, args.suite, args.run_id)
    except (OSError, ValueError) as failure:
        print(f"[报告错误] {failure}", file=sys.stderr)
        return 2
    print(json.dumps({"path": str(target)}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
