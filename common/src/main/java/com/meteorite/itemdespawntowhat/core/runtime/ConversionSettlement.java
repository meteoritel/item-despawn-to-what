package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.model.CatalystCost;
import com.meteorite.itemdespawntowhat.core.model.CombinationMode;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.OutcomeCandidate;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.api.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerScheduler;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepResult;
import com.meteorite.itemdespawntowhat.core.api.RuntimeObserver;
import com.meteorite.itemdespawntowhat.core.type.TaggedItemMatch;
import com.meteorite.itemdespawntowhat.core.type.effect.SpawnEntityEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.EntityProduct;
import com.meteorite.itemdespawntowhat.core.config.NearbyProductLimits;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.EffectTargets;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.ReturnItemSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * 一次转化的结算任务：固定成本、完整组与真实账目。
 * 四个阶段：
 * 1) 计划——按「源库存可支撑组数 × 催化剂上界 × 产物容量承诺」定出组数，并为第一组选定候选；
 * 2) 准备——为承诺的每组固定效果输入：每步最多 prepare_groups_per_step 组，按组续接，
 *    固定输入按「候选 + 槽位 + 组序号」幂等，已由成功转化当刻领先准备的组直接复用；
 * 3) 派发——每开始一组时独立选候选：轮询从共享游标依次搜索能完成一组的候选，
 *    确定该组并开组后才推进游标；容量不足的候选跳过，全部候选都开不出一组时停止并返还剩余库存；
 *    逐组从结算库存扣除固定源成本，并把候选内各效果派发一次（延迟交给调度器）；
 * 4) 交付——剩余库存作为返还掉落物分批交还世界，全部交付后才算完成。
 * 一次性效果（EffectType.oneShot）：不参与候选数量上限；仅含一次性效果的候选最多一组；
 * 同一源请求内每个一次性效果槽（候选 id + 槽序号）只尝试一次，概率落空或条件不满足也算已尝试，续跑不重试。
 * 真实账目只来自执行器回执（EffectResult 与 reportProgress），计划量不冒充成功量；
 * 取消（规则重载/维度卸载/停服）只落一条待返还记录，不在不安全时刻做世界写入，交给恢复任务（SettlementRecovery）交付。
 */
final class ConversionSettlement implements ServerTask {

    private static final Logger LOGGER = LogManager.getLogger();
    // 候选容量判定中每次世界查询计入的预算单位
    private static final int CAPACITY_QUERY_UNITS = 2;
    // 候选剩余容量哨兵：该候选在本结算内尚未查询过世界
    private static final int CAPACITY_UNKNOWN = -1;
    // 容量或选组搜索遇到预算耗尽：本刻不落任何决定，下刻重试（不写入容量缓存）
    private static final int CAPACITY_PENDING = -2;
    // 选组搜索结束：所有候选都开不出一组
    private static final int SELECT_EXHAUSTED = -3;

    // 结算阶段
    private enum Phase { PLANNING, PREPARING, DISPATCHING, DELIVERING, DONE }

    private final ConversionRuntime runtime;
    private final ServerScheduler scheduler;
    private final ServerLevel level;
    private final ItemEntity source;
    private final Rule rule;
    private final ItemStack held;
    // 触发时刻的整堆快照：计划期整堆已移出实体，效果读取 sourceStack() 时必须用这份快照
    private final ItemStack sourceSnapshot;
    // 成功转化当刻固定的触发点：源实体清空或被移除后仍以此定位触发点
    private final Vec3 triggerPosition;
    // 成功转化当刻固定的源速度：源实体被移除后产物初速仍由此决定
    private final Vec3 sourceVelocity;
    // 效果槽位固定输入（键 = 候选 id + 槽序号 + 组序号）：前若干组在成功转化当刻领先固定，
    // 其余组由准备阶段按组续接写入；源实体清空后仍按固定输入执行
    private final Map<ConversionRuntime.PreparedEffectKey, EffectPreparation> preparations;
    // 组间共享的固定输入（键的组序号恒为 0）：同一候选同一槽位的各组复用首份实例
    private final Map<ConversionRuntime.PreparedEffectKey, EffectPreparation> sharedPreparations = new HashMap<>();
    // 成功转化当刻已领先准备的组数：准备阶段从这里续接，不重做已固定的组
    private final int leadPreparedGroups;
    // 产物容量承诺进度：按候选累计当前可容纳组数，预算耗尽时跨刻续算
    private int capacityCommit;
    private int capacityCommitCursor;
    // 准备进度：已固定输入的组数与承诺组数
    private int prepareGroupIndex;
    private int preparedGroupCount;
    // 账目读取引用：字段变更方法在 SettlementRecord 内私有，本引用不可写；写入一律经 writer
    private final SettlementRecord record;
    // 受控写入句柄：字段变更与落账（双索引 + 标脏）的唯一入口
    private final SettlementRecord.Writer writer;
    private final RoundRobinCursors cursors;
    private final List<OutcomeCandidate> candidates;
    private final int structureVersion;
    private final boolean rotation;
    // 计划期催化剂候选（由近及远），供共享库存分配与预留使用；未声明 catalyst_cost 时恒为空
    private final List<ItemEntity> catalystPicks = new ArrayList<>();
    private CatalystAllocation catalystAllocation;
    private CatalystAllocation catalystThresholdAllocation;
    private int[] catalystAvailable;
    private int catalystScanCursor;
    private boolean catalystCollected;
    private boolean catalystThresholdChecked;
    private CatalystThresholdCheck groupThreshold;
    private FluidSourceResolver groupFluid;

