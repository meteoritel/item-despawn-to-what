package com.meteorite.itemdespawntowhat.config;

import com.meteorite.itemdespawntowhat.event.task.ArrowRainTask;
import com.meteorite.itemdespawntowhat.event.task.ExplosionTask;
import com.meteorite.itemdespawntowhat.event.task.LevelTaskManager;
import com.meteorite.itemdespawntowhat.event.task.LightningTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

// 一些杂乱的现象，使用这个枚举来管理
public enum WorldEffectType {
    // 当前维度天气调整为下雨或雷雨
    RAIN(
            "effect.minecraft.rain",
            () -> new ItemStack(Items.WATER_BUCKET),
            (itemEntity, level, config, count, onFinishCallback) -> {
                // 前置条件：仅主世界，且当前不在下雨（含雷雨）才执行
                if (!level.dimensionType().hasSkyLight()) return;
                if (level.isRaining()) return;
                level.setWeatherParameters(0, config.getWeatherDurationTicks(), true, config.isThundering());
                onFinishCallback.run();
            }
    ),

    // 调整天气为晴天
    CLEAR(
            "effect.minecraft.clear",
            () -> new ItemStack(Items.SUNFLOWER),
            (itemEntity, level, config, count, onFinishCallback) -> {
                if (!level.dimensionType().hasSkyLight()) return;
                if (!level.isRaining() && !level.isThundering()) return;
                level.setWeatherParameters(config.getWeatherDurationTicks(), 0, false, false);
                onFinishCallback.run();
            }
    ),

    // 召唤闪电
    LIGHTNING("entity.minecraft.lightning_bolt",
            () -> new ItemStack(Items.LIGHTNING_ROD),
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
            "effect.explosion",
            () -> new ItemStack(Items.TNT),
            (itemEntity, level, config, count, onFinishCallback) -> {
                BlockPos pos = itemEntity.blockPosition();
                for (int i = 0; i < count; i++) {
                    LevelTaskManager.addTask(level, new ExplosionTask(
                            pos,
                            config.getExplosionPower(),
                            config.isExplosionFire(),
                            config.getExplosionIntervalTicks(),
                            count,
                            onFinishCallback
                    ));
                }
            }),

    // 召唤箭雨
    ARROW_RAIN(
            "entity.minecraft.arrow",
            () -> new ItemStack(Items.ARROW),
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
    private final IconSupplier iconSupplier;
    private final SideEffectExecutor executor;

    WorldEffectType(String descriptionId, IconSupplier iconSupplier , SideEffectExecutor executor) {
        this.descriptionId = descriptionId;
        this.iconSupplier = iconSupplier;
        this.executor = executor;
    }

    public String getDescriptionId() {
        return descriptionId;
    }

    public SideEffectExecutor getExecutor() {
        return executor;
    }

    public IconSupplier getIconSupplier() {
        return iconSupplier;
    }

    // ========== 现象执行器接口 ========== //
    @FunctionalInterface
    public interface SideEffectExecutor {
        void execute(ItemEntity itemEntity, ServerLevel level, SideEffectConfig config,
                     int count, Runnable onFinishCallback);
    }

    // ========== 图标供应接口 ========== //
    @FunctionalInterface
    public interface IconSupplier {
        ItemStack get();
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

        // 箭矢附加的药水效果列表（默认无效果）
        default List<MobEffectInstance> getArrowPotionEffects() { return List.of(); }

        // 箭矢捡起状态（默认不可捡起）
        default Arrow.Pickup getArrowPickupStatus() { return Arrow.Pickup.DISALLOWED; }

        // ========== 间隔时间不重写 ========== //
        // 闪电每次之间的间隔 tick 数（默认 10t = 0.5s）
        default int getLightningIntervalTicks() { return 10; }
        // 爆炸每次之间的间隔 tick 数（默认 5t）
        default int getExplosionIntervalTicks() { return 5; }
        // 箭矢每支之间的间隔 tick 数（默认 2t）
        default int getArrowIntervalTicks() { return 2; }
    }
}
