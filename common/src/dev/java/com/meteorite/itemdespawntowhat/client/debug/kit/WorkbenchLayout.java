package com.meteorite.itemdespawntowhat.client.debug.kit;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiWidget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 调试工作台各页面共用的骨架与排版尺度：标题区、内容区、页脚与分组标题。
 *
 * <p>工作台页面由目录、本地组件展台、组件样例、预览示例组成，它们分别在不同包里实现，因此把
 * 统一的间距与绘制方式收敛到这里，避免各页面各写一套坐标。这里只依赖 kit 契约，供独立样板编译使用。</p>
 */
public final class WorkbenchLayout {
    /** 页面左右内边距。 */
    public static final int INSET = 12;
    /** 标题基线。 */
    public static final int TITLE_Y = 10;
    /** 副标题基线。 */
    public static final int SUBTITLE_Y = 22;
    /** 标题区与内容区之间的分隔线。 */
    public static final int RULE_Y = 36;
    /** 内容区顶边。 */
    public static final int CONTENT_TOP = 44;
    /** 标准控件高度。 */
    public static final int CONTROL_HEIGHT = 20;
    /** 控件之间的垂直间距。 */
    public static final int GAP = 8;
    /** 分组之间的垂直间距。 */
    public static final int GROUP_GAP = 12;
    /** 页脚距窗口底边。 */
    public static final int FOOTER_MARGIN = 12;
    /** 页脚返回按钮宽度。 */
    public static final int FOOTER_BUTTON_WIDTH = 96;

    private WorkbenchLayout() {}

    /** 去掉左右内边距后的可用内容宽度。 */
    public static int contentWidth(int width) { return Math.max(40, width - INSET * 2); }

    /** 页脚按钮的顶边。 */
    public static int footerTop(int height) { return height - FOOTER_MARGIN - CONTROL_HEIGHT; }

    /** 页脚分隔线的纵坐标。 */
    public static int footerRuleY(int height) { return footerTop(height) - GAP; }

    /** 可滚动内容区的底边。 */
    public static int contentBottom(int height) { return footerRuleY(height) - GAP; }

    /** 可滚动内容区的高度。 */
    public static int contentHeight(int height) { return Math.max(0, contentBottom(height) - CONTENT_TOP); }

    /** 页脚返回按钮的矩形。 */
    public static UiRect footerBackRect(int height) { return new UiRect(INSET, footerTop(height), FOOTER_BUTTON_WIDTH, CONTROL_HEIGHT); }

    /** 把控件摆到页脚返回按钮位置。 */
    public static void placeFooterBack(UiWidget widget, int height) {
        UiRect rect = footerBackRect(height);
        widget.setBounds(rect.x(), rect.y(), rect.width(), rect.height());
    }

    /** 绘制窗口底板、标题与标题分隔线。 */
    public static void drawPage(GuiGraphics graphics, Font font, UiStyleSource theme, int width, int height, Component title) {
        theme.drawWindow(graphics, new UiRect(0, 0, width, height));
        drawPageHeading(graphics, font, theme, width, title);
    }

    /** 已由带皮肤屏幕绘制底板时，只绘制标题和分隔线。 */
    public static void drawPageHeading(GuiGraphics graphics, Font font, UiStyleSource theme, int width, Component title) {
        graphics.drawString(font, title, INSET, TITLE_Y, theme.surfaces().text(), false);
        theme.drawDivider(graphics, INSET, RULE_Y, contentWidth(width));
    }

    /** 绘制标题下方的说明行，超宽时截断。 */
    public static void drawSubtitle(GuiGraphics graphics, Font font, UiStyleSource theme, int width, Component subtitle) {
        graphics.drawString(font, ellipsize(font, subtitle, contentWidth(width)), INSET, SUBTITLE_Y, theme.surfaces().secondaryText(), false);
    }

    /** 绘制页脚分隔线；页脚按钮由页面自己排版，保证按钮绘制在分隔线之上。 */
    public static void drawFooterRule(GuiGraphics graphics, UiStyleSource theme, int width, int height) {
        theme.drawDivider(graphics, INSET, footerRuleY(height), contentWidth(width));
    }

    /**
     * 绘制分组标题，返回其下一行的纵坐标。
     *
     * <p>分组标题用次要文字色，与控件正文区分层级；调用方按返回值继续排版。</p>
     */
    public static int drawSection(GuiGraphics graphics, Font font, UiStyleSource theme, int x, int y, Component label, int maxWidth) {
        graphics.drawString(font, ellipsize(font, label, maxWidth), x, y, theme.surfaces().secondaryText(), false);
        return y + font.lineHeight + 4;
    }

    /** 把文本截断到可用宽度，超出部分替换为省略号。 */
    public static Component ellipsize(Font font, Component text, int maxWidth) {
        String raw = text.getString();
        if (font.width(raw) <= maxWidth) return text;
        String suffix = "…";
        int budget = Math.max(0, maxWidth - font.width(suffix));
        return Component.literal(font.plainSubstrByWidth(raw, budget) + suffix);
    }

    /**
     * 按词语换行并把行数限制在 {@code maxLines}；被截断的最后一行补省略号。
     *
     * <p>用于展示运行时数值：这类文本长度随内容变化，不能直接画单行，否则会被裁掉。</p>
     */
    public static List<String> wrapLines(Font font, String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text.isEmpty() || maxLines <= 0) return lines;
        String rest = text;
        while (!rest.isEmpty() && lines.size() < maxLines) {
            if (font.width(rest) <= maxWidth) {
                lines.add(rest);
                return lines;
            }
            String fit = font.plainSubstrByWidth(rest, maxWidth);
            int space = fit.lastIndexOf(' ');
            String line = space > 0 ? fit.substring(0, space) : fit;
            if (line.isEmpty()) line = fit;
            lines.add(line);
            rest = rest.substring(line.length()).stripLeading();
        }
        if (!rest.isEmpty()) {
            int last = lines.size() - 1;
            String suffix = "…";
            int budget = Math.max(0, maxWidth - font.width(suffix));
            lines.set(last, font.plainSubstrByWidth(lines.get(last), budget) + suffix);
        }
        return lines;
    }
}
