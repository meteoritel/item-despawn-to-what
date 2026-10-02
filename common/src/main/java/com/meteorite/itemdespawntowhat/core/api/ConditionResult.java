package com.meteorite.itemdespawntowhat.core.api;

/**
 * 条件求值结果：四态而非布尔，区分「成立」「不成立」「判不了」「出错」。
 * 只有 MATCH 会让规则/效果通过；UNAVAILABLE 与 ERROR 一律不触发效果，且 inverted 节点不改变它们。
 */
public enum ConditionResult {

    // 条件成立
    MATCH,

    // 条件不成立
    NO_MATCH,

    // 上下文不足，无法判定
    UNAVAILABLE,

    // 求值过程抛出异常
    ERROR;

    // 是否成立：只有 MATCH 为真
    public boolean isMatch() {
        return this == MATCH;
    }
}
