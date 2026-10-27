package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.RuntimeObserver;
import com.meteorite.itemdespawntowhat.core.api.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.SchedulerConfig;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepResult;
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
    private static final String FAILURE_REASON = "TASK_FAILED";

    private final SettlementRecoveryQueue queue;
    private final SchedulerConfig config;
    private final ServerLevel level;
    private final SettlementRecord record;
    // 受控写入句柄：交付递减与终态标记都经它配套落账（双索引 + 标脏）
    private final SettlementRecord.Writer writer;
    // 位置载体：记录创建后源实体可能已经不存在，只借它提供搜索起点，不加入世界
    private final RebateDelivery delivery;
    private int failures;
    // 终态清理幂等：失败与取消共用，避免重复记账。
    private boolean closed;

    SettlementRecovery(SettlementRecoveryQueue queue, SchedulerConfig config, ServerLevel level,
                       SettlementRecord.Writer writer, Vec3 position) {
        this.queue = queue;
        this.config = config;
        this.level = level;
        this.writer = writer;
        this.record = writer.record();
        ItemEntity anchor = new ItemEntity(level, position.x, position.y, position.z, ItemStack.EMPTY);
        this.delivery = new RebateDelivery(level, anchor, writer, config.positionSearchChecksPerTick());
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
        RebateDelivery.Result result = delivery.advance(budget, Math.max(1, config.dispatchBatchSize()));
        if (result.deliveredStacks() > 0) {
            failures = 0;
        }
        if (result.stop() == RebateDelivery.Stop.DONE) {
            finish();
            return StepResult.done(1);
        }
        if (result.stop() == RebateDelivery.Stop.CHUNK_UNLOADED
                || result.stop() == RebateDelivery.Stop.SEARCH_PENDING) {
            budget.charge(1);
            failures++;
            if (result.stop() == RebateDelivery.Stop.CHUNK_UNLOADED) {
                LOGGER.debug("返还交付延后（区块不可用）：记录={} 维度={} 待返还={} 连续失败={}",
                        record.id(), record.dimension(), record.pendingDelivery(), failures);
                return StepResult.retry(1, RecoveryBackoff.delayTicks(failures));
            }
        }
        LOGGER.debug("返还交付部分完成：记录={} 本刻交付={} 剩余待返还={}",
                record.id(), result.deliveredStacks(), record.pendingDelivery());
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
    // failed=true 时把剩余交付交给运行期恢复队列按退避重试；取消则交回运行时，由它按记录是否仍需恢复
    // 决定是否立即重排（reload 会取消在飞恢复，取消后本会话仍需继续交付）
    private void close(String reason, boolean failed) {
        if (closed) {
            return;
        }
        closed = true;
        try {
            // 失败与取消均不补做世界写入，只保留剩余库存并释放未支付预留。
            queue.reservations().releaseOwner(record.id());
            try {
                // 恢复失败/取消只记中断并落账，不补做世界写入
                writer.interrupt(reason, level.getGameTime());
            } catch (RuntimeException failure) {
                LOGGER.warn("返还记录写盘失败：id={} reason={}", record.id(), reason, failure);
            }
        } finally {
            // 即使收尾写盘或预留释放失败，也必须允许后续扫描或运行期重试重新排期。
            if (failed) {
                queue.onFailed(record.id(), reason);
            } else {
                queue.onFinished(level.getServer(), record.id());
            }
        }
    }

    // 交付结束：记录标记完成并落账，释放幂等位
    private void finish() {
        writer.complete(level.getGameTime());
        closed = true;
        queue.onFinished(level.getServer(), record.id());
        LOGGER.debug("返还交付完成：记录={} 已交付={}", record.id(), record.deliveredReturns());
    }

}
