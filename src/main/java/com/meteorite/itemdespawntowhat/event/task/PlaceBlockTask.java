package com.meteorite.itemdespawntowhat.event.task;

import com.meteorite.itemdespawntowhat.ModConfigValues;
import com.meteorite.itemdespawntowhat.event.ItemConversionEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class PlaceBlockTask implements LevelDelayTask{

    private static final Logger LOGGER = LogManager.getLogger();

    private final BlockPos center;
    private final int maxBlocks;
    private final Block block;
    private final int maxRadius;
    private final boolean consumeFluid;
    private final Runnable onFinishCallback;
    private final int tickInterval;
    private final ModConfigValues.CircleShape circleShape;

    private int radius = 0;
    private int placed = 0;
    private int ticksElapsed = 0;
    private boolean finished = false;

    public PlaceBlockTask(BlockPos center, Block block, int maxRadius, boolean consumeFluid,
                          int maxBlocks, Runnable onFinishCallback) {
        this.center = center;
        this.block = block;
        this.maxRadius = maxRadius;
        this.consumeFluid = consumeFluid;
        this.maxBlocks = maxBlocks;
        this.onFinishCallback = onFinishCallback;
        this.tickInterval = ModConfigValues.BLOCK_PLACE_INTERVAL_TICKS.get();
        this.circleShape = ModConfigValues.BLOCK_PLACE_CIRCLE_SHAPE.get();
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void onFinish(ServerLevel serverLevel) {
        int unplaced = maxBlocks - placed;
        if (unplaced > 0) {
            ItemStack returnStack = new ItemStack(block.asItem(), unplaced);
            // 因为放置了方块，所以在上方一格创造返还物品
            ItemEntity returnItem = new ItemEntity(serverLevel,
                    center.getX() + 0.5,
                    center.getY() + 1,
                    center.getZ() + 0.5, returnStack);
            // 返还物品添加转化锁定的标签，防止重复转化
            returnItem.getPersistentData().putBoolean(ItemConversionEvent.CHECK_LOCK_TAG, true);
            serverLevel.addFreshEntity(returnItem);
            LOGGER.debug("Returned {} unplaced result blocks", unplaced);
        }
        if (onFinishCallback != null) {
            onFinishCallback.run();
        }
    }

    @Override
    public void tick(ServerLevel serverLevel) {
        if (finished) return;
        ticksElapsed++;
        if (ticksElapsed < tickInterval) return;
        ticksElapsed = 0;

        if (radius > maxRadius || placed >= maxBlocks) {
            finished = true;
            return;
        }

        if (radius == 0 && !consumeFluid) {
            radius++;
            return;
        }

        for (BlockPos pos : getRingPositions(center, radius, circleShape)) {
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
    private static List<BlockPos> getRingPositions(BlockPos center, int radius, ModConfigValues.CircleShape shape) {
        List<BlockPos> result = new ArrayList<>();

        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();

        if (radius == 0) {
            result.add(center);
            return result;
        }

        if (shape == ModConfigValues.CircleShape.CIRCLE) {
            // 欧几里得距离圆圈：floor(dist) == radius 的格子
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int dist = (int) Math.floor(Math.sqrt(dx * dx + dz * dz));
                    if (dist == radius) {
                        result.add(new BlockPos(cx + dx, cy, cz + dz));
                    }
                }
            }
        } else {
            // SQUARE：切比雪夫距离圆圈（原有逻辑）
            for (int dx = -radius; dx <= radius; dx++) {
                result.add(new BlockPos(cx + dx, cy, cz + radius));
                result.add(new BlockPos(cx + dx, cy, cz - radius));
            }
            for (int dz = -radius + 1; dz <= radius - 1; dz++) {
                result.add(new BlockPos(cx + radius, cy, cz + dz));
                result.add(new BlockPos(cx - radius, cy, cz + dz));
            }
        }

        return result;
    }
}
