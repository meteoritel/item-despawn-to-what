package com.meteorite.itemdespawntowhat.core.runtime.scheduler;

/**
 * 任务单步执行的结果状态。
 * 让出与重试都会把任务按延迟重新入队，不算完成也不算失败。
 */
public enum StepOutcome {
    // 本步已完成，任务可以释放。
    DONE,
    // 主动让出预算，稍后继续（可续接进度）。
    YIELD,
    // 本步未完成，需要按延迟重试。
    RETRY,
    // 本步失败，记录后释放；预算耗尽不是失败。
    FAILED
}
