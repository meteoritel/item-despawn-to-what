package com.meteorite.itemdespawntowhat.core.runtime;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * 一次转化的结算记录：计划量、已开始的组、真实完成量与返还交付进度。
 * 账目口径（PLAN §4.4 / ADR-0001）：N = C + 已交付返还 + 尚待交付返还，C = 每组源成本 × 已开始组数；
 * appliedUnits 只由执行器回执累加，pendingUnits 是已派发未回执的部分，计划量不冒充成功量。
 * 记录可持久化：无法加入世界的返还留在 pendingDelivery 里，交给阶段 6 恢复。
 * 催化剂按单独账目计数（catalystCostPerGroup / catalystGroupsPaid / catalystsConsumed），不进源物品守恒式（D31）。
 */
public final class SettlementRecord {

    // 结算状态：计划 → 已开始 → 完成；中断与失败保留待返还数据
    public enum Status { PLANNED, STARTED, COMPLETED, INTERRUPTED, FAILED }

    private final String id;
    private final String ruleId;
    private final String dimension;
    private final ItemStack source;
    private final int initialCount;
    private final int sourceCostPerGroup;
    // 记录创建时的源位置：重启后源实体可能已不存在，返还交付只能以这个位置为搜索起点（旧记录缺该字段）
    private final double positionX;
    private final double positionY;
    private final double positionZ;
    private final boolean hasPosition;
    private String candidateId = "";
    private int groupCount;
    private int groupsStarted;
    private int consumedSources;
    private int pendingDelivery;
    private int deliveredReturns;
    private int appliedUnits;
    private int pendingUnits;
    private int skippedEffects;
    private int failedEffects;
    // 催化剂固定成本（D31）：每组消耗件数、已支付组数、已真实扣减的催化剂总件数；
    // 未声明 catalyst_cost 的规则三项恒为 0，且不写盘（存档字节与阶段 6 完全一致）
    private int catalystCostPerGroup;
    private int catalystGroupsPaid;
    private int catalystsConsumed;
    private Status status = Status.PLANNED;
    private String interruptReason = "";
    private long startedTick = -1L;
    private long finishedTick = -1L;

    // 无位置的重载：旧存档与外部构造用，恢复交付时退回世界出生点
    SettlementRecord(String id, String ruleId, String dimension, ItemStack source, int initialCount, int sourceCostPerGroup) {
        this(id, ruleId, dimension, source, initialCount, sourceCostPerGroup, false, 0.0D, 0.0D, 0.0D);
    }

    SettlementRecord(String id, String ruleId, String dimension, ItemStack source, int initialCount, int sourceCostPerGroup,
                     boolean hasPosition, double positionX, double positionY, double positionZ) {
        this.id = id;
        this.ruleId = ruleId;
        this.dimension = dimension;
        this.source = source.copy();
        this.initialCount = Math.max(0, initialCount);
        this.sourceCostPerGroup = Math.max(0, sourceCostPerGroup);
        this.hasPosition = hasPosition;
        this.positionX = positionX;
        this.positionY = positionY;
        this.positionZ = positionZ;
        // 计划期整堆都在结算任务手里，未开始的组可以整份返还
        this.pendingDelivery = this.initialCount;
    }

    // 记录已选定候选与计划组数（组数为 0 表示全部返还）
    void plan(String candidateId, int groupCount) {
        this.candidateId = candidateId == null ? "" : candidateId;
        this.groupCount = Math.max(0, groupCount);
    }

    // 开始执行：第一次进入计划步骤时调用，可重入
    void markStarted(long gameTime) {
        if (status != Status.PLANNED) {
            return;
        }
        status = Status.STARTED;
        startedTick = gameTime;
    }

    // 一组开始：该组的固定源成本已从结算库存扣减（ADR-0001 只对已开始的组收费）
    void groupStarted(int cost) {
        groupsStarted++;
        consumedSources += Math.max(0, cost);
    }

    // 计划期登记每组的催化剂成本（0 表示该规则未声明 catalyst_cost）
    void catalystPlan(int costPerGroup) {
        this.catalystCostPerGroup = Math.max(0, costPerGroup);
    }

