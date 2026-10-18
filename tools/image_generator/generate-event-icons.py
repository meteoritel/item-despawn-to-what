"""事件图标：32x32 画布、单列、逐行一个事件。

输出 assets/itemdespawntowhat/textures/gui/editor/events.png，行序见 EVENTS，
与 theme/UiEventIcons.java 的 Event 枚举逐行对应。

图标是彩色成品而非白色字形：天气与法术本身带固有配色（日光、雨滴、雷光等），
染色交给主题反而会失真，因此只允许界面在禁用态统一压暗。
"""

import math
from pathlib import Path

from PIL import Image, ImageDraw

CELL = 32
TRANSPARENT = (0, 0, 0, 0)

# 与 theme/UiEventIcons.java 的 Event 枚举逐行对应：(名称,)
EVENTS = (
    ("clear",),
    ("rain",),
    ("thunder",),
    ("lightning",),
    ("arrow_rain",),
)

# ---- 配色：每个事件 2-3 个色阶，保证 16 像素下仍有层次 ----
SUN_FILL = (255, 205, 66, 255)
SUN_EDGE = (240, 160, 24, 255)
CLOUD = (186, 197, 212, 255)
CLOUD_DARK = (116, 128, 148, 255)
CLOUD_EDGE = (96, 108, 128, 255)
RAIN = (74, 163, 240, 255)
BOLT = (255, 214, 64, 255)
BOLT_EDGE = (232, 160, 16, 255)
# 箭矢沿用原版材质配色：金褐箭杆加深褐描边、浅灰金属箭头、灰白尾羽
ARROW_SHAFT = (137, 103, 39, 255)
ARROW_SHAFT_EDGE = (40, 30, 11, 255)
ARROW_HEAD = (216, 216, 216, 255)
ARROW_HEAD_LIGHT = (255, 255, 255, 255)
ARROW_HEAD_SHADE = (150, 150, 150, 255)
ARROW_METAL_EDGE = (63, 63, 63, 255)
ARROW_FLETCH = (232, 232, 232, 255)
ARROW_FLETCH_EDGE = (68, 68, 68, 255)


# ---- 基础绘制工具：全部按整数像素落笔 ----

def _fill(d, x0, y0, x1, y1, color):
    """闭区间填充：包含右下边界像素。"""
    d.rectangle((x0, y0, x1, y1), fill=color)


def _disc(d, cx, cy, radius, color):
    """实心圆：按像素中心距离判定，边缘不留锯齿台阶。"""
    for y in range(cy - radius, cy + radius + 1):
        for x in range(cx - radius, cx + radius + 1):
            if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= radius * radius:
                d.point((x, y), fill=color)


def _line(d, x0, y0, x1, y1, color, width):
    """等宽线段：按像素中心到线段的距离判定落笔。"""
    y0, y1 = int(round(y0)), int(round(y1))
    x0, x1 = int(round(x0)), int(round(x1))
    for y in range(min(y0, y1) - width, max(y0, y1) + width + 1):
        for x in range(min(x0, x1) - width, max(x0, x1) + width + 1):
            if _distance(x + 0.5, y + 0.5, x0, y0, x1, y1) <= width / 2:
                d.point((x, y), fill=color)


def _distance(px, py, x0, y0, x1, y1):
    dx, dy = x1 - x0, y1 - y0
    length = dx * dx + dy * dy
    if length == 0:
        return ((px - x0) ** 2 + (py - y0) ** 2) ** 0.5
    t = max(0.0, min(1.0, ((px - x0) * dx + (py - y0) * dy) / length))
    return ((px - (x0 + t * dx)) ** 2 + (py - (y0 + t * dy)) ** 2) ** 0.5


def _poly(d, points, color):
    d.polygon(points, fill=color)


def _quad(d, a, b, c, e, color):
    """四边形：用于沿任意方向延伸的箭杆。"""
    d.polygon((a, b, c, e), fill=color)


# ---- 各事件字形 ----

def event_clear(d):
    """晴天：居中的太阳圆盘加八向光芒。"""
    for step in range(8):
        angle = step * math.pi / 4
        _line(d, 16 + math.cos(angle) * 10.5, 16 + math.sin(angle) * 10.5,
              16 + math.cos(angle) * 14, 16 + math.sin(angle) * 14, SUN_EDGE, 3)
    _disc(d, 16, 16, 8, SUN_FILL)
    _disc(d, 16, 16, 5, SUN_EDGE)


def event_rain(d):
    """下雨：浅灰云层加三条斜向雨丝。"""
    _cloud(d, CLOUD, CLOUD_EDGE, top=5, bottom=16, left=4, right=28)
    for x in (9, 16, 23):
        _line(d, x + 2, 19, x - 2, 28, RAIN, 2)


def event_thunder(d):
    """雷雨：暗云加黄色闪电，附少量雨丝与下雨区分。"""
    _cloud(d, CLOUD_DARK, CLOUD_EDGE, top=4, bottom=14, left=4, right=28)
    _line(d, 8, 17, 6, 24, RAIN, 2)
    _line(d, 25, 17, 23, 24, RAIN, 2)
    _bolt(d, BOLT, BOLT_EDGE, top=13, bottom=27, cx=16)


