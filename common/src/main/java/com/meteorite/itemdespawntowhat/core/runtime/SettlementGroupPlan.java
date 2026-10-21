package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.model.CombinationMode;
import com.meteorite.itemdespawntowhat.core.model.OutcomeCandidate;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import net.minecraft.server.level.ServerLevel;

import java.util.Arrays;
import java.util.List;

/**
 * 候选与组计划：定组数（容量承诺）与逐组选候选。
 * 组数上界只有一个实现（{@link #bound}）：同一候选的「本地预留」与「本刻世界测量」取小，
 * 计划期的容量承诺与开组前的复核共用它，不允许两处各写一份 min 逻辑。
 * 候选剩余组数是只减不增的硬上界，因此已判定为 0 的候选不必再查世界。
 * 世界存量按刻可变：本刻测量结果只在同一刻内复用，跨刻重新查询。
 */
final class SettlementGroupPlan {

    // 候选剩余容量哨兵：该候选在本结算内尚未查询过世界
    static final int CAPACITY_UNKNOWN = -1;
    // 容量或选组搜索遇到预算耗尽：本刻不落任何决定，下刻重试（不写入容量缓存）
    static final int CAPACITY_PENDING = -2;
    // 选组搜索结束：所有候选都开不出一组
    static final int SELECT_EXHAUSTED = -3;

    private final SettlementCapacityProbe probe;
    private final List<OutcomeCandidate> candidates;
    private final RoundRobinCursors cursors;
    private final String cursorKey;
    private final int structureVersion;
    private final boolean rotation;
    // 各候选剩余可开始组数（下标与 candidates 对齐）：CAPACITY_UNKNOWN = 未查询；
    // Integer.MAX_VALUE = 无限，不递减
    private final int[] remainingCapacity;
    // 本刻已查询过的候选容量（CAPACITY_UNKNOWN = 本刻未查询）
    private final int[] capacityThisStep;
    private int capacityCommit;
    private int capacityCommitCursor;
    // 轮询扫描起点：-1 = 本结算尚未读取共享游标；确定一组并开组后推进到该候选的下一位
    private int rotationIndex = -1;

    SettlementGroupPlan(ServerLevel level, Rule rule, List<OutcomeCandidate> candidates, int structureVersion,
                        SettlementCapacityProbe probe) {
        this.probe = probe;
        this.candidates = candidates;
        this.cursors = RoundRobinCursors.get(level);
        this.cursorKey = rule.id() + "@" + level.dimension().location();
        this.structureVersion = structureVersion;
        this.rotation = rule.combination() == CombinationMode.ROUND_ROBIN && candidates.size() > 1;
        this.remainingCapacity = new int[candidates.size()];
        this.capacityThisStep = new int[candidates.size()];
        Arrays.fill(this.remainingCapacity, CAPACITY_UNKNOWN);
        Arrays.fill(this.capacityThisStep, CAPACITY_UNKNOWN);
    }

    // 每步出口清空刻内测量缓存：世界存量在刻间可变，跨刻复用旧的正数测量值会高估 room
    void resetStepMeasurements() {
        Arrays.fill(capacityThisStep, CAPACITY_UNKNOWN);
    }

    // 产物容量承诺：按候选顺序累计当前可容纳的产物组数，够 planned 即停。
    // 预算耗尽返回 CAPACITY_PENDING，游标与累计值跨刻保留，下刻从断点续算；
    // 已查询过的候选容量记入 remainingCapacity，派发期的复核直接复用这份上界。
    int commit(int planned, ServerTickBudget budget) {
        while (capacityCommitCursor < candidates.size() && capacityCommit < planned) {
            int index = capacityCommitCursor;
            int remaining = measure(index, budget);
            if (remaining == CAPACITY_PENDING) {
                return CAPACITY_PENDING;
            }
            capacityCommit = SettlementCapacityProbe.saturatedAdd(capacityCommit, bound(index, remaining));
            capacityCommitCursor++;
        }
        return Math.min(planned, capacityCommit);
    }

    // 为本组选候选：轮询从扫描起点依次找剩余容量 >= 1 的候选（首次取共享游标），优先模式从列表首项找；
    // 缓存记录本地预留，开组前仍复核世界存量。CAPACITY_PENDING = 预算耗尽需重试，
    // SELECT_EXHAUSTED = 所有候选都开不出一组
    int select(ServerTickBudget budget) {
        int size = candidates.size();
        int scan = rotationStart();
        for (int offset = 0; offset < size; offset++) {
            int index = Math.floorMod(scan + offset, size);
            // 本地预留只减不增，是候选剩余组数的硬上界：已判定为 0 时不必再查世界
            if (remainingCapacity[index] == 0) {
                continue;
            }
            int remaining = measure(index, budget);
            if (remaining == CAPACITY_PENDING) { return CAPACITY_PENDING; }
            if (bound(index, remaining) <= 0) {
                continue;
            }
            return index;
        }
        return SELECT_EXHAUSTED;
    }

    // 候选剩余可开组数：同一刻内同一候选只查一次世界，选组与开组前复核复用同一结果。
    // 派发期刻内唯一的世界写是催化剂支付（只减少物品数），复用刻内更早的测量值不会高估剩余容量；
    // 预算耗尽（CAPACITY_PENDING）不记入本刻缓存，下刻重新查询
    int measure(int index, ServerTickBudget budget) {
        int cached = capacityThisStep[index];
        if (cached != CAPACITY_UNKNOWN) {
            return cached;
        }
        int remaining = probe.groups(candidates.get(index), budget);
        if (remaining != CAPACITY_PENDING) {
            capacityThisStep[index] = remaining;
        }
        return remaining;
    }

    // 组数上界的唯一实现：本地预留与本次测量取小后写回。首次测量（UNKNOWN）直接采用测量值；
    // 计划期承诺与开组前复核都经由此处，组数截断口径因此只有一份。
    int bound(int index, int measured) {
        int cached = remainingCapacity[index];
        int bounded = cached == CAPACITY_UNKNOWN ? measured : Math.min(measured, cached);
        remainingCapacity[index] = bounded;
        return bounded;
    }

    // 候选当前剩余组数（只读）：未知返回 CAPACITY_UNKNOWN
    int remaining(int index) {
        return remainingCapacity[index];
    }

    // 本组开组后占用一个名额：无限候选（MAX_VALUE）不递减，其余只减不增
    void reserve(int index) {
        if (remainingCapacity[index] != Integer.MAX_VALUE) {
            remainingCapacity[index]--;
        }
    }

    // 门槛或流体让出：本结算不再选该候选（剩余容量置 0 是硬上界），由派发循环重扫其它候选
    void deny(int index) {
        remainingCapacity[index] = 0;
    }

    // 轮询扫描起点：未推进过时读取共享游标（跨结算接续），推进过则用本结算内的位置；优先模式恒为 0
    int rotationStart() {
        if (!rotation) {
            return 0;
        }
        return rotationIndex >= 0 ? rotationIndex : cursors.cursor(cursorKey, structureVersion, candidates.size());
    }

    // 本组候选定稿后推进轮询游标：确定该组并开组后才推进，被跳过的候选不推进
    void advanceRotation(int pickedIndex) {
        if (!rotation) {
            return;
        }
        rotationIndex = Math.floorMod(pickedIndex + 1, candidates.size());
        cursors.moveTo(cursorKey, structureVersion, candidates.size(), pickedIndex + 1);
    }
}
