package com.meteorite.itemdespawntowhat.core.runtime.scheduler;

import java.util.Collection;
import java.util.EnumMap;

/**
 * 单个 realm（维度）的任务队列集合：按任务种类分 lane，所有 lane 共享服务器总预算。
 * realm 只是公平轮转与公平搬运的单位，不持有独立预算。
 */
final class RealmQueues {

    private final ServerScheduler scheduler;
    private final Object realmKey;
    private final EnumMap<ServerTaskKind, TaskLane> lanes = new EnumMap<>(ServerTaskKind.class);

    RealmQueues(ServerScheduler scheduler, Object realmKey) {
        this.scheduler = scheduler;
        this.realmKey = realmKey;
    }

    Object realmKey() {
        return realmKey;
    }

    Collection<TaskLane> lanes() {
        return lanes.values();
    }

    TaskLane lane(ServerTaskKind kind) {
        return lanes.computeIfAbsent(kind, key -> new TaskLane(realmKey, key));
    }

    ScheduledTask schedule(ServerTask task, long dueTick) {
        ScheduledTask handle = new ScheduledTask(task, dueTick);
        handle.attach(this);
        lane(task.kind()).schedule(handle);
        return handle;
    }

    int pendingCount() {
        int total = 0;
        for (TaskLane lane : lanes.values()) {
            total += lane.pending();
        }
        return total;
    }

    long maxReadyDelay(long gameTime) {
        long maximum = 0L;
        for (TaskLane lane : lanes.values()) {
            maximum = Math.max(maximum, lane.readyDelay(gameTime));
        }
        return maximum;
    }

    boolean hasWork(long gameTime) {
        for (TaskLane lane : lanes.values()) {
            if (lane.hasReady() || lane.hasDue(gameTime)) {
                return true;
            }
        }
        return false;
    }

    // 某一类任务在本 realm 中本轮结束时仍未执行的就绪任务数。
    int readyCount(ServerTaskKind kind) {
        TaskLane lane = lanes.get(kind);
        return lane == null ? 0 : lane.readyCount();
    }

    QueueStats queueStats(ServerTaskKind kind, long gameTime) {
        TaskLane lane = lanes.get(kind);
        return lane == null ? new QueueStats(0, 0, 0L, 0L, 0L, 0L) : lane.stats(gameTime);
    }

    // 任务取消统一在这里回退计数并上报原因。
    void onTaskCancelled(ScheduledTask handle, ScheduledTask.State previous, CancelReason reason) {
        TaskLane lane = lanes.get(handle.kind());
        if (lane != null) {
            lane.onCancelled(previous);
        }
        scheduler.countCancelled(reason);
    }

    void cancelAll(CancelReason reason) {
        for (TaskLane lane : lanes.values()) {
            lane.clear(reason);
        }
        lanes.clear();
    }

    void beginTick() {
        for (TaskLane lane : lanes.values()) {
            lane.beginTick();
        }
    }

    void finishTick() {
        for (TaskLane lane : lanes.values()) {
            lane.finishTick();
        }
    }
}
