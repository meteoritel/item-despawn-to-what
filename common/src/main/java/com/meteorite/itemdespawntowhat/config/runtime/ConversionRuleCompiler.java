package com.meteorite.itemdespawntowhat.config.runtime;

import com.meteorite.itemdespawntowhat.condition.ConditionCheckerUtil;
import com.meteorite.itemdespawntowhat.condition.ConditionContext;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 校验并解析序列化配置，生成可直接查询的运行时规则。
 */
public final class ConversionRuleCompiler {
    private static final Logger LOGGER = LogManager.getLogger();

    public CompiledRuleResult compile(BaseConversionConfig config) {
        if (config == null || !config.shouldProcess()) {
            return CompiledRuleResult.invalid();
        }

        config.initCache();
        if (!config.isCacheValid()) {
            LOGGER.warn("Skipping config with invalid result cache: item={}, result={}",
                    config.getItemId(), config.getResultId());
            return CompiledRuleResult.invalid();
        }

        List<ResourceLocation> sourceItemIds = resolveSourceItems(config);
        if (sourceItemIds.isEmpty()) {
            return CompiledRuleResult.invalid();
        }

        ConditionContext context = new ConditionContext(
                config.getDimension(),
                config.isNeedOutdoor(),
                config.getSurroundingBlocks(),
                config.getCatalystItems(),
                config.getSourceMultiple(),
                config.getInnerFluid());
        CompiledConversionRule rule = new CompiledConversionRule(
                config,
                ConditionCheckerUtil.buildCombinedChecker(context));
        return new CompiledRuleResult(rule, sourceItemIds);
    }

    private List<ResourceLocation> resolveSourceItems(BaseConversionConfig config) {
        if (!config.isTagMode()) {
            ResourceLocation itemId = SafeParseUtil.parseResourceLocation(config.getItemId());
            return itemId == null ? List.of() : List.of(itemId);
        }

        config.expandTagItems();
        if (config.getTagItems().isEmpty()) {
            LOGGER.warn("Tag '{}' resolved to no items, config will be skipped", config.getItemId());
            return List.of();
        }

        List<ResourceLocation> itemIds = new ArrayList<>();
        for (Item item : config.getTagItems()) {
            itemIds.add(BuiltInRegistries.ITEM.getKey(item));
        }
        return List.copyOf(itemIds);
    }

    /**
     * 保存编译后的规则及其展开后的源物品索引。
     */
    public record CompiledRuleResult(CompiledConversionRule rule, List<ResourceLocation> sourceItemIds) {
        private static CompiledRuleResult invalid() {
            return new CompiledRuleResult(null, List.of());
        }

        public boolean isValid() {
            return rule != null;
        }
    }
}
