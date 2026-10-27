package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/** 开组当刻复核门槛与流体，通过后才扣催化剂；门槛只读在前、流体世界写在后。 */
final class SettlementPayment {
    private final ConversionRuntime runtime;
    private final ServerLevel level;

    SettlementPayment(ConversionRuntime runtime, ServerLevel level) {
        this.runtime = runtime;
        this.level = level;
    }

    // 开组前支付催化剂：门槛与流体统一在提交回调里复核，通过后才真实扣减；返回原因由调用方分流
    CatalystReservations.PayResult pay(ServerTickBudget budget, String owner, int perGroup,
                                       @Nullable CatalystThresholdCheck threshold,
                                       @Nullable FluidSourceResolver fluid) {
        return runtime.catalystReservations().payGroup(owner, level, perGroup, budget,
                () -> commitInputs(threshold, fluid));
    }

    // 开组前的输入复核：门槛与流体任一不满足都返回对应原因，不通过时不扣催化剂、不开组
    private static @Nullable CatalystReservations.PayOutcome commitInputs(
            @Nullable CatalystThresholdCheck threshold, @Nullable FluidSourceResolver fluid) {
        if (threshold != null && !threshold.valid()) {
            return CatalystReservations.PayOutcome.THRESHOLD_SHORT;
        }
        if (fluid != null && !fluid.consume()) {
            return CatalystReservations.PayOutcome.FLUID_SHORT;
        }
        return null;
    }

}
