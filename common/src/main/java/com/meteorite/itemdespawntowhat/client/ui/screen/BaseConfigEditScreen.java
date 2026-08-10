package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.register.ClientConversionTypeDefinition;
import com.meteorite.itemdespawntowhat.client.ui.form.FormDefinition;
import com.meteorite.itemdespawntowhat.client.ui.form.FormRenderer;
import com.meteorite.itemdespawntowhat.client.ui.handler.ConfigEditSessionHandler;
import com.meteorite.itemdespawntowhat.client.ui.panel.ConfigListPanel;
import com.meteorite.itemdespawntowhat.client.ui.panel.FormListPanel;
import com.meteorite.itemdespawntowhat.client.ui.support.EditCallback;
import com.meteorite.itemdespawntowhat.client.ui.support.ListScreenCallback;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.condition.ConditionExpression;
import com.meteorite.itemdespawntowhat.network.payload.c2s.ReleaseEditSessionPayload;
import com.meteorite.itemdespawntowhat.platform.Services;
import com.meteorite.itemdespawntowhat.util.PlayerStateChecker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.function.Supplier;

/**
 * 配置编辑界面薄壳，负责会话、列表生命周期和 Screen 事件委托。
 */
public class BaseConfigEditScreen<T extends BaseConversionConfig> extends Screen
        implements EditCallback<T>, ListScreenCallback {
    protected static final Logger LOGGER = LogManager.getLogger();

    protected T draftConfig;
    protected T resizeBackup;
    protected boolean listEditPerformed;
    protected boolean suppressDraftRestoreOnce;

    protected final ConfigEditSessionHandler<T> editHandler;
    private final Supplier<T> configFactory;
    private final ClientConversionTypeDefinition<T> definition;
    private FormDefinition<T> formDefinition;
    private FormRenderer<T> formRenderer;
    private Button configListButton;
    private Component errorMessage;
    private int errorDisplayTicks;
    private static final int ERROR_DISPLAY_DURATION = 120;

    protected FormListPanel formList;

    public BaseConfigEditScreen(ClientConversionTypeDefinition<T> definition) {
        super(Component.translatable("gui.itemdespawntowhat.edit.title", definition.fileName()));
        this.definition = definition;
        this.editHandler = new ConfigEditSessionHandler<>(definition.id(), definition.codec());
        this.configFactory = definition.configFactory();
    }

    @Override
    protected void init() {
        super.init();
        formList = new FormListPanel(minecraft, width, height - 80, 36, height - 215);
        addRenderableWidget(formList);
        formDefinition = definition.createFormDefinition(font);
        formRenderer = new FormRenderer<>(font, formList, formDefinition);
        formRenderer.initialize();
        initButtons();
        clearFields();
        restoreResizeBackup();
    }

    private void initButtons() {
        int centerX = width / 2;
        int y = height - 28;
        addRenderableWidget(Button.builder(Component.translatable("gui.itemdespawntowhat.save_to_cache"), button -> {
            if (validateAllFields()) {
                editHandler.saveCurrentToCache(this);
            } else {
                onSaveError();
            }
        }).bounds(centerX - 160, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.itemdespawntowhat.apply_to_file"),
                button -> editHandler.applyToFile(this)).bounds(centerX - 50, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(centerX + 60, y, 100, 20).build());

        configListButton = Button.builder(buildConfigListButtonLabel(), button -> openConfigListScreen())
                .bounds(width - 110, 6, 100, 16).build();
        addRenderableWidget(configListButton);
    }

    @Override
    public void onClearFields() {
        clearFields();
    }

    @Override
    public T buildConfigFromFields() {
        T config = configFactory.get();
        formRenderer.writeTo(config);
        return config;
    }

    @Override
    public void onRefillFields(T config) {
        formRenderer.readFrom(config);
        clearAllSuggestions();
    }

    @Override
    public void onListChanged() {
        refreshConfigListButton();
    }

    @Override
    public void onClose() {
        List<?> pending = editHandler.getPendingConfigs();
        if (!pending.isEmpty()) {
            LOGGER.info("Discarding {} unsaved configs", pending.size());
        }
        ConfigListPanel.clearEntityCache();
        if (minecraft != null) {
            if (minecraft.player != null) {
                Services.PLATFORM.sendToServer(new ReleaseEditSessionPayload());
            }
            minecraft.setScreen(new ConfigTypeSelectionScreen());
        }
    }

    @Override
    public void onSaveError() {
        onDisplayError(Component.translatable("gui.itemdespawntowhat.edit.save_error"));
    }

    @Override
    public void onDisplayError(Component message) {
        errorMessage = message.copy().withStyle(ChatFormatting.RED);
        errorDisplayTicks = ERROR_DISPLAY_DURATION;
    }

    @Override
    public void onEditRequested(ConfigListPanel.EntrySource source, int indexInSource) {
        loadSelectedFromList(source, indexInSource, true);
    }

    @Override
    public void onCopyRequested(ConfigListPanel.EntrySource source, int indexInSource) {
        loadSelectedFromList(source, indexInSource, false);
    }

    @Override
    public void onListDataChanged() {
        refreshConfigListButton();
    }

    private void loadSelectedFromList(ConfigListPanel.EntrySource source, int indexInSource, boolean removeFromList) {
        List<T> list = source == ConfigListPanel.EntrySource.ORIGINAL
                ? editHandler.getOriginalConfigs() : editHandler.getPendingConfigs();
        if (indexInSource < 0 || indexInSource >= list.size()) {
            return;
        }
        suppressDraftRestoreOnce = true;
        T selected = removeFromList ? list.remove(indexInSource) : list.get(indexInSource);
        onClearFields();
        onRefillFields(selected);
        if (removeFromList) {
            listEditPerformed = true;
            onListChanged();
        }
    }

    @Override
    public void onListScreenClosed() {
        if (!suppressDraftRestoreOnce && !listEditPerformed && draftConfig != null) {
            onRefillFields(draftConfig);
        }
        listEditPerformed = false;
        suppressDraftRestoreOnce = false;
        draftConfig = null;
        refreshConfigListButton();
    }

    private Component buildConfigListButtonLabel() {
        int total = editHandler.getOriginalConfigs().size() + editHandler.getPendingConfigs().size();
        int pending = editHandler.getPendingConfigs().size();
        if (pending > 0) {
            return Component.translatable("gui.itemdespawntowhat.edit.config_stat_2",
                    Component.literal(String.valueOf(total)).withStyle(ChatFormatting.GREEN),
                    Component.literal(String.valueOf(total - pending)).withStyle(ChatFormatting.GREEN),
                    Component.literal(String.valueOf(pending)).withStyle(ChatFormatting.YELLOW));
        }
        return Component.translatable("gui.itemdespawntowhat.edit.config_stat_1", total);
    }

    private void refreshConfigListButton() {
        if (configListButton != null) {
            configListButton.setMessage(buildConfigListButtonLabel());
        }
    }

    private void openConfigListScreen() {
        draftConfig = buildConfigFromFields();
        listEditPerformed = false;
        suppressDraftRestoreOnce = false;
        if (minecraft != null) {
            minecraft.setScreen(new ConfigListScreen<>(this, editHandler, this));
        }
    }

    // 子屏打开前保存完整表单，父屏重新初始化后据此恢复。
    public void preserveNestedScreenDraft() {
        resizeBackup = buildConfigFromFields();
    }

    // 条件子屏完成后更新保存中的草稿，避免父屏重建时覆盖编辑结果。
    public void updateNestedConditionDraft(ConditionExpression expression) {
        if (resizeBackup == null) {
            resizeBackup = buildConfigFromFields();
        }
        resizeBackup.setConditionExpression(expression);
    }

    protected void clearFields() {
        formRenderer.clear();
    }

    protected boolean validateAllFields() {
        return formRenderer.validateAll();
    }

    protected void clearAllSuggestions() {
        formRenderer.clearSuggestions();
    }

    private void restoreResizeBackup() {
        if (resizeBackup != null) {
            onRefillFields(resizeBackup);
            resizeBackup = null;
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        Component mode = minecraft != null && PlayerStateChecker.isSinglePlayerMode(minecraft)
                ? Component.translatable("gui.itemdespawntowhat.edit.mode.local_mode")
                : Component.translatable("gui.itemdespawntowhat.edit.mode.server_mode");
        graphics.drawString(font, mode, 10, 12, 0x808080, false);
        if (errorMessage != null && errorDisplayTicks > 0) {
            graphics.drawCenteredString(font, errorMessage, width / 2, 24, 0xFFFFFF);
            errorDisplayTicks--;
            if (errorDisplayTicks <= 0) {
                errorMessage = null;
            }
        }
        formRenderer.render(graphics, mouseX, mouseY);
    }

    protected void clearAllFocus() {
        formRenderer.clearFocus();
    }

    @Override
    public void setFocused(GuiEventListener focused) {
        if (focused instanceof Button || focused instanceof CycleButton) {
            super.setFocused(null);
        } else {
            super.setFocused(focused);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (formRenderer.mouseClicked(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (formRenderer.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return formRenderer.keyPressed(keyCode, scanCode, modifiers)
                || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return formRenderer.charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }

    @Override
    public void resize(@NotNull Minecraft minecraft, int width, int height) {
        resizeBackup = buildConfigFromFields();
        super.resize(minecraft, width, height);
    }

    @Override
    public void tick() {
        super.tick();
        formRenderer.refreshVisibility();
    }

}
