package com.meteorite.itemdespawntowhat.server.conversion;

import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import com.meteorite.itemdespawntowhat.config.runtime.CompiledConversionRule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * 跨平台物品转换选择、计时与执行核心。
 */
public final class ItemConversionProcessor {
    public static final int CHECK_INTERVAL_TICKS = 20;

    private static final Logger LOGGER = LogManager.getLogger();

    private ItemConversionProcessor() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean shouldCheck(ServerLevel level) {
        return level.getGameTime() % CHECK_INTERVAL_TICKS == 0;
    }

    public static boolean tickTrackedItem(
            ItemEntity itemEntity,
            ConversionTracker.ConversionState state,
            int entityLifespan
    ) {
        if (!(itemEntity.level() instanceof ServerLevel serverLevel)) {
            return true;
        }

        return processItemEntity(itemEntity, serverLevel, state, entityLifespan);
    }

    private static boolean processItemEntity(
            ItemEntity itemEntity,
            ServerLevel serverLevel,
            ConversionTracker.ConversionState state,
            int entityLifespan
    ) {
        ItemStack itemStack = itemEntity.getItem();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        if (!ConfigExtractorManager.hasAnyConfigs(itemId)) {
            return true;
        }
        String selectedConfigId = state.selectedConfigId();
        CompiledConversionRule selectedRule = selectedConfigId.isEmpty()
                ? null
                : ConfigExtractorManager.getRuleByInternalId(selectedConfigId);

        if (selectedRule == null) {
            if (!selectedConfigId.isEmpty()) {
                state.resetProgress();
            }
            selectedRule = selectBestMatchingRule(itemEntity, serverLevel, itemId);
            if (selectedRule == null) {
                return false;
            }
            state.setSelectedConfigId(selectedRule.internalId());
        } else if (hasHigherRankedRule(itemId, selectedRule)) {
            CompiledConversionRule bestRule = selectHigherMatchingRule(
                    itemEntity, serverLevel, itemId, selectedRule);
            if (bestRule != null) {
                selectedRule = bestRule;
                state.setSelectedConfigId(selectedRule.internalId());
                state.setCheckTimer(0);
                LOGGER.debug("Switched item {} to higher-priority config {}",
                        itemId, selectedRule.internalId());
            }
        }

        if (itemStack.getCount() < selectedRule.sourceMultiple()) {
            return false;
        }

        if (!selectedRule.matches(itemEntity, serverLevel)) {
            state.resetProgress();
            return false;
        }

        int currentTimer = state.checkTimer();
        int newTimer = currentTimer == Integer.MAX_VALUE ? Integer.MAX_VALUE : currentTimer + 1;
        state.setCheckTimer(newTimer);
        LOGGER.debug("Item {} passed conversion check ({}/{})",
                itemId, newTimer, selectedRule.conversionTime());

        int safeLifespan = Math.max(CHECK_INTERVAL_TICKS, entityLifespan);
        boolean timerReached = newTimer >= selectedRule.conversionTime();
        boolean aboutToExpire = itemEntity.getAge() > safeLifespan - CHECK_INTERVAL_TICKS;
        if (!timerReached && !aboutToExpire) {
            return false;
        }

        if (selectedRule.isResultLimitExceeded(itemEntity)) {
            state.resetProgress();
            LOGGER.debug("Conversion result limit reached for item {}", itemId);
            return false;
        }

        return performConversion(itemEntity, selectedRule, serverLevel, state);
    }

    private static CompiledConversionRule selectBestMatchingRule(
            ItemEntity itemEntity,
            ServerLevel serverLevel,
            ResourceLocation itemId
    ) {
        List<CompiledConversionRule> rules = ConfigExtractorManager.getRulesForItem(itemId);
        for (CompiledConversionRule rule : rules) {
            if (rule.isResultLimitExceeded(itemEntity) || !rule.matches(itemEntity, serverLevel)) {
                continue;
            }
            LOGGER.debug("Selected config {} with priority {} and complexity {} for item {}",
                    rule.internalId(), rule.priority(), rule.complexity(), itemId);
            return rule;
        }
        return null;
    }

    private static boolean hasHigherRankedRule(ResourceLocation itemId, CompiledConversionRule selectedRule) {
        List<CompiledConversionRule> rules = ConfigExtractorManager.getRulesForItem(itemId);
        return !rules.isEmpty() && rules.getFirst() != selectedRule;
    }

    private static CompiledConversionRule selectHigherMatchingRule(
            ItemEntity itemEntity,
            ServerLevel serverLevel,
            ResourceLocation itemId,
            CompiledConversionRule selectedRule
    ) {
        for (CompiledConversionRule rule : ConfigExtractorManager.getRulesForItem(itemId)) {
            if (rule == selectedRule) {
                break;
            }
            if (!rule.isResultLimitExceeded(itemEntity) && rule.matches(itemEntity, serverLevel)) {
                return rule;
            }
        }
        return null;
    }

    private static boolean performConversion(
            ItemEntity itemEntity,
            CompiledConversionRule rule,
            ServerLevel serverLevel,
            ConversionTracker.ConversionState state
    ) {
        if (state.conversionLocked()) {
            LOGGER.debug("Conversion already in progress for item {}", itemEntity.getUUID());
            return false;
        }

        try {
            state.setConversionLocked(true);
            boolean converted = rule.performConversion(itemEntity, serverLevel);
            if (converted) {
                serverLevel.sendParticles(
                        ParticleTypes.HAPPY_VILLAGER,
                        itemEntity.getX(), itemEntity.getY() + 0.25, itemEntity.getZ(),
                        8, 0.25, 0.2, 0.25, 0.02);
            }
            return converted;
        } finally {
            state.setConversionLocked(false);
        }
    }
}
