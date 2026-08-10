package com.meteorite.itemdespawntowhat.client.ui.condition;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.client.ui.widget.AbstractCompositeWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Predicate;

/**
 * 编辑单个字符串参数的条件控件。
 */
public final class TextConditionParamInput extends AbstractCompositeWidget implements ConditionParameterInput {
    private final String parameterName;
    private final Predicate<String> validator;
    private final EditBox box;

    public TextConditionParamInput(Font font, String parameterName, Component hint, Predicate<String> validator) {
        super(0, 0, 240, 20, Component.empty());
        this.parameterName = parameterName;
        this.validator = validator;
        box = new EditBox(font, 0, 0, 240, 20, Component.empty());
        box.setMaxLength(256);
        box.setHint(hint);
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

    @Override
    public JsonObject value() {
        JsonObject result = new JsonObject();
        result.addProperty(parameterName, box.getValue().trim());
        return result;
    }

    @Override
    public void setValue(JsonObject value) {
        box.setValue(value != null && value.has(parameterName)
                ? value.get(parameterName).getAsString() : "");
    }

    @Override
    public boolean isValid() {
        return validator.test(box.getValue().trim());
    }

    @Override
    public void setEditorWidth(int width) {
        setWidth(width);
        box.setWidth(width);
    }

    @Override
    public void setEditable(boolean editable) {
        active = editable;
        box.setEditable(editable);
    }

    @Override
    public AbstractWidget widget() {
        return this;
    }

    @Override
    public void clear() {
        box.setValue("");
    }
}