    // 一组催化剂支付完成：只有真实扣减后才累加（催化剂单独计数，不进源物品守恒式）
    void catalystGroupPaid(int count) {
        if (count <= 0) {
            return;
        }
        catalystGroupsPaid++;
        catalystsConsumed += count;
    }

    // 真实完成量回执（APPLIED / DEFERRED 的已成部分）
    void addApplied(int units) {
        appliedUnits += Math.max(0, units);
    }

    // 已派发未回执的计划量
    void addPending(int units) {
        pendingUnits += Math.max(0, units);
    }

    // 异步批次回执：把「已受理」收敛为「已完成」，不重复计费
    void addProgress(int units) {
        int done = Math.max(0, units);
        appliedUnits += done;
        pendingUnits = Math.max(0, pendingUnits - done);
    }

    void effectSkipped() {
        skippedEffects++;
    }

    void effectFailed() {
        failedEffects++;
    }

    // 返还交付：交付出去后才从待交付里减去
    void delivered(int count) {
        int moved = Math.max(0, Math.min(count, pendingDelivery));
        pendingDelivery -= moved;
        deliveredReturns += moved;
    }

    // 用结算库存的实际数量对齐待交付数（成本扣减/交付/中断后调用）
    void syncPendingDelivery(int remaining) {
        pendingDelivery = Math.max(0, remaining);
    }

    // 全部返还交付完毕
    void complete(long gameTime) {
        status = Status.COMPLETED;
        pendingDelivery = 0;
        finishedTick = gameTime;
    }

    // 取消（规则重载 / 维度卸载 / 停服）：只记结算，不在此刻做世界写入
    void interrupt(String reason, long gameTime) {
        if (status == Status.COMPLETED) {
            return;
        }
        status = Status.INTERRUPTED;
        interruptReason = reason == null ? "" : reason;
        finishedTick = gameTime;
    }

    // 结算失败（账目结构与库存不一致等），保留待返还数据
    void fail(String reason, long gameTime) {
        status = Status.FAILED;
        interruptReason = reason == null ? "" : reason;
        finishedTick = gameTime;
    }

    String id() { return id; }
    String ruleId() { return ruleId; }
    String dimension() { return dimension; }
    String candidateId() { return candidateId; }
    ItemStack source() { return source; }
    int initialCount() { return initialCount; }
    int sourceCostPerGroup() { return sourceCostPerGroup; }
    int catalystCostPerGroup() { return catalystCostPerGroup; }
    int catalystGroupsPaid() { return catalystGroupsPaid; }
    int catalystsConsumed() { return catalystsConsumed; }
    boolean hasPosition() { return hasPosition; }
    double positionX() { return positionX; }
    double positionY() { return positionY; }
    double positionZ() { return positionZ; }
    int groupCount() { return groupCount; }
    int groupsStarted() { return groupsStarted; }
    int consumedSources() { return consumedSources; }
    int pendingDelivery() { return pendingDelivery; }
    boolean completed() { return status == Status.COMPLETED; }
    // 恢复扫描的唯一判据：只看待返还库存，绝不把「已派发未回执」的 pendingUnits 当成待返还库存
    boolean needsRecovery() { return status != Status.COMPLETED && pendingDelivery > 0 && !source.isEmpty(); }
    int deliveredReturns() { return deliveredReturns; }
    int appliedUnits() { return appliedUnits; }
    int pendingUnits() { return pendingUnits; }
    int skippedEffects() { return skippedEffects; }
    int failedEffects() { return failedEffects; }
    Status status() { return status; }
    String interruptReason() { return interruptReason; }
    long startedTick() { return startedTick; }
    long finishedTick() { return finishedTick; }

