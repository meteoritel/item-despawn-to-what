package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionLimits;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 条件树共用校验：限额判定、节点状态与违规项。
 *
 * <p>缩进摘要（{@link UiConditionTreeSummary}）与画布编辑器（{@link UiConditionGraphEditor}）共用本类，
 * 结构校验、状态判定与 {@link Issue} 类型只有一份来源，避免两种渲染各写一遍后行为漂移。</p>
 *
 * <p>深度一律按「根节点为 1」计数；叶与节点的序号按深度优先扫描顺序累计，与既有额度语义一致。</p>
 */
public final class ConditionTreeInspection {

    // 层级引导阈值：深度超过该值只提示「层级较深」，不计入违规项
    public static final int DEEP_HINT_DEPTH = 6;

    // 工具类，禁止实例化
    private ConditionTreeInspection() {
    }

    // 违规项种类
    public enum IssueKind {
        // 组合节点没有任何子项
        INCOMPLETE_GROUP,
        // 缺少条件对象
        MISSING_CONDITION,
        // 超过最大深度
        TOO_DEEP,
        // 超过最大节点数
        TOO_MANY_NODES,
        // 超过最大叶数
        TOO_MANY_LEAVES
    }

    // 违规项：种类 + 节点路径 + 可直接展示的文本
    public record Issue(IssueKind kind, String path, Component label) {
    }

    // 分组是否为空（空组合或空 NOT 视为结构不完整）
    public static boolean isIncomplete(ConditionNode node) {
        if (node instanceof ConditionNode.Inverted(var term)) {
            return term == null;
        }
        if (node instanceof ConditionNode.AllOf(var terms)) {
            return terms.isEmpty();
        }
        if (node instanceof ConditionNode.AnyOf(var terms)) {
            return terms.isEmpty();
        }
        return false;
    }

    // 叶是否缺少条件对象
    public static boolean isLeafMissing(ConditionNode node) {
        return node instanceof ConditionNode.Leaf(var condition) && condition == null;
    }

    // 深度、节点数或叶数是否超限
    public static boolean isOverflow(int depth, int nodeOrdinal, int leafOrdinal) {
        return depth > ConditionLimits.MAX_DEPTH
                || nodeOrdinal > ConditionLimits.MAX_NODES
                || leafOrdinal > ConditionLimits.MAX_LEAVES;
    }

    // 深度是否只触发层级引导提示
    public static boolean isDeep(int depth) {
        return depth > DEEP_HINT_DEPTH;
    }

    // 单个节点产生的违规项（按种类顺序追加，供两种渲染共同使用）
    public static List<Issue> nodeIssues(ConditionNode node, String path, int depth, int nodeOrdinal, int leafOrdinal) {
        List<Issue> issues = new ArrayList<>();
        if (isIncomplete(node)) {
            issues.add(new Issue(IssueKind.INCOMPLETE_GROUP, path,
                    Component.translatable("gui.itemdespawntowhat.edit.issue.incomplete_group")));
        }
        if (isLeafMissing(node)) {
            issues.add(new Issue(IssueKind.MISSING_CONDITION, path,
                    Component.translatable("gui.itemdespawntowhat.edit.issue.missing_condition")));
        }
        if (depth > ConditionLimits.MAX_DEPTH) {
            issues.add(new Issue(IssueKind.TOO_DEEP, path, Component.translatable(
                    "gui.itemdespawntowhat.edit.issue.too_deep", depth, ConditionLimits.MAX_DEPTH)));
        }
        if (nodeOrdinal > ConditionLimits.MAX_NODES) {
            issues.add(new Issue(IssueKind.TOO_MANY_NODES, path, Component.translatable(
                    "gui.itemdespawntowhat.edit.issue.too_many_nodes", nodeOrdinal, ConditionLimits.MAX_NODES)));
        }
        if (leafOrdinal > ConditionLimits.MAX_LEAVES) {
            issues.add(new Issue(IssueKind.TOO_MANY_LEAVES, path, Component.translatable(
                    "gui.itemdespawntowhat.edit.issue.too_many_leaves", leafOrdinal, ConditionLimits.MAX_LEAVES)));
        }
        return issues;
    }

    // 候选结构是否超限：返回首个提示文本，未超限返回 null（供 kit 树编辑器提交前校验）
    public static @Nullable Component validateCandidate(@Nullable ConditionNode root) {
        ConditionExpression candidate = new ConditionExpression(root);
        if (candidate.depth() > ConditionLimits.MAX_DEPTH) {
            return Component.translatable("gui.itemdespawntowhat.edit.issue.too_deep",
                    candidate.depth(), ConditionLimits.MAX_DEPTH);
        }
        if (candidate.nodeCount() > ConditionLimits.MAX_NODES) {
            return Component.translatable("gui.itemdespawntowhat.edit.issue.too_many_nodes",
                    candidate.nodeCount(), ConditionLimits.MAX_NODES);
        }
        if (candidate.leafCount() > ConditionLimits.MAX_LEAVES) {
            return Component.translatable("gui.itemdespawntowhat.edit.issue.too_many_leaves",
                    candidate.leafCount(), ConditionLimits.MAX_LEAVES);
        }
        return null;
    }
}
