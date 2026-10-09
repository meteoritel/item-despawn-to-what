"""生成编辑卡片与紧凑控件的抗锯齿九宫格底图。"""

from pathlib import Path
from PIL import Image, ImageDraw

OUTPUT = Path(__file__).resolve().parents[1] / "common/src/main/resources/assets/itemdespawntowhat/textures/gui/editor"
SIZE = 32
SCALE = 8


def surface(name, fill, outline, radius):
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


if __name__ == "__main__":
    surface("card", "#c6c6c6", "#a3a3a3", 6)
    surface("chip", "#d6d6d6", "#b0b0b0", 4)
    surface("control", "#c6c6c6", "#a3a3a3", 4)
    surface("hover", "#dcdcdc", "#a3a3a3", 4)
    surface("selected", "#6e86a8", "#5a708f", 4)
    surface("disabled", "#a0a0a0", "#929292", 4)
