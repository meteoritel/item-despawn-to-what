package com.meteorite.itemdespawntowhat.core.type.condition.eval;

import com.meteorite.itemdespawntowhat.core.api.ConditionContext;
import com.meteorite.itemdespawntowhat.core.type.condition.WeatherCondition;

/**
 * weather 条件的求值器：判定掉落物所在维度当前的天气。
 * 语义与旧实现一致：clear 要求既不降雨也不雷暴；rain 要求降雨且非雷暴；thunder 只要求雷暴。
 * 纯谓词：只读取上下文，不修改世界；不处理 negated（叶级取反由运行时统一应用）。
 */
public final class WeatherEvaluator {

    private WeatherEvaluator() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 按天气取值判定；取值缺失属非法配置，fail-closed
    public static boolean test(WeatherCondition condition, ConditionContext context) {
        if (condition.weather() == null) {
            return false;
        }
        boolean raining = context.level().isRaining();
        boolean thundering = context.level().isThundering();
        return switch (condition.weather()) {
            case CLEAR -> !raining && !thundering;
            case RAIN -> raining && !thundering;
            case THUNDER -> thundering;
        };
    }
}
