package com.meteorite.itemdespawntowhat.ui.widget;

import com.meteorite.itemdespawntowhat.config.CatalystItems;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CatalystItemsWidget extends AbstractCompositeWidget {
    // ===== 布局常量 ===== //
    private static final int TOTAL_WIDTH = 240;
    private static final int BOX_HEIGHT = 18;
    private static final int LABEL_HEIGHT = 9;
    private static final int TIP_HEIGHT = 9;
    private static final int H_GAP = 6;
    private static final int V_GAP = 3;

    private static final int BOX_WIDTH = (TOTAL_WIDTH - H_GAP) / 2;

    private final Font font;
    private final EditBox itemBox;
    private final EditBox countBox;

    public CatalystItemsWidget(Font font, int x, int y) {
        super(x, y, TOTAL_WIDTH, getTotalHeight(), Component.empty());
        this.font = font;

        this.itemBox = new EditBox(font, 0, 0, BOX_WIDTH, BOX_HEIGHT, Component.empty());
        this.itemBox.setMaxLength(256);

        this.countBox = new EditBox(font, 0, 0, BOX_WIDTH, BOX_HEIGHT, Component.empty());
        this.countBox.setMaxLength(64);
    }

    public static int getTotalHeight() {
        return LABEL_HEIGHT + V_GAP + BOX_HEIGHT + V_GAP + TIP_HEIGHT;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();

        // —— 第一行：标签
        gfx.drawString(font, "物品注册名", x, y, 0xAAAAAA, false);
        gfx.drawString(font, "数量", x + BOX_WIDTH + H_GAP, y, 0xAAAAAA, false);

        // —— 第二行：文本框
        int boxY = y + LABEL_HEIGHT + V_GAP;
        itemBox.setX(x);
        itemBox.setY(boxY);
        itemBox.render(gfx, mouseX, mouseY, partialTick);

        countBox.setX(x + BOX_WIDTH + H_GAP);
        countBox.setY(boxY);
        countBox.render(gfx, mouseX, mouseY, partialTick);

        // —— 第三行：提示
        gfx.drawString(
                font,
                "（多个物品使用英文逗号隔开）",
                x,
                boxY + BOX_HEIGHT + V_GAP,
                0x777777,
                false
        );
    }

    @Override
    protected Iterable<EditBox> getEditBoxes() {
        return List.of(itemBox, countBox);
    }

    // ========== 值绑定 ========== //
    public CatalystItems getValue() {
        CatalystItems result = new CatalystItems();
        List<CatalystItems.CatalystEntry> list = new ArrayList<>();

        // 提取逗号分隔，并删除所有空格
        String[] rawItems = itemBox.getValue()
                .replace(" ", "")
                .split(",");

        String[] rawCounts = countBox.getValue()
                .replace(" ", "")
                .split(",");

        // 以物品注册名的长度为准
        for (int i = 0; i < rawItems.length; i++) {
            String itemStr = rawItems[i];
            // 校验物品注册名是否合法
            if (itemStr.isEmpty()) {
                continue;
            }

            ResourceLocation id = ResourceLocation.tryParse(itemStr);
            if (id == null || id.getPath().isEmpty()) {
                continue;
            }

            // 数量处理（缺失 / 非法 → 1）
            int count = 1;
            if (i < rawCounts.length) {
                try {
                    int parsed = Integer.parseInt(rawCounts[i]);
                    if (parsed >= 1) {
                        count = parsed;
                    }
                } catch (NumberFormatException ignored) {
                    // 非数字，保持 count = 1
                }
            }

            list.add(new CatalystItems.CatalystEntry(id, count));
        }
        result.setCatalystList(list);
        return result.hasAnyCatalyst() ? result : new CatalystItems();
    }

    public void setValue(@Nullable CatalystItems items) {
        if (items == null || !items.hasAnyCatalyst()) {
            clear();
            return;
        }

        StringBuilder itemSb = new StringBuilder();
        StringBuilder countSb = new StringBuilder();

        for (var entry : items.getCatalystList()) {
            if (entry == null || !entry.isValid()) continue;

            if (!itemSb.isEmpty()) {
                itemSb.append(",");
                countSb.append(",");
            }

            itemSb.append(entry.getItemId());
            countSb.append(Math.max(1, entry.getCount()));
        }

        itemBox.setValue(itemSb.toString());
        countBox.setValue(countSb.toString());
    }

    public void clear() {
        itemBox.setValue("");
        countBox.setValue("");
    }

    public EditBox getItemBox() {
        return itemBox;
    }

    // ========== 接口实现 ========== //

}
