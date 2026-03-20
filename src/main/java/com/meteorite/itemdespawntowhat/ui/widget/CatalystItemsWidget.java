package com.meteorite.itemdespawntowhat.ui.widget;

import com.meteorite.itemdespawntowhat.config.catalogue.CatalystItems;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CatalystItemsWidget extends AbstractCompositeWidget {
    // ===== 本地化键名 ===== //
    private static final String LABEL_PREFIX = "gui.itemdespawntowhat.edit.";
    private static final String KEY_ITEM_LABEL = LABEL_PREFIX + "catalyst.item_label";
    private static final String KEY_COUNT_LABEL = LABEL_PREFIX + "catalyst.count_label";
    private static final String KEY_TIP = LABEL_PREFIX + "catalyst.tip";
    private static final String KEY_CONSUME = LABEL_PREFIX + "catalyst.consume_button";
    private static final String KEY_CONSUME_ON = LABEL_PREFIX + "on";
    private static final String KEY_CONSUME_OFF = LABEL_PREFIX + "off";

    // ===== 布局常量 ===== //
    private static final int TOTAL_WIDTH = 240;
    private static final int BOX_HEIGHT = 18;
    private static final int BTN_HEIGHT = 18;
    private static final int LABEL_HEIGHT = 9;
    private static final int TIP_HEIGHT = 9;
    private static final int H_GAP = 6;
    private static final int V_GAP = 3;

    private static final int COUNT_BOX_WIDTH = 80;
    private static final int ITEM_BOX_WIDTH = TOTAL_WIDTH - COUNT_BOX_WIDTH - H_GAP;

    private final Font font;
    private final LinkedBoxGroup linkedBoxGroup;
    private final CycleButton<Boolean> consumeButton;

    public CatalystItemsWidget(Font font, int x, int y) {
        super(x, y, TOTAL_WIDTH, getTotalHeight(), Component.empty());
        this.font = font;

        // 主框：物品 ID；从框：数量，新增条目默认 "1"
        EditBox countBox = new EditBox(font, 0, 0, COUNT_BOX_WIDTH, BOX_HEIGHT, Component.empty());
        countBox.setMaxLength(64);
        countBox.setFilter(s -> s.matches("[\\d,]*")); // 仅允许数字和逗号

        this.linkedBoxGroup = LinkedBoxGroup
                .builder(font, ITEM_BOX_WIDTH, 256)
                .follow(countBox, () -> "1")
                .build();

        this.consumeButton = CycleButton.<Boolean>builder(
                        value -> Component.translatable(value ? KEY_CONSUME_ON : KEY_CONSUME_OFF))
                .withValues(true, false)
                .withInitialValue(true)
                .create(0, 0, TOTAL_WIDTH, BTN_HEIGHT,
                        Component.translatable(KEY_CONSUME));
    }

    public static int getTotalHeight() {
        return BTN_HEIGHT + V_GAP +
                LABEL_HEIGHT + V_GAP +
                BOX_HEIGHT + V_GAP +
                TIP_HEIGHT;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();

        // 第一行：按钮
        consumeButton.setX(x);
        consumeButton.setY(y);
        consumeButton.render(gfx, mouseX, mouseY, partialTick);

        // 第二行：标签
        int labelY = y + BTN_HEIGHT + V_GAP;
        gfx.drawString(font, Component.translatable(KEY_ITEM_LABEL), x, labelY, 0xAAAAAA, false);
        gfx.drawString(font, Component.translatable(KEY_COUNT_LABEL), x + ITEM_BOX_WIDTH + H_GAP, labelY, 0xAAAAAA, false);

        // 第三行：文本框
        int boxY = labelY + LABEL_HEIGHT + V_GAP;
        EditBox itemBox  = linkedBoxGroup.getPrimaryBox();
        EditBox countBox = linkedBoxGroup.getFollower(0);

        itemBox.setX(x);
        itemBox.setY(boxY);
        itemBox.render(gfx, mouseX, mouseY, partialTick);

        countBox.setX(x + ITEM_BOX_WIDTH + H_GAP);
        countBox.setY(boxY);
        countBox.render(gfx, mouseX, mouseY, partialTick);

        // 第四行：提示
        gfx.drawString(font, Component.translatable(KEY_TIP), x, boxY + BOX_HEIGHT + V_GAP, 0xAAAAAA, false);
    }

    // ========== 事件路由 ========== //
    @Override
    protected Iterable<EditBox> getEditBoxes() {
        return linkedBoxGroup.allBoxes();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (consumeButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ========== 值绑定 ========== //
    public CatalystItems getValue() {
        List<String> items  = LinkedBoxGroup.splitTokens(linkedBoxGroup.getPrimaryBox().getValue());
        List<String> counts = LinkedBoxGroup.splitValues(linkedBoxGroup.getFollower(0).getValue());

        List<CatalystItems.CatalystEntry> list = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(items.get(i));
            if (id == null || id.getPath().isEmpty()) continue;

            int count = 1;
            if (i < counts.size()) {
                try {
                    int parsed = Integer.parseInt(counts.get(i));
                    if (parsed >= 1) count = parsed;
                } catch (NumberFormatException ignored) {}
            }
            list.add(new CatalystItems.CatalystEntry(id, count));
        }

        CatalystItems result = new CatalystItems();
        result.setCatalystList(list);
        result.setCatalystConsume(consumeButton.getValue());
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
            itemSb.append(entry.itemId());
            countSb.append(Math.max(1, entry.count()));
        }

        linkedBoxGroup.getPrimaryBox().setValue(itemSb.toString());
        linkedBoxGroup.getFollower(0).setValue(countSb.toString());
        // setValue 后手动对齐，确保初始状态一致
        linkedBoxGroup.syncAllFollowers();
        consumeButton.setValue(items.isCatalystConsume());
    }

    public void clear() {
        linkedBoxGroup.clear();
        consumeButton.setValue(false);
    }

    // 返回主框（物品 ID 框），供外部注册 SuggestionWidget 使用
    public EditBox getItemBox() {
        return linkedBoxGroup.getPrimaryBox();
    }

}
