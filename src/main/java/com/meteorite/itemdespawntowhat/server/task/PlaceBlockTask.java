package com.meteorite.itemdespawntowhat.server.task;

import com.meteorite.itemdespawntowhat.ModConfigValues;
import com.meteorite.itemdespawntowhat.util.DistanceUtil;
import com.meteorite.itemdespawntowhat.util.ItemReturnUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
    private final BlockPlaceShape blockPlaceShape;

    private int radius = 0;
    private int placed = 0;
    private int ticksElapsed = 0;
    private boolean finished = false;

    public PlaceBlockTask(BlockPos center, Block block, int maxRadius, BlockPlaceShape blockPlaceShape,
                          boolean consumeFluid, int maxBlocks, Runnable onFinishCallback) {
        this.center = center;
        this.block = block;
        this.maxRadius = maxRadius;
        this.blockPlaceShape = blockPlaceShape != null ? blockPlaceShape : BlockPlaceShape.SQUARE;
        this.consumeFluid = consumeFluid;
        this.maxBlocks = maxBlocks;
        this.onFinishCallback = onFinishCallback;
        this.tickInterval = ModConfigValues.BLOCK_PLACE_INTERVAL_TICKS.get();
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void onFinish(ServerLevel serverLevel) {
        // 返还应该放置但是被挡住了没能放置的方块
        int unplaced = maxBlocks - placed;
        if (unplaced > 0) {
            ItemStack returnStack = new ItemStack(block.asItem(), unplaced);
            ItemReturnUtil.spawnLockedItem(serverLevel,
                    returnStack,
                    center.getX() + 0.5,
                    center.getY() + 1.5,
                    center.getZ() + 0.5);
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

        for (BlockPos pos : getLayerPositions(center, radius, blockPlaceShape)) {
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

    // 逐层位置辅助方法
    private static List<BlockPos> getLayerPositions(BlockPos center, int radius, BlockPlaceShape shape) {
        List<BlockPos> result = new ArrayList<>();

        if (radius == 0) {
            result.add(center);
            return result;
        }

        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();
        int minY = shape.isThreeDimensional() ? -radius : 0;
        int maxY = shape.isThreeDimensional() ? radius : 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = minY; dy <= maxY; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (!isOnBoundary(shape, dx, dy, dz, radius)) {
                        continue;
                    }
                    result.add(new BlockPos(cx + dx, cy + dy, cz + dz));
                }
            }
        }

        return result;
    }

    private static boolean isOnBoundary(BlockPlaceShape shape, int dx, int dy, int dz, int radius) {
        return shape.usesEuclideanDistance()
                ? isOnEuclideanBoundary(shape.isThreeDimensional(), dx, dy, dz, radius)
                : isOnChebyshevBoundary(shape.isThreeDimensional(), dx, dy, dz, radius);
    }

    private static boolean isOnEuclideanBoundary(boolean threeDimensional, int dx, int dy, int dz, int radius) {
        double distance = threeDimensional
                ? DistanceUtil.euclideanDistance(dx, dy, dz)
                : DistanceUtil.euclideanDistance(dx, dz);
        return distance >= radius && distance < radius + 1;
    }

    private static boolean isOnChebyshevBoundary(boolean threeDimensional, int dx, int dy, int dz, int radius) {
        int distance = threeDimensional
                ? DistanceUtil.chebyshevDistance(dx, dy, dz)
                : DistanceUtil.chebyshevDistance(dx, dz);
        return distance == radius;
    }

    public enum BlockPlaceShape {
        SQUARE(false, false, "gui.itemdespawntowhat.block_place_shape.square"),
        CIRCLE(false, true, "gui.itemdespawntowhat.block_place_shape.circle"),
        CUBE(true, false, "gui.itemdespawntowhat.block_place_shape.cube"),
        SPHERE(true, true, "gui.itemdespawntowhat.block_place_shape.sphere");

        private final boolean threeDimensional;
        private final boolean euclidean;
        private final String descriptionId;

        BlockPlaceShape(boolean threeDimensional, boolean euclidean, String descriptionId) {
            this.threeDimensional = threeDimensional;
            this.euclidean = euclidean;
            this.descriptionId = descriptionId;
        }

        public boolean isThreeDimensional() {
            return threeDimensional;
        }

        public boolean usesEuclideanDistance() {
            return euclidean;
        }

        public String getDescriptionId() {
            return descriptionId;
        }
    }
}
