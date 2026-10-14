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

    /** 本预算周期对应的游戏刻，由调度器在 {@link #begin(long, long, int)} 时写入。 */
    public long gameTime() {
        return gameTime;
    }

    /** 本周期的时间上限（纳秒），已在入参处按最小 1 纳秒归一。 */
    public long budgetNanos() {
        return budgetNanos;
    }

    /** 本周期的全局工作量上限，已在入参处按最小 1 归一。 */
    public int maxWorkUnits() {
        return maxWorkUnits;
    }

    /** 当前已用时间（纳秒）；结算后返回本 tick 冻结的最终值。 */
    public long usedNanos() {
        return frozen ? frozenNanos : Math.max(0L, System.nanoTime() - startedNanos);
    }

    /** 本周期已记入的工作量：在 maxWorkUnits 处截断，不会越界。 */
    public int usedWorkUnits() {
        return usedWorkUnits;
    }

    /** 剩余可用工作量（负数归零），供任务在单步内自行节流。 */
    public int remainingWorkUnits() {
        return Math.max(0, maxWorkUnits - usedWorkUnits);
    }

    /** 本周期搬运的到期任务数：与业务工作量分开计数，以区分排队成本与执行成本。 */
    public int drainedTasks() {
        return drainedTasks;
    }

    /** 时间上限是否已耗尽；标记只由记账与结算提交，本方法不改变它。 */
    public boolean timeExpired() {
        return timeExpired;
    }

    /** 工作量上限是否已耗尽；标记只由记账与结算提交，本方法不改变它。 */
    public boolean workUnitsExpired() {
        return workUnitsExpired;
    }

    /** 预算耗尽时是否仍有待推进任务：仅用于解释观测结论，不参与调度决策。 */
    public boolean workRemaining() {
        return workRemaining;
    }

    /**
     * 纯查询：时间或工作量任一耗尽即返回 true，不修改任何耗尽标记。
     * 调用方必须在每个任务之间检查；耗尽标记只由记账入口提交，保证判定可被重复调用而不产生副作用。
     *
     * @return 本 tick 预算是否已耗尽
     */
    public boolean exhausted() {
        return usedWorkUnits >= maxWorkUnits || System.nanoTime() >= deadlineNanos;
    }

    // 记入工作量消耗：超过剩余额度时截断，保证不会越过本 tick 上限，并在计费提交处提交耗尽标记。
    public void charge(int workUnits) {
        usedWorkUnits = Math.min(maxWorkUnits, usedWorkUnits + Math.max(0, workUnits));
        commitExhaustion();
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
        // 结算时补提交耗尽标记：时间耗尽可能发生在最后一次记账之后，没有任何后续记账可承担该提交
        commitExhaustion();
    }

    // 按当前已用额度提交耗尽标记：由记账与结算触发，判定本身（exhausted）保持无副作用。
    private void commitExhaustion() {
        if (usedWorkUnits >= maxWorkUnits) {
            workUnitsExpired = true;
        }
        if (System.nanoTime() >= deadlineNanos) {
            timeExpired = true;
        }
    }

    /**
     * 冻结当前统计为不可变快照，供观测与开发工具读取。
     *
     * @return 本 tick 的预算统计快照
     */
    public ServerTickBudgetSnapshot snapshot() {
        return new ServerTickBudgetSnapshot(gameTime, budgetNanos, usedNanos(), usedWorkUnits, maxWorkUnits,
                drainedTasks, timeExpired, workUnitsExpired, workRemaining);
    }
}
