package com.meteorite.itemdespawntowhat.client.ui.panel;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.client.ui.panel.configlist.CatalystInlineRenderer;
import com.meteorite.itemdespawntowhat.client.ui.panel.configlist.ConfigTooltipBuilder;
import com.meteorite.itemdespawntowhat.client.ui.panel.configlist.EntityIconCache;
import com.meteorite.itemdespawntowhat.client.ui.panel.configlist.ScrollableTextRenderer;
import com.meteorite.itemdespawntowhat.client.ui.panel.configlist.TagPreviewResolver;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToBlockConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToMobConfig;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public class ConfigListPanel<T extends BaseConversionConfig> extends ObjectSelectionList<ConfigListPanel.ConfigEntry<T>> {

    // 条目来源标记
    public enum EntrySource { ORIGINAL, PENDING }

    // 当用户点击 Edit 时的回调
    public interface EditCallback {
        void onEdit(EntrySource source, int indexInSource);
    }

    // 当用户点击 Delete 时的回调
    public interface DeleteCallback {
        void onDelete(EntrySource source, int indexInSource);
    }

    // ========== 实体图标缓存 ========== //

    // 实体模型图标的默认缩放，数字越大模型越大
    private static final float DEFAULT_MOB_SCALE = 13.0f;

    @Nullable
    public static LivingEntity getOrCreateEntityIcon(EntityType<?> type, Level level) {
        return EntityIconCache.getOrCreateEntityIcon(type, level);
    }

    public static void clearEntityCache() {
        EntityIconCache.clear();
    }

    // ========== 布局常量 ========== //
    private static final int ENTRY_HEIGHT = 26;
    private static final int ICON_SIZE = 16;

    // 固定列布局
    private static final int COL_TAG_W = 3;
    private static final int EDGE_PAD = 4;

    //催化剂先不加了//
    private static final int CATALYST_W = 0;

    private static final int COLUMN_GAP = 4;
    private static final int TEXT_COL_WIDTH = 70;
    private static final int QTY_COL_WIDTH = 20;
    private static final int MULTIPLY_COL_WIDTH = 6;
    private static final int ARROW_CENTER_SHIFT = 24;
    private static final int ROW_WIDTH = 340;
    private static final Component MULTIPLY_MARK = Component.literal("x");

    // 确认弹窗常量
    private static final int DIALOG_W = 160;
    private static final int DIALOG_H = 54;
    private static final int DIALOG_BTN_W = 60;
    private static final int DIALOG_BTN_H = 16;

    // 待确认动作，null 表示弹窗关闭
    private @Nullable PendingAction pendingAction = null;

    // 弹窗的确认与取消按钮
    private final Button confirmButton;
    private final Button cancelButton;

    // ========== 回调接口 ========== //
    private final EditCallback editCallback;
    private final DeleteCallback deleteCallback;

    public ConfigListPanel(
            Minecraft mc,
            int width, int height,
            int bottom, int top,
            List<T> originalConfigs,
            List<T> pendingConfigs,
            EditCallback editCallback,
            DeleteCallback deleteCallback
    ) {
        super(mc, width, height - bottom, top, ENTRY_HEIGHT);
        this.editCallback = editCallback;
        this.deleteCallback = deleteCallback;

        this.confirmButton = Button.builder(
                Component.translatable("gui.itemdespawntowhat.edit.list.apply"),
                b -> commitPendingAction()
        ).size(DIALOG_BTN_W, DIALOG_BTN_H).build();
        this.cancelButton = Button.builder(
                Component.translatable("gui.cancel"),
                b -> pendingAction = null
        ).size(DIALOG_BTN_W, DIALOG_BTN_H).build();

        rebuild(originalConfigs, pendingConfigs);
    }

    public void rebuild(List<T> originalConfigs, List<T> pendingConfigs) {
        clearEntries();
        for (int i = 0; i < originalConfigs.size(); i++) {
            addEntry(new ConfigEntry<>(this, originalConfigs.get(i), EntrySource.ORIGINAL, i));
        }
        for (int i = 0; i < pendingConfigs.size(); i++) {
            addEntry(new ConfigEntry<>(this, pendingConfigs.get(i), EntrySource.PENDING, i));
        }
    }

    @Override
    public int getRowWidth() {
        return ROW_WIDTH;
    }

    // 触发 edit 回调
    public void fireEdit(EntrySource source, int idx) {
        if (editCallback != null) editCallback.onEdit(source, idx);
    }

    // 请求对当前选中项执行 delete 确认
    public void requestDeleteSelected() {
        ConfigEntry<T> sel = getSelectedEntry();
        if (sel == null) return;
        requestConfirmation(sel.getSource(), sel.getIndexInSource(), () -> {
            if (deleteCallback != null) {
                deleteCallback.onDelete(sel.getSource(), sel.getIndexInSource());
            }
        });
    }

    // 请求对当前选中项执行 edit 确认
    public void requestEditSelected() {
        ConfigEntry<T> sel = getSelectedEntry();
        if (sel == null) return;
        requestConfirmation(sel.getSource(), sel.getIndexInSource(), () -> fireEdit(sel.getSource(), sel.getIndexInSource()));
    }

    private void requestConfirmation(EntrySource source, int idx, Runnable onConfirm) {
        pendingAction = new PendingAction(source, idx, onConfirm);
    }

    // 真正执行确认动作
    private void commitPendingAction() {
        if (pendingAction != null) {
            pendingAction.onConfirm.run();
        }
        pendingAction = null;
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 先渲染列表本体
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);

        if (pendingAction == null) return;

        Minecraft mc = Minecraft.getInstance();

        // 半透明遮罩，遮住整个列表区域防止穿透点击
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);
        guiGraphics.fill(getX(), getY(), getX() + this.width, getY() + this.height, 0xAA_000000);

        // 弹窗居中于列表区域
        int dlgX = getX() + (this.width - DIALOG_W) / 2;
        int dlgY = getY() + (this.height - DIALOG_H) / 2;

        // 弹窗背景 + 边框
        guiGraphics.fill(dlgX, dlgY, dlgX + DIALOG_W, dlgY + DIALOG_H, 0xFF_2B2B2B);
        guiGraphics.renderOutline(dlgX, dlgY, DIALOG_W, DIALOG_H, 0xFF_AAAAAA);

        // 警告标题（红色)
        int titleY = dlgY + 8;
        guiGraphics.drawCenteredString(mc.font,
                Component.translatable("gui.itemdespawntowhat.edit.list.delete.title"),
                dlgX + DIALOG_W / 2, titleY, 0xFF_FF5555);

        // 提示正文
        guiGraphics.drawCenteredString(mc.font,
                Component.translatable("gui.itemdespawntowhat.edit.list.delete.body"),
                dlgX + DIALOG_W / 2, titleY + mc.font.lineHeight + 3, 0xFF_CCCCCC);

        // 按钮行（确认在左，取消在右）
        int btnY = dlgY + DIALOG_H - DIALOG_BTN_H - 6;
        int totalBtnW = DIALOG_BTN_W * 2 + 6;
        int confirmX = dlgX + (DIALOG_W - totalBtnW) / 2;
        int cancelX = confirmX + DIALOG_BTN_W + 6;

        confirmButton.setPosition(confirmX, btnY);
        cancelButton.setPosition(cancelX, btnY);

        confirmButton.render(guiGraphics, mouseX, mouseY, partialTick);
        cancelButton.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.pose().popPose();
    }

    // 弹窗打开时拦截所有鼠标点击，仅转发给弹窗按钮，防止穿透到列表条目
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pendingAction != null) {
            confirmButton.mouseClicked(mouseX, mouseY, button);
            cancelButton.mouseClicked(mouseX, mouseY, button);
            return true; // 吞掉事件，不传递给列表
        }
        boolean result = super.mouseClicked(mouseX, mouseY, button);
        // 点击未命中任何催化剂图标/下拉框时，关闭所有下拉框
        if (!isMouseOverAnyCatalyst(mouseX, mouseY)) {
            closeAllCatalystDropdowns();
        }
        // 点击列表内但未命中任何条目时，清除选中状态
        if (isMouseOver(mouseX, mouseY) && getEntryAtPosition(mouseX, mouseY) == null) {
            setSelected(null);
        }
        return result;
    }

    // 检查鼠标是否在任何条目的催化剂图标或下拉框上
    private boolean isMouseOverAnyCatalyst(double mouseX, double mouseY) {
        for (ConfigEntry<T> entry : children()) {
            if (entry.isMouseOverCatalystIcon(mouseX, mouseY)
                    || entry.isMouseOverCatalystDropdown(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    // 关闭所有条目的催化剂下拉框
    private void closeAllCatalystDropdowns() {
        for (ConfigEntry<T> entry : children()) {
            entry.catalystDropdownOpen = false;
        }
    }

    @Override
    public void setSelected(@Nullable ConfigEntry<T> entry) {
        super.setSelected(entry);
    }

    @Nullable
    public ConfigEntry<T> getSelectedEntry() {
        return getSelected();
    }

    // ========== 内部记录：待确认动作 ========== //
    private record PendingAction(EntrySource source, int index, Runnable onConfirm) {}

    // ========== 列表条目 ========== //
    public static class ConfigEntry<T extends BaseConversionConfig>
            extends ObjectSelectionList.Entry<ConfigEntry<T>> {

        private final ConfigListPanel<T> parent;
        private final T config;
        private final EntrySource source;
        private final int indexInSource;

        private final boolean sourceIsTag;
        private final List<Item> sourceTagItems;

        // 条目创建的时间，用于计算滚动偏移
        private final long createdAt = System.currentTimeMillis();
        // 实体图标（仅 mob 类型非 null）
        @Nullable private LivingEntity entityIcon;
        private static final long ENTITY_ICON_ROTATION_PERIOD_MS = 8000L;

        // 催化剂内联显示
        private boolean catalystDropdownOpen = false;
        private int catalystIconX;
        private int catalystIconY;

        ConfigEntry(ConfigListPanel<T> parent, T config, EntrySource source, int indexInSource) {
            this.parent = parent;
            this.config = config;
            this.source = source;
            this.indexInSource = indexInSource;
            this.sourceIsTag = config.isTagMode();
            this.sourceTagItems = sourceIsTag ? TagPreviewResolver.resolveTagItems(config) : List.of();

            // 若为 mob 类型，从缓存取实体图标
            if (config instanceof ItemToMobConfig mobConfig) {
                Minecraft mc = Minecraft.getInstance();
                Level level = mc.level;
                EntityType<?> type = mobConfig.getResultEntityType();
                this.entityIcon = (level != null && type != null)
                        ? ConfigListPanel.getOrCreateEntityIcon(type, level)
                        : null;
            } else {
                this.entityIcon = null;
            }
        }

        private ItemStack getSourceIconStack() {
            if (sourceIsTag) {
                Item item = TagPreviewResolver.pickRotatingTagItem(sourceTagItems);
                if (item != null) {
                    return item.getDefaultInstance();
                }
                return new ItemStack(Items.BARRIER);
            }
            return config.getStartItemIcon();
        }

        private ItemStack getResultIconStack(ItemStack sourceIconStack) {
            if (config instanceof ItemToBlockConfig blockConfig && blockConfig.isEnableItemBlock()) {
                return sourceIconStack;
            }
            return config.getResultIcon();
        }

        private Component getDisplayNameForStack(ItemStack stack) {
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
                return Component.translatable(blockItem.getBlock().getDescriptionId());
            }
            return stack.isEmpty()
                    ? Component.empty()
                    : Component.translatable(stack.getDescriptionId());
        }

        // ========== 催化剂内联显示 ========== //

        private boolean hasCatalyst() {
            return CatalystInlineRenderer.hasCatalyst(config);
        }

        private Component getSourceText(ItemStack sourceIconStack) {
            if (sourceIsTag) {
                return getDisplayNameForStack(sourceIconStack);
            }
            return Component.translatable(config.getStartItem().getDescriptionId());
        }

        private Component getResultText(ItemStack resultIconStack) {
            if (config instanceof ItemToBlockConfig blockConfig && blockConfig.isEnableItemBlock()) {
                return getDisplayNameForStack(resultIconStack);
            }
            return Component.translatable(config.getResultDescriptionId());
        }

        public T getConfig() {
            return config;
        }

        public EntrySource getSource() {
            return source;
        }

        public int getIndexInSource() {
            return indexInSource;
        }

        @Override
        public void render(@NotNull GuiGraphics guiGraphics,
                           int index, int top, int left,
                           int width, int height,
                           int mouseX, int mouseY,
                           boolean hovered, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            renderRowBackground(guiGraphics, left, top, width, height, hovered);
            renderSourceMarker(guiGraphics, left, top, height);

            int sourceColumnLeft = left + COL_TAG_W + EDGE_PAD;
            int sourceQtyColX = sourceColumnLeft + CATALYST_W + COLUMN_GAP;
            int sourceMultiplyColX = sourceQtyColX + QTY_COL_WIDTH + COLUMN_GAP;
            int sourceIconX = sourceMultiplyColX + MULTIPLY_COL_WIDTH + COLUMN_GAP;
            int sourceTextX = sourceIconX + ICON_SIZE + COLUMN_GAP;

            int arrowCenterX = left + width / 2 + ARROW_CENTER_SHIFT;
            int arrowWidth = mc.font.width("->");
            int arrowX = arrowCenterX - arrowWidth / 2;
            int resultQtyColX = arrowX + arrowWidth + EDGE_PAD;
            int resultMultiplyColX = resultQtyColX + QTY_COL_WIDTH + COLUMN_GAP;
            int resultIconX = resultMultiplyColX + MULTIPLY_COL_WIDTH + COLUMN_GAP;
            int resultTextX = resultIconX + ICON_SIZE + COLUMN_GAP;

            int iconY = top + (height - ICON_SIZE) / 2;
            int textY = top + (height - mc.font.lineHeight) / 2;

            guiGraphics.drawCenteredString(mc.font, Component.literal("->"), arrowCenterX, textY, 0x888888);

            ItemStack sourceIcon = getSourceIconStack();
            Component sourceText = getSourceText(sourceIcon);
            renderSourceColumn(guiGraphics, mc, sourceQtyColX, sourceMultiplyColX, sourceIconX, sourceTextX,
                    iconY, textY, sourceIcon, sourceText);

            // 催化剂内联图标
            renderCatalystInline(guiGraphics, mc, sourceTextX, arrowX, iconY, textY);

            if (entityIcon == null && config instanceof ItemToMobConfig mobConfig) {
                Level level = mc.level;
                EntityType<?> type = mobConfig.getResultEntityType();
                if (level != null && type != null) {
                    entityIcon = ConfigListPanel.getOrCreateEntityIcon(type, level);
                }
            }

            ItemStack resultIcon = getResultIconStack(sourceIcon);
            Component resultText = getResultText(resultIcon);
            int resultMultiple = config.getResultMultiple();
            String resultMultipleStr = Integer.toString(resultMultiple);
            int textColor = (source == EntrySource.PENDING) ? 0xFFFF88 : 0xFFFFFF;

            renderResultIcon(guiGraphics, resultIconX, iconY, resultIcon);
            renderResultColumn(guiGraphics, mc, resultQtyColX, resultMultiplyColX, resultTextX,
                    textY, resultMultipleStr, resultText, textColor);

            // 催化剂下拉框
            renderCatalystDropdown(guiGraphics, mc, mouseX, mouseY);

            if (hovered && mc.screen instanceof Screen screen) {
                screen.setTooltipForNextRenderPass(ConfigTooltipBuilder.build(config));
            }
        }

        // 渲染催化剂内联图标（+ 图标），在源文本右端与箭头之间居中
        private void renderCatalystInline(GuiGraphics guiGraphics, Minecraft mc,
                                          int sourceTextX, int arrowX, int iconY, int textY) {
            CatalystInlineRenderer.IconPos pos = CatalystInlineRenderer.renderInline(
                    guiGraphics, mc, config, sourceTextX, arrowX, iconY, textY);
            if (pos != null) {
                catalystIconX = pos.x();
                catalystIconY = pos.y();
            }
        }

        // 渲染催化剂下拉框
        private void renderCatalystDropdown(GuiGraphics guiGraphics, Minecraft mc,
                                            int mouseX, int mouseY) {
            if (!catalystDropdownOpen || !hasCatalyst()) return;
            CatalystInlineRenderer.renderDropdown(guiGraphics, mc, config,
                    catalystIconX, catalystIconY,
                    parent.getBottom(), parent.getY(),
                    mouseX, mouseY);
        }

        private boolean isMouseOverCatalystIcon(double mouseX, double mouseY) {
            if (!hasCatalyst()) return false;
            return CatalystInlineRenderer.isMouseOverIcon(mouseX, mouseY,
                    catalystIconX, catalystIconY, Minecraft.getInstance());
        }

        private boolean isMouseOverCatalystDropdown(double mouseX, double mouseY) {
            if (!catalystDropdownOpen) return false;
            return CatalystInlineRenderer.isMouseOverDropdown(mouseX, mouseY,
                    catalystIconX, catalystIconY, config, Minecraft.getInstance(),
                    parent.getBottom(), parent.getY());
        }

        private void renderRowBackground(GuiGraphics guiGraphics, int left, int top, int width, int height, boolean hovered) {
            if (hovered) {
                guiGraphics.fill(left, top, left + width, top + height, 0x22_FFFFFF);
            }
        }

        private void renderSourceMarker(GuiGraphics guiGraphics, int left, int top, int height) {
            int tagColor = (source == EntrySource.PENDING) ? 0xFF_FFA500 : 0xFF_44AA44;
            guiGraphics.fill(left, top + 1, left + COL_TAG_W, top + height - 1, tagColor);
        }

        private void renderSourceColumn(GuiGraphics guiGraphics, Minecraft mc,
                                        int sourceQtyColX, int sourceMultiplyColX, int sourceIconX, int sourceTextX,
                                        int iconY, int textY, ItemStack sourceIcon, Component sourceText) {
            int sourceMultiple = config.getSourceMultiple();
            String sourceMultipleStr = Integer.toString(sourceMultiple);
            int sourceQtyX = sourceQtyColX + Math.max(0, QTY_COL_WIDTH - mc.font.width(sourceMultipleStr));
            int sourceMultiplyX = sourceMultiplyColX + Math.max(0, (MULTIPLY_COL_WIDTH - mc.font.width("*")) / 2);
            guiGraphics.drawString(mc.font, sourceMultipleStr, sourceQtyX, textY, 0xFFFFFF, false);
            guiGraphics.drawString(mc.font, MULTIPLY_MARK, sourceMultiplyX, textY, 0xFFFFFF, false);
            guiGraphics.renderItem(sourceIcon, sourceIconX, iconY);
            ScrollableTextRenderer.drawScrollableText(guiGraphics, mc.font, sourceText, sourceTextX, textY, TEXT_COL_WIDTH, 0xFFFFFF, createdAt);
        }

        private void renderResultIcon(GuiGraphics guiGraphics, int resultIconX, int iconY, ItemStack resultIcon) {
            if (entityIcon == null) {
                guiGraphics.renderItem(resultIcon, resultIconX, iconY);
                return;
            }

            ResourceLocation entityId = SafeParseUtil.parseResourceLocation(config.getResultId());
            float scale = (entityId != null) ? Constants.getEntityScale(entityId, DEFAULT_MOB_SCALE) : DEFAULT_MOB_SCALE;
            float cx = resultIconX + ICON_SIZE / 2.0f;
            float cy = iconY + ICON_SIZE / 2.0f;
            float bbHeight = entityIcon.getBbHeight();
            Vector3f translate = new Vector3f(0.0f, bbHeight / 2.0f, 0.0f);
            Quaternionf pose = createRotatingEntityPose();
            InventoryScreen.renderEntityInInventory(guiGraphics, cx, cy, scale, translate, pose, null, entityIcon);
        }

        private Quaternionf createRotatingEntityPose() {
            float progress = (System.currentTimeMillis() % ENTITY_ICON_ROTATION_PERIOD_MS) / (float) ENTITY_ICON_ROTATION_PERIOD_MS;
            float rotation = (float) (progress * Math.PI * 2.0);
            return new Quaternionf()
                    .rotateZ((float) Math.PI)
                    .rotateY((float) (7 * Math.PI / 8.0) - rotation);
        }

        private void renderResultColumn(GuiGraphics guiGraphics, Minecraft mc,
                                        int resultQtyColX, int resultMultiplyColX, int resultTextX,
                                        int textY, String resultMultipleStr, Component resultText, int textColor) {
            int resultQtyX = resultQtyColX + Math.max(0, QTY_COL_WIDTH - mc.font.width(resultMultipleStr));
            int resultMultiplyX = resultMultiplyColX + Math.max(0, (MULTIPLY_COL_WIDTH - mc.font.width("*")) / 2);
            guiGraphics.drawString(mc.font, resultMultipleStr, resultQtyX, textY, textColor, false);
            guiGraphics.drawString(mc.font, MULTIPLY_MARK, resultMultiplyX, textY, textColor, false);
            ScrollableTextRenderer.drawScrollableText(guiGraphics, mc.font, resultText, resultTextX, textY, TEXT_COL_WIDTH, textColor, createdAt);
        }

        @Override
        public @NotNull Component getNarration() {
            return Component.empty();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // 催化剂图标点击 → toggle 下拉框
            if (isMouseOverCatalystIcon(mouseX, mouseY)) {
                if (!catalystDropdownOpen) {
                    parent.closeAllCatalystDropdowns();
                }
                catalystDropdownOpen = !catalystDropdownOpen;
                return true;
            }
            // 催化剂下拉框内部点击 → 吞掉事件，不关闭
            if (isMouseOverCatalystDropdown(mouseX, mouseY)) {
                return true;
            }
            // 点击其他地方 → 关闭所有下拉框
            parent.closeAllCatalystDropdowns();
            parent.setSelected(this);
            return true;
        }
    }
}
