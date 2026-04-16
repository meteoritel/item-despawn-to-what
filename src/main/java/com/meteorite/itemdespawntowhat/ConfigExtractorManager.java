package com.meteorite.itemdespawntowhat;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.handler.BaseConfigHandler;
import com.meteorite.itemdespawntowhat.condition.checker.ConditionChecker;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ConfigExtractorManager {
    private static final Logger LOGGER = LogManager.getLogger();

    // ======== 所有配置缓存的统一管理 ======== //
    // 主缓存：物品ID -> 该物品的所有配置实例
    private static final ConcurrentMap<ResourceLocation, List<BaseConversionConfig>> ITEM_CONFIGS_CACHE = new ConcurrentHashMap<>();

    // 待展开的标签配置（itemId 以 # 开头），在 expandTagConfigs() 中处理
    private static final List<BaseConversionConfig> TAG_PENDING_CONFIGS = new ArrayList<>();

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

            // 展开标签配置并插入主缓存
            expandTagConfigs();

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
        ConfigHandlerManager handlerManager = ConfigHandlerManager.getInstance();

        if (!handlerManager.isLoaded()) {
            handlerManager.generateDefaultConfig();
        }

        // 获取所有已注册的配置处理器类型
        Set<ConfigType> handlerTypes = handlerManager.getRegisteredHandlerTypes();

        for (ConfigType configType : handlerTypes) {
            try {
                BaseConfigHandler<?> handler = handlerManager.getHandler(configType);
                if (handler == null) {
                    LOGGER.warn("No handler found for config type: {}", configType);
                    continue;
                }

                // 如果中途json文件被删掉了，加载之前也会重新生成
                if (!handler.isConfigFileExists()) {
                    handler.generateDefaultConfig();
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
            if (config == null || !config.shouldProcess()) {
                LOGGER.warn("Skipping invalid config for item: {}",
                        config != null ? config.getItemId() : "null");
                continue;
            }

            // 初始化配置缓存
            config.initCache();

            if (!config.isCacheValid()) {
                LOGGER.warn("Skipping config with invalid result cache: item={}, result={}",
                        config.getItemId(), config.getResultId());
                continue;
            }

            String itemIdStr = config.getItemId();
            String internalId = config.getInternalId();

            if (config.isTagMode()) {
                // 标签配置暂存，由 expandTagConfigs() 统一展开
                TAG_PENDING_CONFIGS.add(config);
                INTERNAL_ID_CACHE.put(internalId, config);
            } else {
                ResourceLocation itemId = SafeParseUtil.parseResourceLocation(itemIdStr);
                // 添加到主缓存
                ITEM_CONFIGS_CACHE
                        .computeIfAbsent(itemId, rl -> new ArrayList<>())
                        .add(config);

                // 添加到内部ID缓存
                INTERNAL_ID_CACHE.put(internalId, config);

                // 构建并缓存条件检查器
                ConditionChecker checker = config.buildConditionChecker();
                if (checker != null) {
                    CONDITION_CHECKER_CACHE.put(internalId, checker);
                }
            }
        }
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

    // 根据配置类型获取已加载的对应缓存
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

    // ========== 生命周期管理 ========== //
    // 清除所有缓存
    public static void clearAllCaches() {
        ITEM_CONFIGS_CACHE.clear();
        INTERNAL_ID_CACHE.clear();
        CONDITION_CHECKER_CACHE.clear();
        TAG_PENDING_CONFIGS.clear();
        initialized = false;

        LOGGER.info("All caches cleared");
    }

    // 服务端启动后调用，将标签配置展开为具体物品并插入主缓存
    public static synchronized void expandTagConfigs() {
        if (TAG_PENDING_CONFIGS.isEmpty()) return;
        for (BaseConversionConfig config : TAG_PENDING_CONFIGS) {
            config.expandTagItems();
            List<net.minecraft.world.item.Item> items = config.getTagItems();
            if (items.isEmpty()) {
                LOGGER.warn("Tag '{}' resolved to no items, config will be skipped", config.getItemId());
                continue;
            }
            String internalId = config.getInternalId();
            for (net.minecraft.world.item.Item item : items) {
                ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
                ITEM_CONFIGS_CACHE.computeIfAbsent(key, k -> new ArrayList<>()).add(config);
            }
            ConditionChecker checker = config.buildConditionChecker();
            if (checker != null) {
                CONDITION_CHECKER_CACHE.put(internalId, checker);
            }
            LOGGER.debug("Expanded tag config '{}' to {} items", config.getItemId(), items.size());
        }
        TAG_PENDING_CONFIGS.clear();
        LOGGER.info("Tag configs expanded, total items with configs: {}", ITEM_CONFIGS_CACHE.size());
    }

    // 清除特定uuid的缓存
    public static boolean removeConfigByInternalId(String internalId) {
        if (internalId == null || internalId.isEmpty()) return false;
        BaseConversionConfig config = INTERNAL_ID_CACHE.remove(internalId);

        if (config == null) {
            LOGGER.debug("removeConfigByInternalId: no config found for id={}", internalId);
            return false;
        }

        CONDITION_CHECKER_CACHE.remove(internalId);

        // 从主缓存中移除
        if (config.isTagMode()) {
            // 标签配置可能展开到多个物品 key
            for (net.minecraft.world.item.Item item : config.getTagItems()) {
                ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
                ITEM_CONFIGS_CACHE.computeIfPresent(key, (k, list) -> {
                    list.remove(config);
                    return list.isEmpty() ? null : list;
                });
            }
        } else {
            ResourceLocation itemKey = SafeParseUtil.parseResourceLocation(config.getItemId());
            if (itemKey != null) ITEM_CONFIGS_CACHE.computeIfPresent(itemKey, (k, list) -> {
                list.remove(config);
                return list.isEmpty() ? null : list;
            });
        }
        LOGGER.info("Removed invalid config: internalId={}, item={}, type={}",
                internalId, config.getItemId(), config.getConfigType());
        return true;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    // 重新加载所有配置，用于热重载
    public static boolean reloadAllConfigs() {
        try {
            if (!initialized) {
                initialize();
                return true;
            }

            // 清除现有缓存
            clearAllCaches();
            // 重新加载配置
            initialize();

            LOGGER.info("All configs reloaded successfully");
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to reload configs", e);
            return false;
        }
    }

    // ========== 辅助方法 ========== //


    private static void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("ConfigExtractorManager not initialized. Call initialize() first.");
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
                    String type = config.getConfigType().name();
                    typeStats.put(type, typeStats.getOrDefault(type, 0) + 1);
                });

        stats.put("type_stats", typeStats);

        return stats;
    }

    // 加载统计信息，用于日志调试
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
