package com.meteorite.itemdespawntowhat.core.runtime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.TreeMap;

/** 到期任务队列：预算耗尽时保留队列游标，不复制积压任务；跳过的游戏刻也能补执行。 */
public final class TickScheduler {
    public static final long SOFT_BUDGET_NANOS = 2_000_000L;
    private static final Logger LOGGER = LogManager.getLogger();
    private final TreeMap<Long, ArrayDeque<Task>> buckets = new TreeMap<>();
    private final ArrayDeque<ArrayDeque<Task>> ready = new ArrayDeque<>();
    private final int maxTasksPerTick;
    private int pending;
    private int peakPending;
    private long totalVisited;
    private long lastMicros;
    private long maxMicros;
    private long lastTick;

    /** 调度统计：时间预算只能在任务之间检查，单次原版世界操作不能被抢占。 */
    public record Stats(int pending, int peakPending, long totalVisited, long lastMicros, long maxMicros, long oldestDelay) {}

    /** 可取消的任务句柄；取消同时释放捕获对象。 */
    public final class Task {
        private Runnable action;
        private final long due;
        private Task(Runnable action, long due) { this.action = action; this.due = due; }
        // 取消后释放任务捕获的实体和上下文。
        public void cancel() {
            if (action != null) { action = null; pending--; }
        }
        private void run() {
            Runnable work = action;
            cancel();
            if (work != null) { work.run(); }
        }
    }

    public TickScheduler(int maxTasksPerTick) {
        this.maxTasksPerTick = Math.max(1, maxTasksPerTick);
    }

    // 零延迟也入队，本轮新增任务在剩余预算内执行，递归调度不能绕过预算。
    public Task schedule(long now, int delayTicks, Runnable action) {
        Task task = new Task(action, now + Math.max(0, delayTicks));
        if (action != null) {
            buckets.computeIfAbsent(now + Math.max(0, delayTicks), key -> new ArrayDeque<>()).add(task);
            pending++;
            peakPending = Math.max(peakPending, pending);
        }
        return task;
    }

    public int runDue(long now) {
        int visited = 0;
        lastTick = now;
        long started = System.nanoTime();
        long deadline = started + SOFT_BUDGET_NANOS;
        while (visited < maxTasksPerTick && System.nanoTime() < deadline) {
            Map.Entry<Long, ArrayDeque<Task>> entry;
            while ((entry = buckets.firstEntry()) != null && entry.getKey() <= now) {
                ready.add(buckets.pollFirstEntry().getValue());
            }
            if (ready.isEmpty()) { break; }
            ArrayDeque<Task> queue = ready.peek();
            Task task = queue.remove();
            if (queue.isEmpty()) { ready.remove(); }
            visited++;
            try { task.run(); }
            catch (VirtualMachineError fatal) { throw fatal; }
            catch (Throwable failure) { LOGGER.error("延迟任务执行失败", failure); }
        }
        lastMicros = (System.nanoTime() - started) / 1000;
        maxMicros = Math.max(maxMicros, lastMicros);
        totalVisited += visited;
        return visited;
    }

    public int pending() { return pending; }

    public Stats stats() {
        var queue = ready.peek();
        Task first = queue == null ? null : queue.peek();
        long oldest = pending == 0 || first == null ? 0 : Math.max(0, lastTick - first.due);
        return new Stats(pending, peakPending, totalVisited, lastMicros, maxMicros, oldest);
    }

    public void clear() {
        for (ArrayDeque<Task> queue : buckets.values()) { queue.forEach(Task::cancel); }
        ready.forEach(queue -> queue.forEach(Task::cancel));
        buckets.clear();
        ready.clear();
    }
}
