#!/usr/bin/env python3
"""生成条件树画布编辑页的图标贴图。

产出：common/src/main/resources/assets/itemdespawntowhat/textures/gui/editor/icons_<scheme>.png

图集布局（固定，Java 侧按同一规格切图）：
- 单元格 16x16 像素，无内边距
- 行 = 图标，行序见 ICON_ROWS
- 列 = 状态：0 = 常态，1 = 禁用
- 颜色方案：dark = 深色字形（用于浅色底），light = 浅色字形（用于深色底）

只用 Pillow 绘制，纯整数像素坐标，保证可重复生成的产物稳定。
"""

from __future__ import annotations

import argparse
import math
from pathlib import Path

from PIL import Image, ImageDraw

CELL = 16
COLUMNS = 2  # 常态、禁用
TRANSPARENT = (0, 0, 0, 0)

# 与 client/ui/theme/UiPalette.java 对齐的灰阶方案
SCHEMES = {
    "dark": {"normal": (0x1E, 0x1E, 0x1E, 255), "disabled": (0x7A, 0x7A, 0x7A, 255), "accent": (0x6E, 0x86, 0xA8, 255)},
    "light": {"normal": (0xF2, 0xF2, 0xF2, 255), "disabled": (0x8E, 0x8E, 0x8E, 255), "accent": (0x9F, 0xB6, 0xCE, 255)},
}

ICON_ROWS = [
    "undo",
    "redo",
    "view_tree",
    "view_json",
    "help",
    "add_condition",
    "add_group",
    "invert",
    "remove",
    "move_up",
    "move_down",
    "wrap",
    "add_group_any",
    "wrap_any",
]

# ---- 基础绘制工具：全部按整数像素落笔 ----

def _h(d, x0, x1, y, color):
    if x1 < x0:
        x0, x1 = x1, x0
    d.line((x0, y, x1, y), fill=color)


def _v(d, x, y0, y1, color):
    if y1 < y0:
        y0, y1 = y1, y0
    d.line((x, y0, x, y1), fill=color)


def _rect(d, x0, y0, x1, y1, color, fill=False):
    d.rectangle((x0, y0, x1, y1), outline=color, fill=color if fill else None)


def _poly(d, points, color):
    d.polygon(points, fill=color)


def _circle(d, cx, cy, r, color, start_deg=0.0, end_deg=360.0, step_deg=12.0):
    """按角度采样画圆/圆弧，保证落在整数像素上。"""
    pts = []
    angle = start_deg
    while angle <= end_deg + 1e-9:
        rad = math.radians(angle)
        pts.append((round(cx + r * math.cos(rad)), round(cy + r * math.sin(rad))))
        angle += step_deg
    last = pts[0]
    for pt in pts[1:]:
        d.line((last[0], last[1], pt[0], pt[1]), fill=color)
        last = pt


def _px(d, x, y, color):
    d.point((x, y), fill=color)


# ---- 各图标字形：坐标固定，改动即改图 ----

def icon_undo(d, c, a):
    # U 形回转 + 左端向上箭头：正典的「撤销」形状
    _circle(d, 8, 7, 5, c, start_deg=0.0, end_deg=180.0, step_deg=10.0)
    _v(d, 3, 4, 6, c)
    _poly(d, [(1, 5), (5, 5), (3, 2)], c)


def icon_redo(d, c, a):
    _circle(d, 8, 7, 5, c, start_deg=0.0, end_deg=180.0, step_deg=10.0)
    _v(d, 13, 4, 6, c)
    _poly(d, [(11, 5), (15, 5), (13, 2)], c)


def icon_view_tree(d, c, a):
    _rect(d, 6, 2, 9, 4, c, fill=True)
    _v(d, 7, 5, 7, c)
    _h(d, 3, 12, 7, c)
    _v(d, 3, 8, 10, c)
    _v(d, 12, 8, 10, c)
    _rect(d, 1, 11, 5, 13, c, fill=True)
    _rect(d, 10, 11, 14, 13, c, fill=True)


def icon_view_json(d, c, a):
    # 左「{」：竖直主干偏左，顶/底横线向右延伸，中部尖点在主干右侧
    _v(d, 3, 3, 12, c)
    _h(d, 3, 4, 3, c)
    _h(d, 3, 4, 12, c)
    _px(d, 4, 7, c)
    _px(d, 4, 8, c)
    # 右「}」：与左侧沿图标中线镜像
    _v(d, 12, 3, 12, c)
    _h(d, 11, 12, 3, c)
    _h(d, 11, 12, 12, c)
    _px(d, 11, 7, c)
    _px(d, 11, 8, c)


def icon_help(d, c, a):
    _circle(d, 8, 6, 3, c, start_deg=180.0, end_deg=360.0, step_deg=15.0)
    _px(d, 11, 6, c)
    _px(d, 10, 7, c)
    _px(d, 9, 8, c)
    _v(d, 8, 9, 10, c)
    _v(d, 8, 12, 13, c)


def icon_add_condition(d, c, a):
    _h(d, 3, 12, 7, a)
    _h(d, 3, 12, 8, a)
    _v(d, 7, 3, 12, a)
    _v(d, 8, 3, 12, a)


