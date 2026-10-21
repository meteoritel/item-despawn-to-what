package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerScheduler;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.ReturnItemSpawner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 支付的输入复核与返还交付。
 * 支付：开组当刻复核门槛与流体（门槛只读在前，流体 consume 有世界写入在后，避免无谓写入），
 * 复核通过才扣催化剂；任一项不满足都由 {@link CatalystReservations} 回传具体原因，本类不自行截断组数。
 * 交付：剩余库存作为返还掉落物分批加入世界；区块未加载或位置搜索预算耗尽时保留待返还，由调用方决定重试节奏。
 */
final class SettlementPayment {

    // 一步交付的结果语义：全部交完、本刻让出、需要带退避重试
    enum DeliveryOutcome { DONE, YIELD, RETRY }

    // 成功交付一份返还物的计价：位置搜索推进与世界写入各占一个工作单位
    static final int DELIVERY_WORK_UNITS = 2;

    private final ConversionRuntime runtime;
    private final ServerScheduler scheduler;
    private final ServerLevel level;
    private final ItemEntity source;
    private final ItemStack held;
    private final SettlementReceipts receipts;
    // 返还位置搜索游标：跨刻保留进度，交付成功后重置
    private ReturnItemSpawner.PositionSearch rebateSearch;

    SettlementPayment(ConversionRuntime runtime, ServerScheduler scheduler, ServerLevel level, ItemEntity source,
                      ItemStack held, SettlementReceipts receipts) {
        this.runtime = runtime;
        this.scheduler = scheduler;
        this.level = level;
        this.source = source;
        this.held = held;
        this.receipts = receipts;
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

    // 本刻把剩余库存按批大小交付为返还掉落物：每成功一份扣减结算库存并落账；
    // 区块未加载时本刻未扣减待交付量，返回 RETRY 由调用方带退避重试
    DeliveryOutcome deliverRemaining(ServerTickBudget budget, int batch) {
        int deliveredNow = 0;
        while (held.getCount() > 0 && deliveredNow < batch) {
            if (budget.exhausted()) {
                return DeliveryOutcome.YIELD;
            }
            int size = Math.clamp(held.getCount(), 1, Math.max(1, held.getMaxStackSize()));
            ItemStack stack = held.copyWithCount(size);
            ReturnItemSpawner.Outcome outcome = deliverStack(stack, budget);
            if (outcome == ReturnItemSpawner.Outcome.CHUNK_UNLOADED) {
                return DeliveryOutcome.RETRY;
            }
            if (outcome == ReturnItemSpawner.Outcome.NEEDS_MORE_STEPS) {
                return DeliveryOutcome.YIELD;
            }
            held.setCount(held.getCount() - size);
            receipts.delivered(size);
            budget.charge(DELIVERY_WORK_UNITS);
            deliveredNow++;
        }
        return held.getCount() > 0 ? DeliveryOutcome.YIELD : DeliveryOutcome.DONE;
    }

    // 返还物加入世界：位置搜索在公共预算内分步推进；区块未加载或生成被拒时保留待返还并重试
    private ReturnItemSpawner.Outcome deliverStack(ItemStack stack, ServerTickBudget budget) {
        if (rebateSearch == null) {
            // 位置搜索游标按源实体位置创建一次，跨刻保留进度；源实体已清空并在下个 tick 移除，位置即触发点
            rebateSearch = new ReturnItemSpawner.PositionSearch(level, source);
        }
        return ReturnItemSpawner.deliver(stack, rebateSearch, budget,
                scheduler.config().positionSearchChecksPerTick());
    }
}
