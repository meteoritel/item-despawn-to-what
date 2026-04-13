package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.client.configedit.BaseConfigEditHandler;
import com.meteorite.itemdespawntowhat.client.ui.ListScreenCallback;
import com.meteorite.itemdespawntowhat.client.ui.panel.ConfigListPanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ConfigListScreen<T extends BaseConversionConfig> extends Screen {

    private enum BottomAction {
        EDIT, DELETE, COPY
    }

    private final BaseConfigEditHandler<T> editHandler;
    private final ListScreenCallback<T> listCallback;
    private final Screen parentScreen;

    // 列表面板
    private ConfigListPanel<T> listPanel;

    // 底部操作按钮（依赖选中项）
    private Button editButton;
    private Button deleteButton;
    private Button copyButton;

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
        listPanel = new ConfigListPanel<>(
                minecraft,
                width,
                height,
                80,
                36,
                editHandler.getOriginalConfigs(),
                editHandler.getPendingConfigs(),
                this::performEditConfirmed,
                this::performDeleteConfirmed
        );
        addRenderableWidget(listPanel);

        // 底部按钮区，居中排列：Edit | Delete | Copy | Back
        int btnW = 80;
        int btnH = 20;
        int gap = 6;
        int totalW = btnW * 4 + gap * 3;
        int startX = (width - totalW) / 2;
        int btnY = height - 28;

        editButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.itemdespawntowhat.edit.list.edit"),
                b -> invokeBottomAction(BottomAction.EDIT)
        ).bounds(startX, btnY, btnW, btnH).build());

        deleteButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.itemdespawntowhat.edit.list.delete"),
                b -> invokeBottomAction(BottomAction.DELETE)
        ).bounds(startX + btnW + gap, btnY, btnW, btnH).build());

        copyButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.itemdespawntowhat.edit.list.copy"),
                b -> invokeBottomAction(BottomAction.COPY)
        ).bounds(startX + (btnW + gap) * 2, btnY, btnW, btnH).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.back"),
                b -> onClose()
        ).bounds(startX + (btnW + gap) * 3, btnY, btnW, btnH).build());

        editButton.active = false;
        deleteButton.active = false;
        copyButton.active = false;
    }

    @Override
    public void tick() {
        super.tick();
        boolean hasSelection = listPanel != null && listPanel.getSelectedEntry() != null;
        editButton.active = hasSelection;
        deleteButton.active = hasSelection;
        copyButton.active = hasSelection;
    }

    // ========== 列表事件处理 ========== //
    private void invokeBottomAction(BottomAction action) {
        ConfigListPanel.ConfigEntry<T> sel = listPanel.getSelectedEntry();
        if (sel == null) {
            return;
        }

        switch (action) {
            case EDIT -> listPanel.requestEditSelected();
            case DELETE -> listPanel.requestDeleteSelected();
            case COPY -> handleCopy(sel.getSource(), sel.getIndexInSource());
        }
    }

    private void handleCopy(ConfigListPanel.EntrySource source, int indexInSource) {
        if (minecraft != null) {
            minecraft.tell(() -> {
                minecraft.setScreen(parentScreen);
                minecraft.tell(() -> {
                    listCallback.onCopyRequested(source, indexInSource);
                    listCallback.onListScreenClosed();
                });
            });
        }
    }

    private void performEditConfirmed(ConfigListPanel.EntrySource source, int indexInSource) {
        if (minecraft != null) {
            minecraft.tell(() -> {
                minecraft.setScreen(parentScreen);
                minecraft.tell(() -> listCallback.onEditRequested(source, indexInSource));
            });
        }
    }

    private void performDeleteConfirmed(ConfigListPanel.EntrySource source, int indexInSource) {
        List<T> targetList = resolveListBySource(source);
        if (indexInSource < 0 || indexInSource >= targetList.size()) {
            return;
        }

        targetList.remove(indexInSource);
        listPanel.rebuild(editHandler.getOriginalConfigs(), editHandler.getPendingConfigs());
        listCallback.onListDataChanged();
    }

    private List<T> resolveListBySource(ConfigListPanel.EntrySource source) {
        return source == ConfigListPanel.EntrySource.ORIGINAL
                ? editHandler.getOriginalConfigs()
                : editHandler.getPendingConfigs();
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
