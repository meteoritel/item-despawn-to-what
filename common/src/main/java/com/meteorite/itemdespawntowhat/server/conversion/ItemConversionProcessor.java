package com.meteorite.itemdespawntowhat.server.conversion;

import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.condition.checker.ConditionChecker;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
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
        BaseConversionConfig selectedConfig = selectedConfigId.isEmpty()
                ? null
                : ConfigExtractorManager.getConfigByInternalId(selectedConfigId);

        if (selectedConfig == null) {
            if (!selectedConfigId.isEmpty()) {
                state.resetProgress(itemEntity);
            }
            selectedConfig = selectBestMatchingConfig(itemEntity, serverLevel, itemId);
            if (selectedConfig == null) {
                return;
            }
            state.setSelectedConfigId(itemEntity, selectedConfig.getInternalId());
        } else if (selectedConfig.computeComplexity() < ConfigExtractorManager.getMaxComplexityForItem(itemId)) {
            BaseConversionConfig bestConfig = selectBestMatchingConfig(itemEntity, serverLevel, itemId);
            if (bestConfig == null) {
                state.resetProgress(itemEntity);
                return;
            }
            if (!bestConfig.getInternalId().equals(selectedConfigId)) {
                selectedConfig = bestConfig;
                state.setSelectedConfigId(itemEntity, selectedConfig.getInternalId());
                state.setCheckTimer(itemEntity, 0);
                LOGGER.debug("Switched item {} to higher-complexity config {}",
                        itemId, selectedConfig.getInternalId());
            }
        }

        if (itemStack.getCount() < selectedConfig.getSourceMultiple()) {
            return;
        }

        ConditionChecker checker = selectedConfig.getConditionChecker();
        if (checker == null) {
            LOGGER.warn("No condition checker found for config {}", selectedConfig.getInternalId());
            state.resetProgress(itemEntity);
            return;
        }
        if (!checker.checkCondition(itemEntity, serverLevel)) {
            state.resetProgress(itemEntity);
            return;
        }

        int currentTimer = state.getCheckTimer(itemEntity);
        int newTimer = currentTimer == Integer.MAX_VALUE ? Integer.MAX_VALUE : currentTimer + 1;
        state.setCheckTimer(itemEntity, newTimer);
        LOGGER.debug("Item {} passed conversion check ({}/{})",
                itemId, newTimer, selectedConfig.getConversionTime());

        int safeLifespan = Math.max(CHECK_INTERVAL_TICKS, entityLifespan);
        boolean timerReached = newTimer >= selectedConfig.getConversionTime();
        boolean aboutToExpire = itemEntity.getAge() > safeLifespan - CHECK_INTERVAL_TICKS;
        if (!timerReached && !aboutToExpire) {
            return;
        }

        if (selectedConfig.isResultLimitExceeded(itemEntity)) {
            state.resetProgress(itemEntity);
            LOGGER.debug("Conversion result limit reached for item {}", itemId);
            return;
        }

        if (performConversion(itemEntity, selectedConfig, serverLevel, state)) {
            state.clearConversionState(itemEntity);
        }
    }

    private static BaseConversionConfig selectBestMatchingConfig(
            ItemEntity itemEntity,
            ServerLevel serverLevel,
            ResourceLocation itemId
    ) {
        List<BaseConversionConfig> configs = ConfigExtractorManager.getAllConfigsForItem(itemId);
        BaseConversionConfig bestConfig = null;
        int bestComplexity = -1;

        for (BaseConversionConfig config : configs) {
            ConditionChecker checker = config.getConditionChecker();
            if (config.isResultLimitExceeded(itemEntity)
                    || checker == null
                    || !checker.checkCondition(itemEntity, serverLevel)) {
                continue;
            }

            int complexity = config.computeComplexity();
            if (complexity > bestComplexity) {
                bestComplexity = complexity;
                bestConfig = config;
            }
        }

        if (bestConfig != null) {
            LOGGER.debug("Selected config {} with complexity {} for item {}",
                    bestConfig.getInternalId(), bestComplexity, itemId);
        }
        return bestConfig;
    }

    private static boolean performConversion(
            ItemEntity itemEntity,
            BaseConversionConfig config,
            ServerLevel serverLevel,
            ItemConversionStateAccess state
    ) {
        if (state.isConversionLocked(itemEntity)) {
            LOGGER.debug("Conversion already in progress for item {}", itemEntity.getUUID());
            return false;
        }

        try {
            state.setConversionLocked(itemEntity, true);
            return config.performConversion(itemEntity, serverLevel);
        } finally {
            state.setConversionLocked(itemEntity, false);
        }
    }
}
