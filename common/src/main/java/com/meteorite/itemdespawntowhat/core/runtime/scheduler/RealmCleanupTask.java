package com.meteorite.itemdespawntowhat.core.runtime.scheduler;

import com.meteorite.itemdespawntowhat.core.api.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * 摘除 realm 后的逐槽取消续接任务。
 * 句柄已从队列容器摘出，任务每步只取消调度配置允许的数量，预算内跨 tick 推进；取消回调异常在
 * {@link ScheduledTask#cancel} 内隔离，单条失败不会中断同批取消。
 * 停服时没有下一 tick，{@link #drainNow()} 就地排空剩余句柄，保证取消回调在最终存档前完成。
 */
final class RealmCleanupTask implements ServerTask {

    private static final Logger LOGGER = LogManager.getLogger("IDTW.Scheduler");

    private final ServerScheduler scheduler;
    private final CancelReason reason;
    private final List<ScheduledTask> handles;
    private final int realmCount;
    private int cursor;
    private int cancelled;
    private int steps;
    private boolean finished;

    RealmCleanupTask(ServerScheduler scheduler, CancelReason reason, List<ScheduledTask> handles, int realmCount) {
        this.scheduler = scheduler;
        this.reason = reason;
        this.handles = List.copyOf(handles);
        this.realmCount = realmCount;
    }

    @Override
    public ServerTaskKind kind() {
        return ServerTaskKind.MAINTENANCE;
    }

    @Override
    public String tracingName() {
        return "realm-cleanup/" + reason.name();
    }

    @Override
    public StepResult step(ServerTickBudget budget) {
        if (cursor >= handles.size()) {
            finish();
            return StepResult.done(1);
        }
        // 单 tick 取消量受调度配置约束：额度用尽就下一 tick 续接，不把取消尖峰压进同一 tick
        int allowance = scheduler.cancelAllowanceRemaining();
        if (allowance <= 0) {
            return StepResult.yield(1, 1);
        }
        int processed = cancelSlots(allowance);
        steps++;
        if (cursor >= handles.size()) {
            finish();
            return StepResult.done(1);
        }
        // 处理量计入预算：让出前取消的每一条都是真实工作量，不能按空转免费
        return StepResult.yield(Math.max(1, processed), 1);
    }

    @Override
    public void onCancelled(CancelReason cancellation) {
        // 再次摘除或停服时没有下一 tick：就地排空剩余句柄，取消回调必须在最终存档前完成
        drain(cancellation == null ? reason : cancellation);
    }

    @Override
    public void onFailed(Throwable failure) {
        LOGGER.error("维度任务清理失败：原因={} 未取消={}", reason, handles.size() - cursor, failure);
        drain(reason);
    }

    // 停服兜底入口：不按每步额度，直接排空
    void drainNow() {
        drain(reason);
    }

    private int cancelSlots(int limit) {
        int processed = 0;
        while (cursor < handles.size() && processed < limit) {
            cancelNext(reason);
            processed++;
        }
        return processed;
    }

    private void drain(CancelReason drainReason) {
        while (cursor < handles.size()) {
            cancelNext(drainReason);
        }
        finish();
    }

    private void cancelNext(CancelReason cancelReason) {
        ScheduledTask handle = handles.get(cursor++);
        try {
            handle.cancel(cancelReason);
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable failure) {
            // 句柄已摘出容器，记录失误后继续推进同批其它任务的取消
            LOGGER.error("维度任务清理异常：task={} reason={}", handle.task().tracingName(), cancelReason, failure);
        }
        cancelled++;
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        // 取消原因写进日志：停服、维度卸载与规则重载共用同一实现，靠这一行区分实际路径
        LOGGER.info("维度任务清理完成：原因={} 取消={} 维度={} 步={}", reason, cancelled, realmCount, steps);
    }
}
