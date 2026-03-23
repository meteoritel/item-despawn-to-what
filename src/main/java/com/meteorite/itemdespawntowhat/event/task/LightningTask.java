package com.meteorite.itemdespawntowhat.event.task;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public class LightningTask implements LevelDelayTask{
    // 默认最大水平扩散半径
    private static final double DEFAULT_MAX_SPREAD = 5.0;

    private final BlockPos origin;
    private final boolean visualOnly;
    private final int delayTicks;
    private final int count;
    private final double maxSpread;
    private final Runnable onFinishCallback;

    private int ticksElapsed = 0;
    private int executedCount = 0;
    private boolean finished = false;

    public LightningTask(BlockPos origin, boolean visualOnly, int delayTicks,
                         int count, double maxSpread, Runnable onFinishCallback) {
        this.origin = origin;
        this.visualOnly = visualOnly;
        this.delayTicks = delayTicks;
        this.count = count;
        this.maxSpread = maxSpread;
        this.onFinishCallback = onFinishCallback;
    }

    public LightningTask(BlockPos origin, boolean visualOnly, int delayTicks,
                         int count, Runnable onFinishCallback) {
        this(origin, visualOnly, delayTicks, count, DEFAULT_MAX_SPREAD, onFinishCallback);
    }

    @Override
    public void tick(ServerLevel serverLevel) {
        if (finished) return;
        ticksElapsed++;

        if (ticksElapsed <= delayTicks) return;

        spawnLightning(serverLevel);

        executedCount++;
        ticksElapsed = 0;

        if (executedCount >= count) {
            finished = true;
        }
    }

    private void spawnLightning(ServerLevel serverLevel) {
        double strikeX;
        double strikeZ;

        if (executedCount == 0 || maxSpread <= 0) {
            // 第一道雷精准打在 origin
            strikeX = origin.getX() + 0.5;
            strikeZ = origin.getZ() + 0.5;
        } else {
            // 后续在圆形范围内均匀随机落点
            double angle = serverLevel.random.nextDouble() * 2 * Math.PI;
            double radius = Math.sqrt(serverLevel.random.nextDouble()) * maxSpread;
            strikeX = origin.getX() + 0.5 + Math.cos(angle) * radius;
            strikeZ = origin.getZ() + 0.5 + Math.sin(angle) * radius;
        }

        // 查找地面真实高度，确保闪电接地
        BlockPos groundPos = serverLevel.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING,
                BlockPos.containing(strikeX, origin.getY(), strikeZ)
        );

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
        if (bolt == null) return;

        bolt.moveTo(new Vec3(strikeX, groundPos.getY(), strikeZ));
        bolt.setVisualOnly(visualOnly);
        serverLevel.addFreshEntity(bolt);
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void onFinish(ServerLevel serverLevel) {
        if (onFinishCallback != null) {
            onFinishCallback.run();
        }
    }
}
