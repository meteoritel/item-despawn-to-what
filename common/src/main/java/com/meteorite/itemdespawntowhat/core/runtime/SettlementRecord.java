package com.meteorite.itemdespawntowhat.core.runtime;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * 一次转化的结算记录：计划量、已开始的组、真实完成量与返还交付进度。
 * 账目口径：N = C + 已交付返还 + 尚待交付返还，C = 每组源成本 × 已开始组数；
 * appliedUnits 只由执行器回执累加，pendingUnits 是已派发未回执的部分，计划量不冒充成功量。
 * 记录可持久化：无法加入世界的返还留在 pendingDelivery 里，交给恢复任务（SettlementRecovery）逐步交付。
 * 催化剂按单独账目计数（catalystCostPerGroup / catalystGroupsPaid / catalystsConsumed），不进源物品守恒式。
 * 账目字段不可从外部直接变更：全部变更方法私有，只能经受控写入句柄 {@link Writer} 调用，
 * 句柄保证「改字段 + 维护账本双索引 + 标脏」在同一次调用内完成，业务判断（是否对齐待交付量、
 * 记中断还是失败、截断到几组）仍留在各调用方。
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
    // 记录创建时的源位置：重启后源实体可能已不存在，返还交付只能以这个位置为搜索起点；缺该字段的记录退回世界出生点
    private final double positionX;
    private final double positionY;
    private final double positionZ;
    private final boolean hasPosition;
    private String candidateId = "";
    private int groupCount;
    private int groupsStarted;
    private int consumedSources;
    // 待返还量：结算任务持有库存期间由任务出口对齐到实际结算库存；
    // 中止/交付完成后所有权移交恢复任务，改由其按交付进度递减
    private int pendingDelivery;
    private int deliveredReturns;
    // 经验总量可能超过 int；完成量与待完成量在内存和存档中均保留 long 精度。
    private long appliedUnits;
    private long pendingUnits;
    private int skippedEffects;
    private int failedEffects;
    // 催化剂固定成本：每组消耗件数、已支付组数、已真实扣减的催化剂总件数；
    // 未声明 catalyst_cost 的规则三项恒为 0，且不写盘（存档字节与未声明时一致）
    private int catalystCostPerGroup;
    private int catalystGroupsPaid;
    private int catalystsConsumed;
    private Status status = Status.PLANNED;
    private String interruptReason = "";
    private long startedTick = -1L;
    private long finishedTick = -1L;

    // 无位置的重载：供缺位置字段的存档与外部构造使用，恢复交付时退回世界出生点
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
    private void plan(String candidateId, int groupCount) {
        this.candidateId = candidateId == null ? "" : candidateId;
        this.groupCount = Math.max(0, groupCount);
    }

    // 开始执行：第一次进入计划步骤时调用，可重入
    private void markStarted(long gameTime) {
        if (status != Status.PLANNED) {
            return;
        }
        status = Status.STARTED;
        startedTick = gameTime;
    }

    // 一组开始：该组的固定源成本已从结算库存扣减（只对已开始的组收费）
    private void groupStarted(int cost) {
        groupsStarted++;
        consumedSources += Math.max(0, cost);
    }

    // 计划期登记每组的催化剂成本（0 表示该规则未声明 catalyst_cost）
    private void catalystPlan(int costPerGroup) {
        this.catalystCostPerGroup = Math.max(0, costPerGroup);
    }

    // 一组催化剂支付完成：只有真实扣减后才累加（催化剂单独计数，不进源物品守恒式）
    private void catalystGroupPaid(int count) {
        if (count <= 0) {
            return;
        }
        catalystGroupsPaid++;
        catalystsConsumed += count;
    }

    // 真实完成量回执（APPLIED / DEFERRED 的已成部分）
    private void addApplied(long units) {
        appliedUnits = Math.addExact(appliedUnits, Math.max(0L, units));
    }

    // 已派发未回执的计划量
    private void addPending(long units) {
        pendingUnits = Math.addExact(pendingUnits, Math.max(0L, units));
    }

    // 异步批次回执：把「已受理」收敛为「已完成」，不重复计费
    private void addProgress(int units) {
        int done = Math.max(0, units);
        appliedUnits = Math.addExact(appliedUnits, done);
        pendingUnits = Math.max(0, pendingUnits - done);
    }

    private void effectSkipped() {
        skippedEffects++;
    }

    private void effectFailed() {
        failedEffects++;
    }

    // 返还交付：交付出去后才从待交付里减去
    private void delivered(int count) {
        // 上界先归零：pendingDelivery 正常恒非负，但存档读回的字段可能为负，夹取不得因此抛 IllegalArgumentException
        int moved = Math.clamp(count, 0, Math.max(0, pendingDelivery));
        pendingDelivery -= moved;
        deliveredReturns += moved;
    }

    // 用结算库存的实际数量对齐待交付数：由持有库存的一方（结算任务的统一出口）在落账前调用，
    // 保证内存账目与落盘值一致；所有权移交后不再调用，避免覆盖恢复任务推进的交付进度
    private void syncPendingDelivery(int remaining) {
        pendingDelivery = Math.max(0, remaining);
    }

    // 全部返还交付完毕
    private void complete(long gameTime) {
        status = Status.COMPLETED;
        pendingDelivery = 0;
        finishedTick = gameTime;
    }

    // 取消（规则重载 / 维度卸载 / 停服）：只记结算，不在此刻做世界写入
    private void interrupt(String reason, long gameTime) {
        if (status == Status.COMPLETED) {
            return;
        }
        status = Status.INTERRUPTED;
        interruptReason = reason == null ? "" : reason;
        finishedTick = gameTime;
    }

    // 结算失败（账目结构与库存不一致等），保留待返还数据
    private void fail(String reason, long gameTime) {
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
    long appliedUnits() { return appliedUnits; }
    long pendingUnits() { return pendingUnits; }
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
        tag.putLong("applied_units", appliedUnits);
        tag.putLong("pending_units", pendingUnits);
        tag.putInt("skipped_effects", skippedEffects);
        tag.putInt("failed_effects", failedEffects);
        tag.putString("status", status.name());
        tag.putString("interrupt_reason", interruptReason);
        tag.putLong("started_tick", startedTick);
        tag.putLong("finished_tick", finishedTick);
        // 位置字段：缺 has_position 的记录恢复交付时退回世界出生点，故只在有位置时写入坐标
        tag.putBoolean("has_position", hasPosition);
        if (hasPosition) {
            tag.putDouble("pos_x", positionX);
            tag.putDouble("pos_y", positionY);
            tag.putDouble("pos_z", positionZ);
        }
        // 催化剂字段只在 >0 时写盘：未声明 catalyst_cost 的记录不产生这些字段，存档字节保持一致
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
        record.appliedUnits = tag.getLong("applied_units");
        record.pendingUnits = tag.getLong("pending_units");
        record.skippedEffects = tag.getInt("skipped_effects");
        record.failedEffects = tag.getInt("failed_effects");
        // 缺失取 0：没有该字段的记录（未声明 catalyst_cost）不产生催化剂账目
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

    /**
     * 结算记录的受控写入句柄：账目字段的唯一变更入口。
     * <p>
     * 记录与账本两张索引持有同一可变引用，字段变更若不配套标脏就会丢失（{@code SavedData} 只在仍为脏时落盘）。
     * 因此 {@link SettlementRecord} 的变更方法全部私有，调用方只能经本句柄写入，且每个域变更方法都在同一次
     * 调用内完成「改字段 + 写回双索引 + 标脏」，外部不存在只改字段不落账的漏提交路径：新建用 {@link #register}，
     * 撤销登记用 {@link #discard}，其余按语义入口调用。是否对齐待交付量、记中断还是失败、截断到几组等业务判断
     * 仍留在各调用方；本句柄不建立通用事务、不自动刷盘、不自动异常回滚，提交只标脏。
     */
    static final class Writer {

        // 新建一条记录并登记：入两张索引并标脏；返回的句柄是该记录唯一变更入口
        static Writer register(SettlementLedger ledger, String id, String ruleId, String dimension,
                               ItemStack source, int initialCount, int sourceCostPerGroup,
                               boolean hasPosition, double positionX, double positionY, double positionZ) {
            Writer writer = new Writer(ledger, new SettlementRecord(id, ruleId, dimension, source,
                    initialCount, sourceCostPerGroup, hasPosition, positionX, positionY, positionZ));
            writer.commit();
            return writer;
        }

        // 包装账本中已有的记录（恢复扫描按 id 取出记录后据此写入）；不改变索引
        static Writer of(SettlementLedger ledger, SettlementRecord record) {
            return new Writer(ledger, record);
        }

        private final SettlementLedger ledger;
        private final SettlementRecord record;

        private Writer(SettlementLedger ledger, SettlementRecord record) {
            this.ledger = ledger;
            this.record = record;
        }

        // 记录本体：账目字段读取的唯一来源，不为此复制第二份账目
        SettlementRecord record() {
            return record;
        }

        // 落账：把记录当前字段写回账本双索引并标脏；只由本句柄内的变更方法调用，不对调用方暴露，
        // 避免出现「改了字段却忘记提交」的路径
        private void commit() {
            ledger.submit(record);
        }

        // 登记失败补偿：把记录从两张索引成对摘除，避免重启后的恢复任务重复交付已写回的库存
        void discard() {
            ledger.unregister(record.id());
        }

        // 每个入口都在同一次调用内改字段并落账（写回双索引 + 标脏），调用方无需再自行提交
        void plan(String candidateId, int groupCount) {
            record.plan(candidateId, groupCount);
            commit();
        }

        void markStarted(long gameTime) {
            record.markStarted(gameTime);
            commit();
        }

        // 开始一组并落账：调用方已从结算库存扣掉该组固定成本
        void groupStarted(int cost) {
            record.groupStarted(cost);
            commit();
        }

        void catalystPlan(int costPerGroup) {
            record.catalystPlan(costPerGroup);
            commit();
        }

        void catalystGroupPaid(int count) {
            record.catalystGroupPaid(count);
            commit();
        }

        void addApplied(long units) {
            record.addApplied(units);
            commit();
        }

        void addPending(long units) {
            record.addPending(units);
            commit();
        }

        // 进度回执入口：执行器只拿到这条写入通道，本方法自己落账；
        // 只改 applied/pending units，绝不触碰待交付量，因此不覆盖库存所有权与恢复进度
        void addProgress(int units) {
            record.addProgress(units);
            commit();
        }

        void effectSkipped() {
            record.effectSkipped();
            commit();
        }

        void effectFailed() {
            record.effectFailed();
            commit();
        }

        // 返还交付：交付成功后递减待交付并落账，账目始终等于「还没交付出去的库存」
        void delivered(int count) {
            record.delivered(count);
            commit();
        }

        // 用结算库存实际数量对齐待交付量：只由仍持有库存所有权的一方调用，移交后不得再调用
        void syncPendingDelivery(int remaining) {
            record.syncPendingDelivery(remaining);
            commit();
        }

        void complete(long gameTime) {
            record.complete(gameTime);
            commit();
        }

        void interrupt(String reason, long gameTime) {
            record.interrupt(reason, gameTime);
            commit();
        }

        void fail(String reason, long gameTime) {
            record.fail(reason, gameTime);
            commit();
        }
    }
}
