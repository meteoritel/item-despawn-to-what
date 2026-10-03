package com.meteorite.itemdespawntowhat.core.runtime.scheduler;

/**
 * 公共预算调度器的最小任务单元。
 * 实现者不得依赖任何平台类，也不得在单步内做无界循环；长循环必须自行拆成有界步骤。
 */
public interface ServerTask {

    // 任务种类，用于公平轮转、效果配额与分类统计。
    ServerTaskKind kind();

    // 稳定名称，仅用于日志与调试。
    String tracingName();

    // 执行一个有界步骤；返回本轮消耗的工作量与后续安排。
    StepResult step(ServerTickBudget budget);

    // 任务被取消时的通知，实现者可在这里记录业务状态。
    default void onCancelled(CancelReason reason) {
    }

    // 任务正常完成后的资源释放。
    default void onReleased() {
    }
}
