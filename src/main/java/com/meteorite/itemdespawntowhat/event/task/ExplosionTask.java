package com.meteorite.itemdespawntowhat.event.task;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public class ExplosionTask implements LevelDelayTask {
    // 每次爆炸对应的基础偏移半径（格）
    private static final double BASE_SPREAD = 1.5;
    // 每次爆炸的最大额外随机偏移（格）
    private static final double RANDOM_JITTER = 1.0;

    private final BlockPos origin;
    private final float explosionPower;
    private final boolean explosionFire;
    private final int delayTicks;
    private final int count;
    private final Runnable onFinishCallback;

    private int ticksElapsed = 0;
    private int executedCount = 0;
    private boolean finished = false;

    public ExplosionTask(BlockPos origin, float explosionPower, boolean explosionFire,
                         int delayTicks, int count, Runnable onFinishCallback) {
        this.origin = origin;
        this.explosionPower = explosionPower;
        this.explosionFire = explosionFire;
        this.delayTicks = Math.max(1, delayTicks);
        this.count = Math.max(1, count);
        this.onFinishCallback = onFinishCallback;
    }

    @Override
    public void tick(ServerLevel serverLevel) {
        if (finished) return;
        ticksElapsed++;

        if (ticksElapsed <= delayTicks) return;

        double x = origin.getX() + 0.5;
        double y = origin.getY() + 0.5;
        double z = origin.getZ() + 0.5;

        if (executedCount > 0) {
            // 随机偏移角度，半径随已执行次数增大
            double angle = serverLevel.random.nextDouble() * 2 * Math.PI;
            double radius = executedCount * BASE_SPREAD * serverLevel.random.nextDouble()
                    + serverLevel.random.nextDouble() * RANDOM_JITTER;
            x += Math.cos(angle) * radius;
            z += Math.sin(angle) * radius;
        }

        serverLevel.explode(
                null,
                x, y, z,
                explosionPower,
                explosionFire,
                Level.ExplosionInteraction.TNT
        );

        executedCount++;
        ticksElapsed = 0;

        if (executedCount >= count) {
            finished = true;
        }
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
