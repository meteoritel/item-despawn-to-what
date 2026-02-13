package com.meteorite.itemdespawntowhat.event;

import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import com.meteorite.itemdespawntowhat.config.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.ItemToBlockConfig;
import com.meteorite.itemdespawntowhat.config.ItemToEntityConfig;
import com.meteorite.itemdespawntowhat.config.ItemToItemConfig;
import com.meteorite.itemdespawntowhat.util.ConditionChecker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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

    public static final String CHECK_TAG = MOD_ID + ":needs_check";
    public static final String TIMER_TAG = MOD_ID + ":check_timer";
    public static final String SELECTED_CONFIG_TAG = MOD_ID + ":selected_config";
    public static final String CONVERSION_LOCK_TAG = MOD_ID + ":conversion_lock";
    public static final String CHECK_TAG_LOCK = MOD_ID + ":check_lock";

    // 检查间隔（每20tick检查一次）
    private static final int CHECK_INTERVAL = 20;
    // 防止刷物品的全局锁（按物品UUID记录转化状态）
    private static final Set<UUID> CONVERSION_IN_PROGRESS = Collections.newSetFromMap(new WeakHashMap<>());
    // 预分配的列表，减少内存分配
    private static final List<ItemEntity> ITEM_ENTITIES_CACHE = new ArrayList<>(64);

    // ---------- 掉落物加入世界后的逻辑 ---------- //
    // 订阅实体加入世界事件
    @SubscribeEvent
    public static void onItemSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        Entity entity = event.getEntity();
        if (!(entity instanceof ItemEntity itemEntity)) {
            return;
        }

        if (itemEntity.getPersistentData().contains(CHECK_TAG) ||
                itemEntity.getPersistentData().contains(CHECK_TAG_LOCK)) {
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
        if (event.isCanceled()) return;

        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            for (ItemEntity itemEntity : event.getDrops()) {
                itemEntity.getPersistentData().putBoolean(CHECK_TAG_LOCK, true);
            }
        }
    }

    // 转化的主订阅事件
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();

        if (!(level instanceof ServerLevel serverLevel) ) {
            return;
        }
        // 执行预先加入的延迟任务，如果有的话
        LevelTaskManager.tick(serverLevel);

        // 每20tick检查一次
        if (serverLevel.getGameTime() % CHECK_INTERVAL != 0) {
            return;
        }

        collectTaggedItemEntities(serverLevel);

        for (ItemEntity itemEntity : ITEM_ENTITIES_CACHE) {
            processItemEntity(itemEntity, serverLevel);
        }

        ITEM_ENTITIES_CACHE.clear();
    }

    // ========== 核心处理逻辑 ==========//

    private static void processItemEntity(ItemEntity itemEntity, ServerLevel serverLevel) {
        ItemStack itemStack = itemEntity.getItem();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());

        // 检查是否有选定的配置
        String selectedConfigId = itemEntity.getPersistentData().getString(SELECTED_CONFIG_TAG);
        BaseConversionConfig selectedConfig = null;


        if (!selectedConfigId.isEmpty()) {
            selectedConfig = ConfigExtractorManager.getConfigByInternalId(selectedConfigId);
        }

        // 如果没有选定配置，尝试选择第一个匹配的配置
        if (selectedConfig == null) {
            selectedConfig = selectFirstMatchingConfig(itemEntity, serverLevel, itemId);
            if (selectedConfig == null) {
                return;
            }
            selectedConfigId = selectedConfig.getInternalId();
            itemEntity.getPersistentData().putString(SELECTED_CONFIG_TAG, selectedConfigId);
        }

        // 获取条件检查器并验证
        ConditionChecker checker = ConfigExtractorManager.getConditionCheckerForConfig(selectedConfigId);
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
        int timer = itemEntity.getPersistentData().getInt(TIMER_TAG);
        int newTimer = timer + 1;
        itemEntity.getPersistentData().putInt(TIMER_TAG, newTimer);

        int maxConsecutiveChecks = selectedConfig.getConversionTime();
        LOGGER.debug("Condition check passed for item {} (timer: {}/{})", itemId, newTimer, maxConsecutiveChecks);

        // 检查是否达到转化条件
        // 转化时间目前还有点小问题，未来要改成最后一秒进入延迟任务池，然后指定tick后消失再转化
        if ((newTimer >= maxConsecutiveChecks ||
                itemEntity.getAge() > itemStack.getEntityLifespan(serverLevel) - CHECK_INTERVAL)) {
            if (selectedConfig.isResultLimitExceeded(itemEntity)) {
                itemEntity.getPersistentData().putInt(TIMER_TAG, 0);
                itemEntity.getPersistentData().remove(SELECTED_CONFIG_TAG);
                LOGGER.debug("Conversion failed, the result number is exceeded！");
            }
            // 执行转化
            performConversion(itemEntity, selectedConfig, serverLevel);

            // 清除检查标记
            itemEntity.getPersistentData().remove(CHECK_TAG);
            itemEntity.getPersistentData().remove(TIMER_TAG);
            itemEntity.getPersistentData().remove(SELECTED_CONFIG_TAG);
        }
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
            ConditionChecker checker = ConfigExtractorManager.getConditionCheckerForConfig(configId);

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

            // 根据配置类型执行不同的转化逻辑
            switch (config) {
                case ItemToEntityConfig entityConfig -> convertToEntity(itemEntity, entityConfig, serverLevel);
                case ItemToItemConfig itemConfig -> convertToItem(itemEntity, itemConfig, serverLevel);
                case ItemToBlockConfig blockConfig -> convertToBlock(itemEntity, blockConfig, serverLevel);
                default -> LOGGER.warn("Unknown config type: {}", config.getClass().getName());
            }

        } finally {
            itemEntity.getPersistentData().remove(CONVERSION_LOCK_TAG);
            CONVERSION_IN_PROGRESS.remove(itemUuid);
        }
    }

    // 转化为实体逻辑
    private static void convertToEntity(ItemEntity itemEntity,
                                        ItemToEntityConfig config,
                                        ServerLevel serverLevel) {
        ResourceLocation resultEntityId = config.getResultId();
        EntityType<?> entityType = config.getResultEntityType();
        BlockPos pos = itemEntity.blockPosition();

        if (entityType == null) {
            LOGGER.warn("Unknown entity type: {}", resultEntityId);
            return;
        }

        ItemStack originalStack = itemEntity.getItem();
        int originalStackSize = originalStack.getCount();
        int resultMultiple = config.getResultMultiple();

        int currentCount = config.countNearbyResult(itemEntity);
        int maxLimit = config.getResultLimit();

        // 计算可以生成的实体数量和需要返还的物品数量
        int actualEntitiesToSpawn = Math.min(originalStackSize * resultMultiple, maxLimit - currentCount);

        if (actualEntitiesToSpawn <= 0) {
            LOGGER.debug("No capacity for entity conversion of {}", resultEntityId);
            return;
        }

        int itemsUsed = (int) Math.ceil((double) actualEntitiesToSpawn / resultMultiple);
        int itemsRemaining = originalStackSize - itemsUsed;

        LOGGER.debug("Converting to entity: {} -> {} ({} entities, using {} items, {} remaining)",
                originalStack.getItem().getDescriptionId(), resultEntityId,
                actualEntitiesToSpawn, itemsUsed, itemsRemaining);

        // 物品实体下一tick消失
        itemEntity.makeFakeItem();

        // 生成实体
        for (int i = 0; i < actualEntitiesToSpawn; i++) {
            Entity resultEntity = entityType.create(serverLevel);
            if (resultEntity != null) {
                // 稍微分散位置，避免重叠
                double offsetX = (serverLevel.random.nextDouble() - 0.5) * 0.5;
                double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 0.5;

                resultEntity.moveTo(pos.getX() + 0.5 + offsetX, pos.getY(), pos.getZ() + 0.5 + offsetZ, 0, 0);

                // 设置实体年龄（如果需要）
                if (resultEntity instanceof AgeableMob ageable) {
                    ageable.setAge(config.getEntityAge());
                }

                serverLevel.addFreshEntity(resultEntity);
            }
        }

        // 创建返还物品
        if (itemsRemaining > 0) {
            ItemStack returnStack = itemEntity.getItem().copy();
            returnStack.setCount(itemsRemaining);
            ItemEntity returnItem = new ItemEntity(serverLevel, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, returnStack);
            serverLevel.addFreshEntity(returnItem);
        }

    }

    // 转化为物品逻辑
    private static void convertToItem(ItemEntity itemEntity,
                                      ItemToItemConfig config,
                                      ServerLevel serverLevel) {
        Item resultItem = config.hasResultItem();
        BlockPos pos = itemEntity.blockPosition();

        // 获取当前物品堆叠
        ItemStack originalStack = itemEntity.getItem();
        int originalStackSize = originalStack.getCount();
        int resultMultiple = config.getResultMultiple();

        // 物品实体下一tick消失
        itemEntity.makeFakeItem();

        ItemStack resultStack = new ItemStack(resultItem, originalStackSize);

        // 生成结果物品实体（如果倍率大于1，生成多个实体）
        for (int i = 0; i < resultMultiple; i++) {
            ItemEntity resultItemEntity = new ItemEntity(
                    serverLevel,
                    pos.getX() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.3,
                    pos.getY() + 0.1,
                    pos.getZ() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.3,
                    resultStack.copy()
            );

            // 设置一些随机速度，让物品生成时飞起来
            resultItemEntity.setDeltaMovement(
                    (serverLevel.random.nextDouble() - 0.5) * 0.1,
                    0.2,
                    (serverLevel.random.nextDouble() - 0.5) * 0.1
            );
            serverLevel.addFreshEntity(resultItemEntity);
        }
        LOGGER.debug("Converted to item: {} -> {} ({}x, stack size: {})",
                originalStack.getItem().getDescriptionId(), config.getResultId(),
                resultMultiple, originalStackSize);
    }

    // 转化为方块逻辑
    private static void convertToBlock(ItemEntity itemEntity,
                                      ItemToBlockConfig config,
                                      ServerLevel serverLevel){
        // 物品下一tick消失
        itemEntity.makeFakeItem();
        // 下一tick开始执行延迟放置方块的任务
        LevelTaskManager.addTask(serverLevel, new ItemToBlockTask(itemEntity, config));
    }

    // ========== 辅助方法 ========== //

    // 获取所有带有CHECK_TAG标签的、不属于检查锁定、处于tick()状态的itemEntity
    private static void collectTaggedItemEntities(ServerLevel level) {
        // 复用列表，避免每次分配新内存
        ITEM_ENTITIES_CACHE.clear();

        level.getEntities().getAll().forEach(entity -> {
            if (! (entity instanceof ItemEntity itemEntity)) {
                return;
            }

            if (!itemEntity.isAlive() ||
                    !level.isLoaded(itemEntity.blockPosition()) ||
                    !itemEntity.getPersistentData().getBoolean(CHECK_TAG) ||
                    itemEntity.getPersistentData().getBoolean(CHECK_TAG_LOCK)) {
                return;
            }

            if (itemEntity.getAge() < itemEntity.getItem().getEntityLifespan(level) ) {
                ITEM_ENTITIES_CACHE.add(itemEntity);
            }
        });
    }

}