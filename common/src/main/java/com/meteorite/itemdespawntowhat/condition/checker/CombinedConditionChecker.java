package com.meteorite.itemdespawntowhat.condition.checker;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 按注册顺序执行并在首个失败条件处停止的组合检查器。
 */
public final class CombinedConditionChecker implements ConditionChecker {
    private final List<ConditionChecker> checkers;

    public CombinedConditionChecker(List<ConditionChecker> checkers) {
        this.checkers = List.copyOf(checkers);
    }

    @Override
    public String debugName() {
        return "combined";
    }

    @Override
    public boolean checkCondition(ItemEntity itemEntity, ServerLevel level) {
        for (ConditionChecker checker : checkers) {
            if (!checker.checkCondition(itemEntity, level)) {
                return false;
            }
        }
        return true;
    }

    public List<ConditionDebugResult> debug(ItemEntity itemEntity, ServerLevel level) {
        List<ConditionDebugResult> results = new ArrayList<>(checkers.size());
        for (ConditionChecker checker : checkers) {
            results.add(new ConditionDebugResult(
                    checker.debugName(), checker.checkCondition(itemEntity, level)));
        }
        return List.copyOf(results);
    }
}
