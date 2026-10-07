package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRenderLayers;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiListView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/*** 标签展开容器：上方自动轮播真实成员，下方滚动列出全部成员，点击条目切换预览。 */
public final class TagCarouselView implements UiWidget {
    private static final String UI = "gui.itemdespawntowhat.edit.tag.";
    private final TagPreviewIcons.Tag tag;
    private final UiListView<TagPreviewIcons.Member> list;
    private UiRect bounds = new UiRect(0, 0, 0, 0);

    public TagCarouselView(Font font, TagPreviewIcons.Tag tag) {
        this.tag = tag;
        list = new UiListView<>(font, (g, f, member, index, row, selected, hovered, focused) -> {
            member.icon().render(g, row.x() + 2, row.y() + 2);
            UiRenderLayers.draw(g, UiRenderLayers.FOREGROUND, () -> g.drawString(f,
                    com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll.trimToWidth(f, member.name().getString(), row.width() - 25),
                    row.x() + 23, row.y() + 6, UiPalette.TEXT_PRIMARY, false));
        });
        list.setItems(tag.members());
        list.setRowHeight(22);
        list.setEmptyMessage(Component.translatable(UI + "empty"));
        list.setOnSelectionChanged(index -> tag.carousel().select(index, Util.getMillis()));
    }

    @Override public UiRect bounds() { return bounds; }
    @Override public void setBounds(int x, int y, int width, int height) {
        bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        list.setBounds(x, y + 78, width, Math.max(0, height - 78));
    }

    @Override public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        UiTheme.drawInset(graphics, new UiRect(bounds.x(), bounds.y(), bounds.width(), 76));
        tag.carousel().render(graphics, new UiRect(bounds.x() + (bounds.width() - 56) / 2, bounds.y() + 2, 56, 56), Util.getMillis());
        int index = tag.carousel().index(Util.getMillis());
        if (index >= 0) {
            Component name = tag.members().get(index).name();
            UiRenderLayers.draw(graphics, UiRenderLayers.FOREGROUND, () -> graphics.drawCenteredString(font,
                    com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll.trimToWidth(font, name.getString(), bounds.width() - 8),
                    bounds.x() + bounds.width() / 2, bounds.y() + 60, UiPalette.TEXT_PRIMARY));
        }
        list.render(graphics, font, mouseX, mouseY);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        return list.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return list.mouseDragged(x, y, button, dx, dy); }
    @Override public boolean mouseReleased(double x, double y, int button) { return list.mouseReleased(x, y, button); }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) { return list.mouseScrolled(x, y, sx, sy); }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return list.keyPressed(keyCode, scanCode, modifiers);
    }
}
