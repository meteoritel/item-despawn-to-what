package com.meteorite.itemdespawntowhat.server.event;

import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.condition.checker.ConditionChecker;
import com.meteorite.itemdespawntowhat.server.task.LevelTaskManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

import static com.meteorite.itemdespawntowhat.ItemDespawnToWhat.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public class ItemConversionEvent {
    private static final Logger LOGGER = LogManager.getLogger();
    // 标签
    public static final String CHECK_TAG = MOD_ID + ":needs_check";
    public static final String TIMER_TAG = MOD_ID + ":check_timer";
    public static final String SELECTED_CONFIG_TAG = MOD_ID + ":selected_config";
    public static final String CONVERSION_LOCK_TAG = MOD_ID + ":conversion_lock";
    public static final String CHECK_LOCK_TAG = Constants.CHECK_LOCK_TAG;

    // 检查间隔（每20tick检查一次）
    private static final int CHECK_INTERVAL = 20;
    // 防止刷物品的全局锁（按物品UUID记录转化状态）
    private static final Set<UUID> CONVERSION_IN_PROGRESS = Collections.newSetFromMap(new WeakHashMap<>());

    // ---------- 掉落物加入世界后的逻辑 ---------- //
    // 订阅实体加入世界事件
    @SubscribeEvent
    public static void onItemSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        if (!(event.getEntity() instanceof ItemEntity itemEntity)) {
            return;
        }

        if (itemEntity.getPersistentData().contains(CHECK_TAG) ||
                itemEntity.getTags().contains(CHECK_LOCK_TAG)) {
            return ;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());

        if (ConfigExtractorManager.hasAnyConfigs(itemId)) {
            // 标记为需要检查
            itemEntity.getPersistentData().putBoolean(CHECK_TAG, true);
            // 初始化计时器为0
            itemEntity.getPersistentData().putInt(TIMER_TAG, 0);
            LOGGER.debug("Marked item {} for condition checking, tag", itemId);
        }
    }

    // 玩家死亡掉落物添加锁定，不会进行转化
    @SubscribeEvent
    public  static void onLivingDrops(LivingDropsEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        for (ItemEntity itemEntity : event.getDrops()) {
            itemEntity.addTag(CHECK_LOCK_TAG);
        }
    }

    // 转化的主订阅事件
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel) ) {
            return;
        }
        // 执行预先加入的延迟任务，如果有的话
        LevelTaskManager.tick(serverLevel);

        // 每20tick检查一次
        if (serverLevel.getGameTime() % CHECK_INTERVAL != 0) {
            return;
        }

        List<ItemEntity> candidates = collectTaggedItemEntities(serverLevel);
        for (ItemEntity itemEntity : candidates) {
            processItemEntity(itemEntity, serverLevel);
        }
    }

    // ========== 核心处理逻辑 ==========//

    private static void processItemEntity(ItemEntity itemEntity, ServerLevel serverLevel) {
        ItemStack itemStack = itemEntity.getItem();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());

        // 检查是否有选定的配置
        String selectedConfigId = itemEntity.getPersistentData().getString(SELECTED_CONFIG_TAG);
        BaseConversionConfig selectedConfig = selectedConfigId.isEmpty()
                ? null
                : ConfigExtractorManager.getConfigByInternalId(selectedConfigId);

        // 如果没有选定配置，尝试选择第一个匹配的配置
        if (selectedConfig == null) {
            selectedConfig = selectFirstMatchingConfig(itemEntity, serverLevel, itemId);
            if (selectedConfig == null) {
                return;
            }
            selectedConfigId = selectedConfig.getInternalId();
            itemEntity.getPersistentData().putString(SELECTED_CONFIG_TAG, selectedConfigId);
        }

        // 当前物品实体的数量至少满足最低转化需要的数量
        if (itemEntity.getItem().getCount() < selectedConfig.getSourceMultiple()) {
            return;
        }

        // 获取条件检查器并验证
        ConditionChecker checker = selectedConfig.getConditionChecker();
        if (checker == null) {
            LOGGER.warn("No condition checker found for config: {}", selectedConfigId);
            itemEntity.getPersistentData().remove(SELECTED_CONFIG_TAG);
            return;
        }

        // 条件不再满足时，重置计时器并清除选定配置tag
        if (!checker.checkCondition(itemEntity, serverLevel)) {
            itemEntity.getPersistentData().putInt(TIMER_TAG, 0);
            itemEntity.getPersistentData().remove(SELECTED_CONFIG_TAG);
            return;
        }

        // 条件满足，更新计时器
        int newTimer = itemEntity.getPersistentData().getInt(TIMER_TAG) + 1;
        itemEntity.getPersistentData().putInt(TIMER_TAG, newTimer);

        int maxChecks = selectedConfig.getConversionTime();
        LOGGER.debug("Condition check passed for item {} (timer: {}/{})", itemId, newTimer, maxChecks);

        // 判断是否到达转化时机：计时达标，或物品即将在下一检查周期消失
        boolean timerReached = newTimer >= maxChecks;
        boolean aboutToExpire = itemEntity.getAge() > itemStack.getEntityLifespan(serverLevel) - CHECK_INTERVAL;
        if (!timerReached && !aboutToExpire) return;

        // 超过上限，清除计时器，并清除选定的配置
        if (selectedConfig.isResultLimitExceeded(itemEntity)) {
            itemEntity.getPersistentData().putInt(TIMER_TAG, 0);
            itemEntity.getPersistentData().remove(SELECTED_CONFIG_TAG);
            LOGGER.debug("Conversion failed, the result number is exceeded！");
            return;
        }

        // 执行转化
        performConversion(itemEntity, selectedConfig, serverLevel);

        // 清除检查标记
        itemEntity.getPersistentData().remove(CHECK_TAG);
        itemEntity.getPersistentData().remove(TIMER_TAG);
        itemEntity.getPersistentData().remove(SELECTED_CONFIG_TAG);
    }

    // 当物品查找到满足的第一个条件时，便不会再检测其他条件，直到该条件不再满足
    private static BaseConversionConfig selectFirstMatchingConfig(ItemEntity itemEntity, ServerLevel serverLevel, ResourceLocation itemId) {
        List<BaseConversionConfig> configs = ConfigExtractorManager.getAllConfigsForItem(itemId);
        if (configs.isEmpty()) {
            return null;
        }

        // 查找第一个满足条件的配置
        for (BaseConversionConfig config : configs) {
            String configId = config.getInternalId();
            ConditionChecker checker = config.getConditionChecker();

            // 所选择的配置不应已经超过上限，并且条件检查器不为空且条件符合
            if (!config.isResultLimitExceeded(itemEntity) &&
                    checker != null &&
                    checker.checkCondition(itemEntity, serverLevel)) {
                LOGGER.debug("Selected config {} for item {}", configId, itemId);
                return config;
            }
        }
        return null;
    }

    // 转化主逻辑，添加锁，防止同一物品被多次转化。使用父类构造多态，用来兼容未来更多配置
    private static void performConversion(ItemEntity itemEntity, BaseConversionConfig config, ServerLevel serverLevel) {
        UUID itemUuid = itemEntity.getUUID();

        if (itemEntity.getPersistentData().getBoolean(CONVERSION_LOCK_TAG) ||
                CONVERSION_IN_PROGRESS.contains(itemUuid)) {
            LOGGER.debug("Conversion already in progress for item: {}", itemUuid);
            return;
        }

        try {
            // 设置转化锁，防止多次转化
            itemEntity.getPersistentData().putBoolean(CONVERSION_LOCK_TAG, true);
            CONVERSION_IN_PROGRESS.add(itemUuid);

            // 转化逻辑在各个配置子类中
            config.performConversion(itemEntity, serverLevel);
        } finally {
            itemEntity.getPersistentData().remove(CONVERSION_LOCK_TAG);
            CONVERSION_IN_PROGRESS.remove(itemUuid);
        }
    }

    // ========== 辅助方法 ========== //
    // 收集当前 Level 中所有需要检查、存活、已加载、未锁定且尚未到期的 ItemEntity
    private static List<ItemEntity> collectTaggedItemEntities(ServerLevel level) {
        List<ItemEntity> result = new ArrayList<>();

        level.getEntities(EntityType.ITEM,
                itemEntity -> itemEntity.isAlive()
                        && level.isLoaded(itemEntity.blockPosition())
                        && itemEntity.getPersistentData().getBoolean(CHECK_TAG)
                        && !itemEntity.getTags().contains(CHECK_LOCK_TAG)
                        && itemEntity.getAge() < itemEntity.getItem().getEntityLifespan(level), result);

        return result;
    }
}