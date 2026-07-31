package com.meteorite.itemdespawntowhat.server.event;

import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.condition.checker.ConditionChecker;
import com.meteorite.itemdespawntowhat.server.task.LevelTaskManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

/**
 * Fabric 服务端物品转换事件与周期检查入口。
 */
public class ItemConversionEvent {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Marker LOG_MARKER = MarkerManager.getMarker(ItemDespawnToWhat.MOD_ID);
    private static final int CHECK_INTERVAL = 20;

    public static final String CHECK_LOCK_TAG = Constants.CHECK_LOCK_TAG;

    private static boolean registered;

    // 转化的逻辑写在物品实体自己的tick中，世界tick仅用来打标记
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        // 实体加入世界事件
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (world.isClientSide() || !(entity instanceof ItemEntity itemEntity)) {
                return;
            }

            ItemConversionState state = (ItemConversionState) itemEntity;
            if (itemEntity.getTags().contains(CHECK_LOCK_TAG)) {
                state.itemdespawntowhat$clearConversionState();
                return;
            }

            if (state.itemdespawntowhat$isTracked()) {
                return;
            }

            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());
            if (!ConfigExtractorManager.hasAnyConfigs(itemId)) {
                return;
            }

            state.itemdespawntowhat$setTracked(true);
            state.itemdespawntowhat$setCheckTimer(0);
            state.itemdespawntowhat$setSelectedConfigId("");
            LOGGER.debug(LOG_MARKER, "已标记物品待条件检查: {}", itemId);
        });

        // 当前tick结束执行延迟事件tick
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world instanceof ServerLevel serverLevel) {
                LevelTaskManager.tick(serverLevel);
            }
        });
    }

    public static void tickTrackedItem(ItemEntity itemEntity) {
        if (!(itemEntity.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ItemConversionState state = (ItemConversionState) itemEntity;
        if (!state.itemdespawntowhat$isTracked() || itemEntity.getTags().contains(CHECK_LOCK_TAG)) {
            return;
        }

        if (serverLevel.getGameTime() % CHECK_INTERVAL != 0) {
            return;
        }

        processItemEntity(itemEntity, serverLevel, state);
    }

    private static void processItemEntity(ItemEntity itemEntity, ServerLevel serverLevel, ItemConversionState state) {
        ItemStack itemStack = itemEntity.getItem();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());

        String selectedConfigId = state.itemdespawntowhat$getSelectedConfigId();
        BaseConversionConfig selectedConfig = selectedConfigId.isEmpty()
                ? null
                : ConfigExtractorManager.getConfigByInternalId(selectedConfigId);

        if (selectedConfig == null) {
            // 没有选定配置，选择复杂度最高的匹配配置
            selectedConfig = selectBestMatchingConfig(itemEntity, serverLevel, itemId);
            if (selectedConfig == null) {
                return;
            }
            state.itemdespawntowhat$setSelectedConfigId(selectedConfig.getInternalId());
        } else {
            // 已有选中配置，检查是否可能被更高复杂度配置替代
            int maxComplexity = ConfigExtractorManager.getMaxComplexityForItem(itemId);
            if (selectedConfig.computeComplexity() < maxComplexity) {
                // 未达到最高复杂度，每次循环重新扫描以发现更优配置
                BaseConversionConfig bestConfig = selectBestMatchingConfig(itemEntity, serverLevel, itemId);
                if (bestConfig == null) {
                    state.itemdespawntowhat$setCheckTimer(0);
                    state.itemdespawntowhat$setSelectedConfigId("");
                    return;
                }
                if (!bestConfig.getInternalId().equals(selectedConfigId)) {
                    // 切换到更高复杂度配置，重置计时器
                    selectedConfig = bestConfig;
                    state.itemdespawntowhat$setSelectedConfigId(selectedConfig.getInternalId());
                    state.itemdespawntowhat$setCheckTimer(0);
                    LOGGER.debug(LOG_MARKER, "切换到更高复杂度配置 {} (物品: {})", selectedConfig.getInternalId(), itemId);
                }
            }
            // 复杂度已达上限，锁定模式：仅依赖下方的条件检查
        }

        if (itemStack.getCount() < selectedConfig.getSourceMultiple()) {
            return;
        }

        ConditionChecker checker = selectedConfig.getConditionChecker();
        if (checker == null) {
            LOGGER.warn(LOG_MARKER, "未找到条件检查器，配置: {}", selectedConfig.getInternalId());
            state.itemdespawntowhat$setSelectedConfigId("");
            return;
        }

        if (!checker.checkCondition(itemEntity, serverLevel)) {
            state.itemdespawntowhat$setCheckTimer(0);
            state.itemdespawntowhat$setSelectedConfigId("");
            return;
        }

        int newTimer = state.itemdespawntowhat$getCheckTimer() + 1;
        state.itemdespawntowhat$setCheckTimer(newTimer);

        int maxChecks = selectedConfig.getConversionTime();
        LOGGER.debug(LOG_MARKER, "物品条件检查通过: {} (计时: {}/{})", itemId, newTimer, maxChecks);

        boolean timerReached = newTimer >= maxChecks;
        boolean aboutToExpire = itemEntity.getAge() > 6000 - CHECK_INTERVAL;
        if (!timerReached && !aboutToExpire) {
            return;
        }

        if (selectedConfig.isResultLimitExceeded(itemEntity)) {
            state.itemdespawntowhat$setCheckTimer(0);
            state.itemdespawntowhat$setSelectedConfigId("");
            LOGGER.debug(LOG_MARKER, "转化失败，结果数量已达上限");
            return;
        }

        if (performConversion(itemEntity, selectedConfig, serverLevel, state)) {
            state.itemdespawntowhat$clearConversionState();
        }
    }

    private static BaseConversionConfig selectBestMatchingConfig(ItemEntity itemEntity, ServerLevel serverLevel, ResourceLocation itemId) {
        java.util.List<BaseConversionConfig> configs = ConfigExtractorManager.getAllConfigsForItem(itemId);
        if (configs.isEmpty()) {
            return null;
        }

        BaseConversionConfig bestConfig = null;
        int bestComplexity = -1;

        for (BaseConversionConfig config : configs) {
            ConditionChecker checker = config.getConditionChecker();
            if (!config.isResultLimitExceeded(itemEntity) && checker != null && checker.checkCondition(itemEntity, serverLevel)) {
                int complexity = config.computeComplexity();
                if (complexity > bestComplexity) {
                    bestComplexity = complexity;
                    bestConfig = config;
                }
            }
        }

        if (bestConfig != null) {
            LOGGER.debug(LOG_MARKER, "已为物品 {} 选定最佳配置: {} (复杂度: {})", itemId, bestConfig.getInternalId(), bestComplexity);
        }
        return bestConfig;
    }

    private static boolean performConversion(ItemEntity itemEntity, BaseConversionConfig config, ServerLevel serverLevel, ItemConversionState state) {
        if (state.itemdespawntowhat$isConversionLocked()) {
            LOGGER.debug(LOG_MARKER, "物品正在转化中，跳过: {}", itemEntity.getUUID());
            return false;
        }

        try {
            state.itemdespawntowhat$setConversionLocked(true);
            return config.performConversion(itemEntity, serverLevel);
        } finally {
            state.itemdespawntowhat$setConversionLocked(false);
        }
    }
}
