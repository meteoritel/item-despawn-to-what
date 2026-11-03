package com.meteorite.itemdespawntowhat.client.ui.kit.api.render;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * 全 GUI 共用的无阴影文本绘制：原版 {@code drawCenteredString}、{@code drawWordWrap} 与悬停提示都会给字形叠加暗色阴影，
 * 深色文本叠在浅色底板上时阴影与笔画糊在一起，因此这些场景统一改由本类逐行 {@code drawString(..., false)} 绘制。
 *
 * <p>悬停提示保留原版的深色底板与渐变描边观感，只去掉文字阴影；顶部优先从指针上方展开，越界时翻转并钳制在屏幕内。</p>
 */
public final class UiFlatText {
    // 提示底板内边距（四周描边外的留白）
    private static final int TOOLTIP_PADDING = 3;
    private static final int TOOLTIP_BACKGROUND = 0xF0100010;
    private static final int TOOLTIP_BORDER_TOP = 0x505000FF;
    private static final int TOOLTIP_BORDER_BOTTOM = 0x5028007F;
    private static final int TOOLTIP_TEXT = 0xFFFFFFFF;
    // 首行之后额外让出的间距，与原版提示的行距一致
    private static final int TOOLTIP_FIRST_LINE_GAP = 2;
    private static final int TOOLTIP_MOUSE_OFFSET = 12;
    private static final int TOOLTIP_SCREEN_MARGIN = 4;

    private UiFlatText() { }

