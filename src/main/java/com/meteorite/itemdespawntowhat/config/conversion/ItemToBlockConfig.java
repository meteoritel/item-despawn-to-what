package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.event.task.PlaceBlockTask;
import com.meteorite.itemdespawntowhat.event.task.LevelTaskManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class ItemToBlockConfig extends BaseConversionConfig{
    // 最大放置半径的限制，默认为6
    @SerializedName("radius_limit")
    private int radius = 6;
    @SerializedName("block_of_item")
    private boolean enableItemBlock = false;

    // 缓存的结果方块实例，不参与序列化
    private transient Block cachedResultBlock;

    public ItemToBlockConfig() {
    }

    public ItemToBlockConfig(String item, String result) {
        super(item, result);
    }

    // ========== 缓存与校验 ========== //
    @Override
    protected void initResultCache() {
        if (enableItemBlock) {
            if (cachedStartItem instanceof BlockItem blockItem) {
                Block block = blockItem.getBlock();
                // 将真实的 resultId 回写
                this.resultId = BuiltInRegistries.BLOCK.getKey(block).toString();
                this.cachedResultBlock = block;
                // LOGGER.debug("enableItemBlock: resolved resultId={} for item={}", resultId, itemId);
            } else {
                LOGGER.warn("enableItemBlock=true but item '{}' is not a BlockItem, config will be rejected", itemId);
            }
        } else {
            // 直接按 resultId 查找
            ResourceLocation resultRl = ResourceLocation.tryParse(resultId != null ? resultId : "");
            Block block = resultRl != null ? BuiltInRegistries.BLOCK.get(resultRl) : Blocks.AIR;
            this.cachedResultBlock = (block != Blocks.AIR) ? block : null;
            if (cachedResultBlock == null) {
                LOGGER.warn("Could not find block for resultId='{}', config will be rejected", resultId);
            }
        }
    }

    @Override
    protected boolean additionalCheck() {
        if (radius < 0) {
            LOGGER.warn("radius_limit must be >= 0, current={}", radius);
            return false;
        }
        return true;
    }

    @Override
    protected boolean isResultIdRequired() {
        return !enableItemBlock;
    }

    public boolean isResultCacheValid() {
        return cachedResultBlock != null;
    }

    // ========== 转化逻辑 ========== //
    @Override
    public void performConversion(ItemEntity itemEntity, ServerLevel serverLevel) {
        int originalStackSize = itemEntity.getItem().getCount();
        int actualConvertCount = computeActualConvertCount(itemEntity, originalStackSize);
        if (actualConvertCount <= 0) {
            LOGGER.debug("No items can be converted to block for {} (catalysts exhausted)", getResultId());
            return;
        }
        // 物品下一tick消失
        itemEntity.makeFakeItem();
        consumeAllOthers(itemEntity, actualConvertCount);
        // 下一tick开始执行延迟放置方块的任务
        // 消耗流体就直接从中心的位置放，不消耗流体就跳过中心的点
        LevelTaskManager.addTask(serverLevel, new PlaceBlockTask(
                itemEntity, this, actualConvertCount, innerFluid.isConsumeFluid()));
    }

    // ========== 结果相关方法 ========== //
    // 当开启了使用物品对应方块，且物品是方块物品，就直接使用对应的方块
    public Block getResultBlock() {
        if (isCacheInitialized()) {
            return cachedResultBlock;
        }
        ResourceLocation rl = ResourceLocation.tryParse(resultId != null ? resultId : "");
        return rl != null ? BuiltInRegistries.BLOCK.get(rl) : Blocks.AIR;
    }

    @Override
    public String getResultDescriptionId() {
        return getResultBlock().getDescriptionId();
    }

    @Override
    public ItemStack getResultIcon() {
        Block block = getResultBlock();
        if (block == null) {
            return new ItemStack(Items.BARRIER);
        }
        return block.asItem() == Items.AIR
                ? new ItemStack(Items.BARRIER)
                : new ItemStack(block.asItem());
    }

    public int getRadius() {
        return radius;
    }
    public void setRadius(int radius) {
        this.radius = radius;
    }
    public boolean isEnableItemBlock() {
        return enableItemBlock;
    }
    public void setEnableItemBlock(boolean enableItemBlock) {
        this.enableItemBlock = enableItemBlock;
    }
}