    private Phase phase = Phase.PLANNING;
    private OutcomeCandidate candidate;
    // 已选定但尚未开组的候选：预算让出点跨刻保留选择，不重进 tick 重新选择
    private OutcomeCandidate pendingCandidate;
    private int pendingCandidateIndex = -1;
    // 轮询扫描起点：-1 = 本结算尚未读取共享游标；确定一组并开组后推进到该候选的下一位
    private int rotationIndex = -1;
    // 各候选剩余可开始组数（下标与 candidates 对齐）：CAPACITY_UNKNOWN = 未查询；
    // Integer.MAX_VALUE = 无限，不递减
    private final int[] remainingCapacity;
    // 本刻已查询过的候选容量（下标与 candidates 对齐，CAPACITY_UNKNOWN = 本刻未查询）：
    // 世界存量在刻间可能变化，同一刻内同一候选只查一次世界并复用结果
    private final int[] capacityThisStep;
    // 同一源请求内已尝试过的一次性效果槽（键 = 候选 id + "#" + 槽序号）
    private final Set<String> attemptedOneShots = new HashSet<>();
    private EffectContext groupContext;
    private int groupCount;
    private int groupsStarted;
    private int effectCursor;
    private int dispatchedThisStep;
    private boolean groupOpen;
    private boolean cancelled;
    // 待交付库存的所有权：true = 结算任务仍持有，任务出口负责把记录的待交付量对齐到手里的结算库存；
    // 中止或交付完成后移交（剩余库存改由恢复任务交付，本任务不再触碰待交付量）
    private boolean ownsStock = true;
    // 返还位置搜索游标：跨刻保留进度，交付成功后重置
    private ReturnItemSpawner.PositionSearch rebateSearch;

    // 构造结算任务：源库存 held 已在触发时刻整堆移出源实体，受控写入句柄由调用方（首次登记方）给出
    ConversionSettlement(ConversionRuntime runtime, ServerScheduler scheduler, ServerLevel level, ItemEntity source,
                         Rule rule, ItemStack held, SettlementRecord.Writer writer, Vec3 triggerPosition,
                         Vec3 sourceVelocity, Map<ConversionRuntime.PreparedEffectKey, EffectPreparation> preparations,
                         int leadPreparedGroups) {
        this.runtime = runtime;
        this.scheduler = scheduler;
        this.level = level;
        this.source = source;
        this.rule = rule;
        this.held = held;
        this.sourceSnapshot = held.copy();
        this.triggerPosition = triggerPosition == null ? source.position() : triggerPosition;
        this.sourceVelocity = sourceVelocity == null ? Vec3.ZERO : sourceVelocity;
        // 准备阶段要按组续接写入，因此复制成可变映射；领先准备的内容原样带入
        this.preparations = preparations == null ? new HashMap<>() : new HashMap<>(preparations);
        this.leadPreparedGroups = Math.max(0, leadPreparedGroups);
        this.writer = writer;
        this.record = writer.record();
        this.cursors = RoundRobinCursors.get(level);
        this.candidates = rule.effectiveOutcomes();
        // 候选结构摘要按规则实例缓存，结构未变时不重算；失效条件见 ConversionRuntime
        this.structureVersion = runtime.candidateStructureVersion(rule, level.registryAccess());
        this.rotation = rule.combination() == CombinationMode.ROUND_ROBIN && candidates.size() > 1;
        this.remainingCapacity = new int[candidates.size()];
        Arrays.fill(this.remainingCapacity, CAPACITY_UNKNOWN);
        this.capacityThisStep = new int[candidates.size()];
        Arrays.fill(this.capacityThisStep, CAPACITY_UNKNOWN);
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
        // 结算任务不经观察器的排期入口，调试注入的步骤失败只能在这里进入失败路径
        if (RuntimeObserver.current().enabled()) {
            RuntimeObserver.current().taskStepStarting(level.getServer(), kind(), tracingName());
        }
        dispatchedThisStep = 0;
        // 本刻容量查询结果只在同一刻内复用：世界存量在刻间可变，跨刻复用旧的正数测量值会高估 room
        Arrays.fill(capacityThisStep, CAPACITY_UNKNOWN);
        try {
            StepResult result = switch (phase) {
                case PLANNING -> plan(budget);
                case PREPARING -> prepare(budget);
                case DISPATCHING -> dispatch(budget);
                case DELIVERING -> deliver(budget);
                case DONE -> StepResult.done(1);
            };
            // 任务内统一状态出口：正常完成、显式中止、预算耗尽提前让出与交付让出都在这里按当前所有权对齐待交付量；
            // 账目字段本身已由受控写入入口逐次落账，此处不重复提交
            commit();
            return result;
        } catch (RuntimeException failure) {
            // 异常路径与取消路径共用同一条中止顺序：先把待返还量写盘并释放未支付预留，
            // 再把异常抛给调度器转 FAILED，避免预留悬空
            abort("exception: " + failure.getClass().getSimpleName(), true);
            throw failure;
        }
    }

    // 任务内统一出口：所有权仍在本任务时，把记录的待交付量对齐到手里的结算库存（受控入口会落账）；
    // 所有权已移交（中止/完成）时不再触碰待交付量，避免覆盖恢复任务推进的交付进度
    private void commit() {
        if (ownsStock) {
            writer.syncPendingDelivery(held.getCount());
        }
    }

    /** 取消回调：中止结算并落盘待返还；ROUND_ROBIN 规则额外记录一条调试日志 */
    @Override
    public void onCancelled(CancelReason reason) {
        if (!abort(reason.name(), false)) {
            return;
        }
        if (rule.combination() == CombinationMode.ROUND_ROBIN) {
            LOGGER.debug("结算被取消：规则={} 记录={} 原因={} 待返还={}", rule.id(), record.id(), reason, held.getCount());
        }
    }

