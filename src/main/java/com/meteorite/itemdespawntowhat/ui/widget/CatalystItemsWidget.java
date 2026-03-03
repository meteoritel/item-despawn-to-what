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
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

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
    private final FocusAwareEditBox itemBox;
    private final EditBox countBox;

    private List<String> itemSnapshotOnFocus = List.of();

    public CatalystItemsWidget(Font font, int x, int y) {
        super(x, y, TOTAL_WIDTH, getTotalHeight(), Component.empty());
        this.font = font;

        this.itemBox = new FocusAwareEditBox(font, 0, 0, BOX_WIDTH, BOX_HEIGHT, Component.empty());
        this.itemBox.setMaxLength(256);
        this.itemBox.setFocusListener(focused -> {
            if (focused) {
                // 获得焦点：保存当前物品列表快照，供失焦时 diff 使用
                itemSnapshotOnFocus = parseItemList(itemBox.getValue());
            } else {
                // 失去焦点：同步数量框
                adjustCountBox(itemSnapshotOnFocus);
            }
        });

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

    // 同步数量文本框数量
    private void adjustCountBox(List<String> oldItems) {
        List<String> newItems = parseItemList(itemBox.getValue());
        List<String> oldCounts = parseCountList(countBox.getValue());

        // 物品为空，直接清空数量框
        if (newItems.isEmpty()) {
            countBox.setValue("");
            return;
        }

        // 补齐旧数量列表长度，不足部分默认为 "1"
        List<String> counts = new ArrayList<>(oldCounts);
        while (counts.size() < oldItems.size()) {
            counts.add("1");
        }

        // 用游标在旧列表中从左向右顺序查找，避免重复消费同一位置
        int searchFrom = 0;
        List<String> newCounts = new ArrayList<>(newItems.size());

        for (String newItem : newItems) {
            // 在 oldItems[searchFrom..] 中寻找第一个与 newItem 相同的项
            int foundIdx = -1;
            for (int i = searchFrom; i < oldItems.size(); i++) {
                if (oldItems.get(i).equals(newItem)) {
                    foundIdx = i;
                    break;
                }
            }

            if (foundIdx >= 0) {
                // 找到：迁移对应数量，游标推进到该位置的下一位
                newCounts.add(counts.get(foundIdx));
                searchFrom = foundIdx + 1;
            } else {
                // 未找到（新增项）：补默认值 "1"
                newCounts.add("1");
                // searchFrom 不变，继续从同一位置向后匹配后续新项
            }
        }
        countBox.setValue(String.join(",", newCounts));
    }

    public void clear() {
        itemBox.setValue("");
        countBox.setValue("");
    }

    public EditBox getItemBox() {
        return itemBox;
    }

    // 将原始文本解析为物品注册名列表（去除空格、过滤空串）
    private static List<String> parseItemList(String raw) {
        return Arrays.stream(raw.replace(" ", "").split(",", -1))
                .filter(s -> !s.isEmpty())
                .toList();
    }

    // 将数量文本框解析为可变列表（去除空格，保留空串占位）
    private static List<String> parseCountList(String raw) {
        String[] parts = raw.replace(" ", "").split(",", -1);
        List<String> list = new ArrayList<>(Arrays.asList(parts));
        while (!list.isEmpty() && list.getLast().isEmpty()) {
            list.removeLast();
        }
        return list;
    }

    // ========== 内部可监听的文本框类 ========== //
    public static class FocusAwareEditBox extends EditBox {
        @Nullable
        private Consumer<Boolean> focusListener;
        public FocusAwareEditBox(Font font, int x, int y, int width, int height, Component message) {
            super(font, x, y, width, height, message);
        }

        public void setFocusListener(@Nullable Consumer<Boolean> listener) {
            this.focusListener = listener;
        }

        @Override
        public void setFocused(boolean focused) {
            boolean old = isFocused();
            super.setFocused(focused);
            if (old != focused && focusListener != null) {
                focusListener.accept(focused);
            }
        }
    }
}
