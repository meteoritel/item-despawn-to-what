package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFlowLayout;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** 分区拥有的控件、表单和卡片；组合视图只消费当前分区的装配结果。 */
final class EditorSectionContent {
    private static final int HEADER_BAR_WIDTH = 2;
    private static final int HEADER_TITLE_GAP = 4;
    private static final int HEADER_DIVIDER_GAP = 5;
    private final Font font;
    final List<UiWidget> widgets = new ArrayList<>();
    final List<FormView> forms = new ArrayList<>();
    final List<UiButton> buttons = new ArrayList<>();
    final List<UiButton> candidateButtons = new ArrayList<>();
    final List<UiButton> effectButtons = new ArrayList<>();
    final List<PageCard> cards = new ArrayList<>();

    EditorSectionContent(Font font) { this.font = font; }

    void setEnabled(boolean enabled) {
        for (FormView form : forms) form.setEnabled(enabled);
        for (UiButton button : buttons) button.setEnabled(enabled);
        for (UiButton button : candidateButtons) button.setEnabled(enabled);
        for (UiButton button : effectButtons) button.setEnabled(enabled);
    }

    void clear() {
        for (FormView form : forms) form.unmount();
        widgets.clear();
        forms.clear();
        buttons.clear();
        candidateButtons.clear();
        effectButtons.clear();
        cards.clear();
    }

    /** 卡片头部内容：标题必填，图标可选（图标绘制在标题行内、蓝条右侧）。 */
    record CardHeader(Component title, @Nullable UiIcon icon) {
        CardHeader {
            Objects.requireNonNull(title, "title");
        }
    }

    /**
     * 页面分区的绘制数据，与控件共用滚动坐标。
     * <p>头部内容既可以是构造时的常量标题，也可以在每帧渲染时求值（见 {@link #dynamic}），
     * 卡片之间的头部几何统一由 {@link #headerRect()} 派生，绘制统一走
     * {@link EditorSectionContent#drawCardHeader}。</p>
     */
    static final class PageCard {

        // 卡片矩形
        private final UiRect bounds;
        // 头部内容来源；为空表示该卡片不绘制头部
        private final @Nullable Supplier<CardHeader> header;
        // 头部内容距卡片边缘的内缩，决定蓝条、图标、标题与分隔线的位置
        private final int headerInset;

        /** 静态标题卡片：头部内缩沿用页面默认值，不绘制图标。 */
        PageCard(UiRect bounds, @Nullable Component title) {
            this(bounds, constantHeader(title), RuleEditorPageSupport.CARD_PAD);
        }

        private PageCard(UiRect bounds, @Nullable Supplier<CardHeader> header, int headerInset) {
            this.bounds = bounds;
            this.header = header;
            this.headerInset = headerInset;
        }

        /** 静态标题卡片：由调用方指定头部内缩，用于同页内需要对齐的卡片。 */
        static PageCard titled(UiRect bounds, Component title, int headerInset) {
            return new PageCard(bounds, constantHeader(title), headerInset);
        }

        /** 动态头部卡片：标题与图标在每帧渲染时求值，可随编辑状态变化。 */
        static PageCard dynamic(UiRect bounds, Supplier<CardHeader> header, int headerInset) {
            return new PageCard(bounds, header, headerInset);
        }

        // 常量标题包装为头部内容来源；标题为空即表示该卡片没有头部
        private static @Nullable Supplier<CardHeader> constantHeader(@Nullable Component title) {
            return title == null ? null : () -> new CardHeader(title, null);
        }

        /** 卡片矩形：卡片高与滚动坐标都相对它计算。 */
        UiRect bounds() {
            return bounds;
        }

        /** 当前头部内容；返回 null 表示该卡片不绘制头部。 */
        @Nullable CardHeader header() {
            return header == null ? null : header.get();
        }

        /** 头部矩形：由卡片矩形按内缩与固定头高派生，蓝条、标题与分隔线都相对它定位。 */
        UiRect headerRect() {
            return cardHeaderRect(bounds.x(), bounds.y(), bounds.width(), headerInset);
        }
    }

    static int layoutForm(@Nullable FormView form, int x, int y, int width) {
        if (form == null) {
            return y;
        }
        // 一次测量同时得到行几何与内容高度，替代「preferredHeight 再 layout」的两次测量
        int height = form.layoutNatural(x, y, width);
        return y + height + RuleEditorPageSupport.PAD;
    }

    int finishCard(int x, int top, int width, int contentBottom, String titleKey) {
        int bottom = contentBottom + RuleEditorPageSupport.CARD_PAD;
        cards.add(new PageCard(new UiRect(x, top, width, Math.max(RuleEditorPageSupport.CARD_HEADER_H + RuleEditorPageSupport.CARD_PAD * 2, bottom - top)),
                Component.translatable(RuleEditorPageSupport.UI + titleKey)));
        return bottom + RuleEditorPageSupport.CARD_GAP;
    }

    static int layoutButtonGroup(List<UiButton> group, int x, int y, int width) {
        if (group.isEmpty()) {
            return 0;
        }
        int limit = Math.max(0, width);
        int[] widths = new int[group.size()];
        for (int index = 0; index < group.size(); index++) {
            widths[index] = Math.clamp(group.get(index).preferredWidth(RuleEditorPageSupport.PAD), Math.min(24, limit), limit);
        }
        UiFlowLayout.Wrap flow = UiFlowLayout.wrap(widths, limit, RuleEditorPageSupport.BUTTON_GAP, RuleEditorPageSupport.FORM_BUTTON_H);
        for (int index = 0; index < group.size(); index++) {
            UiRect offset = flow.bounds(index);
            group.get(index).setBounds(x + offset.x(), y + offset.y(), offset.width(), offset.height());
        }
        return flow.height();
    }

    static UiRect cardHeaderRect(int x, int y, int width, int inset) {
        return new UiRect(x + inset, y + inset, Math.max(0, width - inset * 2), RuleEditorPageSupport.CARD_HEADER_H);
    }

    static void drawCardHeader(UiHostTheme THEME, GuiGraphics graphics, Font font, UiRect headerRect, @Nullable UiIcon icon, Component title) {
        if (headerRect.width() <= 0) {
            return;
        }
        int dividerY = headerRect.y() + headerRect.height() - HEADER_DIVIDER_GAP;
        int centerY = headerRect.y() + (dividerY - headerRect.y()) / 2;
        int textY = centerY - font.lineHeight / 2;
        graphics.fill(headerRect.x(), textY, headerRect.x() + HEADER_BAR_WIDTH, textY + font.lineHeight,
                THEME.palette().accent());
        int titleX = headerRect.x() + HEADER_BAR_WIDTH + HEADER_TITLE_GAP;
        if (icon != null) {
            icon.render(graphics, titleX, centerY - icon.height() / 2);
            titleX += icon.width() + RuleEditorPageSupport.PAD;
        }
        var titleLines = font.split(title, Math.max(1, headerRect.right() - titleX));
        if (!titleLines.isEmpty()) {
            graphics.drawString(font, titleLines.getFirst(), titleX, textY, THEME.palette().textPrimary(), false);
        }
        THEME.drawDivider(graphics, headerRect.x(), dividerY, headerRect.width());
    }

    void drawText(GuiGraphics graphics, @Nullable UiRect rect, Component text, int color) {
        if (rect == null) {
            return;
        }
        String trimmed = TextScroll.trimToWidth(font, text.getString(), Math.max(1, rect.width()));
        graphics.drawString(font, trimmed, rect.x(), rect.y(), color, false);
    }
}
