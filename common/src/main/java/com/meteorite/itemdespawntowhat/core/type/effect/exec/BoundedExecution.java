package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.EffectStep;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;

/** 包内有界推进骨架：一个 advance 共用额度，内部续接不扩展调度契约，终态只提交一次。 */
abstract class BoundedExecution implements EffectExecution {
    protected sealed interface Advancement {
        enum Continue implements Advancement { INSTANCE }
        record Stop(EffectStep step) implements Advancement {}
    }

    private EffectResult terminal;

    protected int workUnitsPerStep() { return Integer.MAX_VALUE; }

    protected void beginStep() {}

    protected abstract Advancement advanceStep(StepBudget budget);

    protected void beforeFinish() {}

    protected static Advancement stop(EffectStep step) {
        return new Advancement.Stop(step);
    }

    @Override
    public final EffectStep advance(ServerTickBudget budget) {
        if (terminal != null) {
            return EffectStep.done(terminal);
        }
        StepBudget step = new StepBudget(budget, workUnitsPerStep());
        beginStep();
        while (true) {
            switch (advanceStep(step)) {
                case Advancement.Continue ignored -> { }
                case Advancement.Stop(EffectStep result) -> {
                    if (result instanceof EffectStep.Done(EffectResult completed)) {
                        beforeFinish();
                        terminal = completed;
                    }
                    return result;
                }
            }
        }
    }
}
