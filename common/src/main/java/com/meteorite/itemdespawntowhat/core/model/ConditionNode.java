package com.meteorite.itemdespawntowhat.core.model;

import java.util.List;

/**
 * 递归条件节点：组合节点（all_of / any_of / inverted）与具体条件叶（leaf）分离。
 * 组合节点的紧凑构造器只做不可变化拷贝，不做非空校验：空 terms 是编辑器中间态，必须可构造、可序列化；
 * 非空性与规模限制由 ConditionExpression#isStructurallyValid() 与解码期的条件负责。
 */
public sealed interface ConditionNode
        permits ConditionNode.AllOf, ConditionNode.AnyOf, ConditionNode.Inverted, ConditionNode.Leaf {

    /**
     * 合取组合：全部子节点成立才成立（空 terms 在求值期视为 MATCH，仅供中间态使用）。
     */
    record AllOf(List<ConditionNode> terms) implements ConditionNode {

        public AllOf {
            terms = List.copyOf(terms);
        }
    }

    /**
     * 析取组合：任一子节点成立即成立（空 terms 在求值期视为 NO_MATCH，仅供中间态使用）。
     */
    record AnyOf(List<ConditionNode> terms) implements ConditionNode {

        public AnyOf {
            terms = List.copyOf(terms);
        }
    }

    /**
     * 取反：MATCH 与 NO_MATCH 互换，UNAVAILABLE 与 ERROR 原样保留（不吞掉「判不了」与「出错」）。
     */
    record Inverted(ConditionNode term) implements ConditionNode {
    }

    /**
     * 条件叶：承载一个具体条件参数对象，叶级取反已删除，取反一律用 Inverted。
     */
    record Leaf(Condition condition) implements ConditionNode {
    }
}
