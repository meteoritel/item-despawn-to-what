package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.runtime.ConversionRuntime;
import com.meteorite.itemdespawntowhat.core.runtime.RuleIndex;
import com.meteorite.itemdespawntowhat.core.runtime.TickScheduler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** 从准备和预热之后统计窗口；服务端总成本与场景维度两条队列分别计量。 */
final class DebugPerformanceWindow {
    private final DebugMeasurements serverTimes = new DebugMeasurements();
    private final QueueWindow checks;
    private final QueueWindow effects;
    private final long started = System.nanoTime();
    private final long initialWorldTick;
    private long lastWorldTick;
    private long lastSample = started;
    private long maxInterval;
    private RuleIndex index;
    private int indexChanges;

    /** 队列窗口不使用生命周期峰值，也不把开始前的任务访问计入窗口。 */
    private static final class QueueWindow {
        private final DebugMeasurements times = new DebugMeasurements();
        private long previousVisited;
        private long visited;
        private long resets;
        private long pendingSum;
        private int maxPending;
        private int lastPending;
        private long maxDelay;

        // 用当前累计计数建立基线。
        private QueueWindow(TickScheduler.Stats baseline) {
            previousVisited = baseline.totalVisited();
            lastPending = baseline.pending();
            maxPending = lastPending;
        }

        // 只在维度时间推进时采集耗时；计数回退作为重置处理。
        private void add(TickScheduler.Stats stats) {
            if (stats.totalVisited() < previousVisited) { resets++; visited += stats.totalVisited(); }
            else { visited += stats.totalVisited() - previousVisited; }
            previousVisited = stats.totalVisited();
            times.add(stats.lastMicros());
            pendingSum += stats.pending();
            lastPending = stats.pending();
            maxPending = Math.max(maxPending, lastPending);
            maxDelay = Math.max(maxDelay, stats.oldestDelay());
        }

        // 分位数只在最终输出时计算，避免周期汇总反复排序。
        private JsonObject summary() {
            JsonObject data = times.summary();
            data.addProperty("visited_in_window", visited);
            data.addProperty("counter_resets", resets);
            data.addProperty("mean_pending_at_tick_end", times.size() == 0 ? 0 : (double) pendingSum / times.size());
            data.addProperty("max_pending_at_tick_end", maxPending);
            data.addProperty("final_pending", lastPending);
            data.addProperty("max_oldest_ready_delay_ticks", maxDelay);
            return data;
        }
    }

    // 准备与预热结束时建立窗口，日志开始输出不计入服务端原版耗时数组。
    DebugPerformanceWindow(ServerLevel level, ConversionRuntime runtime) {
        initialWorldTick = lastWorldTick = level.getGameTime();
        checks = new QueueWindow(runtime.queueStats(level, false));
        effects = new QueueWindow(runtime.queueStats(level, true));
        index = runtime.index();
    }

    // 两端 EndServerTick 均在原版耗时数组写入后派发，读取当前完整 tick。
    void sample(MinecraftServer server, ServerLevel level, ConversionRuntime runtime) {
        long now = System.nanoTime();
        maxInterval = Math.max(maxInterval, now - lastSample);
        lastSample = now;
        long[] times = server.getTickTimesNanos();
        serverTimes.add(times[Math.floorMod(server.getTickCount(), times.length)] / 1000);
        if (index != runtime.index()) { indexChanges++; index = runtime.index(); }
        if (lastWorldTick != level.getGameTime()) {
            lastWorldTick = level.getGameTime();
            checks.add(runtime.queueStats(level, false));
            effects.add(runtime.queueStats(level, true));
        }
    }

    // 按单调墙钟限制采样，避免低 TPS 时等待数倍时长。
    double elapsedSeconds() { return (lastSample - started) / 1_000_000_000D; }

    // 有界原始数组达到上限后必须停止，不对更长窗口伪造完整统计。
    boolean full() { return serverTimes.size() >= DebugMeasurements.MAX_SAMPLES; }

    // 最终统计清楚标注口径；队列属于场景维度，可能包括普通实体的后台工作。
    JsonObject summary(ServerLevel level) {
        JsonObject data = new JsonObject();
        double seconds = elapsedSeconds();
        data.addProperty("sampled_seconds", seconds);
        data.addProperty("sampled_ticks", serverTimes.size());
        data.addProperty("world_ticks_advanced", lastWorldTick - initialWorldTick);
        data.addProperty("observed_ticks_per_second", seconds == 0 ? 0 : serverTimes.size() / seconds);
        data.addProperty("max_tick_interval_ms", maxInterval / 1_000_000D);
        data.addProperty("sample_limit_reached", full());
        data.addProperty("index_changes", indexChanges);
        data.addProperty("queue_scope_dimension", level.dimension().location().toString());
        data.add("server_tick_cost", serverTimes.summary());
        data.add("checks", checks.summary());
        data.add("effects", effects.summary());
        return data;
    }
}
