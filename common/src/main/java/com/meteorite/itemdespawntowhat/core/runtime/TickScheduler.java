package com.meteorite.itemdespawntowhat.core.runtime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 按 tick 分桶的到期调度器：入队/出队均为 O(1)，只在到期那一 tick 取出对应桶。
 * 每 tick 执行数受预算限制，超出部分顺延到下一 tick，避免单 tick 抖动。
 * 任务与调度器同生命周期（per-level），随维度卸载一起丢弃。
 */
public final class TickScheduler {

    private static final Logger LOGGER = LogManager.getLogger();

    private final Map<Long, List<Runnable>> buckets = new HashMap<>();
    private final int maxTasksPerTick;

    public TickScheduler(int maxTasksPerTick) {
        this.maxTasksPerTick = Math.max(1, maxTasksPerTick);
    }

    // 登记一个延迟任务；delayTicks<=0 视为本轮到期
    public void schedule(long now, int delayTicks, Runnable task) {
        if (task == null) {
            return;
        }
        long due = now + Math.max(0, delayTicks);
        buckets.computeIfAbsent(due, key -> new ArrayList<>()).add(task);
    }

    // 执行到期任务，返回实际执行数；单任务异常被隔离，不影响其余任务
    public int runDue(long now) {
        List<Runnable> due = buckets.remove(now);
        if (due == null || due.isEmpty()) {
            return 0;
        }
        int executed = 0;
        List<Runnable> deferred = new ArrayList<>();
        for (Runnable task : due) {
            if (executed >= maxTasksPerTick) {
                deferred.add(task);
                continue;
            }
            executed++;
            try {
                task.run();
            } catch (VirtualMachineError fatal) {
                // 内存/栈等 VM 级错误不可恢复，直接上抛
                throw fatal;
            } catch (Throwable t) {
                // 其余异常与错误一律隔离，保证同桶的兄弟任务继续执行
                LOGGER.error("延迟任务执行失败", t);
            }
        }
        if (!deferred.isEmpty()) {
            buckets.computeIfAbsent(now + 1, key -> new ArrayList<>()).addAll(deferred);
        }
        return executed;
    }

    // 当前待执行任务数（含尚未到期的分桶）
    public int pending() {
        int total = 0;
        for (List<Runnable> bucket : buckets.values()) {
            total += bucket.size();
        }
        return total;
    }

    // 维度卸载时丢弃全部任务
    public void clear() {
        buckets.clear();
    }
}
