package com.meteorite.itemdespawntowhat.core.type.condition.eval;

import com.meteorite.itemdespawntowhat.core.api.ConditionContext;
import com.meteorite.itemdespawntowhat.core.type.condition.LightLevelCondition;

/**
 * light_level 条件的求值器：判定掉落物所在位置的实际光照等级是否落在区间内。
 * 光照取值采用 LevelReader#getMaxLocalRawBrightness：天空光经时间衰减后与方块光取较大值，
 * 与实体/刷怪所使用的"光照等级"口径一致；两端可空表示该端不限制。
 * 纯谓词：只读取上下文，不修改世界；不处理取反（取反由条件树的 inverted 节点承担）。
 */
public final class LightLevelEvaluator {

    private LightLevelEvaluator() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 区间判定：空端视为不限制
    public static boolean test(LightLevelCondition condition, ConditionContext context) {
        int light = context.level().getMaxLocalRawBrightness(context.pos());
        if (condition.min() != null && light < condition.min()) {
            return false;
        }
        return condition.max() == null || light <= condition.max();
    }
}
