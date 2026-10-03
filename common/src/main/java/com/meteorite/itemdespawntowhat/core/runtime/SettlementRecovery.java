package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.runtime.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerScheduler;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.StepResult;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.ReturnItemSpawner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 持久返还交付任务：加载或重启后只交付结算记录里的待返还库存，不恢复旧效果队列。
 * 交付量每步直接读取记录字段 pendingDelivery（唯一口径，与「已派发未回执」的 pendingUnits 无关），
 * 交付成功后才扣减待交付量并写盘，因此正常路径不会多生成；区块不可用时带退避延后重试，
 * 停服/维度卸载时由调度器带原因取消，取消路径不做世界写入。
 */
final class SettlementRecovery implements ServerTask {

    private static final Logger LOGGER = LogManager.getLogger();
    // 交付失败后的退避起点与上限：20 tick 起，最多 1200 tick（1 分钟），避免区块长期不可用时反复重试
    private static final int BASE_RETRY_DELAY_TICKS = 20;
    private static final int MAX_RETRY_DELAY_TICKS = 1200;

    private final ConversionRuntime runtime;
    private final ServerScheduler scheduler;
    private final ServerLevel level;
    private final SettlementRecord record;
    private final SettlementLedger ledger;
    // 位置载体：记录创建后源实体可能已经不存在，只借它提供搜索起点，不加入世界
    private final ItemEntity anchor;
    private ReturnItemSpawner.PositionSearch search;
    private int failures;
    // 取消幂等：同一记录被重复取消（停服后又维度卸载）不得重复记账
    private boolean cancelled;

    SettlementRecovery(ConversionRuntime runtime, ServerScheduler scheduler, ServerLevel level,
                       SettlementRecord record, SettlementLedger ledger, Vec3 position) {
        this.runtime = runtime;
        this.scheduler = scheduler;
        this.level = level;
        this.record = record;
        this.ledger = ledger;
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
                // 交付成功后才扣减待交付量并写盘：账目始终等于「还没交付出去的库存」
                record.delivered(stack.getCount());
                ledger.put(record);
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
        if (cancelled) {
            return;
        }
        cancelled = true;
        // 释放未支付的催化剂预留（恢复路径不支付催化剂，仅防御性释放）：幂等、无世界写入
        runtime.catalystReservations().releaseOwner(record.id());
        // 取消（维度卸载/停服）不做世界写入：已交付量在每次交付后已写盘，这里只保留待返还数据
        // 顺序证据：停服取消发生在 vanilla stopServer() 的 ServerStoppingEvent/SERVER_STOPPING，
        // 之后才是 flush → saveAllChunks → 维度 close，所以这次 ledger.put 会被最终存档保存
        record.interrupt(reason.name(), level.getGameTime());
        try {
            ledger.put(record);
        } catch (RuntimeException failure) {
            LOGGER.warn("返还记录写盘失败：id={} reason={}", record.id(), reason, failure);
        }
        runtime.onRecoveryFinished(record.id());
    }

    // 交付结束：记录标记完成并释放幂等位
    private void finish() {
        record.complete(level.getGameTime());
        ledger.put(record);
        runtime.onRecoveryFinished(record.id());
        LOGGER.debug("返还交付完成：记录={} 已交付={}", record.id(), record.deliveredReturns());
    }

    // 退避：20/40/80…最高 1200 tick
    private int retryDelayTicks() {
        int shift = Math.clamp(failures - 1, 0, 6);
        return Math.min(MAX_RETRY_DELAY_TICKS, BASE_RETRY_DELAY_TICKS << shift);
    }
}
