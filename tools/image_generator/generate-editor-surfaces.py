"""生成白色填充与描边掩码；圆角几何与 UiTheme 的九宫格规格一致。"""
import argparse
from pathlib import Path

from atlas import textures_dir, require_pillow, save, run
SIZE = 32
SCALE = 8


def main():
    """每种圆角生成互补的填充/描边，运行期分别染色。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, help="输出目录，默认写入模组资源目录")
    args = parser.parse_args()
    output_dir = args.output_dir if args.output_dir is not None else textures_dir()
    Image, ImageDraw = require_pillow()
    from PIL import ImageChops

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
            target = save(image, output_dir, name + "_" + part + ".png")
            print(f"wrote {target} ({SIZE}x{SIZE})")


if __name__ == "__main__":
    run(main)
