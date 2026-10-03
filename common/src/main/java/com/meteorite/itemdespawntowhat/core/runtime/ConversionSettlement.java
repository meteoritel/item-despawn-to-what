package com.meteorite.itemdespawntowhat.core.runtime;

import com.google.gson.JsonElement;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.debug.DebugMode;
import com.meteorite.itemdespawntowhat.core.debug.DebugScenarioManager;
import com.meteorite.itemdespawntowhat.core.model.CatalystCost;
import com.meteorite.itemdespawntowhat.core.model.CombinationMode;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.OutcomeCandidate;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerScheduler;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.StepResult;
import com.meteorite.itemdespawntowhat.core.type.effect.SpawnEntityEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.SpawnItemEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.EffectTargets;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.ReturnItemSpawner;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.SpawnEntityExecutor;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.SpawnItemExecutor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * 一次转化的结算任务：固定成本、完整组与真实账目。
 * 三个阶段：
 * 1) 计划——按「最小完整容量」选定候选与组数 g（含一次性效果的候选最多一组）；
 * 2) 派发——逐组从结算库存扣除固定源成本，并把候选内各效果派发一次（延迟交给调度器）；
 * 3) 交付——剩余库存作为返还掉落物分批交还世界，全部交付后才算完成。
 * 真实账目只来自执行器回执（EffectResult 与 reportProgress），计划量不冒充成功量；
 * 取消（规则重载/维度卸载/停服）只落一条待返还记录，不在不安全时刻做世界写入，交给阶段 6 恢复。
 */
final class ConversionSettlement implements ServerTask {

    private static final Logger LOGGER = LogManager.getLogger();
    // 候选容量判定中每次世界查询计入的预算单位
    private static final int CAPACITY_QUERY_UNITS = 2;

    // 结算阶段
    private enum Phase { PLANNING, DISPATCHING, DELIVERING, DONE }

    private final ConversionRuntime runtime;
    private final ServerScheduler scheduler;
    private final ServerLevel level;
    private final ItemEntity source;
    private final Rule rule;
    private final ItemStack held;
    // 触发时刻的整堆快照：计划期整堆已移出实体，效果读取 sourceStack() 时必须用这份快照
    private final ItemStack sourceSnapshot;
    private final SettlementRecord record;
    private final SettlementLedger ledger;
    private final RoundRobinCursors cursors;
    private final List<OutcomeCandidate> candidates;
    private final int structureVersion;
    private final boolean rotation;
    // 计划期催化剂候选（由近及远），供同 tick 无 yield 点的预留使用；未声明 catalyst_cost 时恒为空
    private final List<ItemEntity> catalystPicks = new ArrayList<>();

    private Phase phase = Phase.PLANNING;
    private OutcomeCandidate candidate;
    private EffectContext groupContext;
    private int groupCount;
    private int groupsStarted;
    private int effectCursor;
    private int dispatchedThisStep;
    private boolean groupOpen;
    private boolean cancelled;
    // 返还位置搜索游标：跨刻保留进度，交付成功后重置（阶段 5）
    private ReturnItemSpawner.PositionSearch rebateSearch;

    ConversionSettlement(ConversionRuntime runtime, ServerScheduler scheduler, ServerLevel level, ItemEntity source,
                         Rule rule, ItemStack held, SettlementRecord record, SettlementLedger ledger) {
        this.runtime = runtime;
        this.scheduler = scheduler;
        this.level = level;
        this.source = source;
        this.rule = rule;
        this.held = held;
        this.sourceSnapshot = held.copy();
        this.record = record;
        this.ledger = ledger;
        this.cursors = RoundRobinCursors.get(level);
        this.candidates = rule.effectiveOutcomes();
        this.structureVersion = structureVersion(candidates);
        this.rotation = rule.combination() == CombinationMode.ROUND_ROBIN && candidates.size() > 1;
    }

