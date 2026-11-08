# 编辑器图集生成

生成器使用 Pillow；机械校验与 GUI 的 PNG/bitmap_font 实现使用标准库。

## 环境

在仓库根目录运行以下命令安装已固定的版本：

    python -m pip install -r tools/image_generator/requirements.txt

依赖为 Pillow 12.2.0，生成脚本兼容项目 Python 3.12 运行环境。

## 入口

以下命令将图集写入显式指定的临时目录；省略 --output-dir 则写入编辑器纹理资源目录。默认根目录从工具位置定位，与工作目录无关。

    python tools/image_generator/generate-editor-surfaces.py --output-dir .local/atlas
    python tools/image_generator/generate-editor-chrome.py --output-dir .local/atlas
    python tools/image_generator/generate-editor-icons.py --scheme dark --output-dir .local/atlas
    python tools/image_generator/generate-editor-icons.py --scheme light --output-dir .local/atlas
    python tools/image_generator/generate-editor-marks.py --output-dir .local/atlas

surfaces 按固定几何生成白色掩码，使用 --output-dir 指定输出目录；chrome 与 icons 使用 --palette 读取调色板。调色板缺文件或缺少所需 ARGB 常量时输出定位诊断并返回 2。

## 颜色与切片

- atlas 统一将 UiPalette.java 的 ARGB 解析成 RGBA；surfaces 使用纯白 RGB，alpha 由圆角与抗锯齿决定。
- chrome 的滚动条烘焙完整 RGBA，立体边使用纯白掩码。图集为横向 8×8 单元，格序与 UiTheme.chrome 一致。
- icons 的 dark normal/disabled 分别映射 TEXT_PRIMARY/TEXT_DISABLED。dark accent 与 light 的 normal/disabled/accent 是具名图标专属颜色，不按相同色值关联其他主题用途。16×16 单元按 ICON_ROWS 排列，列为 normal/disabled，与 UiFlatSkin 的图集切片和图标行常量一致。
- marks 只生成纯白掩码；坐标、宽高和格序由 MARKS 管理，与 UiMarkIcons.Mark 一致。
- surfaces 每张 32×32，角区为 8，与 UiTheme.surface 一致。

生成后应在同一 Pillow 环境中比较解码后的 RGBA、尺寸和透明像素，再判断编码字节差异；临时输出验证不会更改正式资源。
