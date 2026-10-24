package com.meteorite.itemdespawntowhat.core.api.scheduler;

/**
 * 任务被取消的原因。
 * 只要求「不得静默丢弃」，每种原因都计入统计；持久返还的差异处理由恢复任务补齐。
 */
public enum CancelReason {
    // 规则重载：索引重建，既有检查与效果任务全部失效。
    RULE_RELOAD,
    // 维度卸载：该维度的任务离开调度器。
    DIMENSION_UNLOAD,
    // 停服：所有尚未开始的任务终止。
    SERVER_STOP,
    // 所属结算中止，子效果必须先终止，再把未消耗库存交给恢复任务。
    SETTLEMENT_ABORTED,
    // 源实体已被移除。
    ENTITY_REMOVED,
    // 开发场景主动停止。
    SCENARIO_STOP,
    // 自然寿命延后：物品临近原版消失，既有检查让位给重新排定的预留检查。
    NATURAL_EXPIRY_DEFERRED,
    // 重排：旧检查句柄先被取消，再为新句柄让位；只允许经统一重排入口触发。
    RESCHEDULED,
    // 追踪释放：检查已不再适用于该物品（无候选、永久禁转或到期未命中），句柄随追踪一起移除。
    TRACKING_RELEASED
}