    @Override
    public ServerTaskKind kind() {
        return ServerTaskKind.EFFECT;
    }

    @Override
    public String tracingName() {
        return "settlement/" + rule.id();
    }

    @Override
    public StepResult step(ServerTickBudget budget) {
        if (cancelled) {
            return StepResult.done(1);
        }
        dispatchedThisStep = 0;
        try {
            return switch (phase) {
                case PLANNING -> plan(budget);
                case DISPATCHING -> dispatch(budget);
                case DELIVERING -> deliver(budget);
                case DONE -> StepResult.done(1);
            };
        } catch (RuntimeException failure) {
            // 异常路径与取消路径共用同一条中止顺序：先把待返还量写盘并释放未支付预留，
            // 再把异常抛给调度器转 FAILED，避免预留悬空
            abort("exception: " + failure.getClass().getSimpleName(), true);
            throw failure;
        }
    }

    @Override
    public void onCancelled(CancelReason reason) {
        if (!abort(reason.name(), false)) {
            return;
        }
        if (rule.combination() == CombinationMode.ROUND_ROBIN) {
            LOGGER.debug("结算被取消：规则={} 记录={} 原因={} 待返还={}", rule.id(), record.id(), reason, held.getCount());
        }
    }

    // 中止结算并把待返还量落盘：取消路径（onCancelled）与异常路径（step 的 catch）共用同一条顺序，
    // 不新造第二份账目口径。顺序：幂等位 → 待返还同步 → 状态记录 → 释放未支付预留 → 写盘 + 观测。
    // 本方法不做任何世界写入（维度卸载/停服时加入世界不安全，待返还数据留给阶段 6 恢复）；
    // 返回 false 表示此前已中止过（幂等，不重复写盘）。failed=true 时记 FAILED 语义，否则记 INTERRUPTED。
    private boolean abort(String reason, boolean failed) {
        if (cancelled) {
            return false;
        }
        cancelled = true;
        if (!record.completed()) {
            // 待返还量以任务手里的结算库存为准：重启后只交付这一部分，不重放已开始的组
            record.syncPendingDelivery(held.getCount());
            if (failed) {
                record.fail(reason, level.getGameTime());
            } else {
                record.interrupt(reason, level.getGameTime());
            }
        }
        // 释放未支付的催化剂预留（计划期登记、开组时转已支付）：无世界写入且幂等（D33）
        reservations().releaseOwner(record.id());
        try {
            ledger.put(record);
        } catch (RuntimeException failure) {
            LOGGER.warn("结算记录写盘失败：id={} reason={}", record.id(), reason, failure);
        }
        if (DebugMode.ENABLED) {
            DebugScenarioManager.observe(source, "SETTLEMENT_CANCELLED", "record", record.id(),
                    "reason", reason, "pending_delivery", record.pendingDelivery());
        }
        if (DebugMode.ENABLED && record.catalystCostPerGroup() > 0) {
            DebugScenarioManager.observe(source, "CATALYST_RELEASED", "record", record.id(), "reason", reason,
                    "released_groups", Math.max(0, record.groupCount() - record.groupsStarted()));
        }
        return true;
    }

