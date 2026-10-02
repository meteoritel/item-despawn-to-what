package com.meteorite.itemdespawntowhat.client.ui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 后端切换期间的编辑器占位入口；规则暂由 JSON 和服务端命令管理。 */
public final class RuleEditorPlaceholderScreen extends Screen {
    public RuleEditorPlaceholderScreen() {
        super(Component.translatable("gui.itemdespawntowhat.placeholder.title"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 70, height / 2 + 42, 140, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 48, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("gui.itemdespawntowhat.placeholder.message"),
                width / 2, height / 2 - 14, 0xAAAAAA);
        graphics.drawCenteredString(font, Component.translatable("gui.itemdespawntowhat.placeholder.commands"),
                width / 2, height / 2 + 8, 0xAAAAAA);
    }
}
