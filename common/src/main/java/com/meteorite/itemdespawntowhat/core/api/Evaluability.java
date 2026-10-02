package com.meteorite.itemdespawntowhat.core.api;

/**
 * 可求值性：条件在当前上下文里能否被判定。
 * 与「条件是否成立」分离：上下文不足（区块未加载、动态注册表未就绪等）必须与「不成立」区分，
 * 否则条件树的 inverted 节点会把「判不了」错误地变成「成立」。
 */
public enum Evaluability {

    // 当前上下文足以判定
    AVAILABLE,

    // 当前上下文不足以判定，求值结果为 ConditionResult.UNAVAILABLE
    UNAVAILABLE
}
