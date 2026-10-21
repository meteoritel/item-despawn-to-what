package com.meteorite.itemdespawntowhat.core.runtime.scheduler;

import com.meteorite.itemdespawntowhat.core.api.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ScheduledTaskHandle;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudgetSnapshot;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepResult;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepWorkCounter;
import com.meteorite.itemdespawntowhat.core.api.scheduler.TaskQueue;
import com.meteorite.itemdespawntowhat.core.api.RuntimeObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 服务器级共享 tick 预算调度器：每个服务器一个实例，所有维度与任务种类共享同一份时间与工作量预算。
 * 每 tick 只推进一次；到期搬运、就绪提取与任务执行全部计入预算，超出预算的任务保留在队列中跨 tick 延后。
 * 任务保留原始到期 tick：只有在到期后才参与执行，公平轮转不会让任务提前执行。
 * 本类是实现层：契约层只通过 {@link TaskQueue} 提出排期请求，通过 {@link ScheduledTaskHandle} 引用已入队任务。
 */
public final class ServerScheduler implements TaskQueue {

    private static final Logger LOGGER = LogManager.getLogger("IDTW.Scheduler");

    private final SchedulerConfig config;
    private final LinkedHashMap<Object, RealmQueues> realms = new LinkedHashMap<>();
    private final ServerTickBudget budget = new ServerTickBudget();
    private final EnumMap<ServerTaskKind, long[]> kindCounters = new EnumMap<>(ServerTaskKind.class);
    private final EnumMap<CancelReason, Long> cancelledByReason = new EnumMap<>(CancelReason.class);
    private long lastGameTime;
    private long ticks;
    private long steps;
    private long released;
    private long failed;
    private long drained;
    private long exhaustedByTime;
    private long exhaustedByWorkUnits;
    private int peakPending;
    private long maxReadyDelayTicks;
    // 观测：本 tick 的服务器级最大就绪延迟，以及到期搬运是否被平滑窗口铺开
    private long lastMaxReadyDelayTicks;
    private long lastDrainedTasks;
    private long drainedTicks;
    private long maxDrainedInTick;
    // 观测：按任务种类的本 tick 忙碌微秒（跨维度合并），每 tick 结算后快照为 kindLastMicros
    private final EnumMap<ServerTaskKind, Long> kindMicrosThisTick = new EnumMap<>(ServerTaskKind.class);
    private final EnumMap<ServerTaskKind, Long> kindLastMicros = new EnumMap<>(ServerTaskKind.class);
    private long backlogStartTick = -1L;
    private long lastBacklogClearTicks = -1L;
    private int rotationCursor;
    // 观测：本 tick 效果类累计消耗的工作量；runTick 累加，finishTick 输出后归零
    private int effectsUsedThisTick;
    // 观测：单步工作量的本 tick 峰值与历史峰值；与计费同一口径，用于核实工作单位是否跟真实世界操作量同向变化
    private int maxStepWorkUnitsThisTick;
    private int lastMaxStepWorkUnits;
    private int maxStepWorkUnits;
    // 观测：本 tick 结算后的预算载荷已输出，避免同一 tick 重复打印
    private long lastBudgetLogGameTime = Long.MIN_VALUE;
    // 观测：上次预算日志时的累计步骤数，用于计算两次日志之间的步骤增量
    private long lastLoggedSteps;
    private ServerTickBudgetSnapshot lastSnapshot =
            new ServerTickBudgetSnapshot(0L, 0L, 0L, 0, 0, 0, false, false, false);

