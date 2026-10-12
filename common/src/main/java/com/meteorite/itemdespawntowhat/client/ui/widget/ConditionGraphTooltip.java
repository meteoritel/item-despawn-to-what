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
 * 画布节点悬停提示面板：把带行色的文本逐行折行后自绘底色与描边。
 * 层级高于画布内容与侧栏，避免被卡片或滑入面板遮挡；折行宽度受面板可用宽度与最大宽度双重限制。
 */
final class ConditionGraphTooltip {
    private static final int TOOLTIP_MIN_WIDTH = 80;
    private static final int TOOLTIP_MAX_WIDTH = 250;
    private static final int TOOLTIP_PADDING = 4;
    private static final int TOOLTIP_MARGIN = 4;
    private static final int TOOLTIP_LINE_GAP = 1;
    // 与鼠标的间距：优先画在指针右下，越界时翻到左上
    private static final int TOOLTIP_MOUSE_OFFSET = 12;
    private static final int TOOLTIP_LAYER = UiRenderLayers.FOREGROUND * 2 + UiRenderLayers.ICON;

    /** 面板中的一行：正文与行色由调用方按语义决定（标题 / 摘要 / 结构问题）。 */
    record Line(Component text, int color) {
    }

    private final UiHostTheme theme;
    private final Font font;

    ConditionGraphTooltip(UiHostTheme theme, Font font) {
        this.theme = theme;
        this.font = font;
    }

    /**
     * 在画布内绘制提示面板。
     *
     * @param graphics 绘制上下文
     * @param bounds 画布可视区域，面板越界时按此区域翻转并钳制
     * @param lines 提示内容行，空行会被跳过
     * @param mouseX 鼠标屏幕 X 坐标
     * @param mouseY 鼠标屏幕 Y 坐标
     */
    void render(GuiGraphics graphics, UiRect bounds, List<Line> lines, int mouseX, int mouseY) {
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
        int maxWidth = Math.min(TOOLTIP_MAX_WIDTH, bounds.width() - TOOLTIP_MARGIN * 2);
        int width = Math.clamp(contentWidth + TOOLTIP_PADDING * 2, minWidth, maxWidth);
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
        int available = Math.min(TOOLTIP_MAX_WIDTH, bounds.width() - TOOLTIP_MARGIN * 2);
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
