package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.server.task.PlaceBlockTask;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import com.meteorite.itemdespawntowhat.server.task.LevelTaskManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
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
            this.resultId = null;
            this.cachedResultBlock = null;
            return;
        }

        // 直接按 resultId 查找
        ResourceLocation resultRl = SafeParseUtil.parseResourceLocation(resultId);
        Block block = resultRl != null ? BuiltInRegistries.BLOCK.get(resultRl) : Blocks.AIR;
        this.cachedResultBlock = (block != Blocks.AIR) ? block : null;
        if (cachedResultBlock == null) {
            LOGGER.warn("Could not find block for resultId='{}', config will be rejected", resultId);
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

    @Override
    public boolean isCacheValid() {
        return enableItemBlock || cachedResultBlock != null;
    }

    // ========== 转化逻辑 ========== //
    @Override
    public void performConversion(ItemEntity itemEntity, ServerLevel serverLevel) {
        int originalStackSize = itemEntity.getItem().getCount();
        int rounds = computeActualRounds(itemEntity, originalStackSize);
        if (rounds <= 0) {
            LOGGER.debug("No items can be converted to block for {} (catalysts exhausted)", getResultId());
            return;
        }

        Block resultBlock = getResultBlock(itemEntity);
        if (resultBlock == Blocks.AIR) {
            LOGGER.warn("Could not resolve result block for item '{}' when enableItemBlock={}, config will be skipped",
                    itemEntity.getItem().getItem(), enableItemBlock);
            return;
        }

        int actualConvertCount = rounds * getSourceMultiple();
        int remaining = originalStackSize - actualConvertCount;
        // 物品下一tick消失
        itemEntity.makeFakeItem();
        consumeAllOthers(itemEntity, actualConvertCount);
        // 下一tick开始执行延迟放置方块的任务
        boolean consumeFluid = innerFluid == null || !innerFluid.hasInnerFluid() || innerFluid.isConsumeFluid();
        LevelTaskManager.addTask(serverLevel, new PlaceBlockTask(
                itemEntity.blockPosition(), resultBlock, getRadius(), consumeFluid,
                rounds * getResultMultiple(),
                () -> addRemainingItems(itemEntity, serverLevel, remaining, 0.5, 1, 0.5)));
    }

    // ========== 结果相关方法 ========== //
    // 当开启了使用物品对应方块，且物品是方块物品，就直接使用对应的方块
    public Block getResultBlock() {
        if (enableItemBlock) {
            Item startItem = getStartItem();
            if (startItem instanceof BlockItem blockItem) {
                return blockItem.getBlock();
            }
            return Blocks.AIR;
        }

        if (isCacheInitialized() && cachedResultBlock != null) {
            return cachedResultBlock;
        }
        ResourceLocation rl = SafeParseUtil.parseResourceLocation(resultId);
        return rl != null ? BuiltInRegistries.BLOCK.get(rl) : Blocks.AIR;
    }

    public Block getResultBlock(ItemEntity itemEntity) {
        if (!enableItemBlock) {
            return getResultBlock();
        }

        Item startItem = itemEntity != null ? itemEntity.getItem().getItem() : null;
        if (startItem instanceof BlockItem blockItem) {
            return blockItem.getBlock();
        }

        return Blocks.AIR;
    }

    @Override
    public String getResultDescriptionId() {
        return getResultBlock().getDescriptionId();
    }

    @Override
    public ItemStack getResultIcon() {
        if (enableItemBlock) {
            return getStartItemIcon();
        }

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
