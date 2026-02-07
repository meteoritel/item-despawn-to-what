package com.meteorite.expiringitemlib.extractor;


import com.meteorite.expiringitemlib.config.BaseConversionConfig;
import com.meteorite.expiringitemlib.config.SurroundingBlocks;
import com.meteorite.expiringitemlib.util.ConditionChecker;
import com.meteorite.expiringitemlib.util.ConditionCheckerUtil;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// 不再用的写法，缓存已经诺到了configExtractorManager类中
// 废弃进行中~
// 差不多可以删了
@Deprecated
public class BaseConfigExtractor<T extends BaseConversionConfig> implements IConfigExtractor<T>{
    protected static final Logger LOGGER = LogManager.getLogger();
    protected final String configType;

    @Deprecated
    protected final Map<ResourceLocation, List<T>> itemConfigsCache = new ConcurrentHashMap<>();
    @Deprecated
    protected final Map<String, T> internalConfigCache = new ConcurrentHashMap<>();
    @Deprecated
    protected final Map<String, ConditionChecker> conditionCache = new ConcurrentHashMap<>();
    @Deprecated
    protected final Map<String, ConditionChecker> configConditionCache = new ConcurrentHashMap<>();


    protected BaseConfigExtractor(String configType) {
        this.configType = configType;
    }


    @Override
    public String getConfigType() {
        return configType;
    }

    @Override
    public boolean isValidConfig(T config) {
        return config != null && config.shouldProcess();
    }

    // 配置缓存键
    @Deprecated
    @Override
    public String generateCacheKey(T config) {
        if (!isValidConfig(config)) {
            return "";
        }
        StringBuilder keyBuilder = new StringBuilder(128);

        String dimension = config.getDimension();
        String isOutdoor = config.getIsOutdoor();
        SurroundingBlocks blocks = config.getSurroundingBlocks();

        keyBuilder.append(dimension != null ? dimension : "")
                .append(':')
                .append(isOutdoor != null ? isOutdoor : "");

        if (blocks != null) {
            keyBuilder.append(":north=").append(blocks.getNorth())
                    .append(":south=").append(blocks.getSouth())
                    .append(":east=").append(blocks.getEast())
                    .append(":west=").append(blocks.getWest())
                    .append(":up=").append(blocks.getUp())
                    .append(":down=").append(blocks.getDown());
        }

        return keyBuilder.toString();
    }

    // 构建条件检查器
    @Deprecated
    public ConditionChecker buildConditionChecker(T config) {
        if (!isValidConfig(config)) {
            return null;
        }

        String cacheKey = generateCacheKey(config);

        return conditionCache.computeIfAbsent(cacheKey, key -> {
            String dimension = Optional.ofNullable(config.getDimension()).orElse("");
            String isOutdoor = Optional.ofNullable(config.getIsOutdoor()).orElse("");
            SurroundingBlocks surroundingBlocks = config.getSurroundingBlocks();

            return ConditionCheckerUtil.buildCombinedChecker(dimension, isOutdoor, surroundingBlocks);
        });
    }

    @Override
    public Set<ResourceLocation> getAllItemIds(List<T> configs) {
        if (configs == null || configs.isEmpty()) {
            return Collections.emptySet();
        }

        return configs.stream()
                .filter(this::isValidConfig)
                .map(BaseConversionConfig::getItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @Override
    public List<T> getItemConfigs(List<T> configs, ResourceLocation itemId) {
        if (configs == null || configs.isEmpty() || itemId == null) {
            return Collections.emptyList();
        }

        return configs.stream()
                .filter(this::isValidConfig)
                .filter(config -> itemId.equals(config.getItemId()))
                .collect(Collectors.toList());
    }

    @Override
    public T getConfigByInternalId(List<T> configs, String internalId) {
        if (configs == null || configs.isEmpty() || internalId == null || internalId.isEmpty()) {
            return null;
        }

        return configs.stream()
                .filter(this::isValidConfig)
                .filter(config -> internalId.equals(config.getInternalId()))
                .findFirst()
                .orElse(null);
    }




    // 初始化缓存
    @Deprecated
    public void initializeCaches(List<T> configs) {
        if (configs == null || configs.isEmpty()) {
            LOGGER.debug("[{}] No configs provided for initialization", configType);
            return;
        }

        clearAllCaches();

        int validConfigCount = 0;

        for (T config : configs) {
            if (!isValidConfig(config)) {
                LOGGER.warn("[{}] Skipping invalid config for item: {}", configType, config.getItemId());
                continue;
            }

            ResourceLocation itemId = config.getItemId();
            String internalId = config.getInternalId();

            // 添加到物品配置列表
            itemConfigsCache
                    .computeIfAbsent(itemId, k -> new ArrayList<>())
                    .add(config);

            // 添加到内部ID缓存
            internalConfigCache.put(internalId, config);

            // 构建条件检查器
            ConditionChecker checker = buildConditionChecker(config);
            if (checker != null) {
                configConditionCache.put(internalId, checker);
                LOGGER.debug("[{}] Condition checker added for config: {}", configType, internalId);
            }

            validConfigCount++;
        }

        LOGGER.info("[{}] Initialized with {} valid configs", configType, validConfigCount);
    }




    // 获取物品对应的所有配置实例，只读
    @Deprecated
    public List<T> getItemConfigs(ResourceLocation itemId) {
        List<T> configs = itemConfigsCache.get(itemId);
        return configs != null ? Collections.unmodifiableList(configs) : Collections.emptyList();
    }

    // 获取特定uuid的配置实例
    @Deprecated
    public T getConfigByInternalId(String internalId) {
        return internalConfigCache.get(internalId);
    }

    // 获取特定uuid配置的条件检查器
    @Deprecated
    public ConditionChecker getConditionCheckerForConfig(String internalId) {
        return configConditionCache.get(internalId);
    }

    // 清除所有缓存
    @Deprecated
    public void clearAllCaches() {
        itemConfigsCache.clear();
        internalConfigCache.clear();
        conditionCache.clear();
        configConditionCache.clear();

        LOGGER.debug("[{}] All caches cleared", configType);
    }

    // ======== 辅助方法 ======== //

    // 检查缓存是否为空
    public boolean isAnyCacheEmpty() {
        return itemConfigsCache.isEmpty() || internalConfigCache.isEmpty();
    }

    // 获取所有配置的条件检查器与uuid的映射
    public Map<String, ConditionChecker> getConditionCheckersForConfigs(Set<String> internalIds) {
        Map<String, ConditionChecker> result = new HashMap<>();
        for (String id : internalIds) {
            ConditionChecker checker = configConditionCache.get(id);
            if (checker != null) {
                result.put(id, checker);
            }
        }
        return result;
    }

}