    // 计划：选候选 → 定组数；预算耗尽时本刻不落决定，保持 PLANNING 下刻重试
    private StepResult plan(ServerTickBudget budget) {
        budget.charge(1);
        record.markStarted(level.getGameTime());
        if (candidates.isEmpty() || held.getCount() <= 0 || record.sourceCostPerGroup() > held.getCount()) {
            groupCount = 0;
            record.plan("", 0);
            phase = Phase.DELIVERING;
            return StepResult.yield(1, 1);
        }
        int size = candidates.size();
        int start = rotation ? cursors.cursor(cursorKey(), structureVersion, size) : 0;
        for (int offset = 0; offset < size; offset++) {
            int index = rotation ? (start + offset) % size : offset;
            OutcomeCandidate selected = candidates.get(index);
            int groups = groupsFor(selected, budget);
            if (groups < 0) {
                // 容量搜索未完成（预算耗尽）：本刻不落任何决定
                return StepResult.yield(1, 1);
            }
            if (groups <= 0) {
                continue;
            }
            // 催化剂固定成本上界（D31）：-1 = 预算耗尽本刻不落决定；0 = 可用件数不足则跳过该候选
            int catalystLimit = catalystGroupLimit(budget);
            if (catalystLimit < 0) {
                return StepResult.yield(1, 1);
            }
            groups = Math.min(groups, catalystLimit);
            if (groups <= 0) {
                continue;
            }
            CatalystCost catalyst = rule.catalystCost();
            if (catalyst != null
                    && reservations().tryReserve(record.id(), level, catalystPicks, groups * catalyst.count()) == null) {
                // 防御：扫描所得可用量与预留结果不一致时跳过候选，绝不半预留
                continue;
            }
            this.candidate = selected;
            this.groupCount = groups;
            // 组数定稿后先登记催化剂成本再落计划；预留已在候选条件（上方 tryReserve）里完成，两者之间没有 yield 点
            record.catalystPlan(catalyst == null ? 0 : catalyst.count());
            record.plan(selected.id(), groups);
            if (DebugMode.ENABLED && catalyst != null) {
                DebugScenarioManager.observe(source, "CATALYST_PLANNED", "record", record.id(), "rule", rule.id(),
                        "per_group", catalyst.count(), "groups", groups, "reserved", groups * catalyst.count());
            }
            if (rotation) {
                cursors.moveTo(cursorKey(), structureVersion, size, index + 1);
            }
            if (DebugMode.ENABLED) {
                DebugScenarioManager.observe(source, "OUTCOME_PLANNED", "rule", rule.id(), "candidate", selected.id(),
                        "groups", groups, "source_cost_per_group", record.sourceCostPerGroup(), "available", held.getCount(),
                        "structure_version", structureVersion, "cursor_start", start, "candidates", size);
            }
            phase = Phase.DISPATCHING;
            return StepResult.yield(1, 1);
        }
        // 全部候选容量不足：整堆返还，不支付任何成本（阶段 4 验收⑤）
        groupCount = 0;
        record.plan("", 0);
        if (DebugMode.ENABLED) {
            DebugScenarioManager.observe(source, "OUTCOME_SKIPPED", "rule", rule.id(), "candidates", size);
        }
        phase = Phase.DELIVERING;
        return StepResult.yield(1, 1);
    }

    // 组数 = min(源库存能支撑的组数, 候选最小完整容量, 一次性效果上限 1)；-1 表示预算不足需重试
    private int groupsFor(OutcomeCandidate selected, ServerTickBudget budget) {
        int costPerGroup = record.sourceCostPerGroup();
        int groups = costPerGroup > 0 ? held.getCount() / costPerGroup : 1;
        if (groups <= 0) {
            return 0;
        }
        int capacity = capacityGroups(selected, budget);
        if (capacity < 0) {
            return -1;
        }
        return Math.min(groups, capacity);
    }

    // 计划期催化剂上界：未声明 catalyst_cost 直接返回 Integer.MAX_VALUE（不查世界、不预留，纯零开销）；
    // -1 = 预算耗尽（本刻不落决定，下刻重试）；其余为「半径盒内可用件数 / 每组需求」的组数上界
    private int catalystGroupLimit(ServerTickBudget budget) {
        catalystPicks.clear();
        CatalystCost catalyst = rule.catalystCost();
        if (catalyst == null) {
            return Integer.MAX_VALUE;
        }
        if (budget.exhausted()) {
            return -1;
        }
        budget.charge(1);
        List<ItemEntity> ordered = collectCatalystItems(catalyst);
        int total = 0;
        for (ItemEntity item : ordered) {
            if (budget.exhausted()) {
                return -1;
            }
            budget.charge(1);
            total += reservations().available(level, item);
        }
        catalystPicks.addAll(ordered);
        return total / Math.max(1, catalyst.count());
    }

