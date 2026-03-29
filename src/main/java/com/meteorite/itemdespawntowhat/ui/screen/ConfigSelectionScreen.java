package com.meteorite.itemdespawntowhat.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.ConfigScreenRegistry;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

// 配置选择UI
public class ConfigSelectionScreen extends Screen {

    public ConfigSelectionScreen() {
        super(Component.translatable("gui.itemdespawntowhat.config_selection.title"));
    }

    @Override
    protected void init() {
        int y = height / 2 - (ConfigType.values().length * 25) / 2;
        for (ConfigType type : ConfigType.values()) {
            Button button = Button.builder(
                            Component.translatable("gui.itemdespawntowhat.config_type." + type.name().toLowerCase()),
                            btn -> openConfigList(type))
                    .bounds(width / 2 - 100, y, 200, 20).build();
            button.setTooltip(Tooltip.create(
                    Component.translatable("gui.itemdespawntowhat.config_type." + type.name().toLowerCase() + ".tooltip")));
            addRenderableWidget(button);
            y += 25;
        }
    }

    private void openConfigList(ConfigType type) {
        if (minecraft != null) {
            minecraft.setScreen(ConfigScreenRegistry.create(type));
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
    }
}
