package com.meteorite.itemdespawntowhat.client.ui.theme;

import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 编辑器界面的小标记贴图：一张纯白字形图集，绘制时按调用方给的主题色染色。
 *
 * <p>图集只存字形不存颜色，同一张图因此在深色底与浅色底上都能用；颜色仍由调色板决定，
 * 主题换色时不需要重新生成贴图。切片规格与 {@code tools/image_generator/generate-editor-marks.py}
 * 一致：单元格 16×16、单列，每个标记在单元格左上角按 {@link Mark} 声明的宽高落笔。</p>
 *
 * <p>kit 只提供 {@link UiIcon} 与贴图注入点，不保存任何资源路径；贴图与染色都留在主题层，
 * 由控件显式取用。标记不放禁用态：禁用色由调用方把调色板颜色传进来即可。</p>
 */
public final class UiMarkIcons {

    // 标记图集：与 icons_dark.png 同目录，纯白字形
    private static final ResourceLocation SHEET = ResourceLocation.fromNamespaceAndPath(
            TypeLabels.OWN_NAMESPACE, "textures/gui/editor/marks.png");
    // 单元格边长与行数：行数参与 UV 除数，必须与生成脚本一致
    private static final int CELL = 16;
    private static final int ROWS = 6;

    // 工具类，禁止实例化
    private UiMarkIcons() {
    }

    /** 标记种类：行序与尺寸逐条对应生成脚本 {@code MARKS} 表。 */
    public enum Mark {
        // 目录行：条目不可渲染
        UNAVAILABLE(0, 6, 9),
        // 目录行：已选中
        CHECK(1, 8, 8),
        // 取消 / 删除
        CLOSE(2, 9, 9),
        // 翻页：上一页 / 下一页
        PREVIOUS(3, 8, 10),
        NEXT(4, 8, 10),
        // 对象角标：被配置为消耗
        CONSUMED_STAR(5, 7, 7);

        private final int row;
        private final int width;
        private final int height;

        Mark(int row, int width, int height) {
            this.row = row;
            this.width = width;
            this.height = height;
        }

        /** 图块宽度，也是布局尺寸。 */
        public int width() {
            return width;
        }

        /** 图块高度，也是布局尺寸。 */
        public int height() {
            return height;
        }
    }

    /**
     * 取一个染色后的标记图标。
     *
     * @param mark  标记种类，决定图集行与图标尺寸
     * @param color ARGB 颜色，通常取自当前主题调色板
     * @return 固定尺寸的图标，调用方只负责摆位
     */
    public static UiIcon icon(Mark mark, int color) {
        return new UiIcon.Rendered(mark.width(), mark.height(),
                (graphics, box) -> draw(graphics, mark, box.x(), box.y(), color));
    }

    // 纯白字形乘上着色器颜色即主题色；批次前后都提交，避免染色泄漏到相邻控件。
    // getShaderColor 返回渲染系统内部数组本身，setColor 会就地改写它，
    // 因此必须先取成局部值，否则 finally 读回的是本次染色后的颜色，后续绘制会一直被乘暗。
    private static void draw(GuiGraphics graphics, Mark mark, int x, int y, int color) {
        graphics.flush();
        float[] current = RenderSystem.getShaderColor();
        float previousRed = current[0];
        float previousGreen = current[1];
        float previousBlue = current[2];
        float previousAlpha = current[3];
        try {
            graphics.setColor(red(color), green(color), blue(color), alpha(color));
            graphics.blit(SHEET, x, y, 0, mark.row * CELL, mark.width(), mark.height(), CELL, ROWS * CELL);
        } finally {
            try {
                graphics.flush();
            } finally {
                graphics.setColor(previousRed, previousGreen, previousBlue, previousAlpha);
            }
        }
    }

    private static float red(int color) {
        return ((color >> 16) & 0xFF) / 255.0F;
    }

    private static float green(int color) {
        return ((color >> 8) & 0xFF) / 255.0F;
    }

    private static float blue(int color) {
        return (color & 0xFF) / 255.0F;
    }

    private static float alpha(int color) {
        return ((color >>> 24) & 0xFF) / 255.0F;
    }
}
