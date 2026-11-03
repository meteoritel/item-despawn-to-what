"""把一次运行里的截图拼成 contact sheet，并写一份纯清单的截图索引。

用途：GUI 探针只把截图当证据——按原始像素分页拼接，保留「序号 + 用例 + 截图名」标签，
另写 visual-summary.md 列出每张截图的序号、用例、尺寸与路径，供人和 Agent 直接读图确认界面。

边界：只做拼图与清单，不比较两张图、不产出 diff、不维护基线；单张截图解码失败只跳过该格，
解码错误记录在运行记录的 artifact_errors，其他格子继续生成。
"""

import os
import time
from pathlib import Path
from record import FILES

import bitmap_font
import pixels

_MARGIN = 16
_GAP = 14
_SHOTS_PER_PAGE = 2
_BORDER = 2
_CELL_PADDING = 12
_FONT_SCALE = 2
_LINE_HEIGHT = bitmap_font.GLYPH_HEIGHT * _FONT_SCALE + 6
_LABEL_HEIGHT = 2 * _LINE_HEIGHT

_SHEET_BACKGROUND = (27, 31, 39, 255)
_CELL_BACKGROUND = (35, 40, 51, 255)
_CELL_BORDER = (60, 66, 78, 255)
_PLACEHOLDER = (54, 46, 46, 255)
_TEXT_PRIMARY = (230, 233, 239, 255)
_TEXT_DIM = (140, 147, 160, 255)


def _collect(snapshot):
    """截图元数据始终来自运行记录，字节只在生成派生图时读取。"""
    return [dict(artifact, index=index) for index, artifact in enumerate(snapshot.screenshots(), 1)]


def _label_lines(shot):
    """两行标签：序号+用例、截图文件名；图像尺寸与缩放比走截图清单表格，不挤进图里。"""
    return "#%02d  %s" % (shot["index"], shot["case"]), shot["name"]


def _draw_cell(canvas, sheet_width, sheet_height, x, y, shot, image_width, image_height):
    """画一格原始像素截图；逐张解码，限制分页拼接的峰值内存。"""
    cell_width = image_width + 2 * (_BORDER + _CELL_PADDING)
    cell_height = _LABEL_HEIGHT + image_height + 2 * (_BORDER + _CELL_PADDING)
    pixels.fill_rect(canvas, sheet_width, sheet_height, x, y, cell_width, cell_height, _CELL_BORDER)
    inner_x = x + _BORDER
    inner_y = y + _BORDER
    pixels.fill_rect(canvas, sheet_width, sheet_height, inner_x, inner_y,
                     cell_width - 2 * _BORDER, cell_height - 2 * _BORDER, _CELL_BACKGROUND)
    text_x = inner_x + _CELL_PADDING
    first_line_y = inner_y + _CELL_PADDING
    title, detail = _label_lines(shot)
    bitmap_font.draw_text(canvas, sheet_width, sheet_height, text_x, first_line_y,
                          bitmap_font.fit_text(title, image_width, _FONT_SCALE),
                          _TEXT_PRIMARY, _FONT_SCALE)
    bitmap_font.draw_text(canvas, sheet_width, sheet_height, text_x,
                          first_line_y + _LINE_HEIGHT,
                          bitmap_font.fit_text(detail, image_width, _FONT_SCALE),
                          _TEXT_DIM, _FONT_SCALE)
    image_x = inner_x + _CELL_PADDING
    image_y = inner_y + _CELL_PADDING + _LABEL_HEIGHT
    try:
        width, height, rgba = pixels.decode_png(Path(shot["path"]).read_bytes())
    except (ValueError, OSError) as failure:
        shot["render_error"] = str(failure)
        pixels.fill_rect(canvas, sheet_width, sheet_height, image_x, image_y,
                         image_width, image_height, _PLACEHOLDER)
        bitmap_font.draw_text(canvas, sheet_width, sheet_height, image_x + 8, image_y + 8,
                              "decode failed", _TEXT_DIM, _FONT_SCALE)
        return
    offset_x = image_x + (image_width - width) // 2
    offset_y = image_y + (image_height - height) // 2
    pixels.blit(canvas, sheet_width, sheet_height, rgba, width, height,
                offset_x, offset_y)


