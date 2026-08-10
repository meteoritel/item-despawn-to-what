package com.meteorite.itemdespawntowhat.server.conversion;

import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.config.runtime.CompiledConversionRule;
import net.minecraft.core.registries.BuiltInRegistries;
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

    public static void trackIfEligible(ItemEntity itemEntity, ItemConversionStateAccess state) {
        if (itemEntity.getTags().contains(Constants.CHECK_LOCK_TAG)) {
            state.clearConversionState(itemEntity);
            return;
        }
        if (state.isTracked(itemEntity)) {
            return;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());
        if (!ConfigExtractorManager.hasAnyConfigs(itemId)) {
            return;
        }

        state.setTracked(itemEntity, true);
        state.resetProgress(itemEntity);
        LOGGER.debug("Marked item {} for conversion checks", itemId);
    }

    public static boolean shouldCheck(ServerLevel level) {
        return level.getGameTime() % CHECK_INTERVAL_TICKS == 0;
    }

    public static void tickTrackedItem(
            ItemEntity itemEntity,
            ItemConversionStateAccess state,
            int entityLifespan
    ) {
        if (!(itemEntity.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!state.isTracked(itemEntity) || itemEntity.getTags().contains(Constants.CHECK_LOCK_TAG)) {
            return;
        }
        if (!shouldCheck(serverLevel)) {
            return;
        }

        processItemEntity(itemEntity, serverLevel, state, entityLifespan);
    }

    private static void processItemEntity(
            ItemEntity itemEntity,
            ServerLevel serverLevel,
            ItemConversionStateAccess state,
            int entityLifespan
    ) {
        ItemStack itemStack = itemEntity.getItem();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        String selectedConfigId = state.getSelectedConfigId(itemEntity);
        CompiledConversionRule selectedRule = selectedConfigId.isEmpty()
                ? null
                : ConfigExtractorManager.getRuleByInternalId(selectedConfigId);

        if (selectedRule == null) {
            if (!selectedConfigId.isEmpty()) {
                state.resetProgress(itemEntity);
            }
            selectedRule = selectBestMatchingRule(itemEntity, serverLevel, itemId);
            if (selectedRule == null) {
                return;
            }
            state.setSelectedConfigId(itemEntity, selectedRule.internalId());
        } else if (selectedRule.complexity() < ConfigExtractorManager.getMaxComplexityForItem(itemId)) {
            CompiledConversionRule bestRule = selectBestMatchingRule(itemEntity, serverLevel, itemId);
            if (bestRule == null) {
                state.resetProgress(itemEntity);
                return;
            }
            if (!bestRule.internalId().equals(selectedConfigId)) {
                selectedRule = bestRule;
                state.setSelectedConfigId(itemEntity, selectedRule.internalId());
                state.setCheckTimer(itemEntity, 0);
                LOGGER.debug("Switched item {} to higher-complexity config {}",
                        itemId, selectedRule.internalId());
            }
        }

        if (itemStack.getCount() < selectedRule.sourceMultiple()) {
            return;
        }

        if (!selectedRule.matches(itemEntity, serverLevel)) {
            state.resetProgress(itemEntity);
            return;
        }

        int currentTimer = state.getCheckTimer(itemEntity);
        int newTimer = currentTimer == Integer.MAX_VALUE ? Integer.MAX_VALUE : currentTimer + 1;
        state.setCheckTimer(itemEntity, newTimer);
        LOGGER.debug("Item {} passed conversion check ({}/{})",
                itemId, newTimer, selectedRule.conversionTime());

        int safeLifespan = Math.max(CHECK_INTERVAL_TICKS, entityLifespan);
        boolean timerReached = newTimer >= selectedRule.conversionTime();
        boolean aboutToExpire = itemEntity.getAge() > safeLifespan - CHECK_INTERVAL_TICKS;
        if (!timerReached && !aboutToExpire) {
            return;
        }

        if (selectedRule.isResultLimitExceeded(itemEntity)) {
            state.resetProgress(itemEntity);
            LOGGER.debug("Conversion result limit reached for item {}", itemId);
            return;
        }

        if (performConversion(itemEntity, selectedRule, serverLevel, state)) {
            state.clearConversionState(itemEntity);
        }
    }

    private static CompiledConversionRule selectBestMatchingRule(
            ItemEntity itemEntity,
            ServerLevel serverLevel,
            ResourceLocation itemId
    ) {
        List<CompiledConversionRule> rules = ConfigExtractorManager.getRulesForItem(itemId);
        CompiledConversionRule bestRule = null;
        int bestComplexity = -1;

        for (CompiledConversionRule rule : rules) {
            if (rule.isResultLimitExceeded(itemEntity) || !rule.matches(itemEntity, serverLevel)) {
                continue;
            }

            int complexity = rule.complexity();
            if (complexity > bestComplexity) {
                bestComplexity = complexity;
                bestRule = rule;
            }
        }

        if (bestRule != null) {
            LOGGER.debug("Selected config {} with complexity {} for item {}",
                    bestRule.internalId(), bestComplexity, itemId);
        }
        return bestRule;
    }

    private static boolean performConversion(
            ItemEntity itemEntity,
            CompiledConversionRule rule,
            ServerLevel serverLevel,
            ItemConversionStateAccess state
    ) {
        if (state.isConversionLocked(itemEntity)) {
            LOGGER.debug("Conversion already in progress for item {}", itemEntity.getUUID());
            return false;
        }

        try {
            state.setConversionLocked(itemEntity, true);
            return rule.performConversion(itemEntity, serverLevel);
        } finally {
            state.setConversionLocked(itemEntity, false);
        }
    }
}