    /**
     * 失败回调：结算步骤抛异常时按失败类型区分原因，落盘待返还量并在仍有库存时交给运行期恢复队列。
     * 恢复队列按记录去重并带退避重试，失败不会在调度器里留下悬挂任务。
     *
     * @param failure 失败原因；为 {@code null} 时按通用任务失败处理
     */
    @Override
    public void onFailed(@Nullable Throwable failure) {
        String reason = failure == null ? "TASK_FAILED" : "exception:" + failure.getClass().getSimpleName();
        abort(reason, true);
        if (record.needsRecovery()) {
            runtime.enqueueRecovery(level.getServer(), record.id(), reason);
        }
    }

    // 中止结算并把待返还量落盘：取消路径（onCancelled）与异常路径（step 的 catch、onFailed）共用同一条顺序，
    // 不新造第二份账目口径。顺序：幂等位 → 待返还同步 → 释放未支付预留 → 状态记录 → 观测。
    // 本方法不做任何世界写入（维度卸载/停服时加入世界不安全，待返还数据留给恢复任务 SettlementRecovery）；
    // 返回 false 表示此前已中止过（幂等，不重复写盘）。failed=true 时记 FAILED 语义，否则记 INTERRUPTED。
    // 批量取消时单个任务收尾失败不得让同批其它任务漏掉中止：每一步各自兜异常，失败只降级为日志。
    private boolean abort(String reason, boolean failed) {
        if (cancelled) {
            return false;
        }
        cancelled = true;
        // 中止即移交库存所有权：剩余库存改由恢复任务交付，本任务不再改写待返还量
        ownsStock = false;
        try {
            // 待返还量以任务手里的结算库存为准：重启后只交付这一部分，不重放已开始的组
            writer.syncPendingDelivery(held.getCount());
        } catch (RuntimeException failure) {
            LOGGER.warn("结算待返还量写盘失败：id={} reason={}", record.id(), reason, failure);
        }
        try {
            // 释放未支付的催化剂预留（计划期登记、开组时转已支付）：无世界写入且幂等
            reservations().releaseOwner(record.id());
        } catch (RuntimeException failure) {
            LOGGER.warn("结算催化剂预留释放失败：id={} reason={}", record.id(), reason, failure);
        }
        if (!record.completed()) {
            try {
                if (failed) {
                    writer.fail(reason, level.getGameTime());
                } else {
                    writer.interrupt(reason, level.getGameTime());
                }
            } catch (RuntimeException failure) {
                LOGGER.warn("结算终态写盘失败：id={} reason={}", record.id(), reason, failure);
            }
        }
        if (RuntimeObserver.current().enabled()) {
            RuntimeObserver.current().observe(source, "SETTLEMENT_CANCELLED", "record", record.id(),
                    "reason", reason, "pending_delivery", record.pendingDelivery());
        }
        if (RuntimeObserver.current().enabled() && record.catalystCostPerGroup() > 0) {
            RuntimeObserver.current().observe(source, "CATALYST_RELEASED", "record", record.id(), "reason", reason,
                    "released_groups", Math.max(0, record.groupCount() - record.groupsStarted()));
        }
        return true;
    }

    // 计划：定组数上界 → 为第一组选候选 → 预留催化剂；预算耗尽时本刻不落决定，保持 PLANNING 下刻重试
    private StepResult plan(ServerTickBudget budget) {
        budget.charge(1);
        writer.markStarted(level.getGameTime());
        if (candidates.isEmpty() || held.getCount() <= 0 || record.sourceCostPerGroup() > held.getCount()) {
            groupCount = 0;
            writer.plan("", 0);
            phase = Phase.DELIVERING;
            return StepResult.yield(1, 1);
        }
        int costPerGroup = record.sourceCostPerGroup();
        int sourceGroups = costPerGroup > 0 ? held.getCount() / costPerGroup : 1;
        // 催化剂固定成本上界：-1 = 预算耗尽本刻不落决定
        int catalystLimit = catalystGroupLimit(budget);
        if (catalystLimit < 0) {
            return StepResult.yield(1, 1);
        }
        int planned = Math.min(sourceGroups, catalystLimit);
        // 产物容量承诺：组数先按「当前区域还能容纳多少组产物」截断。准备成本随组数增长，
        // 先承诺再准备，才不会把整批固定输入准备好却因邻近阈值开不出组而整批作废。
        // 预算耗尽时本刻不落决定，已累计的进度跨刻保留。
        int committed = commitCapacity(planned, budget);
        if (committed == CAPACITY_PENDING) {
            return StepResult.yield(1, 1);
        }
        // 本结算第一组的候选：计划期只落「第一组」的选择，后续组在派发期逐组重选
        int pick = SELECT_EXHAUSTED;
        if (committed > 0) {
            pick = selectCandidateForGroup(budget);
        }
        if (pick == CAPACITY_PENDING) {
            // 容量搜索未完成（预算耗尽）：本刻不落任何决定
            return StepResult.yield(1, 1);
        }
        if (pick == SELECT_EXHAUSTED) {
            return abandonAll();
        }
        pendingCandidate = candidates.get(pick);
        pendingCandidateIndex = pick;
        groupCount = committed;
        CatalystCost catalyst = rule.catalystCost();
        if (catalyst != null && catalyst.totalConsumeCount() > 0
                && reservations().tryReserve(record.id(), level, catalystAllocation.paymentGroups(groupCount)) == null) {
            // 防御：扫描所得可用量与预留结果不一致时整批返还，绝不半预留
            return abandonAll();
        }
        // 组数定稿后先登记催化剂成本再落计划；预留已完成，两者之间没有 yield 点
        writer.catalystPlan(catalyst == null ? 0 : catalyst.totalConsumeCount());
        writer.plan(pendingCandidate.id(), groupCount);
        if (RuntimeObserver.current().enabled() && catalyst != null) {
            RuntimeObserver.current().observe(source, "CATALYST_PLANNED", "record", record.id(), "rule", rule.id(),
                    "per_group", catalyst.totalConsumeCount(), "groups", groupCount, "reserved", groupCount * catalyst.totalConsumeCount());
        }
        if (RuntimeObserver.current().enabled()) {
            // 每次结算只观测一条 OUTCOME_PLANNED（candidate = 本结算第一组所选候选，groups = 组数上界）；
            // 多组结算的逐组候选切换由每组的 EFFECT_SCHEDULED / EFFECT_ONESHOT_SKIPPED 观测体现
            RuntimeObserver.current().observe(source, "OUTCOME_PLANNED", "rule", rule.id(), "candidate", pendingCandidate.id(),
                    "groups", groupCount, "source_cost_per_group", record.sourceCostPerGroup(), "available", held.getCount(),
                    "structure_version", structureVersion, "cursor_start", rotationStart(), "candidates", candidates.size());
        }
        // 计划定稿后进入准备：领先准备已覆盖的组直接复用，剩余组按 prepare_groups_per_step 分组续接。
        // 领先准备已覆盖全部组时不插入 PREPARING 空转步，单组结算的步进节奏与改前一致
        prepareGroupIndex = Math.min(leadPreparedGroups, groupCount);
        preparedGroupCount = prepareGroupIndex;
        phase = prepareGroupIndex >= groupCount ? Phase.DISPATCHING : Phase.PREPARING;
        return StepResult.yield(1, 1);
    }

