"""把一次运行里的截图拼成 contact sheet，并写一份纯清单的截图索引。

用途：GUI 探针只把截图当证据——拼一张带「序号 + 用例 + 截图名」标签的缩略图总览，
另写 visual-summary.md 列出每张截图的序号、用例、尺寸与路径，供人和 Agent 直接读图确认界面。

边界：只做拼图与清单，不比较两张图、不产出 diff、不维护基线；单张截图解码失败只跳过该格，
不影响其它格子和整轮运行。
"""

import math
import os
import time

import bitmap_font
import pixels

_MARGIN = 16
_GAP = 14
_COLUMNS = 3
_BORDER = 2
_CELL_PADDING = 12
_IMAGE_WIDTH = 440
_IMAGE_HEIGHT = 248
_FONT_SCALE = 2
_LINE_HEIGHT = bitmap_font.GLYPH_HEIGHT * _FONT_SCALE + 6
_LABEL_HEIGHT = 2 * _LINE_HEIGHT
_INNER_WIDTH = _IMAGE_WIDTH
_CELL_WIDTH = _INNER_WIDTH + 2 * (_BORDER + _CELL_PADDING)
_CELL_HEIGHT = _LABEL_HEIGHT + _IMAGE_HEIGHT + 2 * (_BORDER + _CELL_PADDING)

_SHEET_BACKGROUND = (27, 31, 39, 255)
_CELL_BACKGROUND = (35, 40, 51, 255)
_CELL_BORDER = (60, 66, 78, 255)
_PLACEHOLDER = (54, 46, 46, 255)
_TEXT_PRIMARY = (230, 233, 239, 255)
_TEXT_DIM = (140, 147, 160, 255)


def _collect(cases):
    """按用例顺序收集截图证据：只认 kind=screenshot 且字节已读入的 artifact。"""
    shots = []
    for case in cases:
        for artifact in case.get("artifacts") or []:
            if artifact.get("kind") != "screenshot":
                continue
            png = artifact.get("png_bytes")
            if not png:
                continue
            shots.append({
                "index": len(shots) + 1,
                "case": str(case.get("name", "")),
                "name": str(artifact.get("name", "")),
                "path": artifact.get("path"),
                "thumb": artifact.get("thumb"),
                "width": artifact.get("width"),
                "height": artifact.get("height"),
                "scale": artifact.get("scale"),
                "png_bytes": png,
            })
    return shots


def _label_lines(shot):
    """两行标签：序号+用例、截图文件名；图像尺寸与缩放比走截图清单表格，不挤进图里。"""
    return "#%02d  %s" % (shot["index"], shot["case"]), shot["name"]


def _draw_cell(canvas, sheet_width, sheet_height, x, y, shot):
    """画一格：外边框 + 两行标签 + 等比缩放后的截图；截图解不开时画占位底色。"""
    pixels.fill_rect(canvas, sheet_width, sheet_height, x, y, _CELL_WIDTH, _CELL_HEIGHT, _CELL_BORDER)
    inner_x = x + _BORDER
    inner_y = y + _BORDER
    pixels.fill_rect(canvas, sheet_width, sheet_height, inner_x, inner_y,
                     _CELL_WIDTH - 2 * _BORDER, _CELL_HEIGHT - 2 * _BORDER, _CELL_BACKGROUND)
    text_x = inner_x + _CELL_PADDING
    first_line_y = inner_y + _CELL_PADDING
    title, detail = _label_lines(shot)
    bitmap_font.draw_text(canvas, sheet_width, sheet_height, text_x, first_line_y,
                          bitmap_font.fit_text(title, _INNER_WIDTH, _FONT_SCALE),
                          _TEXT_PRIMARY, _FONT_SCALE)
    bitmap_font.draw_text(canvas, sheet_width, sheet_height, text_x,
                          first_line_y + _LINE_HEIGHT,
                          bitmap_font.fit_text(detail, _INNER_WIDTH, _FONT_SCALE),
                          _TEXT_DIM, _FONT_SCALE)
    image_x = inner_x + _CELL_PADDING
    image_y = inner_y + _CELL_PADDING + _LABEL_HEIGHT
    try:
        width, height, rgba = pixels.decode_png(bytes(shot["png_bytes"]))
        fit_width, fit_height, fitted = pixels.scale_to_fit(width, height, rgba,
                                                           _IMAGE_WIDTH, _IMAGE_HEIGHT)
    except (ValueError, OSError):
        pixels.fill_rect(canvas, sheet_width, sheet_height, image_x, image_y,
                         _IMAGE_WIDTH, _IMAGE_HEIGHT, _PLACEHOLDER)
        bitmap_font.draw_text(canvas, sheet_width, sheet_height, image_x + 8, image_y + 8,
                              "decode failed", _TEXT_DIM, _FONT_SCALE)
        return
    offset_x = image_x + (_IMAGE_WIDTH - fit_width) // 2
    offset_y = image_y + (_IMAGE_HEIGHT - fit_height) // 2
    pixels.blit(canvas, sheet_width, sheet_height, fitted, fit_width, fit_height,
                offset_x, offset_y)


