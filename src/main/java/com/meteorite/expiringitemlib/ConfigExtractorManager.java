package com.meteorite.expiringitemlib;

import com.meteorite.expiringitemlib.config.BaseConversionConfig;
import com.meteorite.expiringitemlib.handler.BaseConfigHandler;
import com.meteorite.expiringitemlib.util.ConditionChecker;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class ConfigExtractorManager {
    private static final Logger LOGGER = LogManager.getLogger();

    // ======== 所有配置缓存的统一管理 ======== //
    // 主缓存：物品ID -> 该物品的所有配置实例
    private static final ConcurrentMap<ResourceLocation, List<BaseConversionConfig>> ITEM_CONFIGS_CACHE = new ConcurrentHashMap<>();

    // 内部ID映射：内部ID -> 配置实例
    private static final ConcurrentMap<String, BaseConversionConfig> INTERNAL_ID_CACHE = new ConcurrentHashMap<>();

    // 条件检查器缓存：内部ID -> 条件检查器（用于快速查找检查条件）
    private static final ConcurrentMap<String, ConditionChecker> CONDITION_CHECKER_CACHE = new ConcurrentHashMap<>();

    // 是否已初始化标志
    private static volatile boolean initialized = false;

    private ConfigExtractorManager() {
        throw new UnsupportedOperationException("Utility class");

    }

    // 初始化所有缓存
    public static synchronized void initialize() {
        if (initialized) {
            LOGGER.warn("ConfigExtractorManager already initialized");
            return;
        }

        try {
            // 初始化提取器缓存
            loadAllConfigs();

            initialized = true;
            LOGGER.info("ConfigExtractorManager initialized successfully");
            LOGGER.info("Total items with configs: {}, Total configs: {}",
                    ITEM_CONFIGS_CACHE.size(), INTERNAL_ID_CACHE.size());

            // 输出缓存统计
            logCacheStats();
        } catch (Exception e) {
            LOGGER.error("Failed to initialize ConfigExtractorManager", e);
            clearAllCaches();
            throw new RuntimeException("ConfigExtractorManager initialization failed", e);
        }
    }

    // 加载所有配置文件的内容
    public static void loadAllConfigs() {
        ConfigManager configManager = ConfigManager.getInstance();

        if (!configManager.isLoaded()) {
            configManager.generateDefaultConfig();
        }

        // 获取所有已注册的配置处理器类型
        Set<String> handlerTypes = configManager.getRegisteredHandlerTypes();

        for (String configType : handlerTypes) {
            try {
                BaseConfigHandler<?> handler = configManager.getHandler(configType);
                if (handler == null) {
                    LOGGER.warn("No handler found for config type: {}", configType);
                    continue;
                }

                List<? extends BaseConversionConfig> configs = handler.loadConfig();
                if (configs != null && !configs.isEmpty()) {
                    processConfigs(configs);
                }

            } catch (Exception e) {
                LOGGER.error("Failed to load configs for type: {}", configType, e);
            }
        }

    }

    private static void processConfigs(List<? extends BaseConversionConfig> configs) {
        for (BaseConversionConfig config : configs) {
            if (!isValidConfig(config)) {
                LOGGER.warn("Skipping invalid config for item: {}", config.getItemId());
                continue;
            }

            ResourceLocation itemId = config.getItemId();
            String internalId = config.getInternalId();

            // 添加到主缓存
            List<BaseConversionConfig> itemConfigs = ITEM_CONFIGS_CACHE
                    .computeIfAbsent(itemId, k -> new ArrayList<>());
            itemConfigs.add(config);

            // 添加到内部ID缓存
            INTERNAL_ID_CACHE.put(internalId, config);

            // 构建并缓存条件检查器
            ConditionChecker checker = config.buildConditionChecker();
            if (checker != null) {
                CONDITION_CHECKER_CACHE.put(internalId, checker);
            }
        }
    }

    private static boolean isValidConfig(BaseConversionConfig config) {
        return config != null && config.shouldProcess();
    }

    // ========== 查询方法，用于其他类调用缓存 ========== //

    // 获取物品的所有配置实例
    public static List<BaseConversionConfig> getAllConfigsForItem(ResourceLocation itemId) {
        checkInitialized();
        List<BaseConversionConfig> configs = ITEM_CONFIGS_CACHE.get(itemId);
        return configs != null ? Collections.unmodifiableList(configs) : Collections.emptyList();
    }


    // 根据实例uuid获取配置实例
    @Nullable
    public static BaseConversionConfig getConfigByInternalId(String internalId) {
        checkInitialized();
        return INTERNAL_ID_CACHE.get(internalId);
    }

    // 根据实例uuid获取配置对应的条件检查器
    @Nullable
    public static ConditionChecker getConditionCheckerForConfig(String internalId) {
        checkInitialized();
        return CONDITION_CHECKER_CACHE.get(internalId);
    }

    // 检查物品是否有任何配置
    public static boolean hasAnyConfigs(ResourceLocation itemId) {
        checkInitialized();
        List<BaseConversionConfig> configs = ITEM_CONFIGS_CACHE.get(itemId);
        return configs != null && !configs.isEmpty();
    }

    // 获取所有配置的物品ID
    public static Set<ResourceLocation> getAllConfiguredItems() {
        checkInitialized();
        return Collections.unmodifiableSet(ITEM_CONFIGS_CACHE.keySet());
    }

    // 获取所有配置实例
    public static List<BaseConversionConfig> getAllConfigs() {
        checkInitialized();
        return ITEM_CONFIGS_CACHE.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }
    // ========== 辅助方法 ========== //

    // 清除所有缓存，用于服务端结束
    public static synchronized void clearAllCaches() {
        ITEM_CONFIGS_CACHE.clear();
        INTERNAL_ID_CACHE.clear();
        CONDITION_CHECKER_CACHE.clear();
        initialized = false;

        LOGGER.info("All caches cleared");
    }

    public static boolean isInitialized() {
        return initialized;
    }

    // 重新加载所有配置，用于热重载
    public static synchronized void reloadAllConfigs() {
        if (!initialized) {
            initialize();
            return;
        }

        try {
            // 清除现有缓存
            clearAllCaches();
            initialized = false;

            // 重新加载配置
            initialize();

            LOGGER.info("All configs reloaded successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to reload configs", e);
        }
    }

    // 缓存统计信息
    public static Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("initialized", initialized);
        stats.put("total_items", ITEM_CONFIGS_CACHE.size());
        stats.put("total_configs", INTERNAL_ID_CACHE.size());
        stats.put("condition_checkers", CONDITION_CHECKER_CACHE.size());

        // 按配置类型统计
        Map<String, Integer> typeStats = new HashMap<>();
        ITEM_CONFIGS_CACHE.values().stream()
                .flatMap(List::stream)
                .forEach(config -> {
                    String type = config.getClass().getSimpleName();
                    typeStats.put(type, typeStats.getOrDefault(type, 0) + 1);
                });

        stats.put("type_stats", typeStats);

        return stats;
    }

    private static void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("ConfigExtractorManager not initialized. Call initialize() first.");
        }
    }

    // 加载统计信息，用于日志
    private static void logCacheStats() {
        Map<String, Object> stats = getCacheStats();
        LOGGER.info("=== Config Cache Statistics ===");
        LOGGER.info("Total items with configs: {}", stats.get("total_items"));
        LOGGER.info("Total config instances: {}", stats.get("total_configs"));
        LOGGER.info("Condition checkers: {}", stats.get("condition_checkers"));

        @SuppressWarnings("unchecked")
        Map<String, Integer> typeStats = (Map<String, Integer>) stats.get("type_stats");
        typeStats.forEach((type, count) ->
                LOGGER.info("  {}: {} configs", type, count));
    }

}
