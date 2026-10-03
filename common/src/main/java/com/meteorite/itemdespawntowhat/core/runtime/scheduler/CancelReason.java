package com.meteorite.itemdespawntowhat.core.runtime.scheduler;

/**
 * 任务被取消的原因。
 * 阶段2 只要求「不得静默丢弃」，每种原因都计入统计；持久返还的差异处理由阶段6 补。
 */
public enum CancelReason {
    // 规则重载：索引重建，旧检查与效果任务全部失效。
    RULE_RELOAD,
    // 维度卸载：该维度的任务离开调度器。
    DIMENSION_UNLOAD,
    // 停服：所有尚未开始的任务终止。
    SERVER_STOP,
    // 源实体已被移除。
    ENTITY_REMOVED,
    // 开发场景主动停止。
    SCENARIO_STOP
}
