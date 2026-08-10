package com.meteorite.itemdespawntowhat.client.ui.condition;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.client.ui.widget.AbstractCompositeWidget;
import com.meteorite.itemdespawntowhat.util.IdValidator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 编辑流体在场条件的流体 ID 与源方块要求。
 */
public final class FluidPresentParamInput extends AbstractCompositeWidget implements ConditionParameterInput {
    private static final int GAP = 5;
    private final EditBox fluidBox;
    private final CycleButton<Boolean> sourceButton;

    public FluidPresentParamInput(Font font) {
        super(0, 0, 240, 20, Component.empty());
        fluidBox = new EditBox(font, 0, 0, 155, 20, Component.empty());
        fluidBox.setMaxLength(256);
        fluidBox.setHint(Component.translatable("gui.itemdespawntowhat.condition.fluid"));
        sourceButton = CycleButton.booleanBuilder(Component.translatable("gui.itemdespawntowhat.edit.on"),
                        Component.translatable("gui.itemdespawntowhat.edit.off"))
                .withInitialValue(true)
                .create(0, 0, 80, 20,
                        Component.translatable("gui.itemdespawntowhat.edit.inner_fluid.source_label"));
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int buttonWidth = Math.min(85, Math.max(60, getWidth() / 3));
        int boxWidth = getWidth() - buttonWidth - GAP;
        fluidBox.setX(getX());
        fluidBox.setY(getY());
        fluidBox.setWidth(boxWidth);
        sourceButton.setX(getX() + boxWidth + GAP);
        sourceButton.setY(getY());
        sourceButton.setWidth(buttonWidth);
        fluidBox.render(graphics, mouseX, mouseY, partialTick);
        sourceButton.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected Iterable<EditBox> getEditBoxes() {
        return List.of(fluidBox);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (sourceButton.mouseClicked(mouseX, mouseY, button)) {
            clearInternalFocus();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public JsonObject value() {
        JsonObject result = new JsonObject();
        result.addProperty("fluid", fluidBox.getValue().trim());
        result.addProperty("require_source", sourceButton.getValue());
        return result;
    }

    @Override
    public void setValue(JsonObject value) {
        fluidBox.setValue(value != null && value.has("fluid") ? value.get("fluid").getAsString() : "");
        sourceButton.setValue(value == null || !value.has("require_source")
                || value.get("require_source").getAsBoolean());
    }

    @Override
    public boolean isValid() {
        return IdValidator.isValidFluidId(fluidBox.getValue().trim());
    }

    @Override
    public void setEditorWidth(int width) {
        setWidth(width);
    }

    @Override
    public void setEditable(boolean editable) {
        active = editable;
        fluidBox.setEditable(editable);
        sourceButton.active = editable;
    }

    @Override
    public AbstractWidget widget() {
        return this;
    }

    @Override
    public void clear() {
        fluidBox.setValue("");
        sourceButton.setValue(true);
    }
}