    // 产物容量承诺：按候选顺序累计当前可容纳的产物组数，够 planned 即停。
    // 预算耗尽返回 CAPACITY_PENDING，游标与累计值跨刻保留，下刻从断点续算；
    // 已查询过的候选容量记入 remainingCapacity，派发期的本地预留直接复用这份上界。
    private int commitCapacity(int planned, ServerTickBudget budget) {
        while (capacityCommitCursor < candidates.size() && capacityCommit < planned) {
            int index = capacityCommitCursor;
            int remaining = capacityGroupsAt(index, budget);
            if (remaining == CAPACITY_PENDING) {
                return CAPACITY_PENDING;
            }
            int cached = remainingCapacity[index];
            int local = cached == CAPACITY_UNKNOWN ? remaining : Math.min(remaining, cached);
            remainingCapacity[index] = local;
            capacityCommit = saturatedAdd(capacityCommit, local);
            capacityCommitCursor++;
        }
        return Math.min(planned, capacityCommit);
    }

    // 准备：为承诺的每组固定效果输入。每步最多 prepare_groups_per_step 组，组内各候选各槽位逐份固定；
    // 键按「候选 + 槽位 + 组序号」幂等，跨步续接不重复求值；真实开销由固定输入入口按工作单位上报
    private StepResult prepare(ServerTickBudget budget) {
        int perStep = scheduler.config().prepareGroupsPerStep();
        int preparedThisStep = 0;
        while (prepareGroupIndex < groupCount) {
            if (budget.exhausted() || preparedThisStep >= perStep) {
                return StepResult.yield(1, 1);
            }
            prepareGroup(prepareGroupIndex);
            prepareGroupIndex++;
            preparedThisStep++;
            // 逐组推进：中途取消/异常时已固定的组仍是有效输入，恢复路径按组数对齐
            preparedGroupCount = prepareGroupIndex;
        }
        phase = Phase.DISPATCHING;
        return StepResult.yield(1, 1);
    }

    // 固定一组的全部槽位输入：一次性效果只固定第 0 组一份；可共享的固定输入在组间复用首份实例。
    // 已存在的键直接跳过，因此成功转化当刻的领先准备与准备阶段不会重复求值
    private void prepareGroup(int groupIndex) {
        for (OutcomeCandidate candidate : candidates) {
            List<Effect> effects = candidate.effects();
            for (int slot = 0; slot < effects.size(); slot++) {
                Effect effect = effects.get(slot);
                EffectType<?> definition = runtime.effectDefinition(effect.type());
                if (definition == null) {
                    continue;
                }
                int targetGroup = definition.oneShot() ? 0 : groupIndex;
                ConversionRuntime.PreparedEffectKey key =
                        new ConversionRuntime.PreparedEffectKey(candidate.id(), slot, targetGroup);
                if (preparations.containsKey(key)) {
                    continue;
                }
                EffectPreparation preparation = runtime.prepareEffectSlot(definition, effect, level, source,
                        BlockPos.containing(triggerPosition), triggerPosition, sourceSnapshot, candidate.fillOrigin());
                if (preparation == null) {
                    // 未迁移的类型不在准备阶段固定输入，其条件与概率仍在首次执行时判定
                    continue;
                }
                if (preparation instanceof EffectPreparation.SharedPreparation) {
                    EffectPreparation shared = sharedPreparations.putIfAbsent(
                            new ConversionRuntime.PreparedEffectKey(candidate.id(), slot, 0), preparation);
                    if (shared != null) {
                        preparation = shared;
                    }
                }
                preparations.put(key, preparation);
            }
        }
    }

    // 为本组选候选：轮询从扫描起点依次找剩余容量 >= 1 的候选（首次取共享游标），优先模式从列表首项找；
    // 缓存记录本地预留，开组前仍复核世界存量。CAPACITY_PENDING = 预算耗尽需重试，
    // SELECT_EXHAUSTED = 所有候选都开不出一组
    private int selectCandidateForGroup(ServerTickBudget budget) {
        int size = candidates.size();
        int scan = rotationStart();
        for (int offset = 0; offset < size; offset++) {
            int index = Math.floorMod(scan + offset, size);
            int cached = remainingCapacity[index];
            // 本地预留只减不增，是候选剩余组数的硬上界：已判定为 0 时不必再查世界
            if (cached == 0) {
                continue;
            }
            int remaining = capacityGroupsAt(index, budget);
            if (remaining == CAPACITY_PENDING) { return CAPACITY_PENDING; }
            // 保留本结算对待生成组的本地预留，同时复核已经变化的世界存量。
            if (cached != CAPACITY_UNKNOWN) { remaining = Math.min(remaining, cached); }
            remainingCapacity[index] = remaining;
            if (remaining <= 0) {
                continue;
            }
            return index;
        }
        return SELECT_EXHAUSTED;
    }

