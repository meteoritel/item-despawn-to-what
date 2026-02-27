package com.meteorite.itemdespawntowhat.ui.widget;

import com.meteorite.itemdespawntowhat.config.BaseConversionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class ConfigListPanel<T extends BaseConversionConfig> extends ObjectSelectionList<ConfigListPanel.ConfigEntry<T>> {

    //** 条目来源标记 */
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
    private static final int BUTTON_WIDTH = 36;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_GAP = 2;

    private final EditCallback<T> editCallback;
    private final DeleteCallback<T> deleteCallback;

    public ConfigListPanel(
            Minecraft mc,
            int width, int height,
            int top, int bottom,
            List<T> originalConfigs,
            List<T> pendingConfigs,
            EditCallback<T> editCallback,
            DeleteCallback<T> deleteCallback
    ) {
        super(mc, width, height - top, top, ENTRY_HEIGHT);
        this.editCallback = editCallback;
        this.deleteCallback = deleteCallback;
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

    // 触发 edit 回调
    void fireEdit(T config, EntrySource source, int idx) {
        if (editCallback != null) editCallback.onEdit(config, source, idx);
    }

    // 触发 delete 回调
    void fireDelete(T config, EntrySource source, int idx) {
        if (deleteCallback != null) deleteCallback.onDelete(config, source, idx);
    }

    // ========== 列表条目 ========== //
    public static class ConfigEntry<T extends BaseConversionConfig>
            extends ObjectSelectionList.Entry<ConfigEntry<T>> {
        private final ConfigListPanel<T> parent;
        private final T config;
        private final EntrySource source;
        private final int indexInSource;

        private final Button editButton;
        private final Button deleteButton;

        //** 缓存的图标 ItemStack（避免每帧重新查找注册表）*/
        private final ItemStack itemIcon;
        private final ItemStack resultIcon;

        ConfigEntry(ConfigListPanel<T> parent, T config, EntrySource source, int indexInSource) {
            this.parent = parent;
            this.config = config;
            this.source = source;
            this.indexInSource = indexInSource;

            // 预解析图标
            this.itemIcon = resolveItemStack(config.getItemId());
            this.resultIcon = resolveItemStack(config.getResultId());

            this.editButton = Button.builder(
                    Component.translatable("gui.itemdespawntowhat.edit.list.edit"),
                    b -> parent.fireEdit(config, source, indexInSource)
            ).size(BUTTON_WIDTH, BUTTON_HEIGHT).build();

            this.deleteButton = Button.builder(
                    Component.translatable("gui.itemdespawntowhat.edit.list.delete"),
                    b -> parent.fireDelete(config, source, indexInSource)
            ).size(BUTTON_WIDTH, BUTTON_HEIGHT).build();
        }

        private static ItemStack resolveItemStack(@Nullable ResourceLocation rl) {
            if (rl == null) return new ItemStack(Items.BARRIER);
            Optional<Item> found = BuiltInRegistries.ITEM.getOptional(rl);
            return found.map(ItemStack::new).orElseGet(() -> new ItemStack(Items.BARRIER));
        }

        @Override
        public void render(@NotNull GuiGraphics guiGraphics,
                           int index, int top, int left,
                           int width, int height,
                           int mouseX, int mouseY,
                           boolean hovered, float partialTick) {
            Minecraft mc = Minecraft.getInstance();

            // ── 悬停背景 ── //
            if (hovered) {
                guiGraphics.fill(left, top, left + width, top + height, 0x22_FFFFFF);
            }

            // ── 来源标签（左侧色块） ──
            int tagColor = (source == EntrySource.PENDING) ? 0xFF_FFA500 : 0xFF_44AA44;
            guiGraphics.fill(left, top + 1, left + 3, top + height - 1, tagColor);

            // ── 图标 + 文字区域布局 ── //
            int cursorX = left + 6;
            int iconY = top + (height - ICON_SIZE) / 2;

            // itemId 图标
            guiGraphics.renderItem(itemIcon, cursorX, iconY);
            cursorX += ICON_SIZE + 2;

            // itemId 文字（最多 20 字符）
            String itemStr = shortenId(config.getItemId());
            guiGraphics.drawString(mc.font, itemStr, cursorX, top + (height - 8) / 2, 0xFFFFFF, false);
            cursorX += mc.font.width(itemStr) + 4;

            // "→" 分隔符
            guiGraphics.drawString(mc.font, "→", cursorX, top + (height - 8) / 2, 0xAAAAAA, false);
            cursorX += mc.font.width("→") + 4;

            // resultId 图标
            guiGraphics.renderItem(resultIcon, cursorX, iconY);
            cursorX += ICON_SIZE + 2;

            // resultId 文字
            String resultStr = shortenId(config.getResultId());
            int textColor = (source == EntrySource.PENDING) ? 0xFFFF88 : 0xFFFFFF;
            guiGraphics.drawString(mc.font, resultStr, cursorX, top + (height - 8) / 2, textColor, false);

            // pending 星号前缀
            if (source == EntrySource.PENDING) {
                guiGraphics.drawString(mc.font, "★",
                        left + width - BUTTON_WIDTH * 2 - BUTTON_GAP - 12,
                        top + (height - 8) / 2,
                        0xFF_FFA500, false);
            }

            // ── 右侧按钮 ── //
            int btnAreaRight = left + width - 4;
            int btnY = top + (height - BUTTON_HEIGHT) / 2;

            deleteButton.setPosition(btnAreaRight - BUTTON_WIDTH, btnY);
            editButton.setPosition(btnAreaRight - BUTTON_WIDTH * 2 - BUTTON_GAP, btnY);

            editButton.render(guiGraphics, mouseX, mouseY, partialTick);
            deleteButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        //** 从 ResourceLocation 提取 path 部分，最多保留 18 字符 */
        private String shortenId(@Nullable ResourceLocation rl) {
            if (rl == null) return "—";
            String path = rl.getPath();
            return path.length() > 18 ? path.substring(0, 16) + "…" : path;
        }

//        //** 构建显示在行内的简短摘要，优先显示 itemId，截断过长内容
//        private String buildLabel() {
//            String raw = config.getSummary();
//            if (raw == null || raw.isBlank()) raw = config.toString();
//            // 截断：最多 40 字符
//            if (raw.length() > 40) raw = raw.substring(0, 38) + "…";
//
//            // pending 条目加星号标注
//            String prefix = (source == EntrySource.PENDING) ? "* " : "  ";
//            return prefix + raw;
//        }

        @Override
        public @NotNull Component getNarration() {
            return Component.literal(
                    shortenId(config.getItemId()) + " → " + shortenId(config.getResultId())
            );
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
