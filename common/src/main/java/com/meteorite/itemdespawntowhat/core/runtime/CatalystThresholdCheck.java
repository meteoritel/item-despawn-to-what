package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.model.CatalystCost;
import com.meteorite.itemdespawntowhat.core.type.TaggedItemMatch;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.EffectTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** 每轮开组前按共享半径检查门槛，保留预算游标并在扣除前复核实体。 */
final class CatalystThresholdCheck {
    private final ServerLevel level;
    private final ItemEntity source;
    private final CatalystCost cost;
    private final AABB box;
    private final BlockPos center;
    private List<ItemEntity> items;
    private CatalystAllocation allocation;
    private List<CatalystReservations.Hold> plan;

    CatalystThresholdCheck(ServerLevel level, ItemEntity source, Vec3 position, CatalystCost cost) {
        this.level = level;
        this.source = source;
        this.cost = cost;
        center = BlockPos.containing(position);
        box = EffectTargets.blockBox(center, cost.radius());
    }

    // true 表示检查完成；结果在开组提交时由 valid() 读取。
    boolean advance(ServerTickBudget budget) {
        if (items == null) {
            if (budget.exhausted()) return false;
            budget.charge(1);
            if (!com.meteorite.itemdespawntowhat.core.api.ChunkPresence.containsArea(level, center, cost.radius())) {
                plan = List.of();
                return true;
            }
            var target = TaggedItemMatch.compileAll(cost.items());
            items = level.getEntitiesOfClass(ItemEntity.class, box,
                    item -> item != source && item.isAlive() && target.matches(item.getItem()));
            int[] available = items.stream().mapToInt(item -> item.getItem().getCount()).toArray();
            allocation = new CatalystAllocation(cost.thresholdPaymentProfile(), items, available, 1);
        }
        if (!allocation.advance(budget)) return false;
        if (allocation.groups() == 0) { plan = List.of(); return true; }
        if (plan == null) plan = allocation.thresholdPlan(budget);
        return plan != null;
    }

    boolean valid() {
        if (plan == null || plan.isEmpty()
                || !com.meteorite.itemdespawntowhat.core.api.ChunkPresence.containsArea(level, center, cost.radius())) return false;
        for (var hold : plan) {
            if (!(level.getEntity(hold.itemId()) instanceof ItemEntity item) || !item.isAlive()
                    || !box.contains(item.position()) || item.getItem().getItem() != hold.expectedItem()
                    || item.getItem().getCount() < hold.count()) return false;
        }
        return true;
    }
}
