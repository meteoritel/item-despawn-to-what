package com.meteorite.itemdespawntowhat.ui.Screen;

import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import com.meteorite.itemdespawntowhat.ConfigManager;
import com.meteorite.itemdespawntowhat.config.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.handler.BaseConfigHandler;
import com.meteorite.itemdespawntowhat.network.SaveConfigPayload;
import com.meteorite.itemdespawntowhat.ui.FormList;
import com.meteorite.itemdespawntowhat.ui.SuggestionWidget;
import com.meteorite.itemdespawntowhat.ui.SurroundingBlocksWidget;
import com.meteorite.itemdespawntowhat.util.PlayerStateChecker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
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
    protected static final String LABEL_PREFIX = "gui.itemdespawntowhat.edit.";
    protected static final int  BOX_WIDTH = 240;

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

    // UI 组件列表
    protected FormList formList;
    // 物品ID建议组件
    protected SuggestionWidget itemIdSuggestion;
    protected SuggestionWidget resultIdSuggestion;
    // 周围方块组件有6个文本框，需要定义多个建议组件
    protected List<SuggestionWidget> sbSuggestions = new ArrayList<>();

    // 存储所有建议组件的列表
    protected final List<SuggestionWidget> suggestionWidgets = new ArrayList<>();
    // 游戏主类实例
    Minecraft mc;
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
            LOGGER.debug("Read cache from {} , count is {}", this.configType.name(), configs.size());
        }

        this.originalConfigs = configs;
        this.pendingConfigs = new ArrayList<>();
    }

    @Override
    protected void init() {
        super.init();
        focusedWidget = null;

        formList = new FormList(
                minecraft,
                width,
                height - 80,
                36,
                height - 215
        );
        addRenderableWidget(formList);

        // 创造字段对应实例
        itemIdInput = textBox();
        dimensionInput = textBox();
        needOutdoorButton = CycleButton.booleanBuilder(
                        Component.translatable(LABEL_PREFIX + "need_outdoor." + "on"),
                        Component.translatable(LABEL_PREFIX + "need_outdoor." + "off")
                ).withInitialValue(false)
                .create(0, 0, BOX_WIDTH, 18, Component.translatable(LABEL_PREFIX + "need_outdoor"));
        surroundingWidget = new SurroundingBlocksWidget(font, 0, 0);
        resultIdInput = textBox();
        conversionTimeInput = numericBox();
        resultMultipleInput = numericBox();

        // 将组件加入列表
        formList.add(Component.translatable(LABEL_PREFIX + "item_id"), itemIdInput);
        formList.add(Component.translatable(LABEL_PREFIX + "dimension"), dimensionInput);
        formList.add(Component.translatable(LABEL_PREFIX + "need_outdoor"), needOutdoorButton);
        formList.add(Component.translatable(LABEL_PREFIX + "surrounding_blocks"), surroundingWidget);
        formList.add(Component.translatable(LABEL_PREFIX + "result_id"), resultIdInput);
        formList.add(Component.translatable(LABEL_PREFIX + "conversion_time"), conversionTimeInput);
        formList.add(Component.translatable(LABEL_PREFIX + "result_multiple"), resultMultipleInput);
        // 将子类字段加入列表
        addCustomEntries(formList);
        // 注册下拉建议框组件，在所有列表组件添加完成之后再添加，并添加文本监听
        itemIdSuggestion = registerSuggestion(itemIdInput, BuiltInRegistries.ITEM);
        for (EditBox box : surroundingWidget.getBoxes().values()) {
            SuggestionWidget widget = registerSuggestion(box, BuiltInRegistries.BLOCK);
            sbSuggestions.add(widget);
            addSuggestionListener(box,widget);
        }
        addSuggestionListener(itemIdInput, itemIdSuggestion);

        // 添加子类下拉建议框监听
        addCustomSuggestion();

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
        EditBox box = new EditBox(font, 0, 0, BOX_WIDTH, 18, Component.empty());
        box.setMaxLength(256);
        return box;
    }

    // 数字输入框
    protected EditBox numericBox() {
        EditBox box = textBox();
        box.setFilter(s -> s.matches("\\d*")); // 仅允许数字
        return box;
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

        for (SuggestionWidget widget : suggestionWidgets) {
            widget.hide();
        }
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

        // 因为文本的按钮文本开启了深度测试，所以需要把z值拉高
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);
        // 建议下拉框要最后渲染
        for (SuggestionWidget widget : suggestionWidgets) {
            renderSuggestion(guiGraphics, mouseX, mouseY, widget);
        }
        guiGraphics.pose().popPose();
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

    // ========== 统一焦点管理 ========== //

    public void setFocusedWidget(@Nullable AbstractWidget widget) {
        if (focusedWidget == widget) return;

        // 旧焦点失效
        if (focusedWidget != null) {
            focusedWidget.setFocused(false);
            if (focusedWidget instanceof SurroundingBlocksWidget sbw) {
                sbw.clearInternalFocus();
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
        setFocusedWidget(null);
    }

    @Nullable
    public AbstractWidget getFocusedWidget() {
        return focusedWidget;
    }

    // ========== 输入相关 ========== //
    @Override
    public void setFocused(GuiEventListener focused) {
        if (focused instanceof Button || focused instanceof CycleButton) {
            // 不允许非输入组件持有Screen焦点
            super.setFocused(null);
        } else {
            super.setFocused(focused);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 优先让建议组件处理点击（点击建议条目）
        for (SuggestionWidget widget : suggestionWidgets) {
            if (widget.isVisible() && widget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        clearAllFocus();

        // 隐藏那些鼠标不在其关联输入框上的建议组件
        for (SuggestionWidget widget : suggestionWidgets) {
            EditBox box = widget.getAttachedBox();
            if (box != null && !isMouseOverBox(box, mouseX, mouseY)) {
                widget.hide();
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (SuggestionWidget sw : suggestionWidgets) {
            if (sw.isVisible() && sw.mouseScrolled(mouseX, mouseY, scrollY)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 优先让建议组件处理 Tab / 方向键 / Enter
        if (focusedWidget instanceof EditBox editBox) {
            // 查找与该输入框关联的建议组件
            for (SuggestionWidget widget : suggestionWidgets) {
                if (widget.getAttachedBox() == editBox && widget.isVisible()) {
                    if (widget.keyPressed(keyCode)) {
                        return true;
                    }
                    break;
                }
            }
        } else if (focusedWidget instanceof SurroundingBlocksWidget sbw) {
            // 周围方块组件是复合组件，需要单独处理
            EditBox active = sbw.getInternalFocused();
            if (active != null) {
                for (SuggestionWidget sw : suggestionWidgets) {
                    if (sw.getAttachedBox() == active && sw.isVisible()) {
                        if (sw.keyPressed(keyCode)) return true;
                        break;
                    }
                }
            }
        }

        if (focusedWidget != null) {
            if (focusedWidget instanceof SurroundingBlocksWidget sbw) {
                // 委托给内部活跃 EditBox
                EditBox active = sbw.getInternalFocused();
                if (active != null && active.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            } else if (focusedWidget.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (focusedWidget != null) {
            if (focusedWidget instanceof SurroundingBlocksWidget sbw) {
                EditBox active = sbw.getInternalFocused();
                if (active != null && active.charTyped(codePoint, modifiers)) {
                    return true;
                }
            } else if (focusedWidget.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    // ========== 悬浮建议下拉框方法 ========== //
    // 渲染建议下拉框
    protected void renderSuggestion(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, SuggestionWidget suggestionWidget) {
        if (suggestionWidget.isVisible()) {
            suggestionWidget.render(guiGraphics, mouseX, mouseY);
        }
    }

    // 创建并注册一个建议组件
    protected SuggestionWidget registerSuggestion(EditBox editBox, Registry<?> registry) {
        SuggestionWidget widget = new SuggestionWidget(font, editBox, registry);
        suggestionWidgets.add(widget);
        return widget;
    }

    // 添加建议组件的文本监听
    protected void addSuggestionListener(EditBox editBox, SuggestionWidget suggestionWidget) {
        editBox.setResponder(text -> {
            if (suggestionWidget != null) {
                suggestionWidget.updateSuggestions();
            }
        });
    }

    // 辅助方法：判断鼠标是否在某个输入框区域内
    private boolean isMouseOverBox(EditBox box, double mouseX, double mouseY) {
        return mouseX >= box.getX() && mouseX <= box.getX() + box.getWidth()
                && mouseY >= box.getY() && mouseY <= box.getY() + box.getHeight();
    }

    // ========== 子类方法 ========== //

    // 添加子类特有组件
    protected abstract void addCustomEntries(FormList fromList);
    // 构建配置对象
    protected abstract T createConfigFromFields();
    // 清空子类组件
    protected abstract void clearCustomFields();
    protected abstract void addCustomSuggestion();
}