    // 写盘：账目字段全部落盘，源物品快照用于返还模板
    Tag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("rule", ruleId);
        tag.putString("dimension", dimension);
        tag.putString("candidate", candidateId);
        tag.put("source_stack", source.save(registries));
        tag.putInt("initial_count", initialCount);
        tag.putInt("source_cost", sourceCostPerGroup);
        tag.putInt("group_count", groupCount);
        tag.putInt("groups_started", groupsStarted);
        tag.putInt("consumed_sources", consumedSources);
        tag.putInt("pending_delivery", pendingDelivery);
        tag.putInt("delivered_returns", deliveredReturns);
        tag.putInt("applied_units", appliedUnits);
        tag.putInt("pending_units", pendingUnits);
        tag.putInt("skipped_effects", skippedEffects);
        tag.putInt("failed_effects", failedEffects);
        tag.putString("status", status.name());
        tag.putString("interrupt_reason", interruptReason);
        tag.putLong("started_tick", startedTick);
        tag.putLong("finished_tick", finishedTick);
        // 位置字段：旧存档缺 has_position，恢复交付退回世界出生点
        tag.putBoolean("has_position", hasPosition);
        if (hasPosition) {
            tag.putDouble("pos_x", positionX);
            tag.putDouble("pos_y", positionY);
            tag.putDouble("pos_z", positionZ);
        }
        // 催化剂字段只在 >0 时写盘：未声明 catalyst_cost 的存档字节与阶段 6 完全一致
        if (catalystCostPerGroup > 0) {
            tag.putInt("catalyst_cost", catalystCostPerGroup);
        }
        if (catalystGroupsPaid > 0) {
            tag.putInt("catalyst_groups_paid", catalystGroupsPaid);
        }
        if (catalystsConsumed > 0) {
            tag.putInt("catalysts_consumed", catalystsConsumed);
        }
        return tag;
    }

    // 读盘：字段缺失按默认值补齐，单条记录损坏由调用方跳过
    static SettlementRecord load(CompoundTag tag, HolderLookup.Provider registries) {
        var sourceStack = tag.get("source_stack");
        ItemStack stack = sourceStack == null ? ItemStack.EMPTY
                : ItemStack.parse(registries, sourceStack).orElse(ItemStack.EMPTY);
        SettlementRecord record = new SettlementRecord(tag.getString("id"), tag.getString("rule"),
                tag.getString("dimension"), stack, tag.getInt("initial_count"), tag.getInt("source_cost"),
                tag.getBoolean("has_position"), tag.getDouble("pos_x"), tag.getDouble("pos_y"), tag.getDouble("pos_z"));
        record.candidateId = tag.getString("candidate");
        record.groupCount = tag.getInt("group_count");
        record.groupsStarted = tag.getInt("groups_started");
        record.consumedSources = tag.getInt("consumed_sources");
        record.pendingDelivery = tag.getInt("pending_delivery");
        record.deliveredReturns = tag.getInt("delivered_returns");
        record.appliedUnits = tag.getInt("applied_units");
        record.pendingUnits = tag.getInt("pending_units");
        record.skippedEffects = tag.getInt("skipped_effects");
        record.failedEffects = tag.getInt("failed_effects");
        // 缺失取 0：旧存档与未声明 catalyst_cost 的记录都不产生催化剂账目
        record.catalystCostPerGroup = tag.contains("catalyst_cost") ? tag.getInt("catalyst_cost") : 0;
        record.catalystGroupsPaid = tag.contains("catalyst_groups_paid") ? tag.getInt("catalyst_groups_paid") : 0;
        record.catalystsConsumed = tag.contains("catalysts_consumed") ? tag.getInt("catalysts_consumed") : 0;
        record.status = parseStatus(tag.getString("status"));
        record.interruptReason = tag.getString("interrupt_reason");
        record.startedTick = tag.contains("started_tick") ? tag.getLong("started_tick") : -1L;
        record.finishedTick = tag.contains("finished_tick") ? tag.getLong("finished_tick") : -1L;
        return record;
    }

    // 状态解析：未知取值退回「计划」，不因单条脏数据丢记录
    private static Status parseStatus(String raw) {
        for (Status candidate : Status.values()) {
            if (candidate.name().equals(raw)) {
                return candidate;
            }
        }
        return Status.PLANNED;
    }
}
