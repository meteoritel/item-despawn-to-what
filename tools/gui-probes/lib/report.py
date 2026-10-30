"""消费 RunRecord 与截图投影渲染自包含 HTML，判定与证据身份由记录持有。"""

import base64
import html
import json
import os
from typing import Any

_VERDICT_LABELS = {"pass": "通过", "warn": "警告", "fail": "失败", "skip": "跳过", "error": "异常"}
_RUNNER_VERDICTS = {"pass": "pass", "warn": "warn", "fail": "fail", "error": "error", "skip": "skip"}

_STYLE = """
:root { color-scheme: light; }
* { box-sizing: border-box; }
body {
  margin: 0; padding: 32px 24px 64px;
  font: 14px/1.6 "Segoe UI", "Microsoft YaHei", sans-serif;
  color: #1f2328; background: #f6f8fa;
}
.hero, section { max-width: 1180px; margin: 0 auto; }
.hero {
  display: flex; align-items: center; justify-content: space-between; gap: 24px;
  padding: 20px 24px; background: #fff;
  border: 1px solid #d0d7de; border-radius: 10px;
}
h1 { margin: 0 0 6px; font-size: 22px; line-height: 1.3; }
.counts { display: flex; flex-wrap: wrap; gap: 14px; font-size: 13px; color: #57606a; }
.counts b { color: #1f2328; }
.overall {
  flex: none; padding: 8px 20px; border-radius: 8px;
  font-size: 18px; font-weight: 600; color: #fff; white-space: nowrap;
}
.pass-bg { background: #1a7f37; }
.fail-bg { background: #cf222e; }
.skip-bg { background: #57606a; }
.warn-bg { background: #9a6700; }
section {
  margin-top: 24px; padding: 18px 22px; background: #fff;
  border: 1px solid #d0d7de; border-radius: 10px;
}
section > h2 { margin: 0 0 12px; font-size: 16px; }
table { width: 100%; border-collapse: collapse; }
th, td { padding: 6px 10px; border-bottom: 1px solid #eaeef2; text-align: left; vertical-align: top; }
table.kv th { width: 210px; color: #57606a; font-weight: 600; }
table.checks th { color: #57606a; font-size: 12px; }
code {
  font: 12px/1.6 "Cascadia Mono", Consolas, monospace; word-break: break-all;
  background: #f6f8fa; padding: 1px 4px; border-radius: 4px;
}
.empty { color: #57606a; font-style: italic; }
article.case {
  margin-top: 14px; padding: 14px 16px; background: #fff;
  border: 1px solid #eaeef2; border-left: 4px solid #d0d7de; border-radius: 8px;
}
article.case.pass { border-left-color: #1a7f37; }
article.case.fail { border-left-color: #cf222e; }
article.case.skip { border-left-color: #57606a; }
article.case.warn { border-left-color: #9a6700; }
article.case.error { border-left-color: #bc4c00; }
article.case h2 { display: flex; align-items: center; gap: 10px; margin: 0 0 10px; font-size: 15px; }
.index {
  display: inline-block; min-width: 22px; height: 22px; line-height: 22px;
  text-align: center; border-radius: 50%; background: #eaeef2; color: #57606a; font-size: 12px;
}
.badge { padding: 1px 10px; border-radius: 999px; color: #fff; font-size: 12px; font-weight: 600; }
.badge.pass { background: #1a7f37; }
.badge.fail { background: #cf222e; }
.badge.skip { background: #57606a; }
.badge.warn { background: #9a6700; }
.badge.error { background: #bc4c00; }
.detail { margin: 0 0 10px; color: #57606a; }
table.checks tr.bad { background: #fff5f5; }
table.checks tr.bad td:last-child { color: #cf222e; font-weight: 600; }
table.checks tr.ok td:last-child { color: #1a7f37; }
.shot { margin: 0; max-width: 100%; overflow: auto; }
.shot-frame {
  position: relative; display: inline-block; line-height: 0;
  background: #fff; border: 1px solid #d0d7de;
}
.shot-frame img { display: block; max-width: none; }
.no-png { display: inline-block; padding: 24px; font-size: 12px; color: #57606a; }
.shot figcaption { display: flex; gap: 8px; align-items: baseline; margin-top: 6px; font-size: 12px; color: #57606a; }
.shot figcaption code { background: none; padding: 0; }
p.file { margin: 6px 0; font-size: 12px; color: #57606a; }
.rect { position: absolute; border: 2px solid #0969da; background: rgba(9, 105, 218, 0.08); z-index: 2; }
.rect i {
  position: absolute; top: -18px; left: -2px; padding: 0 5px;
  background: #0969da; color: #fff; font: 11px/16px "Cascadia Mono", Consolas, monospace;
  font-style: normal; white-space: nowrap;
}
h3 { margin: 18px 0 8px; font-size: 14px; }
.files { margin-top: 12px; }
details > summary.section-title {
  font-size: 16px; font-weight: 600; cursor: pointer; list-style-position: outside;
}
details[open] > summary.section-title { margin-bottom: 12px; }
table.perf th { width: 160px; }
"""


