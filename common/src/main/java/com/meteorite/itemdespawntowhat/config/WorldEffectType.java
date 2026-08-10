package com.meteorite.itemdespawntowhat.config;

import com.meteorite.itemdespawntowhat.server.task.ArrowRainTask;
import com.meteorite.itemdespawntowhat.server.task.ExplosionTask;
import com.meteorite.itemdespawntowhat.server.task.LevelTaskManager;
import com.meteorite.itemdespawntowhat.server.task.LightningTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 世界效果类型及其服务端任务执行器。
 */
public enum WorldEffectType {
    // 当前维度天气调整为下雨或雷雨
    RAIN(
            "effect.itemdespawntowhat.world_effect_type.rain",
            (itemEntity, level, config, count, onFinishCallback) -> {
                level.setWeatherParameters(0, config.getWeatherDurationTicks(), true, config.isThundering());
                onFinishCallback.run();
            }
    ),

    // 调整天气为晴天
    CLEAR(
            "effect.itemdespawntowhat.world_effect_type.clear",
            (itemEntity, level, config, count, onFinishCallback) -> {
                level.setWeatherParameters(config.getWeatherDurationTicks(), 0, false, false);
                onFinishCallback.run();
            }
    ),

    // 召唤闪电
    LIGHTNING("effect.itemdespawntowhat.world_effect_type.lightning_bolt",
            (itemEntity, level, config, count, onFinishCallback) -> {
                BlockPos pos = itemEntity.blockPosition();
                LevelTaskManager.addTask(level, new LightningTask(
                        pos,
                        config.isVisualOnly(),
                        config.getLightningIntervalTicks(),
                        count,
                        onFinishCallback
                ));
            }),

    // 召唤爆炸
    EXPLOSION(
            "effect.itemdespawntowhat.world_effect_type.explosion",
            (itemEntity, level, config, count, onFinishCallback) -> {
                BlockPos pos = itemEntity.blockPosition();
                LevelTaskManager.addTask(level, new ExplosionTask(
                        pos,
                        config.getExplosionPower(),
                        config.isExplosionFire(),
                        config.getExplosionIntervalTicks(),
                        count,
                        config.getExplosionDirectionType(),
                        onFinishCallback
                ));
            }),

    // 召唤箭雨
    ARROW_RAIN(
            "effect.itemdespawntowhat.world_effect_type.arrow",
            (itemEntity, level, config, count, onFinishCallback) -> {
                BlockPos pos = itemEntity.blockPosition();
                LevelTaskManager.addTask(level, new ArrowRainTask(
                        pos,
                        config.getArrowIntervalTicks(),
                        count,
                        config.getArrowPotionEffects(),
                        config.getArrowPickupStatus(),
                        onFinishCallback
                ));
            });

    private final String descriptionId;
    private final SideEffectExecutor executor;

    WorldEffectType(String descriptionId, SideEffectExecutor executor) {
        this.descriptionId = descriptionId;
        this.executor = executor;
    }

    public String getDescriptionId() {
        return descriptionId;
    }

    public SideEffectExecutor getExecutor() {
        return executor;
    }

    // 在消耗源物品前判断当前世界是否允许执行该效果
    public boolean canExecute(ServerLevel level) {
        return switch (this) {
            case RAIN -> level.dimensionType().hasSkyLight() && !level.isRaining();
            case CLEAR -> level.dimensionType().hasSkyLight()
                    && (level.isRaining() || level.isThundering());
            default -> true;
        };
    }

    // ========== 现象执行器接口 ========== //
    @FunctionalInterface
    public interface SideEffectExecutor {
        void execute(ItemEntity itemEntity, ServerLevel level, SideEffectConfig config,
                     int count, Runnable onFinishCallback);
    }

    // ========== 现象参数载体接口 ========== //
    public interface SideEffectConfig {
        // 闪电是否为纯视觉（不造成伤害/火焰）
        default boolean isVisualOnly() { return false; }

        // 下雨持续时间（默认 6000t = 5 min）
        default int getWeatherDurationTicks() { return 6000; }

        // 是否为雷雨天
        default boolean isThundering() {return false; }

        // 爆炸的威力（默认 1）
        default float getExplosionPower() { return 1f; }

        // 爆炸是否点火
        default boolean isExplosionFire() { return false; }

        // 爆炸方向
        default ExplosionTask.DirectionType getExplosionDirectionType() { return ExplosionTask.DirectionType.FLAT; }

        // 箭矢附加的药水效果列表（默认无效果）
        default @Nullable List<MobEffectInstance> getArrowPotionEffects() { return List.of(); }

        // 箭矢捡起状态（默认不可捡起）
        default Arrow.Pickup getArrowPickupStatus() { return Arrow.Pickup.DISALLOWED; }

        // ========== 间隔时间 ========== //
        // 闪电每次之间的间隔 (ticks)
        int getLightningIntervalTicks();
        // 爆炸每次之间的间隔 (ticks)
        int getExplosionIntervalTicks();
        // 箭矢每支之间的间隔 (ticks)
        int getArrowIntervalTicks();
    }
}
