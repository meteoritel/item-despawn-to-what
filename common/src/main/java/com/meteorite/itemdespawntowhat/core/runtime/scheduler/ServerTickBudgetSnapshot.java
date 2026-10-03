package com.meteorite.itemdespawntowhat.core.runtime.scheduler;

/**
 * 单 tick 预算结算后的只读快照，用于性能窗口与 debug 输出。
 * usedNanos 是软预算的实际耗时，不是被抢占的精确值；调用方不得据此宣称 TPS 达标。
 */
public record ServerTickBudgetSnapshot(long gameTime, long budgetNanos, long usedNanos, int usedWorkUnits,
                                       int maxWorkUnits, int drainedTasks, boolean timeExpired,
                                       boolean workUnitsExpired, boolean workRemaining) {

    // 预算使用率，仅用于观测；预算为 0 时视为 0。
    public double usageRatio() {
        return budgetNanos <= 0L ? 0.0D : (double) usedNanos / (double) budgetNanos;
    }

    // 本 tick 的耗尽原因：time / work_units / none。
    public String exhaustionReason() {
        if (timeExpired) {
            return "time";
        }
        if (workUnitsExpired) {
            return "work_units";
        }
        return "none";
    }
}
