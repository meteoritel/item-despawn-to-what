package com.meteorite.itemdespawntowhat.client.ui.view;

import com.meteorite.itemdespawntowhat.client.ui.form.FormFieldInput;
import com.meteorite.itemdespawntowhat.client.ui.screen.RuleConditionEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * 主表单中的条件摘要字段（视图模型版）：点击进入 DNF 条件子屏，编辑结果写回视图模型。
 */
public final class ConditionFieldInput extends AbstractWidget implements FormFieldInput<ConditionView> {

    private final Button editButton;
    private ConditionView value = ConditionView.empty();
    private Runnable beforeOpen = () -> { };
    private Consumer<ConditionView> onEdited = edited -> { };
    private boolean editable = true;

    public ConditionFieldInput() {
        super(0, 0, 240, 20, Component.empty());
        editButton = Button.builder(summary(), button -> openEditor())
                .bounds(0, 0, 240, 20).build();
    }

    // 打开子屏前先把表单写回草稿，避免子屏返回后父屏重建覆盖编辑结果
    public void setBeforeOpen(Runnable beforeOpen) {
        this.beforeOpen = beforeOpen == null ? () -> { } : beforeOpen;
    }

    // 子屏保存后的立即写回钩子（父屏重建前把编辑结果落到草稿）
    public void setOnEdited(Consumer<ConditionView> onEdited) {
        this.onEdited = onEdited == null ? edited -> { } : onEdited;
    }

    // 多效果只读等场景下禁用编辑入口
    public void setEditable(boolean editable) {
        this.editable = editable;
        editButton.active = editable;
        this.active = editable;
    }

    private void openEditor() {
        if (!editable) {
            return;
        }
        beforeOpen.run();
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.screen;
        minecraft.setScreen(new RuleConditionEditorScreen(parent, value, edited -> {
            value = edited == null ? ConditionView.empty() : edited;
            editButton.setMessage(summary());
            onEdited.accept(value);
        }));
    }

    private Component summary() {
        return value == null ? ConditionView.empty().summary() : value.summary();
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
    public ConditionView value() {
        return value;
    }

    @Override
    public void setValue(ConditionView value) {
        this.value = value == null ? ConditionView.empty() : value;
        editButton.setMessage(summary());
    }

    @Override
    public void clear() {
        value = ConditionView.empty();
        editButton.setMessage(summary());
    }

    @Override
    public AbstractWidget widget() {
        return this;
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narration) {
    }
}
