package com.meteorite.itemdespawntowhat.config.condition;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.config.condition.type.ConditionType;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 保存条件组间 OR、组内 AND 的析取范式条件表达式。
 */
public final class ConditionExpression {
    @SerializedName("groups")
    private List<ConditionGroup> groups = new ArrayList<>();

    public ConditionExpression() {
    }

    public ConditionExpression(List<ConditionGroup> groups) {
        this.groups = new ArrayList<>(groups == null ? List.of() : groups);
    }

    public List<ConditionGroup> groups() {
        if (groups == null) {
            groups = new ArrayList<>();
        }
        return groups;
    }

    // 显式的 null 结构属于非法配置，不能按空条件静默放行。
    public boolean isStructurallyValid() {
        return groups != null && groups.stream()
                .allMatch(group -> group != null && group.isStructurallyValid());
    }

    public int leafCount() {
        return groups().stream().mapToInt(group -> group.conditions().size()).sum();
    }

    public ConditionExpressionEvaluator compile(BaseConversionConfig config) {
        return ConditionExpressionEvaluator.compile(this, config);
    }

    public boolean containsType(ConditionType type) {
        return groups().stream()
                .flatMap(group -> group.conditions().stream())
                .anyMatch(leaf -> leaf.typeId().equals(type.id()));
    }

}
