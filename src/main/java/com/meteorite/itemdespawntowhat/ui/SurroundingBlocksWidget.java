package com.meteorite.itemdespawntowhat.ui;

import com.meteorite.itemdespawntowhat.config.ConfigDirection;
import com.meteorite.itemdespawntowhat.config.SurroundingBlocks;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;

public class SurroundingBlocksWidget extends AbstractWidget {

    // 单个输入框
    private static final int BOX_WIDTH = 34;
    private static final int BOX_HEIGHT = 18;

    // 文本
    private static final int LABEL_HEIGHT = 9;

    // 水平间距
    private static final int H_GAP = 4;
    // 垂直间距
    private static final int V_GAP = 3;

    private final Font font;
    private final EnumMap<ConfigDirection, EditBox> boxes = new EnumMap<>(ConfigDirection.class);
    private EditBox focused;

    public SurroundingBlocksWidget(Font font, int x, int y) {
        super(x, y, totalWidth(), totalHeight(), Component.empty());
        this.font = font;

        for (ConfigDirection dir : ConfigDirection.values()) {
            EditBox box = new EditBox(font, 0, 0, BOX_WIDTH, BOX_HEIGHT, Component.empty());
            box.setMaxLength(64);
            boxes.put(dir, box);
        }
    }

    public static int totalWidth() {
        int count = ConfigDirection.values().length;
        return count * BOX_WIDTH + (count - 1) * H_GAP;
    }

    public static int totalHeight() {
        return LABEL_HEIGHT + V_GAP + BOX_HEIGHT;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int startX = getX();
        int labelY = getY();
        int boxY = labelY + LABEL_HEIGHT + V_GAP;

        int i = 0;
        // 绘制方向的字母和输入框
        for (var entry : boxes.entrySet()) {
            EditBox box = entry.getValue();
            int x = startX + i * (BOX_WIDTH + H_GAP);

            // 先渲染方向标签
            String label = entry.getKey().name().substring(0, 1);
            gfx.drawString(font, label,
                    x + BOX_WIDTH / 2 - font.width(label) / 2,
                    labelY,
                    0xAAAAAA,
                    false
            );

            box.setX(x);
            box.setY(boxY);
            box.render(gfx, mouseX, mouseY, partialTick);
            i++;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        for (EditBox box : boxes.values()) {
            if (box.mouseClicked(mouseX, mouseY, button)) {
                setFocusedBox(box);
                return true;
            }
        }
        return false;
    }

    private void setFocusedBox(EditBox box) {
        if (focused != null) focused.setFocused(false);
        focused = box;
        focused.setFocused(true);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return focused != null && focused.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return focused != null && focused.charTyped(codePoint, modifiers);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narration) { }

    public SurroundingBlocks getValue() {
        SurroundingBlocks sb = new SurroundingBlocks();
        boxes.forEach((dir, box) -> sb.set(dir, box.getValue()));
        return sb;
    }

    public void clearFocus() {
        if (focused != null) {
            focused.setFocused(false);
            focused = null;
        }
    }

    public void clear() {
        boxes.values().forEach(box -> box.setValue(""));
        if (focused != null) {
            focused.setFocused(false);
            focused = null;
        }
    }
}
