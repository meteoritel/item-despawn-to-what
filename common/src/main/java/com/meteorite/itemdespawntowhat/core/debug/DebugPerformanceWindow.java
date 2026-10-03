package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.runtime.ConversionRuntime;
import com.meteorite.itemdespawntowhat.core.runtime.RuleIndex;
import com.meteorite.itemdespawntowhat.core.runtime.SettlementLedger;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.KindStats;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.QueueStats;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.SchedulerStats;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerTickBudgetSnapshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.EnumMap;
import java.util.Locale;

/**
 * 从准备和预热之后统计窗口；服务端总成本、场景维度各任务种类队列与共享预算分别计量。
 * 级别约定：以 server_ 前缀或写在 server_* 段内的字段是跨维度合并的服务器级口径；
 * checks/effects/level_kinds 是绑定单个 ServerLevel 的本维度切片。
 * 不做 (维度×种类) 二维分位数：每个组合的样本量不足以支撑分位数结论，按种类在服务器级聚合即可。
 */
final class DebugPerformanceWindow {
    private final DebugMeasurements serverTimes = new DebugMeasurements();
    private final QueueWindow checks;
    private final QueueWindow effects;
    // 阶段7：本维度按任务种类的队列切片（level 级），把阶段2 的 checks/effects 两类口径扩到全部种类
    private final EnumMap<ServerTaskKind, QueueWindow> levelKindQueues = new EnumMap<>(ServerTaskKind.class);
    // 阶段7：跨维度合并的预算使用率、就绪延迟、到期搬运分布（server 级）
    private final DebugDistribution serverBudgetRatioPpm = new DebugDistribution();
    // 就绪延迟只在「存在就绪任务的 tick」采样（就绪数取 per_kind.deferred 之和），不是全 tick 平均：
    // 无就绪任务时延迟恒为 0，混入会把 p50/p95 稀释成 0、掩盖真实等待
    private final DebugDistribution serverReadyDelayTicks = new DebugDistribution();
    private final DebugDistribution serverDrainedPerTick = new DebugDistribution();
    // 阶段7：跨维度合并的单类步骤耗时（server 级），每个 kind 一条独立样本数组
    private final EnumMap<ServerTaskKind, DebugMeasurements> serverKindStepMicros = new EnumMap<>(ServerTaskKind.class);
    private final ConversionRuntime runtime;
    private final long started = System.nanoTime();
    private final long initialWorldTick;
    private final SchedulerStats schedulerBaseline;
    private long lastWorldTick;
    private long lastSample = started;
    private long maxInterval;
    private RuleIndex index;
    private int indexChanges;
    private SchedulerStats lastSchedulerStats;
    private long lastSampledSchedulerTicks;
    private long lastObservedBacklogClear;
    private double budgetRatioSum;
    private double maxBudgetRatio;
    private long budgetSamples;
    private long ticksTimeExhausted;
    private long ticksWorkUnitsExhausted;
    private long ticksUnexhausted;
    private long drainedInWindow;
    private long ticksWithDrainInWindow;
    private long maxDrainedInTick;
    private long backlogClearSamples;
    private long lastBacklogClearTicks = -1L;
    private long maxBacklogClearTicks;

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
        private QueueWindow(QueueStats baseline) {
            previousVisited = baseline.totalVisited();
            lastPending = baseline.pending();
            maxPending = lastPending;
        }

        // 只在维度时间推进时采集耗时；计数回退作为重置处理。
        private void add(QueueStats stats) {
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
        this.runtime = runtime;
        initialWorldTick = lastWorldTick = level.getGameTime();
        checks = new QueueWindow(runtime.queueStats(level, false));
        effects = new QueueWindow(runtime.queueStats(level, true));
        for (ServerTaskKind kind : ServerTaskKind.values()) {
            levelKindQueues.put(kind, new QueueWindow(runtime.queueStats(level, kind)));
            serverKindStepMicros.put(kind, new DebugMeasurements());
        }
        index = runtime.index();
        // 调度计数是服务器级累计值，窗口内一律用差值表达，避免把开始前的负载算进来。
        schedulerBaseline = runtime.schedulerStats();
        lastSchedulerStats = schedulerBaseline;
        lastSampledSchedulerTicks = schedulerBaseline.ticks();
        lastObservedBacklogClear = schedulerBaseline.lastBacklogClearTicks();
    }

