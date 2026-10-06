package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiListView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/*** 按可用宽度换行的只读详情，供损坏规则与长文案完整阅读。 */
public final class ReadOnlyTextView implements UiWidget, UiFocusTarget {
    private final Font font;
    private final Component text;
    private final UiListView<FormattedCharSequence> lines;
    private int wrapWidth = -1;

    public ReadOnlyTextView(Font font, Component text) {
        this.font = font;
        this.text = text;
        lines = new UiListView<>(font, (graphics, rowFont, line, index, row, selected, hovered, focused) ->
                graphics.drawString(rowFont, line, row.x() + 2, row.y() + 2, UiPalette.TEXT_PRIMARY, false));
        lines.setRowHeight(font.lineHeight + 2);
    }

    @Override
    public UiRect bounds() {
        return lines.bounds();
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        int available = Math.max(1, width - 12);
        if (wrapWidth != available) {
            wrapWidth = available;
            lines.setItems(font.split(text, available));
        }
        lines.setBounds(x, y, width, height);
    }

    @Override
    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        lines.render(graphics, renderFont, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return lines.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return lines.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return lines.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return lines.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return lines.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean canFocus() {
        return lines.canFocus();
    }

    @Override
    public void setFocused(boolean focused) {
        lines.setFocused(focused);
    }

    @Override
    public boolean isFocused() {
        return lines.isFocused();
    }

    @Override
    public boolean activate() {
        return false;
    }

    @Override
    public Component accessibleName() {
        return text;
    }
}
