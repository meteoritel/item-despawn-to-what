package com.meteorite.itemdespawntowhat.client.ui.condition;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * 展示无需额外参数的露天条件。
 */
public final class OutdoorParamInput extends AbstractWidget implements ConditionParameterInput {
    public OutdoorParamInput() {
        super(0, 0, 240, 20, Component.translatable("gui.itemdespawntowhat.condition.no_params"));
        active = false;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, getMessage(),
                getX() + getWidth() / 2, getY() + 6, 0x888888);
    }

    @Override
    public JsonObject value() {
        return new JsonObject();
    }

    @Override
    public void setValue(JsonObject value) {
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void setEditorWidth(int width) {
        setWidth(width);
    }

    @Override
    public AbstractWidget widget() {
        return this;
    }

    @Override
    public void clear() {
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narration) {
    }
}