    // 两端 EndServerTick 均在原版耗时数组写入后派发，读取当前完整 tick。
    void sample(MinecraftServer server, ServerLevel level, ConversionRuntime runtime) {
        long now = System.nanoTime();
        maxInterval = Math.max(maxInterval, now - lastSample);
        lastSample = now;
        long[] times = server.getTickTimesNanos();
        serverTimes.add(times[Math.floorMod(server.getTickCount(), times.length)] / 1000);
        if (index != runtime.index()) { indexChanges++; index = runtime.index(); }
        lastSchedulerStats = runtime.schedulerStats();
        ServerTickBudgetSnapshot budget = runtime.budgetSnapshot();
        if (budget != null && budget.gameTime() > 0L) {
            double ratio = budget.usageRatio();
            budgetRatioSum += ratio;
            maxBudgetRatio = Math.max(maxBudgetRatio, ratio);
            budgetSamples++;
            // 比例以百万分之一为整数样本：nearest-rank 只接受整数，避免浮点样本与排序口径混用
            serverBudgetRatioPpm.add(Math.round(ratio * 1_000_000D));
            // 这里的耗尽按预算自身标志统计（与阶段2 调度器口径不同：调度器只在「仍有剩余工作」时才计入 exhausted_by_*）
            if (budget.timeExpired()) { ticksTimeExhausted++; }
            if (budget.workUnitsExpired()) { ticksWorkUnitsExhausted++; }
            if (!budget.timeExpired() && !budget.workUnitsExpired()) { ticksUnexhausted++; }
            drainedInWindow += budget.drainedTasks();
        }
        // 调度器级样本每推进一 tick 只采一次：同一 tick 可能有维度 tick 与服务端 tick 两次采样
        if (lastSchedulerStats.ticks() > lastSampledSchedulerTicks) {
            lastSampledSchedulerTicks = lastSchedulerStats.ticks();
            serverDrainedPerTick.add(lastSchedulerStats.lastDrainedTasks());
            maxDrainedInTick = Math.max(maxDrainedInTick, lastSchedulerStats.lastDrainedTasks());
            if (lastSchedulerStats.lastDrainedTasks() > 0) { ticksWithDrainInWindow++; }
            long readyNow = 0L;
            for (ServerTaskKind kind : ServerTaskKind.values()) {
                KindStats current = lastSchedulerStats.perKind().get(kind);
                DebugMeasurements micros = serverKindStepMicros.get(kind);
                if (current == null) { continue; }
                if (micros != null) { micros.add(current.lastMicros()); }
                readyNow += current.deferred();
            }
            // 只在存在就绪任务的 tick 采样就绪延迟：没有就绪任务时延迟恒为 0，混入会把分位数稀释成 0 而掩盖真实等待
            if (readyNow > 0L) { serverReadyDelayTicks.add(lastSchedulerStats.lastMaxReadyDelayTicks()); }
            // 积压清空耗时只在数值变化时计一次：它是「最近一次」而非累计值
            long clear = lastSchedulerStats.lastBacklogClearTicks();
            if (clear >= 0L && clear != lastObservedBacklogClear) {
                lastObservedBacklogClear = clear;
                backlogClearSamples++;
                lastBacklogClearTicks = clear;
                maxBacklogClearTicks = Math.max(maxBacklogClearTicks, clear);
            }
        }
        if (lastWorldTick != level.getGameTime()) {
            lastWorldTick = level.getGameTime();
            checks.add(runtime.queueStats(level, false));
            effects.add(runtime.queueStats(level, true));
            for (ServerTaskKind kind : ServerTaskKind.values()) {
                QueueWindow window = levelKindQueues.get(kind);
                if (window != null) { window.add(runtime.queueStats(level, kind)); }
            }
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
        // 本维度（level 级）切片：旧字段保持不变，便于和阶段0 基线逐字段对照
        data.add("checks", checks.summary());
        data.add("effects", effects.summary());
        JsonObject levelKinds = new JsonObject();
        for (ServerTaskKind kind : ServerTaskKind.values()) {
            QueueWindow window = levelKindQueues.get(kind);
            if (window != null) { levelKinds.add(kind.name().toLowerCase(Locale.ROOT), window.summary()); }
        }
        data.add("level_kinds", levelKinds);
        data.addProperty("budget_samples", budgetSamples);
        data.addProperty("mean_budget_usage_ratio", budgetSamples == 0 ? 0 : budgetRatioSum / budgetSamples);
        data.addProperty("max_budget_usage_ratio", maxBudgetRatio);
        data.addProperty("ticks_exhausted_by_time", ticksTimeExhausted);
        data.addProperty("ticks_exhausted_by_work_units", ticksWorkUnitsExhausted);
        data.addProperty("drained_tasks_in_window", drainedInWindow);
        // 服务器级（跨维度合并）预算口径：分位数、耗尽原因占比与单类耗时都在这里
        JsonObject serverBudget = new JsonObject();
        serverBudget.addProperty("samples", budgetSamples);
        serverBudget.add("usage_ratio_ppm", serverBudgetRatioPpm.summary());
        serverBudget.addProperty("ticks_exhausted_by_time", ticksTimeExhausted);
        serverBudget.addProperty("ticks_exhausted_by_work_units", ticksWorkUnitsExhausted);
        serverBudget.addProperty("ticks_unexhausted", ticksUnexhausted);
        data.add("server_budget", serverBudget);
        // 服务器级就绪延迟分布：每 tick 取全维度最大就绪延迟作为一个样本（单位 tick）
        data.add("server_ready_delay_ticks", serverReadyDelayTicks.summary());
        JsonObject serverDrain = serverDrainedPerTick.summary();
        serverDrain.addProperty("ticks_with_drain_in_window", ticksWithDrainInWindow);
        serverDrain.addProperty("max_drained_in_tick", maxDrainedInTick);
        data.add("server_drain_per_tick", serverDrain);
        JsonObject serverBacklog = new JsonObject();
        serverBacklog.addProperty("cleared_count_in_window", backlogClearSamples);
        serverBacklog.addProperty("last_clear_ticks", lastBacklogClearTicks);
        serverBacklog.addProperty("max_clear_ticks", maxBacklogClearTicks);
        data.add("server_backlog", serverBacklog);
        JsonObject serverKindMicros = new JsonObject();
        for (ServerTaskKind kind : ServerTaskKind.values()) {
            DebugMeasurements micros = serverKindStepMicros.get(kind);
            if (micros != null) { serverKindMicros.add(kind.name().toLowerCase(Locale.ROOT), micros.summary()); }
        }
        data.add("server_kind_step_micros", serverKindMicros);
        data.add("scheduler", schedulerSummary());
        // 结算账本规模：未结清记录是阶段 6 的恢复依据，已完成记录按 tick 过期裁剪
        SettlementLedger ledger = SettlementLedger.get(level);
        data.addProperty("ledger_unsettled", ledger.unsettledCount());
        data.addProperty("ledger_records", ledger.size());
        JsonObject serverRecovery = new JsonObject();
        serverRecovery.addProperty("ledger_unsettled", ledger.unsettledCount());
        serverRecovery.addProperty("ledger_records", ledger.size());
        serverRecovery.addProperty("ledger_pending_delivery_total", ledger.pendingDeliveryTotal());
        // 仅调试观测：pendingUnits 是执行器自报的剩余量，绝不参与交付量计算（交付只认 pendingDelivery）
        serverRecovery.addProperty("ledger_pending_units_total_debug_only", ledger.pendingUnitsTotal());
        serverRecovery.addProperty("active_recoveries", runtime.activeRecoveryCount());
        serverRecovery.addProperty("recovery_skipped_dimensions", runtime.recoverySkippedDimensions());
        data.add("server_recovery", serverRecovery);
        return data;
    }

    // 跨维度合并视图：所有维度共用同一份预算，这里输出的是服务器级差值而非单维度切片。
    private JsonObject schedulerSummary() {
        JsonObject data = new JsonObject();
        SchedulerStats now = lastSchedulerStats;
        if (now == null) { return data; }
        data.addProperty("ticks", delta(now.ticks(), schedulerBaseline.ticks()));
        data.addProperty("steps", delta(now.steps(), schedulerBaseline.steps()));
        data.addProperty("released", delta(now.released(), schedulerBaseline.released()));
        data.addProperty("failed", delta(now.failed(), schedulerBaseline.failed()));
        data.addProperty("drained", delta(now.drained(), schedulerBaseline.drained()));
        data.addProperty("cancelled", delta(now.cancelled(), schedulerBaseline.cancelled()));
        data.addProperty("dropped", delta(now.dropped(), schedulerBaseline.dropped()));
        data.addProperty("exhausted_by_time", delta(now.exhaustedByTime(), schedulerBaseline.exhaustedByTime()));
        data.addProperty("exhausted_by_work_units", delta(now.exhaustedByWorkUnits(), schedulerBaseline.exhaustedByWorkUnits()));
        data.addProperty("pending", now.pending());
        data.addProperty("peak_pending", now.peakPending());
        data.addProperty("realms", now.realms());
        // 就绪延迟与积压清空是历史峰值/最近值，只能取当前值，不做差值
        data.addProperty("max_ready_delay_ticks", now.maxReadyDelayTicks());
        data.addProperty("last_backlog_clear_ticks", now.lastBacklogClearTicks());
        data.addProperty("last_max_ready_delay_ticks", now.lastMaxReadyDelayTicks());
        data.addProperty("last_drained_tasks", now.lastDrainedTasks());
        data.addProperty("drained_ticks", delta(now.drainedTicks(), schedulerBaseline.drainedTicks()));
        data.addProperty("max_drained_in_tick", now.maxDrainedInTick());
        JsonObject cancelledByReason = new JsonObject();
        for (CancelReason reason : CancelReason.values()) {
            long current = now.cancelledByReason().getOrDefault(reason, 0L);
            long base = schedulerBaseline.cancelledByReason().getOrDefault(reason, 0L);
            cancelledByReason.addProperty(reason.name().toLowerCase(Locale.ROOT), delta(current, base));
        }
        data.add("cancelled_by_reason", cancelledByReason);
        JsonObject perKind = new JsonObject();
        for (ServerTaskKind kind : ServerTaskKind.values()) {
            KindStats current = now.perKind().get(kind);
            if (current == null) { continue; }
            KindStats base = schedulerBaseline.perKind().get(kind);
            JsonObject entry = new JsonObject();
            entry.addProperty("pending", current.pending());
            entry.addProperty("deferred", current.deferred());
            entry.addProperty("steps", base == null ? current.steps() : delta(current.steps(), base.steps()));
            entry.addProperty("failed", base == null ? current.failed() : delta(current.failed(), base.failed()));
            entry.addProperty("requeued", base == null ? current.requeued() : delta(current.requeued(), base.requeued()));
            entry.addProperty("done", base == null ? current.done() : delta(current.done(), base.done()));
            entry.addProperty("yielded", base == null ? current.yielded() : delta(current.yielded(), base.yielded()));
            entry.addProperty("retried", base == null ? current.retried() : delta(current.retried(), base.retried()));
            entry.addProperty("work_units", base == null ? current.workUnits() : delta(current.workUnits(), base.workUnits()));
            entry.addProperty("last_micros", current.lastMicros());
            perKind.add(kind.name().toLowerCase(Locale.ROOT), entry);
        }
        data.add("per_kind", perKind);
        return data;
    }

    // 计数器只会单调增长，出现回退时按新值处理，避免负差值污染窗口。
    private static long delta(long current, long base) {
        return current < base ? current : current - base;
    }
}
