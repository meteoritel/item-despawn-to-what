package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * 条件树的遍历、统计与不可变结构变换工具：条件树算法集中在这里，求值、校验、编辑器共用。
 * 结构变换（replaceAt / updateTermsAt）一律返回新树、绝不就地修改入参，也不产生 null 中间态。
 * 路径语义固定为：路径元素是「目标节点在其父节点 terms / term 中的下标」，根节点用空列表表示；
 * Inverted 只有一个子节点，因此只有下标 0 合法；非法路径一律返回原树并记一条 WARN 日志（不抛异常）。
 */
public final class ConditionTrees {

    private static final Logger LOGGER = LogManager.getLogger();

    // 工具类不创建实例。
    private ConditionTrees() {}

    // 遍历全部条件叶，深度优先且保持声明顺序
    public static void forEachLeaf(@Nullable ConditionNode node, Consumer<Condition> visitor) {
        forEachLeaf(node, "", (path, condition) -> visitor.accept(condition));
    }

    // 带 JSON 路径的叶遍历：路径与 JSON 形状一致，如 conditions.terms[0].condition，供校验期定位字段
    public static void forEachLeaf(@Nullable ConditionNode node, String basePath, BiConsumer<String, Condition> visitor) {
        switch (node) {
            case null -> { }
            case ConditionNode.Leaf(Condition condition) ->
                    visitor.accept(basePath + "." + RuleFields.CONDITION, condition);
            case ConditionNode.Inverted(ConditionNode term) ->
                    forEachLeaf(term, basePath + "." + RuleFields.TERM, visitor);
            case ConditionNode.AllOf(List<ConditionNode> terms) -> forEachTerms(terms, basePath, visitor);
            case ConditionNode.AnyOf(List<ConditionNode> terms) -> forEachTerms(terms, basePath, visitor);
        }
    }

    // 叶数：叶节点计 1，组合节点取子节点之和
    public static int leafCount(@Nullable ConditionNode node) {
        return switch (node) {
            case null -> 0;
            case ConditionNode.Leaf ignored -> 1;
            case ConditionNode.Inverted(ConditionNode term) -> leafCount(term);
            case ConditionNode.AllOf(List<ConditionNode> terms) -> sumLeaves(terms);
            case ConditionNode.AnyOf(List<ConditionNode> terms) -> sumLeaves(terms);
        };
    }

    // 节点数：所有节点（含组合节点与叶）都计入
    public static int nodeCount(@Nullable ConditionNode node) {
        return switch (node) {
            case null -> 0;
            case ConditionNode.Inverted(ConditionNode term) -> 1 + nodeCount(term);
            case ConditionNode.AllOf(List<ConditionNode> terms) -> 1 + sumNodes(terms);
            case ConditionNode.AnyOf(List<ConditionNode> terms) -> 1 + sumNodes(terms);
            case ConditionNode.Leaf ignored -> 1;
        };
    }

    // 深度：根节点为 1，空树为 0，组合节点取子节点最大深度加一
    public static int depth(@Nullable ConditionNode node) {
        return switch (node) {
            case null -> 0;
            case ConditionNode.Inverted(ConditionNode term) -> 1 + depth(term);
            case ConditionNode.AllOf(List<ConditionNode> terms) -> 1 + maxDepth(terms);
            case ConditionNode.AnyOf(List<ConditionNode> terms) -> 1 + maxDepth(terms);
            case ConditionNode.Leaf ignored -> 1;
        };
    }

