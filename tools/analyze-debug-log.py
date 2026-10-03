#!/usr/bin/env python3
"""流式解析 IDTW 开发日志，导出阶段、场景、队列指标和可追溯的失败证据。"""

from __future__ import annotations

import argparse
import csv
import gzip
import json
import re
import sys
from collections import Counter
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any


EVENT = re.compile(r"\[IDTW_DEBUG\] run=(\S+) scene=(\S+) event=(\S+) (\{.*\})\s*$")
SEVERITY = re.compile(r"\[([^\]]+)/(WARN|ERROR)\] \[([^\]]+)\]: (.*)")
FRAME_FIELDS = (
    "checks_pending", "effects_pending", "scheduler_pending_server_wide",
    "scheduler_last_max_ready_delay_ticks", "checks_last_us", "effects_last_us",
    "budget_used_us", "budget_usage_ratio", "budget_work_units_used",
)


def at(data: dict[str, Any], *keys: str, default: Any = None) -> Any:
    # 缺失指标保留为空，不伪造为零成本。
    value: Any = data
    for key in keys:
        if not isinstance(value, dict) or key not in value:
            return default
        value = value[key]
    return value


def number(value: Any, digits: int = 3) -> str:
    # Markdown 空值明确表示没有测量数据。
    return "—" if value is None else f"{value:.{digits}f}"


def cell(value: Any) -> str:
    # 日志文本不能破坏报告表格。
    return str(value).replace("|", "\\|").replace("\n", " ")


@dataclass
class Run:
    """单个 UUID 的有界汇总；保留 START/END，不保留大量逐实体日志。"""

    run_id: str
    scene: str
    first_line: int
    last_line: int
    events: Counter = field(default_factory=Counter)
    start: dict[str, Any] = field(default_factory=dict)
    end: dict[str, Any] = field(default_factory=dict)
    start_time: str = ""
    end_time: str = ""
    start_line: int | None = None
    end_line: int | None = None
    frame_count: int = 0
    frame_max: dict[str, float] = field(default_factory=dict)
    last_frame: dict[str, Any] = field(default_factory=dict)
    issues: list[dict[str, Any]] = field(default_factory=list)

    def consume(self, event: str, data: dict[str, Any], line: int, timestamp: str) -> None:
        # FRAME 只保存数量、峰值及末帧，空间占用不随逐实体日志增长。
        self.last_line = line
        self.events[event] += 1
        if event in ("START", "PIPELINE_START", "READY"):
            self.start, self.start_time, self.start_line = data, timestamp, line
        elif event in ("END", "PIPELINE_END"):
            self.end, self.end_time, self.end_line = data, timestamp, line
        elif event == "FRAME":
            self.frame_count += 1
            self.last_frame = data
            for key in FRAME_FIELDS:
                value = data.get(key)
                if isinstance(value, (int, float)):
                    self.frame_max[key] = max(self.frame_max.get(key, value), value)
        elif event in ("CHECK_FAILED", "ERROR", "EVALUATION_ERROR", "PIPELINE_ERROR"):
            if len(self.issues) < 64:
                self.issues.append({"event": event, "line": line, "data": data})

    def export(self) -> dict[str, Any]:
        # JSON 保留原始最终指标，供跨版本比较而非重新计算分位数。
        return {
            "run": self.run_id, "scene": self.scene,
            "first_line": self.first_line, "last_line": self.last_line,
            "start_line": self.start_line, "end_line": self.end_line,
            "start_time": self.start_time, "end_time": self.end_time,
            "events": dict(self.events), "start": self.start, "end": self.end,
            "frame_count": self.frame_count, "frame_max": self.frame_max,
            "last_frame": self.last_frame, "issues": self.issues,
        }


