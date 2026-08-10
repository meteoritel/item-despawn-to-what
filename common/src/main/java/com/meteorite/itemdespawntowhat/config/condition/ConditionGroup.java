package com.meteorite.itemdespawntowhat.config.condition;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * 表示 DNF 中组内全部条件叶均需成立的合取子句。
 */
public final class ConditionGroup {
    @SerializedName("conditions")
    private List<ConditionLeaf> conditions = new ArrayList<>();

    public ConditionGroup() {
    }

    public ConditionGroup(List<ConditionLeaf> conditions) {
        this.conditions = new ArrayList<>(conditions == null ? List.of() : conditions);
    }

    public List<ConditionLeaf> conditions() {
        if (conditions == null) {
            conditions = new ArrayList<>();
        }
        return conditions;
    }

    // 显式的 null 列表或叶节点属于非法配置。
    public boolean isStructurallyValid() {
        return conditions != null && conditions.stream()
                .allMatch(leaf -> leaf != null && leaf.isStructurallyValid());
    }
}
