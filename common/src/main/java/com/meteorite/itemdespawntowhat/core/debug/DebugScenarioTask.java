package com.meteorite.itemdespawntowhat.core.debug;

import com.meteorite.itemdespawntowhat.core.api.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ScheduledTaskHandle;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepResult;

/**
 * 场景任务的注入装饰器：保持委托任务的分类、追踪名与终态语义，只在步骤与取消回调前插入受控故障。
 * 未声明故障或次数已用完时，执行结果与直接排队完全一致。
 */
final class DebugScenarioTask implements ServerTask {
    private final ServerTask delegate;
    private final DebugScenarioRun run;
    private final ScheduledTaskHandle[] holder;
    private final boolean releaseOnStep;

    DebugScenarioTask(ServerTask delegate, DebugScenarioRun run, ScheduledTaskHandle[] holder, boolean releaseOnStep) {
        this.delegate = delegate;
        this.run = run;
        this.holder = holder;
        this.releaseOnStep = releaseOnStep;
    }

    @Override public ServerTaskKind kind() { return delegate.kind(); }

    @Override public String tracingName() { return delegate.tracingName(); }

    @Override public StepResult step(ServerTickBudget budget) {
        if (run.faults.step(kind(), tracingName())) {
            throw new DebugScenarioFaults.InjectedFault("注入的步骤异常：" + kind() + "/" + tracingName());
        }
        // 一次性动作在执行前释放句柄，续接任务等终态回调再释放，避免场景停止时重复取消
        if (releaseOnStep) { run.tasks.remove(holder[0]); }
        return delegate.step(budget);
    }

    @Override public void onCancelled(CancelReason reason) {
        try {
            if (run.faults.cancel(kind(), tracingName())) {
                throw new DebugScenarioFaults.InjectedFault("注入的取消回调异常：" + kind() + "/" + tracingName());
            }
            delegate.onCancelled(reason);
        } finally {
            run.tasks.remove(holder[0]);
        }
    }

    @Override public void onFailed(Throwable failure) {
        try { delegate.onFailed(failure); }
        finally { run.tasks.remove(holder[0]); }
    }

    @Override public void onReleased() {
        try { delegate.onReleased(); }
        finally { run.tasks.remove(holder[0]); }
    }
}