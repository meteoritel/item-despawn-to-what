package com.meteorite.itemdespawntowhat.core.runtime.scheduler;

/**
 * 单条队列（realm + 任务种类）的只读观测值。
 * 字段口径与调度器性能统计一致，便于复用已有的性能窗口与 debug 输出。
 */
public record QueueStats(int pending, int peakPending, long totalVisited, long lastMicros, long maxMicros,
                         long oldestDelay) {
}
