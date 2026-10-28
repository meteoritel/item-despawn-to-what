package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import java.util.List;

/** 一个条件字段的展示协调器；只缓存相对结构与类型上下文，不持有可能重排的绝对 scope。 */
public final class ConditionTreeViewModel {
    private ConditionTreeProjection projection = new ConditionTreeProjection(ConditionExpression.EMPTY);
    private List<ConditionTreeContract.TypeOption> options = List.of();
    /** 相同表达式与上下文复用快照；折叠、选择、字体和宽度不触发重新诊断。 */
    public ConditionTreeProjection project(ConditionExpression expression, List<ConditionTreeContract.TypeOption> types) {
        if (!projection.expression().equals(expression) || !options.equals(types)) {
            projection = new ConditionTreeProjection(expression);
            options = List.copyOf(types);
        }
        return projection;
    }
}
