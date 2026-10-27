package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.shared.DescriptionLine;
import com.meteorite.itemdespawntowhat.client.ui.shared.DescriptionLine.Tone;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.ConditionLimits;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 条件树的结构化说明：用缩进表达组合嵌套，叶节点在标题行下方展开参数说明。
 *
 * <p><b>职责</b>：只负责树结构与分组语义（全部满足 / 任一满足 / 非），叶节点参数交给
 * {@link ConditionDescription}；两者都输出 {@link DescriptionLine}，由悬停面板统一排版取色。</p>
 *
 * <p><b>层次</b>：缩进按层级交给渲染方换算像素，不拼空格也不画连接线；分组标题使用运算符语气色，
 * 与画布上的分组卡片保持同一套语义色。结构不完整或节点不可读时显示问题语气提示，不隐藏节点。</p>
 */
public final class ConditionTreeDescription {

    private static final String UI = TypeLabels.UI_PREFIX;
    private static final int MAX_DEPTH = ConditionLimits.MAX_DEPTH;

    private ConditionTreeDescription() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 生成适合悬停面板展示的条件树说明行；缺省条件表示无条件限制。
     *
     * @param root 条件树根，允许为空
     * @return 结构化说明行；无条件限制时返回一条说明，永不返回空列表
     */
    public static List<DescriptionLine> lines(@Nullable RuleValue root) {
        List<DescriptionLine> lines = new ArrayList<>();
        if (root == null || root.isNull()) {
            lines.add(DescriptionLine.note(Component.translatable(UI + "summary.any_condition"), 0));
            return lines;
        }
        append(root, 0, 1, lines);
        return lines;
    }

    // 未完成草稿节点保留原结构，用问题语气提示而不是丢弃
    private static void append(@Nullable RuleValue node, int indent, int depth, List<DescriptionLine> lines) {
        if (depth > MAX_DEPTH) {
            lines.add(DescriptionLine.text(incomplete(), Tone.DANGER, indent));
            return;
        }
        if (node == null || !node.isMapping()) {
            lines.add(DescriptionLine.text(incomplete(), Tone.DANGER, indent));
            return;
        }
        String op = node.text(RuleFields.OP, "");
        switch (op) {
            case RuleFields.OP_LEAF -> appendLeaf(node, indent, lines);
            case RuleFields.OP_ALL_OF, RuleFields.OP_ANY_OF -> appendGroup(node, op, indent, depth, lines);
            case RuleFields.OP_INVERTED -> {
                lines.add(DescriptionLine.text(Component.translatable(UI + "tree.operator.not"), Tone.NOT, indent));
                append(node.child(RuleFields.TERM), indent + 1, depth + 1, lines);
            }
            default -> lines.add(DescriptionLine.text(incomplete(), Tone.DANGER, indent));
        }
    }

    // 叶节点：条件名作标题，参数说明缩进一层展开
    private static void appendLeaf(RuleValue node, int indent, List<DescriptionLine> lines) {
        RuleValue condition = node.child(RuleFields.CONDITION);
        if (condition == null || !condition.isMapping()) {
            lines.add(DescriptionLine.text(incomplete(), Tone.DANGER, indent));
            return;
        }
        lines.add(DescriptionLine.text(leafLabel(condition), Tone.TITLE, indent));
        lines.addAll(ConditionDescription.lines(condition, indent + 1));
    }

    // 分组节点：运算符语气标题 + 子节点，空分组按不完整提示
    private static void appendGroup(RuleValue node, String op, int indent, int depth, List<DescriptionLine> lines) {
        boolean all = RuleFields.OP_ALL_OF.equals(op);
        RuleValue terms = node.child(RuleFields.TERMS);
        List<RuleValue> items = terms != null && terms.isList() ? terms.items() : List.of();
        lines.add(DescriptionLine.text(Component.translatable(UI + "tree.group_label",
                Component.translatable(UI + (all ? "tree.node.all" : "tree.node.any")), items.size()),
                all ? Tone.ALL : Tone.ANY, indent));
        if (items.isEmpty()) {
            lines.add(DescriptionLine.text(incomplete(), Tone.DANGER, indent + 1));
            return;
        }
        for (RuleValue child : items) {
            append(child, indent + 1, depth + 1, lines);
        }
    }

    // 叶标题用条件类型名：参数已经在详情行展开，标题不再重复摘要
    private static Component leafLabel(RuleValue condition) {
        String type = RuleValue.typeOf(condition);
        ResourceLocation id = type == null ? null : ResourceLocation.tryParse(type);
        return id == null
                ? Component.translatable(UI + "summary.unknown_condition")
                : TypeLabels.conditionLabel(id);
    }

    private static Component incomplete() {
        return Component.translatable(UI + "tree.incomplete");
    }
}