    // 构造调度器：预算参数在构造时固定，realm 在首次登记时创建
    public ServerScheduler(SchedulerConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public SchedulerConfig config() {
        return config;
    }

    // 最近一次推进的游戏刻；调度器尚未推进时为 0。
    @SuppressWarnings("unused") // 公共诊断接口，允许外部开发工具读取。
    public long currentTick() {
        return lastGameTime;
    }

    @SuppressWarnings("unused") // 公共诊断接口，允许外部开发工具读取。
    public int realmCount() {
        return realms.size();
    }

    @SuppressWarnings("unused") // 公共诊断接口，允许外部开发工具读取。
    public boolean hasRealm(Object realmKey) {
        return realms.containsKey(realmKey);
    }

    // 登记（或获取）一个 realm；realm 只决定公平轮转与搬运分片，不持有独立预算。
    public void registerRealm(Object realmKey) {
        realmFor(realmKey);
    }

    private RealmQueues realmFor(Object realmKey) {
        return realms.computeIfAbsent(realmKey, key -> new RealmQueues(this, key));
    }

    // 登记任务：到期 tick = 当前刻 + 延迟；延迟为 0 也入队，递归调度不能绕过预算。
    public ScheduledTask schedule(Object realmKey, ServerTask task, int delayTicks) {
        Objects.requireNonNull(task, "task");
        return realmFor(realmKey).schedule(task, lastGameTime + Math.max(0, delayTicks));
    }

    // 指定到期 tick 入队：持久返还恢复时按记录保留原始到期时间；返回值收窄为具体实现以保留任务状态查询能力。
    @Override
    public ScheduledTask scheduleAt(Object realmKey, ServerTask task, long dueTick) {
        Objects.requireNonNull(task, "task");
        return realmFor(realmKey).schedule(task, dueTick);
    }

    // 每 tick 推进一次；同一 tick 重复调用直接忽略，保证「每 tick 一个总预算」。
    public void tick(long gameTime) {
        if (gameTime <= lastGameTime) {
            return;
        }
        lastGameTime = gameTime;
        budget.begin(gameTime, config.budgetNanos(), config.maxWorkUnitsPerTick());
        for (RealmQueues realm : realms.values()) {
            realm.beginTick();
        }
        List<TaskLane> order = activeLanes();
        if (!order.isEmpty()) {
            runTick(gameTime, order);
            rotationCursor = (rotationCursor + 1) % order.size();
        }
        finishTick(gameTime);
    }

    // 公平轮转每个 lane：先按额度搬运到期任务，再执行一个就绪任务，直到预算耗尽或没有任务可推进。
    private void runTick(long gameTime, List<TaskLane> order) {
        // 搬运额度每 tick 每 lane 只统计一次：countDue 要遍历到期桶，不能在每个 pass 里重复做
        int[] allowances = new int[order.size()];
        for (int i = 0; i < order.size(); i++) {
            allowances[i] = drainAllowance(order.get(i), gameTime);
        }
        int maxPasses = Math.max(16, config.maxWorkUnitsPerTick() * 2 + order.size() * 2);
        for (int pass = 0; pass < maxPasses; pass++) {
            boolean progressed = false;
            for (int i = 0; i < order.size(); i++) {
                if (budget.exhausted()) {
                    return;
                }
                int laneIndex = (rotationCursor + i) % order.size();
                TaskLane lane = order.get(laneIndex);
                if (lane.kind() == ServerTaskKind.EFFECT && effectsUsedThisTick >= config.effectsWorkUnitsPerTick()) {
                    continue;
                }
                if (lane.takeDue(gameTime, budget, allowances[laneIndex])) {
                    progressed = true;
                    continue;
                }
                ScheduledTask handle = lane.pollReady();
                if (handle == null) {
                    continue;
                }
                // 效果配额按本步计费结果累加，与全局预算、时间软预算共用同一口径；不能用声明回执，否则配额退化为步数
                int billedWorkUnits = runStep(lane, handle);
                if (lane.kind() == ServerTaskKind.EFFECT) {
                    effectsUsedThisTick += billedWorkUnits;
                }
                progressed = true;
            }
            if (!progressed) {
                return;
            }
        }
    }

    // 单条 lane 本 tick 的搬运额度：按到期量在平滑窗口内分摊，并受 dispatch_batch_size 上限约束。
    // 计数上限取 batch × window，既能覆盖一个完整窗口的突发量，也避免为巨大积压做全量遍历。
    private int drainAllowance(TaskLane lane, long gameTime) {
        int batch = config.dispatchBatchSize();
        int window = config.checkSpreadWindowTicks();
        int due = lane.countDue(gameTime, Math.max(batch, batch * window));
        int spread = (due + window - 1) / window;
        return Math.clamp(spread, 1, batch);
    }

    // 执行一个任务步骤：异常不中断整轮推进，预算耗尽不是业务失败。
    // 返回本步计费工作量（声明回执与步骤内实际上报的较大者），调用方据此累加效果 lane 配额。
    private int runStep(TaskLane lane, ScheduledTask handle) {
        long started = System.nanoTime();
        StepResult result;
        Throwable stepFailure = null;
        int reportedWorkUnits;
        // 开窗：本步真实完成的世界操作量由任务实现回执，同步执行与延迟执行共用同一份窗口
        StepWorkCounter.begin();
        try {
            result = handle.task().step(budget);
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable failure) {
            stepFailure = failure;
            LOGGER.error("调度任务执行失败：realm={} kind={} task={}", lane.realmKey(), lane.kind(),
                    handle.task().tracingName(), failure);
            result = StepResult.failed(1);
        } finally {
            // 关窗必须在 finally：步骤抛错也要关窗，否则窗口状态会污染后续步骤的计价
            reportedWorkUnits = StepWorkCounter.end();
        }
        if (result == null) {
            result = StepResult.done(1);
        }
        // 计价取「任务回执的工作量」与「步骤内上报的真实世界操作量」的较大者；未上报时与改前一致
        int workUnits = Math.max(result.workUnits(), reportedWorkUnits);
        budget.charge(workUnits);
        maxStepWorkUnitsThisTick = Math.max(maxStepWorkUnitsThisTick, workUnits);
        long elapsed = System.nanoTime() - started;
        lane.recordStep(elapsed);
        // 观测：按种类累计本 tick 的忙碌微秒，用于跨维度的单类峰值定位
        kindMicrosThisTick.merge(lane.kind(), elapsed / 1000L, Long::sum);
        steps++;
        long[] counters = kindCounters.computeIfAbsent(lane.kind(), key -> new long[6]);
        counters[0]++;
        // 分类统计与实际计入预算的工作量保持同一口径
        counters[4] += workUnits;
        switch (result.outcome()) {
            case DONE -> {
                if (!handle.terminal()) {
                    handle.markDone();
                    // 释放回调可能抛异常：与失败分支同口径捕获，保证单个任务的收尾异常不会中断本轮推进
                    try {
                        handle.task().onReleased();
                    } catch (VirtualMachineError fatal) {
                        throw fatal;
                    } catch (Throwable releaseFailure) {
                        // DONE 终态已置位，后续轮次不会再次进入本分支，因此不会重复释放
                        LOGGER.error("调度任务释放异常：realm={} kind={} task={}", lane.realmKey(), lane.kind(),
                                handle.task().tracingName(), releaseFailure);
                    }
                    released++;
                    counters[5]++;
                }
            }
            case FAILED -> {
                if (!handle.terminal()) {
                    handle.markFailed();
                    try {
                        handle.task().onFailed(stepFailure);
                    } catch (VirtualMachineError fatal) {
                        throw fatal;
                    } catch (Throwable cleanupFailure) {
                        // 单个任务的收尾异常不能阻止其它 lane 推进；FAILED 终态不重复通知。
                        LOGGER.error("调度任务失败清理异常：realm={} kind={} task={}", lane.realmKey(), lane.kind(),
                                handle.task().tracingName(), cleanupFailure);
                    }
                }
                failed++;
                counters[1]++;
            }
            // 把 YIELD（预算/区块让出）与 RETRY（业务重试）分列计数；requeued 对外仍是两者合计
            case YIELD -> {
                if (!handle.terminal()) {
                    lane.requeue(handle, lastGameTime + Math.max(1, result.nextDelayTicks()));
                    counters[2]++;
                }
            }
            case RETRY -> {
                if (!handle.terminal()) {
                    lane.requeue(handle, lastGameTime + Math.max(1, result.nextDelayTicks()));
                    counters[3]++;
                }
            }
        }
        return workUnits;
    }

    // 本 tick 参与轮转的 lane：只包含仍有待推进任务（或就绪任务）的队列。
    private List<TaskLane> activeLanes() {
        List<TaskLane> order = new ArrayList<>();
        for (RealmQueues realm : realms.values()) {
            for (TaskLane lane : realm.lanes()) {
                if (lane.active()) {
                    order.add(lane);
                }
            }
        }
        return order;
    }

    // 结算本 tick：汇总跨维度积压、预算耗尽原因与积压清空时间。
    private void finishTick(long gameTime) {
        int pendingNow = 0;
        long maxDelay = 0L;
        boolean workRemaining = false;
        for (RealmQueues realm : realms.values()) {
            pendingNow += realm.pendingCount();
            maxDelay = Math.max(maxDelay, realm.maxReadyDelay(gameTime));
            workRemaining |= realm.hasWork(gameTime);
            realm.finishTick();
        }
        peakPending = Math.max(peakPending, pendingNow);
        maxReadyDelayTicks = Math.max(maxReadyDelayTicks, maxDelay);
        lastMaxReadyDelayTicks = maxDelay;
        budget.finish();
        if (workRemaining) {
            budget.markWorkRemaining();
        }
        boolean timeExpired = budget.timeExpired();
        boolean workUnitsExpired = budget.workUnitsExpired();
        if (workRemaining && timeExpired) {
            exhaustedByTime++;
        }
        if (workRemaining && workUnitsExpired) {
            exhaustedByWorkUnits++;
        }
        if (pendingNow > 0 && backlogStartTick < 0L) {
            backlogStartTick = gameTime;
        } else if (pendingNow == 0 && backlogStartTick >= 0L) {
            lastBacklogClearTicks = gameTime - backlogStartTick;
            backlogStartTick = -1L;
        }
        long drainedNow = budget.drainedTasks();
        lastDrainedTasks = drainedNow;
        if (drainedNow > 0) {
            drainedTicks++;
            maxDrainedInTick = Math.max(maxDrainedInTick, drainedNow);
        }
        drained += drainedNow;
        // 本 tick 的按种类耗时在此期间固定下来，供 stats() 读取（不在 tick 内做分位数计算）
        kindLastMicros.clear();
        kindLastMicros.putAll(kindMicrosThisTick);
        kindMicrosThisTick.clear();
        ticks++;
        lastSnapshot = budget.snapshot();
        // 单步峰值先结算成本 tick 样本，日志才能输出本 tick 的值
        lastMaxStepWorkUnits = maxStepWorkUnitsThisTick;
        maxStepWorkUnits = Math.max(maxStepWorkUnits, lastMaxStepWorkUnits);
        maxStepWorkUnitsThisTick = 0;
        logBudgetObservation();
        // 本 tick 已统计完毕，归零避免无任务的 tick 沿用上一 tick 的效果用量
        effectsUsedThisTick = 0;
    }

    /**
     * 每个忙碌 tick 输出一行可解析的预算载荷，供后续性能测试直接采集。
     * 仅在开发环境写入 INFO（该环境控制台不输出 DEBUG）；无任务且无搬运的 tick 不输出，避免高 TPS 刷屏。
     */
    private void logBudgetObservation() {
        if (!RuntimeObserver.current().enabled() || lastSnapshot.gameTime() == lastBudgetLogGameTime) {
            return;
        }
        if (lastSnapshot.drainedTasks() == 0 && steps == lastLoggedSteps) {
            return;
        }
        long stepsThisTick = steps - lastLoggedSteps;
        lastLoggedSteps = steps;
        lastBudgetLogGameTime = lastSnapshot.gameTime();
        long readyDelay = 0L;
        for (RealmQueues realm : realms.values()) {
            readyDelay = Math.max(readyDelay, realm.maxReadyDelay(lastSnapshot.gameTime()));
        }
        LOGGER.info("IDTW_BUDGET tick={} used_ns={} budget_ns={} used_units={} max_units={} effects_units={} "
                        + "max_effect_units={} step_max_units={} steps={} drained={} pending={} ready_delay={} exhausted={}",
                lastSnapshot.gameTime(), lastSnapshot.usedNanos(), lastSnapshot.budgetNanos(),
                lastSnapshot.usedWorkUnits(), lastSnapshot.maxWorkUnits(), effectsUsedThisTick,
                config.effectsWorkUnitsPerTick(), lastMaxStepWorkUnits, stepsThisTick,
                lastSnapshot.drainedTasks(), pendingCount(), readyDelay, lastSnapshot.exhaustionReason());
    }

    // 取消某个 realm 的全部任务但保留 realm 本身：用于规则重载后按维度重扫，任务不留静默丢弃。
    // 与 releaseRealm/clear 共用 RealmQueues 的批量取消实现：先收集句柄、再清空容器、最后逐个取消。
    public void cancelRealm(Object realmKey, CancelReason reason) {
        RealmQueues realm = realms.get(realmKey);
        if (realm != null) {
            realm.cancelAll(reason);
        }
    }

    /**
     * 将维度旧队列从执行轮转中摘除，以续接任务逐项取消；同 key 的新任务进入新队列。
     * @param realmKey 维度标识
     * @param reason 取消原因
     * @return 每步最多取消一个槽位的清理任务
     */
    public ServerTask detachRealm(Object realmKey, CancelReason reason) {
        RealmQueues detached = realms.remove(realmKey);
        // 本 tick 的轮转列表已创建；旧 lane 必须立即停止搬运与执行，不能等到下 tick 才退出。
        if (detached != null) { detached.detach(); }
        return new ServerTask() {
            @Override public ServerTaskKind kind() { return ServerTaskKind.MAINTENANCE; }
            @Override public String tracingName() { return "realm-cleanup/" + realmKey; }
            @Override public StepResult step(ServerTickBudget tickBudget) {
                if (detached == null) { return StepResult.done(1); }
                try {
                    return detached.cancelOne(reason) ? StepResult.yield(1, 1) : StepResult.done(1);
                } catch (RuntimeException failure) {
                    // 槽位已经摘除，记录失误后仍推进其它任务的取消。
                    LOGGER.error("维度任务清理异常：realm={}", realmKey, failure);
                    return StepResult.yield(1, 1);
                }
            }
            @Override public void onCancelled(CancelReason cancellation) {
                if (detached != null) { detached.cancelAll(cancellation); }
            }
        };
    }

    // 维度卸载：先摘除 realm 索引再逐项取消并清空容器，不留静默丢弃；待返还库存由恢复任务补交。
    public void releaseRealm(Object realmKey, CancelReason reason) {
        RealmQueues realm = realms.remove(realmKey);
        if (realm != null) {
            realm.cancelAll(reason);
        }
    }

    // 停服或规则重载：所有 realm 的任务带原因取消。先摘除索引再逐个取消，
    // 取消回调重新登记的任务会落在新的 realm 上，不会被本批清理误删。
    public void clear(CancelReason reason) {
        List<RealmQueues> clearing = new ArrayList<>(realms.values());
        realms.clear();
        for (RealmQueues realm : clearing) {
            realm.cancelAll(reason);
        }
    }

    // 跨维度合并的待处理任务数。
    public int pendingCount() {
        int total = 0;
        for (RealmQueues realm : realms.values()) {
            total += realm.pendingCount();
        }
        return total;
    }

    // 指定 realm 的待处理任务数；realm 未登记时返回 0
    public int pendingCount(Object realmKey) {
        RealmQueues realm = realms.get(realmKey);
        return realm == null ? 0 : realm.pendingCount();
    }

    // 单条队列的观测值；realm 不存在时返回零值。
    public QueueStats queueStats(Object realmKey, ServerTaskKind kind) {
        RealmQueues realm = realms.get(realmKey);
        return realm == null ? new QueueStats(0, 0, 0L, 0L, 0L, 0L) : realm.queueStats(kind, lastGameTime);
    }

    public ServerTickBudgetSnapshot budgetSnapshot() {
        return lastSnapshot;
    }

    // pending 按当前车道实时汇总，取消后立即可见；耗时等字段仍是最近一次已结束 tick 的快照。
    public SchedulerStats stats() {
        EnumMap<ServerTaskKind, KindStats> perKind = new EnumMap<>(ServerTaskKind.class);
        for (ServerTaskKind kind : ServerTaskKind.values()) {
            long kindPending = 0L;
            long deferred = 0L;
            for (RealmQueues realm : realms.values()) {
                kindPending += realm.queueStats(kind, lastGameTime).pending();
                deferred += realm.readyCount(kind);
            }
            long[] counters = kindCounters.get(kind);
            long done = counters == null ? 0L : counters[5];
            long yielded = counters == null ? 0L : counters[2];
            long retried = counters == null ? 0L : counters[3];
            perKind.put(kind, new KindStats(kindPending, counters == null ? 0L : counters[0],
                    counters == null ? 0L : counters[1], yielded + retried, deferred,
                    done, yielded, retried, counters == null ? 0L : counters[4],
                    kindLastMicros.getOrDefault(kind, 0L)));
        }
        long cancelledTotal = 0L;
        for (long value : cancelledByReason.values()) {
            cancelledTotal += value;
        }
        return new SchedulerStats(ticks, steps, released, failed, drained, cancelledTotal, 0L, exhaustedByTime,
                exhaustedByWorkUnits, pendingCount(), peakPending, realms.size(), maxReadyDelayTicks, lastBacklogClearTicks,
                lastMaxReadyDelayTicks, lastDrainedTasks, drainedTicks, maxDrainedInTick,
                lastMaxStepWorkUnits, maxStepWorkUnits,
                Map.copyOf(cancelledByReason), Map.copyOf(perKind));
    }

    // 由 RealmQueues 回调：按原因累计取消计数；dropped 恒为 0。
    void countCancelled(CancelReason reason) {
        if (reason == null) {
            return;
        }
        cancelledByReason.merge(reason, 1L, Long::sum);
    }
}
