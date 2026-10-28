package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFlatText;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRenderLayers;
import com.meteorite.itemdespawntowhat.client.ui.shared.TagPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiListView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 标签展开容器：上方展示当前成员，默认自动轮播，下方滚动列出全部成员。
 * <p>点击条目立即选中并停止轮播；选中被清除（点击空白处）或列表失焦后才恢复自动轮播。
 */
public final class TagCarouselView implements UiWidget, UiFocusTarget {

    // 宿主默认主题：屏幕按主题取色与绘制原语
    private final UiHostTheme THEME;

    private static final String UI = TypeLabels.UI_PREFIX + "tag.";
    // 预览区高度：轮播图标与名称
    private static final int HEADER_HEIGHT = 76;
    // 成员列表起始偏移
    private static final int LIST_TOP = 78;
    // 轮播图标边长
    private static final int PREVIEW_SIZE = 56;
    // 行内名称起始偏移（图标 18 + 间距）
    private static final int ROW_LABEL_X = 23;
    private final TagPreviewIcons.Tag tag;
    private final UiListView<TagPreviewIcons.Member> list;
    private UiRect bounds = new UiRect(0, 0, 0, 0);

    /** 以宿主主题装配标签成员预览和列表，两者共享同一字体与视觉。 */
    public TagCarouselView(UiHostTheme theme, Font font, TagPreviewIcons.Tag tag) {
        this.THEME = UiHostThemes.require(theme);
        this.tag = tag;
        list = new UiListView<>(THEME, font, (g, f, member, index, row, selected, hovered, focused) -> {
            member.icon().render(g, row.x() + 2, row.y() + 2);
            UiRenderLayers.draw(g, UiRenderLayers.FOREGROUND, () -> g.drawString(f,
                    TextScroll.trimToWidth(f, member.name().getString(), row.width() - ROW_LABEL_X - 2),
                    row.x() + ROW_LABEL_X, row.y() + 6, THEME.palette().textPrimary(), false));
        });
        list.setItems(tag.members());
        list.setRowHeight(22);
        list.setEmptyMessage(Component.translatable(UI + "empty"));
        list.setOnSelectionChanged(index -> {
            if (index >= 0) tag.carousel().select(index, Util.getMillis());
            else tag.carousel().resume(Util.getMillis());
        });
        list.setOnFocusChanged(focused -> {
            if (!focused) clearSelection();
        });
    }

    @Override public UiRect bounds() { return bounds; }
    @Override public void setBounds(int x, int y, int width, int height) {
        bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        list.setBounds(x, y + LIST_TOP, width, Math.max(0, height - LIST_TOP));
    }

    @Override public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        THEME.drawInset(graphics, new UiRect(bounds.x(), bounds.y(), bounds.width(), HEADER_HEIGHT));
        tag.carousel().render(graphics,
                new UiRect(bounds.x() + (bounds.width() - PREVIEW_SIZE) / 2, bounds.y() + 2, PREVIEW_SIZE, PREVIEW_SIZE),
                Util.getMillis());
        int index = tag.carousel().index(Util.getMillis());
        if (index >= 0) {
            Component name = tag.members().get(index).name();
            UiRenderLayers.draw(graphics, UiRenderLayers.FOREGROUND, () -> UiFlatText.drawCentered(graphics, font,
                    TextScroll.trimToWidth(font, name.getString(), bounds.width() - 8),
                    bounds.x() + bounds.width() / 2, bounds.y() + 60, THEME.palette().textPrimary()));
        }
        list.render(graphics, font, mouseX, mouseY);
    }

    /**
     * 恢复自动轮播：弹窗关闭导致视图失去交互时调用，避免再次打开时预览停在固定成员。
     */
    public void resumePolling() {
        if (list.selectedIndex() >= 0) {
            list.setSelectedIndex(-1);
        }
        tag.carousel().resume(Util.getMillis());
    }

    // 清除选中：已有选中行时走选中变化回调恢复轮播，否则直接恢复轮播。
    private void clearSelection() {
        if (list.selectedIndex() >= 0) {
            list.setSelectedIndex(-1);
        } else {
            tag.carousel().resume(Util.getMillis());
        }
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && bounds.contains(x, y) && !list.bounds().contains(x, y)) {
            // 点击预览区视为取消选中并恢复轮播
            clearSelection();
        }
        return list.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return list.mouseDragged(x, y, button, dx, dy); }
    @Override public boolean mouseReleased(double x, double y, int button) { return list.mouseReleased(x, y, button); }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) { return list.mouseScrolled(x, y, sx, sy); }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return list.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean canFocus() { return list.canFocus(); }
    @Override public void setFocused(boolean focused) { list.setFocused(focused); }
    @Override public boolean isFocused() { return list.isFocused(); }
    @Override public boolean activate() { return list.activate(); }
    @Override public Component accessibleName() { return tag.label(); }
}
