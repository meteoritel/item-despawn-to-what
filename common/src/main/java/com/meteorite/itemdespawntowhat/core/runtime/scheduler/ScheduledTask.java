package com.meteorite.itemdespawntowhat.core.runtime.scheduler;

/**
 * 已入队任务的句柄：承载任务本体、当前到期 tick 与可精确取消的状态。
 * 取消是幂等的，并且一定会把原因上报给调度器计数，不做静默丢弃。
 */
public final class ScheduledTask {

    // 任务在队列中的生命周期状态。
    public enum State {
        // 在到期桶中等待搬运。
        PENDING,
        // 已搬入就绪队列，等待执行。
        READY,
        // 已被调度器取出，正在执行。
        RUNNING,
        // 已取消（规则重载、维度卸载、停服、实体移除或场景停止）。
        CANCELLED,
        // 已完成。
        DONE,
        // 已失败。
        FAILED
    }

    private final ServerTask task;
    private long dueTick;
    private RealmQueues owner;
    private State state = State.PENDING;

    ScheduledTask(ServerTask task, long dueTick) {
        this.task = task;
        this.dueTick = dueTick;
    }

    public ServerTask task() {
        return task;
    }

    public long dueTick() {
        return dueTick;
    }

    public ServerTaskKind kind() {
        return task.kind();
    }

    public State state() {
        return state;
    }

    // 已完成、失败或取消的任务不会再被执行。
    public boolean terminal() {
        return state == State.CANCELLED || state == State.DONE || state == State.FAILED;
    }

    // 精确取消：幂等；仅对仍在队列中的任务回退待处理计数。
    public boolean cancel(CancelReason reason) {
        if (terminal()) {
            return false;
        }
        State previous = state;
        state = State.CANCELLED;
        if (owner != null) {
            owner.onTaskCancelled(this, previous, reason);
        }
        task.onCancelled(reason);
        return true;
    }

    // 由队列在入队时建立归属，出队后归属失效。
    void attach(RealmQueues owner) {
        this.owner = owner;
    }

    void detach() {
        this.owner = null;
    }

    // 让出或重试后按新的到期 tick 重新入队。
    void requeue(long dueTick) {
        this.dueTick = dueTick;
        this.state = State.PENDING;
    }

    void markReady() {
        state = State.READY;
    }

    void markRunning() {
        state = State.RUNNING;
    }

    void markDone() {
        state = State.DONE;
        owner = null;
    }

    void markFailed() {
        state = State.FAILED;
        owner = null;
    }
}