    // 候选剩余可开组数：同一刻内同一候选只查一次世界，选组与开组前复核复用同一结果。
    // 派发期刻内唯一的世界写是催化剂支付（只减少物品数），复用刻内更早的测量值不会高估剩余容量；
    // 预算耗尽（CAPACITY_PENDING）不记入本刻缓存，下刻重新查询
    private int capacityGroupsAt(int index, ServerTickBudget budget) {
        int cached = capacityThisStep[index];
        if (cached != CAPACITY_UNKNOWN) {
            return cached;
        }
        int remaining = capacityGroups(candidates.get(index), budget);
        if (remaining != CAPACITY_PENDING) {
            capacityThisStep[index] = remaining;
        }
        return remaining;
    }

    // 轮询扫描起点：未推进过时读取共享游标（跨结算接续），推进过则用本结算内的位置；优先模式恒为 0
    private int rotationStart() {
        if (!rotation) {
            return 0;
        }
        return rotationIndex >= 0 ? rotationIndex : cursors.cursor(cursorKey(), structureVersion, candidates.size());
    }

    // 无法开出任何一组：整堆返还，不支付任何成本
    private StepResult abandonAll() {
        pendingCandidate = null;
        pendingCandidateIndex = -1;
        groupCount = 0;
        writer.plan("", 0);
        if (RuntimeObserver.current().enabled()) {
            RuntimeObserver.current().observe(source, "OUTCOME_SKIPPED", "rule", rule.id(), "candidates", candidates.size());
        }
        phase = Phase.DELIVERING;
        return StepResult.yield(1, 1);
    }

    // 计划期催化剂上界：未声明 catalyst_cost 直接返回 Integer.MAX_VALUE（不查世界、不预留，纯零开销）；
    // -1 = 预算耗尽（本刻不落决定，下刻重试）；其余为各引用共享库存分配所得的完整组数上界
    private int catalystGroupLimit(ServerTickBudget budget) {
        CatalystCost catalyst = rule.catalystCost();
        if (catalyst == null) {
            return Integer.MAX_VALUE;
        }
        if (budget.exhausted()) {
            return -1;
        }
        if (!catalystCollected) {
            budget.charge(1);
            catalystPicks.addAll(collectCatalystItems(catalyst));
            catalystAvailable = new int[catalystPicks.size()];
            catalystCollected = true;
        }
        while (catalystScanCursor < catalystPicks.size()) {
            if (budget.exhausted()) {
                return -1;
            }
            budget.charge(1);
            catalystAvailable[catalystScanCursor] = reservations().available(level, catalystPicks.get(catalystScanCursor));
            catalystScanCursor++;
        }
        if (!catalystThresholdChecked) {
            if (catalystThresholdAllocation == null) {
                catalystThresholdAllocation = new CatalystAllocation(catalyst.thresholdPaymentProfile(),
                        catalystPicks, catalystAvailable, 1);
            }
            if (!catalystThresholdAllocation.advance(budget)) {
                return -1;
            }
            catalystThresholdChecked = true;
            if (catalystThresholdAllocation.groups() < 1) {
                return 0;
            }
        }
        if (catalyst.totalConsumeCount() <= 0) {
            return Integer.MAX_VALUE;
        }
        if (catalystAllocation == null) {
            int sourceGroups = record.sourceCostPerGroup() > 0 ? held.getCount() / record.sourceCostPerGroup() : 1;
            catalystAllocation = new CatalystAllocation(catalyst, catalystPicks, catalystAvailable, sourceGroups);
        }
        return catalystAllocation.advance(budget) ? catalystAllocation.groups() : -1;
    }

    // 触发位置 radius 盒内命中催化剂定义的掉落物，排除源实体；按到触发点距离由近及远排序，
    // 距离相同时按 UUID 兜底，保证与实体遍历顺序无关
    private List<ItemEntity> collectCatalystItems(CatalystCost catalyst) {
        AABB box = EffectTargets.blockBox(BlockPos.containing(triggerPosition), catalyst.radius());
        TaggedItemMatch target = TaggedItemMatch.compileAll(catalyst.items());
        List<ItemEntity> found = level.getEntitiesOfClass(ItemEntity.class, box,
                item -> item != source && !item.isRemoved() && target.matches(item.getItem()));
        found.sort(Comparator.comparingDouble((ItemEntity item) -> item.position().distanceToSqr(source.position()))
                .thenComparing((ItemEntity item) -> item.getUUID().toString()));
        return found;
    }

    // 预留表挂在运行期实例上（停服清空）；这里只取引用，不缓存成字段以免与生命周期错位
    private CatalystReservations reservations() {
        return runtime.catalystReservations();
    }

