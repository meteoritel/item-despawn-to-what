package com.meteorite.itemdespawntowhat.core.api.scheduler;

/**
 * 已入队任务的句柄契约：让契约层在不依赖具体队列实现的前提下引用、查询与精确取消一个已排期任务。
 * 运行时层的调度实现返回该接口的实现；契约层只允许通过它读取排期信息或发起取消，
 * 不得假设句柄是可比较、可序列化或长期有效的对象。
 */
public interface ScheduledTaskHandle {

    /**
     * 返回该句柄承载的任务本体。
     *
     * @return 入队时提交的任务，永不为 {@code null}
     */
    ServerTask task();

    /**
     * 返回任务当前的到期游戏刻。
     * 任务让出或重试后会按新的到期刻重新入队，因此该值可能在任务终态之前变化。
     *
     * @return 到期游戏刻
     */
    long dueTick();

    /**
     * 返回任务的分类，用于队列统计与观测归类。
     * 委托给 {@link #task()}，保证句柄与任务本体的分类始终一致。
     *
     * @return 任务分类，永不为 {@code null}
     */
    ServerTaskKind kind();

    /**
     * 判断任务是否已进入终态（完成、失败或取消）。
     * 终态任务不会再被执行，也不会再被队列统计计入待处理。
     *
     * @return 已进入终态返回 {@code true}
     */
    boolean terminal();

    /**
     * 精确取消该任务：终态任务返回 {@code false}，其余情况转为取消态并回退待处理计数。
     * 取消是幂等的，且必须把取消原因上报给调度器与任务本体，不做静默丢弃。
     *
     * @param reason 取消原因，用于调度器分类计数与任务的生命周期回调
     * @return 本次调用真正取消了任务返回 {@code true}；任务已是终态返回 {@code false}
     */
    boolean cancel(CancelReason reason);
}
