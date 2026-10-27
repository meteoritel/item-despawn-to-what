package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.EffectResult;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** 效果回执解释与进度入口：接收资格由结算生命周期统一决定。 */
final class SettlementReceipts {
    private final SettlementRecord.Writer writer;
    private final BooleanSupplier active;

    SettlementReceipts(SettlementRecord.Writer writer, BooleanSupplier active) {
        this.writer = writer;
        this.active = active;
    }

    IntConsumer progressSink() {
        return units -> {
            if (acceptsEffects()) {
                writer.addProgress(units);
            }
        };
    }

    boolean acceptsEffects() {
        return active.getAsBoolean();
    }

    // 计划量不冒充完成量；终态后的进度与最终回执共用同一门禁。
    void apply(EffectResult result) {
        if (!acceptsEffects()) {
            return;
        }
        if (result == null) {
            writer.effectFailed();
        } else {
            switch (result.outcome()) {
                case APPLIED -> writer.addApplied(result.appliedUnits());
                case DEFERRED -> {
                    writer.addApplied(result.appliedUnits());
                    writer.addPending(result.pendingUnits());
                }
                case SKIPPED -> writer.effectSkipped();
                case FAILED -> writer.effectFailed();
            }
        }
    }
}
