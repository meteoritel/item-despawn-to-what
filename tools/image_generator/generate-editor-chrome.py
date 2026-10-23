#!/usr/bin/env python3
"""生成像素主题的结构贴图：立体边掩码与滚动条轨道/滑块。

产出：common/src/main/resources/assets/itemdespawntowhat/textures/gui/editor/chrome.png

图集布局（固定，Java 侧 theme/UiTheme.java 按同一规格切图）：
- 四格 8x8 横向排列，无内边距：
  0 = 立体边亮边掩码（上、左），1 = 立体边暗边掩码（下、右），
  2 = 滚动条轨道，3 = 滚动条滑块
- 两张立体边掩码是纯白字形，颜色由 drawBevel 按调用方传入的高光/阴影色染色，
  因此调色板换色时不需要重新生成立体边；掩码的右下/右上拐角留空，
  与原先纯色绘制的像素排布一致。
- 滚动条按 UiPalette 的取值直接生成：轨道用内凹槽位色，滑块用控件高光/阴影色。
  调色板常量缺失或格式不符即抛错终止，不回落旧字面量。
- 九宫格角区 1：四条边各 1 像素，中间区域在绘制时拉伸。

只用 Pillow 绘制，纯整数像素坐标，保证可重复生成的产物稳定。
"""

from __future__ import annotations

import argparse
import re
from pathlib import Path

from PIL import Image, ImageDraw

TILE = 8
TRANSPARENT = (0, 0, 0, 0)
WHITE = (255, 255, 255, 255)

# 与 client/ui/theme/UiPalette.java 对齐的取值：调色板是单一事实源，脚本只负责烘焙。
PALETTE_ENTRY = re.compile(r"^\s*public\s+static\s+final\s+int\s+(\w+)\s*=\s*0x([0-9A-Fa-f]{8})\s*;", re.MULTILINE)
PALETTE_CONSTANTS = (
    "SCROLL_TRACK",
    "SCROLL_THUMB",
    "SLOT_BORDER_DARK",
    "SLOT_BORDER_LIGHT",
    "CONTROL_BEVEL_LIGHT",
    "CONTROL_BEVEL_DARK",
)

# 图集格序：与 Java 侧 chrome() 的下标对应。
TILES = (
    ("bevel_light", 0),
    ("bevel_dark", 8),
    ("scroll_track", 16),
    ("scroll_thumb", 24),
)


def load_palette(path: Path) -> dict[str, tuple[int, int, int, int]]:
    """解析 UiPalette 里约定的 ARGB 常量，返回 {常量名: RGBA}。"""
    if not path.is_file():
        raise RuntimeError(f"未找到调色板文件：{path}")
    declared = {name: value for name, value in PALETTE_ENTRY.findall(path.read_text(encoding="utf-8"))}
    palette: dict[str, tuple[int, int, int, int]] = {}
    for name in PALETTE_CONSTANTS:
        value = declared.get(name)
        if value is None:
            raise RuntimeError(f"调色板 {path.name} 中未找到形如 0xAARRGGBB 的常量 {name}")
        # 0xAARRGGBB -> RGBA；界面贴图全部不透明，alpha 仍按声明保留。
        palette[name] = (
            int(value[2:4], 16),
            int(value[4:6], 16),
            int(value[6:8], 16),
            int(value[0:2], 16),
        )
    return palette


def _fill(d: ImageDraw.ImageDraw, x0: int, y0: int, x1: int, y1: int, color) -> None:
    """闭区间填充：包含右下边界像素。"""
    d.rectangle((x0, y0, x1, y1), fill=color)


def _bevel_light(d: ImageDraw.ImageDraw) -> None:
    """上、左两条 1 像素亮边；右上与左下拐角留空，避免和暗边抢同一像素。"""
    _fill(d, 0, 0, TILE - 2, 0, WHITE)
    _fill(d, 0, 1, 0, TILE - 2, WHITE)


def _bevel_dark(d: ImageDraw.ImageDraw) -> None:
    """下、右两条 1 像素暗边；拐角归属与亮边互补，拼合后是完整外框。"""
    _fill(d, 1, TILE - 1, TILE - 1, TILE - 1, WHITE)
    _fill(d, TILE - 1, 1, TILE - 1, TILE - 2, WHITE)


def _inset_tile(d: ImageDraw.ImageDraw, fill, top_left, bottom_right) -> None:
    """内凹槽：上、左为阴影，下、右为高光，四角按边归属拼成完整 1 像素框。"""
    _fill(d, 0, 0, TILE - 1, TILE - 1, fill)
    _fill(d, 0, 0, TILE - 1, 0, top_left)
    _fill(d, 0, 1, 0, TILE - 2, top_left)
    _fill(d, 0, TILE - 1, TILE - 1, TILE - 1, bottom_right)
    _fill(d, TILE - 1, 1, TILE - 1, TILE - 2, bottom_right)


def _raised_tile(d: ImageDraw.ImageDraw, fill, top_left, bottom_right) -> None:
    """外凸滑块：上、左为高光，下、右为阴影，四角按边归属拼成完整 1 像素框。"""
    _fill(d, 0, 0, TILE - 1, TILE - 1, fill)
    _fill(d, 0, 0, TILE - 1, 0, top_left)
    _fill(d, 0, 1, 0, TILE - 2, top_left)
    _fill(d, 0, TILE - 1, TILE - 1, TILE - 1, bottom_right)
    _fill(d, TILE - 1, 1, TILE - 1, TILE - 2, bottom_right)


def render(palette: dict[str, tuple[int, int, int, int]]) -> Image.Image:
    image = Image.new("RGBA", (TILE * len(TILES), TILE), TRANSPARENT)
    painters = {
        "bevel_light": _bevel_light,
        "bevel_dark": _bevel_dark,
        "scroll_track": lambda d: _inset_tile(
            d, palette["SCROLL_TRACK"], palette["SLOT_BORDER_DARK"], palette["SLOT_BORDER_LIGHT"]),
        "scroll_thumb": lambda d: _raised_tile(
            d, palette["SCROLL_THUMB"], palette["CONTROL_BEVEL_LIGHT"], palette["CONTROL_BEVEL_DARK"]),
    }
    for name, x in TILES:
        cell = Image.new("RGBA", (TILE, TILE), TRANSPARENT)
        painters[name](ImageDraw.Draw(cell))
        image.paste(cell, (x, 0))
    return image


def main() -> None:
    parser = argparse.ArgumentParser(description="生成像素主题的立体边与滚动条贴图")
    parser.add_argument("--output-dir", default=None, help="输出目录，默认写入模组资源目录")
    parser.add_argument("--palette", default=None, help="UiPalette.java 路径，默认取仓库内文件")
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[2]
    palette_path = Path(args.palette) if args.palette else (
        repo_root / "common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/theme/UiPalette.java")
    palette = load_palette(palette_path)

    out_dir = Path(args.output_dir) if args.output_dir else (
        repo_root / "common/src/main/resources/assets/itemdespawntowhat/textures/gui/editor")
    out_dir.mkdir(parents=True, exist_ok=True)
    target = out_dir / "chrome.png"
    render(palette).save(target, format="PNG", optimize=False)
    print(f"wrote {target} ({TILE * len(TILES)}x{TILE}, {len(TILES)} tiles)")


if __name__ == "__main__":
    main()
