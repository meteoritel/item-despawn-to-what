package com.meteorite.itemdespawntowhat.config.condition;

import com.meteorite.itemdespawntowhat.condition.checker.ConditionChecker;
import com.meteorite.itemdespawntowhat.condition.checker.ConditionDebugResult;
import com.meteorite.itemdespawntowhat.config.condition.type.ConditionType;
import com.meteorite.itemdespawntowhat.config.condition.type.ConditionTypeRegistry;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 对预编译的 DNF 条件表达式执行短路求值。
 */
public final class ConditionExpressionEvaluator {
    private final List<List<CompiledLeaf>> groups;

    private ConditionExpressionEvaluator(List<List<CompiledLeaf>> groups) {
        this.groups = groups;
    }

    public static ConditionExpressionEvaluator compile(
            ConditionExpression expression,
            BaseConversionConfig config
    ) {
        List<List<CompiledLeaf>> compiledGroups = new ArrayList<>();
        for (ConditionGroup group : expression.groups()) {
            List<CompiledLeaf> compiledLeaves = new ArrayList<>();
            for (ConditionLeaf leaf : group.conditions()) {
                ConditionType type = ConditionTypeRegistry.require(leaf.typeId());
                compiledLeaves.add(new CompiledLeaf(type.debugName(),
                        ConditionTypeRegistry.createChecker(leaf, config), leaf.negated()));
            }
            compiledGroups.add(List.copyOf(compiledLeaves));
        }
        return new ConditionExpressionEvaluator(List.copyOf(compiledGroups));
    }

    public boolean matches(ItemEntity itemEntity, ServerLevel level) {
        if (groups.isEmpty()) {
            return true;
        }
        for (List<CompiledLeaf> group : groups) {
            if (matchesGroup(group, itemEntity, level)) {
                return true;
            }
        }
        return false;
    }

    public List<ConditionDebugResult> debug(ItemEntity itemEntity, ServerLevel level) {
        List<ConditionDebugResult> results = new ArrayList<>();
        for (List<CompiledLeaf> group : groups) {
            for (CompiledLeaf leaf : group) {
                results.add(new ConditionDebugResult(leaf.name(), leaf.matches(itemEntity, level)));
            }
        }
        return List.copyOf(results);
    }

    private static boolean matchesGroup(
            List<CompiledLeaf> group,
            ItemEntity itemEntity,
            ServerLevel level
    ) {
        for (CompiledLeaf leaf : group) {
            if (!leaf.matches(itemEntity, level)) {
                return false;
            }
        }
        return true;
    }

    private record CompiledLeaf(String name, ConditionChecker checker, boolean negated) {
        private boolean matches(ItemEntity itemEntity, ServerLevel level) {
            boolean matched = checker.checkCondition(itemEntity, level);
            return negated != matched;
        }
    }
}
