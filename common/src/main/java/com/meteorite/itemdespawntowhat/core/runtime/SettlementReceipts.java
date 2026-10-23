package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.EffectResult;

import java.util.function.IntConsumer;

/**
 * 回执记账：结算记录的唯一写入通道，持有待交付库存的所有权位。
 * 记录字段不可从外部直接变更，所有落账（组计划、组开始、效果回执、交付进度、终态）都经由本类转交
 * {@link SettlementRecord.Writer}，保证「改字段 + 维护账本双索引 + 标脏」在同一次调用内完成。
 * 所有权仍在本任务时，任务出口把记录的待交付量对齐到手里的结算库存；中止或交付完成后移交，
 * 本类不再触碰待交付量，避免覆盖恢复任务推进的交付进度。
 */
final class SettlementReceipts {

    private final SettlementRecord.Writer writer;
    private final SettlementRecord record;
    private boolean ownsStock = true;

    SettlementReceipts(SettlementRecord.Writer writer) {
        this.writer = writer;
        this.record = writer.record();
    }

    // 账目只读引用：字段变更方法在 SettlementRecord 内私有，这里拿不到裸写入口
    SettlementRecord record() {
        return record;
    }

    // 中止或交付完成后移交库存所有权（剩余库存改由恢复任务交付）
    void giveUpStock() {
        ownsStock = false;
    }

    // 进度回执写入通道：执行器只持有写入通道，拿不到可任意变更账目的裸入口
    IntConsumer progressSink() {
        return units -> {
            if (acceptsEffects()) {
                writer.addProgress(units);
            }
        };
    }

    // 完成、中断或失败后库存与恢复均已收尾，迟到回执不得重登记终态记录。
    private boolean acceptsEffects() {
        return record.status() == SettlementRecord.Status.PLANNED
                || record.status() == SettlementRecord.Status.STARTED;
    }

    void markStarted(long gameTime) {
        writer.markStarted(gameTime);
    }

    // 记录已选定候选与计划组数（组数为 0 表示全部返还）
    void plan(String candidateId, int groupCount) {
        writer.plan(candidateId, groupCount);
    }

    void catalystPlan(int costPerGroup) {
        writer.catalystPlan(costPerGroup);
    }

    // 先记账再扣库存：写盘可能抛异常，若先扣库存，abort 按库存回写待交付量时
    // 这次扣减还没进 consumedSources，守恒会少算 cost 件；本方法只改账目并落账
    void groupStarted(int cost) {
        writer.groupStarted(cost);
    }

    // 固定顺序：催化剂已支付、源成本已扣减，才记账
    void catalystGroupPaid(int paid) {
        writer.catalystGroupPaid(paid);
    }

    void delivered(int count) {
        writer.delivered(count);
    }

    // 任务出口对齐：所有权仍在本任务时把记录的待交付量同步到手里的结算库存
    void syncPendingDelivery(int remaining) {
        if (ownsStock) {
            writer.syncPendingDelivery(remaining);
        }
    }

    // 中止路径对齐：先移交所有权，再无条件把待返还量落盘（此后的写入只由恢复任务推进）
    void writePendingDelivery(int remaining) {
        writer.syncPendingDelivery(remaining);
    }

    void complete(long gameTime) {
        writer.complete(gameTime);
    }

    void interrupt(String reason, long gameTime) {
        writer.interrupt(reason, gameTime);
    }

    void fail(String reason, long gameTime) {
        writer.fail(reason, gameTime);
    }

    /** 进入步内累积：本步的账目变更不逐次落账，由 {@link #endStep()} 在步末合并提交。 */
    void beginStep() {
        writer.beginBatch();
    }

    /** 结束步内累积并落账本步累积的变更；步末无变更时不产生提交。 */
    void endStep() {
        writer.endBatch();
    }

    // 本记录的落账次数与被步内合并的变更次数：随结算完成观测输出
    int commitCount() {
        return writer.commitCount();
    }

    int deferredMutations() {
        return writer.deferredMutations();
    }

    // 执行器回执入账：已完成、已受理未完成、跳过与失败分别计数，计划量不冒充成功量；
    // 终态后的迟到回执与进度回执共用门禁，避免重新登记已经终结或裁剪的记录。
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
        // 回执只更新本记录的效果计数（applied/pending units），不触碰待交付量；
        // 待交付量对齐由调用方在随后的提交边界（syncPendingDelivery）补齐
    }
}
