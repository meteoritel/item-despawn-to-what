package com.meteorite.itemdespawntowhat.config;

import com.google.gson.annotations.SerializedName;
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
    private int resultLimit;

    public ItemToItemConfig() {
        super();
        this.configType = ConfigType.ITEM_TO_ITEM;
    }

    public ItemToItemConfig(ResourceLocation item, ResourceLocation resultItem) {
        super(item, resultItem);
        this.resultLimit = DEFAULT_RESULT_LIMIT;
        this.configType = ConfigType.ITEM_TO_ITEM;
    }

    // 物品转化需要保证转化前后结果不同，防止循环转化
    @Override
    public boolean shouldProcess() {
        return super.shouldProcess() && !this.itemId.equals(resultId);
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

        CatalystItems catalyst = getCatalystItems();

        // 物品实体下一tick消失
        itemEntity.makeFakeItem();

        // 根据条件决定是否消耗催化剂
        consumeCatalysts(itemEntity, originalStackSize);

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
                originalStack.getItem().getDescriptionId(), getResultId(),
                resultMultiple, originalStackSize);
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
        return this.countNearbyResult(itemEntity) >= this.getResultLimit() * 64;
    }

    // ========== 结果相关方法 ========== //
    public Item getResultItem() {
        return BuiltInRegistries.ITEM.get(resultId);
    }
    @Override
    public String getResultDescriptionId() {
        return getResultItem().getDescriptionId();
    }
    @Override
    public ItemStack getResultIcon() {
        return BuiltInRegistries.ITEM.getOptional(resultId)
                .map(ItemStack::new).orElseGet(() -> new ItemStack(Items.BARRIER));
    }
    public int getResultLimit() {
        return resultLimit <= 0 ? DEFAULT_RESULT_LIMIT : resultLimit;
    }
    public void setResultLimit(int resultLimit) {
        this.resultLimit = resultLimit;
    }
}
