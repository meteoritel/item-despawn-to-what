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

public class ItemConversionEvent {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Marker LOG_MARKER = MarkerManager.getMarker(ItemDespawnToWhat.MOD_ID);
    private static final int CHECK_INTERVAL = 20;

    public static final String CHECK_LOCK_TAG = Constants.CHECK_LOCK_TAG;

    private static boolean registered;

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

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
            selectedConfig = selectFirstMatchingConfig(itemEntity, serverLevel, itemId);
            if (selectedConfig == null) {
                return;
            }
            state.itemdespawntowhat$setSelectedConfigId(selectedConfig.getInternalId());
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

    private static BaseConversionConfig selectFirstMatchingConfig(ItemEntity itemEntity, ServerLevel serverLevel, ResourceLocation itemId) {
        java.util.List<BaseConversionConfig> configs = ConfigExtractorManager.getAllConfigsForItem(itemId);
        if (configs.isEmpty()) {
            return null;
        }

        for (BaseConversionConfig config : configs) {
            ConditionChecker checker = config.getConditionChecker();
            if (!config.isResultLimitExceeded(itemEntity) && checker != null && checker.checkCondition(itemEntity, serverLevel)) {
                LOGGER.debug(LOG_MARKER, "已为物品 {} 选定配置: {}", itemId, config.getInternalId());
                return config;
            }
        }
        return null;
    }

    private static boolean performConversion(ItemEntity itemEntity, BaseConversionConfig config, ServerLevel serverLevel, ItemConversionState state) {
        if (state.itemdespawntowhat$isConversionLocked()) {
            LOGGER.debug(LOG_MARKER, "物品正在转化中，跳过: {}", itemEntity.getUUID());
            return false;
        }

        try {
            state.itemdespawntowhat$setConversionLocked(true);
            config.performConversion(itemEntity, serverLevel);
            return true;
        } finally {
            state.itemdespawntowhat$setConversionLocked(false);
        }
    }
}