def event_lightning(d):
    """闪电：无云的大号闪电，靠轮廓独立成图。"""
    _bolt(d, BOLT, BOLT_EDGE, top=4, bottom=27, cx=16)


def event_arrow_rain(d):
    """箭雨：三支同向斜落的箭，交错排布出成片下落的密度。"""
    _arrow(d, (3, 10), (16, 23), 0.9)
    _arrow(d, (11, 1), (23, 13), 0.8)
    _arrow(d, (20, 18), (27, 25), 0.5)


# ---- 复合图形 ----

def _cloud(d, body, edge, top, bottom, left, right):
    """云层：三个圆顶加底部平边；先画放大一圈的轮廓，保证浅底上也有边界。"""
    _cloud_shape(d, edge, top - 1, bottom + 1, left - 1, right + 1)
    _cloud_shape(d, body, top, bottom, left, right)


def _cloud_shape(d, color, top, bottom, left, right):
    _disc(d, left + 6, top + 6, 6, color)
    _disc(d, (left + right) // 2, top + 4, 7, color)
    _disc(d, right - 6, top + 6, 6, color)
    _fill(d, left + 4, top + 6, right - 4, bottom, color)


def _bolt(d, body, edge, top, bottom, cx):
    """闪电：粗折线加一圈深色轮廓，两段折角形成经典闪电剪影。"""
    mid = top + (bottom - top) * 5 // 12
    path = ((cx + 6, top), (cx - 3, mid), (cx + 4, mid), (cx - 6, bottom))
    for width, color in ((7, edge), (5, body)):
        for start, end in zip(path, path[1:]):
            _line(d, start[0], start[1], end[0], end[1], color, width)


def _arrow(d, tail, head, scale):
    """按原版箭矢的构造画一支斜向箭：描边箭杆、金属箭头、双侧尾羽。

    tail 为尾羽端、head 为箭头尖端；scale 同时缩放杆宽、箭头与尾羽，
    这样三支不同尺寸的箭能叠出远近层次，而不必为每种尺寸另写一套坐标。
    """
    dx, dy = head[0] - tail[0], head[1] - tail[1]
    length = math.hypot(dx, dy)
    ux, uy = dx / length, dy / length
    px, py = -uy, ux

    def at(along, perp):
        return (tail[0] + ux * along + px * perp, tail[1] + uy * along + py * perp)

    head_len = 6.0 * scale
    head_half = 2.8 * scale
    shaft_end = length - head_len * 0.8
    # 箭杆：先粗描边再压细芯，得到原版那种一像素深褐轮廓
    # 箭杆只在背光侧压一道深褐边：全包边会让小尺寸下的箭看起来发黑
    _quad(d, at(0, 2.4 * scale), at(shaft_end, 2.4 * scale),
          at(shaft_end, 0.8 * scale), at(0, 0.8 * scale), ARROW_SHAFT_EDGE)
    _quad(d, at(0, 1.7 * scale), at(shaft_end, 1.7 * scale),
          at(shaft_end, -1.7 * scale), at(0, -1.7 * scale), ARROW_SHAFT)
    # 尾羽：两侧各一片，外缘深灰、内芯灰白
    for side in (1, -1):
        _poly(d, (at(0.3 * scale, 0.8 * scale * side), at(3.8 * scale, 2.9 * scale * side),
                  at(4.8 * scale, 0.8 * scale * side)), ARROW_FLETCH_EDGE)
        _poly(d, (at(1.1 * scale, 1.0 * scale * side), at(3.4 * scale, 2.4 * scale * side),
                  at(4.1 * scale, 1.0 * scale * side)), ARROW_FLETCH)
    # 箭头：深灰三角描边包住金属三角，左半高光、右下暗面
    _poly(d, (at(length + 1.1 * scale, 0), at(length - head_len + 0.9 * scale, head_half + 1.1 * scale),
              at(length - head_len + 0.9 * scale, -head_half - 1.1 * scale)), ARROW_METAL_EDGE)
    _poly(d, (head, at(length - head_len, head_half), at(length - head_len, -head_half)), ARROW_HEAD)
    _poly(d, (head, at(length - head_len, head_half), at(length - head_len, 0.2 * head_half)), ARROW_HEAD_LIGHT)
    _poly(d, (head, at(length - head_len, -0.5 * head_half), at(length - head_len, -head_half)), ARROW_HEAD_SHADE)


HANDLERS = {
    "clear": event_clear,
    "rain": event_rain,
    "thunder": event_thunder,
    "lightning": event_lightning,
    "arrow_rain": event_arrow_rain,
}


def render() -> Image.Image:
    image = Image.new("RGBA", (CELL, CELL * len(EVENTS)), TRANSPARENT)
    for row, (name,) in enumerate(EVENTS):
        cell = Image.new("RGBA", (CELL, CELL), TRANSPARENT)
        HANDLERS[name](ImageDraw.Draw(cell))
        image.paste(cell, (0, row * CELL))
    return image


def main() -> None:
    root = Path(__file__).resolve().parents[2]
    target = root / "common/src/main/resources/assets/itemdespawntowhat/textures/gui/editor/events.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    image = render()
    image.save(target)
    print(f"wrote {target} ({CELL}x{CELL * len(EVENTS)}, {len(EVENTS)} events)")


if __name__ == "__main__":
    main()