@dataclass
class Analysis:
    """单日志解析结果；非debug警告和解析错误单独保留，避免被忽略。"""

    path: Path
    runs: dict[str, Run] = field(default_factory=dict)
    lines: int = 0
    debug_events: int = 0
    parse_errors: int = 0
    parse_examples: list[dict[str, Any]] = field(default_factory=list)
    warning_counts: Counter = field(default_factory=Counter)
    warnings: list[dict[str, Any]] = field(default_factory=list)
    step_results: dict[str, dict[str, Any]] = field(default_factory=dict)

    def parse(self) -> None:
        # 支持最新日志和轮转压缩日志，逐行读取，不把整个文件载入内存。
        opener = gzip.open if self.path.suffix == ".gz" else open
        with opener(self.path, "rt", encoding="utf-8-sig", errors="replace") as stream:
            for self.lines, line in enumerate(stream, 1):
                match = EVENT.search(line)
                if match:
                    self.debug_events += 1
                    run_id, scene, event, payload = match.groups()
                    try:
                        data = json.loads(payload)
                    except json.JSONDecodeError as error:
                        self.parse_error(str(error))
                        continue
                    run = self.runs.setdefault(run_id, Run(run_id, scene, self.lines, self.lines))
                    timestamp = line[1:line.find("]")] if line.startswith("[") else ""
                    run.consume(event, data, self.lines, timestamp)
                    if event == "PIPELINE_STEP_END" and "scene_run" in data:
                        self.step_results[data["scene_run"]] = data
                elif "[IDTW_DEBUG] run=" in line:
                    self.parse_error("事件行未匹配；可能日志被截断或不是结构化事件")
                else:
                    warning = SEVERITY.search(line)
                    if warning:
                        thread, severity, logger, message = warning.groups()
                        self.warning_counts[severity] += 1
                        if len(self.warnings) < 100:
                            self.warnings.append({
                                "line": self.lines, "thread": thread, "severity": severity,
                                "logger": logger, "message": message,
                            })

    def parse_error(self, message: str) -> None:
        # 异常不阻止后续轮次分析，但报告不能隐藏丢失事件。
        self.parse_errors += 1
        if len(self.parse_examples) < 20:
            self.parse_examples.append({"line": self.lines, "message": message})

    def scenes(self) -> list[Run]:
        return [run for run in self.runs.values() if run.scene.startswith(("run/", "bench/"))]

    def pipelines(self) -> list[Run]:
        return [run for run in self.runs.values() if run.scene.startswith("pipeline/")]


def summary(run: Run, steps: dict[str, dict[str, Any]]) -> dict[str, Any]:
    # 性能门槛取流水线实际结果；独立场景没有门槛时不补造通过结论。
    window = run.end.get("window", {})
    step = steps.get(run.run_id, {})
    failed = [check for check in run.end.get("checks", []) if check.get("pass") is False]
    performance = step.get("performance_checks", [])
    failed_performance = [check["name"] for check in performance if check.get("pass") is False]
    return {
        "scene": run.scene, "run": run.run_id, "start_line": run.start_line,
        "end_line": run.end_line, "entities": run.start.get("entities"),
        "requested_seconds": run.start.get("requested_measurement_seconds"),
        "verdict": run.end.get("verdict", "NO_END"),
        "stop_reason": run.end.get("stop_reason", ""),
        "tps": window.get("observed_ticks_per_second"),
        "tick_mean_us": at(window, "server_tick_cost", "mean_us"),
        "tick_p95_us": at(window, "server_tick_cost", "p95_us"),
        "tick_max_us": at(window, "server_tick_cost", "max_us"),
        "tick_cost_scope": window.get("tick_cost_scope", run.start.get("tick_cost_scope", "legacy_vanilla_tick")),
        "tick_timing_missing_ticks": window.get("tick_timing_missing_ticks"),
        "vanilla_tick_p95_us": at(window, "vanilla_tick_cost", "p95_us"),
        "idtw_runtime_p95_us": at(window, "idtw_runtime_cost", "p95_us"),
        "checks_p95_us": at(window, "checks", "p95_us"),
        "checks_max_us": at(window, "checks", "max_us"),
        "effects_p95_us": at(window, "effects", "p95_us"),
        "effects_max_us": at(window, "effects", "max_us"),
        "ready_delay_max_ticks": at(window, "server_ready_delay_ticks", "max"),
        "exhausted_time_ticks": window.get("ticks_exhausted_by_time"),
        "exhausted_work_ticks": window.get("ticks_exhausted_by_work_units"),
        "scheduler_steps": at(window, "scheduler", "steps"),
        "scheduler_mean_us": window.get("mean_budget_usage_ratio", 0) * run.start["server_budget_us"]
        if "mean_budget_usage_ratio" in window and "server_budget_us" in run.start else None,
        "scheduler_max_us": window.get("max_budget_usage_ratio", 0) * run.start["server_budget_us"]
        if "max_budget_usage_ratio" in window and "server_budget_us" in run.start else None,
        "first_output_delay_ticks": run.end.get("first_output_delay_ticks"),
        "scheduler_failed": at(window, "scheduler", "failed"),
        "scheduler_pending_after_cleanup": run.end.get("scheduler_pending_after_cleanup"),
        "remaining_scene_tasks": run.end.get("remaining_scene_tasks"),
        "unsettled": window.get("ledger_unsettled"),
        "performance": "FAIL" if failed_performance else ("PASS" if performance else "N/A"),
        "failed_checks": "; ".join(f'{c["name"]}: {c.get("actual")} vs {c.get("expected")}' for c in failed),
        "failed_performance": "; ".join(failed_performance),
    }


