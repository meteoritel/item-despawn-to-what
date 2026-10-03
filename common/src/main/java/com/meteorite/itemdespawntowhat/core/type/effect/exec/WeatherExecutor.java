package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.type.effect.WeatherEffect;
import net.minecraft.server.level.ServerLevel;

/**
 * weather 执行器：切换触发维度的天气与持续时长。
 * 无天空光的维度不适用天气；已处于目标天气时不重复设置，避免打断正在进行的天气。
 * rounds 语义：一次性世界效果，不随 rounds 缩放（不是「每个物品一份」的结果），
 * 也不参与容量计算与组数限制（ADR-0001 阶段 4）。
 * 约定：delay_ticks / chance / 效果级 conditions 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 */
public final class WeatherExecutor {

    private WeatherExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 此处借用 Minecraft 管理的实例，生命周期由游戏负责，不能在此关闭。
    @SuppressWarnings("resource")
    public static EffectResult execute(WeatherEffect effect, EffectContext context) {
        ServerLevel level = context.level();
        if (!level.dimensionType().hasSkyLight()) {
            return EffectResult.skipped("no_sky_light");
        }
        if (effect.mode() == WeatherEffect.Mode.RAIN) {
            if (level.isRaining()) {
                return EffectResult.skipped("already_raining");
            }
            level.setWeatherParameters(0, effect.durationTicks(), true, effect.thundering());
            return EffectResult.applied(1);
        }
        if (!level.isRaining() && !level.isThundering()) {
            return EffectResult.skipped("already_clear");
        }
        level.setWeatherParameters(effect.durationTicks(), 0, false, false);
        return EffectResult.applied(1);
    }
}
