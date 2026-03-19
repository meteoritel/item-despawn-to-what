package com.meteorite.itemdespawntowhat.event.task;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;

import java.util.Collections;
import java.util.List;

/*
 * 延迟生成箭雨的任务。
 * 箭矢从 Y=255 高处生成，位置在 origin 水平方向随机散开，
 * 箭矢方向竖直向下，形成箭雨效果。
 */
public class ArrowRainTask implements LevelDelayTask{

    // 箭矢生成的固定 Y 高度
    private static final int SPAWN_Y = 255;
    // 水平最大散布半径（格
    private static final double MAX_SPREAD = 5.0;
    // 箭矢速度
    private static final float ARROW_SPEED = 3.0f;

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
        double offsetX = (serverLevel.random.nextDouble() * 2 - 1) * MAX_SPREAD;
        double offsetZ = (serverLevel.random.nextDouble() * 2 - 1) * MAX_SPREAD;

        double spawnX = origin.getX() + 0.5 + offsetX;
        double spawnZ = origin.getZ() + 0.5 + offsetZ;

        Arrow arrow = EntityType.ARROW.create(serverLevel);
        if (arrow == null) return;

        arrow.moveTo(spawnX, SPAWN_Y, spawnZ, 0, 90);
        // 竖直向下射出
        arrow.shoot(0, -1, 0, ARROW_SPEED, 0f);
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