def table(headers: list[str], rows: list[list[Any]]) -> list[str]:
    # 所有表格统一转义；分位数由Java日志提供，不跨轮次求平均。
    return [
        "| " + " | ".join(headers) + " |",
        "| " + " | ".join("---" for _ in headers) + " |",
        *["| " + " | ".join(cell(value) for value in row) + " |" for row in rows],
    ]


def markdown(analysis: Analysis, rows: list[dict[str, Any]]) -> str:
    # 报告列出证据和边界，不从聚合日志推断CPU调用栈。
    text = [
        "# IDTW Debug 日志报告", "",
        f"日志：`{analysis.path}`  ",
        f"共{analysis.lines}行，{analysis.debug_events}条结构化事件，"
        f"{len(rows)}个场景，{len(analysis.pipelines())}个阶段，解析异常{analysis.parse_errors}条。",
        "", "## 阶段结果", "",
    ]
    text += table(["阶段", "结果", "完成/计划", "原因", "起止日志行"], [
        [run.scene, run.end.get("verdict", "NO_END"),
         f'{run.end.get("completed_steps", 0)}/{run.start.get("total_steps", "?")}',
         run.end.get("stop_reason", ""), f"{run.start_line}–{run.end_line}"]
        for run in analysis.pipelines()
    ])
    text += ["", "## 场景结果", ""]
    text += table(["场景", "源数量", "功能", "TPS", "tick p95 ms", "tick max ms", "性能", "END行"], [
        [row["scene"], row["entities"], row["verdict"], number(row["tps"]),
         number(row["tick_p95_us"] / 1000 if row["tick_p95_us"] is not None else None),
         number(row["tick_max_us"] / 1000 if row["tick_max_us"] is not None else None),
         row["performance"], row["end_line"]]
        for row in rows
    ])
    text += ["", "## 失败证据", ""]
    for row in rows:
        if row["verdict"] != "PASS" or row["performance"] == "FAIL":
            text.append(f'- `{row["scene"]}`（run={row["run"]}，END行{row["end_line"]}）：'
                        f'{row["failed_checks"] or row["stop_reason"] or "没有END"}；'
                        f'性能失败项：{row["failed_performance"] or "无"}。')
    text += ["", "## Tick计量范围与分项", ""]
    text += table(["场景", "数量", "范围", "原版 p95 µs", "IDTW运行时 p95 µs", "缺失计时 tick"], [
        [row["scene"], row["entities"], row["tick_cost_scope"],
         row["vanilla_tick_p95_us"], row["idtw_runtime_p95_us"], row["tick_timing_missing_ticks"]]
        for row in rows
    ])
    text += ["", "## 调度总耗时与出货延迟", ""]
    text += table(["场景", "数量", "调度平均/峰值 µs", "提交至首次产出 tick"], [
        [row["scene"], row["entities"],
         f'{number(row["scheduler_mean_us"], 1)}/{number(row["scheduler_max_us"], 1)}',
         row["first_output_delay_ticks"]]
        for row in rows
    ])
    text += ["", "调度耗时来自同轮预算占用比例×server_budget_us；-1表示没有首次产出。", ""]
    text += ["", "## 队列与调度", ""]
    text += table(["场景", "数量", "checks p95/max µs", "effects p95/max µs",
                   "就绪延迟max tick", "预算耗尽tick（时间/工作量）", "清理后服务器pending"], [
        [row["scene"], row["entities"],
         f'{row["checks_p95_us"]}/{row["checks_max_us"]}',
         f'{row["effects_p95_us"]}/{row["effects_max_us"]}',
         row["ready_delay_max_ticks"], f'{row["exhausted_time_ticks"]}/{row["exhausted_work_ticks"]}',
         row["scheduler_pending_after_cleanup"]]
        for row in rows
    ])
    text += ["", "## 非Debug警告与错误", "",
             f'WARN={analysis.warning_counts["WARN"]}，ERROR={analysis.warning_counts["ERROR"]}；下面最多保留100条。', ""]
    for warning in analysis.warnings:
        text.append(f'- 日志行{warning["line"]} [{warning["severity"]}] {warning["message"]}')
    text += ["", "## 解释边界", "",
             "- NO_END 表示该轮日志未见结束，不能当作通过；活跃日志可能仍在写入。",
             "- 性能N/A表示未运行阶段性能门槛，不表示失败或通过。",
             "- p95很低仍可能出现单次长停顿；需同时检查max、max_tick_interval_ms和外部告警。",
             "- 服务器pending可能是上一tick汇总快照；需与实时车道pending及任务terminal状态核对，不能据此认定泄漏。",
             "- v3的server_tick_cost按同tick原版成本与IDTW运行时成本相加后统计；旧日志仅有原版成本，两种口径不可混比。",
             "- v3额外提供vanilla_tick_cost/idtw_runtime_cost；开发场景推进及其它结束事件监听器不在合计范围内。",
             "- 日志没有客户端FPS、完整CPU调用栈或GC时间线，不能据此锁定未计量的热点。",
             "- 不把不同轮次的p95相减解释为模组净开销；原始START/END在JSON报告中保留。"]
    if analysis.parse_examples:
        text += ["", "## 解析异常", "", *[f'- 行{item["line"]}：{item["message"]}' for item in analysis.parse_examples]]
    return "\n".join(text) + "\n"