def _write_contact_sheet(run_dir, shots):
    """把全部截图拼成一张网格图，返回落盘路径；没有截图时返回 None。"""
    if not shots:
        return None
    rows = math.ceil(len(shots) / _COLUMNS)
    sheet_width = 2 * _MARGIN + _COLUMNS * _CELL_WIDTH + (_COLUMNS - 1) * _GAP
    sheet_height = 2 * _MARGIN + rows * _CELL_HEIGHT + (rows - 1) * _GAP
    canvas = pixels.new_canvas(sheet_width, sheet_height, _SHEET_BACKGROUND)
    for position, shot in enumerate(shots):
        column = position % _COLUMNS
        row = position // _COLUMNS
        x = _MARGIN + column * (_CELL_WIDTH + _GAP)
        y = _MARGIN + row * (_CELL_HEIGHT + _GAP)
        _draw_cell(canvas, sheet_width, sheet_height, x, y, shot)
    path = os.path.join(run_dir, "gui-contact-sheet.png")
    with open(path, "wb") as handle:
        handle.write(pixels.encode_png(sheet_width, sheet_height, canvas))
    return path


def _read(path):
    if not path:
        return None
    with open(path, "rb") as handle:
        return handle.read()


def _write_list(run_dir, shots, sheet_path):
    """写截图清单：只有序号、用例、截图名、尺寸与路径，没有比对结论。"""
    lines = [
        "# 截图清单",
        "",
        "- 运行目录：`" + os.path.abspath(run_dir).replace("\\", "/") + "`",
        "- 截图张数：%d" % len(shots),
        "- contact sheet：" + (os.path.basename(sheet_path) if sheet_path else "（无截图）"),
        "",
        "| # | 用例 | 截图 | 图像尺寸@缩放 | 文件 |",
        "| --- | --- | --- | --- | --- |",
    ]
    for shot in shots:
        lines.append("| %02d | %s | %s | %d×%d @%s | `%s` |"
                     % (shot["index"], shot["case"], shot["name"],
                        int(shot.get("width") or 0), int(shot.get("height") or 0),
                        shot.get("scale"), shot.get("path")))
    lines.append("")
    path = os.path.join(run_dir, "visual-summary.md")
    with open(path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines))
    return path


def build(run_dir, cases):
    """拼 contact sheet 并写截图清单。

    返回 {contact_sheet, list_path, shots, count, sheet_ms}；没有截图时 contact_sheet 为 None。
    """
    started = time.perf_counter()
    shots = _collect(cases)
    sheet_path = _write_contact_sheet(run_dir, shots)
    list_path = _write_list(run_dir, shots, sheet_path)
    return {
        "contact_sheet": sheet_path,
        "png_bytes": _read(sheet_path),
        "list_path": list_path,
        "count": len(shots),
        "shots": [{key: value for key, value in shot.items() if key != "png_bytes"}
                  for shot in shots],
        "sheet_ms": int((time.perf_counter() - started) * 1000),
    }