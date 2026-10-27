package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.ReturnItemSpawner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** 返还库存的有界交付循环：账目是唯一数量来源，位置搜索跨刻续接。 */
final class RebateDelivery {
    private static final int DELIVERY_WORK_UNITS = 2;

    // 搜索未完成属于一次未成功尝试；批量/总预算让出则没有新的失败尝试。
    enum Stop { DONE, YIELD, SEARCH_PENDING, CHUNK_UNLOADED }

    record Result(Stop stop, int deliveredStacks) {}

    private final ServerLevel level;
    private final ItemEntity anchor;
    private final SettlementRecord.Writer writer;
    private final SettlementRecord record;
    private final int positionChecks;
    private ReturnItemSpawner.PositionSearch search;

    RebateDelivery(ServerLevel level, ItemEntity anchor, SettlementRecord.Writer writer, int positionChecks) {
        this.level = level;
        this.anchor = anchor;
        this.writer = writer;
        this.record = writer.record();
        this.positionChecks = positionChecks;
    }

    Result advance(ServerTickBudget budget, int maxStacks) {
        int delivered = 0;
        while (record.pendingDelivery() > 0 && delivered < maxStacks) {
            if (budget.exhausted()) {
                return new Result(Stop.YIELD, delivered);
            }
            if (search == null) {
                search = new ReturnItemSpawner.PositionSearch(level, anchor);
            }
            int count = Math.clamp(record.pendingDelivery(), 1, Math.max(1, record.source().getMaxStackSize()));
            ItemStack stack = record.source().copyWithCount(count);
            ReturnItemSpawner.Outcome outcome = ReturnItemSpawner.deliver(stack, search, budget, positionChecks);
            if (outcome != ReturnItemSpawner.Outcome.ADDED) {
                return new Result(outcome == ReturnItemSpawner.Outcome.CHUNK_UNLOADED
                        ? Stop.CHUNK_UNLOADED : Stop.SEARCH_PENDING, delivered);
            }
            writer.delivered(count);
            budget.charge(DELIVERY_WORK_UNITS);
            delivered++;
        }
        return new Result(record.pendingDelivery() == 0 ? Stop.DONE : Stop.YIELD, delivered);
    }
}
