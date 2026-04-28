package com.meteorite.itemdespawntowhat.manage;

import com.meteorite.itemdespawntowhat.ConfigHandlerManager;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.handler.BaseConfigHandler;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.*;

public class ConfigBootstrap {
    private static final Logger LOGGER = LogManager.getLogger();

    // 待展开的标签配置（itemId 以 # 开头），在 expandTagConfigs() 中处理
    private static final List<BaseConversionConfig> TAG_PENDING_CONFIGS = new ArrayList<>();

    private ConfigBootstrap() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ========== 公开方法 ========== //

    public static synchronized void initialize(Path configDir) {
        if (ConfigCache.isInitialized()) {
            LOGGER.warn("ConfigExtractorManager already initialized");
            return;
        }

        try {
            loadAllConfigs(configDir);

            expandTagConfigs();

            ConfigCache.setInitialized(true);
            LOGGER.info("ConfigExtractorManager initialized successfully");
            LOGGER.info("Total items with configs: {}, Total configs: {}",
                    ConfigCache.getCacheStats().get("total_items"),
                    ConfigCache.getCacheStats().get("total_configs"));
            ConfigCache.logCacheStats();
        } catch (Exception e) {
            LOGGER.error("ConfigExtractorManager initialization partially failed, continuing with loaded configs", e);
            // 保留已成功加载的配置，不因单个类型失败而丢弃全部缓存
            ConfigCache.setInitialized(true);
        }
    }

    public static boolean reloadAllConfigs(Path configDir) {
        try {
            if (!ConfigCache.isInitialized()) {
                initialize(configDir);
                return true;
            }

            clearAllCaches();
            initialize(configDir);

            LOGGER.info("All configs reloaded successfully");
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to reload configs", e);
            return false;
        }
    }

    public static void reloadConfigsForType(Path configDir, ConfigType configType) {
        try {
            removeConfigsOfType(configType);

            ConfigHandlerManager handlerManager = ConfigHandlerManager.getInstance(configDir);
            BaseConfigHandler<?> handler = handlerManager.getHandler(configType);
            if (handler == null) {
                LOGGER.error("No handler found for config type: {}", configType);
                return;
            }

            if (!handler.isConfigFileExists()) {
                handler.generateDefaultConfig();
            }

            List<? extends BaseConversionConfig> configs = handler.loadConfig();
            if (configs != null && !configs.isEmpty()) {
                processConfigs(configs);
            }

            expandTagConfigs();

            LOGGER.info("Configs reloaded for type: {}", configType);
        } catch (Exception e) {
            LOGGER.error("Failed to reload configs for type: {}", configType, e);
        }
    }

    public static void expandTagConfigs() {
        if (TAG_PENDING_CONFIGS.isEmpty()) return;
        for (BaseConversionConfig config : TAG_PENDING_CONFIGS) {
            config.expandTagItems();
            List<Item> items = config.getTagItems();
            if (items.isEmpty()) {
                LOGGER.warn("Tag '{}' resolved to no items, config will be skipped", config.getItemId());
                continue;
            }
            for (Item item : items) {
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                ConfigCache.addToItemCache(key, config);
            }
            LOGGER.debug("Expanded tag config '{}' to {} items", config.getItemId(), items.size());
        }
        TAG_PENDING_CONFIGS.clear();
        LOGGER.info("Tag configs expanded, total items with configs: {}", ConfigCache.getCacheStats().get("total_items"));
    }

    public static void clearAllCaches() {
        ConfigCache.clearAll();
        TAG_PENDING_CONFIGS.clear();
        ConfigCache.setInitialized(false);

        LOGGER.info("All caches cleared");
    }

    public static void removeConfigByInternalId(String internalId) {
        if (internalId == null || internalId.isEmpty()) return;
        BaseConversionConfig config = ConfigCache.removeFromInternalIdCache(internalId);

        if (config == null) {
            LOGGER.debug("removeConfigByInternalId: no config found for id={}", internalId);
            return;
        }

        if (config.isTagMode()) {
            for (Item item : config.getTagItems()) {
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                ConfigCache.removeFromItemCache(key, config);
            }
        } else {
            ResourceLocation itemKey = SafeParseUtil.parseResourceLocation(config.getItemId());
            if (itemKey != null) ConfigCache.removeFromItemCache(itemKey, config);
        }
        LOGGER.info("Removed invalid config: internalId={}, item={}, type={}",
                internalId, config.getItemId(), config.getConfigType());
    }

    // ========== 包级方法 ========== //

    static void loadAllConfigs(Path configDir) {
        ConfigHandlerManager handlerManager = ConfigHandlerManager.getInstance(configDir);

        if (!handlerManager.isLoaded()) {
            handlerManager.generateDefaultConfig();
        }

        Set<ConfigType> handlerTypes = handlerManager.getRegisteredHandlerTypes();

        for (ConfigType configType : handlerTypes) {
            try {
                BaseConfigHandler<?> handler = handlerManager.getHandler(configType);
                if (handler == null) {
                    LOGGER.warn("No handler found for config type: {}", configType);
                    continue;
                }

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

    static void processConfigs(List<? extends BaseConversionConfig> configs) {
        for (BaseConversionConfig config : configs) {
            if (config == null || !config.shouldProcess()) {
                LOGGER.warn("Skipping invalid config for item: {}",
                        config != null ? config.getItemId() : "null");
                continue;
            }

            config.initCache();

            if (!config.isCacheValid()) {
                LOGGER.warn("Skipping config with invalid result cache: item={}, result={}",
                        config.getItemId(), config.getResultId());
                continue;
            }

            String itemIdStr = config.getItemId();
            String internalId = config.getInternalId();

            if (config.isTagMode()) {
                TAG_PENDING_CONFIGS.add(config);
                ConfigCache.addToInternalIdCache(internalId, config);
            } else {
                ResourceLocation itemId = SafeParseUtil.parseResourceLocation(itemIdStr);
                ConfigCache.addToItemCache(itemId, config);
                ConfigCache.addToInternalIdCache(internalId, config);
            }
        }
    }

    // ========== 私有方法 ========== //

    private static void removeConfigsOfType(ConfigType configType) {
        List<String> idsToRemove = new ArrayList<>();
        for (Map.Entry<String, BaseConversionConfig> entry : ConfigCache.internalIdCacheEntries()) {
            if (entry.getValue().getConfigType() == configType) {
                idsToRemove.add(entry.getKey());
            }
        }

        for (String internalId : idsToRemove) {
            BaseConversionConfig config = ConfigCache.removeFromInternalIdCache(internalId);
            if (config != null) {
                if (config.isTagMode()) {
                    for (Item item : config.getTagItems()) {
                        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                        ConfigCache.removeFromItemCache(key, config);
                    }
                } else {
                    ResourceLocation itemKey = SafeParseUtil.parseResourceLocation(config.getItemId());
                    if (itemKey != null) ConfigCache.removeFromItemCache(itemKey, config);
                }
            }
        }
    }
}
