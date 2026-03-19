package com.meteorite.itemdespawntowhat.event.task;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;

public class LightningTask implements LevelDelayTask{
    private final BlockPos origin;
    private final boolean visualOnly;
    private final int delayTicks;
    private final int count;
    private final Runnable onFinishCallback;

    private int ticksElapsed = 0;
    private int executedCount = 0;
    private boolean finished = false;

    public LightningTask(BlockPos origin, boolean visualOnly, int delayTicks,int count, Runnable onFinishCallback) {
        this.origin = origin;
        this.visualOnly = visualOnly;
        this.delayTicks = delayTicks;
        this.count = count;
        this.onFinishCallback = onFinishCallback;
    }

    @Override
    public void tick(ServerLevel serverLevel) {
        if (finished) return;
        ticksElapsed++;

        if (ticksElapsed <= delayTicks) return;

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
        if (bolt != null) {
            bolt.moveTo(Vec3.atCenterOf(origin));
            bolt.setVisualOnly(visualOnly);
            serverLevel.addFreshEntity(bolt);
        }
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