    // 按路径替换指定节点，返回新树（纯函数：不修改入参，未触及的兄弟节点保持原引用，结构共享）
    // 路径语义：path 的每个元素是「目标节点在其父节点 terms / term 中的下标」，根路径用空列表表示；
    // Inverted 只有一个子节点，所以只有下标 0 合法；路径想深入 Leaf 必然非法（Leaf 没有子节点）。
    // 失败语义（与实现严格一致）：path 为 null、下标越界、路径穿过 Leaf、或试图把子节点替换成 null（会产出 null 中间态）时，
    // 一律返回原树并记一条 WARN 日志，绝不抛异常；path 为空表示替换根，此时 replacement 允许为 null（表示清空整棵条件树）。
    public static @Nullable ConditionNode replaceAt(@Nullable ConditionNode root, List<Integer> path,
                                                    @Nullable ConditionNode replacement) {
        if (path == null) {
            warnInvalidPath("replaceAt", null, "path 不能为 null");
            return root;
        }
        if (path.isEmpty()) {
            return replacement;
        }
        boolean[] replaced = {false};
        ConditionNode result = replaceAt(root, path, 0, replacement, replaced);
        if (!replaced[0]) {
            warnInvalidPath("replaceAt", path, "路径越界或类型不符");
            return root;
        }
        return result;
    }

    // 按路径取出组合节点的子节点列表做整体变换（删除子项 / 上移下移 / 切换组合类型都靠它），返回新树
    // 目标必须是组合节点：AllOf / AnyOf 传入其 terms 的不可变副本，Inverted 传入只含唯一 term 的单元素列表；
    // Leaf 没有 terms，视为非法路径。变换结果不得为 null、元素不得为 null（否则会产生 null 中间态）；
    // 目标为 Inverted 时结果必须恰好 1 个元素（要换结构请配合 replaceAt 使用）。
    // 失败语义同 replaceAt：返回原树并记一条 WARN 日志，绝不抛异常、绝不修改入参；operator 返回可变或不可变列表都会重新拷贝。
    public static @Nullable ConditionNode updateTermsAt(@Nullable ConditionNode root, List<Integer> path,
                                                        UnaryOperator<List<ConditionNode>> operator) {
        if (root == null || path == null || operator == null) {
            warnInvalidPath("updateTermsAt", path, "根节点或参数为 null，没有 terms 可变换");
            return root;
        }
        ConditionNode target = nodeAt(root, path);
        switch (target) {
            case null -> {
                warnInvalidPath("updateTermsAt", path, "路径越界或类型不符");
                return root;
            }
            case ConditionNode.Leaf ignored -> {
                warnInvalidPath("updateTermsAt", path, "叶节点没有 terms");
                return root;
            }
            // 文档语义是「Inverted 传入只含唯一 term 的单元素列表」，term 为 null 时无法成立，按类型不符失败
            case ConditionNode.Inverted(ConditionNode term) when term == null -> {
                warnInvalidPath("updateTermsAt", path, "inverted 节点的 term 为 null（结构非法）");
                return root;
            }
            default -> { }
        }
        List<ConditionNode> current = target instanceof ConditionNode.Inverted(ConditionNode term)
                ? List.of(term) : childrenOf(target);
        List<ConditionNode> updated;
        try {
            updated = operator.apply(List.copyOf(current));
        } catch (RuntimeException failure) {
            warnInvalidPath("updateTermsAt", path, "变换抛出异常: " + failure);
            return root;
        }
        if (updated == null) {
            warnInvalidPath("updateTermsAt", path, "变换结果不能为 null");
            return root;
        }
        for (ConditionNode child : updated) {
            if (child == null) {
                warnInvalidPath("updateTermsAt", path, "变换结果不能包含 null 子节点");
                return root;
            }
        }
        ConditionNode newTarget;
        if (target instanceof ConditionNode.Inverted) {
            if (updated.size() != 1) {
                warnInvalidPath("updateTermsAt", path, "inverted 必须恰好 1 个子节点");
                return root;
            }
            newTarget = new ConditionNode.Inverted(updated.getFirst());
        } else {
            newTarget = withChildren(target, updated);
        }
        if (path.isEmpty()) {
            return newTarget;
        }
        boolean[] replaced = {false};
        ConditionNode result = replaceAt(root, path, 0, newTarget, replaced);
        if (!replaced[0]) {
            warnInvalidPath("updateTermsAt", path, "路径越界或类型不符");
            return root;
        }
        return result;
    }

