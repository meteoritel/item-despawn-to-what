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

/**
 * NeoForge 服务端物品转换事件与周期检查入口。
 */
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

        if (selectedConfig == null) {
            // 没有选定配置，选择复杂度最高的匹配配置
            selectedConfig = selectBestMatchingConfig(itemEntity, serverLevel, itemId);
            if (selectedConfig == null) {
                return;
            }
            selectedConfigId = selectedConfig.getInternalId();
            itemEntity.getPersistentData().putString(SELECTED_CONFIG_TAG, selectedConfigId);
        } else {
            // 已有选中配置，检查是否可能被更高复杂度配置替代
            int maxComplexity = ConfigExtractorManager.getMaxComplexityForItem(itemId);
            if (selectedConfig.computeComplexity() < maxComplexity) {
                // 未达到最高复杂度，每次循环重新扫描以发现更优配置
                BaseConversionConfig bestConfig = selectBestMatchingConfig(itemEntity, serverLevel, itemId);
                if (bestConfig == null) {
                    itemEntity.getPersistentData().putInt(TIMER_TAG, 0);
                    itemEntity.getPersistentData().remove(SELECTED_CONFIG_TAG);
                    return;
                }
                if (!bestConfig.getInternalId().equals(selectedConfigId)) {
                    // 切换到更高复杂度配置，重置计时器
                    selectedConfig = bestConfig;
                    selectedConfigId = bestConfig.getInternalId();
                    itemEntity.getPersistentData().putString(SELECTED_CONFIG_TAG, selectedConfigId);
                    itemEntity.getPersistentData().putInt(TIMER_TAG, 0);
                    LOGGER.debug("Switched to higher complexity config {} for item {}", selectedConfigId, itemId);
                }
            }
            // 复杂度已达上限，锁定模式：仅依赖下方的条件检查
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

        // 只有实际完成转化后才清除检查标记
        if (performConversion(itemEntity, selectedConfig, serverLevel)) {
            itemEntity.getPersistentData().remove(CHECK_TAG);
            itemEntity.getPersistentData().remove(TIMER_TAG);
            itemEntity.getPersistentData().remove(SELECTED_CONFIG_TAG);
        }
    }

    // 选择当前满足条件的配置中复杂度最高的那个
    private static BaseConversionConfig selectBestMatchingConfig(ItemEntity itemEntity, ServerLevel serverLevel, ResourceLocation itemId) {
        List<BaseConversionConfig> configs = ConfigExtractorManager.getAllConfigsForItem(itemId);
        if (configs.isEmpty()) {
            return null;
        }

        BaseConversionConfig bestConfig = null;
        int bestComplexity = -1;

        for (BaseConversionConfig config : configs) {
            ConditionChecker checker = config.getConditionChecker();
            if (!config.isResultLimitExceeded(itemEntity) &&
                    checker != null &&
                    checker.checkCondition(itemEntity, serverLevel)) {
                int complexity = config.computeComplexity();
                if (complexity > bestComplexity) {
                    bestComplexity = complexity;
                    bestConfig = config;
                }
            }
        }

        if (bestConfig != null) {
            LOGGER.debug("Selected best config {} (complexity {}) for item {}", bestConfig.getInternalId(), bestComplexity, itemId);
        }
        return bestConfig;
    }

    // 转化主逻辑，添加锁，防止同一物品被多次转化。使用父类构造多态，用来兼容未来更多配置
    private static boolean performConversion(ItemEntity itemEntity, BaseConversionConfig config, ServerLevel serverLevel) {
        UUID itemUuid = itemEntity.getUUID();

        if (itemEntity.getPersistentData().getBoolean(CONVERSION_LOCK_TAG) ||
                CONVERSION_IN_PROGRESS.contains(itemUuid)) {
            LOGGER.debug("Conversion already in progress for item: {}", itemUuid);
            return false;
        }

        try {
            // 设置转化锁，防止多次转化
            itemEntity.getPersistentData().putBoolean(CONVERSION_LOCK_TAG, true);
            CONVERSION_IN_PROGRESS.add(itemUuid);

            // 转化逻辑在各个配置子类中
            return config.performConversion(itemEntity, serverLevel);
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
