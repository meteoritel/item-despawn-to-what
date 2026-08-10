package com.meteorite.itemdespawntowhat.condition.checker;

import com.meteorite.itemdespawntowhat.util.IdValidator;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import com.meteorite.itemdespawntowhat.util.TagResolver;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

/**
 * 检查物品所在位置的生物群系注册项或标签。
 */
public final class BiomeConditionChecker implements ConditionChecker {
    private final @Nullable ResourceKey<Biome> biomeKey;
    private final @Nullable TagKey<Biome> biomeTag;

    private BiomeConditionChecker(@Nullable ResourceKey<Biome> biomeKey, @Nullable TagKey<Biome> biomeTag) {
        this.biomeKey = biomeKey;
        this.biomeTag = biomeTag;
    }

    public static @Nullable BiomeConditionChecker create(String selector) {
        if (TagResolver.isTagId(selector)) {
            if (!IdValidator.isValidTagId(selector)) {
                return null;
            }
            ResourceLocation location = SafeParseUtil.parseResourceLocation(TagResolver.stripTagPrefix(selector));
            return location == null ? null : new BiomeConditionChecker(null,
                    TagKey.create(Registries.BIOME, location));
        }
        ResourceLocation location = SafeParseUtil.parseResourceLocation(selector);
        return location == null ? null : new BiomeConditionChecker(
                ResourceKey.create(Registries.BIOME, location), null);
    }

    @Override
    public String debugName() {
        return "biome";
    }

    @Override
    public boolean checkCondition(ItemEntity itemEntity, ServerLevel level) {
        var biome = level.getBiome(itemEntity.blockPosition());
        return biomeTag != null ? biome.is(biomeTag) : biomeKey != null && biome.is(biomeKey);
    }
}
