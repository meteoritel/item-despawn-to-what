package com.meteorite.itemdespawntowhat.config.conversion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ItemToItemConfig extends BaseItemToEntityConfig{
    // 缓存的结果物品实例
    private transient Item cachedResultItem;
    // 缓存结果物品的最大堆叠数，避免重复查找
    private transient int cachedResultMaxStackSize;

    public ItemToItemConfig() {
    }

    public ItemToItemConfig(String item, String result) {
        super(item, result);
    }

    // ========== 初始化缓存与校验 ========== //
    @Override
    protected void initResultCache() {
        ResourceLocation resultRl = ResourceLocation.tryParse(resultId != null ? resultId : "");
        cachedResultItem = resultRl != null ? BuiltInRegistries.ITEM.get(resultRl) : Items.AIR;
        if (cachedResultItem == Items.AIR) {
            LOGGER.warn("Could not find item for resultId='{}', config will be rejected", resultId);
            cachedResultItem = null;
            cachedResultMaxStackSize = 1;
        } else {
            cachedResultMaxStackSize = cachedResultItem.getDefaultMaxStackSize();
        }
    }

    // 物品转化需要保证转化前后结果不同，防止循环转化
    @Override
    protected boolean additionalCheck() {
        if (itemId.equals(resultId)) {
            LOGGER.warn("Source and result item are the same: {}, this would cause infinite conversion", itemId);
            return false;
        }

        if (resultLimit <= 0) {
            LOGGER.warn("ResultLimit should not be less than 0, current is: {}", resultLimit);
            return false;
        }
        return true;
    }
    // ========== 转化逻辑 ========== //
    @Override
    public void performConversion(ItemEntity itemEntity, ServerLevel serverLevel) {
        Item resultItem = getResultItem();
        BlockPos pos = itemEntity.blockPosition();

        // 获取当前物品堆叠
        ItemStack originalStack = itemEntity.getItem();
        int originalStackSize = originalStack.getCount();
        int resultMultiple = getResultMultiple();

        // 综合催化剂和结果上限，计算实际转化轮数
        int rounds = computeActualRounds(itemEntity, originalStackSize);
        if (rounds <= 0) {
            LOGGER.debug("No items can be converted for {} (catalysts or limit exhausted)", getResultId());
            return;
        }

        int actualConvertCount = rounds * getSourceMultiple();

        // 物品实体下一tick消失
        itemEntity.makeFakeItem();

        // 根据条件消耗催化剂与流体
        consumeAllOthers(itemEntity, actualConvertCount);
        ItemStack resultStack = new ItemStack(resultItem, rounds);
        // 生成结果物品实体（每轮产出 resultMultiple 堆，每堆数量为 rounds）
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

        int itemsRemaining = originalStackSize - actualConvertCount;
        // 添加未转转化完成的返还物品
        addRemainingItems(itemEntity, serverLevel, itemsRemaining);
        LOGGER.debug("Converted to item: {} -> {} ({}x, rounds: {}, consumed: {}/{}, stack remaining: {})",
                originalStack.getItem().getDescriptionId(), getResultId(),
                resultMultiple, rounds, actualConvertCount, originalStackSize, itemsRemaining);
    }

    // 对于物掉落物结果，计算所有的stack堆叠之和
    @Override
    protected int countNearbyResult(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(ItemEntity.class, buildSearchBox(pos), Entity::isAlive)
                .stream()
                .map(ItemEntity::getItem)
                .filter(itemStack -> BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString().equals(resultId))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    // ========== 结果相关方法 ========== //
    public Item getResultItem() {
        if (isCacheInitialized()) {
            return cachedResultItem;
        }
        ResourceLocation resultRl = ResourceLocation.tryParse(resultId != null ? resultId : "");
        return resultRl != null ? BuiltInRegistries.ITEM.get(resultRl) : Items.AIR;
    }

    @Override
    protected int getRawResultLimit() {
        return resultLimit * cachedResultMaxStackSize;
    }

    @Override
    public String getResultDescriptionId() {
        return getResultItem().getDescriptionId();
    }

    @Override
    public ItemStack getResultIcon() {
        return getResultItem().getDefaultInstance();
    }

}
