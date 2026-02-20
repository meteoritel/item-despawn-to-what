package com.meteorite.itemdespawntowhat.ui;

import com.meteorite.itemdespawntowhat.config.ConfigDirection;
import com.meteorite.itemdespawntowhat.config.SurroundingBlocks;
import com.meteorite.itemdespawntowhat.ui.Screen.BaseConfigEditScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;

public class SurroundingBlocksWidget extends AbstractWidget {

    // =========== 布局常量 ========== //
    private static final int TOTAL_WIDTH = 240;
    private static final int BOX_PER_ROW = 2;
    private static final int BOX_HEIGHT = 18;
    private static final int LABEL_HEIGHT = 9;
    private static final int H_GAP = 4;
    private static final int V_GAP = 3;
    // 文本框宽度：由总宽度计算
    private static final int BOX_WIDTH = (TOTAL_WIDTH - H_GAP) / BOX_PER_ROW;

    private final Font font;
    private final EnumMap<ConfigDirection, EditBox> boxes = new EnumMap<>(ConfigDirection.class);
    @Nullable
    private EditBox internalFocused;

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
        return TOTAL_WIDTH;
    }

    public static int totalHeight() {
        return 3 * (LABEL_HEIGHT + V_GAP + BOX_HEIGHT)
                + (3 - 1) * V_GAP;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int startX = getX();
        int startY = getY();

        int index = 0;
        // 绘制方向的字母和输入框
        for (var entry : boxes.entrySet()) {
            int row = index / BOX_PER_ROW;
            int col = index % BOX_PER_ROW;

            int x = startX + col * (BOX_WIDTH + H_GAP);
            int y = startY + row * (LABEL_HEIGHT + V_GAP + BOX_HEIGHT + V_GAP);

            // 先渲染方向标签
            String label = entry.getKey().name().substring(0, 1);
            gfx.drawString(
                    font,
                    label,
                    x + BOX_WIDTH / 2 - font.width(label) / 2,
                    y,
                    0xAAAAAA,
                    false
            );

            // 再渲染文本框
            EditBox box = entry.getValue();
            box.setX(x);
            box.setY(y + LABEL_HEIGHT + V_GAP);
            box.render(gfx, mouseX, mouseY, partialTick);
            index++;
        }
    }

    // ========== 输入方法 ========== //

    private void setInternalFocused(@Nullable EditBox box) {
        if (internalFocused == box) return;

        if (internalFocused != null) {
            internalFocused.setFocused(false);
        }
        internalFocused = box;
        if (internalFocused != null) {
            internalFocused.setFocused(true);
        }
    }

    @Nullable
    public EditBox getInternalFocused() {
        return internalFocused;
    }

    public void clearInternalFocus() {
        setInternalFocused(null);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        EditBox clicked = null;
        for (EditBox box : boxes.values()) {
            if (box.mouseClicked(mouseX, mouseY, button)) {
                clicked = box;
                break;
            }
        }

        if (clicked == null) return false;

        setInternalFocused(clicked);
        // 将本 widget 整体上报给 Screen 焦点管理器
        if (Minecraft.getInstance().screen instanceof BaseConfigEditScreen<?> screen) {
            screen.setFocusedWidget(this);
        }

        return true;
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narration) { }

    public SurroundingBlocks getValue() {
        SurroundingBlocks sb = new SurroundingBlocks();
        boxes.forEach((dir, box) -> sb.set(dir, box.getValue()));
        return sb;
    }

    public void clear() {
        boxes.values().forEach(box -> box.setValue(""));
    }

    public EnumMap<ConfigDirection, EditBox> getBoxes() {
        return boxes;
    }
}
