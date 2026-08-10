package com.meteorite.itemdespawntowhat.client.ui.condition;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.client.ui.widget.AbstractCompositeWidget;
import com.meteorite.itemdespawntowhat.config.condition.type.BuiltinConditionParameters;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 通过 CycleButton 编辑天气条件参数。
 */
public final class WeatherParamInput extends AbstractCompositeWidget implements ConditionParameterInput {
    private final CycleButton<BuiltinConditionParameters.WeatherMode> button;

    public WeatherParamInput() {
        super(0, 0, 240, 20, Component.empty());
        button = CycleButton.<BuiltinConditionParameters.WeatherMode>builder(
                        mode -> Component.translatable(mode.getDescriptionId()))
                .withValues(BuiltinConditionParameters.WeatherMode.CLEAR,
                        BuiltinConditionParameters.WeatherMode.RAINING,
                        BuiltinConditionParameters.WeatherMode.THUNDERING)
                .withInitialValue(BuiltinConditionParameters.WeatherMode.CLEAR)
                .create(0, 0, 240, 20, Component.translatable("gui.itemdespawntowhat.condition.weather"));
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        button.setX(getX());
        button.setY(getY());
        button.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected Iterable<EditBox> getEditBoxes() {
        return List.of();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int buttonCode) {
        return button.mouseClicked(mouseX, mouseY, buttonCode);
    }

    @Override
    public JsonObject value() {
        JsonObject result = new JsonObject();
        result.addProperty("weather", button.getValue().name());
        return result;
    }

    @Override
    public void setValue(JsonObject value) {
        BuiltinConditionParameters.WeatherMode mode = BuiltinConditionParameters.WeatherMode.CLEAR;
        if (value != null && value.has("weather")) {
            try {
                mode = BuiltinConditionParameters.WeatherMode.valueOf(value.get("weather").getAsString());
            } catch (IllegalArgumentException ignored) {
                mode = BuiltinConditionParameters.WeatherMode.CLEAR;
            }
        }
        if (mode == BuiltinConditionParameters.WeatherMode.ANY) mode = BuiltinConditionParameters.WeatherMode.CLEAR;
        button.setValue(mode);
    }

    @Override
    public boolean isValid() {
        return button.getValue() != BuiltinConditionParameters.WeatherMode.ANY;
    }

    @Override
    public void setEditorWidth(int width) {
        setWidth(width);
        button.setWidth(width);
    }

    @Override
    public void setEditable(boolean editable) {
        active = editable;
        button.active = editable;
    }

    @Override
    public AbstractWidget widget() {
        return this;
    }

    @Override
    public void clear() {
        button.setValue(BuiltinConditionParameters.WeatherMode.CLEAR);
    }
}
