package com.meteorite.itemdespawntowhat.client.ui.panel.configlist;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.util.TagResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class TagPreviewResolver {

    private static final long TAG_ICON_SWITCH_MS = 1500L;

    private TagPreviewResolver() {
    }

    public static List<Item> resolveTagItems(BaseConversionConfig config) {
        List<Item> cachedTagItems = config.getTagItems();
        if (!cachedTagItems.isEmpty()) {
            return cachedTagItems;
        }

        Minecraft mc = Minecraft.getInstance();
        var registryAccess = mc.level != null
                ? mc.level.registryAccess()
                : (mc.getConnection() != null ? mc.getConnection().registryAccess() : null);
        return TagResolver.resolveTagItems(registryAccess, Registries.ITEM, BuiltInRegistries.ITEM, config.getItemId());
    }

    public static @Nullable Item pickRotatingTagItem(List<Item> tagItems) {
        if (tagItems.isEmpty()) {
            return null;
        }

        long rotationTick = System.currentTimeMillis() / TAG_ICON_SWITCH_MS;
        int index = (int) (rotationTick % tagItems.size());
        return tagItems.get(index);
    }
}
