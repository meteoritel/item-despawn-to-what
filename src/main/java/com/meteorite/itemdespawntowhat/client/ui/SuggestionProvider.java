package com.meteorite.itemdespawntowhat.client.ui;

import com.meteorite.itemdespawntowhat.util.TagResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@OnlyIn(Dist.CLIENT)
@FunctionalInterface
public interface SuggestionProvider {
    int DEFAULT_MAX_FETCH = 100;

    List<String> getSuggestions(String segment, int maxResults);

    default List<String> getSuggestions(String segment) {
        return getSuggestions(segment, DEFAULT_MAX_FETCH);
    }

    // 这是客户端缓存，或许需要
    Map<String, List<String>> CACHE = new ConcurrentHashMap<>();

    // ========== 内置工厂方法 ========== //
    // 匹配注册表（物品、方块、实体）
    static <T> SuggestionProvider ofRegistry(Registry<T> registry) {
        return ofRegistry(registry, entry -> true);
    }

    // 带过滤的注册表
    private static <T> SuggestionProvider ofRegistry(Registry<T> registry, Predicate<T> filter) {
        // 预先排好序，查询时直接遍历
        String cacheKey = "registry_" + System.identityHashCode(registry) + "_" + filter.hashCode();

        List<String> ids = CACHE.computeIfAbsent(
                cacheKey,
                k -> registry.keySet().stream()
                        .filter(id -> registry.getOptional(id).filter(filter).isPresent())
                        .map(ResourceLocation::toString)
                        .sorted()
                        .toList()
        );

        return matcher(ids);
    }

    // 匹配mob实体
    static SuggestionProvider ofMobEntityTypes() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return empty();
        }

        var registry = server.registryAccess().registryOrThrow(Registries.ENTITY_TYPE);

        List<String> ids = CACHE.computeIfAbsent("mob_entities",
                k -> registry.keySet()
                        .stream()
                        .filter(id -> registry.getOptional(id)
                                .map(entityType -> entityType.getCategory() != MobCategory.MISC)
                                .orElse(false))
                        .map(ResourceLocation::toString)
                        .sorted()
                        .toList());
        return matcher(ids);
    }


    // 同时匹配注册表 ID 和 #tag 标签
    static <T> SuggestionProvider ofRegistryWithTags(Registry<T> registry, ResourceKey<? extends Registry<T>> registryKey) {
        SuggestionProvider entries = ofRegistry(registry);
        SuggestionProvider tags = ofTags(registryKey);
        return (segment, maxResults) -> {
            if (TagResolver.isTagId(segment)) {
                return tags.getSuggestions(segment, maxResults);
            }
            return entries.getSuggestions(segment, maxResults);
        };
    }

    // 匹配维度
    static SuggestionProvider ofDimensions() {
        return (segment, maxResults) -> {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                return List.of();
            }

            List<String> ids = new ArrayList<>();
            for (ServerLevel level : server.getAllLevels()) {
                ids.add(level.dimension().location().toString());
            }

            ids.sort(Comparator.naturalOrder());
            return filter(ids, segment, maxResults);
        };
    }

    // 匹配tag
    static <T> SuggestionProvider ofTags(ResourceKey<? extends Registry<T>> registryKey) {
        return (segment, maxResults) -> {
            Registry<T> registry = getRegistry(registryKey);
            if (registry == null) {
                return List.of();
            }

            String effectiveSegment = TagResolver.stripTagPrefix(segment);
            String lower = effectiveSegment.toLowerCase(Locale.ROOT);

            return registry.getTagNames()
                    .map(TagKey::location)
                    .map(ResourceLocation::toString)
                    .filter(id ->
                            startsWithIgnoreCase(id, lower) ||
                                    startsWithIgnoreCase(pathOf(id), lower))
                    .sorted()
                    .limit(maxResults)
                    .map(id -> "#" + id)
                    .collect(Collectors.toList());
        };
    }

    // 获取注册键（数据包）
    private static <T> Registry<T> getRegistry(ResourceKey<? extends Registry<T>> registryKey) {
        var serve = ServerLifecycleHooks.getCurrentServer();
        if (serve != null) {
            return serve.registryAccess().registry(registryKey).orElse(null);
        }

        var mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            return mc.getConnection().registryAccess().registry(registryKey).orElse(null);
        }

        return null;
    }

    // ========== 通用匹配方案 ========== //
    static SuggestionProvider matcher(List<String> ids) {
        return (segment, maxResults) -> filter(ids, segment, maxResults);
    }

    static List<String> filter(List<String> ids, String segment, int maxResults) {
        if (segment == null || segment.isEmpty()) {
            return List.of();
        }

        String lower = segment.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>(Math.min(maxResults, 32));

        // 第一轮：前缀匹配（优先）
        for (String id : ids) {
            if (result.size() >= maxResults) {
                return result;
            }

            if (startsWithIgnoreCase(id, lower)
                    || startsWithIgnoreCase(pathOf(id), lower)) {
                result.add(id);
            }
        }

        // 第二轮：contains 模糊匹配
        for (String id : ids) {
            if (result.size() >= maxResults) {
                break;
            }

            if (result.contains(id)) {
                continue;
            }

            String path = pathOf(id).toLowerCase(Locale.ROOT);
            String full = id.toLowerCase(Locale.ROOT);

            if (path.contains(lower) || full.contains(lower)) {
                result.add(id);
            }
        }

        return result;
    }

    static SuggestionProvider empty() {
        return (segment, maxResults) -> List.of();
    }

    // ========== 工具方法 ========== //
    private static String pathOf(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    static boolean startsWithIgnoreCase(String text, String prefix) {
        return text.toLowerCase(Locale.ROOT).startsWith(prefix);
    }

}