    // 触发位置 radius 盒内命中催化剂定义的掉落物，排除源实体；按到触发点距离由近及远排序，
    // 距离相同时按 UUID 兜底，保证与实体遍历顺序无关（D31）
    private List<ItemEntity> collectCatalystItems(CatalystCost catalyst) {
        AABB box = EffectTargets.blockBox(source.blockPosition(), catalyst.radius());
        List<ItemEntity> found = level.getEntitiesOfClass(ItemEntity.class, box,
                item -> item != source && !item.isRemoved() && EffectTargets.matchesAny(catalyst.items(), item.getItem()));
        found.sort(Comparator.comparingDouble((ItemEntity item) -> item.position().distanceToSqr(source.position()))
                .thenComparing((ItemEntity item) -> item.getUUID().toString()));
        return found;
    }

    // 预留表挂在运行期实例上（停服清空）；这里只取引用，不缓存成字段以免与生命周期错位
    private CatalystReservations reservations() {
        return runtime.catalystReservations();
    }

    // 候选内多个产出效果用「最小完整容量」共同限制组数（阶段 4 验收②）；
    // 只由 spawn_item / spawn_entity 的 limit 决定（其余效果不参与容量），
    // 任一效果为一次性类型时组数封顶 1（阶段 4 验收⑤）
    private int capacityGroups(OutcomeCandidate selected, ServerTickBudget budget) {
        int groups = Integer.MAX_VALUE;
        boolean oneShot = false;
        for (Effect effect : selected.effects()) {
            if (budget.exhausted()) {
                return -1;
            }
            budget.charge(1);
            EffectType<?> definition = runtime.effectDefinition(effect.type());
            if (definition == null) {
                // 未注册类型由执行阶段记录错误，这里不参与容量
                continue;
            }
            if (definition.oneShot()) {
                oneShot = true;
                continue;
            }
            if (effect instanceof SpawnItemEffect itemEffect && itemEffect.limit() != null) {
                budget.charge(CAPACITY_QUERY_UNITS);
                int per = Math.max(1, itemEffect.count());
                int room = Math.max(0, itemEffect.limit() - countNearbyItems(itemEffect, itemEffect.limit()));
                groups = Math.min(groups, room / per);
            } else if (effect instanceof SpawnEntityEffect entityEffect && entityEffect.limit() != null) {
                budget.charge(CAPACITY_QUERY_UNITS);
                int per = Math.max(1, entityEffect.count());
                int room = Math.max(0, entityEffect.limit() - countNearbyEntities(entityEffect, entityEffect.limit()));
                groups = Math.min(groups, room / per);
            }
        }
        if (oneShot) {
            groups = Math.min(groups, 1);
        }
        return groups;
    }