    // 聚合同一物品或通用实体的组内需求；经验不参与邻近产物准入，避免球数导致源物品返还。
    // 仅含一次性效果的候选不参与容量计算，固定按 1 组处理。
    private int capacityGroups(OutcomeCandidate selected, ServerTickBudget budget) {
        boolean hasOneShot = false;
        boolean hasRepeatable = false;
        Map<TaggedId, Integer> items = new LinkedHashMap<>();
        Map<TaggedId, Integer> entities = new LinkedHashMap<>();
        for (Effect effect : selected.effects()) {
            EffectType<?> definition = runtime.effectDefinition(effect.type());
            if (definition == null) { continue; }
            if (definition.oneShot()) { hasOneShot = true; continue; }
            hasRepeatable = true;
            if (effect instanceof SpawnEntityEffect spawn) {
                switch (spawn.product()) {
                    case EntityProduct.Item item -> items.merge(item.item(), item.count(), ConversionSettlement::saturatedAdd);
                    case EntityProduct.Generic entity -> entities.merge(entity.entity(), entity.count(), ConversionSettlement::saturatedAdd);
                    case EntityProduct.Experience ignored -> { /* 经验仅受执行批次预算约束。 */ }
                }
            }
        }
        int groups = Integer.MAX_VALUE;
        NearbyProductLimits limits = runtime.nearbyProducts();
        if (limits.itemLimit() > 0) {
            for (var demand : items.entrySet()) {
                if (budget.exhausted()) { return CAPACITY_PENDING; }
                budget.charge(CAPACITY_QUERY_UNITS);
                int room = Math.max(0, limits.itemLimit() - countNearbyItems(demand.getKey(), limits.itemLimit(), limits.itemRadius()));
                groups = Math.min(groups, room / demand.getValue());
            }
        }
        if (limits.entityLimit() > 0) {
            for (var demand : entities.entrySet()) {
                if (budget.exhausted()) { return CAPACITY_PENDING; }
                budget.charge(CAPACITY_QUERY_UNITS);
                int room = Math.max(0, limits.entityLimit() - countNearbyEntities(demand.getKey(), limits.entityLimit(), limits.entityRadius()));
                groups = Math.min(groups, room / demand.getValue());
            }
        }
        return hasOneShot && !hasRepeatable ? 1 : groups;
    }

    // 组内需求累加采用饱和值，不允许溢出后绕过阈值。
    private static int saturatedAdd(int left, int right) {
        return (int) Math.min(Integer.MAX_VALUE, (long) left + right);
    }

    // 派发：逐组支付成本并派发候选内效果；每刻派发量受 dispatch_batch_size 限制
    private StepResult dispatch(ServerTickBudget budget) {
        int batch = scheduler.config().dispatchBatchSize();
        // 最后一组已开组也必须继续派发剩余效果，预算让出后保留同一组游标。
        while (groupOpen || groupsStarted < groupCount) {
            if (!groupOpen) {
                // 已选但尚未支付成本的候选可能跨刻等待，开组前再次核对邻近存量。
                if (pendingCandidate != null) {
                    int capacity = capacityGroupsAt(pendingCandidateIndex, budget);
                    if (capacity == CAPACITY_PENDING) { return StepResult.yield(1, 1); }
                    remainingCapacity[pendingCandidateIndex] = Math.min(remainingCapacity[pendingCandidateIndex], capacity);
                    if (remainingCapacity[pendingCandidateIndex] <= 0) {
                        pendingCandidate = null;
                        pendingCandidateIndex = -1;
                    }
                }
                if (pendingCandidate == null) {
                    // 每组独立选候选：轮询从共享游标搜索能完成一组的候选，容量不足的候选跳过
                    int pick = selectCandidateForGroup(budget);
                    if (pick == CAPACITY_PENDING) {
                        // 容量搜索未完成（预算耗尽）：本刻不开组，不落任何决定，下刻从同一游标重算
                        return StepResult.yield(1, 1);
                    }
                    if (pick == SELECT_EXHAUSTED) {
                        // 所有候选都开不出一组：组数截断为已开始组数，剩余源库存走返还路径
                        // 组数截断经受控入口落账，随后释放未支付预留
                        int stoppedGroups = groupCount - groupsStarted;
                        groupCount = groupsStarted;
                        writer.plan(record.candidateId(), groupCount);
                        reservations().releaseOwner(record.id());
                        if (RuntimeObserver.current().enabled()) {
                            RuntimeObserver.current().observe(source, "OUTCOME_SKIPPED", "record", record.id(), "rule", rule.id(),
                                    "candidates", candidates.size(), "groups_started", groupsStarted,
                                    "stopped_groups", stoppedGroups, "reason", "capacity");
                        }
                        break;
                    }
                    pendingCandidate = candidates.get(pick);
                    pendingCandidateIndex = pick;
                }
                if (rule.catalystCost() != null) {
                    if (groupThreshold == null) groupThreshold = new CatalystThresholdCheck(level, source, triggerPosition, rule.catalystCost());
                    if (!groupThreshold.advance(budget)) return StepResult.yield(1, 1);
                }
                if (rule.consumeFluid()) {
                    if (groupFluid == null) groupFluid = new FluidSourceResolver(level, triggerPosition);
                    if (!groupFluid.advance(budget)) return StepResult.yield(1, 1);
                }
                // 输入统一复核后支付；流体写入与催化剂扣除之间不让出。

                int perGroup = record.catalystCostPerGroup();
                int paidCatalyst;
                {
                    CatalystReservations.PayResult pay = reservations().payGroup(record.id(), level, perGroup, budget,
                            EffectTargets.blockBox(BlockPos.containing(triggerPosition),
                                    rule.catalystCost() == null ? 0 : rule.catalystCost().radius()),
                            () -> (groupThreshold == null || groupThreshold.valid())
                                    && (groupFluid == null || groupFluid.consume()));
                    if (pay.outcome() == CatalystReservations.PayOutcome.RETRY) {
                        // 本刻预算耗尽：一件不扣、不开组，下刻从头重试
                        return StepResult.yield(1, 1);
                    }
                    if (pay.outcome() == CatalystReservations.PayOutcome.SHORT) {
                        // 可用量不足：不开该组（不扣源成本、不派发效果），组数截断为已开始组数，
                        // 释放未支付预留，剩余源库存走正常返还路径（绝不「少扣照常产出」）
                        // 组数截断经受控入口落账，随后释放未支付预留
                        int shortGroups = groupCount - groupsStarted;
                        groupCount = groupsStarted;
                        writer.plan(record.candidateId(), groupCount);
                        reservations().releaseOwner(record.id());
                        if (RuntimeObserver.current().enabled()) {
                            RuntimeObserver.current().observe(source, "CATALYST_SHORT", "record", record.id(), "rule", rule.id(),
                                    "groups_started", groupsStarted, "group_count", groupCount,
                                    "short_groups", shortGroups, "short_catalyst", shortGroups * perGroup);
                        }
                        break;
                    }
                    paidCatalyst = pay.paid();
                }
                groupThreshold = null;
                groupFluid = null;
                int cost = Math.min(record.sourceCostPerGroup(), held.getCount());
                if (cost > 0) {
                    // 已开始的组支付完整固定成本，成本从结算库存里扣掉
                    held.setCount(held.getCount() - cost);
                }
                writer.groupStarted(cost);
                // 固定顺序：催化剂已支付、源成本已扣减，才记账
                writer.catalystGroupPaid(paidCatalyst);
                // 已开始组的扣减与记账在同一刻完成（两者之间没有让出点），且待交付量在本步骤出口对齐：
                // 中途停服重启后，已支付成本的组不会因为未落账而被当作「未开始库存」重复返还
                groupsStarted++;
                groupOpen = true;
                effectCursor = 0;
                // 本组候选定稿：容量递减 + 轮询游标推进（确定该组并开组后才推进，被跳过的候选不推进）
                candidate = pendingCandidate;
                pendingCandidate = null;
                if (remainingCapacity[pendingCandidateIndex] != Integer.MAX_VALUE) {
                    remainingCapacity[pendingCandidateIndex]--;
                }
                if (rotation) {
                    rotationIndex = Math.floorMod(pendingCandidateIndex + 1, candidates.size());
                    cursors.moveTo(cursorKey(), structureVersion, candidates.size(), pendingCandidateIndex + 1);
                }
                pendingCandidateIndex = -1;
                groupContext = contextForGroup(groupsStarted);
            }
            if (budget.exhausted() || dispatchedThisStep >= batch) {
                // 预算耗尽或批量上限让出：返回前由步骤出口按当前所有权对齐待交付量
                return StepResult.yield(1, 1);
            }
            List<Effect> effects = candidate.effects();
            if (effectCursor >= effects.size()) {
                groupOpen = false;
                continue;
            }
            int slot = effectCursor++;
            Effect effect = effects.get(slot);
            if (isOneShotAttempted(candidate.id(), slot, effect)) {
                // 同一源请求内一次性效果只尝试一次：后续组跳过该槽，不重试也不重复计费
                if (RuntimeObserver.current().enabled()) {
                    RuntimeObserver.current().observe(source, "EFFECT_ONESHOT_SKIPPED", "effect", effect.type(),
                            "outcome", candidate.id(), "slot", slot, "group", groupsStarted);
                }
                continue;
            }
            dispatchEffect(effect, slot, groupContext, groupsStarted - 1);
            budget.charge(1);
            dispatchedThisStep++;
        }
        // 派发结束：待交付量由步骤出口对齐，随后进入交付阶段
        phase = Phase.DELIVERING;
        return StepResult.yield(1, 1);
    }

