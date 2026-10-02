package com.meteorite.itemdespawntowhat.core.model;

import org.jetbrains.annotations.Nullable;

/**
 * 条件表达式：整棵条件树只有一个根节点，root 为 null 表示无条件。
 * 旧版「组 × 叶」二维数组结构已废弃（见 docs/plan/plan-frontend-rewrite-contract.md §2），
 * 空表达式、空 all_of / any_of 都允许构造与序列化（编辑器中间态），非空性只在 isStructurallyValid() 与解码期强制。
 */
public record ConditionExpression(@Nullable ConditionNode root) {

    // 无条件：序列化时上层直接省略 conditions 字段，不得输出 null
    public static final ConditionExpression EMPTY = new ConditionExpression(null);

    // 是否无条件
    public boolean isEmpty() {
        return root == null;
    }

    // 叶节点数量
    public int leafCount() {
        return ConditionTrees.leafCount(root);
    }

    // 节点总数（组合节点与叶都计入）
    public int nodeCount() {
        return ConditionTrees.nodeCount(root);
    }

    // 树深度：根为 1，空表达式为 0
    public int depth() {
        return ConditionTrees.depth(root);
    }

    /**
     * 结构是否合法：空表达式合法；all_of / any_of 的 terms 至少 1 项；inverted 必须有 term；
     * 叶必须携带条件；叶数、节点数、深度都在 ConditionLimits 限额内。
     */
    public boolean isStructurallyValid() {
        if (root == null) {
            return true;
        }
        return hasLegalShape(root)
                && leafCount() <= ConditionLimits.MAX_LEAVES
                && nodeCount() <= ConditionLimits.MAX_NODES
                && depth() <= ConditionLimits.MAX_DEPTH;
    }

    // 递归检查组合节点的形状（不检查规模，规模由调用方统一比对）
    private static boolean hasLegalShape(ConditionNode node) {
        if (node instanceof ConditionNode.Leaf leaf) {
            return leaf.condition() != null;
        }
        if (node instanceof ConditionNode.Inverted inverted) {
            return inverted.term() != null && hasLegalShape(inverted.term());
        }
        if (node instanceof ConditionNode.AllOf allOf) {
            return hasLegalTerms(allOf.terms());
        }
        if (node instanceof ConditionNode.AnyOf anyOf) {
            return hasLegalTerms(anyOf.terms());
        }
        return false;
    }

    // 组合节点：terms 非空且每个子节点形状合法
    private static boolean hasLegalTerms(java.util.List<ConditionNode> terms) {
        if (terms.isEmpty()) {
            return false;
        }
        for (ConditionNode term : terms) {
            if (!hasLegalShape(term)) {
                return false;
            }
        }
        return true;
    }
}
