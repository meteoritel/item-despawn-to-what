"""图集生成器的路径、调色板和像素操作；Pillow 仅在绘图入口加载。"""
from __future__ import annotations

import re
import sys
from pathlib import Path

_tools = next(parent for parent in Path(__file__).resolve().parents
              if (parent / "toolslib/__init__.py").is_file())
sys.path.insert(0, str(_tools))
from toolslib.repo import repo_root  # noqa: E402
from toolslib.checks import configure_console  # noqa: E402

PALETTE_ENTRY = re.compile(r"^\s*public\s+static\s+final\s+int\s+(\w+)\s*=\s*0x([0-9A-Fa-f]{8})\s*;", re.MULTILINE)


def textures_dir() -> Path:
    """返回编辑器图集的默认资源目录。"""
    return repo_root() / "common/src/main/resources/assets/itemdespawntowhat/textures/gui/editor"


def palette_path() -> Path:
    """返回主题调色板的单一事实源。"""
    return repo_root() / "common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/theme/UiPalette.java"


def load_palette(path: Path, required) -> dict[str, tuple[int, int, int, int]]:
    """按所需常量解析 ARGB 为 RGBA；缺文件、缺常量立即报错。"""
    declared = dict(PALETTE_ENTRY.findall(path.read_text(encoding="utf-8")))
    palette = {}
    for name in required:
        value = declared.get(name)
        if value is None:
            raise ValueError(f"调色板 {path} 中未找到形如 0xAARRGGBB 的常量 {name}")
        palette[name] = tuple(int(value[i:i + 2], 16) for i in (2, 4, 6, 0))
    return palette


def require_pillow():
    """为生成入口加载图像依赖，给出可直接执行的安装命令。"""
    try:
        from PIL import Image, ImageDraw
    except ModuleNotFoundError as failure:
        if failure.name != "PIL":
            raise
        raise SystemExit("缺少 Pillow，请运行：python -m pip install -r tools/image_generator/requirements.txt") from None
    return Image, ImageDraw


def save(image, output_dir: Path, name: str) -> Path:
    """保存无随机元数据的 PNG，输出目录由调用者显式选择。"""
    output_dir.mkdir(parents=True, exist_ok=True)
    target = output_dir / name
    image.save(target, format="PNG", optimize=False)
    return target


def fill(d, x0, y0, x1, y1, color):
    """填充包含右下边界的整数像素矩形。"""
    d.rectangle((x0, y0, x1, y1), fill=color)


def rectangle(d, x0, y0, x1, y1, color, fill=False):
    """绘制包含右下边界的矩形轮廓，可同时填充。"""
    d.rectangle((x0, y0, x1, y1), outline=color, fill=color if fill else None)


def polygon(d, points, color):
    """按调用方的坐标填充多边形。"""
    d.polygon(points, fill=color)


def run(main):
    """生成入口统一使用 UTF-8，输入和文件错误返回 2。"""
    configure_console()
    try:
        main()
    except (OSError, ValueError, RuntimeError) as failure:
        print(f"[生成错误] {failure}", file=sys.stderr)
        raise SystemExit(2) from None
