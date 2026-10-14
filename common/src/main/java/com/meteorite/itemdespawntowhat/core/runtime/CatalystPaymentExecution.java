package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.model.CatalystCost;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.EffectTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** 效果式催化剂付款：共享库存分配可续接，完整清单复核与真实扣减在主线程一次完成。 */
public final class CatalystPaymentExecution implements EffectExecution {
    private static final String CATALYST_ABSENT = "catalyst_absent";
    private static final String CATALYST_CHANGED = "catalyst_changed";
    private final CatalystCost cost;
    private final EffectContext context;
    private final AABB box;
    private final int rounds;
    private List<ItemEntity> candidates;
    private int[] available;
    private int scanCursor;
    private CatalystAllocation allocation;
    private List<CatalystReservations.Hold> plan;
    private int chargedPayments;
    private EffectResult result;

    /**
     * 固定本次付款的需求和作用范围；构造时不查询世界。
     * @param cost 每轮催化剂需求
     * @param context 服务端效果上下文
     */
    public CatalystPaymentExecution(CatalystCost cost, EffectContext context) {
        this.cost = cost;
        this.context = context;
        box = EffectTargets.blockBox(BlockPos.containing(context.position()), cost.radius());
        rounds = Math.max(1, context.rounds());
    }

    /**
     * 推进预算计算；预算不足保留游标，完成后重复调用返回同一回执。
     * @param budget 当前服务器 tick 共享预算
     * @return 最终真实回执，尚未完成时为 null
     */
    @Override
    public @Nullable EffectResult advance(ServerTickBudget budget) {
        if (result != null) return result;
        if (budget.exhausted()) return null;
        if (candidates == null) {
            budget.charge(1);
            // 原版邻域查询与排序不可抢占；逐实体库存采集和分配搜索在后续预算步骤完成。
            candidates = context.level().getEntitiesOfClass(ItemEntity.class, box,
                    item -> item != context.source() && item.isAlive() && !item.getItem().isEmpty()
                            && EffectTargets.matchesAny(cost.items(), item.getItem()));
            candidates.sort(Comparator.comparingDouble((ItemEntity item) -> item.distanceToSqr(context.position()))
                    .thenComparing(ItemEntity::getUUID));
            available = new int[candidates.size()];
        }
        while (scanCursor < candidates.size()) {
            if (budget.exhausted()) return null;
            budget.charge(1);
            ItemEntity item = candidates.get(scanCursor);
            available[scanCursor++] = item.isAlive() && box.contains(item.position()) ? item.getItem().getCount() : 0;
        }
        if (budget.exhausted()) return null;
        if (allocation == null) allocation = new CatalystAllocation(cost, candidates, available, rounds);
        if (!allocation.advance(budget)) return null;
        if (allocation.groups() < rounds) return finish(EffectResult.skipped(CATALYST_ABSENT));
        if (plan == null) {
            plan = allocation.totalPayment(rounds, budget);
            if (plan == null) return null;
        }
        int total = cost.totalCount() * rounds;
        while (chargedPayments < plan.size()) {
            if (budget.exhausted()) return null;
            budget.charge(1);
            chargedPayments++;
        }
        // 支付准备逐项计费可以续接；真正扣除前重读整张清单，复核与扣除之间禁止让出。
        List<ItemEntity> resolved = new ArrayList<>(plan.size());
        for (CatalystReservations.Hold payment : plan) {
            ItemEntity item = context.level().getEntity(payment.itemId()) instanceof ItemEntity found ? found : null;
            if (item == null || item == context.source() || !item.isAlive() || !box.contains(item.position())
                    || !LoadedChunks.contains(context.level(), item.blockPosition())
                    || item.getItem().getItem() != payment.expectedItem()
                    || item.getItem().getCount() < payment.count()) {
                return finish(EffectResult.skipped(CATALYST_CHANGED));
            }
            resolved.add(item);
        }
        for (int index = 0; index < plan.size(); index++) {
            ItemStack stack = resolved.get(index).getItem();
            stack.shrink(plan.get(index).count());
        }
        // 扣减完成后先固定终态，再通知实体移除，避免移除回调重入导致重复扣款。
        EffectResult paid = finish(EffectResult.applied(total));
        for (ItemEntity item : resolved) if (item.getItem().isEmpty()) item.discard();
        return paid;
    }

    private EffectResult finish(EffectResult terminal) {
        result = terminal;
        allocation = null;
        candidates = null;
        available = null;
        plan = null;
        return result;
    }
}
