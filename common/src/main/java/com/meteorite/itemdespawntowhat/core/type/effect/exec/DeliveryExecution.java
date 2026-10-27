package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.ChunkRequirement;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.EffectStep;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * 逐单位交付循环：把「待落实体 → 区块门 → 预算双检 → 逐单位回执 → 收尾清理」收成一份状态机。
 * 子类只描述产出什么单位、怎么写入、节奏与终态，区块等待、计费与回执记账不再各自实现。
 *
 * <p>与 {@link com.meteorite.itemdespawntowhat.core.api.EffectExecution} 契约的分工保持不变（见 ADR-0042）：执行状态只上报当前需要的区块，
 * 何时复查、排期与超时由调用层驱动；本类不自己轮询区块，也不持有 tick 级状态。
 *
 * <p>子类必须保持的语义：单位真正写入世界之后才回执完成量；写入失败只表示目标区块未加载，
 * 单位被保留到下次推进，不重造、不重抽。
 */
abstract class DeliveryExecution extends BoundedExecution {

    /** 一次交付的单位：已加入世界的实体，以及本次写入应计入的完成量。 */
    protected record Unit(Entity entity, int appliedUnits) {}

    /** 产出下一单位的结果；等待区块与预算续接在这里被显式区分，避免子类各自解释。 */
    protected sealed interface Production {

        /** 拿到一个待写入世界的单位。 */
        record Next(Unit unit) implements Production {}

        /** 本步额度或时空预算已用完：让出，稍后继续，不改变已保留的状态。 */
        record Busy() implements Production {}

        /** 产出本身要求先有某个区块：交给调用层等待。 */
        record Await(ChunkRequirement requirement) implements Production {}

        /** 交付就此结束，且终态由产出路径给出（例如搜索不到可生成位置）。 */
        record Finished(EffectResult result) implements Production {}
    }

    protected final EffectContext context;
    private Unit pending;
    private int written;

    protected DeliveryExecution(EffectContext context) {
        this.context = context;
    }

    // 没有待写单位时的区块声明：由子类按自己的搜索与完成状态给出，可为 null
    protected abstract @Nullable ChunkRequirement declaredRequirement();

    // 产出下一个待写单位：允许扣本步额度（如候选点检查），但不直接写世界
    protected abstract Production produce(StepBudget budget);

    // 真正写入世界：目标区块未加载返回 false，单位保留在 pending 里等待
    protected abstract boolean write(Entity entity);

    protected abstract boolean isComplete();

    // 全部单位已交付时的终态回执：只表达剩余增量，完成量已逐单位回执
    protected abstract EffectResult result();

    protected abstract int unitsPerStep();

    // 单位之间的最小间隔刻数；为 0 表示本步还有余量就继续，用尽后用 Yield 续接
    protected abstract long intervalTicks();

    // 每步开头：重置只在本步内有效的额度（候选点检查次数等），跨步保留的状态不要在这里清
    protected void onStepStart() {}

    // 单位写入成功后推进游标、发反馈；此时完成量已回执、工作量已计费
    protected abstract void onUnitWritten(Unit unit);

    @Override
    protected final void beginStep() {
        written = 0;
        onStepStart();
    }

    @Override
    protected final Advancement advanceStep(StepBudget budget) {
        if (isComplete()) {
            return stop(EffectStep.done(result()));
        }
        if (written >= Math.max(1, unitsPerStep())) {
            long interval = intervalTicks();
            return stop(interval > 0
                    ? new EffectStep.Delay(context.level().getGameTime() + interval)
                    : EffectStep.Yield.INSTANCE);
        }
        if (budget.exhausted()) {
            return stop(EffectStep.Yield.INSTANCE);
        }
        if (pending == null) {
            switch (produce(budget)) {
                case Production.Next(Unit unit) -> pending = unit;
                case Production.Busy ignored -> {
                    return stop(EffectStep.Yield.INSTANCE);
                }
                case Production.Await(ChunkRequirement requirement) -> {
                    return stop(new EffectStep.AwaitChunks(requirement));
                }
                case Production.Finished(EffectResult produced) -> {
                    return stop(EffectStep.done(produced));
                }
            }
        }
        // 生产可能消耗候选检查额度；待写实体在让出或等待区块时保持原样。
        if (budget.exhausted()) {
            return stop(EffectStep.Yield.INSTANCE);
        }
        if (!write(pending.entity())) {
            return stop(new EffectStep.AwaitChunks(ChunkRequirement.at(pending.entity().blockPosition())));
        }
        context.reportProgress(pending.appliedUnits());
        budget.spend();
        onUnitWritten(pending);
        pending = null;
        written++;
        return Advancement.Continue.INSTANCE;
    }

    @Override
    public final @Nullable ChunkRequirement requiredChunks() {
        ChunkRequirement declared = declaredRequirement();
        if (pending == null) {
            return declared;
        }
        BlockPos position = pending.entity().blockPosition();
        return declared == null ? ChunkRequirement.at(position) : declared.including(position);
    }

    @Override
    public final void onFinished(EffectResult result) {
        // 交付完成或已失败：不再持有未写单位，取消路径也不补做
        pending = null;
    }

}
