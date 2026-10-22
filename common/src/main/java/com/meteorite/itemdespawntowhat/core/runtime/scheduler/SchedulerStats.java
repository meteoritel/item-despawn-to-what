package com.meteorite.itemdespawntowhat.core.runtime.scheduler;

import com.meteorite.itemdespawntowhat.core.api.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;

import java.util.Map;

/**
 * 调度器的全局只读统计：跨维度合并，是判断预算、积压与取消是否正常的主要观测面。
 * dropped 始终为 0：调度器不允许任何静默丢弃，取消必须带原因。
 * 追加的 lastMaxReadyDelayTicks/lastDrainedTasks 是「最近一 tick」的服务器级样本，
 * drainedTicks/maxDrainedInTick 是自启动以来的累计，用于判断同一 dueTick 桶是否被平滑窗口铺开。
 * lastMaxStepWorkUnits 是最近一 tick 的最大单步计费工作量，maxStepWorkUnits 是自启动以来的峰值；
 * 单步计费取「声明回执」与「步骤内实际上报」的较大者，二者与预算快照共用同一口径。
 * lastCancelledInTick/maxCancelledInTick 是取消清理的单 tick 样本：清理任务逐槽取消，单 tick 量受 cancels_per_step 约束。
 */
public record SchedulerStats(long ticks, long steps, long released, long failed, long drained, long cancelled,
                             long dropped, long exhaustedByTime, long exhaustedByWorkUnits, int pending,
                             int peakPending, int realms, long maxReadyDelayTicks, long lastBacklogClearTicks,
                             long lastMaxReadyDelayTicks, long lastDrainedTasks, long drainedTicks,
                             long maxDrainedInTick, int lastMaxStepWorkUnits, int maxStepWorkUnits,
                             int lastCancelledInTick, int maxCancelledInTick,
                             Map<CancelReason, Long> cancelledByReason, Map<ServerTaskKind, KindStats> perKind) {
}
