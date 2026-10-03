package com.meteorite.itemdespawntowhat.core.runtime.scheduler;

/**
 * 把既有的无参动作适配成 ServerTask：一次调用即完成，固定消耗 1 个工作单位。
 * 取消语义与原 TickScheduler.Task 一致：被取消的动作不会再执行，业务侧自行负责清理。
 */
public final class RunnableTask implements ServerTask {

    private final ServerTaskKind kind;
    private final String name;
    private final Runnable action;

    public RunnableTask(ServerTaskKind kind, String name, Runnable action) {
        this.kind = kind == null ? ServerTaskKind.MAINTENANCE : kind;
        this.name = name == null ? "unnamed" : name;
        this.action = action;
    }

    @Override
    public ServerTaskKind kind() {
        return kind;
    }

    @Override
    public String tracingName() {
        return name;
    }

    // 动作可能抛异常，由调度器统一捕获并计为失败。
    @Override
    public StepResult step(ServerTickBudget budget) {
        if (action != null) {
            action.run();
        }
        return StepResult.done(1);
    }
}
