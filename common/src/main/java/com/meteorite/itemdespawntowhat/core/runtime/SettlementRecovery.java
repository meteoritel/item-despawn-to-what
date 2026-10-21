package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.RuntimeObserver;
import com.meteorite.itemdespawntowhat.core.api.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerScheduler;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepResult;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.ReturnItemSpawner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

/**
 * 持久返还交付任务：加载或重启后只交付结算记录里的待返还库存，不恢复既有效果队列。
 * 交付量每步直接读取记录字段 pendingDelivery（唯一口径，与「已派发未回执」的 pendingUnits 无关），
 * 交付成功后才扣减待交付量并写盘，因此正常路径不会多生成；区块不可用时带退避延后重试，
 * 停服/维度卸载时由调度器带原因取消，取消路径不做世界写入。
 */
final class SettlementRecovery implements ServerTask {

    private static final Logger LOGGER = LogManager.getLogger();
    // 交付失败后的退避起点与上限：20 tick 起，最多 1200 tick（1 分钟），避免区块长期不可用时反复重试
    private static final int BASE_RETRY_DELAY_TICKS = 20;
    private static final int MAX_RETRY_DELAY_TICKS = 1200;
    private static final String FAILURE_REASON = "TASK_FAILED";

    private final ConversionRuntime runtime;
    private final ServerScheduler scheduler;
    private final ServerLevel level;
    private final SettlementRecord record;
    // 受控写入句柄：交付递减与终态标记都经它配套落账（双索引 + 标脏）
    private final SettlementRecord.Writer writer;
    // 位置载体：记录创建后源实体可能已经不存在，只借它提供搜索起点，不加入世界
    private final ItemEntity anchor;
    private ReturnItemSpawner.PositionSearch search;
    private int failures;
    // 终态清理幂等：失败与取消共用，避免重复记账。
    private boolean closed;

    SettlementRecovery(ConversionRuntime runtime, ServerScheduler scheduler, ServerLevel level,
                       SettlementRecord.Writer writer, Vec3 position) {
        this.runtime = runtime;
        this.scheduler = scheduler;
        this.level = level;
        this.writer = writer;
        this.record = writer.record();
        this.anchor = new ItemEntity(level, position.x, position.y, position.z, ItemStack.EMPTY);
    }

    @Override
    public ServerTaskKind kind() {
        return ServerTaskKind.REBATE;
    }

    @Override
    public String tracingName() {
        return "recovery/" + record.id();
    }

    @Override
    public StepResult step(ServerTickBudget budget) {
        // 恢复任务由运行期恢复队列直接排入，不经观察器的排期入口，调试注入的步骤失败在这里进入失败路径
        if (RuntimeObserver.current().enabled()) {
            RuntimeObserver.current().taskStepStarting(level.getServer(), kind(), tracingName());
        }
        if (record.pendingDelivery() <= 0) {
            finish();
            return StepResult.done(1);
        }
        if (search == null) {
            search = new ReturnItemSpawner.PositionSearch(level, anchor);
        }
        int maxStacks = Math.max(1, scheduler.config().dispatchBatchSize());
        int delivered = 0;
        while (delivered < maxStacks && !budget.exhausted()) {
            int pending = record.pendingDelivery();
            if (pending <= 0) {
                break;
            }
            ItemStack stack = record.source().copy();
            stack.setCount(Math.min(pending, stack.getMaxStackSize()));
            ReturnItemSpawner.Outcome outcome = ReturnItemSpawner.deliver(stack, search, budget,
                    scheduler.config().positionSearchChecksPerTick());
            if (outcome == ReturnItemSpawner.Outcome.ADDED) {
                // 交付成功后才扣减待交付量并落账：账目始终等于「还没交付出去的库存」
                writer.delivered(stack.getCount());
                budget.charge(2);
                delivered++;
                failures = 0;
                continue;
            }
            budget.charge(1);
            failures++;
            if (outcome == ReturnItemSpawner.Outcome.CHUNK_UNLOADED) {
                LOGGER.debug("返还交付延后（区块不可用）：记录={} 维度={} 待返还={} 连续失败={}",
                        record.id(), record.dimension(), record.pendingDelivery(), failures);
                return StepResult.retry(1, retryDelayTicks());
            }
            // 本刻位置搜索预算用尽：保留搜索断点，下刻继续
            return StepResult.yield(1, 1);
        }
        if (record.pendingDelivery() <= 0) {
            finish();
            return StepResult.done(1);
        }
        LOGGER.debug("返还交付部分完成：记录={} 本刻交付={} 剩余待返还={}",
                record.id(), delivered, record.pendingDelivery());
        return StepResult.yield(1, 1);
    }

    @Override
    public void onCancelled(CancelReason reason) {
        close(reason.name(), false);
    }

    @Override
    public void onFailed(@Nullable Throwable failure) {
        close(FAILURE_REASON, true);
    }

    // 失败与取消共用收尾：保留剩余库存、释放预留并释放幂等位；
    // failed=true 时把剩余交付交给运行期恢复队列按退避重试，取消则不重排（等下次扫描）
    private void close(String reason, boolean failed) {
        if (closed) {
            return;
        }
        closed = true;
        try {
            // 失败与取消均不补做世界写入，只保留剩余库存并释放未支付预留。
            runtime.catalystReservations().releaseOwner(record.id());
            try {
                // 恢复失败/取消只记中断并落账，不补做世界写入
                writer.interrupt(reason, level.getGameTime());
            } catch (RuntimeException failure) {
                LOGGER.warn("返还记录写盘失败：id={} reason={}", record.id(), reason, failure);
            }
        } finally {
            // 即使收尾写盘或预留释放失败，也必须允许后续扫描或运行期重试重新排期。
            if (failed) {
                runtime.onRecoveryFailed(record.id(), reason);
            } else {
                runtime.onRecoveryFinished(record.id());
            }
        }
    }

    // 交付结束：记录标记完成并落账，释放幂等位
    private void finish() {
        writer.complete(level.getGameTime());
        runtime.onRecoveryFinished(record.id());
        LOGGER.debug("返还交付完成：记录={} 已交付={}", record.id(), record.deliveredReturns());
    }

    // 运行期恢复队列的退避：按已尝试次数指数退避，20/40/80…最高 1200 tick
    static int queueRetryDelayTicks(int attempts) {
        int shift = Math.clamp(attempts - 1, 0, 6);
        return Math.min(MAX_RETRY_DELAY_TICKS, BASE_RETRY_DELAY_TICKS << shift);
    }

    // 交付失败的退避：同一套指数退避口径
    private int retryDelayTicks() {
        return queueRetryDelayTicks(failures);
    }
}
