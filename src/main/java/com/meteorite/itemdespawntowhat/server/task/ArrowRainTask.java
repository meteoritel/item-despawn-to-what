package com.meteorite.itemdespawntowhat.server.task;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;

import java.util.Collections;
import java.util.List;

public class ArrowRainTask implements LevelDelayTask{

    private static final int SPAWN_HEIGHT_ABOVE_GROUND = 80;
    // 水平最大散布半径
    private static final double MAX_SPREAD = 5.0;
    // 箭矢速度
    private static final float ARROW_SPEED = 3.5f;
    // 箭矢水平随机抖动最大值
    private static final float HORIZONTAL_JITTER = 0.08f;

    private final BlockPos origin;
    private final int delayTicks;
    private final int count;
    private final List<MobEffectInstance> potionEffects;
    private final Arrow.Pickup pickupStatus;
    private final Runnable onFinishCallback;

    private int ticksElapsed = 0;
    private int executedCount = 0;
    private boolean finished = false;

    public ArrowRainTask(BlockPos origin, int delayTicks, int count,
                         List<MobEffectInstance> potionEffects, Arrow.Pickup pickupStatus,
                         Runnable onFinishCallback) {
        this.origin = origin;
        this.delayTicks = Math.max(1, delayTicks);
        this.count = Math.max(1, count);
        this.potionEffects = (potionEffects != null) ? potionEffects : Collections.emptyList();
        this.pickupStatus = (pickupStatus != null) ? pickupStatus : Arrow.Pickup.DISALLOWED;
        this.onFinishCallback = onFinishCallback;
    }

    // 简化构造器（无药水效果，箭矢不可捡起）
    public ArrowRainTask(BlockPos origin, int delayTicks, int count, Runnable onFinishCallback) {
        this(origin, delayTicks, count, null, Arrow.Pickup.DISALLOWED,onFinishCallback);
    }

    @Override
    public void tick(ServerLevel serverLevel) {
        if (finished) return;
        ticksElapsed++;

        if (ticksElapsed <= delayTicks) return;

        spawnArrow(serverLevel);

        executedCount++;
        ticksElapsed = 0;

        if (executedCount >= count) {
            finished = true;
        }
    }

    private void spawnArrow(ServerLevel serverLevel) {
        // 水平随机散布
        double angle = serverLevel.random.nextDouble() * 2 * Math.PI;
        double radius = Math.sqrt(serverLevel.random.nextDouble()) * MAX_SPREAD;

        double offsetX = Math.cos(angle) * radius;
        double offsetZ = Math.sin(angle) * radius;

        double spawnX = origin.getX() + 0.5 + offsetX;
        double spawnZ = origin.getZ() + 0.5 + offsetZ;

        Arrow arrow = EntityType.ARROW.create(serverLevel);
        if (arrow == null) return;

        arrow.moveTo(spawnX, origin.getY() + SPAWN_HEIGHT_ABOVE_GROUND, spawnZ, 0, 90);

        // 主方向竖直向下，附加轻微水平随机抖动
        float jitterX = (serverLevel.random.nextFloat() * 2 - 1) * HORIZONTAL_JITTER;
        float jitterZ = (serverLevel.random.nextFloat() * 2 - 1) * HORIZONTAL_JITTER;
        arrow.shoot(jitterX, -1, jitterZ, ARROW_SPEED, 0f);
        arrow.setOwner(null);
        arrow.pickup = pickupStatus;

        // 附加药水效果
        for (MobEffectInstance effect : potionEffects) {
            arrow.addEffect(new MobEffectInstance(effect));
        }

        serverLevel.addFreshEntity(arrow);
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
