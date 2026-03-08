package com.meteorite.itemdespawntowhat.ui.screen;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.ui.BaseConfigEditHandler;
import com.meteorite.itemdespawntowhat.ui.Callback;
import com.meteorite.itemdespawntowhat.ui.ListScreenCallback;
import com.meteorite.itemdespawntowhat.ui.SuggestionProvider;
import com.meteorite.itemdespawntowhat.ui.panel.ConfigListPanel;
import com.meteorite.itemdespawntowhat.ui.panel.FormListPanel;
import com.meteorite.itemdespawntowhat.ui.widget.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseConfigEditScreen<T extends BaseConversionConfig> extends Screen
        implements Callback<T>, ListScreenCallback<T> {

    protected T draftConfig;
    protected T resizeBackup; // 用于窗口调整时的临时备份
    protected boolean listEditPerformed;

    protected static final Logger LOGGER = LogManager.getLogger();
    protected static final String LABEL_PREFIX = "gui.itemdespawntowhat.edit.";
    protected static final int BOX_WIDTH = 240;
    // 后端处理器
    protected final BaseConfigEditHandler<T> editHandler;
    // UI 组件
    protected EditBox itemIdInput;
    protected EditBox dimensionInput;
    protected CycleButton<Boolean> needOutdoorButton;
    protected SurroundingBlocksWidget surroundingWidget;
    protected CatalystItemsWidget catalystWidget;
    protected InnerFluidWidget innerFluidWidget;
    protected EditBox resultIdInput;
    protected EditBox conversionTimeInput;
    protected EditBox resultMultipleInput;
    // UI 组件列表
    protected FormListPanel formList;
    // 存储所有建议下拉框的列表
    protected final List<SuggestionWidget> suggestionWidgets = new ArrayList<>();
    // 聚焦的组件
    protected AbstractWidget focusedWidget;
    // 右上角配置列表按钮
    private Button configListButton;

    // 错误提示
    private Component errorMessage = null;
    private int errorDisplayTicks = 0;
    private static final int ERROR_DISPLAY_DURATION = 120; // 显示6秒

    public BaseConfigEditScreen(ConfigType configType) {
        super(Component.translatable("gui.itemdespawntowhat.edit.title", configType.getFileName()));
        this.editHandler = new BaseConfigEditHandler<>(configType);
    }

    @Override
    protected void init() {
        super.init();
        focusedWidget = null;

        formList = new FormListPanel(
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
                        Component.translatable(LABEL_PREFIX + "on"),
                        Component.translatable(LABEL_PREFIX + "off")
                ).withInitialValue(false)
                .create(0, 0, BOX_WIDTH, 18, Component.translatable(LABEL_PREFIX + "need_outdoor"));
        surroundingWidget = new SurroundingBlocksWidget(font, 0, 0);
        catalystWidget = new CatalystItemsWidget(font, 0, 0);
        innerFluidWidget = new InnerFluidWidget(font, 0, 0);
        resultIdInput = textBox();
        conversionTimeInput = numericBox();
        resultMultipleInput = numericBox();

        // 将组件加入列表
        formList.add(Component.translatable(LABEL_PREFIX + "item_id"), itemIdInput);
        formList.add(Component.translatable(LABEL_PREFIX + "dimension"), dimensionInput);
        formList.add(Component.translatable(LABEL_PREFIX + "need_outdoor"), needOutdoorButton);
        formList.add(Component.translatable(LABEL_PREFIX + "surrounding_blocks"), surroundingWidget);
        formList.add(Component.translatable(LABEL_PREFIX + "catalyst_items"), catalystWidget);
        formList.add(Component.translatable(LABEL_PREFIX + "inner_fluid"), innerFluidWidget);
        formList.add(Component.translatable(LABEL_PREFIX + "result_id"), resultIdInput);
        formList.add(Component.translatable(LABEL_PREFIX + "conversion_time"), conversionTimeInput);
        formList.add(Component.translatable(LABEL_PREFIX + "result_multiple"), resultMultipleInput);
        // 将子类字段加入列表
        addCustomEntries(formList);

        // 注册下拉建议框组件，在所有列表组件添加完成之后再添加，并添加文本监听
        registerSuggestion(itemIdInput, SuggestionProvider.ofRegistry(BuiltInRegistries.ITEM));
        for (EditBox box : surroundingWidget.getBoxes().values()) {
            registerSuggestion(box, SuggestionProvider.combine(
                    SuggestionProvider.ofRegistry(BuiltInRegistries.BLOCK),
                    SuggestionProvider.ofTags(Registries.BLOCK)));
        }
        registerSuggestion(dimensionInput, SuggestionProvider.ofDimensions());
        registerSuggestion(catalystWidget.getItemBox(), SuggestionProvider.ofRegistry(BuiltInRegistries.ITEM));
        registerSuggestion(innerFluidWidget.getFluidBox(), SuggestionProvider.ofRegistry(BuiltInRegistries.FLUID));

        // 添加子类下拉建议框监听
        addCustomSuggestion();

        initButtons();
        clearFields();

        if (resizeBackup != null) {
            onRefillFields(resizeBackup);
            resizeBackup = null;
        }
    }

    private void initButtons() {
        int centerX = width / 2;
        int y = height - 28;

        addRenderableWidget(Button.builder(
                Component.translatable("gui.itemdespawntowhat.save_to_cache"),
                b -> editHandler.saveCurrentToCache(this)
        ).bounds(centerX - 160, y, 100, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.itemdespawntowhat.apply_to_file"),
                b -> editHandler.applyToFile(this)
        ).bounds(centerX - 50, y, 100, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.cancel"),
                b -> onClose()
        ).bounds(centerX + 60, y, 100, 20).build());

        // 右上角配置列表按钮
        configListButton = Button.builder(
                buildConfigListButtonLabel(),
                b -> openConfigListScreen()
        ).bounds(width - 110, 6, 100, 16).build();
        addRenderableWidget(configListButton);
    }

    // ========== Callback 接口实现 =========== //
    @Override
    public void onClearFields() {
        clearFields();
    }

    @Override
    public T buildConfigFromFields() {
        return createConfigFromFields();
    }

    @Override
    public void onRefillFields(T config) {
        refillCommonFields(config);
        refillCustomFields(config);
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
        if (minecraft != null) {
            minecraft.setScreen(new ConfigSelectionScreen());
        }
    }

    @Override
    public void onSaveError() {
        errorMessage = Component.translatable("gui.itemdespawntowhat.edit.save_error")
                .withStyle(ChatFormatting.RED);
        errorDisplayTicks = ERROR_DISPLAY_DURATION;
    }

    // ========== ListScreenCallback 实现 ========== //
    @Override
    public void onEditRequested(ConfigListPanel.EntrySource source, int indexInSource) {
        listEditPerformed = true;
        editHandler.startEditConfig(source, indexInSource, this);
    }

    @Override
    public void onDeleteRequested(ConfigListPanel.EntrySource source, int indexInSource) {
        editHandler.deleteConfig(source, indexInSource, this);
    }

    @Override
    public void onListScreenClosed() {
        if (!listEditPerformed && draftConfig != null ) {// && draftConfig.shouldProcess()
            onRefillFields(draftConfig);
        }
        // 重置标志和草稿，避免下次误用
        listEditPerformed = false;
        draftConfig = null;
        refreshConfigListButton();
    }

    // ============================
    // 按钮文本构建方法
    private Component buildConfigListButtonLabel() {
        int total = editHandler.getOriginalConfigs().size()
                + editHandler.getPendingConfigs().size();
        int pending = editHandler.getPendingConfigs().size();

        if (pending > 0) {
            // 星号标注待定条目
            return Component.translatable("gui.itemdespawntowhat.edit.config_stat_2",
                    Component.literal(String.valueOf(total)).withStyle(ChatFormatting.GREEN),
                    Component.literal(String.valueOf(total - pending)).withStyle(ChatFormatting.GREEN),
                    Component.literal(String.valueOf(pending)).withStyle(ChatFormatting.YELLOW));
        } else {
            return Component.translatable("gui.itemdespawntowhat.edit.config_stat_1",total);
        }
    }

    // 刷新右上角按钮文字，每次列表变化后调用
    private void refreshConfigListButton() {
        if (configListButton != null) {
            configListButton.setMessage(buildConfigListButtonLabel());
        }
    }

    // 打开配置列表screen
    private void openConfigListScreen() {
        draftConfig = buildConfigFromFields();
        listEditPerformed = false;
        if (minecraft != null) {
            minecraft.setScreen(new ConfigListScreen<>(this, editHandler, this));
        }
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

    // ========== 字段处理 ========== //

    // 填充通用字段到给定的配置对象
    protected void populateCommonFields(T config) {
        config.setItemId(parseResourceLocation(itemIdInput.getValue()));
        config.setDimension(dimensionInput.getValue());
        config.setNeedOutdoor(needOutdoorButton.getValue());
        config.setSurroundingBlocks(surroundingWidget.getValue());
        config.setCatalystItems(catalystWidget.getValue());
        config.setInnerFluid(innerFluidWidget.getValue());
        config.setResultId(parseResourceLocation(resultIdInput.getValue()));
        config.setConversionTime(parseInt(conversionTimeInput.getValue(),300));
        config.setResultMultiple(parseInt(resultMultipleInput.getValue(), 1));
    }

    protected void clearFields() {
        itemIdInput.setValue("");
        dimensionInput.setValue("");
        needOutdoorButton.setValue(false);
        resultIdInput.setValue("");
        conversionTimeInput.setValue("");
        resultMultipleInput.setValue("");
        surroundingWidget.clear();
        catalystWidget.clear();
        innerFluidWidget.clear();

        clearCustomFields();
        clearAllSuggestions();
    }

    protected void clearAllSuggestions() {
        suggestionWidgets.forEach((SuggestionWidget::hide));
    }

    protected void refillCommonFields(T config) {
        itemIdInput.setValue(rlToString(config.getItemId()));
        dimensionInput.setValue(config.getDimension());
        needOutdoorButton.setValue(config.isNeedOutdoor());
        resultIdInput.setValue(rlToString(config.getResultId()));
        conversionTimeInput.setValue(String.valueOf(config.getConversionTime()));
        resultMultipleInput.setValue(String.valueOf(config.getResultMultiple()));
        surroundingWidget.setValue(config.getSurroundingBlocks());
        catalystWidget.setValue(config.getCatalystItems());
        innerFluidWidget.setValue(config.getInnerFluid());
    }

    // 安全解析字符串到数字
    protected int parseInt(String boxInput, int def) {
        try {
            return Integer.parseInt(boxInput);
        } catch (Exception e) {
            return def;
        }
    }

    protected String rlToString(ResourceLocation rl) {
        if (rl == null) {
            return "";
        }
        return rl.toString();
    }

    // 解析 ResourceLocation，字符串为空时返回 null
    public static ResourceLocation parseResourceLocation(String value) {
        return value == null || value.isEmpty() ? null : ResourceLocation.tryParse(value);
    }

    // ========== 渲染 ========== //
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);

        // 显示当前模式
        String modeText = editHandler.getModeLabelText();
        guiGraphics.drawString(font, modeText, 10, 12, 0x808080);

        // 渲染错误提示
        if (errorMessage != null && errorDisplayTicks > 0) {
            guiGraphics.drawCenteredString(font, errorMessage, width / 2, 24, 0xFFFFFF);
            errorDisplayTicks--;
            if (errorDisplayTicks <= 0) {
                errorMessage = null;
            }
        }
        // 因为文本的按钮文本开启了深度测试，所以需要把z值拉高
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);
        // 建议下拉框要最后渲染
        for (SuggestionWidget widget : suggestionWidgets) {
            if (widget.isVisible()) {
                widget.render(guiGraphics, mouseX, mouseY);
            }
        }
        guiGraphics.pose().popPose();
    }

    // ========== 统一焦点管理 ========== //
    public void setFocusedWidget(@Nullable AbstractWidget widget) {
        if (focusedWidget == widget) return;

        // 旧焦点失效
        if (focusedWidget != null) {
            focusedWidget.setFocused(false);
            if (focusedWidget instanceof ICompositeWidget comp) {
                comp.clearInternalFocus();
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
        // 优先让建议下拉框处理点击
        for (SuggestionWidget widget : suggestionWidgets) {
            if (widget.isVisible() && widget.mouseClicked(mouseX, mouseY)) {
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
        // 优先让建议组件处理建议下拉框中的按键
        if (focusedWidget instanceof EditBox editBox ) {
            // 查找与该输入框关联的建议组件
            if (suggestionKeyPressed(keyCode, editBox)){
                return true;
            }
        } else if (focusedWidget instanceof ICompositeWidget comp) {
            // 复合组件需要单独处理
            EditBox active = comp.getInternalFocused();
            if (suggestionKeyPressed(keyCode, active)) {
                return true;
            }
        }

        if (focusedWidget != null && focusedWidget.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean suggestionKeyPressed(int keyCode, EditBox editBox) {
        for (SuggestionWidget sw : suggestionWidgets) {
            if (sw.getAttachedBox() == editBox && sw.isVisible()) {
                if (sw.keyPressed(keyCode)) {
                    return true;
                }
                break;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (focusedWidget != null && focusedWidget.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    // 备份当前表单内容, 用来调整大小时恢复
    @Override
    public void resize(@NotNull Minecraft minecraft, int width, int height) {
        this.resizeBackup = buildConfigFromFields();
        super.resize(minecraft, width, height);
    }

    // ========== 建议下拉框方法 ========== //

    // 创建并注册一个建议组件，并添加监听
    protected void registerSuggestion(EditBox editBox, SuggestionProvider sProvider) {
        SuggestionWidget widget = new SuggestionWidget(font, editBox, sProvider);
        suggestionWidgets.add(widget);
        editBox.setResponder(text -> widget.updateSuggestions());
    }

    // 添加建议组件的文本监听
    @Deprecated
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
    protected abstract void addCustomEntries(FormListPanel fromList);
    // 构建配置对象
    protected abstract T createConfigFromFields();
    protected abstract void populateCustomFields(T config);
    protected abstract void clearCustomFields();
    protected abstract void refillCustomFields(T config);
    // 添加子类下拉框组件
    protected abstract void addCustomSuggestion();
}