    // 结构变换的递归实现：只重建路径上的节点，未触及的兄弟节点保持原引用
    private static @Nullable ConditionNode replaceAt(@Nullable ConditionNode node, List<Integer> path, int index,
                                                     @Nullable ConditionNode replacement, boolean[] replaced) {
        if (node == null) {
            return null;
        }
        Integer slot = path.get(index);
        if (slot == null) {
            return node;
        }
        List<ConditionNode> children = childrenOf(node);
        if (slot < 0 || slot >= children.size()) {
            return node;
        }
        ConditionNode newChild;
        if (index == path.size() - 1) {
            if (replacement == null) {
                return node;
            }
            newChild = replacement;
        } else {
            newChild = replaceAt(children.get(slot), path, index + 1, replacement, replaced);
            if (!replaced[0]) {
                return node;
            }
        }
        List<ConditionNode> newChildren = new ArrayList<>(children);
        newChildren.set(slot, newChild);
        replaced[0] = true;
        return withChildren(node, newChildren);
    }

    // 按路径定位节点：根路径（空列表）返回根节点本身，越界或类型不符返回 null
    private static @Nullable ConditionNode nodeAt(@Nullable ConditionNode root, List<Integer> path) {
        ConditionNode current = root;
        for (Integer slot : path) {
            if (current == null || slot == null) {
                return null;
            }
            List<ConditionNode> children = childrenOf(current);
            if (slot < 0 || slot >= children.size()) {
                return null;
            }
            current = children.get(slot);
        }
        return current;
    }

    // 子节点列表：AllOf / AnyOf 取 terms，Inverted 取只含唯一 term 的单元素列表，Leaf 没有子节点
    private static List<ConditionNode> childrenOf(ConditionNode node) {
        return switch (node) {
            case ConditionNode.AllOf(List<ConditionNode> terms) -> terms;
            case ConditionNode.AnyOf(List<ConditionNode> terms) -> terms;
            // term 为 null 属结构非法（ConditionExpression#isStructurallyValid 已判不合法）；
            // 这里返回空列表而不是 List.of(null)，否则 List.of 本身会抛 NPE 把上层逻辑炸掉
            case ConditionNode.Inverted(ConditionNode term) -> term == null ? List.of() : List.of(term);
            case ConditionNode.Leaf ignored -> List.of();
        };
    }

    // 用新的子节点列表重建同类型节点（record 紧凑构造器会再做一次不可变拷贝）
    private static ConditionNode withChildren(ConditionNode node, List<ConditionNode> children) {
        if (node instanceof ConditionNode.AllOf) {
            return new ConditionNode.AllOf(children);
        }
        if (node instanceof ConditionNode.AnyOf) {
            return new ConditionNode.AnyOf(children);
        }
        if (node instanceof ConditionNode.Inverted) {
            return new ConditionNode.Inverted(children.getFirst());
        }
        return node;
    }

    // 非法路径统一记录：结构变换是纯函数，失败时不抛异常，只记一条 WARN 供编辑器排查
    private static void warnInvalidPath(String method, @Nullable List<Integer> path, String reason) {
        LOGGER.warn("条件树结构变换失败：方法={} 路径={} 原因={}", method, path, reason);
    }

    // 组合节点的 terms 路径段：conditions.terms[0]、conditions.terms[1] ...
    private static void forEachTerms(List<ConditionNode> terms, String basePath, BiConsumer<String, Condition> visitor) {
        for (int index = 0; index < terms.size(); index++) {
            forEachLeaf(terms.get(index), basePath + "." + RuleFields.TERMS + "[" + index + "]", visitor);
        }
    }

    // 叶数求和
    private static int sumLeaves(List<ConditionNode> terms) {
        int total = 0;
        for (ConditionNode term : terms) {
            total += leafCount(term);
        }
        return total;
    }

    // 节点数求和
    private static int sumNodes(List<ConditionNode> terms) {
        int total = 0;
        for (ConditionNode term : terms) {
            total += nodeCount(term);
        }
        return total;
    }

    // 子节点最大深度
    private static int maxDepth(List<ConditionNode> terms) {
        int max = 0;
        for (ConditionNode term : terms) {
            max = Math.max(max, depth(term));
        }
        return max;
    }
}