def _escape(value: object) -> str:
    return html.escape(str(value), quote=True)


def _display(value: object) -> str:
    """把任意元数据值转成可读文本；容器类型按 JSON 展开。"""
    if value is None:
        return "—"
    if isinstance(value, (str, int, float, bool)):
        return str(value)
    return json.dumps(value, ensure_ascii=False, sort_keys=True)


def _cell(value: object) -> str:
    text = _display(value)
    if text == "—":
        return '<span class="empty">—</span>'
    return "<code>" + _escape(text) + "</code>"


def _verdict(case: dict[str, Any]) -> str:
    value = str(case.get("verdict", "error")).lower()
    return value if value in _VERDICT_LABELS else "error"


def _badge(verdict: str) -> str:
    return '<span class="badge ' + verdict + '">' + _VERDICT_LABELS[verdict] + "</span>"


_PHASE_LABELS = (
    ("install_ms", "安装 prelude"),
    ("wait_client_ms", "等待客户端"),
    ("setup_ms", "前置 setup"),
    ("cases_ms", "用例"),
    ("sheet_ms", "截图拼图"),
    ("report_ms", "渲染报告"),
)


def _milliseconds(value: object) -> str:
    return "%d ms" % int(float(value or 0))


def _size(width: object, height: object) -> str:
    return "%d×%d" % (int(width or 0), int(height or 0))


def _embedded_image(alt: str, png: object, width: object = None, height: object = None) -> str:
    """内嵌 PNG；字节由调用方读好传入，渲染层不做文件 IO。"""
    if not png:
        return '<span class="no-png">未内嵌图片</span>'
    attributes = ' alt="' + _escape(alt) + '"'
    if width and height:
        attributes += ' width="' + str(int(width)) + '" height="' + str(int(height)) + '"'
    encoded = base64.b64encode(bytes(png)).decode("ascii")
    return '<img src="data:image/png;base64,' + encoded + '"' + attributes + ">"


def _shots_section(shots):
    """截图区块：内嵌 contact sheet 总览 + 每张截图的清单；不做任何图像比对判定。"""
    if not shots:
        return ""
    items = shots.get("shots") or []
    parts = ['<section><h2>截图</h2>',
             '<div class="counts"><span>截图 <b>' + str(shots.get("count", len(items)))
             + "</b></span>" + "<span>拼图耗时 <b>" + _milliseconds(shots.get("sheet_ms"))
             + "</b></span></div>"]
    if shots.get("contact_sheet"):
        parts.append('<h3>contact sheet</h3><figure class="shot"><div class="shot-frame">'
                     + _embedded_image("contact sheet", shots.get("png_bytes"))
                     + "</div><figcaption><b>contact sheet</b><code>"
                     + _escape(shots["contact_sheet"]) + "</code></figcaption></figure>")
    if items:
        rows = "".join(
            "<tr><td>" + str(shot.get("index", 0)) + "</td>"
            + "<td>" + _escape(shot.get("case", "")) + "</td>"
            + "<td>" + _escape(shot.get("name", "")) + "</td>"
            + "<td>" + _size(shot.get("width"), shot.get("height")) + " @"
            + _escape(shot.get("scale", "")) + "</td>"
            + "<td><code>" + _escape(shot.get("path", "")) + "</code></td></tr>"
            for shot in items)
        parts.append('<table class="checks"><thead><tr><th>#</th><th>用例</th><th>截图</th>'
                     "<th>逻辑尺寸</th><th>文件</th></tr></thead><tbody>" + rows + "</tbody></table>")
    else:
        parts.append('<p class="empty">本轮没有截图，contact sheet 为空网格</p>')
    if shots.get("list_path"):
        parts.append('<p class="file">截图清单 <code>' + _escape(shots["list_path"]) + "</code></p>")
    parts.append("</section>")
    return "\n".join(parts)


