package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.model.CatalystCost;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.type.TaggedItemMatch;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.EffectTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 催化剂编排：计划期收集触发点半径内的催化剂、按共享库存分配算组数上界，并为每组开组准备门槛复核与流体复核。
 * 计划期上界与开组前门槛共用同一口径（现存件数减其它任务的预留），位置变化不影响归属；
 * 只有实体消失、类型变化或件数不足才算催化剂不足。
 * 未声明 catalyst_cost 的规则不查世界、不预留，纯零开销。
 */
final class SettlementCatalystStage {

    // 计划期上界：预算耗尽（本刻不落决定）
    static final int PENDING = -1;

    private final ConversionRuntime runtime;
    private final ServerLevel level;
    private final ItemEntity source;
    private final Vec3 triggerPosition;
    private final Rule rule;
    private final ItemStack held;
    // 每组源成本：决定源库存能支撑的组数上界（与记录的固定成本同源）
    private final int sourceCostPerGroup;
    private final String recordId;
    // 计划期催化剂候选（由近及远），供共享库存分配、预留与开组前门槛复核使用；未声明 catalyst_cost 时恒为空
    private final List<ItemEntity> picks = new ArrayList<>();
    private CatalystAllocation allocation;
    private CatalystAllocation thresholdAllocation;
    private int[] available;
    private int scanCursor;
    private boolean collected;
    private boolean thresholdChecked;

    SettlementCatalystStage(ConversionRuntime runtime, ServerLevel level, ItemEntity source, Vec3 triggerPosition,
                            Rule rule, ItemStack held, int sourceCostPerGroup, String recordId) {
        this.runtime = runtime;
        this.level = level;
        this.source = source;
        this.triggerPosition = triggerPosition;
        this.rule = rule;
        this.held = held;
        this.sourceCostPerGroup = sourceCostPerGroup;
        this.recordId = recordId;
    }

    // 规则的催化剂固定成本；未声明时返回 null
    @Nullable
    CatalystCost cost() {
        return rule.catalystCost();
    }

    // 计划期催化剂上界：未声明 catalyst_cost 直接返回 Integer.MAX_VALUE（不查世界、不预留，纯零开销）；
    // PENDING = 预算耗尽（本刻不落决定，下刻重试）；其余为各引用共享库存分配所得的完整组数上界
    int groupLimit(ServerTickBudget budget) {
        CatalystCost catalyst = rule.catalystCost();
        if (catalyst == null) {
            return Integer.MAX_VALUE;
        }
        if (budget.exhausted()) {
            return PENDING;
        }
        if (!collected) {
            budget.charge(1);
            picks.addAll(collect(catalyst));
            available = new int[picks.size()];
            collected = true;
        }
        while (scanCursor < picks.size()) {
            if (budget.exhausted()) {
                return PENDING;
            }
            budget.charge(1);
            // 计划期上界与开组前门槛共用同一口径：现存件数减去其它任务的预留
            available[scanCursor] = runtime.catalystReservations().availableExcept(level, picks.get(scanCursor), recordId);
            scanCursor++;
        }
        if (!thresholdChecked) {
            if (thresholdAllocation == null) {
                thresholdAllocation = new CatalystAllocation(catalyst.thresholdPaymentProfile(), picks, available, 1);
            }
            if (!thresholdAllocation.advance(budget)) {
                return PENDING;
            }
            thresholdChecked = true;
            if (thresholdAllocation.groups() < 1) {
                return 0;
            }
        }
        if (catalyst.totalConsumeCount() <= 0) {
            return Integer.MAX_VALUE;
        }
        if (allocation == null) {
            int sourceGroups = sourceCostPerGroup > 0 ? held.getCount() / sourceCostPerGroup : 1;
            allocation = new CatalystAllocation(catalyst, picks, available, sourceGroups);
        }
        return allocation.advance(budget) ? allocation.groups() : PENDING;
    }

    // 计划期登记的预留组：按 consume_counts 生成每组要扣的件数（组数已由 groupLimit 定稿）
    List<List<CatalystReservations.Hold>> paymentGroups(int groupCount) {
        return allocation.paymentGroups(groupCount);
    }

    // 开组前门槛复核：按计划期候选的 UUID 重解析，不重扫位置盒
    @Nullable
    CatalystThresholdCheck thresholdCheck() {
        CatalystCost catalyst = rule.catalystCost();
        if (catalyst == null) {
            return null;
        }
        return new CatalystThresholdCheck(level, triggerPosition, picks, catalyst,
                runtime.catalystReservations(), recordId);
    }

    // 开组前流体复核：搜索并保留来源方块，consume 时才真实扣流体
    @Nullable
    FluidSourceResolver fluidSource() {
        return rule.consumeFluid() ? new FluidSourceResolver(level, triggerPosition) : null;
    }

    // 触发位置 radius 盒内命中催化剂定义的掉落物，排除源实体；按到触发点距离由近及远排序，
    // 距离相同时按 UUID 兜底，保证与实体遍历顺序无关
    private List<ItemEntity> collect(CatalystCost catalyst) {
        AABB box = EffectTargets.blockBox(BlockPos.containing(triggerPosition), catalyst.radius());
        TaggedItemMatch target = TaggedItemMatch.compileAll(catalyst.items());
        List<ItemEntity> found = level.getEntitiesOfClass(ItemEntity.class, box,
                item -> item != source && !item.isRemoved() && target.matches(item.getItem()));
        found.sort(Comparator.comparingDouble((ItemEntity item) -> item.position().distanceToSqr(source.position()))
                .thenComparing((ItemEntity item) -> item.getUUID().toString()));
        return found;
    }
}
