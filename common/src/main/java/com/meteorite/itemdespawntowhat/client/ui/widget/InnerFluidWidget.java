package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.config.catalogue.InnerFluid;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class InnerFluidWidget extends AbstractCompositeWidget{
    // ===== 本地化键名 ===== //
    private static final String KEY_FLUID_LABEL = "gui.itemdespawntowhat.edit.inner_fluid.fluid_label";
    private static final String KEY_SOURCE_LABEL = "gui.itemdespawntowhat.edit.inner_fluid.source_label";
    private static final String KEY_CONSUME_LABEL = "gui.itemdespawntowhat.edit.inner_fluid.consume_label";
    private static final String KEY_SOURCE_ON = "gui.itemdespawntowhat.edit.on";
    private static final String KEY_SOURCE_OFF = "gui.itemdespawntowhat.edit.off";

    // ===== 布局常量 ===== //
    private static final int TOTAL_WIDTH = 240;
    private static final int BOX_HEIGHT = 18;
    private static final int BTN_HEIGHT = 18;
    private static final int LABEL_HEIGHT = 9;
    private static final int H_GAP = 6;
    private static final int V_GAP = 3;

    private static final int BTN_WIDTH = (TOTAL_WIDTH - H_GAP) / 2;

    private final Font font;

    private final EditBox fluidBox;
    private final CycleButton<Boolean> sourceButton;
    private final CycleButton<Boolean> consumeFluidButton;

    public InnerFluidWidget(Font font, int x, int y) {
        super(x, y, TOTAL_WIDTH, getTotalHeight(), Component.empty());
        this.font = font;

        this.fluidBox = new EditBox(font, 0, 0, TOTAL_WIDTH, BOX_HEIGHT, Component.empty());
        this.fluidBox.setMaxLength(256);
        this.sourceButton = CycleButton.<Boolean>builder(
                        value -> Component.translatable(value ? KEY_SOURCE_ON : KEY_SOURCE_OFF))
                .withValues(true, false)
                .withInitialValue(true)
                .create(0, 0, BTN_WIDTH, BTN_HEIGHT,
                        Component.translatable(KEY_SOURCE_LABEL));

        this.consumeFluidButton = CycleButton.<Boolean>builder(
                value -> Component.translatable(value ? KEY_SOURCE_ON : KEY_SOURCE_OFF))
                .withValues(true, false)
                .withInitialValue(true)
                .create(0, 0, BTN_WIDTH, BTN_HEIGHT,
                        Component.translatable(KEY_CONSUME_LABEL));
    }

    public static int getTotalHeight() {
        return LABEL_HEIGHT + V_GAP +
                BOX_HEIGHT + V_GAP +
                BTN_HEIGHT;
    }

    // ========== 渲染 ========== //
    @Override
    protected void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();

        // 第一行：列标签
        gfx.drawString(font, Component.translatable(KEY_FLUID_LABEL), x, y, 0xAAAAAA, false);

        // 第二行：输入框
        int boxY = y + LABEL_HEIGHT + V_GAP;
        fluidBox.setX(x);
        fluidBox.setY(boxY);
        fluidBox.render(gfx, mouseX, mouseY, partialTick);

        // 第三行：两个按钮
        int buttonY = boxY + BOX_HEIGHT + V_GAP;
        sourceButton.setX(x);
        sourceButton.setY(buttonY);
        sourceButton.render(gfx, mouseX, mouseY, partialTick);

        consumeFluidButton.setX(x + BTN_WIDTH + H_GAP);
        consumeFluidButton.setY(buttonY);
        consumeFluidButton.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    protected Iterable<EditBox> getEditBoxes() {
        return List.of(fluidBox);
    }

    // 将按钮点击路由进来
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (sourceButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        } else if (consumeFluidButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ========== 值绑定 ========== //
    @Nullable
    public InnerFluid getValue() {
        InnerFluid value = new InnerFluid(fluidBox.getValue().trim(), sourceButton.getValue(), consumeFluidButton.getValue());
        return value.hasInnerFluid() ? value : null;
    }

    public void setValue(@Nullable InnerFluid innerFluid) {
        if (innerFluid == null || !innerFluid.hasInnerFluid()) {
            clear();
            return;
        }
        fluidBox.setValue(innerFluid.getFluidId());
        sourceButton.setValue(innerFluid.isRequireSource());
        consumeFluidButton.setValue(innerFluid.isConsumeFluid());
    }

    public void clear() {
        fluidBox.setValue("");
        sourceButton.setValue(true);
        consumeFluidButton.setValue(false);
    }

    public EditBox getFluidBox() {
        return fluidBox;
    }
}
