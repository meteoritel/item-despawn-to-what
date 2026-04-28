package com.meteorite.itemdespawntowhat.client.ui.panel.configlist;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.catalogue.CatalystItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class CatalystInlineRenderer {
    private static final int ICON_SIZE = 16;
    private static final int DROPDOWN_MIN_WIDTH = 100;
    private static final int DROPDOWN_ENTRY_HEIGHT = 18;
    private static final int DROPDOWN_PADDING = 3;
    private static final long ICON_ROTATION_MS = 1500L;

    private CatalystInlineRenderer() {
        throw new UnsupportedOperationException("Utility class");
    }

    public record IconPos(int x, int y) {}

    // ========== 查询方法 ========== //

    public static boolean hasCatalyst(BaseConversionConfig config) {
        CatalystItems ci = config.getCatalystItems();
        return ci != null && ci.hasAnyCatalyst();
    }

    public static List<CatalystItems.CatalystEntry> getCatalystEntries(BaseConversionConfig config) {
        CatalystItems ci = config.getCatalystItems();
        if (ci == null) return List.of();
        return ci.getCatalystList();
    }

    public static ItemStack getCatalystIconStack(BaseConversionConfig config) {
        List<CatalystItems.CatalystEntry> entries = getCatalystEntries(config);
        if (entries.isEmpty()) return ItemStack.EMPTY;
        long rotationTick = System.currentTimeMillis() / ICON_ROTATION_MS;
        int index = (int) (rotationTick % entries.size());
        return getEntryIconStack(entries.get(index));
    }

    public static ItemStack getDropdownEntryIconStack(CatalystItems.CatalystEntry entry) {
        if (entry.isTagEntry()) {
            List<Item> tagItems = entry.getTagItems();
            if (!tagItems.isEmpty()) {
                long rotationTick = System.currentTimeMillis() / ICON_ROTATION_MS;
                int index = (int) (rotationTick % tagItems.size());
                return tagItems.get(index).getDefaultInstance();
            }
            return new ItemStack(Items.BARRIER);
        }
        return getEntryIconStack(entry);
    }

    public static ItemStack getEntryIconStack(CatalystItems.CatalystEntry entry) {
        if (entry.isTagEntry()) {
            List<Item> tagItems = entry.getTagItems();
            if (!tagItems.isEmpty()) return tagItems.getFirst().getDefaultInstance();
            return new ItemStack(Items.BARRIER);
        }
        Item item = entry.getItem();
        return item != null ? item.getDefaultInstance() : new ItemStack(Items.BARRIER);
    }

    public static Component getEntryDisplayName(CatalystItems.CatalystEntry entry) {
        ItemStack icon = getEntryIconStack(entry);
        if (!icon.isEmpty()) {
            if (icon.getItem() instanceof BlockItem blockItem) {
                return Component.translatable(blockItem.getBlock().getDescriptionId());
            }
            return Component.translatable(icon.getDescriptionId());
        }
        return Component.literal(entry.itemId());
    }

    // ========== 渲染方法 ========== //

    private static final int TEXT_COL_WIDTH = 70;

    // 渲染催化剂内联图标（+ 图标），返回图标位置
    public static IconPos renderInline(GuiGraphics guiGraphics, Minecraft mc,
                                        BaseConversionConfig config,
                                        int sourceTextX, int arrowX, int iconY, int textY) {
        if (!hasCatalyst(config)) return null;

        String plusText = "+ ";
        int plusWidth = mc.font.width(plusText);
        int catalystBlockWidth = plusWidth + ICON_SIZE;

        int gapStart = sourceTextX + TEXT_COL_WIDTH;
        int catalystIconX = gapStart + (arrowX - gapStart - catalystBlockWidth) / 2;
        int iconX = catalystIconX + plusWidth;

        guiGraphics.drawString(mc.font, plusText, catalystIconX, textY, 0xAAAAAA, false);
        ItemStack catalystIcon = getCatalystIconStack(config);
        guiGraphics.renderItem(catalystIcon, iconX, iconY);

        return new IconPos(catalystIconX, iconY);
    }

    // 渲染催化剂下拉框（调用方保证 catalystDropdownOpen == true 且 hasCatalyst）
    public static void renderDropdown(GuiGraphics guiGraphics, Minecraft mc,
                                       BaseConversionConfig config,
                                       int catalystIconX, int catalystIconY,
                                       int parentBottom, int parentY,
                                       int mouseX, int mouseY) {
        List<CatalystItems.CatalystEntry> entries = getCatalystEntries(config);
        if (entries.isEmpty()) return;

        int dropdownWidth = getDropdownWidth(mc, config);
        int dropdownHeight = entries.size() * DROPDOWN_ENTRY_HEIGHT + DROPDOWN_PADDING * 2;
        int dropdownY = catalystIconY + ICON_SIZE + 2;

        if (dropdownY + dropdownHeight > parentBottom) {
            dropdownY = Math.max(parentBottom - dropdownHeight, parentY);
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400);

        guiGraphics.fill(catalystIconX, dropdownY, catalystIconX + dropdownWidth,
                dropdownY + dropdownHeight, 0xEE_1A1A1A);
        guiGraphics.renderOutline(catalystIconX, dropdownY, dropdownWidth,
                dropdownHeight, 0xFF_888888);

        for (int i = 0; i < entries.size(); i++) {
            CatalystItems.CatalystEntry entry = entries.get(i);
            int rowY = dropdownY + DROPDOWN_PADDING + i * DROPDOWN_ENTRY_HEIGHT;
            int rowCenterY = rowY + (DROPDOWN_ENTRY_HEIGHT - mc.font.lineHeight) / 2;

            boolean hoverRow = mouseX >= catalystIconX && mouseX <= catalystIconX + dropdownWidth
                    && mouseY >= rowY && mouseY < rowY + DROPDOWN_ENTRY_HEIGHT;
            if (hoverRow) {
                guiGraphics.fill(catalystIconX + 1, rowY, catalystIconX + dropdownWidth - 1,
                        rowY + DROPDOWN_ENTRY_HEIGHT, 0xFF_333333);
            }

            String countStr = entry.count() + "x";
            guiGraphics.drawString(mc.font, countStr, catalystIconX + 4, rowCenterY, 0xFFFFFF, false);

            int countWidth = mc.font.width(countStr);
            ItemStack icon = getDropdownEntryIconStack(entry);
            int itemIconY = rowY + (DROPDOWN_ENTRY_HEIGHT - ICON_SIZE) / 2;
            guiGraphics.renderItem(icon, catalystIconX + 4 + countWidth + 4, itemIconY);

            Component name = getEntryDisplayName(entry);
            int nameX = catalystIconX + 4 + countWidth + 4 + ICON_SIZE + 4;
            guiGraphics.drawString(mc.font, name.getString(), nameX, rowCenterY, 0xCCCCCC, false);
        }

        guiGraphics.pose().popPose();
    }

    public static int getDropdownWidth(Minecraft mc, BaseConversionConfig config) {
        List<CatalystItems.CatalystEntry> entries = getCatalystEntries(config);
        int maxWidth = DROPDOWN_MIN_WIDTH;
        for (CatalystItems.CatalystEntry entry : entries) {
            String countStr = entry.count() + "x";
            int countWidth = mc.font.width(countStr);
            Component name = getEntryDisplayName(entry);
            int nameWidth = mc.font.width(name.getString());
            int rowWidth = 4 + countWidth + 4 + ICON_SIZE + 4 + nameWidth + 4;
            if (rowWidth > maxWidth) {
                maxWidth = rowWidth;
            }
        }
        return maxWidth;
    }

    // ========== Hit-test 方法 ========== //

    public static boolean isMouseOverIcon(double mouseX, double mouseY,
                                           int catalystIconX, int catalystIconY, Minecraft mc) {
        int plusWidth = mc.font.width("+ ");
        int totalWidth = plusWidth + ICON_SIZE;
        return mouseX >= catalystIconX && mouseX <= catalystIconX + totalWidth
                && mouseY >= catalystIconY && mouseY <= catalystIconY + ICON_SIZE;
    }

    public static boolean isMouseOverDropdown(double mouseX, double mouseY,
                                               int catalystIconX, int catalystIconY,
                                               BaseConversionConfig config,
                                               Minecraft mc,
                                               int parentBottom, int parentY) {
        List<CatalystItems.CatalystEntry> entries = getCatalystEntries(config);
        int dropdownWidth = getDropdownWidth(mc, config);
        int dropdownHeight = entries.size() * DROPDOWN_ENTRY_HEIGHT + DROPDOWN_PADDING * 2;
        int dropdownX = catalystIconX;
        int dropdownY = catalystIconY + ICON_SIZE + 2;
        if (dropdownY + dropdownHeight > parentBottom) {
            dropdownY = Math.max(parentBottom - dropdownHeight, parentY);
        }
        return mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth
                && mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight;
    }
}
