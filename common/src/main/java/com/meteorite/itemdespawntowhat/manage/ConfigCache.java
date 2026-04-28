package com.meteorite.itemdespawntowhat.manage;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ConfigCache {
    private static final Logger LOGGER = LogManager.getLogger();

    // 主缓存：物品ID -> 该物品的所有配置实例
    private static final ConcurrentMap<ResourceLocation, List<BaseConversionConfig>> ITEM_CONFIGS_CACHE = new ConcurrentHashMap<>();

    // 内部ID映射：内部ID -> 配置实例
    private static final ConcurrentMap<String, BaseConversionConfig> INTERNAL_ID_CACHE = new ConcurrentHashMap<>();

    // 是否已初始化标志
    private static volatile boolean initialized = false;

    private ConfigCache() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ========== 公开查询方法 ========== //

    public static List<BaseConversionConfig> getAllConfigsForItem(ResourceLocation itemId) {
        checkInitialized();
        List<BaseConversionConfig> configs = ITEM_CONFIGS_CACHE.get(itemId);
        return configs != null ? Collections.unmodifiableList(configs) : Collections.emptyList();
    }

    @Nullable
    public static BaseConversionConfig getConfigByInternalId(String internalId) {
        checkInitialized();
        return INTERNAL_ID_CACHE.get(internalId);
    }

    public static boolean hasAnyConfigs(ResourceLocation itemId) {
        checkInitialized();
        List<BaseConversionConfig> configs = ITEM_CONFIGS_CACHE.get(itemId);
        return configs != null && !configs.isEmpty();
    }

    @SuppressWarnings("unchecked")
    public static <T extends BaseConversionConfig> List<T> getConfigByType(ConfigType configType) {
        checkInitialized();

        List<T> result = new ArrayList<>();

        for (BaseConversionConfig config : INTERNAL_ID_CACHE.values()) {
            if (config.getConfigType() == configType) {
                result.add((T) config);
            }
        }
        return result.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(result);
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("initialized", initialized);
        stats.put("total_items", ITEM_CONFIGS_CACHE.size());
        stats.put("total_configs", INTERNAL_ID_CACHE.size());

        Map<String, Integer> typeStats = new HashMap<>();
        ITEM_CONFIGS_CACHE.values().stream()
                .flatMap(List::stream)
                .forEach(config -> {
                    String type = config.getConfigType().name();
                    typeStats.put(type, typeStats.getOrDefault(type, 0) + 1);
                });

        stats.put("type_stats", typeStats);

        return stats;
    }

    // ========== 包级写入方法（供 ConfigBootstrap 使用）========== //

    static void addToItemCache(ResourceLocation key, BaseConversionConfig config) {
        ITEM_CONFIGS_CACHE
                .computeIfAbsent(key, rl -> new ArrayList<>())
                .add(config);
    }

    static void addToInternalIdCache(String internalId, BaseConversionConfig config) {
        INTERNAL_ID_CACHE.put(internalId, config);
    }

    static BaseConversionConfig removeFromInternalIdCache(String internalId) {
        return INTERNAL_ID_CACHE.remove(internalId);
    }

    static void removeFromItemCache(ResourceLocation key, BaseConversionConfig config) {
        ITEM_CONFIGS_CACHE.computeIfPresent(key, (k, list) -> {
            list.remove(config);
            return list.isEmpty() ? null : list;
        });
    }

    static Set<Map.Entry<String, BaseConversionConfig>> internalIdCacheEntries() {
        return INTERNAL_ID_CACHE.entrySet();
    }

    static void clearAll() {
        ITEM_CONFIGS_CACHE.clear();
        INTERNAL_ID_CACHE.clear();
    }

    static void setInitialized(boolean value) {
        initialized = value;
    }

    static void logCacheStats() {
        Map<String, Object> stats = getCacheStats();
        LOGGER.info("=== Config Cache Statistics ===");
        LOGGER.info("Total items with configs: {}", stats.get("total_items"));
        LOGGER.info("Total config instances: {}", stats.get("total_configs"));

        @SuppressWarnings("unchecked")
        Map<String, Integer> typeStats = (Map<String, Integer>) stats.get("type_stats");
        typeStats.forEach((type, count) ->
                LOGGER.info("  {}: {} configs", type, count));
    }

    private static void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("ConfigExtractorManager not initialized. Call initialize() first.");
        }
    }
}
