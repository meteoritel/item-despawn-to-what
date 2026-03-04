package com.meteorite.itemdespawntowhat.ui.Screen;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.ui.BaseConfigEditHandler;
import com.meteorite.itemdespawntowhat.ui.ListScreenCallback;
import com.meteorite.itemdespawntowhat.ui.panel.ConfigListPanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class ConfigListScreen<T extends BaseConversionConfig> extends Screen {

    private final BaseConfigEditHandler<T> editHandler;
    private final ListScreenCallback<T> listCallback;
    private final Screen parentScreen;

    //** 列表面板 */
    private ConfigListPanel<T> listPanel;

    public ConfigListScreen(
            Screen parentScreen,
            BaseConfigEditHandler<T> editHandler,
            ListScreenCallback<T> listCallback
    ) {
        super(Component.translatable("gui.itemdespawntowhat.edit.title",
                editHandler.getConfigType().getFileName()));
        this.parentScreen = parentScreen;
        this.editHandler = editHandler;
        this.listCallback = listCallback;
    }

    @Override
    protected void init() {
        // 列表面板：从顶部 36px 开始，底部留 80px 给按钮
        listPanel = new ConfigListPanel<>(
                minecraft,
                width,
                height,
                80,
                36,
                editHandler.getOriginalConfigs(),
                editHandler.getPendingConfigs(),
                this::handleEdit,
                this::handleDelete
        );
        addRenderableWidget(listPanel);

        // 底部"关闭"按钮
        addRenderableWidget(Button.builder(
                Component.translatable("gui.back"),
                b -> onClose()
        ).bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    // ========== 列表事件处理 ========== //
    private void handleEdit(T config, ConfigListPanel.EntrySource source, int indexInSource) {
        if (minecraft != null) {
            minecraft.tell(() -> {
                minecraft.setScreen(parentScreen);
                minecraft.tell(() -> listCallback.onEditRequested(source, indexInSource));
            });
        }
    }

    private void handleDelete(T config, ConfigListPanel.EntrySource source, int indexInSource) {
        listCallback.onDeleteRequested(source, indexInSource);
        // 刷新列表面板
        listPanel.rebuild(editHandler.getOriginalConfigs(), editHandler.getPendingConfigs());
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parentScreen);
        }
        listCallback.onListScreenClosed();
    }

    // ========== 渲染 ========== //

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);

        // 统计行
        int total = editHandler.getOriginalConfigs().size() + editHandler.getPendingConfigs().size();
        int pending = editHandler.getPendingConfigs().size();
        Component stat = Component.translatable("gui.itemdespawntowhat.edit.stat", total, pending);
        guiGraphics.drawString(font, stat, width - font.width(stat) - 6, 12, 0xAAAAAA);
    }

}
