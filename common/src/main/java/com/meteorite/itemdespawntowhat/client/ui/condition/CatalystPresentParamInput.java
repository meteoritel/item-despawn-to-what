package com.meteorite.itemdespawntowhat.client.ui.condition;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.client.ui.widget.AbstractCompositeWidget;
import com.meteorite.itemdespawntowhat.util.IdValidator;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 编辑催化剂在场条件的物品列表与数量。
 */
public final class CatalystPresentParamInput extends AbstractCompositeWidget implements ConditionParameterInput {
    private static final int GAP = 5;
    private final EditBox itemBox;
    private final EditBox countBox;

    public CatalystPresentParamInput(Font font) {
        super(0, 0, 240, 20, Component.empty());
        itemBox = new EditBox(font, 0, 0, 160, 20, Component.empty());
        itemBox.setMaxLength(1024);
        itemBox.setHint(Component.translatable("gui.itemdespawntowhat.condition.catalyst_items"));
        countBox = new EditBox(font, 0, 0, 75, 20, Component.empty());
        countBox.setMaxLength(256);
        countBox.setHint(Component.translatable("gui.itemdespawntowhat.condition.counts"));
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int countWidth = Math.min(75, Math.max(45, getWidth() / 3));
        int itemWidth = getWidth() - countWidth - GAP;
        itemBox.setX(getX());
        itemBox.setY(getY());
        itemBox.setWidth(itemWidth);
        countBox.setX(getX() + itemWidth + GAP);
        countBox.setY(getY());
        countBox.setWidth(countWidth);
        itemBox.render(graphics, mouseX, mouseY, partialTick);
        countBox.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected Iterable<EditBox> getEditBoxes() {
        return List.of(itemBox, countBox);
    }

    @Override
    public JsonObject value() {
        String[] items = itemBox.getValue().split(",");
        String[] counts = countBox.getValue().split(",");
        JsonArray entries = new JsonArray();
        for (int i = 0; i < items.length; i++) {
            String item = items[i].trim();
            if (item.isEmpty()) continue;
            JsonObject entry = new JsonObject();
            entry.addProperty("item", item);
            entry.addProperty("count", i < counts.length
                    ? Math.max(1, SafeParseUtil.parseInt(counts[i].trim(), 1)) : 1);
            entries.add(entry);
        }
        JsonObject result = new JsonObject();
        result.add("items", entries);
        return result;
    }

    @Override
    public void setValue(JsonObject value) {
        if (value == null || !value.has("items") || !value.get("items").isJsonArray()) {
            clear();
            return;
        }
        StringBuilder items = new StringBuilder();
        StringBuilder counts = new StringBuilder();
        value.getAsJsonArray("items").forEach(element -> {
            if (!element.isJsonObject()) return;
            JsonObject entry = element.getAsJsonObject();
            if (!items.isEmpty()) {
                items.append(',');
                counts.append(',');
            }
            items.append(entry.has("item") ? entry.get("item").getAsString() : "");
            counts.append(entry.has("count") ? entry.get("count").getAsInt() : 1);
        });
        itemBox.setValue(items.toString());
        countBox.setValue(counts.toString());
    }

    @Override
    public boolean isValid() {
        if (!IdValidator.isValidCommaSeparatedItemId(itemBox.getValue())) return false;
        if (countBox.getValue().isBlank()) return true;
        for (String count : countBox.getValue().split(",")) {
            if (SafeParseUtil.parseInt(count.trim(), 0) < 1) return false;
        }
        return true;
    }

    @Override
    public void setEditorWidth(int width) {
        setWidth(width);
    }

    @Override
    public void setEditable(boolean editable) {
        active = editable;
        itemBox.setEditable(editable);
        countBox.setEditable(editable);
    }

    @Override
    public AbstractWidget widget() {
        return this;
    }

    @Override
    public void clear() {
        itemBox.setValue("");
        countBox.setValue("");
    }
}
