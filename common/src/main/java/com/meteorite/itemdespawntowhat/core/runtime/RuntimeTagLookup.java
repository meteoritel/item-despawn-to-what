package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.TagLookup;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 标签查询的服务端实现：按标签缓存成员集合，缓存随运行时重建（reload）一起丢弃。
 * 物品/方块/实体/流体/药水效果走静态注册表；生物群系走当前服务端的动态注册表。
 */
public final class RuntimeTagLookup implements TagLookup {

    private final ServerLevel level;
    private final Map<String, Set<ResourceLocation>> cache = new HashMap<>();

    public RuntimeTagLookup(ServerLevel level) {
        this.level = level;
    }

    @Override
    public boolean itemInTag(ResourceLocation tagId, ResourceLocation itemId) {
        return members("item", BuiltInRegistries.ITEM, Registries.ITEM, tagId, item -> BuiltInRegistries.ITEM.getKey(item))
                .contains(itemId);
    }

    @Override
    public boolean blockInTag(ResourceLocation tagId, ResourceLocation blockId) {
        return members("block", BuiltInRegistries.BLOCK, Registries.BLOCK, tagId, block -> BuiltInRegistries.BLOCK.getKey(block))
                .contains(blockId);
    }

    @Override
    public boolean entityInTag(ResourceLocation tagId, ResourceLocation entityTypeId) {
        return members("entity", BuiltInRegistries.ENTITY_TYPE, Registries.ENTITY_TYPE, tagId,
                type -> BuiltInRegistries.ENTITY_TYPE.getKey(type)).contains(entityTypeId);
    }

    @Override
    public boolean biomeInTag(ResourceLocation tagId, ResourceLocation biomeId) {
        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        return members("biome", biomes, Registries.BIOME, tagId, biome -> biomes.getKey(biome)).contains(biomeId);
    }

    @Override
    public boolean fluidInTag(ResourceLocation tagId, ResourceLocation fluidId) {
        return members("fluid", BuiltInRegistries.FLUID, Registries.FLUID, tagId, fluid -> BuiltInRegistries.FLUID.getKey(fluid))
                .contains(fluidId);
    }

    @Override
    public boolean mobEffectInTag(ResourceLocation tagId, ResourceLocation effectId) {
        return members("mob_effect", BuiltInRegistries.MOB_EFFECT, Registries.MOB_EFFECT, tagId,
                effect -> BuiltInRegistries.MOB_EFFECT.getKey(effect)).contains(effectId);
    }

    // 展开标签成员并缓存；标签不存在时缓存空集合
    private <T> Set<ResourceLocation> members(String kind, Registry<T> registry,
                                              ResourceKey<? extends Registry<T>> registryKey,
                                              ResourceLocation tagId, Function<T, ResourceLocation> idOf) {
        String key = kind + ":" + tagId;
        Set<ResourceLocation> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        HolderSet.Named<T> holders = registry.getTag(TagKey.create(registryKey, tagId)).orElse(null);
        if (holders == null) {
            cache.put(key, Set.of());
            return Set.of();
        }
        Set<ResourceLocation> members = new HashSet<>();
        for (Holder<T> holder : holders) {
            T value = holder.value();
            ResourceLocation id = idOf.apply(value);
            if (id != null) {
                members.add(id);
            }
        }
        Set<ResourceLocation> immutable = Set.copyOf(members);
        cache.put(key, immutable);
        return immutable;
    }
}