    /**
     * 居中绘制单行文本，替代强制带阴影的 {@link GuiGraphics#drawCenteredString}。
     *
     * @param graphics 绘制上下文
     * @param font 绘制字体
     * @param text 待绘制文本
     * @param centerX 文本中心的屏幕 X 坐标
     * @param y 文本顶部的屏幕 Y 坐标
     * @param color 文本颜色
     */
    public static void drawCentered(GuiGraphics graphics, Font font, Component text, int centerX, int y, int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    /**
     * 居中绘制单行文本，替代强制带阴影的 {@link GuiGraphics#drawCenteredString}。
     *
     * @param graphics 绘制上下文
     * @param font 绘制字体
     * @param text 待绘制文本
     * @param centerX 文本中心的屏幕 X 坐标
     * @param y 文本顶部的屏幕 Y 坐标
     * @param color 文本颜色
     */
    public static void drawCentered(GuiGraphics graphics, Font font, String text, int centerX, int y, int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    /**
     * 按宽度折行后逐行绘制，替代强制带阴影的 {@link GuiGraphics#drawWordWrap}。
     *
     * @param graphics 绘制上下文
     * @param font 绘制字体
     * @param text 待绘制文本
     * @param x 文本区左侧屏幕 X 坐标
     * @param y 文本区顶部屏幕 Y 坐标
     * @param width 折行宽度
     * @param color 文本颜色
     */
    public static void drawWrapped(GuiGraphics graphics, Font font, Component text, int x, int y, int width, int color) {
        int lineY = y;
        for (FormattedCharSequence line : font.split(text, Math.max(1, width))) {
            graphics.drawString(font, line, x, lineY, color, false);
            lineY += font.lineHeight;
        }
    }

    /**
     * 绘制已折行的悬停提示。
     *
     * @param graphics 绘制上下文
     * @param font 绘制字体
     * @param lines 已折行的提示文本行
     * @param mouseX 指针屏幕 X 坐标
     * @param mouseY 指针屏幕 Y 坐标
     */
    public static void tooltip(GuiGraphics graphics, Font font, List<FormattedCharSequence> lines, int mouseX, int mouseY) {
        tooltip(graphics, font, new UiRect(0, 0, graphics.guiWidth(), graphics.guiHeight()), lines, mouseX, mouseY);
    }

    private static void tooltip(GuiGraphics graphics, Font font, UiRect host,
                                List<FormattedCharSequence> lines, int mouseX, int mouseY) {
        if (lines.isEmpty() || UiTooltipPanel.contentWidth(host, Integer.MAX_VALUE,
                TOOLTIP_PADDING + 1, TOOLTIP_SCREEN_MARGIN) == 0) return;
        int widest = 0;
        for (FormattedCharSequence line : lines) {
            widest = Math.max(widest, font.width(line));
        }
        int height = lines.size() * font.lineHeight + (lines.size() > 1 ? TOOLTIP_FIRST_LINE_GAP : 0);
        UiTooltipPanel panel = UiTooltipPanel.layout(host,
                widest, height, TOOLTIP_PADDING + 1, mouseX, mouseY, TOOLTIP_MOUSE_OFFSET,
                TOOLTIP_SCREEN_MARGIN, UiTooltipPlacement.VerticalPreference.ABOVE);
        panel.render(graphics, rect -> background(graphics, rect), content -> {
            int lineY = content.y();
            for (int index = 0; index < lines.size(); index++) {
                graphics.drawString(font, lines.get(index), content.x(), lineY, TOOLTIP_TEXT, false);
                lineY += font.lineHeight + (index == 0 ? TOOLTIP_FIRST_LINE_GAP : 0);
            }
        });
    }

    /**
     * 按最大宽度折行后绘制悬停提示。
     *
     * @param graphics 绘制上下文
     * @param font 绘制字体
     * @param text 提示文本，可自带换行
     * @param maxWidth 折行宽度上限
     * @param mouseX 指针屏幕 X 坐标
     * @param mouseY 指针屏幕 Y 坐标
     */
    public static void tooltip(GuiGraphics graphics, Font font, Component text, int maxWidth, int mouseX, int mouseY) {
        componentTooltip(graphics, font, List.of(text), maxWidth, mouseX, mouseY);
    }

    /**
     * 绘制由多个组件组成的悬停提示：每个组件按最大宽度折行后拼成整块提示。
     *
     * @param graphics 绘制上下文
     * @param font 绘制字体
     * @param lines 提示文本行，每项可自带换行
     * @param maxWidth 折行宽度上限
     * @param mouseX 指针屏幕 X 坐标
     * @param mouseY 指针屏幕 Y 坐标
     */
    public static void componentTooltip(GuiGraphics graphics, Font font, List<Component> lines, int maxWidth, int mouseX, int mouseY) {
        componentTooltip(graphics, font, new UiRect(0, 0, graphics.guiWidth(), graphics.guiHeight()),
                lines, maxWidth, mouseX, mouseY);
    }

    /** 在有限宿主内先预算正文宽度，再折行和放置面板。 */
    public static void componentTooltip(GuiGraphics graphics, Font font, UiRect host, List<Component> lines,
                                        int maxWidth, int mouseX, int mouseY) {
        int width = UiTooltipPanel.contentWidth(host, maxWidth, TOOLTIP_PADDING + 1, TOOLTIP_SCREEN_MARGIN);
        if (width == 0) return;
        List<FormattedCharSequence> sequences = new ArrayList<>();
        for (Component line : lines) sequences.addAll(font.split(line, width));
        tooltip(graphics, font, host, sequences, mouseX, mouseY);
    }

    // 底板与描边沿用原版提示观感：深色底、四周一圈暗边、内侧自上而下由紫蓝渐变到深紫
    private static void background(GuiGraphics graphics, UiRect bounds) {
        int left = bounds.x() + 1;
        int top = bounds.y() + 1;
        int outerWidth = bounds.width() - 2;
        int outerHeight = bounds.height() - 2;
        graphics.fill(left, top - 1, left + outerWidth, top, TOOLTIP_BACKGROUND);
        graphics.fill(left, top + outerHeight, left + outerWidth, top + outerHeight + 1, TOOLTIP_BACKGROUND);
        graphics.fill(left, top, left + outerWidth, top + outerHeight, TOOLTIP_BACKGROUND);
        graphics.fill(left - 1, top, left, top + outerHeight, TOOLTIP_BACKGROUND);
        graphics.fill(left + outerWidth, top, left + outerWidth + 1, top + outerHeight, TOOLTIP_BACKGROUND);
        graphics.fillGradient(left, top + 1, left + 1, top + outerHeight - 1, TOOLTIP_BORDER_TOP, TOOLTIP_BORDER_BOTTOM);
        graphics.fillGradient(left + outerWidth - 1, top + 1, left + outerWidth, top + outerHeight - 1,
                TOOLTIP_BORDER_TOP, TOOLTIP_BORDER_BOTTOM);
        graphics.fill(left, top, left + outerWidth, top + 1, TOOLTIP_BORDER_TOP);
        graphics.fill(left, top + outerHeight - 1, left + outerWidth, top + outerHeight, TOOLTIP_BORDER_BOTTOM);
    }
}
