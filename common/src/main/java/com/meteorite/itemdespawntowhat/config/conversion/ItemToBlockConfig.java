package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.config.ConversionLimits;
import com.meteorite.itemdespawntowhat.config.type.BuiltinConversionTypes;
import com.meteorite.itemdespawntowhat.server.task.PlaceBlockTask.BlockPlaceShape;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * 物品到方块转换的配置数据。
 */
public class ItemToBlockConfig extends BaseConversionConfig{
    // 最大放置半径的限制，默认为6
    @SerializedName("radius_limit")
    private int radius = 6;
    @SerializedName("block_of_item")
    private boolean enableItemBlock = false;
    @SerializedName("block_place_shape")
    private BlockPlaceShape blockPlaceShape = BlockPlaceShape.SQUARE;

    // 缓存的结果方块实例，不参与序列化
    private transient Block cachedResultBlock;

    public ItemToBlockConfig() {
        super(BuiltinConversionTypes.ITEM_TO_BLOCK);
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
        if (radius < 0 || radius > ConversionLimits.MAX_BLOCK_RADIUS) {
            LOGGER.warn("radius_limit should be in range [0, {}], current={}",
                    ConversionLimits.MAX_BLOCK_RADIUS, radius);
            return false;
        }
        if (blockPlaceShape == null) {
            LOGGER.warn("block_place_shape must not be null");
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

    public BlockPlaceShape getBlockPlaceShape() {
        return blockPlaceShape != null ? blockPlaceShape : BlockPlaceShape.SQUARE;
    }

    public void setBlockPlaceShape(BlockPlaceShape blockPlaceShape) {
        this.blockPlaceShape = blockPlaceShape != null ? blockPlaceShape : BlockPlaceShape.SQUARE;
    }
}
