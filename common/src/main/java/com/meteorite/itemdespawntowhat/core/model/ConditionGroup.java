package com.meteorite.itemdespawntowhat.core.model;

import java.util.List;

/**
 * 条件组：DNF 表达式中的一个合取子句，组内全部条件叶为真则该组为真。
 */
public record ConditionGroup(List<Condition> conditions) {

    public ConditionGroup {
        conditions = List.copyOf(conditions);
    }

    // 空组在布尔语义下恒真，加载校验会将其判为非法
    public boolean isEmpty() {
        return conditions.isEmpty();
    }

    // 组内条件叶数量
    public int leafCount() {
        return conditions.size();
    }
}
