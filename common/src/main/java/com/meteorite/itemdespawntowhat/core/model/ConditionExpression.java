package com.meteorite.itemdespawntowhat.core.model;

import java.util.List;

/**
 * 条件表达式：析取范式（DNF）。
 * 组间为 OR，组内为 AND；空表达式（无任何组）表示恒真。
 * JSON 形状为二维数组：外层数组是"组的析取"，内层数组是"叶的合取"。
 */
public record ConditionExpression(List<ConditionGroup> groups) {

    // 恒真表达式，用于无条件规则与缺省值
    public static final ConditionExpression EMPTY = new ConditionExpression(List.of());

    public ConditionExpression {
        groups = List.copyOf(groups);
    }

    // 是否为空表达式（等价于恒真）
    public boolean isEmpty() {
        return groups.isEmpty();
    }

    // 条件叶总数，用于同优先级下的特异性兜底排序
    public int leafCount() {
        int total = 0;
        for (ConditionGroup group : groups) {
            total += group.leafCount();
        }
        return total;
    }

    // 结构合法性：不允许出现空条件组（空组合取恒真，会掩盖配置意图）
    public boolean isStructurallyValid() {
        for (ConditionGroup group : groups) {
            if (group.isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