# ---- 分组图标族：容器 + 内部三个小方块 ----
# 三个方块按 2+1 排布：14x14 外框（1..14）内部可用区为 12x12，
# 横排三个 4px 方块连间隙需要 14px 放不下，2+1 是唯一的整数像素居中排布。
_GROUP_BLOCKS = ((3, 3), (9, 3), (6, 9))
_GROUP_BLOCK_SIZE = 4


def _group_blocks(d, color, filled):
    """在容器内部画三个小方块；filled 为假时画空心方块，用于「任一满足」变体。"""
    for x, y in _GROUP_BLOCKS:
        _rect(d, x, y, x + _GROUP_BLOCK_SIZE - 1, y + _GROUP_BLOCK_SIZE - 1, color, fill=filled)


def _container_frame(d, color):
    # 完整方框：条件收进一个容器
    _rect(d, 1, 1, 14, 14, color)


def _container_brackets(d, color):
    # 左右相向的方括号：与方框同族，表达「把选中条件包成容器」
    _v(d, 1, 1, 14, color)
    _h(d, 1, 3, 1, color)
    _h(d, 1, 3, 14, color)
    _v(d, 14, 1, 14, color)
    _h(d, 12, 14, 1, color)
    _h(d, 12, 14, 14, color)


def icon_add_group(d, c, a):
    # 方框 + 三个实心方块：添加「全部满足」分组
    _container_frame(d, c)
    _group_blocks(d, c, True)


def icon_add_group_any(d, c, a):
    # 与 icon_add_group 同族，方块改空心：添加「任一满足」分组
    _container_frame(d, c)
    _group_blocks(d, c, False)


def icon_invert(d, c, a):
    _circle(d, 8, 8, 5, c, step_deg=10.0)
    d.line((5, 11, 11, 5), fill=c)


def icon_remove(d, c, a):
    _h(d, 3, 12, 3, c)
    _rect(d, 6, 1, 9, 2, c)
    _rect(d, 4, 4, 11, 13, c)
    _v(d, 6, 6, 11, c)
    _v(d, 9, 6, 11, c)


def icon_move_up(d, c, a):
    _poly(d, [(8, 2), (3, 8), (6, 8), (6, 13), (10, 13), (10, 8), (13, 8)], c)


def icon_move_down(d, c, a):
    _poly(d, [(8, 13), (3, 7), (6, 7), (6, 2), (10, 2), (10, 7), (13, 7)], c)


def icon_wrap(d, c, a):
    # 相向括号 + 三个实心方块：把选中条件包成「全部满足」分组
    _container_brackets(d, c)
    _group_blocks(d, c, True)


def icon_wrap_any(d, c, a):
    # 与 icon_wrap 同族，方块改空心：把选中条件包成「任一满足」分组
    _container_brackets(d, c)
    _group_blocks(d, c, False)


ICONS = {
    "undo": icon_undo,
    "redo": icon_redo,
    "view_tree": icon_view_tree,
    "view_json": icon_view_json,
    "help": icon_help,
    "add_condition": icon_add_condition,
    "add_group": icon_add_group,
    "invert": icon_invert,
    "remove": icon_remove,
    "move_up": icon_move_up,
    "move_down": icon_move_down,
    "wrap": icon_wrap,
    "add_group_any": icon_add_group_any,
    "wrap_any": icon_wrap_any,
}


def render(scheme: str) -> Image.Image:
    palette = SCHEMES[scheme]
    width = CELL * COLUMNS
    height = CELL * len(ICON_ROWS)
    image = Image.new("RGBA", (width, height), TRANSPARENT)
    for row, key in enumerate(ICON_ROWS):
        draw_fn = ICONS[key]
        for column, state in enumerate(("normal", "disabled")):
            cell = Image.new("RGBA", (CELL, CELL), TRANSPARENT)
            d = ImageDraw.Draw(cell)
            draw_fn(d, palette[state], palette["accent"])
            image.paste(cell, (column * CELL, row * CELL), cell)
    return image


def main() -> None:
    parser = argparse.ArgumentParser(description="生成条件树画布编辑页图标图集")
    parser.add_argument("--scheme", choices=sorted(SCHEMES), default="dark",
                        help="dark = 深色字形（浅色底），light = 浅色字形（深色底）")
    parser.add_argument("--output-dir", default=None, help="输出目录，默认写入模组资源目录")
    args = parser.parse_args()

    if args.output_dir:
        out_dir = Path(args.output_dir)
    else:
        repo_root = Path(__file__).resolve().parent.parent
        out_dir = repo_root / "common/src/main/resources/assets/itemdespawntowhat/textures/gui/editor"
    out_dir.mkdir(parents=True, exist_ok=True)
    target = out_dir / f"icons_{args.scheme}.png"
    render(args.scheme).save(target, format="PNG", optimize=False)
    print(f"wrote {target} ({CELL * COLUMNS}x{CELL * len(ICON_ROWS)}, {len(ICON_ROWS)} icons, {COLUMNS} states)")


if __name__ == "__main__":
    main()
