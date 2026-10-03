package com.meteorite.itemdespawntowhat.core.runtime.scheduler;

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
 */
public final class ServerScheduler {

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
    private final long dropped = 0L;
    private long exhaustedByTime;
    private long exhaustedByWorkUnits;
    private int pending;
    private int peakPending;
    private long maxReadyDelayTicks;
    // 阶段7 观测：本 tick 的服务器级最大就绪延迟，以及到期搬运是否被平滑窗口铺开
    private long lastMaxReadyDelayTicks;
    private long lastDrainedTasks;
    private long drainedTicks;
    private long maxDrainedInTick;
    // 阶段7 观测：按任务种类的本 tick 忙碌微秒（跨维度合并），每 tick 结算后快照为 kindLastMicros
    private final EnumMap<ServerTaskKind, Long> kindMicrosThisTick = new EnumMap<>(ServerTaskKind.class);
    private final EnumMap<ServerTaskKind, Long> kindLastMicros = new EnumMap<>(ServerTaskKind.class);
    private long backlogStartTick = -1L;
    private long lastBacklogClearTicks = -1L;
    private int rotationCursor;
    private ServerTickBudgetSnapshot lastSnapshot =
            new ServerTickBudgetSnapshot(0L, 0L, 0L, 0, 0, 0, false, false, false);

    public ServerScheduler(SchedulerConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public SchedulerConfig config() {
        return config;
    }

    // 最近一次推进的游戏刻；调度器尚未推进时返回 0（与旧实现启动时的 level.getGameTime() 一致）。
    public long currentTick() {
        return lastGameTime;
    }

    public int realmCount() {
        return realms.size();
    }

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

    // 指定到期 tick 入队，供阶段6 的持久返还恢复原始时间。
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
        int effectsUsed = 0;
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
                if (lane.kind() == ServerTaskKind.EFFECT && effectsUsed >= config.effectsWorkUnitsPerTick()) {
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
                StepResult result = runStep(lane, handle);
                if (lane.kind() == ServerTaskKind.EFFECT) {
                    effectsUsed += result.workUnits();
                }
                progressed = true;
            }
            if (!progressed) {
                return;
            }
        }
    }

    // 单条 lane 本 tick 的搬运额度：按到期量在平滑窗口内分摊，并受 dispatch_batch_size 上限约束。
    // 计数上限取 批量×窗口，既能覆盖一个完整窗口的突发量，也避免为巨大积压做全量遍历。
    private int drainAllowance(TaskLane lane, long gameTime) {
        int batch = config.dispatchBatchSize();
        int window = config.checkSpreadWindowTicks();
        int due = lane.countDue(gameTime, Math.max(batch, batch * window));
        int spread = (due + window - 1) / window;
        return Math.min(batch, Math.max(1, spread));
    }

    // 执行一个任务步骤：异常不中断整轮推进，预算耗尽不是业务失败。
    private StepResult runStep(TaskLane lane, ScheduledTask handle) {
        long started = System.nanoTime();
        StepResult result;
        try {
            result = handle.task().step(budget);
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable failure) {
            LOGGER.error("调度任务执行失败：realm={} kind={} task={}", lane.realmKey(), lane.kind(),
                    handle.task().tracingName(), failure);
            result = StepResult.failed(1);
        }
        if (result == null) {
            result = StepResult.done(1);
        }
        budget.charge(result.workUnits());
        long elapsed = System.nanoTime() - started;
        lane.recordStep(elapsed);
        // 阶段7 观测：按种类累计本 tick 的忙碌微秒，用于跨维度的单类峰值定位
        kindMicrosThisTick.merge(lane.kind(), elapsed / 1000L, Long::sum);
        steps++;
        long[] counters = kindCounters.computeIfAbsent(lane.kind(), key -> new long[6]);
        counters[0]++;
        counters[4] += result.workUnits();
        switch (result.outcome()) {
            case DONE -> {
                if (!handle.terminal()) {
                    handle.markDone();
                    handle.task().onReleased();
                    released++;
                    counters[5]++;
                }
            }
            case FAILED -> {
                if (!handle.terminal()) {
                    handle.markFailed();
                }
                failed++;
                counters[1]++;
            }
            // 阶段7 起把 YIELD（预算/区块让出）与 RETRY（业务重试）分列计数；requeued 对外仍是两者合计
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
        return result;
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
        pending = pendingNow;
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
    }

    // 取消某个 realm 的全部任务但保留 realm 本身：用于规则重载后按维度重扫，任务不留静默丢弃。
    public void cancelRealm(Object realmKey, CancelReason reason) {
        RealmQueues realm = realms.get(realmKey);
        if (realm != null) {
            realm.cancelAll(reason);
        }
    }

    // 维度卸载：逐项上报原因后清空该 realm，不留静默丢弃；持久返还由阶段6 补。
    public void releaseRealm(Object realmKey, CancelReason reason) {
        RealmQueues realm = realms.remove(realmKey);
        if (realm != null) {
            realm.cancelAll(reason);
        }
    }

    // 停服或规则重载：所有 realm 的任务带原因取消。
    public void clear(CancelReason reason) {
        for (RealmQueues realm : realms.values()) {
            realm.cancelAll(reason);
        }
        realms.clear();
    }

    // 跨维度合并的待处理任务数。
    public int pendingCount() {
        int total = 0;
        for (RealmQueues realm : realms.values()) {
            total += realm.pendingCount();
        }
        return total;
    }

    public int pendingCount(Object realmKey) {
        RealmQueues realm = realms.get(realmKey);
        return realm == null ? 0 : realm.pendingCount();
    }

    // 单条队列的观测值；realm 不存在时返回零值，与旧实现一致。
    public QueueStats queueStats(Object realmKey, ServerTaskKind kind) {
        RealmQueues realm = realms.get(realmKey);
        return realm == null ? new QueueStats(0, 0, 0L, 0L, 0L, 0L) : realm.queueStats(kind, lastGameTime);
    }

    public ServerTickBudgetSnapshot budgetSnapshot() {
        return lastSnapshot;
    }

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
        return new SchedulerStats(ticks, steps, released, failed, drained, cancelledTotal, dropped, exhaustedByTime,
                exhaustedByWorkUnits, pending, peakPending, realms.size(), maxReadyDelayTicks, lastBacklogClearTicks,
                lastMaxReadyDelayTicks, lastDrainedTasks, drainedTicks, maxDrainedInTick,
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
