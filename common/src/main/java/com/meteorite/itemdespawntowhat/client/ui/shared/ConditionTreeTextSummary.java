package com.meteorite.itemdespawntowhat.client.ui.shared;

import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.ConditionLimits;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 条件树的只读文本摘要：用缩进表达组合嵌套，叶节点显示条件名与参数摘要。
 *
 * <p>层次只用空格缩进表达，不画 {@code +--}／{@code ├─} 一类连接线：默认字体是比例字体，
 * 制表字形与空格的宽度互不相等，混排会让子行相对父行标签错位。</p>
 */
public final class ConditionTreeTextSummary {
    private static final String UI = "gui.itemdespawntowhat.edit.";
    private static final String INDENT = "  ";

    private ConditionTreeTextSummary() { }

    /**
     * 生成适合 tooltip 的多行树摘要；缺省条件表示无条件限制，未完成节点保留提示。
     *
     * @param root 条件树根，允许为空
     * @param maxLines 可用行数，至少保留两行；超出部分以明确提示收尾
     * @param leafLabel 条件叶标签格式器（条件名加参数摘要），由宿主注入
     * @return 不包含输入或产出信息的条件树摘要
     */
    public static Component format(@Nullable RuleValue root, int maxLines, Function<RuleValue, Component> leafLabel) {
        if (root == null || root.isNull()) return Component.translatable(UI + "summary.any_condition");
        int limit = Math.clamp(maxLines, 2, ConditionLimits.MAX_NODES);
        List<Component> lines = new ArrayList<>();
        if (appendTree(root, "", 1, limit, lines, leafLabel)) {
            if (lines.size() == limit) lines.removeLast();
            lines.add(Component.translatable(UI + "recipe.condition_tree_more").withStyle(ChatFormatting.GRAY));
        }
        var result = Component.empty();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) result.append("\n");
            result.append(lines.get(index));
        }
        return result;
    }

    // 返回是否因显示上限截断，未完成草稿节点本身仍按原结构展示。
    private static boolean appendTree(@Nullable RuleValue node, String indent, int depth, int limit,
                                      List<Component> lines, Function<RuleValue, Component> leafLabel) {
        if (lines.size() >= limit || depth > ConditionLimits.MAX_DEPTH) return true;
        if (node == null || !node.isMapping()) {
            lines.add(Component.literal(indent).append(incomplete()));
            return false;
        }
        String op = node.text(RuleFields.OP, "");
        Component label;
        List<RuleValue> children = List.of();
        switch (op) {
            case RuleFields.OP_LEAF -> {
                RuleValue condition = node.child(RuleFields.CONDITION);
                label = condition != null && condition.isMapping() ? leafLabel.apply(condition) : incomplete();
            }
            case RuleFields.OP_ALL_OF, RuleFields.OP_ANY_OF -> {
                boolean all = RuleFields.OP_ALL_OF.equals(op);
                RuleValue terms = node.child(RuleFields.TERMS);
                List<RuleValue> items = terms != null && terms.isList() ? terms.items() : List.of();
                label = Component.translatable(UI + "tree.group_label",
                        Component.translatable(UI + (all ? "tree.node.all" : "tree.node.any")), items.size())
                        .withStyle(ChatFormatting.GOLD);
                children = items.isEmpty() ? Collections.singletonList(null) : items;
            }
            case RuleFields.OP_INVERTED -> {
                label = Component.translatable(UI + "tree.operator.not").withStyle(ChatFormatting.AQUA);
                children = Collections.singletonList(node.child(RuleFields.TERM));
            }
            default -> label = incomplete();
        }
        lines.add(Component.literal(indent).append(label));
        String childIndent = indent + INDENT;
        for (RuleValue child : children) {
            if (appendTree(child, childIndent, depth + 1, limit, lines, leafLabel)) return true;
        }
        return false;
    }

    private static Component incomplete() {
        return Component.translatable(UI + "tree.incomplete").withStyle(ChatFormatting.RED);
    }
}
