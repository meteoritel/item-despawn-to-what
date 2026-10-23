package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.EffectStep;
import com.meteorite.itemdespawntowhat.core.type.effect.WeatherEffect;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * weather 执行器：切换触发维度的天气与持续时长。
 * 无天空光的维度不适用天气；已处于目标天气时不重复设置，避免打断正在进行的天气。
 * rounds 语义：一次性世界效果，不随 rounds 缩放（不是「每个物品一份」的结果），
 * 也不参与容量计算与组数限制。
 * 约定：delay_ticks / chance / 效果级 conditions 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 * 效果级条件与概率在成功转化当刻判定并随任务携带；天空光与目标天气属于执行时必须实时读取的世界状态，
 * 到期执行时按届时状态判定，已变化则跳过，不冻结整个世界。
 */
public final class WeatherExecutor {

    // 执行期跳过原因：无天空光、已处于目标天气；仅本类使用
    private static final String NO_SKY_LIGHT = "no_sky_light";
    private static final String ALREADY_RAINING = "already_raining";
    private static final String ALREADY_CLEAR = "already_clear";

    private WeatherExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 按成功转化当刻固定的输入创建执行状态：已判定跳过时直接给出回执，照常执行时只做天气世界写。
     * @param effect 天气效果参数
     * @param context 本组上下文
     * @param preparation 成功转化当刻固定的输入；null 表示未固定输入，直接执行
     * @return 单步执行状态
     */
    public static EffectExecution start(WeatherEffect effect, EffectContext context, @Nullable EffectPreparation preparation) {
        if (preparation != null && preparation.gate() != null) {
            String reason = preparation.gate();
            return budget -> EffectStep.done(EffectResult.skipped(reason));
        }
        return budget -> EffectStep.done(execute(effect, context));
    }

    // 此处借用 Minecraft 管理的实例，生命周期由游戏负责，不能在此关闭。
    /**
     * 切换到目标天气及雷暴状态；已匹配时保留现有持续时长。
     *
     * @param effect 天气和持续时长
     * @param context 触发维度上下文
     * @return 实际切换或已匹配的回执
     */
    public static EffectResult execute(WeatherEffect effect, EffectContext context) {
        ServerLevel level = context.level();
        if (!level.dimensionType().hasSkyLight()) {
            return EffectResult.skipped(NO_SKY_LIGHT);
        }
        if (effect.mode() == WeatherEffect.Mode.RAIN) {
            if (level.isRaining() && level.isThundering() == effect.thundering()) {
                return EffectResult.skipped(ALREADY_RAINING);
            }
            level.setWeatherParameters(0, effect.durationTicks(), true, effect.thundering());
            return EffectResult.applied(1);
        }
        if (!level.isRaining() && !level.isThundering()) {
            return EffectResult.skipped(ALREADY_CLEAR);
        }
        level.setWeatherParameters(effect.durationTicks(), 0, false, false);
        return EffectResult.applied(1);
    }
}