    // 一次性效果是否已在同一源请求（本次结算）内尝试过：首次尝试时登记并返回 false；
    // 概率落空、条件不满足、执行失败都算已尝试，续跑不重试；未注册类型不参与
    private boolean isOneShotAttempted(String candidateId, int slot, Effect effect) {
        EffectType<?> definition = runtime.effectDefinition(effect.type());
        if (definition == null || !definition.oneShot()) {
            return false;
        }
        return !attemptedOneShots.add(candidateId + "#" + slot);
    }

    // 交付：剩余库存作为返还掉落物分批加入世界；失败/未加载区块时保留待返还并重试
    private StepResult deliver(ServerTickBudget budget) {
        int batch = scheduler.config().dispatchBatchSize();
        int deliveredNow = 0;
        while (held.getCount() > 0 && deliveredNow < batch) {
            if (budget.exhausted()) {
                return StepResult.yield(1, 1);
            }
            int size = Math.clamp(held.getCount(), 1, Math.max(1, held.getMaxStackSize()));
            ItemStack stack = held.copyWithCount(size);
            ReturnItemSpawner.Outcome outcome = deliverStack(stack, budget);
            if (outcome == ReturnItemSpawner.Outcome.CHUNK_UNLOADED) {
                // 区块未加载：本刻未扣减待交付量，由步骤出口对齐后带退避重试
                return StepResult.retry(1, 20);
            }
            if (outcome == ReturnItemSpawner.Outcome.NEEDS_MORE_STEPS) {
                // 位置搜索本刻预算用尽：保留待返还、下刻从断点继续，不整批放弃
                return StepResult.yield(1, 1);
            }
            held.setCount(held.getCount() - size);
            writer.delivered(size);
            budget.charge(2);
            deliveredNow++;
        }
        if (held.getCount() > 0) {
            // 本刻批量交付上限已到：剩余待交付量由步骤出口对齐，下刻继续
            return StepResult.yield(1, 1);
        }
        // 返还已全部交付：完成记录并交出库存所有权（此后不再对齐待交付量）
        writer.complete(level.getGameTime());
        ownsStock = false;
        // 防御：已完成记录的未支付预留必然为空（取消/不足路径已释放），这里幂等再释放一次
        reservations().releaseOwner(record.id());
        phase = Phase.DONE;
        if (RuntimeObserver.current().enabled()) {
            RuntimeObserver.current().observe(source, "SETTLEMENT_COMPLETED", "record", record.id(), "rule", rule.id(),
                    "candidate", record.candidateId(), "groups", record.groupCount(),
                    "consumed_sources", record.consumedSources(), "applied_units", record.appliedUnits(),
                    "pending_units", record.pendingUnits(), "returned", record.deliveredReturns(),
                    "pending_delivery", record.pendingDelivery(), "prepared_groups", preparedGroupCount);
        }
        if (RuntimeObserver.current().enabled()) {
            RuntimeObserver.current().observe(source, "REBATE_DELIVERED", "record", record.id(), "returned", record.deliveredReturns());
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
        // 进度回执经受控句柄改账目：执行器持有的只是写入通道，拿不到可任意变更账目的裸入口
        IntConsumer sink = writer::addProgress;
        int covered = record.sourceCostPerGroup() > 0 ? record.sourceCostPerGroup() : 1;
        // 候选级位置策略（safe_spawn / fill_origin）随组上下文传给执行器；位置用成功转化当刻的固定值
        return new RuntimeEffectContext(level, source, triggerPosition, rule.id(), scheduler,
                sourceSnapshot, sourceVelocity, 1, covered, candidate.id(), groupIndex - 1, groupCount,
                record.sourceCostPerGroup(), sink, candidate.safeSpawn(), candidate.fillOrigin());
    }

    // 效果派发：延迟交给调度器；成功转化当刻已固定输入的槽位按本组的固定输入执行，其余在首次执行时统一判定
    private void dispatchEffect(Effect effect, int slot, EffectContext context, int groupIndex) {
        EffectPreparation preparation = preparations.get(
                new ConversionRuntime.PreparedEffectKey(candidate.id(), slot, preparedGroupIndex(effect, groupIndex)));
        int delay = Math.max(0, effect.delayTicks());
        if (RuntimeObserver.current().enabled()) {
            RuntimeObserver.current().observe(source, "EFFECT_SCHEDULED", "effect", effect.type(),
                    "delay_ticks", delay, "chance", effect.chance(), "outcome", candidate.id());
        }
        RuntimeObserver.current().schedule(source, scheduler, level.dimension(),
                level.getGameTime() + delay,
                new EffectTask(runtime, level, source, context, effect, preparation, this::account));
    }

    // 固定输入的准备组序号：一次性效果只在首次尝试时固定一份，因此任何组都取第 0 份；
    // 其余效果按本组序号各取自己的一份，缺失即表示本组没有固定输入
    private int preparedGroupIndex(Effect effect, int groupIndex) {
        EffectType<?> definition = runtime.effectDefinition(effect.type());
        return definition != null && definition.oneShot() ? 0 : groupIndex;
    }

    // 执行器回执入账：已完成、已受理未完成、跳过与失败分别计数，计划量不冒充成功量
    private void account(EffectResult result) {
        // 中止后到达的迟到回执不得再落账：记录已移交恢复任务，写入会把已终结记录重新登记进账本
        if (cancelled) {
            return;
        }
        if (result == null) {
            writer.effectFailed();
        } else {
            switch (result.outcome()) {
                case APPLIED -> writer.addApplied(result.appliedUnits());
                case DEFERRED -> {
                    writer.addApplied(result.appliedUnits());
                    writer.addPending(result.pendingUnits());
                }
                case SKIPPED -> writer.effectSkipped();
                case FAILED -> writer.effectFailed();
            }
        }
        // 回执只更新本记录的效果计数（applied/pending units），不触碰待交付量，且已由写入入口自行落账；
        // 这里沿用任务出口是为了在仍持有库存时补一次所有权对齐，移交后不再触碰待交付量，不覆盖恢复任务的交付进度
        commit();
        if (result != null && RuntimeObserver.current().enabled()) {
            RuntimeObserver.current().observe(source, "EFFECT_SETTLED", "outcome", result.outcome(),
                    "applied", result.appliedUnits(), "pending", result.pendingUnits(), "detail", result.detail());
        }
    }

    // 轮询游标键：规则 id + 维度（跨实体共享、可持久化）
    private String cursorKey() {
        return rule.id() + "@" + level.dimension().location();
    }

    // 邻近物品总量：与实体产出掉落物子类同一判定（具体物品比注册表对象，标签比 TagKey），
    // 匹配器按引用编译一次后逐实体复用，不在查询谓词里重复创建 TagKey。
    // 计数口径与 countNearbyEntities 一致：谓词只筛选实体，收集条数由查询的 maxResults 限制，
    // 数量来自结果列表；物品按堆叠数逐条累加（空堆叠不命中，因此收满 limit 条时总量必然 >= limit，
    // 此时 room 已经归零，不会高估可开组数）。
    private int countNearbyItems(TaggedId reference, int limit, int radius) {
        AABB box = searchBox(radius);
        TaggedItemMatch target = TaggedItemMatch.compile(reference);
        List<ItemEntity> nearby = new ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(ItemEntity.class), box,
                item -> item.isAlive() && !item.getItem().isEmpty() && target.matches(item.getItem()), nearby, limit);
        int total = 0;
        for (ItemEntity item : nearby) {
            total = saturatedAdd(total, item.getItem().getCount());
        }
        return total;
    }

    // 邻近实体总量：与 spawn_entity 执行器同一判定（支持具体类型与标签）
    private int countNearbyEntities(TaggedId reference, int limit, int radius) {
        AABB box = searchBox(radius);
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
        List<Entity> nearby = new ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(Entity.class), box,
                entity -> entity.isAlive() && matchesEntityType(reference, entity), nearby, limit);
        return nearby.size();
    }

    // 搜索盒与执行器一致：以源位置所在方块为中心、边长 2*radius+1 格
    private AABB searchBox(int radius) {
        Vec3 position = source.position();
        return AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(BlockPos.containing(position.x, position.y, position.z)))
                .inflate(radius);
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
