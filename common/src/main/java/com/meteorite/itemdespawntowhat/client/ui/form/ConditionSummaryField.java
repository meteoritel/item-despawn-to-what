package com.meteorite.itemdespawntowhat.client.ui.form;

import com.meteorite.itemdespawntowhat.client.ui.presentation.ConditionExpressionPresenter;
import com.meteorite.itemdespawntowhat.client.ui.screen.ConditionEditorScreen;
import com.meteorite.itemdespawntowhat.client.ui.screen.BaseConfigEditScreen;
import com.meteorite.itemdespawntowhat.config.condition.ConditionExpression;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * 主表单中的条件摘要字段，点击按钮进入完整 DNF 编辑器。
 */
public final class ConditionSummaryField extends AbstractWidget implements FormFieldInput<ConditionExpression> {
    private final Button editButton;
    private ConditionExpression value = new ConditionExpression();
    private Consumer<ConditionExpression> valueConsumer = ignored -> { };

    public ConditionSummaryField() {
        super(0, 0, 240, 20, Component.empty());
        editButton = Button.builder(Component.translatable("gui.itemdespawntowhat.edit.conditions.open"), button -> openEditor())
                .bounds(0, 0, 240, 20).build();
    }

    public void setValueConsumer(Consumer<ConditionExpression> consumer) {
        valueConsumer = consumer == null ? ignored -> { } : consumer;
    }

    private void openEditor() {
        Minecraft minecraft = Minecraft.getInstance();
        var parent = minecraft.screen;
        if (parent instanceof BaseConfigEditScreen<?> editScreen) {
            editScreen.preserveNestedScreenDraft();
        }
        minecraft.setScreen(new ConditionEditorScreen(parent, value, edited -> {
            value = edited;
            valueConsumer.accept(edited);
            if (parent instanceof BaseConfigEditScreen<?> editScreen) {
                editScreen.updateNestedConditionDraft(edited);
            }
            editButton.setMessage(summary());
        }));
    }

    private Component summary() {
        return ConditionExpressionPresenter.summary(value);
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        editButton.setX(getX());
        editButton.setY(getY());
        editButton.setMessage(summary());
        editButton.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return editButton.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public ConditionExpression value() {
        return value;
    }

    @Override
    public AbstractWidget widget() {
        return this;
    }

    @Override
    public void setValue(ConditionExpression value) {
        this.value = value == null ? new ConditionExpression() : value;
    }

    @Override
    public void clear() {
        value = new ConditionExpression();
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narration) {
    }
}
