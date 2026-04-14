package com.meteorite.itemdespawntowhat.client.ui;

import com.meteorite.itemdespawntowhat.util.TagResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@OnlyIn(Dist.CLIENT)
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

    static <T> SuggestionProvider ofRegistry(Registry<T> registry, Class<?> baseClass) {
        List<String> allIds = registry.entrySet()
                .stream()
                .filter(entry -> baseClass.isInstance(entry.getValue()))
                .map(entry -> entry.getKey().location().toString())
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

    // 专用于 ENTITY_TYPE 注册表，按实体运行时类筛选
    static SuggestionProvider ofEntityTypeRegistry(ServerLevel level, Class<?> baseClass) {
        List<String> allIds = BuiltInRegistries.ENTITY_TYPE.entrySet()
                .stream()
                .filter(entry -> {
                    try {
                        Entity testEntity = entry.getValue().create(level);
                        if (testEntity == null) return false;
                        boolean matches = baseClass.isInstance(testEntity);
                        testEntity.discard();
                        return matches;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .map(entry -> entry.getKey().location().toString())
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

    // 匹配维度
    static SuggestionProvider ofDimensions() {
        return (segment, maxResults) -> {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                return List.of();
            }

            String lower = segment.toLowerCase();
            List<String> candidates = new ArrayList<>();
            for (ServerLevel level : server.getAllLevels()) {
                candidates.add(level.dimension().location().toString());
            }
            candidates.sort(null);

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
            String effectiveSegment = TagResolver.stripTagPrefix(segment);
            String lower = effectiveSegment != null ? effectiveSegment.toLowerCase() : "";

            Registry<T> registry = getRegistry(registryKey);
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
    private static String pathOf(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }
}
