package com.meteorite.itemdespawntowhat.client.ui.widget;

import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphRenderer.drawBorder;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRenderLayers;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * 条件节点与动作参数共用的悬停说明面板：按语义行色折行，自绘底色与描边。
 * 层级高于内容与侧栏，面板定位受宿主传入的可视区域约束。
 */
public final class UiDescriptionTooltip {
    private static final int TOOLTIP_MIN_WIDTH = 80;
    private static final int TOOLTIP_MAX_WIDTH = 250;
    private static final int TOOLTIP_PADDING = 4;
    private static final int TOOLTIP_MARGIN = 4;
    private static final int TOOLTIP_LINE_GAP = 1;
    // 与鼠标的间距：优先画在指针右下，越界时翻到左上
    private static final int TOOLTIP_MOUSE_OFFSET = 12;
    private static final int TOOLTIP_LAYER = UiRenderLayers.FOREGROUND * 2 + UiRenderLayers.ICON;

    /**
     * 面板中的语义行。
     *
     * @param text 标题、参数说明或问题文本
     * @param color 对应语义的文字颜色
     */
    public record Line(Component text, int color) {
    }

    private final UiHostTheme theme;
    private final Font font;
    private int maxWidth = TOOLTIP_MAX_WIDTH;

    /**
     * 创建使用宿主浮层配色的说明面板。
     *
     * @param theme 宿主主题
     * @param font 折行与绘制字体
     */
    public UiDescriptionTooltip(UiHostTheme theme, Font font) {
        this.theme = theme;
        this.font = font;
    }

    /**
     * 调整面板的最大宽度，供参数较多的说明使用。
     *
     * @param width 期望的最大宽度，低于最小宽度时按最小宽度处理
     * @return 当前面板，便于链式调用
     */
    public UiDescriptionTooltip setMaxWidth(int width) {
        this.maxWidth = Math.max(TOOLTIP_MIN_WIDTH, width);
        return this;
    }

    /**
     * 在宿主可视区域内绘制提示面板。
     *
     * @param graphics 绘制上下文
     * @param bounds 宿主可视区域，面板越界时按此区域翻转并钳制
     * @param lines 提示内容行，空行会被跳过
     * @param mouseX 鼠标屏幕 X 坐标
     * @param mouseY 鼠标屏幕 Y 坐标
     */
    public void render(GuiGraphics graphics, UiRect bounds, List<Line> lines, int mouseX, int mouseY) {
        if (lines.isEmpty() || bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        List<Row> rows = wrap(lines, textWidthLimit(bounds));
        if (rows.isEmpty()) {
            return;
        }
        int contentWidth = 0;
        for (Row row : rows) {
            contentWidth = Math.max(contentWidth, row.width());
        }
        int minWidth = Math.min(TOOLTIP_MIN_WIDTH, bounds.width() - TOOLTIP_MARGIN * 2);
        int widthLimit = Math.min(maxWidth, bounds.width() - TOOLTIP_MARGIN * 2);
        int width = Math.clamp(contentWidth + TOOLTIP_PADDING * 2, minWidth, widthLimit);
        int height = TOOLTIP_PADDING * 2 + rows.size() * font.lineHeight + (rows.size() - 1) * TOOLTIP_LINE_GAP;
        int x = resolveX(bounds, width, mouseX);
        int y = resolveY(bounds, height, mouseY);
        UiRenderLayers.draw(graphics, TOOLTIP_LAYER, () -> {
            graphics.fill(x, y, x + width, y + height, theme.palette().canvasPickerFill());
            drawBorder(graphics, new UiRect(x, y, width, height), theme.palette().canvasPickerBorder());
            int textY = y + TOOLTIP_PADDING;
            for (Row row : rows) {
                graphics.drawString(font, row.text(), x + TOOLTIP_PADDING, textY, row.color(), false);
                textY += font.lineHeight + TOOLTIP_LINE_GAP;
            }
        });
    }

    // 折行宽度上限：由面板可用宽度决定换行位置，避免折行后再次超出屏幕
    private int textWidthLimit(UiRect bounds) {
        int available = Math.min(maxWidth, bounds.width() - TOOLTIP_MARGIN * 2);
        return Math.max(1, available - TOOLTIP_PADDING * 2);
    }

    private List<Row> wrap(List<Line> lines, int maxTextWidth) {
        List<Row> rows = new ArrayList<>();
        for (Line line : lines) {
            if (line.text().getString().isEmpty()) {
                continue;
            }
            for (FormattedCharSequence sequence : font.split(line.text(), maxTextWidth)) {
                rows.add(new Row(sequence, font.width(sequence), line.color()));
            }
        }
        return rows;
    }

    private static int resolveX(UiRect bounds, int width, int mouseX) {
        int left = mouseX + TOOLTIP_MOUSE_OFFSET;
        if (left + width > bounds.right() - TOOLTIP_MARGIN) {
            left = mouseX - TOOLTIP_MOUSE_OFFSET - width;
        }
        return Math.clamp(left, bounds.x() + TOOLTIP_MARGIN,
                Math.max(bounds.x() + TOOLTIP_MARGIN, bounds.right() - TOOLTIP_MARGIN - width));
    }

    private static int resolveY(UiRect bounds, int height, int mouseY) {
        int top = mouseY + TOOLTIP_MOUSE_OFFSET;
        if (top + height > bounds.bottom() - TOOLTIP_MARGIN) {
            top = mouseY - TOOLTIP_MOUSE_OFFSET - height;
        }
        return Math.clamp(top, bounds.y() + TOOLTIP_MARGIN,
                Math.max(bounds.y() + TOOLTIP_MARGIN, bounds.bottom() - TOOLTIP_MARGIN - height));
    }

    /** 折行后的单行：已绑定行色的绘制序列与像素宽度。 */
    private record Row(FormattedCharSequence text, int width, int color) {
    }
}
