package com.meteorite.itemdespawntowhat.config.runtime;

import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 保存一次完整加载结果的不可变运行时配置索引。
 */
public final class RuntimeConfigSnapshot {
    private static final RuntimeConfigSnapshot EMPTY = new RuntimeConfigSnapshot(Map.of(), Map.of(), Map.of(), Map.of());

    private final Map<ResourceLocation, List<CompiledConversionRule>> rulesByItem;
    private final Map<String, CompiledConversionRule> rulesByInternalId;
    private final Map<ResourceLocation, Integer> maxComplexityByItem;
    private final Map<ConfigType, List<BaseConversionConfig>> configsByType;

    RuntimeConfigSnapshot(
            Map<ResourceLocation, List<CompiledConversionRule>> rulesByItem,
            Map<String, CompiledConversionRule> rulesByInternalId,
            Map<ResourceLocation, Integer> maxComplexityByItem,
            Map<ConfigType, List<BaseConversionConfig>> configsByType
    ) {
        this.rulesByItem = rulesByItem;
        this.rulesByInternalId = rulesByInternalId;
        this.maxComplexityByItem = maxComplexityByItem;
        this.configsByType = configsByType;
    }

    public static RuntimeConfigSnapshot empty() {
        return EMPTY;
    }

    public List<CompiledConversionRule> getRulesForItem(ResourceLocation itemId) {
        return rulesByItem.getOrDefault(itemId, List.of());
    }

    @Nullable
    public CompiledConversionRule getRuleByInternalId(String internalId) {
        return rulesByInternalId.get(internalId);
    }

    public boolean hasConfigsForItem(ResourceLocation itemId) {
        return rulesByItem.containsKey(itemId);
    }

    public int getMaxComplexity(ResourceLocation itemId) {
        return maxComplexityByItem.getOrDefault(itemId, 0);
    }

    @SuppressWarnings("unchecked")
    public <T extends BaseConversionConfig> List<T> getConfigsByType(ConfigType type) {
        return (List<T>) configsByType.getOrDefault(type, List.of());
    }

    public int itemCount() {
        return rulesByItem.size();
    }

    public int configCount() {
        return rulesByInternalId.size();
    }

}
