package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

public class ItemToItemConfig extends BaseConversionConfig{

    @SerializedName("result_limit")
    private int resultLimit = 30;

    // 缓存的结果物品实例
    private transient Item cachedResultItem;
    // 缓存结果物品的最大堆叠数，避免重复查找
    private transient int cachedResultMaxStackSize;

    public ItemToItemConfig() {
    }

    public ItemToItemConfig(ResourceLocation item, ResourceLocation result) {
        super(item, result);
    }

    // ========== 初始化缓存与校验 ========== //
    @Override
    protected void initResultCache() {
        cachedResultItem = BuiltInRegistries.ITEM.get(resultId);
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

        // 综合催化剂和结果上限，计算实际能转化的起始物品数量
        int actualConvertCount = computeActualConvertCount(itemEntity, originalStackSize);
        if (actualConvertCount <= 0) {
            LOGGER.debug("No items can be converted for {} (catalysts or limit exhausted)", getResultId());
            return;
        }

        // 物品实体下一tick消失
        itemEntity.makeFakeItem();

        // 根据条件消耗催化剂与流体
        consumeAllOthers(itemEntity, actualConvertCount);
        ItemStack resultStack = new ItemStack(resultItem, actualConvertCount);
        // 生成结果物品实体（每个起始物品产出 resultMultiple 堆，每堆数量为 actualConvertCount）
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
        LOGGER.debug("Converted to item: {} -> {} ({}x, converted: {}/{}, stack remaining: {})",
                originalStack.getItem().getDescriptionId(), getResultId(),
                resultMultiple, actualConvertCount, originalStackSize, itemsRemaining);
    }

    // 对于物掉落物结果，计算所有的stack堆叠之和
    @Override
    public int countNearbyResult(ItemEntity itemEntity) {
        if (!(itemEntity.level() instanceof ServerLevel serverLevel)) {
            return 0;
        }
        AABB box = new AABB(
                itemEntity.getX() - MAX_RADIUS, itemEntity.getY() - MAX_RADIUS, itemEntity.getZ() - MAX_RADIUS,
                itemEntity.getX() + MAX_RADIUS, itemEntity.getY() + MAX_RADIUS, itemEntity.getZ() + MAX_RADIUS);

        return serverLevel.getEntitiesOfClass(ItemEntity.class, box, Entity::isAlive)
                .stream()
                .map(ItemEntity::getItem)
                .filter(itemStack -> BuiltInRegistries.ITEM.getKey(itemStack.getItem()).equals(resultId))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    @Override
    public boolean isResultLimitExceeded(ItemEntity itemEntity) {
        return this.countNearbyResult(itemEntity) >= getResultLimitInItems();
    }

    @Override
    protected int getResultCapacityInStartItems(ItemEntity itemEntity) {
        int remaining = getResultLimitInItems() - countNearbyResult(itemEntity);
        if (remaining <= 0) {
            return 0;
        }

        return remaining / Math.max(1, getResultMultiple());
    }

    private int getResultLimitInItems() {
        return getResultLimit() * cachedResultMaxStackSize;
    }

    // ========== 结果相关方法 ========== //
    public Item getResultItem() {
        if (isCacheInitialized()) {
            return cachedResultItem;
        }
        return BuiltInRegistries.ITEM.get(resultId);
    }

    @Override
    public String getResultDescriptionId() {
        return getResultItem().getDescriptionId();
    }

    @Override
    public ItemStack getResultIcon() {
        return getResultItem().getDefaultInstance();
    }
    public int getResultLimit() {
        return resultLimit;
    }

    public void setResultLimit(int resultLimit) {
        this.resultLimit = resultLimit;
    }
}
