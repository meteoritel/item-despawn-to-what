package com.meteorite.itemdespawntowhat.ui;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@FunctionalInterface
public interface SuggestionProvider {
    int DEFAULT_MAX_FETCH = 100;

    List<String> getSuggestions(String segment, int maxResults);

    default List<String> getSuggestions(String segment) {
        return getSuggestions(segment, DEFAULT_MAX_FETCH);
    }

    // ========== 内置工厂方法 ========== //
    // 匹配注册表（物品、方块、实体）
    static <T> SuggestionProvider ofRegistry(Registry<T> registry) {
        // 预先排好序，查询时直接遍历
        List<String> allIds = registry.keySet()
                .stream()
                .map(ResourceLocation::toString)
                .sorted()
                .toList();

        return (segment, maxResults) -> {
            if (segment.isEmpty()) return List.of();
            String lower = segment.toLowerCase();
            List<String> result = new ArrayList<>();
            for (String id : allIds) {
                if (result.size() >= maxResults) break;
                if (id.startsWith(lower) || pathOf(id).startsWith(lower)) {
                    result.add(id);
                }
            }
            return result;
        };
    }

    // 服务端未加载时的返回原版维度兜底列表
    List<String> VANILLA_DIMENSIONS = List.of(
            "minecraft:overworld",
            "minecraft:the_nether",
            "minecraft:the_end"
    );

    // 匹配维度
    static SuggestionProvider ofDimensions() {
        return (segment, maxResults) -> {
            var server = ServerLifecycleHooks.getCurrentServer();
            String lower = segment.toLowerCase();

            // 收集候选列表：服务端在线则读实际维度，否则回退原版三维度
            List<String> candidates;
            if (server != null) {
                candidates = new ArrayList<>();
                for (ServerLevel level : server.getAllLevels()) {
                    candidates.add(level.dimension().location().toString());
                }
                candidates.sort(null);
            } else {
                candidates = VANILLA_DIMENSIONS;
            }

            List<String> result = new ArrayList<>();
            for (String id : candidates) {
                if (result.size() >= maxResults) break;
                if (id.startsWith(lower) || pathOf(id).startsWith(lower)) {
                    result.add(id);
                }
            }
            return result;
        };
    }

    // 匹配tag
    static <T> SuggestionProvider ofTags(ResourceKey<? extends Registry<T>> registryKey) {
        return (segment, maxResults) -> {
            String effectiveSegment = segment.startsWith("#") ? segment.substring(1) : segment;
            String lower = effectiveSegment.toLowerCase();

            @SuppressWarnings("unchecked")
            Registry<T> registry = (Registry<T>) BuiltInRegistries.REGISTRY.get(registryKey.location());
            if (registry == null) {
                return List.of();
            }

            return registry.getTagNames()
                    .map(TagKey::location)
                    .map(ResourceLocation::toString)
                    .filter(id -> id.startsWith(lower) || pathOf(id).startsWith(lower))
                    .sorted()
                    .limit(maxResults)
                    .map(id -> "#" + id)
                    .collect(Collectors.toList());
        };
    }

    // 从固定字符串列表中匹配
    static SuggestionProvider ofList(String... candidates) {
        return ofList(Arrays.asList(candidates));
    }

    // 从固定字符串集合中匹配
    static SuggestionProvider ofList(Collection<String> candidates) {
        List<String> sorted = candidates.stream().sorted().toList();
        return (segment, maxResults) -> {
            if (segment.isEmpty()) return List.of();
            String lower = segment.toLowerCase();
            return sorted.stream()
                    .filter(s -> s.toLowerCase().startsWith(lower))
                    .limit(maxResults)
                    .collect(Collectors.toList());
        };
    }

    // 将多个建议合并为一个，按顺序查询并去重
    static SuggestionProvider combine(SuggestionProvider... providers) {
        return (segment, maxResults) -> {
            List<String> result = new ArrayList<>();
            for (SuggestionProvider provider : providers) {
                if (result.size() >= maxResults) break;
                int remaining = maxResults - result.size();
                provider.getSuggestions(segment, remaining).stream()
                        .filter(s -> !result.contains(s))
                        .forEach(result::add);
            }
            return result;
        };
    }

    // ========== 内部工具方法 ========== //
    // 从 "namespace:path" 提取 path 部分
    private static String pathOf(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }
}
