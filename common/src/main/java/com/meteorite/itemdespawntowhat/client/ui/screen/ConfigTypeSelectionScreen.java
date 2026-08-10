package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.register.ClientConversionTypeDefinition;
import com.meteorite.itemdespawntowhat.client.register.ClientConversionTypeRegistry;
import com.meteorite.itemdespawntowhat.network.payload.c2s.RequestConfigSnapshotPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.meteorite.itemdespawntowhat.platform.Services;
import org.jetbrains.annotations.NotNull;

/**
 * 进入具体编辑器前的配置类型选择界面。
 */
public class ConfigTypeSelectionScreen extends Screen {

    public ConfigTypeSelectionScreen() {
        super(Component.translatable("gui.itemdespawntowhat.config_selection.title"));
    }

    @Override
    protected void init() {
        var types = ClientConversionTypeRegistry.all();
        int y = height / 2 - (types.size() * 25) / 2;
        for (ClientConversionTypeDefinition<?> type : types) {
            String path = type.id().getPath();
            Button button = Button.builder(
                            Component.translatable("gui.itemdespawntowhat.config_type." + path),
                    btn -> requestConfigSnapshot(type.id()))
                    .bounds(width / 2 - 100, y, 200, 20).build();

            // 添加按钮tooltip
            button.setTooltip(Tooltip.create(
                    Component.translatable("gui.itemdespawntowhat.config_type." + path + ".tooltip")));
            addRenderableWidget(button);
            y += 25;
        }
    }

    // 这里只负责向服务端申请快照，不直接构建编辑界面。
    private void requestConfigSnapshot(ResourceLocation typeId) {
        if (minecraft != null) {
            Services.PLATFORM.sendToServer(new RequestConfigSnapshotPayload(typeId));
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
    }
}
