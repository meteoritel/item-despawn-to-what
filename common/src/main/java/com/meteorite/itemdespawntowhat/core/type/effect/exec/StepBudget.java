package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepWorkCounter;

/** 一次 advance 共用的额度；调度器尚未扣账的已报告工作量在步内立即生效。 */
final class StepBudget {
    private final ServerTickBudget tick;
    private final int limit;
    private int reported;

    StepBudget(ServerTickBudget tick, int limit) {
        this.tick = tick;
        this.limit = limit;
    }

    boolean exhausted() {
        return reported >= limit || tick.remainingWorkUnits() <= reported || tick.exhausted();
    }

    void spend() {
        StepWorkCounter.report(1);
        reported++;
    }

    long gameTime() {
        return tick.gameTime();
    }
}