def main() -> int:
    # 默认NeoForge最新日志；每次输出到Git忽略的独立本地目录，保留历史报告。
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    project = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", nargs="?", type=Path, default=project / "neoforge/run/logs/latest.log")
    parser.add_argument("--out", type=Path, help="输出目录；默认.local/debug/reports下按时间生成")
    args = parser.parse_args()
    if args.out is None:
        args.out = project / ".local/debug/reports" / datetime.now().strftime("%Y%m%d-%H%M%S-%f")
    if not args.log.is_file():
        parser.error(f"日志不存在：{args.log}")
    analysis = Analysis(args.log.resolve())
    analysis.parse()
    steps = {
        step["scene_run"]: step
        for pipeline in analysis.pipelines()
        for step in pipeline.end.get("results", [])
        if "scene_run" in step
    }
    steps.update(analysis.step_results)
    rows = [summary(run, steps) for run in analysis.scenes()]
    args.out.mkdir(parents=True, exist_ok=True)
    payload = {
        "log": str(analysis.path), "lines": analysis.lines, "debug_events": analysis.debug_events,
        "parse_errors": analysis.parse_errors, "parse_examples": analysis.parse_examples,
        "warning_counts": dict(analysis.warning_counts), "warnings": analysis.warnings,
        "summary": rows, "runs": [run.export() for run in analysis.runs.values()],
        "step_results": analysis.step_results,
    }
    (args.out / "report.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    (args.out / "report.md").write_text(markdown(analysis, rows), encoding="utf-8")
    with (args.out / "scenes.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        columns = list(rows[0]) if rows else list(summary(Run("", "", 0, 0), {}))
        writer = csv.DictWriter(stream, fieldnames=columns)
        writer.writeheader()
        writer.writerows(rows)
    print(f"Parsed {analysis.lines} lines; {len(rows)} scenes; {len(analysis.pipelines())} stages; "
          f"{analysis.parse_errors} malformed events.")
    for pipeline in analysis.pipelines():
        print(f'{pipeline.scene}: {pipeline.end.get("verdict", "NO_END")} '
              f'{pipeline.end.get("completed_steps", 0)}/{pipeline.start.get("total_steps", "?")} '
              f'{pipeline.end.get("stop_reason", "")}')
    print(f"Reports: {args.out.resolve()}")
    return 1 if analysis.parse_errors else 0


if __name__ == "__main__":
    sys.exit(main())
