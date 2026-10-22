package com.meteorite.itemdespawntowhat.core.api.scheduler;

/**
 * 任务单步执行的回执：声明本步消耗的工作量单位与下一次入队延迟。
 * 声明值只是计价下限：调度器每步还会读取步骤内通过 {@link StepWorkCounter} 上报的真实世界操作量，取较大者计费。
 * 下界为 1，保证任何步骤都会被计入预算。
 */
public record StepResult(StepOutcome outcome, int workUnits, int nextDelayTicks) {

    // 紧凑构造器做下界保护，避免实现返回零工作量绕过预算。
    public StepResult {
        outcome = outcome == null ? StepOutcome.DONE : outcome;
        workUnits = Math.max(1, workUnits);
        nextDelayTicks = Math.max(0, nextDelayTicks);
    }

    // 本步完成。
    public static StepResult done(int workUnits) {
        return new StepResult(StepOutcome.DONE, workUnits, 0);
    }

    // 主动让出：本轮不再执行，按 nextDelayTicks 重新入队。
    public static StepResult yield(int workUnits, int nextDelayTicks) {
        return new StepResult(StepOutcome.YIELD, workUnits, nextDelayTicks);
    }

    // 稍后重试：业务侧负责保证重试次数有上限。
    public static StepResult retry(int workUnits, int nextDelayTicks) {
        return new StepResult(StepOutcome.RETRY, workUnits, nextDelayTicks);
    }

    // 本步失败：记录失败计数，任务不再入队。
    public static StepResult failed(int workUnits) {
        return new StepResult(StepOutcome.FAILED, workUnits, 0);
    }
}
