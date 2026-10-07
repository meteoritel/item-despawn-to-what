package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRenderLayers;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiListView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/*** 标签展开容器：上方轮播真实成员，下方滚动列出全部成员，点击或方向键切换。 */
public final class TagCarouselView implements UiWidget {
    private static final String UI = "gui.itemdespawntowhat.edit.tag.";
    private final TagPreviewIcons.Tag tag;
    private final UiListView<TagPreviewIcons.Member> list;
    private final UiButton previous;
    private final UiButton next;
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
        previous = new UiButton(font, Component.translatable(UI + "previous"), UiButtonVariant.SECONDARY,
                () -> tag.carousel().step(-1, Util.getMillis()));
        next = new UiButton(font, Component.translatable(UI + "next"), UiButtonVariant.SECONDARY,
                () -> tag.carousel().step(1, Util.getMillis()));
        previous.setEnabled(!tag.members().isEmpty());
        next.setEnabled(!tag.members().isEmpty());
    }

    @Override public UiRect bounds() { return bounds; }
    @Override public void setBounds(int x, int y, int width, int height) {
        bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        previous.setBounds(x + 2, y + 24, 36, 16);
        next.setBounds(bounds.right() - 38, y + 24, 36, 16);
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
        previous.render(graphics, font, mouseX, mouseY);
        next.render(graphics, font, mouseX, mouseY);
        list.render(graphics, font, mouseX, mouseY);
        if (!list.isDraggingScrollbar() && bounds.contains(mouseX, mouseY))
            graphics.renderTooltip(font, font.split(tag.tooltip(), Math.max(1, bounds.width())), mouseX, mouseY);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        return previous.mouseClicked(x, y, button) || next.mouseClicked(x, y, button) || list.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return list.mouseDragged(x, y, button, dx, dy); }
    @Override public boolean mouseReleased(double x, double y, int button) { return list.mouseReleased(x, y, button); }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) { return list.mouseScrolled(x, y, sx, sy); }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
            tag.carousel().step(keyCode == GLFW.GLFW_KEY_LEFT ? -1 : 1, Util.getMillis());
            return true;
        }
        return list.keyPressed(keyCode, scanCode, modifiers);
    }
}
