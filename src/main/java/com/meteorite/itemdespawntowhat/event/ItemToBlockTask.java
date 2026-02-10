package com.meteorite.itemdespawntowhat.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class ItemToBlockTask implements LevelDelayTask{

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int MAX_RADIUS = 6;

    private final ItemEntity originalItem;

    private final BlockPos center;
    private final int maxBlocks;
    private final Block block;

    private int radius = 0;
    private int placed = 0;
    private boolean finished = false;

    public ItemToBlockTask(ItemEntity itemEntity, Block block){
        this.originalItem = itemEntity;
        this.center = itemEntity.blockPosition();
        this.block = block;
        this.maxBlocks = itemEntity.getItem().getCount();
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void onFinish(ServerLevel serverLevel) {
        int remainBlocks = maxBlocks - placed;
        if (remainBlocks <= 0) {
            return;
        }

        ItemStack returnStack = originalItem.getItem().copy();
        returnStack.setCount(remainBlocks);

        // 因为放置了方块，所以在上方一格创造返还物品
        ItemEntity returnItem = new ItemEntity(serverLevel,
                center.getX() + 0.5,
                center.getY() + 1,
                center.getZ() + 0.5, returnStack);

        // 返还物品添加转化锁定的标签，防止重复转化
        returnItem.getPersistentData().putBoolean(ItemConversionEvent.CHECK_TAG_LOCK, true);
        serverLevel.addFreshEntity(returnItem);
    }

    @Override
    public void tick(ServerLevel serverLevel) {
        if (finished) return;
        if (radius > MAX_RADIUS || placed >= maxBlocks) {
            finished = true;
            return;
        }

        for (BlockPos pos : getRingPositions(center, radius)) {
            if (placed >= maxBlocks) break;

            if (!canPlace(serverLevel, pos, block)) continue;

            serverLevel.setBlock(pos, block.defaultBlockState(), 3);
            placed++;
        }
        radius++;
    }

    // 放置条件
    private static boolean canPlace(ServerLevel level, BlockPos pos, Block block) {

        if (!level.getBlockState(pos).canBeReplaced()) return false;
        return block.defaultBlockState().canSurvive(level, pos);
    }

    // 圈圈位置辅助方法
    private static List<BlockPos> getRingPositions(BlockPos center, int radius) {
        List<BlockPos> result = new ArrayList<>();

        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();

        if (radius == 0) {
            result.add(center);
            return result;
        }

        for (int dx = -radius; dx <= radius; dx++) {
            result.add(new BlockPos(cx + dx, cy, cz + radius));
            result.add(new BlockPos(cx + dx, cy, cz - radius));
        }

        for (int dz = -radius + 1; dz <= radius - 1; dz++) {
            result.add(new BlockPos(cx + radius, cy, cz + dz));
            result.add(new BlockPos(cx - radius, cy, cz + dz));
        }

        return result;
    }
}
