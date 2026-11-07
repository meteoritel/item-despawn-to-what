package com.meteorite.itemdespawntowhat.client.ui.kit.api.text;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * 一段标签的测量与悬停呈现。实例归控件或稳定行持有，包装与核心可共享；
 * 字体及语言资源变化使测量失效，文本带、颜色与悬停门禁由调用方提供。
 */
public final class UiLabelPresentation {
    private Component text = Component.empty();
    private FormattedCharSequence visualText = FormattedCharSequence.EMPTY;
    private @Nullable Font measuredFont;
    private @Nullable Language measuredLanguage;
    private int width;
    private int hoverTicks;

    /**
     * 当前语义文本，供名称、提示与布局快照共用。
     */
    public Component text() {
        return text;
    }

    /**
     * 内容改变后重新测量并重置滚动；相同内容保持当前呈现。
     */
    public void setText(Component next) {
        if (text.equals(next)) return;
        text = next.copy();
        measuredFont = null;
        hoverTicks = 0;
    }

    /**
     * 读取当前字体与语言下的测量宽度，稳定帧复用缓存。
     */
    public int width(Font font) {
        refreshMetrics(font);
        return width;
    }

    /**
     * 在给定文本带内左对齐绘制；hovered 已包含作用域与几何门禁，离开即重置滚动。
     * 文本超宽时复用 TextScroll 的滚动与裁剪。
     */
    public void render(GuiGraphics graphics, Font font, UiRect band, int color, boolean hovered) {
        refreshMetrics(font);
        hoverTicks = hovered ? hoverTicks + 1 : 0;
        TextScroll.draw(graphics, font, visualText, width, band.x(), band.y(), band.width(),
                color, hovered, hoverTicks);
    }

    private void refreshMetrics(Font font) {
        Language language = Language.getInstance();
        if (measuredFont == font && measuredLanguage == language) return;
        measuredFont = font;
        measuredLanguage = language;
        hoverTicks = 0;
        visualText = text.getVisualOrderText();
        width = font.width(visualText);
    }
}
