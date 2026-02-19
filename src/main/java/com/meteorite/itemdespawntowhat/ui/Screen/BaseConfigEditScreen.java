package com.meteorite.itemdespawntowhat.ui.Screen;

import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import com.meteorite.itemdespawntowhat.ConfigManager;
import com.meteorite.itemdespawntowhat.config.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.handler.BaseConfigHandler;
import com.meteorite.itemdespawntowhat.network.SaveConfigPayload;
import com.meteorite.itemdespawntowhat.ui.FormList;
import com.meteorite.itemdespawntowhat.ui.SurroundingBlocksWidget;
import com.meteorite.itemdespawntowhat.util.PlayerStateChecker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseConfigEditScreen<T extends BaseConversionConfig> extends Screen {
    protected static final Logger LOGGER = LogManager.getLogger();
    Minecraft mc;

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

    protected static final String LABEL_PREFIX = "gui.itemdespawntowhat.edit.";
    // UI 组件列表
    protected FormList formList;

    // 聚焦的组件
    protected AbstractWidget focusedWidget;

    public BaseConfigEditScreen(ConfigType configType) {
        super(Component.translatable("gui.itemdespawntowhat.edit.title", configType.getFileName()));
        this.mc = Minecraft.getInstance();
        this.configType = configType;
        this.handler = ConfigManager.getInstance().getHandler(configType);
        if (handler == null) {
            throw new IllegalStateException("No handler registered for " + configType);
        }

        List<T> configs = new ArrayList<>();

        if (!PlayerStateChecker.isClientWorldLoaded(mc)) {
            // 处于游戏主页面，缓存还未加载，需要手动加载本地文件
            configs = handler.loadConfig();
        }

        if(PlayerStateChecker.isSinglePlayerServerReady(mc)
                || PlayerStateChecker.isMultiPlayerServerConnected(mc)) {
            // 服务端已经加载，那就直接读取缓存
            configs = ConfigExtractorManager.getConfigByType(configType);
            LOGGER.debug("已从 {} 读取缓存，数量为 {}", this.configType.name(), configs.size());
        }

        this.originalConfigs = configs;
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
                height - 215
        );

        addRenderableWidget(formList);

        // ===== 添加通用字段 =====
        itemIdInput = textBox();
        formList.add(Component.translatable(LABEL_PREFIX + "item_id"), itemIdInput);

        dimensionInput = textBox();
        formList.add(Component.translatable(LABEL_PREFIX + "dimension"), dimensionInput);

        needOutdoorButton = CycleButton.booleanBuilder(
                        Component.translatable(LABEL_PREFIX + "need_outdoor." + "on"),
                        Component.translatable(LABEL_PREFIX + "need_outdoor." + "off")
                ).withInitialValue(false)
                .create(0, 0, 220, 18, Component.translatable(LABEL_PREFIX + "need_outdoor"));
        formList.add(Component.translatable(LABEL_PREFIX + "need_outdoor"), needOutdoorButton);

        surroundingWidget = new SurroundingBlocksWidget(font, 0, 0);
        formList.add(Component.translatable(LABEL_PREFIX + "surrounding_blocks"), surroundingWidget);

        resultIdInput = textBox();
        formList.add(Component.translatable(LABEL_PREFIX + "result_id"), resultIdInput);

        conversionTimeInput = numericBox();
        formList.add(Component.translatable(LABEL_PREFIX + "conversion_time"), conversionTimeInput);

        resultMultipleInput = numericBox();
        formList.add(Component.translatable(LABEL_PREFIX + "result_multiple"), resultMultipleInput);

        // 子类字段
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

    // 统一焦点管理
    public void setFocusedInput(@Nullable AbstractWidget widget) {
        if (focusedWidget == widget) return;

        // 旧焦点失效
        if (focusedWidget != null) {
            focusedWidget.setFocused(false);
            if (focusedWidget instanceof SurroundingBlocksWidget sbw) {
                sbw.clearFocus();
            }
        }

        focusedWidget = widget;

        // 新焦点生效
        if (widget != null) {
            widget.setFocused(true);
        }
    }

    // 将按钮等不需要焦点的排除出统一焦点管理
    public boolean shouldTakeFocus(AbstractWidget widget) {
        return !(widget instanceof Button)
                && !(widget instanceof CycleButton);
    }

    // 清除所有焦点
    protected void clearAllFocus() {
        setFocusedInput(null);
    }

    // ========== 通用字段处理 ========== //

    // 填充通用字段到给定的配置对象
    protected void populateCommonFields(T config) {
        if (itemIdInput.getValue().isEmpty() || resultIdInput.getValue().isEmpty()) {
            return;
        }

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

    // 安全解析字符串到数字
    protected int parseInt(String boxInput, int def) {
        try {
            return Integer.parseInt(boxInput);
        } catch (Exception e) {
            return def;
        }
    }

    // ========== 配置文件相关操作 ========== //
    // 将当前表单内容写入缓存
    private void saveCurrentToCache() {
        T config = createConfigFromFields();
        if (config.shouldProcess()) {
            pendingConfigs.add(config);
        } else {
            LOGGER.warn("Invalid config, this won't be saved");
        }

        clearFields();
    }

    // 将缓存写入文件，并清空缓存
    private void applyToFile() {

        // 先尝试将当前表单内容加入缓存
        T currentConfig = createConfigFromFields();
        if (currentConfig.shouldProcess()) {
            pendingConfigs.add(currentConfig);
            LOGGER.debug("Added current form to cache before applying");
        }

        if (pendingConfigs.isEmpty()) {
            LOGGER.debug("No configs to apply, closing screen");
            onClose();
            return;
        }

        // 根据环境选择保存方式
        if (!PlayerStateChecker.isClientWorldLoaded(mc)
                || PlayerStateChecker.isSinglePlayerServerReady(mc)) {
            // 本地环境：直接保存到本地文件
            applyToLocalFile();
        }

        if(PlayerStateChecker.isMultiPlayerServerConnected(mc)) {
            // 服务端环境：发送数据包到服务器
            applyToServer();
        }
    }

    // 本地保存
    private void applyToLocalFile() {
        try {
            // 合并原始配置与缓存配置
            List<T> allConfigs = new ArrayList<>(originalConfigs);
            allConfigs.addAll(pendingConfigs);

            handler.saveConfig(allConfigs);
            LOGGER.info("Applied {} new configs to local file: {}",
                    pendingConfigs.size(), configType.getFileName());

            originalConfigs.clear();
            pendingConfigs.clear();

            onClose();
        } catch (IOException e) {
            LOGGER.error("Failed to save config file: {}", configType.getFileName(), e);
        }
    }

    // 发包到服务端
    private void applyToServer() {
        try {
            List<T> allConfigs = new ArrayList<>(originalConfigs);
            allConfigs.addAll(pendingConfigs);
            // 创建并发送数据包
            String jsonData = handler.serializeToJson(allConfigs);
            SaveConfigPayload payload = new SaveConfigPayload(configType, jsonData);
            PacketDistributor.sendToServer(payload);

            LOGGER.info("Sent {} configs to server for type: {}",
                    pendingConfigs.size(), configType.getFileName());

            // 清空缓存
            pendingConfigs.clear();

            // 返回上一级
            onClose();
        } catch (Exception e) {
            LOGGER.error("Failed to send config packet to server", e);
        }
    }

    // ========== 渲染 ========== //

    // 绘制所有标签
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);

        // 显示当前模式
        String modeText = null;
        // 处于主页面或者单人服务器世界中
        if (!PlayerStateChecker.isClientWorldLoaded(mc)
                || PlayerStateChecker.isSinglePlayerServerReady(mc)) {
            modeText = "Local Mode";
        } else if (PlayerStateChecker.isMultiPlayerServerConnected(mc)) {
            modeText = "Server Mode";
        }

        guiGraphics.drawString(font, modeText, 10, 12, 0x808080);

        // 显示缓存中的配置数量
        if (!pendingConfigs.isEmpty()) {
            String cacheInfo = "Cached: " + pendingConfigs.size();
            guiGraphics.drawString(font, cacheInfo, width - 80, 12, 0xFFFF00);
        }
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
    public void setFocused(GuiEventListener focused) {
        if (focused instanceof AbstractWidget widget && !shouldTakeFocus(widget)) {
            // 不允许非输入组件持有Screen焦点
            super.setFocused(null);
            return;
        }
        super.setFocused(focused);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        clearAllFocus();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ========== 子类方法 ========== //

    // 添加子类特有组件
    protected abstract void addCustomEntries(FormList fromList);
    // 构建配置对象
    protected abstract T createConfigFromFields();
    // 清空子类组件
    protected abstract void clearCustomFields();

}
