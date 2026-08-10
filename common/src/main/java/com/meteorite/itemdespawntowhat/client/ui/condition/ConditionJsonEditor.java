package com.meteorite.itemdespawntowhat.client.ui.condition;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meteorite.itemdespawntowhat.client.ui.widget.AbstractCompositeWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 条件参数的通用 JSON 编辑器。第三方类型可直接复用该编辑器，避免客户端依赖服务端 DTO。
 */
public final class ConditionJsonEditor extends AbstractCompositeWidget implements ConditionParameterInput {
    private static final Gson GSON = new Gson();
    private final EditBox box;

    public ConditionJsonEditor(Font font) {
        super(0, 0, 240, 20, Component.empty());
        box = new EditBox(font, 0, 0, 240, 20, Component.empty());
        box.setMaxLength(2048);
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        box.setX(getX());
        box.setY(getY());
        box.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected Iterable<EditBox> getEditBoxes() {
        return List.of(box);
    }

    public JsonObject value() {
        try {
            return JsonParser.parseString(box.getValue()).getAsJsonObject();
        } catch (RuntimeException ignored) {
            return new JsonObject();
        }
    }

    public boolean isValid() {
        try {
            return JsonParser.parseString(box.getValue()).isJsonObject();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public void setValue(JsonObject value) {
        box.setValue(value == null ? "{}" : GSON.toJson(value));
    }

    public EditBox editBox() {
        return box;
    }

    public void setEditable(boolean editable) {
        active = editable;
        box.setEditable(editable);
    }

    public void setEditorWidth(int width) {
        setWidth(width);
        box.setWidth(width);
    }

    @Override
    public net.minecraft.client.gui.components.AbstractWidget widget() {
        return this;
    }

    @Override
    public void clear() {
        setValue(null);
    }
}
