package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.config.ConfigDirection;
import com.meteorite.itemdespawntowhat.config.catalogue.SurroundingBlocks;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;

public class SurroundingBlocksWidget extends AbstractCompositeWidget {

    // =========== 布局常量 ========== //
    private static final int TOTAL_WIDTH = 240;
    private static final int BOX_PER_ROW = 2;
    private static final int BOX_HEIGHT = 18;
    private static final int LABEL_HEIGHT = 9;
    private static final int H_GAP = 4;
    private static final int V_GAP = 3;
    // 文本框宽度：由总宽度计算
    private final int BOX_WIDTH = (TOTAL_WIDTH - H_GAP) / BOX_PER_ROW;

    private final Font font;
    private final EnumMap<ConfigDirection, EditBox> boxes = new EnumMap<>(ConfigDirection.class);

    public SurroundingBlocksWidget(Font font, int x, int y) {
        super(x, y, TOTAL_WIDTH, getTotalHeight(), Component.empty());
        this.font = font;

        for (ConfigDirection dir : ConfigDirection.values()) {
            EditBox box = new EditBox(font, 0, 0, BOX_WIDTH, BOX_HEIGHT, Component.empty());
            box.setMaxLength(64);
            boxes.put(dir, box);
        }
    }

    public static int getTotalWidth() {
        return TOTAL_WIDTH;
    }

    public static int getTotalHeight() {
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

    @Override
    protected Iterable<EditBox> getEditBoxes() {
        return boxes.values();
    }

    // ========== 值绑定 ========== //
    public SurroundingBlocks getValue() {
        SurroundingBlocks sbs = new SurroundingBlocks();
        boxes.forEach((dir, box) -> sbs.set(dir, box.getValue()));
        return sbs;
    }

    public void setValue(@NotNull SurroundingBlocks sbs) {
        boxes.forEach((dir, box) -> {
            String value = sbs.get(dir);
            box.setValue(value != null ? value : "");
        });
    }

    public void clear() {
        boxes.values().forEach(box -> box.setValue(""));
    }

    public EnumMap<ConfigDirection, EditBox> getBoxes() {
        return boxes;
    }

}
