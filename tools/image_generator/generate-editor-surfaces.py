"""生成白色填充与描边掩码；圆角几何与 UiTheme 的九宫格规格一致。"""
from PIL import ImageChops
from atlas import textures_dir, require_pillow, save, run
Image, ImageDraw = require_pillow()
SIZE = 32
SCALE = 8


def main():
    """每种圆角生成互补的填充/描边，运行期分别染色。"""
    for name, radius in (("card", 6), ("control", 4)):
        outer = Image.new("L", (SIZE * SCALE, SIZE * SCALE))
        inner = Image.new("L", outer.size)
        ImageDraw.Draw(outer).rounded_rectangle((0, 0, SIZE*SCALE-1, SIZE*SCALE-1), radius=radius*SCALE, fill=255)
        ImageDraw.Draw(inner).rounded_rectangle((SCALE, SCALE, (SIZE-1)*SCALE-1, (SIZE-1)*SCALE-1), radius=(radius-1)*SCALE, fill=255)
        outer = outer.resize((SIZE, SIZE), Image.Resampling.LANCZOS)
        inner = inner.resize((SIZE, SIZE), Image.Resampling.LANCZOS)
        for part in ("fill", "border"):
            alpha = inner if part == "fill" else ImageChops.subtract(outer, inner)
            image = Image.new("RGBA", (SIZE, SIZE), "white")
            image.putalpha(alpha)
            save(image, textures_dir(), name + "_" + part + ".png")


if __name__ == "__main__":
    run(main)
