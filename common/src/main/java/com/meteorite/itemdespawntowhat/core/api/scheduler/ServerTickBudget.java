package com.meteorite.itemdespawntowhat.core.api.scheduler;

/**
 * 一个服务器 tick 的共享软预算：时间上限与全局工作量上限同时生效。
 * 到期桶搬运、就绪提取与任务执行都必须从这里扣费，任何路径都不能绕过预算。
 * 时间预算只能在任务之间检查，单次原版世界操作无法被安全抢占。
 */
public final class ServerTickBudget {

    private long gameTime;
    private long budgetNanos;
    private int maxWorkUnits;
    private long startedNanos;
    private long deadlineNanos;
    private int usedWorkUnits;
    private int drainedTasks;
    private boolean timeExpired;
    private boolean workUnitsExpired;
    private boolean workRemaining;
    private boolean frozen;
    private long frozenNanos;

    /**
     * 开始一个新的 tick 预算周期：重置全部计数与耗尽标记，并立即开始计时。
     * 仅由调度器在每 tick 推进开始时调用一次，业务代码与任务实现不得调用。
     *
     * @param gameTime 本 tick 对应的游戏刻
     * @param budgetNanos 本 tick 允许消耗的纳秒上限，小于 1 时按 1 处理，避免预算恒为耗尽
     * @param maxWorkUnits 本 tick 允许消耗的全局工作量上限，小于 1 时按 1 处理
     */
    public void begin(long gameTime, long budgetNanos, int maxWorkUnits) {
        this.gameTime = gameTime;
        this.budgetNanos = Math.max(1L, budgetNanos);
        this.maxWorkUnits = Math.max(1, maxWorkUnits);
        this.startedNanos = System.nanoTime();
        this.deadlineNanos = startedNanos + this.budgetNanos;
        this.usedWorkUnits = 0;
        this.drainedTasks = 0;
        this.timeExpired = false;
        this.workUnitsExpired = false;
        this.workRemaining = false;
        this.frozen = false;
        this.frozenNanos = 0L;
    }

    public long gameTime() {
        return gameTime;
    }

    public long budgetNanos() {
        return budgetNanos;
    }

    public int maxWorkUnits() {
        return maxWorkUnits;
    }

    // 当前已用时间（纳秒）；结算后返回本 tick 的最终值。
    public long usedNanos() {
        return frozen ? frozenNanos : Math.max(0L, System.nanoTime() - startedNanos);
    }

    public int usedWorkUnits() {
        return usedWorkUnits;
    }

    public int remainingWorkUnits() {
        return Math.max(0, maxWorkUnits - usedWorkUnits);
    }

    public int drainedTasks() {
        return drainedTasks;
    }

    public boolean timeExpired() {
        return timeExpired;
    }

    public boolean workUnitsExpired() {
        return workUnitsExpired;
    }

    public boolean workRemaining() {
        return workRemaining;
    }

    // 时间或工作量任一耗尽即返回 true；调用方必须在每个任务之间检查。
    public boolean exhausted() {
        if (usedWorkUnits >= maxWorkUnits) {
            workUnitsExpired = true;
            return true;
        }
        if (System.nanoTime() >= deadlineNanos) {
            timeExpired = true;
            return true;
        }
        return false;
    }

    // 记入工作量消耗；超过剩余额度时截断，保证不会越过本 tick 上限。
    public void charge(int workUnits) {
        usedWorkUnits = Math.min(maxWorkUnits, usedWorkUnits + Math.max(0, workUnits));
    }

    /**
     * 记入一次到期任务搬运的开销：固定消耗 1 个工作单位，并单独计数。
     * 搬运与任务执行分开计数，是为了让观测能区分"排队调度成本"与"业务执行成本"。
     */
    public void chargeDrain() {
        charge(1);
        drainedTasks++;
    }

    /**
     * 标记本 tick 预算耗尽时仍有待推进任务，含已到期但未搬运的部分。
     * 该标记用于解释"预算耗尽但队列非空"的观测结论，不参与调度决策。
     */
    public void markWorkRemaining() {
        workRemaining = true;
    }

    /**
     * 结束本 tick 并冻结耗时：之后 {@link #usedNanos()} 返回本 tick 的最终值。
     * 冻结而不是继续读时钟，是为了让 tick 结束后的统计与快照保持稳定。
     */
    public void finish() {
        frozenNanos = Math.max(0L, System.nanoTime() - startedNanos);
        frozen = true;
    }

    public ServerTickBudgetSnapshot snapshot() {
        return new ServerTickBudgetSnapshot(gameTime, budgetNanos, usedNanos(), usedWorkUnits, maxWorkUnits,
                drainedTasks, timeExpired, workUnitsExpired, workRemaining);
    }
}