def _performance_section(metrics: dict[str, Any] | None) -> str:
    """性能区块：折叠展示 phases、eval RTT、settle 汇总与每用例耗时。"""
    if not metrics:
        return ""
    phases = metrics.get("phases") or {}
    evals = metrics.get("evals") or {}
    settle = metrics.get("settle") or {}
    shots = metrics.get("shots") or {}
    rows = [("总耗时", _milliseconds(metrics.get("total_ms")))]
    rows += [(label, _milliseconds(phases.get(key))) for key, label in _PHASE_LABELS]
    rows += [
        ("eval 次数", evals.get("count", 0)),
        ("RTT 平均", "%.1f ms" % float(evals.get("rtt_avg_ms") or 0)),
        ("RTT 最大", "%.1f ms" % float(evals.get("rtt_max_ms") or 0)),
        ("RTT 合计", "%.1f ms" % float(evals.get("rtt_total_ms") or 0)),
        ("settle 次数", settle.get("count", 0)),
        ("settle 等待", _milliseconds(settle.get("waited_ms"))),
        ("settle 轮询", settle.get("polls", 0)),
        ("settle 超时", settle.get("timeouts", 0)),
        ("截图张数", shots.get("count", 0)),
        ("截图耗时", _milliseconds(shots.get("shot_ms"))),
    ]
    rows_html = "".join("<tr><th>" + _escape(label) + "</th><td>" + _escape(text) + "</td></tr>"
                        for label, text in rows)
    parts = ['<section><details><summary class="section-title">性能</summary>',
             '<table class="kv perf"><tbody>' + rows_html + "</tbody></table>"]
    cases = metrics.get("cases") or []
    if cases:
        head = ("<thead><tr><th>用例</th><th>耗时</th><th>eval</th><th>细项</th>"
                "<th>截图</th><th>settle</th><th>截图耗时</th></tr></thead>")
        body = "".join(
            "<tr><td>" + _escape(case.get("name", "")) + "</td>"
            + "<td>" + _milliseconds(case.get("duration_ms")) + "</td>"
            + "<td>" + str(case.get("evals", 0)) + "</td>"
            + "<td>" + str(case.get("checks", 0)) + "</td>"
            + "<td>" + str(case.get("shots", 0)) + "</td>"
            + "<td>" + _milliseconds(case.get("settle_ms")) + "</td>"
            + "<td>" + _milliseconds(case.get("shot_ms")) + "</td></tr>"
            for case in cases)
        parts.append('<table class="checks perf">' + head + "<tbody>" + body + "</tbody></table>")
    parts.append("</details></section>")
    return "\n".join(parts)


def _kv_table(items: dict[str, Any], empty_text: str) -> str:
    if not items:
        return '<p class="empty">' + _escape(empty_text) + "</p>"
    rows = "".join(
        "<tr><th>" + _escape(key) + "</th><td>" + _cell(value) + "</td></tr>"
        for key, value in items.items()
    )
    return '<table class="kv"><tbody>' + rows + "</tbody></table>"


def _check_table(checks: list[dict[str, Any]]) -> str:
    if not checks:
        return '<p class="empty">该用例没有细项检查</p>'
    rows = []
    for check in checks:
        ok = check.get("pass") is True
        rows.append(
            '<tr class="' + ("ok" if ok else "bad") + '">'
            + "<td>" + _escape(check.get("name", "")) + "</td>"
            + "<td>" + _cell(check.get("expected")) + "</td>"
            + "<td>" + _cell(check.get("actual")) + "</td>"
            + "<td>" + ("通过" if ok else "失败") + "</td></tr>"
        )
    return ('<table class="checks"><thead><tr><th>检查项</th><th>期望</th><th>实际</th><th>结果</th></tr>'
            "</thead><tbody>" + "".join(rows) + "</tbody></table>")


def _artifact(artifact: dict[str, Any]) -> str:
    """用例内的证据只列路径：截图本身在「截图」区与 contact sheet 里，不重复内嵌。"""
    name = _escape(artifact.get("name", "截图"))
    path = _escape(artifact.get("path", ""))
    label = "截图" if artifact.get("kind") == "screenshot" else "证据"
    return '<p class="file">' + label + ' <b>' + name + "</b><code>" + path + "</code></p>"


def _case_section(index: int, case: dict[str, Any]) -> str:
    verdict = _verdict(case)
    parts = ['<article class="case ' + verdict + '">',
             '<h2><span class="index">' + str(index) + "</span><span>" + _escape(case.get("name", ""))
             + "</span>" + _badge(verdict) + "</h2>"]
    detail = case.get("detail")
    if detail:
        parts.append('<p class="detail">' + _escape(detail) + "</p>")
    parts.append(_check_table(case.get("checks") or []))
    artifacts = case.get("artifacts") or []
    if artifacts:
        parts.append('<div class="files">' + "".join(_artifact(item) for item in artifacts) + "</div>")
    parts.append("</article>")
    return "\n".join(parts)


