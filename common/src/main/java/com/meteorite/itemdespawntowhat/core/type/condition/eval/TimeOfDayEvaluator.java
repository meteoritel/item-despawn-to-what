package com.meteorite.itemdespawntowhat.core.type.condition.eval;

import com.meteorite.itemdespawntowhat.core.api.ConditionContext;
import com.meteorite.itemdespawntowhat.core.type.condition.TimeOfDayCondition;

/**
 * time_of_day 条件的求值器：把世界时间取模到一天之内再做区间判定。
 * from &lt;= to 为普通区间；from > to 表示跨零点区间（如 22000 → 2000）。
 * 纯谓词：只读取上下文，不修改世界；不处理 negated（叶级取反由运行时统一应用）。
 */
public final class TimeOfDayEvaluator {

    // 原版一天的刻数
    private static final long TICKS_PER_DAY = 24000L;

    private TimeOfDayEvaluator() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 区间判定：跨零点时取并集
    public static boolean test(TimeOfDayCondition condition, ConditionContext context) {
        int time = (int) Math.floorMod(context.level().getDayTime(), TICKS_PER_DAY);
        int from = condition.from();
        int to = condition.to();
        if (from <= to) {
            return time >= from && time <= to;
        }
        return time >= from || time <= to;
    }
}
