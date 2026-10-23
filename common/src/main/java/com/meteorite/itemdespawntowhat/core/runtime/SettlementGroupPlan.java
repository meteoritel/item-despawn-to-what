package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.model.CombinationMode;
import com.meteorite.itemdespawntowhat.core.model.OutcomeCandidate;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import net.minecraft.server.level.ServerLevel;

import java.util.Arrays;
import java.util.List;

/**
 * 候选与组计划：按初始产出预算定组数，逐组选候选。
 * 计划期为各候选查询一次世界存量，按每组理论产出确定预算，后续只递减本地预留。
 * 组数上界只有一个实现（{@link #bound}）：初始预算与本地剩余取小。
 * 候选剩余组数是只减不增的硬上界，因此已判定为 0 的候选不必再查世界。
 * 外部新增产物与本结算的实际成功量都不改写初始预算。
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
    // 初始容量查询结果：在本次结算生命周期内固定，预算让出不清空。
    private final int[] initialCapacity;
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
        this.initialCapacity = new int[candidates.size()];
        Arrays.fill(this.remainingCapacity, CAPACITY_UNKNOWN);
        Arrays.fill(this.initialCapacity, CAPACITY_UNKNOWN);
    }

    // 初始产出预算：测完所有候选后才开始派发，交叉扣减表因此覆盖所有候选。
    // 不按 planned 提前结束，避免后续才测量的候选错过前面已支付组的理论扣减。
    // 预算耗尽返回 CAPACITY_PENDING，游标与累计值跨刻保留，下刻从断点续算；
    // 已查询过的候选容量记入 remainingCapacity，派发期只消耗这份预算。
    int commit(int planned, ServerTickBudget budget) {
        while (capacityCommitCursor < candidates.size()) {
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
    // 只读取初始预算与本地剩余，不在生成途中重查世界。CAPACITY_PENDING = 初始查询尚未完成，
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
            if (bound(index, Math.min(remaining, probe.remainingGroups(candidates.get(index)))) <= 0) {
                continue;
            }
            return index;
        }
        return SELECT_EXHAUSTED;
    }

    // 初始查询只完成一次；查询遇到预算不足时由 probe 保留进度，完成后整个结算复用。
    int measure(int index, ServerTickBudget budget) {
        int cached = initialCapacity[index];
        if (cached != CAPACITY_UNKNOWN) {
            return cached;
        }
        int remaining = probe.groups(candidates.get(index), budget);
        if (remaining != CAPACITY_PENDING) {
            initialCapacity[index] = remaining;
        }
        return remaining;
    }

    // 初始预算与本地剩余取小，概率落空、实际少产与外部移走产物都不增加可开始组数。
    int bound(int index, int measured) {
        int cached = remainingCapacity[index];
        int bounded = cached == CAPACITY_UNKNOWN ? measured : Math.min(measured, cached);
        remainingCapacity[index] = bounded;
        return bounded;
    }

    // 本组开组后占用一个名额：无限候选（MAX_VALUE）不递减，其余只减不增
    void reserve(int index) {
        probe.reserve(candidates.get(index));
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
