package com.meteorite.itemdespawntowhat.condition;

import com.meteorite.itemdespawntowhat.condition.checker.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 根据配置上下文构建组合条件检查器。
 */
public final class ConditionCheckerUtil {

    private ConditionCheckerUtil() {
    }

    public static ConditionChecker buildCombinedChecker(ConditionContext ctx) {
        List<ConditionChecker> checkers = new ArrayList<>();
        for (var factory : ConditionCheckerRegistry.getFactories()) {
            AbstractConditionChecker checker = factory.get();
            if (checker.shouldApply(ctx)) {
                AbstractConditionChecker created = checker.createChecker(ctx);
                if (created != null) {
                    checkers.add(created);
                }
            }
        }
        return combineAll(checkers);
    }

    public static ConditionChecker combineAll(List<ConditionChecker> checkers) {
        if (checkers.isEmpty()) {
            return (itemEntity, level) -> true;
        }

        // 任何一项条件检查器没有通过就不通过
        return (itemEntity, level) -> {
            for (ConditionChecker checker : checkers) {
                if (!checker.checkCondition(itemEntity,level)) {
                    return false;
                }
            }
            return true;
        };
    }
}
