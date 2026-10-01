package com.meteorite.itemdespawntowhat.core.type.condition.eval;

import com.meteorite.itemdespawntowhat.core.api.ConditionContext;
import com.meteorite.itemdespawntowhat.core.type.condition.YLevelCondition;

/**
 * y_level 条件的求值器：判定掉落物所在方块的 Y 坐标是否落在区间内。
 * 两端可空表示该端不限制；两端都为空时恒真（无意义的配置，但不属非法）。
 * 纯谓词：只读取上下文，不修改世界；不处理 negated（叶级取反由运行时统一应用）。
 */
public final class YLevelEvaluator {

    private YLevelEvaluator() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 区间判定：空端视为不限制
    public static boolean test(YLevelCondition condition, ConditionContext context) {
        int y = context.pos().getY();
        if (condition.min() != null && y < condition.min()) {
            return false;
        }
        return condition.max() == null || y <= condition.max();
    }
}
