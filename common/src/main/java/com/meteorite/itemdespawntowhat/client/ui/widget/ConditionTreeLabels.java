package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.edit.ConditionTreeNodes;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import com.meteorite.itemdespawntowhat.client.edit.OpaqueCondition;
import java.util.List;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** 条件节点的标签语义；画布和摘要只选择展示形态，不各自解释类型与运算符。 */
public final class ConditionTreeLabels {
    private ConditionTreeLabels() {}
    /** 类型选项优先，其次使用条件类型的本地化名称。 */
    public static Component type(@Nullable ResourceLocation type, List<ConditionTreeContract.TypeOption> options) {
        if (type == null) return Component.translatable(TypeLabels.UI_PREFIX + "tree.leaf_missing");
        for (var option : options) if (option.id().equals(type)) return option.label();
        return TypeLabels.conditionLabel(type);
    }
    /** 叶标题保留未知条件的只读标记。 */
    public static Component leaf(ConditionNode.Leaf leaf, List<ConditionTreeContract.TypeOption> options) {
        Component title = type(leaf.condition() == null ? null : leaf.condition().type(), options);
        return leaf.condition() instanceof OpaqueCondition
                ? title.copy().append(" ").append(Component.translatable(TypeLabels.UI_PREFIX + "tree.parameters_readonly")) : title;
    }
    /** 逻辑节点标题。 */
    public static Component operator(ConditionNode node) {
        return Component.translatable(TypeLabels.UI_PREFIX + "tree.operator." + switch (node) {
            case ConditionNode.AllOf ignored -> "all"; case ConditionNode.AnyOf ignored -> "any";
            case ConditionNode.Inverted ignored -> "not"; case ConditionNode.Leaf ignored -> throw new IllegalArgumentException("Not a logic node");
        });
    }
    /** 容器子项计数文案。 */
    public static Component count(ConditionNode node) {
        int count = ConditionTreeNodes.INSTANCE.children(node).size();
        return count == 0 ? Component.translatable(TypeLabels.UI_PREFIX + "tree.empty")
                : count == 1 ? Component.translatable(TypeLabels.UI_PREFIX + "tree.card.count.one")
                : Component.translatable(TypeLabels.UI_PREFIX + "tree.card.count", count);
    }
    /** 逻辑节点的操作说明。 */
    public static Component hint(ConditionNode node) {
        return Component.translatable(TypeLabels.UI_PREFIX + "tree.card.hint." + switch (node) {
            case ConditionNode.AllOf ignored -> "all"; case ConditionNode.AnyOf ignored -> "any";
            case ConditionNode.Inverted ignored -> "not"; case ConditionNode.Leaf ignored -> throw new IllegalArgumentException("Not a logic node");
        });
    }
    /** 摘要行：组合节点带数量，叶节点带宿主提供的参数。 */
    public static Component summary(ConditionNode node, List<ConditionTreeContract.TypeOption> options,
                                    @Nullable Function<ConditionNode.Leaf, Component> detail) {
        return switch (node) {
            case ConditionNode.AllOf all -> Component.translatable(TypeLabels.UI_PREFIX + "tree.group_label",
                    Component.translatable(TypeLabels.UI_PREFIX + "tree.node.all"), all.terms().size());
            case ConditionNode.AnyOf any -> Component.translatable(TypeLabels.UI_PREFIX + "tree.group_label",
                    Component.translatable(TypeLabels.UI_PREFIX + "tree.node.any"), any.terms().size());
            case ConditionNode.Inverted ignored -> operator(node);
            case ConditionNode.Leaf leaf -> {
                Component name = leaf(leaf, options);
                Component parameters = leaf.condition() == null || detail == null ? null : detail.apply(leaf);
                yield parameters == null || parameters.getString().isEmpty() ? name
                        : Component.translatable(TypeLabels.UI_PREFIX + "summary.leaf", name, parameters);
            }
        };
    }
}
