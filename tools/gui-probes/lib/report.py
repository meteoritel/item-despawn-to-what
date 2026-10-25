"""把一次 GUI 探针运行渲染成自包含单文件 HTML 报告。

用途：给人工复核和归档提供一份离线可打开的页面——截图 base64 内嵌，结构化数据放进
<script type="application/json">，在 file:// 下也不依赖任何外部脚本、样式或网络。

边界：只做渲染，不读文件、不写文件、不取时间和机器信息；路径、时间、环境都由调用方
传入。所有函数无副作用，每次调用返回新字符串。

输入：
- run_metadata：运行级键值（title / started_at / git_revision / build 之类），原样成表。
- cases：用例列表，每项形如
  {
    "name": str,
    "verdict": "pass" | "warn" | "fail" | "skip" | "error",
    "detail": str,                                  # 可选说明
    "checks": [{"name": str, "expected": str, "actual": str, "pass": bool}],
    "artifacts": [{                                 # 可选截图
        "name": str,
        "path": str,
        "png_bytes": bytes,                         # 可选；有则 base64 内嵌
        "rects": [{"x": int, "y": int, "w": int, "h": int, "label": str}],  # 可选元素矩形叠层
        "kind": "image" | "file",                   # 可选；file 只列出证据文件路径
    }],
  }
- env：环境一致性块（os / java / 分辨率 / gui_scale / 截图时间等）。

输出：(html, summary)。summary["verdict"] 是 pass / warn / fail / skip 之一，供编排层换算
退出码；明细计数见 passed / warned / failed / skipped / errors / total_checks / passed_checks。

渲染能力只在 render_report 里，且它本身不做 IO；读图与路径拼装由编排层负责。
"""

import base64
import html
import json
import os
from typing import Any

_VERDICT_LABELS = {"pass": "通过", "warn": "警告", "fail": "失败", "skip": "跳过", "error": "异常"}
_COUNT_KEYS = {"pass": "passed", "warn": "warned", "fail": "failed", "skip": "skipped", "error": "errors"}
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
.shots { display: flex; flex-wrap: wrap; gap: 18px; margin-top: 12px; }
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


def _rect(rect: dict[str, Any]) -> str:
    style = "left:{0}px;top:{1}px;width:{2}px;height:{3}px".format(
        int(rect.get("x", 0)), int(rect.get("y", 0)),
        int(rect.get("w", 0)), int(rect.get("h", 0)),
    )
    label = _escape(rect.get("label", ""))
    return '<span class="rect" style="' + style + '">' + ("<i>" + label + "</i>" if label else "") + "</span>"


def _artifact(artifact: dict[str, Any]) -> str:
    name = _escape(artifact.get("name", "截图"))
    path = _escape(artifact.get("path", ""))
    if artifact.get("kind") == "file":
        return '<p class="file">证据 <b>' + name + "</b><code>" + path + "</code></p>"
    caption = "<figcaption><b>" + name + "</b><code>" + path + "</code></figcaption>"
    png = artifact.get("png_bytes")
    if not png:
        return ('<figure class="shot"><div class="shot-frame"><span class="no-png">未内嵌 PNG</span></div>'
                + caption + "</figure>")
    overlay = "".join(_rect(rect) for rect in artifact.get("rects") or [])
    encoded = base64.b64encode(bytes(png)).decode("ascii")
    return ('<figure class="shot"><div class="shot-frame"><img src="data:image/png;base64,' + encoded
            + '" alt="' + name + '">' + overlay + "</div>" + caption + "</figure>")


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
        parts.append('<div class="shots">' + "".join(_artifact(item) for item in artifacts) + "</div>")
    parts.append("</article>")
    return "\n".join(parts)


def _summarize(cases: list[dict[str, Any]]) -> dict[str, Any]:
    """统计用例与细项判定，给出整体 verdict。"""
    counts = {"passed": 0, "warned": 0, "failed": 0, "skipped": 0, "errors": 0}
    total_checks = passed_checks = 0
    rows = []
    for case in cases:
        verdict = _verdict(case)
        counts[_COUNT_KEYS[verdict]] += 1
        checks = case.get("checks") or []
        passed = sum(1 for check in checks if check.get("pass") is True)
        total_checks += len(checks)
        passed_checks += passed
        rows.append({
            "name": str(case.get("name", "")),
            "verdict": verdict,
            "passed_checks": passed,
            "total_checks": len(checks),
        })
    if counts["failed"] or counts["errors"]:
        overall = "fail"
    elif counts["warned"]:
        overall = "warn"
    elif counts["passed"]:
        overall = "pass"
    else:
        overall = "skip"
    return {
        "verdict": overall,
        "total_cases": len(cases),
        "passed": counts["passed"],
        "warned": counts["warned"],
        "failed": counts["failed"],
        "skipped": counts["skipped"],
        "errors": counts["errors"],
        "total_checks": total_checks,
        "passed_checks": passed_checks,
        "cases": rows,
    }


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


def _jsonable_case(case: dict[str, Any]) -> dict[str, Any]:
    data = {key: value for key, value in case.items() if key != "artifacts"}
    data["artifacts"] = [
        {key: value for key, value in artifact.items() if key != "png_bytes"}
        for artifact in case.get("artifacts") or []
    ]
    return data


def _data_script(run_metadata: dict[str, Any], env: dict[str, Any],
                 summary: dict[str, Any], cases: list[dict[str, Any]]) -> str:
    payload = {
        "run": run_metadata,
        "env": env,
        "summary": summary,
        "cases": [_jsonable_case(case) for case in cases],
    }
    text = json.dumps(payload, ensure_ascii=False, indent=2, default=str).replace("</", "<\\/")
    return '<script type="application/json" id="gui-probe-data">\n' + text + "\n</script>"


def render_report(run_metadata: dict[str, Any], cases: list[dict[str, Any]],
                  env: dict[str, Any]) -> tuple[str, dict[str, Any]]:
    """渲染自包含 HTML 并汇总判定，返回 (html, summary)。"""
    summary = _summarize(cases)
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
        '<section><h2>运行元数据</h2>' + _kv_table(run_metadata, "未提供运行元数据") + "</section>",
        '<section><h2>用例明细</h2>'
        + (body if cases else '<p class="empty">本次运行没有用例</p>') + "</section>",
        _data_script(run_metadata, env, summary, cases),
        "</body>",
        "</html>",
        "",
    ])
    return document, summary
