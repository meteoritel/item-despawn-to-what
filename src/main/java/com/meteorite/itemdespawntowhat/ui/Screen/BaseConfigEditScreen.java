package com.meteorite.itemdespawntowhat.ui.Screen;

import com.meteorite.itemdespawntowhat.ConfigManager;
import com.meteorite.itemdespawntowhat.config.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.handler.BaseConfigHandler;
import com.meteorite.itemdespawntowhat.ui.FormList;
import com.meteorite.itemdespawntowhat.ui.SurroundingBlocksWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseConfigEditScreen<T extends BaseConversionConfig> extends Screen {
    protected static final Logger LOGGER = LogManager.getLogger();

    // 配置相关
    protected final ConfigType configType;
    protected final BaseConfigHandler<T> handler;
    protected final List<T> originalConfigs;
    protected final List<T> pendingConfigs;

    // UI 组件
    protected EditBox itemIdInput;
    protected EditBox dimensionInput;
    protected CycleButton<Boolean> needOutdoorButton;
    protected SurroundingBlocksWidget surroundingWidget;
    protected EditBox resultIdInput;
    protected EditBox conversionTimeInput;
    protected EditBox resultMultipleInput;

    // UI 本体
    protected FormList formList;

    public BaseConfigEditScreen(ConfigType configType) {
        super(Component.translatable("gui.itemdespawntowhat.edit.title", configType.getFileName()));
        this.configType = configType;
        this.handler = ConfigManager.getInstance().getHandler(configType);
        if (handler == null) {
            throw new IllegalStateException("No handler registered for " + configType);
        }
        this.originalConfigs = handler.loadConfig();   // 加载现有配置
        this.pendingConfigs = new ArrayList<>();
    }

    @Override
    protected void init() {
        super.init();

        formList = new FormList(
                minecraft,
                width,
                height - 80,
                36,
                height - 216
        );

        addRenderableWidget(formList);

        // ===== 添加通用字段 =====
        itemIdInput = textBox();
        formList.add("Item ID", itemIdInput);

        dimensionInput = textBox();
        formList.add("Dimension", dimensionInput);

        needOutdoorButton = CycleButton.booleanBuilder(
                        Component.literal("ON"),
                        Component.literal("OFF")
                ).withInitialValue(false)
                .create(0, 0, 180, 20, Component.empty());
        formList.add("Need Outdoor", needOutdoorButton);

        surroundingWidget = new SurroundingBlocksWidget(font, 0, 0);
        formList.add("Surrounding Blocks", surroundingWidget);

        resultIdInput = textBox();
        formList.add("Result ID", resultIdInput);

        conversionTimeInput = numericBox();
        formList.add("Conversion Time (s)", conversionTimeInput);

        resultMultipleInput = numericBox();
        formList.add("Result Multiple", resultMultipleInput);

        // ===== 子类字段 =====
        addCustomEntries(formList);

        initButtons();
        clearFields();

    }

    private void initButtons() {
        int centerX = width / 2;
        int y = height - 28;

        addRenderableWidget(Button.builder(
                Component.translatable("gui.itemdespawntowhat.save_to_cache"),
                b -> saveCurrentToCache()
        ).bounds(centerX - 160, y, 100, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.itemdespawntowhat.apply_to_file"),
                b -> applyToFile()
        ).bounds(centerX - 50, y, 100, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.cancel"),
                b -> onClose()
        ).bounds(centerX + 60, y, 100, 20).build());
    }

    // ========== 辅助布局方法 ========== //

    // 普通的文本输入框
    protected EditBox textBox() {
        EditBox box = new EditBox(font, 0, 0, 220, 18, Component.empty());
        box.setMaxLength(256);
        return box;
    }

    // 数字输入框
    protected EditBox numericBox() {
        EditBox box = textBox();
        box.setFilter(s -> s.matches("\\d*")); // 仅允许数字
        return box;
    }

    // 清除所有焦点
    protected void clearAllFocus() {
        itemIdInput.setFocused(false);
        dimensionInput.setFocused(false);
        resultIdInput.setFocused(false);
        conversionTimeInput.setFocused(false);
        resultMultipleInput.setFocused(false);
        surroundingWidget.clearFocus();
    }

    // ========== 通用字段处理 ========== //

    // 填充通用字段到给定的配置对象
    protected void populateCommonFields(T config) {
        config.setItemId(ResourceLocation.tryParse(itemIdInput.getValue()));
        config.setDimension(dimensionInput.getValue().isEmpty() ? null : dimensionInput.getValue());
        config.setNeedOutdoor(needOutdoorButton.getValue());
        config.setSurroundingBlocks(surroundingWidget.getValue());
        config.setResultId(ResourceLocation.tryParse(resultIdInput.getValue()));
        config.setConversionTime(parseInt(conversionTimeInput.getValue(),300));
        config.setResultMultiple(parseInt(resultMultipleInput.getValue(), 1));
    }

    protected void clearFields() {
        itemIdInput.setValue("");
        dimensionInput.setValue("");
        needOutdoorButton.setValue(false);
        surroundingWidget.clear();
        resultIdInput.setValue("");
        conversionTimeInput.setValue("");
        resultMultipleInput.setValue("");
        clearCustomFields();
    }

    protected int parseInt(String boxInput, int def) {
        try {
            return Integer.parseInt(boxInput);
        } catch (Exception e) {
            return def;
        }
    }

    // ========== 缓存操作 ========== //
    // 将当前表单内容写入缓存
    private void saveCurrentToCache() {
        T config = createConfigFromFields();
        if (!config.shouldProcess()) {
            return;
        }
        pendingConfigs.add(config);
        clearFields();
    }

    // 将缓存写入文件，并清空缓存
    private void applyToFile() {
        if (pendingConfigs.isEmpty()) {
            onClose();
            return;
        }
        // 合并原始配置与缓存配置
        List<T> allConfigs = new ArrayList<>(originalConfigs);
        allConfigs.addAll(pendingConfigs);
        try {
            handler.saveConfig(allConfigs);
            LOGGER.info("Applied {} new configs to {}", pendingConfigs.size(), configType.getFileName());

            originalConfigs.clear();
            pendingConfigs.clear();

            // 返回上一级（主选择界面）
            if (minecraft != null) {
                minecraft.setScreen(new ConfigSelectionScreen());
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save config file: {}", configType.getFileName(), e);
        }
    }

    // ========== 渲染 ========== //

    // 绘制所有标签
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    // 返回上一级
    @Override
    public void onClose() {
        if (!pendingConfigs.isEmpty()) {
            LOGGER.info("Discarding {} unsaved configs", pendingConfigs.size());
        }
        if (minecraft != null) {
            minecraft.setScreen(new ConfigSelectionScreen());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (!handled) {
            clearAllFocus();
        }
        return handled;
    }

    // ========== 子类方法 ========== //

    protected abstract void addCustomEntries(FormList fromList);
    // 构建配置对象，子类需负责填充子类字段，并调用父类的通用字段填充方法。
    protected abstract T createConfigFromFields();
    // 清空子类字段
    protected abstract void clearCustomFields();

}
