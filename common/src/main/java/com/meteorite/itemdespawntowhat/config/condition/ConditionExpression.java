package com.meteorite.itemdespawntowhat.config.condition;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.config.condition.type.ConditionType;
import com.meteorite.itemdespawntowhat.config.condition.type.ConditionTypeRegistry;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import org.jetbrains.annotations.Nullable;

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

    public int leafCount() {
        return groups().stream().mapToInt(group -> group.conditions().size()).sum();
    }

    public ConditionExpressionEvaluator compile(BaseConversionConfig config) {
        return ConditionExpressionEvaluator.compile(this, config);
    }

    public @Nullable ConditionLeaf firstGroupLeaf(ConditionType type) {
        if (groups().isEmpty()) {
            return null;
        }
        return groups().getFirst().conditions().stream()
                .filter(leaf -> leaf.typeId().equals(type.id()))
                .findFirst()
                .orElse(null);
    }

    public void setFirstGroupLeaf(ConditionType type, @Nullable Object parameters) {
        ConditionGroup group = ensureFirstGroup();
        group.conditions().removeIf(leaf -> leaf.typeId().equals(type.id()));
        if (parameters != null) {
            group.conditions().add(new ConditionLeaf(type, parameters, false));
        }
        removeEmptyLeadingGroup();
    }

    public boolean containsType(ConditionType type) {
        return groups().stream()
                .flatMap(group -> group.conditions().stream())
                .anyMatch(leaf -> leaf.typeId().equals(type.id()));
    }

    public boolean supportsLegacyEditor() {
        if (groups().size() > 1) {
            return false;
        }
        return groups().stream()
                .flatMap(group -> group.conditions().stream())
                .allMatch(leaf -> !leaf.negated()
                        && ConditionTypeRegistry.byId(leaf.typeId()) != null
                        && leaf.typeId().getNamespace().equals(com.meteorite.itemdespawntowhat.Constants.MOD_ID));
    }

    private ConditionGroup ensureFirstGroup() {
        if (groups().isEmpty()) {
            groups().add(new ConditionGroup());
        }
        return groups().getFirst();
    }

    private void removeEmptyLeadingGroup() {
        if (groups().size() == 1 && groups().getFirst().conditions().isEmpty()) {
            groups().clear();
        }
    }
}
