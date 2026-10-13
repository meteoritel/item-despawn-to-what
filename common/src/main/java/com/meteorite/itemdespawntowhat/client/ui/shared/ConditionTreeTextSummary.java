package com.meteorite.itemdespawntowhat.client.ui.shared;

import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.ConditionLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 条件树的只读 ASCII 摘要：保留组合嵌套，叶节点复用玩家可读的参数摘要。
 */
public final class ConditionTreeTextSummary {
    private static final String UI = "gui.itemdespawntowhat.edit.";
    private static final String BRANCH = "+-- ";
    private static final String LAST_BRANCH = "`-- ";
    private static final String CONTINUATION = "|   ";
    private static final String INDENT = "    ";

    private ConditionTreeTextSummary() { }

    /**
     * 生成适合 tooltip 的多行树摘要；缺省条件表示无条件限制，未完成节点保留提示。
     *
     * @param root 条件树根，允许为空
     * @param maxLines 可用行数，至少保留两行；超出部分以明确提示收尾
     * @param leafSummary 条件叶参数摘要格式器，由宿主注入
     * @return 不包含输入或产出信息的条件树摘要
     */
    public static Component format(@Nullable RuleValue root, int maxLines, Function<RuleValue, Component> leafSummary) {
        if (root == null || root.isNull()) return Component.translatable(UI + "summary.any_condition");
        int limit = Math.clamp(maxLines, 2, ConditionLimits.MAX_NODES);
        List<Component> lines = new ArrayList<>();
        // 行数与深度同时有界，过大的草稿不会撑出屏幕或无限递归。
        if (appendTree(root, "", "", 1, limit, lines, leafSummary)) {
            if (lines.size() == limit) lines.removeLast();
            lines.add(Component.translatable(UI + "recipe.condition_tree_more"));
        }
        var result = Component.empty();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) result.append("\n");
            result.append(lines.get(index));
        }
        return result;
    }

    // 返回是否因显示上限截断，未完成草稿节点本身仍按原结构展示。
    private static boolean appendTree(@Nullable RuleValue node, String prefix, String connector, int depth,
                                  int limit, List<Component> lines, Function<RuleValue, Component> leafSummary) {
        if (lines.size() >= limit || depth > ConditionLimits.MAX_DEPTH) return true;
        if (node == null || !node.isMapping()) {
            lines.add(Component.literal(prefix + connector).append(Component.translatable(UI + "tree.incomplete")));
            return false;
        }
        String op = node.text(RuleFields.OP, "");
        Component label;
        List<RuleValue> children = List.of();
        switch (op) {
            case RuleFields.OP_LEAF -> {
                RuleValue condition = node.child(RuleFields.CONDITION);
                label = condition != null && condition.isMapping() ? leafSummary.apply(condition)
                        : Component.translatable(UI + "tree.incomplete");
            }
            case RuleFields.OP_ALL_OF, RuleFields.OP_ANY_OF -> {
                label = Component.translatable(UI + (RuleFields.OP_ALL_OF.equals(op)
                        ? "recipe.tree_all" : "recipe.tree_or")).append(" ").append(Component.translatable(UI
                        + (RuleFields.OP_ALL_OF.equals(op) ? "tree.operator.all" : "tree.operator.any")));
                RuleValue terms = node.child(RuleFields.TERMS);
                children = terms != null && terms.isList() && !terms.items().isEmpty()
                        ? terms.items() : java.util.Collections.singletonList(null);
            }
            case RuleFields.OP_INVERTED -> {
                label = Component.translatable(UI + "recipe.tree_not").append(" ")
                        .append(Component.translatable(UI + "tree.operator.not"));
                children = java.util.Collections.singletonList(node.child(RuleFields.TERM));
            }
            default -> label = Component.translatable(UI + "tree.incomplete");
        }
        lines.add(Component.literal(prefix + connector).append(label));
        String childPrefix = prefix + (connector.isEmpty() ? "" : BRANCH.equals(connector) ? CONTINUATION : INDENT);
        for (int index = 0; index < children.size(); index++) {
            String branch = index + 1 == children.size() ? LAST_BRANCH : BRANCH;
            if (appendTree(children.get(index), childPrefix, branch, depth + 1, limit, lines, leafSummary)) return true;
        }
        return false;
    }
}
