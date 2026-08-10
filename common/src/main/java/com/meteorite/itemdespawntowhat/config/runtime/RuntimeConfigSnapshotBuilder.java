package com.meteorite.itemdespawntowhat.config.runtime;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.type.ConversionType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 在发布前构建完整的运行时配置索引。
 */
public final class RuntimeConfigSnapshotBuilder {
    private final ConversionRuleCompiler compiler = new ConversionRuleCompiler();
    private final Map<ResourceLocation, List<CompiledConversionRule>> rulesByItem = new HashMap<>();
    private final Map<String, CompiledConversionRule> rulesByInternalId = new HashMap<>();
    private final Map<ConversionType, List<BaseConversionConfig>> configsByType = new HashMap<>();

    public void addAll(List<? extends BaseConversionConfig> configs) {
        for (BaseConversionConfig config : configs) {
            add(config);
        }
    }

    public RuntimeConfigSnapshot build() {
        Map<ResourceLocation, List<CompiledConversionRule>> immutableByItem = immutableRuleLists(rulesByItem);
        Map<ConversionType, List<BaseConversionConfig>> immutableByType = immutableLists(configsByType);
        Map<ResourceLocation, Integer> maxComplexity = new HashMap<>();
        immutableByItem.forEach((itemId, rules) -> maxComplexity.put(itemId, rules.stream()
                .mapToInt(CompiledConversionRule::complexity)
                .max()
                .orElse(0)));

        return new RuntimeConfigSnapshot(
                Map.copyOf(immutableByItem),
                Map.copyOf(rulesByInternalId),
                Map.copyOf(maxComplexity),
                Map.copyOf(immutableByType));
    }

    private void add(BaseConversionConfig config) {
        ConversionRuleCompiler.CompiledRuleResult result = compiler.compile(config);
        if (!result.isValid()) {
            return;
        }

        CompiledConversionRule rule = result.rule();
        for (ResourceLocation itemId : result.sourceItemIds()) {
            rulesByItem.computeIfAbsent(itemId, ignored -> new ArrayList<>()).add(rule);
        }

        rulesByInternalId.put(rule.internalId(), rule);
        ConversionType type = config.getConversionType();
        if (type != null) {
            configsByType.computeIfAbsent(type, ignored -> new ArrayList<>()).add(config);
        }
    }

    private static <K> Map<K, List<BaseConversionConfig>> immutableLists(
            Map<K, List<BaseConversionConfig>> source
    ) {
        Map<K, List<BaseConversionConfig>> result = new HashMap<>();
        source.forEach((key, configs) -> result.put(key, List.copyOf(configs)));
        return result;
    }

    private static Map<ResourceLocation, List<CompiledConversionRule>> immutableRuleLists(
            Map<ResourceLocation, List<CompiledConversionRule>> source
    ) {
        Map<ResourceLocation, List<CompiledConversionRule>> result = new HashMap<>();
        source.forEach((key, rules) -> result.put(key, rules.stream()
                .sorted((left, right) -> Integer.compare(right.complexity(), left.complexity()))
                .toList()));
        return result;
    }
}
