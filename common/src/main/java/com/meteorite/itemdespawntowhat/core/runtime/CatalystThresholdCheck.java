package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.ChunkPresence;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.model.CatalystCost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * 每轮开组前的门槛复核：只校验计划期确定的候选实体是否存活、类型是否未变、件数是否够本组门槛。
 * 可用量口径与计划期一致：现存件数减去其它任务已预留的部分（{@link CatalystReservations#availableExcept}）。
 * 位置不参与复核：催化剂被推动不改变归属，只有实体消失、类型变化或件数不足才算门槛不足。
 */
final class CatalystThresholdCheck {
    private final ServerLevel level;
    private final List<ItemEntity> picks;
    private final CatalystCost cost;
    private final CatalystReservations reservations;
    private final String owner;
    private final BlockPos center;
    private List<ItemEntity> items;
    private CatalystAllocation allocation;
    private List<CatalystReservations.Hold> plan;

    // picks 是计划期按共享半径收集的候选实体；开组前按实体标识重新解析，不再重扫位置盒
    CatalystThresholdCheck(ServerLevel level, Vec3 position, List<ItemEntity> picks, CatalystCost cost,
                           CatalystReservations reservations, String owner) {
        this.level = level;
        this.picks = picks;
        this.cost = cost;
        this.reservations = reservations;
        this.owner = owner;
        center = BlockPos.containing(position);
    }

    // true 表示检查完成；结果在开组提交时由 valid() 读取。
    boolean advance(ServerTickBudget budget) {
        if (items == null) {
            if (budget.exhausted()) return false;
            budget.charge(1);
            // 区块未加载时按实体标识解析会得到空结果，先守住计划期同一片区域，避免把未加载误判成门槛不足
            if (!ChunkPresence.containsArea(level, center, cost.radius())) {
                plan = List.of();
                return true;
            }
            List<ItemEntity> live = new ArrayList<>(picks.size());
            for (ItemEntity pick : picks) {
                if (!(level.getEntity(pick.getUUID()) instanceof ItemEntity item) || !item.isAlive()) continue;
                live.add(item);
            }
            // 先按存活筛选再算可用量：可用量要按实体重读，不能在计划期快照上扣减
            items = live;
            int[] available = items.stream()
                    .mapToInt(item -> reservations.availableExcept(level, item, owner)).toArray();
            allocation = new CatalystAllocation(cost.thresholdPaymentProfile(), items, available, 1);
        }
        if (!allocation.advance(budget)) return false;
        if (allocation.groups() == 0) { plan = List.of(); return true; }
        if (plan == null) plan = allocation.thresholdPlan(budget);
        return plan != null;
    }

    // 开组当刻复核：只校验实体仍存在、类型未变且扣除其它任务预留后仍够本组门槛
    boolean valid() {
        if (plan == null || plan.isEmpty()) return false;
        for (var hold : plan) {
            if (!(level.getEntity(hold.itemId()) instanceof ItemEntity item) || !item.isAlive()
                    || item.getItem().getItem() != hold.expectedItem()
                    || reservations.availableExcept(level, item, owner) < hold.count()) return false;
        }
        return true;
    }
}
