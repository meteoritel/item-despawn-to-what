package com.meteorite.itemdespawntowhat.ui.panel;

import com.meteorite.itemdespawntowhat.config.ConfigDirection;
import com.meteorite.itemdespawntowhat.config.catalogue.CatalystItems;
import com.meteorite.itemdespawntowhat.config.catalogue.InnerFluid;
import com.meteorite.itemdespawntowhat.config.catalogue.SurroundingBlocks;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ConfigListPanel<T extends BaseConversionConfig> extends ObjectSelectionList<ConfigListPanel.ConfigEntry<T>> {

    // 条目来源标记
    public enum EntrySource { ORIGINAL, PENDING }

    // 当用户点击 Edit 时的回调，携带配置对象与其来源
    public interface EditCallback<T> {
        void onEdit(T config, EntrySource source, int indexInSource);
    }

    // 当用户点击 Delete 时的回调
    public interface DeleteCallback<T> {
        void onDelete(T config, EntrySource source, int indexInSource);
    }

    // ========== 布局常量 ========== //
    private static final int ENTRY_HEIGHT = 26;
    private static final int ICON_SIZE = 16;
    private static final int BUTTON_WIDTH = 46;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_GAP = 4;

    // 固定列布局
    private static final int COL_TAG_W = 3;
    private static final int COL_ICON1_X = COL_TAG_W + 4;
    private static final int COL_TEXT1_X = COL_ICON1_X + ICON_SIZE + 3;
    private static final int TEXT_COL_W = 90;  // 每列文本宽度
    private static final int COL_ARROW_X = COL_TEXT1_X + TEXT_COL_W + 4;
    private static final int ARROW_W = 12;
    private static final int COL_ICON2_X = COL_ARROW_X + ARROW_W + 4;
    private static final int COL_TEXT2_X = COL_ICON2_X + ICON_SIZE + 3;

    // 文本超出自动滚动
    // 文本滚动速度：像素/ms
    private static final float SCROLL_SPEED_PX_MS = 0.025f;
    // 滚动前静止时长（ms）
    private static final long SCROLL_PAUSE_MS = 1500L;
    // 文字与裁剪区右边缘的最小间距
    private static final int SCROLL_PADDING = 6;

    // 确认弹窗常量
    private static final int DIALOG_W = 160;
    private static final int DIALOG_H = 54;
    private static final int DIALOG_BTN_W = 60;
    private static final int DIALOG_BTN_H = 16;

    // 待确认删除项，null 表示弹窗关闭
    private @Nullable PendingDelete<T> pendingDelete = null;

    // 弹窗的确认与取消按钮
    private final Button confirmButton;
    private final Button cancelButton;

    // ========== 回调接口 ========== //
    private final EditCallback<T> editCallback;
    private final DeleteCallback<T> deleteCallback;

    public ConfigListPanel(
            Minecraft mc,
            int width, int height,
            int bottom, int top,
            List<T> originalConfigs,
            List<T> pendingConfigs,
            EditCallback<T> editCallback,
            DeleteCallback<T> deleteCallback
    ) {
        super(mc, width, height - bottom, top, ENTRY_HEIGHT);
        this.editCallback = editCallback;
        this.deleteCallback = deleteCallback;

        this.confirmButton = Button.builder(
                Component.translatable("gui.itemdespawntowhat.edit.list.delete"),
                b -> commitDelete()
        ).size(DIALOG_BTN_W, DIALOG_BTN_H).build();
        this.cancelButton = Button.builder(
                Component.translatable("gui.cancel"),
                b -> pendingDelete = null
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
        return 340;
    }

    // 触发 edit 回调
    void fireEdit(T config, EntrySource source, int idx) {
        if (editCallback != null) editCallback.onEdit(config, source, idx);
    }

    // 触发 delete 回调
    void requestDelete(T config, EntrySource source, int idx) {
        pendingDelete = new PendingDelete<>(config, source, idx);
    }

    // 真正执行delete
    private void commitDelete() {
        if (pendingDelete != null && deleteCallback != null) {
            deleteCallback.onDelete(pendingDelete.config, pendingDelete.source, pendingDelete.index);
        }
        pendingDelete = null;
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 先渲染列表本体
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);

        if (pendingDelete == null) return;

        Minecraft mc = Minecraft.getInstance();

        // 半透明遮罩，遮住整个列表区域防止穿透点击
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);
        guiGraphics.fill(getX(), getY(), getX() + this.width, getY() + this.height, 0xAA_000000);

        // 弹窗居中于列表区域
        int dlgX = getX() + (this.width  - DIALOG_W) / 2;
        int dlgY = getY() + (this.height - DIALOG_H) / 2;

        // 弹窗背景 + 边框
        guiGraphics.fill(dlgX, dlgY, dlgX + DIALOG_W, dlgY + DIALOG_H, 0xFF_2B2B2B);
        guiGraphics.renderOutline(dlgX, dlgY, DIALOG_W, DIALOG_H, 0xFF_AAAAAA);

        // 警告标题（红色）
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
        if (pendingDelete != null) {
            confirmButton.mouseClicked(mouseX, mouseY, button);
            cancelButton .mouseClicked(mouseX, mouseY, button);
            return true; // 吞掉事件，不传递给列表
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override
    public void setSelected(@Nullable ConfigEntry<T> entry) {
        // 这个列表不需要焦点，直接全部取消
    }

    // ========== 内部记录：待确认删除项 ========== //
    private record PendingDelete<T>(T config, EntrySource source, int index) {}
    // ========== 列表条目 ========== //
    public static class ConfigEntry<T extends BaseConversionConfig>
            extends ObjectSelectionList.Entry<ConfigEntry<T>> {

        private final T config;
        private final EntrySource source;

        private final Button editButton;
        private final Button deleteButton;

        // 缓存图标 ItemStack
        private final ItemStack itemIcon;
        private final ItemStack resultIcon;

        // 条目创建的时间，用于计算滚动偏移
        private final long createdAt = System.currentTimeMillis();

        ConfigEntry(ConfigListPanel<T> parent, T config, EntrySource source, int indexInSource) {
            this.config = config;
            this.source = source;

            // 预解析图标
            this.itemIcon = config.getStartItemIcon();
            this.resultIcon = config.getResultIcon();

            this.editButton = Button.builder(
                    Component.translatable("gui.itemdespawntowhat.edit.list.edit"),
                    b -> parent.fireEdit(config, source, indexInSource)
            ).size(BUTTON_WIDTH, BUTTON_HEIGHT).build();

            this.deleteButton = Button.builder(
                    Component.translatable("gui.itemdespawntowhat.edit.list.delete"),
                    b -> parent.requestDelete(config, source, indexInSource)
            ).size(BUTTON_WIDTH, BUTTON_HEIGHT).build();
        }

        @Override
        public void render(@NotNull GuiGraphics guiGraphics,
                           int index, int top, int left,
                           int width, int height,
                           int mouseX, int mouseY,
                           boolean hovered, float partialTick) {
            Minecraft mc = Minecraft.getInstance();

            // 悬停背景
            if (hovered) {
                guiGraphics.fill(left, top, left + width, top + height, 0x22_FFFFFF);
            }

            // 来源标签（左侧色块）
            int tagColor = (source == EntrySource.PENDING) ? 0xFF_FFA500 : 0xFF_44AA44;
            guiGraphics.fill(left, top + 1, left + COL_TAG_W, top + height - 1, tagColor);

            // 图标 + 文字区域布局
            int iconY = top + (height - ICON_SIZE) / 2;
            int textY = top + (height - mc.font.lineHeight) / 2;

            // 第一列itemId 图标
            guiGraphics.renderItem(itemIcon, left + COL_ICON1_X, iconY);

            // 第一列itemId 文字，超出范围自动滚动
            String itemStr = config.getStartItem().getDescriptionId();
            int rightReserve = BUTTON_WIDTH * 2 + BUTTON_GAP + 8;
            int col2Right = left + width - rightReserve;
            int col2TextMaxW = col2Right - (left + COL_TEXT2_X);
            drawScrollableText(guiGraphics, mc, Component.translatable(itemStr), left + COL_TEXT1_X, textY, TEXT_COL_W, 0xFFFFFF);

            // 箭头
            guiGraphics.drawString(mc.font, "->", left + COL_ARROW_X, textY, 0x888888, false);

            // 第二列 resultId 图标
            guiGraphics.renderItem(resultIcon, left + COL_ICON2_X, iconY);
            // 第二列 resultId 文字
            String resultStr = config.getResultDescriptionId();
            int textColor = (source == EntrySource.PENDING) ? 0xFFFF88 : 0xFFFFFF;
            int safeCol2W = Math.max(10, col2TextMaxW);
            drawScrollableText(guiGraphics, mc, Component.translatable(resultStr), left + COL_TEXT2_X, textY, safeCol2W, textColor);

            // 右侧按钮
            int btnAreaRight = left + width - 4;
            int btnY = top + (height - BUTTON_HEIGHT) / 2;

            deleteButton.setPosition(btnAreaRight - BUTTON_WIDTH, btnY);
            editButton.setPosition(btnAreaRight - BUTTON_WIDTH * 2 - BUTTON_GAP, btnY);

            editButton.render(guiGraphics, mouseX, mouseY, partialTick);
            deleteButton.render(guiGraphics, mouseX, mouseY, partialTick);

            if (hovered && mc.screen instanceof Screen screen) {
                screen.setTooltipForNextRenderPass(buildTooltip(config));
            }
        }

        // ========== Tooltip 构建 ========== //
        private Component buildTooltip(T config) {
            MutableComponent tooltip = Component.translatable(
                    "gui.itemdespawntowhat.tooltip.conversion_time", config.getConversionTime());

            // 维度
            String dim = config.getDimension();
            if (dim != null && !dim.isEmpty()) {
                tooltip = tooltip.append(Component.literal("\n"))
                        .append(Component.translatable("gui.itemdespawntowhat.tooltip.dimension", dim));
            }

            // 需要露天
            if (config.isNeedOutdoor()) {
                tooltip = tooltip.append(Component.literal("\n"))
                        .append(Component.translatable("gui.itemdespawntowhat.tooltip.need_outdoor"));
            }

            // 六面方块
            SurroundingBlocks sb = config.getSurroundingBlocks();
            if (sb != null && sb.hasAnySurroundBlock()) {
                tooltip = tooltip.append(Component.literal("\n"))
                        .append(Component.translatable("gui.itemdespawntowhat.tooltip.surrounding_blocks_header"));
                for (ConfigDirection dir : ConfigDirection.values()) {
                    String val = sb.get(dir);
                    if (val != null && !val.isEmpty()) {
                        tooltip = tooltip.append(Component.literal("\n"))
                                .append(Component.translatable("gui.itemdespawntowhat.tooltip.surrounding_block",
                                        dir.name().toLowerCase(), val));
                    }
                }
            }

            // 辅助物品
            CatalystItems ci = config.getCatalystItems();
            if (ci != null && ci.hasAnyCatalyst()) {
                tooltip = tooltip.append(Component.literal("\n"))
                        .append(Component.translatable("gui.itemdespawntowhat.tooltip.catalyst_header"));
                for (CatalystItems.CatalystEntry entry : ci.getCatalystList()) {
                    tooltip = tooltip.append(Component.literal("\n"))
                            .append(Component.translatable("gui.itemdespawntowhat.tooltip.catalyst",
                                    entry.itemId(), entry.count()));
                }
            }

            // 浸泡流体
            InnerFluid fluid = config.getInnerFluid();
            if (fluid != null && fluid.hasInnerFluid()) {
                tooltip = tooltip.append(Component.literal("\n"))
                        .append(Component.translatable("gui.itemdespawntowhat.tooltip.inner_fluid",
                                fluid.getFluidId().toString()));
                if (fluid.isRequireSource()) {
                    tooltip = tooltip.append(Component.literal("\n"))
                            .append(Component.translatable("gui.itemdespawntowhat.tooltip.inner_fluid_source"));
                }
                if (fluid.isConsumeFluid()) {
                    tooltip = tooltip.append(Component.literal("\n"))
                            .append(Component.translatable("gui.itemdespawntowhat.tooltip.inner_fluid_consume"));
                }
            }

            return tooltip;
        }

        // ========== 排列辅助方法 ========== //

        // 计算当前的文本x偏移量
        private int calcScrollOffset(int textWidth, int maxWidth) {
            if (textWidth <= maxWidth) return 0;

            long elapsed = System.currentTimeMillis() - createdAt;
            // 超出宽度
            int overflow = textWidth - maxWidth + SCROLL_PADDING;
            // 一次完整来回时长 = pause + scroll_to_end + pause + scroll_back
            long scrollDuration = (long) (overflow / SCROLL_SPEED_PX_MS);
            long cycleDuration = SCROLL_PAUSE_MS * 2 + scrollDuration * 2;
            long t = elapsed % cycleDuration;

            if (t < SCROLL_PAUSE_MS) {
                // 初始静止
                return 0;
            } else if (t < SCROLL_PAUSE_MS + scrollDuration) {
                // 向左滚动
                return -(int) ((t - SCROLL_PAUSE_MS) * SCROLL_SPEED_PX_MS);
            } else if (t < SCROLL_PAUSE_MS * 2 + scrollDuration) {
                // 末尾静止
                return -overflow;
            } else {
                // 向右滚回
                long phase = t - SCROLL_PAUSE_MS * 2 - scrollDuration;
                return -(overflow - (int) (phase * SCROLL_SPEED_PX_MS));
            }
        }

        // 在固定宽度内绘制可滚动文本
        private void drawScrollableText(GuiGraphics guiGraphics, Minecraft mc,
                                        Component text, int x, int y,
                                        int maxWidth, int color) {
            int textWidth = mc.font.width(text);
            int offset = calcScrollOffset(textWidth, maxWidth);

            // scissor 裁剪区（屏幕坐标，需要乘以 guiScale）
            guiGraphics.enableScissor(x, y - 1, x + maxWidth, y + mc.font.lineHeight + 1);
            guiGraphics.drawString(mc.font, text, x + offset, y, color, false);
            guiGraphics.disableScissor();
        }

        @Override
        public @NotNull Component getNarration() {
            return Component.empty();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (editButton.mouseClicked(mouseX, mouseY, button))   {
                return true;
            }
            return deleteButton.mouseClicked(mouseX, mouseY, button);
        }
    }
}