def _write_contact_sheets(run_dir, shots):
    """每页纵向排列原图；首页保留固定文件名，后续页使用数字后缀。"""
    pages = []
    for start in range(0, len(shots), _SHOTS_PER_PAGE):
        group = shots[start:start + _SHOTS_PER_PAGE]
        sizes = []
        for shot in group:
            try:
                sizes.append(pixels.png_size(Path(shot["path"]).read_bytes()))
            except (ValueError, OSError) as failure:
                shot["render_error"] = str(failure)
                sizes.append((max(440, int(shot.get("width") or 0)),
                              max(248, int(shot.get("height") or 0))))
        image_width = max(width for width, _ in sizes)
        cell_heights = [_LABEL_HEIGHT + height + 2 * (_BORDER + _CELL_PADDING)
                        for _, height in sizes]
        sheet_width = 2 * _MARGIN + image_width + 2 * (_BORDER + _CELL_PADDING)
        sheet_height = 2 * _MARGIN + sum(cell_heights) + (len(group) - 1) * _GAP
        canvas = pixels.new_canvas(sheet_width, sheet_height, _SHEET_BACKGROUND)
        y = _MARGIN
        for shot, (_, height), cell_height in zip(group, sizes, cell_heights):
            _draw_cell(canvas, sheet_width, sheet_height, _MARGIN, y, shot, image_width, height)
            y += cell_height + _GAP
        name = Path(FILES["sheet"])
        if pages:
            name = name.with_stem(name.stem + "-%03d" % (len(pages) + 1))
        path = os.path.join(run_dir, name)
        with open(path, "wb") as handle:
            handle.write(pixels.encode_png(sheet_width, sheet_height, canvas))
        pages.append({"path": path, "width": sheet_width, "height": sheet_height,
                      "first": group[0]["index"], "last": group[-1]["index"]})
    return pages


def _read(path):
    if not path:
        return None
    with open(path, "rb") as handle:
        return handle.read()


def _write_list(run_dir, shots, pages):
    """写截图清单：只有序号、用例、截图名、尺寸与路径，没有比对结论。"""
    lines = [
        "# 截图清单",
        "",
        "- 运行目录：`" + os.path.abspath(run_dir).replace("\\", "/") + "`",
        "- 截图张数：%d" % len(shots),
        "- contact sheets（原始像素）：" + (", ".join(os.path.basename(page["path"]) for page in pages)
                                         if pages else "（无截图）"),
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
    path = os.path.join(run_dir, FILES["visual"])
    with open(path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines))
    return path


def build(snapshot):
    """拼 contact sheet 并写截图清单。

    contact_sheets 列出全部原始像素分页；contact_sheet 与 png_bytes 指向首页。
    """
    started = time.perf_counter()
    run_dir = snapshot.directory
    shots = _collect(snapshot)
    pages = _write_contact_sheets(run_dir, shots)
    sheet_path = pages[0]["path"] if pages else None
    errors = [{"role":"screenshot", "id":shot["id"], "error":shot["render_error"]}
              for shot in shots if "render_error" in shot]
    list_path = _write_list(run_dir, shots, pages)
    return {
        "artifact_errors": errors,
        "contact_sheet": sheet_path,
        "contact_sheets": pages,
        "page_png_bytes": [_read(page["path"]) for page in pages],
        "png_bytes": _read(sheet_path),
        "list_path": list_path,
        "count": len(shots),
        "shots": [{key: value for key, value in shot.items() if key != "png_bytes"}
                  for shot in shots],
        "sheet_ms": int((time.perf_counter() - started) * 1000),
    }