def _hero(run_metadata: dict[str, Any], summary: dict[str, Any]) -> str:
    title = _escape(run_metadata.get("title", "GUI 探针报告"))
    overall = summary["verdict"]
    started = run_metadata.get("started_at")
    started_html = "开始时间 <b>" + _escape(started) + "</b>" if started else ""
    counts = (
        '<div class="counts">'
        + "<span>用例 <b>" + str(summary["total_cases"]) + "</b></span>"
        + "<span>通过 <b>" + str(summary["passed"]) + "</b></span>"
        + "<span>警告 <b>" + str(summary["warned"]) + "</b></span>"
        + "<span>失败 <b>" + str(summary["failed"]) + "</b></span>"
        + "<span>跳过 <b>" + str(summary["skipped"]) + "</b></span>"
        + "<span>异常 <b>" + str(summary["errors"]) + "</b></span>"
        + "<span>细项 <b>" + str(summary["passed_checks"]) + "/" + str(summary["total_checks"]) + "</b></span>"
        + started_html
        + "</div>"
    )
    return ('<header class="hero"><div><h1>' + title + "</h1>" + counts + "</div>"
            + '<div class="overall ' + overall + '-bg">' + _VERDICT_LABELS[overall] + "</div></header>")


# 只给渲染用的重字段不进 HTML 数据块：截图字节与元素矩形叠层已由「截图」区承载。
_HEAVY_ARTIFACT_KEYS = ("png_bytes", "rects")


def _jsonable_case(case: dict[str, Any]) -> dict[str, Any]:
    data = {key: value for key, value in case.items() if key != "artifacts"}
    data["artifacts"] = [
        {key: value for key, value in artifact.items() if key not in _HEAVY_ARTIFACT_KEYS}
        for artifact in case.get("artifacts") or []
    ]
    return data


def _jsonable_shots(shots: dict[str, Any] | None) -> dict[str, Any] | None:
    """数据块里的截图证据：去掉 contact sheet 字节，路径与尺寸保留。"""
    if not shots:
        return None
    return {key: value for key, value in shots.items() if key != "png_bytes"}


def _data_script(run_metadata: dict[str, Any], env: dict[str, Any],
                 summary: dict[str, Any], cases: list[dict[str, Any]],
                 shots: dict[str, Any] | None = None,
                 metrics: dict[str, Any] | None = None) -> str:
    payload = {
        "run": run_metadata,
        "env": env,
        "summary": summary,
        "cases": [_jsonable_case(case) for case in cases],
    }
    if shots is not None:
        payload["shots"] = _jsonable_shots(shots)
    if metrics is not None:
        payload["metrics"] = metrics
    text = json.dumps(payload, ensure_ascii=False, indent=2, default=str).replace("</", "<\\/")
    return '<script type="application/json" id="gui-probe-data">\n' + text + "\n</script>"


def render_report(record, shots=None) -> str:
    """从运行记录渲染自包含 HTML，判定由运行记录提供。"""
    run_metadata, cases, env, metrics = record.metadata, record.cases, record.env, record.metrics
    summary = record.summary()
    body = "\n".join(_case_section(index, case) for index, case in enumerate(cases, start=1))
    document = "\n".join([
        "<!DOCTYPE html>",
        '<html lang="zh-CN">',
        "<head>",
        '<meta charset="utf-8">',
        '<meta name="viewport" content="width=device-width, initial-scale=1">',
        "<title>" + _escape(run_metadata.get("title", "GUI 探针报告")) + "</title>",
        "<style>",
        _STYLE,
        "</style>",
        "</head>",
        "<body>",
        _hero(run_metadata, summary),
        '<section><h2>环境</h2>' + _kv_table(env, "未提供环境信息") + "</section>",
        _shots_section(shots),
        _performance_section(metrics),
        '<section><h2>用例明细</h2>'
        + (body if cases else '<p class="empty">本次运行没有用例</p>') + "</section>",
        '<section><h2>运行元数据</h2>' + _kv_table(run_metadata, "未提供运行元数据") + "</section>",
        _data_script(run_metadata, env, summary, cases, shots, metrics),
        "</body>",
        "</html>",
        "",
    ])
    return document