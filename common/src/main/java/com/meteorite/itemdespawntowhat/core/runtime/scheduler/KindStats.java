package com.meteorite.itemdespawntowhat.core.runtime.scheduler;

/**
 * 按任务种类汇总的计数：待处理、已执行步骤、失败、重新入队以及本轮结束时仍未执行的就绪任务。
 * requeued 保持阶段2 起的「yielded + retried」合计语义，阶段7 才把两者拆开以便峰值归因：
 * YIELD 是预算/区块等临时让出，RETRY 是业务失败后的重试。
 * done 只统计真正释放的完成步骤；workUnits 是累计扣除的工作单位，lastMicros 是最近一 tick 该类任务的忙碌微秒（跨维度合并）。
 */
public record KindStats(long pending, long steps, long failed, long requeued, long deferred,
                        long done, long yielded, long retried, long workUnits, long lastMicros) {
}