    // 派发：逐组支付成本并派发候选内效果；每刻派发量受 dispatch_batch_size 限制
    private StepResult dispatch(ServerTickBudget budget) {
        int batch = scheduler.config().dispatchBatchSize();
        while (groupsStarted < groupCount) {
            if (!groupOpen) {
                // 催化剂固定成本支付（D33）：先支付再开组；不足则截断组数并释放预留，绝不半支付
                int perGroup = record.catalystCostPerGroup();
                int paidCatalyst = 0;
                if (perGroup > 0) {
                    CatalystReservations.PayResult pay = reservations().payGroup(record.id(), level, perGroup, budget);
                    if (pay.outcome() == CatalystReservations.PayOutcome.RETRY) {
                        // 本刻预算耗尽：一件不扣、不开组，下刻从头重试
                        return StepResult.yield(1, 1);
                    }
                    if (pay.outcome() == CatalystReservations.PayOutcome.SHORT) {
                        // 可用量不足：不开该组（不扣源成本、不派发效果），组数截断为已开始组数，
                        // 释放未支付预留，剩余源库存走正常返还路径（绝不「少扣照常产出」）
                        int shortGroups = groupCount - groupsStarted;
                        groupCount = groupsStarted;
                        record.plan(record.candidateId(), groupCount);
                        reservations().releaseOwner(record.id());
                        ledger.put(record);
                        if (DebugMode.ENABLED) {
                            DebugScenarioManager.observe(source, "CATALYST_SHORT", "record", record.id(), "rule", rule.id(),
                                    "groups_started", groupsStarted, "group_count", groupCount,
                                    "short_groups", shortGroups, "short_catalyst", shortGroups * perGroup);
                        }
                        break;
                    }
                    paidCatalyst = pay.paid();
                }
                int cost = Math.min(record.sourceCostPerGroup(), held.getCount());
                if (cost > 0) {
                    // 已开始的组支付完整固定成本（ADR-0001），成本从结算库存里扣掉
                    held.setCount(held.getCount() - cost);
                }
                record.groupStarted(cost);
                // 固定顺序：催化剂已支付、源成本已扣减，才记账并写盘（D33）
                record.catalystGroupPaid(paidCatalyst);
                // 已开始组立即写盘：中途停服重启后，已支付成本的组不会因为未落盘而被当作「未开始库存」重复返还
                ledger.put(record);
                groupsStarted++;
                groupOpen = true;
                effectCursor = 0;
                groupContext = contextForGroup(groupsStarted);
            }
            if (budget.exhausted() || dispatchedThisStep >= batch) {
                return StepResult.yield(1, 1);
            }
            List<Effect> effects = candidate.effects();
            if (effectCursor >= effects.size()) {
                groupOpen = false;
                continue;
            }
            Effect effect = effects.get(effectCursor++);
            dispatchEffect(effect, groupContext);
            budget.charge(1);
            dispatchedThisStep++;
        }
        record.syncPendingDelivery(held.getCount());
        phase = Phase.DELIVERING;
        return StepResult.yield(1, 1);
    }

    // 交付：剩余库存作为返还掉落物分批加入世界；失败/未加载区块时保留待返还并重试
    private StepResult deliver(ServerTickBudget budget) {
        int batch = scheduler.config().dispatchBatchSize();
        int deliveredNow = 0;
        while (held.getCount() > 0 && deliveredNow < batch) {
            if (budget.exhausted()) {
                return StepResult.yield(1, 1);
            }
            int size = Math.min(held.getCount(), Math.max(1, held.getMaxStackSize()));
            ItemStack stack = held.copyWithCount(size);
            ReturnItemSpawner.Outcome outcome = deliverStack(stack, budget);
            if (outcome == ReturnItemSpawner.Outcome.CHUNK_UNLOADED) {
                record.syncPendingDelivery(held.getCount());
                ledger.put(record);
                return StepResult.retry(1, 20);
            }
            if (outcome == ReturnItemSpawner.Outcome.NEEDS_MORE_STEPS) {
                // 位置搜索本刻预算用尽：保留待返还、下刻从断点继续，不整批放弃
                record.syncPendingDelivery(held.getCount());
                ledger.put(record);
                return StepResult.yield(1, 1);
            }
            held.setCount(held.getCount() - size);
            record.delivered(size);
            budget.charge(2);
            deliveredNow++;
        }
        if (held.getCount() > 0) {
            record.syncPendingDelivery(held.getCount());
            ledger.put(record);
            return StepResult.yield(1, 1);
        }
        record.syncPendingDelivery(0);
        record.complete(level.getGameTime());
        ledger.put(record);
        // 防御：已完成记录的未支付预留必然为空（取消/不足路径已释放），这里幂等再释放一次
        reservations().releaseOwner(record.id());
        phase = Phase.DONE;
        if (DebugMode.ENABLED) {
            DebugScenarioManager.observe(source, "SETTLEMENT_COMPLETED", "record", record.id(), "rule", rule.id(),
                    "candidate", record.candidateId(), "groups", record.groupCount(),
                    "consumed_sources", record.consumedSources(), "applied_units", record.appliedUnits(),
                    "pending_units", record.pendingUnits(), "returned", record.deliveredReturns(),
                    "pending_delivery", record.pendingDelivery());
        }
        if (DebugMode.ENABLED) {
            DebugScenarioManager.observe(source, "REBATE_DELIVERED", "record", record.id(), "returned", record.deliveredReturns());
        }
        return StepResult.done(1);
    }

