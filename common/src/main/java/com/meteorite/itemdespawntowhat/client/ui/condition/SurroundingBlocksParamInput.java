package com.meteorite.itemdespawntowhat.client.ui.condition;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.client.ui.widget.AbstractCompositeWidget;
import com.meteorite.itemdespawntowhat.config.ConfigDirection;
import com.meteorite.itemdespawntowhat.util.IdValidator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;

/**
 * 使用六个紧凑文本框编辑各方向的相邻方块参数。
 */
public final class SurroundingBlocksParamInput extends AbstractCompositeWidget implements ConditionParameterInput {
    private static final int GAP = 2;
    private final EnumMap<ConfigDirection, EditBox> boxes = new EnumMap<>(ConfigDirection.class);

    public SurroundingBlocksParamInput(Font font) {
        super(0, 0, 240, 20, Component.empty());
        for (ConfigDirection direction : ConfigDirection.values()) {
            EditBox box = new EditBox(font, 0, 0, 38, 20, Component.empty());
            box.setMaxLength(128);
            box.setHint(Component.literal(direction.name().substring(0, 1)));
            boxes.put(direction, box);
        }
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int boxWidth = boxWidth();
        int index = 0;
        for (EditBox box : boxes.values()) {
            box.setX(getX() + index * (boxWidth + GAP));
            box.setY(getY());
            box.setWidth(boxWidth);
            box.render(graphics, mouseX, mouseY, partialTick);
            index++;
        }
    }

    @Override
    protected Iterable<EditBox> getEditBoxes() {
        return boxes.values();
    }

    @Override
    public JsonObject value() {
        JsonObject blocks = new JsonObject();
        boxes.forEach((direction, box) -> {
            String text = box.getValue().trim();
            if (!text.isEmpty()) blocks.addProperty(direction.name().toLowerCase(), text);
        });
        JsonObject result = new JsonObject();
        result.add("blocks", blocks);
        return result;
    }

    @Override
    public void setValue(JsonObject value) {
        JsonObject blocks = value != null && value.has("blocks") && value.get("blocks").isJsonObject()
                ? value.getAsJsonObject("blocks") : new JsonObject();
        boxes.forEach((direction, box) -> {
            String key = direction.name().toLowerCase();
            box.setValue(blocks.has(key) ? blocks.get(key).getAsString() : "");
        });
    }

    @Override
    public boolean isValid() {
        boolean hasValue = boxes.values().stream().anyMatch(box -> !box.getValue().isBlank());
        return hasValue && boxes.values().stream()
                .filter(box -> !box.getValue().isBlank())
                .allMatch(box -> IdValidator.isValidBlockId(box.getValue().trim()));
    }

    @Override
    public void setEditorWidth(int width) {
        setWidth(width);
    }

    @Override
    public void setEditable(boolean editable) {
        active = editable;
        boxes.values().forEach(box -> box.setEditable(editable));
    }

    @Override
    public AbstractWidget widget() {
        return this;
    }

    @Override
    public void clear() {
        boxes.values().forEach(box -> box.setValue(""));
    }

    private int boxWidth() {
        return Math.max(18, (getWidth() - GAP * (boxes.size() - 1)) / boxes.size());
    }
}
