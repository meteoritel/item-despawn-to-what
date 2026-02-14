package com.meteorite.itemdespawntowhat.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FormList extends ContainerObjectSelectionList<FormList.Entry> {

    public static final int BASE_X = 30;
    private static final int LABEL_WIDTH = 100;
    public static final int GAP = 30;

    public FormList(Minecraft mc, int width, int height, int y, int itemHeight) {
        super(mc, width, height, y, itemHeight);
    }

    public void add(String label, AbstractWidget widget) {
        addEntry(new Entry(minecraft.font, label, widget));
    }

    // 调整滑条的位置
    @Override
    public int getRowWidth() {
        return 340;
    }

    public static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        private final Font font;
        private final String label;
        private final AbstractWidget widget;


        public Entry(Font font, String label, AbstractWidget widget) {
            this.font = font;
            this.label = label;
            this.widget = widget;
        }
        @Override
        public void render(GuiGraphics g, int index, int y, int x, int width, int height,
                           int mx, int my, boolean hovered, float pt) {
            int labelX = BASE_X;
            int widgetX = BASE_X + LABEL_WIDTH + GAP;

            int labelY  = y + (height - font.lineHeight) / 2;
            int widgetY = y + (height - widget.getHeight()) / 2;

            g.drawString(font, label, labelX, labelY, 0xE0E0E0, false);

            widget.setX(widgetX);
            widget.setY(widgetY);
            widget.render(g, mx, my, pt);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!widget.isFocused()) {
                widget.setFocused(true);
                return widget.mouseClicked(mouseX, mouseY, button);
            }
            widget.setFocused(false);
            return false;
        }


        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (widget.isFocused()) {
                return widget.keyPressed(keyCode, scanCode, modifiers);
            }
            return false;
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            if (widget.isFocused()) {
                return widget.charTyped(codePoint, modifiers);
            }
            return false;
        }

        @Override
        public @NotNull List<? extends GuiEventListener> children() {
            return List.of(widget);
        }

        @Override
        public @NotNull List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }
}
