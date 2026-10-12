package com.meteorite.itemdespawntowhat.client.ui.shared;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * 目录条目图标分发：物品、方块、实体、流体与标签引用共用一个入口，其余目录类型回落文字槽。 */
public final class CatalogPreviewIcons {

    private CatalogPreviewIcons() { }

    // 物品、方块、实体和流体图标；其他目录类型回落文字槽。
    public static @Nullable UiIcon iconFor(@Nullable RuleCatalogType type, @Nullable String id) {
        if (type == null || id == null || id.isBlank()) {
            return null;
        }
        if (id.startsWith("#") && (type == RuleCatalogType.ITEM || type == RuleCatalogType.BLOCK
                || type == RuleCatalogType.ENTITY || type == RuleCatalogType.FLUID))
            return TagPreviewIcons.resolve(type, id).icon();
        String normalized = id.startsWith("#") ? id.substring(1) : id;
        ResourceLocation key = ResourceLocation.tryParse(
                normalized.indexOf(':') >= 0 ? normalized : "minecraft:" + normalized);
        if (key == null) {
            return null;
        }
        if (type == RuleCatalogType.ENTITY && !id.startsWith("#")) return EntityPreviewIcons.icon(key.toString(), 0);
        if (type == RuleCatalogType.BLOCK && !id.startsWith("#")) return BlockPreviewIcons.icon(key.toString());
        if (type == RuleCatalogType.FLUID && !id.startsWith("#")) return FluidPreviewIcons.icon(key.toString());
        ItemStack stack = switch (type) {
            case ITEM -> stackOf(BuiltInRegistries.ITEM.getOptional(key).orElse(null));
            case BLOCK -> BuiltInRegistries.BLOCK.getOptional(key).map(block -> stackOf(block.asItem()))
                    .orElse(ItemStack.EMPTY);
            default -> ItemStack.EMPTY;
        };
        return stack.isEmpty() ? null : new UiIcon.Item(stack);
    }

    private static ItemStack stackOf(@Nullable Item item) {
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }
}
