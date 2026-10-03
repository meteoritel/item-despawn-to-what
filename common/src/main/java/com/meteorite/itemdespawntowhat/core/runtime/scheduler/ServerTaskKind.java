package com.meteorite.itemdespawntowhat.core.runtime.scheduler;

/**
 * 公共服务器预算调度器的任务种类。
 * 用于跨维度公平轮转、效果类工作量配额以及分类统计，不绑定任何具体业务类型。
 */
public enum ServerTaskKind {
    // 条件检查：自然到期检查、失败退避后的重试。
    CONDITION_CHECK,
    // 位置与候选搜索：阶段2 预留，现有实现仍合并在条件检查任务内。
    WORLD_SEARCH,
    // 一次性效果与转化产出。
    EFFECT,
    // 返还与账目结算：阶段6 使用。
    REBATE,
    // 维护类任务。
    MAINTENANCE
}
