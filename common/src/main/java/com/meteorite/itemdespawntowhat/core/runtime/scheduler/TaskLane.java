package com.meteorite.itemdespawntowhat.core.runtime.scheduler;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.TreeMap;

/**
 * 单条队列：一个 realm（维度）与一个任务种类的到期桶 + 就绪队列。
 * 搬运按额度分片，取消的任务在搬运或取用时懒删除，不占额度也不计费。
 */
final class TaskLane {

    private final Object realmKey;
    private final ServerTaskKind kind;
    private final TreeMap<Long, ArrayDeque<ScheduledTask>> buckets = new TreeMap<>();
    private final ArrayDeque<ScheduledTask> ready = new ArrayDeque<>();
    private int pending;
    private int peakPending;
    private int drainedThisTick;
    private int stepsThisTick;
    private long busyNanos;
    private long lastMicros;
    private long maxMicros;
    private long totalVisited;

    TaskLane(Object realmKey, ServerTaskKind kind) {
        this.realmKey = realmKey;
        this.kind = kind;
    }

    Object realmKey() {
        return realmKey;
    }

    ServerTaskKind kind() {
        return kind;
    }

    int pending() {
        return pending;
    }

    // 是否还有待推进的任务（就绪队列或到期桶）。
    boolean active() {
        if (pending > 0) {
            return true;
        }
        return !ready.isEmpty();
    }

    void schedule(ScheduledTask handle) {
        buckets.computeIfAbsent(handle.dueTick(), key -> new ArrayDeque<>()).addLast(handle);
        pending++;
        peakPending = Math.max(peakPending, pending);
    }

    // 按额度把到期桶中的任务搬入就绪队列：一次只搬一个，搬运本身消耗 1 个工作单位。
    boolean takeDue(long gameTime, ServerTickBudget budget, int allowance) {
        if (drainedThisTick >= allowance) {
            return false;
        }
        while (true) {
            Map.Entry<Long, ArrayDeque<ScheduledTask>> entry = buckets.firstEntry();
            if (entry == null || entry.getKey() > gameTime) {
                return false;
            }
            ArrayDeque<ScheduledTask> bucket = entry.getValue();
            ScheduledTask head = bucket.pollFirst();
            if (bucket.isEmpty()) {
                buckets.pollFirstEntry();
            }
            if (head == null) {
                continue;
            }
            if (head.terminal()) {
                continue;
            }
            head.markReady();
            ready.addLast(head);
            drainedThisTick++;
            budget.chargeDrain();
            return true;
        }
    }

    // 统计到期任务数量，最多统计到上限即停止，避免为统计遍历大量到期桶。
    int countDue(long gameTime, int limit) {
        int total = 0;
        for (ArrayDeque<ScheduledTask> bucket : buckets.headMap(gameTime, true).values()) {
            total += bucket.size();
            if (total >= limit) {
                return limit;
            }
        }
        return total;
    }

    // 取一个就绪任务执行；取消的条目在这里被懒删除。
    ScheduledTask pollReady() {
        while (!ready.isEmpty()) {
            ScheduledTask head = ready.pollFirst();
            if (head.terminal()) {
                continue;
            }
            head.markRunning();
            pending--;
            return head;
        }
        return null;
    }

    // 让出或重试：按新的到期 tick 重新入队；句柄的原归属在出队时保留，取消仍能上报。
    void requeue(ScheduledTask handle, long dueTick) {
        handle.requeue(dueTick);
        buckets.computeIfAbsent(dueTick, key -> new ArrayDeque<>()).addLast(handle);
        pending++;
        peakPending = Math.max(peakPending, pending);
    }

    // 就绪队列中尚未执行的（未被取消的）任务数，用于观测本 tick 被延后的量。
    int readyCount() {
        int total = 0;
        for (ScheduledTask handle : ready) {
            if (!handle.terminal()) {
                total++;
            }
        }
        return total;
    }

    // 取消任务时回退待处理计数：只有在队列中等待的任务才被计数。
    void onCancelled(ScheduledTask.State previous) {
        if (previous == ScheduledTask.State.PENDING || previous == ScheduledTask.State.READY) {
            pending = Math.max(0, pending - 1);
        }
    }

    boolean hasDue(long gameTime) {
        Map.Entry<Long, ArrayDeque<ScheduledTask>> entry = buckets.firstEntry();
        return entry != null && entry.getKey() <= gameTime;
    }

    boolean hasReady() {
        return !ready.isEmpty();
    }

    long readyDelay(long gameTime) {
        for (ScheduledTask handle : ready) {
            if (!handle.terminal()) {
                return Math.max(0L, gameTime - handle.dueTick());
            }
        }
        return 0L;
    }

    void recordStep(long nanos) {
        stepsThisTick++;
        busyNanos += Math.max(0L, nanos);
    }

    QueueStats stats(long gameTime) {
        return new QueueStats(pending, peakPending, totalVisited, lastMicros, maxMicros, readyDelay(gameTime));
    }

    void beginTick() {
        drainedThisTick = 0;
        stepsThisTick = 0;
        busyNanos = 0L;
    }

    void finishTick() {
        if (stepsThisTick > 0) {
            lastMicros = busyNanos / 1000L;
            maxMicros = Math.max(maxMicros, lastMicros);
            totalVisited += stepsThisTick;
        } else {
            lastMicros = 0L;
        }
    }

    // 整条队列取消：逐项上报原因后清空容器，不留任何静默丢弃。
    void clear(CancelReason reason) {
        for (ArrayDeque<ScheduledTask> bucket : buckets.values()) {
            for (ScheduledTask handle : bucket) {
                handle.cancel(reason);
            }
        }
        for (ScheduledTask handle : ready) {
            handle.cancel(reason);
        }
        buckets.clear();
        ready.clear();
        pending = 0;
    }
}
