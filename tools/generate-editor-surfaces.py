"""生成编辑卡片与紧凑控件的抗锯齿九宫格底图。

取值来源
- 调色板来源：九宫格镜像 `client/ui/theme/UiPalette.java` 的常量，约束在 `PALETTE_CONSTANTS` 中，
  改动调色板后重跑本脚本即可对齐贴图；任一常量缺失或格式不符即抛错终止，不回落旧字面量。
  映射为 card 中心 ← CONTROL_FILL、control 中心 ← SLOT_FILL、hover 中心 ← CONTROL_HOVER、
  selected 中心 ← CONTROL_SELECTED 且描边 ← ACCENT_PRESSED、disabled 中心 ← CONTROL_DISABLED。
- 无调色板来源：chip 的中心色，以及 card / chip / control / hover / disabled 的描边色，
  作为脚本内具名常量维护，不随 UiPalette 变化。

版式约定：每张贴图 32x32、九宫格角区 8（与 `UiTheme.surface()` 的切片参数一致），
圆角由 8 倍超采样再降采样得到，因此边界带抗锯齿；降采样会把相邻的填充色带进最外一行，
成品边缘像素因此是描边色与填充色的抗锯齿混合，而不是描边色本身。
control 中心取槽位色 SLOT_FILL，使其在面板带（PANEL_FILL）与卡片底（CONTROL_FILL）两个宿主上都可辨。
"""

import re
import sys
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "common/src/main/resources/assets/itemdespawntowhat/textures/gui/editor"
PALETTE = ROOT / "common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/theme/UiPalette.java"

SIZE = 32
SCALE = 8
# 九宫格角区；圆角必须落在角区内，否则拉伸中段时会出现直边错位。
CORNER = 8
CARD_RADIUS = 6
CONTROL_RADIUS = 4

# 匹配一行 ARGB 常量声明；行首锚定可避开注释掉的旧声明。
PALETTE_ENTRY = re.compile(r"^\s*public\s+static\s+final\s+int\s+(\w+)\s*=\s*0x([0-9A-Fa-f]{8})\s*;", re.MULTILINE)
# 必须从调色板解析的常量，即九宫格镜像的全部调色板取值。
PALETTE_CONSTANTS = (
    "CONTROL_FILL",
    "PANEL_FILL",
    "SLOT_FILL",
    "CONTROL_HOVER",
    "CONTROL_SELECTED",
    "CONTROL_DISABLED",
    "ACCENT_PRESSED",
)
# #rrggbb 前缀：带该前缀的取值视为脚本内字面量，不查调色板。
LITERAL_PREFIX = "#"

# 无调色板来源的取值：chip 中心色与四类描边色，描边一律比同级填充暗。
# 成品上「中心像素 − 边缘像素」的差值延续既有量级：card 32、chip 35、hover 53、disabled 13；
# control 相对 SLOT_FILL 取 22，使未选中段在面板带与卡片底上都拉得出边界。
CHIP_FILL = "#d6d6d6"
OUTLINE_NEUTRAL = "#a3a3a3"
OUTLINE_CHIP = "#b0b0b0"
OUTLINE_CONTROL = "#737373"
OUTLINE_DISABLED = "#929292"

# 六张九宫格的生成清单：(贴图名, 中心色来源, 描边色来源, 圆角半径)。
# 来源为调色板常量名时从 UiPalette 解析，为 #rrggbb 时直接使用脚本内具名常量。
SURFACES = (
    ("card", "CONTROL_FILL", OUTLINE_NEUTRAL, CARD_RADIUS),
    ("chip", CHIP_FILL, OUTLINE_CHIP, CONTROL_RADIUS),
    ("control", "SLOT_FILL", OUTLINE_CONTROL, CONTROL_RADIUS),
    ("hover", "CONTROL_HOVER", OUTLINE_NEUTRAL, CONTROL_RADIUS),
    ("selected", "CONTROL_SELECTED", "ACCENT_PRESSED", CONTROL_RADIUS),
    ("disabled", "CONTROL_DISABLED", OUTLINE_DISABLED, CONTROL_RADIUS),
)


def load_palette(path):
    """解析调色板源文件里约定的 ARGB 常量，返回 {常量名: "#rrggbb"}。

    源文件缺失、常量缺失或取值不是 0xAARRGGBB 字面量时抛出 RuntimeError，
    避免贴图静默沿用旧色而与调色板脱节。
    """
    if not path.is_file():
        raise RuntimeError(f"未找到调色板文件：{path}")
    declared = {name: value for name, value in PALETTE_ENTRY.findall(path.read_text(encoding="utf-8"))}
    palette = {}
    for name in PALETTE_CONSTANTS:
        value = declared.get(name)
        if value is None:
            raise RuntimeError(f"调色板 {path.name} 中未找到形如 0xAARRGGBB 的常量 {name}")
        palette[name] = "#" + value[2:].lower()
    return palette


def resolve(source, palette):
    """把取值来源解析为 #rrggbb：调色板常量名查 palette，脚本内字面量原样返回。"""
    if source.startswith(LITERAL_PREFIX):
        return source
    if source not in palette:
        raise RuntimeError(f"取值来源 {source} 既不是调色板常量，也不是 {LITERAL_PREFIX}rrggbb 字面量")
    return palette[source]


def surface(name, fill, outline, radius):
    """绘制单张九宫格：先铺圆角描边，再内缩一格铺中心色，最后降采样出抗锯齿圆角。"""
    if radius >= CORNER:
        raise ValueError(f"{name} 的圆角 {radius} 必须小于九宫格角区 {CORNER}")
    image = Image.new("RGBA", (SIZE * SCALE, SIZE * SCALE))
    painter = ImageDraw.Draw(image)
    painter.rounded_rectangle((0, 0, SIZE * SCALE - 1, SIZE * SCALE - 1),
                              radius=radius * SCALE, fill=outline)
    painter.rounded_rectangle((SCALE, SCALE, (SIZE - 1) * SCALE - 1, (SIZE - 1) * SCALE - 1),
                              radius=(radius - 1) * SCALE, fill=fill)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    image = image.resize((SIZE, SIZE), Image.Resampling.LANCZOS)
    # 清掉重采样产生的极低透明度振铃，保留可见的圆角抗锯齿。
    image.putalpha(image.getchannel("A").point(lambda alpha: 0 if alpha < 4 else alpha))
    image.save(OUTPUT / f"{name}.png")


def main(argv=None):
    """生成全部九宫格贴图并打印每张的取值来源。

    可选参数为调色板文件路径，用于以替身文件校验解析行为，缺省时使用仓库内调色板。
    """
    args = list(sys.argv[1:] if argv is None else argv)
    palette = load_palette(Path(args[0]) if args else PALETTE)
    for name, fill_source, outline_source, radius in SURFACES:
        fill = resolve(fill_source, palette)
        outline = resolve(outline_source, palette)
        surface(name, fill, outline, radius)
        print(f"{name}.png 中心 {fill}（{fill_source}） 描边 {outline}（{outline_source}） 圆角 {radius}")


if __name__ == "__main__":
    main()
