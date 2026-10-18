#!/usr/bin/env python3
"""生成编辑器界面的小标记贴图。

产出：common/src/main/resources/assets/itemdespawntowhat/textures/gui/editor/marks.png

图集布局（固定，Java 侧按同一规格切图）：
- 单元格 16x16 像素，无内边距；每行一个标记，行序见 MARKS
- 单列：标记为纯白字形，颜色由宿主主题在绘制时染色（RenderSystem 着色），
  因此不再区分深色/浅色方案，也不再出禁用态

每个标记在单元格左上角逐像素落笔，尺寸由 MARKS 的 width/height 声明；
Java 侧 theme/UiMarkIcons.java 用同一张尺寸表定位子区域，绘制时不做缩放。
只用 Pillow 绘制，纯整数像素坐标，保证可重复生成的产物稳定。
"""

from __future__ import annotations

import argparse
import math
from pathlib import Path

from PIL import Image, ImageDraw

CELL = 16
GLYPH = (255, 255, 255, 255)
TRANSPARENT = (0, 0, 0, 0)

# 与 theme/UiMarkIcons.java 的 Mark 枚举逐行对应：(名称, 宽, 高)
MARKS = (
    ("unavailable", 6, 9),
    ("check", 8, 8),
    ("close", 9, 9),
    ("previous", 8, 10),
    ("next", 8, 10),
    ("consumed_star", 7, 7),
)


# ---- 基础绘制工具：全部按整数像素落笔 ----

def _rect(d, x0, y0, x1, y1, color, fill=False):
    d.rectangle((x0, y0, x1, y1), outline=color, fill=color if fill else None)


def _fill(d, x0, y0, x1, y1, color):
    """闭区间填充：包含右下边界像素。"""
    d.rectangle((x0, y0, x1, y1), fill=color)


def _poly(d, points, color):
    d.polygon(points, fill=color)


def _stroke(d, points, radius, color):
    """按像素中心到线段的距离判定落笔，得到整数像素的等宽斜线。"""
    for start, end in zip(points, points[1:]):
        x0, y0 = start
        x1, y1 = end
        for y in range(min(y0, y1) - 2, max(y0, y1) + 3):
            for x in range(min(x0, x1) - 2, max(x0, x1) + 3):
                if _distance(x + 0.5, y + 0.5, x0, y0, x1, y1) <= radius:
                    d.point((x, y), fill=color)


def _distance(px, py, x0, y0, x1, y1):
    dx, dy = x1 - x0, y1 - y0
    length = dx * dx + dy * dy
    if length == 0:
        return math.hypot(px - x0, py - y0)
    t = max(0.0, min(1.0, ((px - x0) * dx + (py - y0) * dy) / length))
    return math.hypot(px - (x0 + t * dx), py - (y0 + t * dy))


# ---- 各标记字形：坐标固定，改动即改图 ----

def mark_unavailable(d, c):
    """感叹号：条目存在但当前不可渲染。"""
    _fill(d, 2, 0, 3, 5, c)
    _fill(d, 2, 7, 3, 8, c)


def mark_check(d, c):
    """对勾：已选中。"""
    _stroke(d, ((1, 5), (3, 7)), 1.2, c)
    _stroke(d, ((3, 7), (7, 2)), 1.2, c)


def mark_close(d, c):
    """叉：取消 / 删除。"""
    _stroke(d, ((1, 1), (7, 7)), 1.0, c)
    _stroke(d, ((7, 1), (1, 7)), 1.0, c)


def mark_previous(d, c):
    """左向实心三角：上一页。"""
    _poly(d, ((0, 4), (7, 0), (7, 9)), c)


def mark_next(d, c):
    """右向实心三角：下一页。"""
    _poly(d, ((7, 4), (0, 0), (0, 9)), c)


def mark_consumed_star(d, c):
    """五角星：对象被配置为消耗。"""
    for row, spans in enumerate((
            ((3, 3),), ((3, 3),), ((0, 6),), ((1, 5),), ((2, 4),),
            ((1, 2), (4, 5)), ((0, 1), (5, 6)))):
        for left, right in spans:
            _fill(d, left, row, right, row, c)


ICONS = {
    "unavailable": mark_unavailable,
    "check": mark_check,
    "close": mark_close,
    "previous": mark_previous,
    "next": mark_next,
    "consumed_star": mark_consumed_star,
}


def render() -> Image.Image:
    image = Image.new("RGBA", (CELL, CELL * len(MARKS)), TRANSPARENT)
    for row, (name, width, height) in enumerate(MARKS):
        cell = Image.new("RGBA", (CELL, CELL), TRANSPARENT)
        d = ImageDraw.Draw(cell)
        ICONS[name](d, GLYPH)
        image.paste(cell.crop((0, 0, width, height)), (0, row * CELL), cell.crop((0, 0, width, height)))
    return image


def main() -> None:
    parser = argparse.ArgumentParser(description="生成编辑器界面的标记贴图")
    parser.add_argument("--output-dir", default=None, help="输出目录，默认写入模组资源目录")
    args = parser.parse_args()

    if args.output_dir:
        out_dir = Path(args.output_dir)
    else:
        repo_root = Path(__file__).resolve().parents[2]
        out_dir = repo_root / "common/src/main/resources/assets/itemdespawntowhat/textures/gui/editor"
    out_dir.mkdir(parents=True, exist_ok=True)
    target = out_dir / "marks.png"
    render().save(target, format="PNG", optimize=False)
    print(f"wrote {target} ({CELL}x{CELL * len(MARKS)}, {len(MARKS)} marks)")


if __name__ == "__main__":
    main()
