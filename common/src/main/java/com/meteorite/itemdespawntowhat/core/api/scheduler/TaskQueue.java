package com.meteorite.itemdespawntowhat.core.api.scheduler;

/**
 * 排期请求契约：契约层向调度器提出"在指定游戏刻执行某个任务"这一能力的最小抽象。
 * 运行时层的调度器实现该接口；契约层与运行时观测接缝只依赖此接口，
 * 从而不需要知道队列、realm 归属与预算的具体实现。
 */
public interface TaskQueue {

    /**
     * 把任务排入指定 realm，并给出绝对的到期游戏刻。
     * 调用方自行把"延迟多少 tick"折算成绝对刻，避免队列与游戏时钟耦合。
     *
     * @param realmKey realm 标识，通常是维度键；同一 realm 的任务按到期刻与入队顺序推进
     * @param task 待执行任务，其单步执行必须是有界的
     * @param dueTick 绝对到期游戏刻，早于当前刻时按当刻就绪处理
     * @return 可用于查询与取消的句柄
     */
    ScheduledTaskHandle scheduleAt(Object realmKey, ServerTask task, long dueTick);
}