    // 返还物加入世界：位置搜索在公共预算内分步推进；区块未加载或生成被拒时保留待返还并重试
    private ReturnItemSpawner.Outcome deliverStack(ItemStack stack, ServerTickBudget budget) {
        if (rebateSearch == null) {
            // 位置搜索游标按触发时快照位置创建一次，跨刻保留进度；每成功交付一份后重置
            rebateSearch = new ReturnItemSpawner.PositionSearch(level, source);
        }
        return ReturnItemSpawner.deliver(stack, rebateSearch, budget,
                scheduler.config().positionSearchChecksPerTick());
    }

    // 本组上下文：组序号、组数、本组已付成本与真实回执入口
    private EffectContext contextForGroup(int groupIndex) {
        IntConsumer sink = record::addProgress;
        int covered = record.sourceCostPerGroup() > 0 ? record.sourceCostPerGroup() : 1;
        // 候选级位置策略（safe_spawn / fill_origin）随组上下文传给执行器
        return new RuntimeEffectContext(level, source, source.position(), rule.id(), scheduler, level.dimension(),
                sourceSnapshot, 1, covered, candidate.id(), groupIndex - 1, groupCount, record.sourceCostPerGroup(),
                sink, candidate.safeSpawn(), candidate.fillOrigin());
    }

    // 效果派发：延迟交给调度器，条件与概率在执行时统一判定
    private void dispatchEffect(Effect effect, EffectContext context) {
        int delay = Math.max(0, effect.delayTicks());
        if (DebugMode.ENABLED) {
            DebugScenarioManager.observe(source, "EFFECT_SCHEDULED", "effect", effect.type(),
                    "delay_ticks", delay, "chance", effect.chance(), "outcome", candidate.id());
        }
        context.schedule(delay, () -> account(runtime.runEffect(level, source, context, effect)));
    }

    // 执行器回执入账：已完成、已受理未完成、跳过与失败分别计数，计划量不冒充成功量
    private void account(EffectResult result) {
        if (result == null) {
            record.effectFailed();
            return;
        }
        switch (result.outcome()) {
            case APPLIED -> record.addApplied(result.appliedUnits());
            case DEFERRED -> {
                record.addApplied(result.appliedUnits());
                record.addPending(result.pendingUnits());
            }
            case SKIPPED -> record.effectSkipped();
            case FAILED -> record.effectFailed();
        }
        if (DebugMode.ENABLED) {
            DebugScenarioManager.observe(source, "EFFECT_SETTLED", "outcome", result.outcome(),
                    "applied", result.appliedUnits(), "pending", result.pendingUnits(), "detail", result.detail());
        }
    }

    // 轮询游标键：规则 id + 维度（跨实体共享、可持久化）
    private String cursorKey() {
        return rule.id() + "@" + level.dimension().location();
    }

    // 候选结构版本：把候选（id、安全生成/起点填充标记、效果顺序与全部参数）用规则编解码器编码成稳定 JSON 文本，
    // 再取 SHA-256 前 8 字节。硬约束：不得使用对象哈希——record 的 hashCode 会逐层展开组件，而枚举组件用的是
    // Object 身份哈希（值不受内容约束，随 JVM、哈希模式与分配状态变化），重启后可能被判为新结构而让游标归零（PLAN.md:276 验收③）。
    private int structureVersion(List<OutcomeCandidate> candidates) {
        Codec<List<OutcomeCandidate>> codec = OutcomeCandidate.codec(RuleCodecs.effectCodec(runtime.types().effectTypes())).listOf();
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        JsonElement json = codec.encodeStart(ops, candidates)
                .resultOrPartial(reason -> LOGGER.warn("候选结构摘要编码失败，轮询游标将重置：规则={} 原因={}", rule.id(), reason))
                .orElse(null);
        if (json == null) {
            return 0;
        }
        return digestVersion(json.toString());
    }

    // 内容摘要：同一文本必然同值、文本变化必然变值（SHA-256 前 8 字节碰撞概率可忽略），与任何对象身份无关
    private static int digestVersion(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            int version = 0;
            for (int i = 0; i < 8; i++) {
                version = (version << 8) | (digest[i] & 0xFF);
            }
            return version;
        } catch (NoSuchAlgorithmException impossible) {
            // 所有 JVM 都必须提供 SHA-256；真缺失时退回字符串内容哈希，仍与对象身份无关
            return text.hashCode();
        }
    }

    // 邻近物品总量：与 spawn_item 执行器同一判定（具体物品比注册表对象，标签比 TagKey）
    private int countNearbyItems(SpawnItemEffect effect, int limit) {
        int radius = effect.radius() == null ? SpawnItemExecutor.DEFAULT_SEARCH_RADIUS : effect.radius();
        AABB box = searchBox(radius);
        int[] total = {0};
        level.getEntities(EntityTypeTest.forClass(ItemEntity.class), box, nearby -> {
            if (nearby.isAlive() && matchesItem(effect.item(), nearby.getItem())) {
                total[0] = (int) Math.min(Integer.MAX_VALUE, (long) total[0] + nearby.getItem().getCount());
            }
            return total[0] >= limit;
        }, new ArrayList<>(1), 1);
        return total[0];
    }

    // 邻近实体总量：与 spawn_entity 执行器同一判定（支持具体类型与标签）
    private int countNearbyEntities(SpawnEntityEffect effect, int limit) {
        int radius = effect.radius() == null ? SpawnEntityExecutor.DEFAULT_SEARCH_RADIUS : effect.radius();
        AABB box = searchBox(radius);
        TaggedId reference = effect.entity();
        if (reference == null) {
            return 0;
        }
        if (!reference.tag()) {
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(reference.id())) {
                return 0;
            }
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(reference.id());
            List<Entity> nearby = new ArrayList<>();
            level.getEntities(type, box, Entity::isAlive, nearby, limit);
            return nearby.size();
        }
        List<Entity> nearby = level.getEntitiesOfClass(Entity.class, box,
                entity -> entity.isAlive() && matchesEntityType(reference, entity));
        return nearby.size();
    }

    // 搜索盒与执行器一致：以源位置所在方块为中心、边长 2*radius+1 格
    private AABB searchBox(int radius) {
        Vec3 position = source.position();
        return AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(BlockPos.containing(position.x, position.y, position.z)))
                .inflate(radius);
    }

    private static boolean matchesItem(TaggedId reference, ItemStack stack) {
        if (reference == null || stack.isEmpty()) {
            return false;
        }
        if (reference.tag()) {
            return stack.is(TagKey.create(Registries.ITEM, reference.id()));
        }
        return BuiltInRegistries.ITEM.containsKey(reference.id())
                && stack.is(BuiltInRegistries.ITEM.get(reference.id()));
    }

    private static boolean matchesEntityType(TaggedId reference, Entity entity) {
        if (reference == null) {
            return false;
        }
        if (reference.tag()) {
            return entity.getType().is(TagKey.create(Registries.ENTITY_TYPE, reference.id()));
        }
        return BuiltInRegistries.ENTITY_TYPE.containsKey(reference.id())
                && entity.getType() == BuiltInRegistries.ENTITY_TYPE.get(reference.id());
    }
}
