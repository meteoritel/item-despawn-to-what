package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.RuntimeObserver;
import com.meteorite.itemdespawntowhat.core.api.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ScheduledTaskHandle;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepResult;
import com.meteorite.itemdespawntowhat.core.model.CatalystCost;
import com.meteorite.itemdespawntowhat.core.model.CombinationMode;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.OutcomeCandidate;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerScheduler;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.SpawnEntityExecutor;
import com.meteorite.itemdespawntowhat.core.type.effect.PlaceBlockEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.PlaceBlockExecutor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * 一次转化的结算任务：聚合根只负责阶段推进与提交边界，具体工作交给结算协作者。
 * 阶段（{@link SettlementPhases}）：
 * 1) 计划——按源库存、催化剂与初始产出预算的共同上界定出组数，并为第一组选定候选；
 * 2) 准备——为承诺的每组固定效果输入：每步最多 prepare_groups_per_step 组，按组续接，
 *    固定输入按「候选 + 槽位 + 组序号」幂等，已由成功转化当刻领先准备的组直接复用；
 * 3) 派发——每开始一组时独立选候选：轮询从共享游标依次搜索能完成一组的候选，
 *    确定该组并开组后才推进游标；容量不足的候选跳过，全部候选都开不出一组时
 *    停止并返还剩余库存；
 *    每组分配源成本，方块按实际放置占用，其它行为按组支付；效果延迟交给调度器；
 * 4) 交付——等待全部效果终态，剩余源库存分批返还后完成。
 * 协作者：
 * - {@link SettlementGroupPlan} 候选与组计划：定组数、逐组选候选，组数上界只有一处实现；
 * - {@link SettlementCapacityProbe} 容量探测：把候选每轮产出折算成可开组数；
 * - {@link SettlementCatalystStage} 催化剂编排：计划期上界、预留组与开组前复核；
 * - {@link SettlementPayment} 支付与交付：开组前输入复核、催化剂扣除与返还交付；
 * - {@link SettlementReceipts} 回执记账：账目的唯一写入通道与库存所有权；
 * - {@link SettlementItemOutputs} 直接物品汇总：已支付组按物品件数装箱，保留最晚原定执行时刻；
 * - {@link SettlementBlockOutputs} 方块汇总：同目标共享候选游标，保留逐组到期刻；
 * - {@link SettlementSourceCharge} 源成本占用：同组效果共享成本，按最高占用递增扣除；
 * - {@link SettlementPhases} 阶段状态机：只维护当前阶段与合法跃迁。
 * 一次性效果（EffectType.oneShot）：不参与候选数量上限；仅含一次性效果的候选最多一组；
 * 同一源请求内每个一次性效果槽（候选 id + 槽序号）只尝试一次：
 * 概率落空或条件不满足也算已尝试，续跑不重试。
 * 真实账目只来自执行器回执（EffectResult 与 reportProgress），计划量不冒充成功量；
 * 取消（规则重载/维度卸载/停服）只落一条待返还记录，不在不安全时刻做世界写入，
 * 交给恢复任务（SettlementRecovery）交付。
 */
final class ConversionSettlement implements ServerTask {

    private static final Logger LOGGER = LogManager.getLogger();

    // 支付受阻原因：截断整批时写入日志与观测，回执据此区分容量、催化剂、门槛与流体不足
    private enum PayReason {
        CAPACITY("capacity_short"),
        CATALYST("catalyst_short"),
        THRESHOLD("threshold_short"),
        FLUID("fluid_short");

        private final String id;

        PayReason(String id) { this.id = id; }

        String id() { return id; }
    }

    // 开组状态区分预算让出、区块等待与等待超时，避免把未决输入当成候选不足。
    private enum GroupStep { OPENED, RETRY_SELECT, YIELD, WAITING_FOR_CHUNKS, FAILED, TRUNCATE }

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
    // 同一候选同一槽位连接共享执行状态；可共享固定输入的类型额外复用首份实例。
    private final PreparedEffectSharing sharedPreparations = new PreparedEffectSharing();
    // 成功转化当刻已领先准备的组数：准备阶段从这里续接，不重做已固定的组
    private final int leadPreparedGroups;
    // 准备进度：已固定输入的组数与承诺组数
    private int prepareGroupIndex;
    private int preparedGroupCount;
    // 账目读取引用与受控写入通道：字段变更一律经 receipts
    private final SettlementRecord record;
    private final SettlementReceipts receipts;
    private final SettlementGroupPlan groupPlan;
    private final SettlementCatalystStage catalystStage;
    private final SettlementPayment payment;
    private final SettlementPhases phases = new SettlementPhases();
    private final List<OutcomeCandidate> candidates;
    private final int structureVersion;
    // 开组前复核：催化剂门槛与流体来源，每开一组前重新建并推进，开组成功后清空
    private CatalystThresholdCheck groupThreshold;
    private FluidSourceResolver groupFluid;
    // 最近一次因门槛/流体让出的原因：重扫到无候选可开时按该原因截断，便于回执区分四种不足
    private @Nullable PayReason paymentBlockReason;

    private OutcomeCandidate candidate;
    // 已选定但尚未开组的候选：预算让出点跨刻保留选择，不重进 tick 重新选择
    private OutcomeCandidate pendingCandidate;
    private int pendingCandidateIndex = -1;
    // 同一源请求内已尝试过的一次性效果槽（键 = 候选 id + "#" + 槽序号）
    private final Set<String> attemptedOneShots = new HashSet<>();
    private EffectContext groupContext;
    private int groupCount;
    private int groupsStarted;
    private int effectCursor;
    private int dispatchedThisStep;
    private boolean groupOpen;
    private boolean cancelled;
    private final SettlementItemOutputs itemOutputs = new SettlementItemOutputs();
    private final SettlementBlockOutputs blockOutputs = new SettlementBlockOutputs();
    private int pendingItemBatches;
    private int pendingEffects;
    private final List<ScheduledTaskHandle> effectTasks = new ArrayList<>();
    private SettlementSourceCharge sourceCharge;

    // 构造结算任务：源库存 held 已在触发时刻整堆移出源实体，
    // 受控写入句柄由调用方（首次登记方）给出
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
        // 领先准备与后续准备连接到同一份执行状态，跨准备阶段仍遵守同一槽位额度。
        this.preparations.replaceAll(sharedPreparations::share);
        this.leadPreparedGroups = Math.max(0, leadPreparedGroups);
        this.receipts = new SettlementReceipts(writer);
        this.record = receipts.record();
        this.candidates = rule.effectiveOutcomes();
        // 候选结构摘要按规则实例缓存，结构未变时不重算；失效条件见 ConversionRuntime
        this.structureVersion = runtime.candidateStructureVersion(rule, level.registryAccess());
        this.groupPlan = new SettlementGroupPlan(level, rule, candidates, structureVersion,
                new SettlementCapacityProbe(runtime, level, this.triggerPosition));
        this.catalystStage = new SettlementCatalystStage(runtime, level, source, this.triggerPosition, rule, held,
                record.sourceCostPerGroup(), record.id());
        this.payment = new SettlementPayment(runtime, scheduler, level, source, held, receipts);
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
        // 步内累积：本步的账目变更只在 finally 合并落账一次，
        // 成批交付因此从「每堆一次提交」降为「每步一次」
        receipts.beginStep();
        try {
            StepResult result = switch (phases.current()) {
                case PLANNING -> plan(budget);
                case PREPARING -> prepare(budget);
                case DISPATCHING -> dispatch(budget);
                case DELIVERING -> deliver(budget);
                case DONE -> StepResult.done(1);
            };
            // 任务内统一状态出口：正常完成、显式中止、预算耗尽提前让出与交付让出
            // 都在这里按当前所有权对齐待交付量
            commit();
            return result;
        } catch (RuntimeException failure) {
            // 异常路径与取消路径共用同一条中止顺序：先把待返还量写盘并释放未支付预留，
            // 再把异常抛给调度器转 FAILED，避免预留悬空
            abort("exception: " + failure.getClass().getSimpleName(), true);
            throw failure;
        } finally {
            // 本步累积的变更无论正常出口、中止还是异常都要落账；
            // 取消回调在步外发生，不受本步累积影响
            receipts.endStep();
        }
    }

    // 任务内统一出口：所有权仍在本任务时，把记录的待交付量对齐到手里的结算库存
    // （受控入口会落账）；所有权已移交（中止/完成）时不再触碰待交付量，
    // 避免覆盖恢复任务推进的交付进度
    private void commit() {
        receipts.syncPendingDelivery(held.getCount());
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
     * 失败回调：结算步骤抛异常时按失败类型区分原因，
     * 落盘待返还量并在仍有库存时交给运行期恢复队列。
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

    // 中止结算并把待返还量落盘：取消路径（onCancelled）与异常路径（step 的 catch、onFailed）
    // 共用同一条顺序，不新造第二份账目口径。顺序：幂等位 → 取消子任务 → 待返还同步
    // → 释放未支付预留 → 状态记录 → 观测。
    // 本方法不做任何世界写入（维度卸载/停服时加入世界不安全，
    // 待返还数据留给恢复任务 SettlementRecovery）；返回 false 表示此前已中止过
    // （幂等，不重复写盘）。failed=true 时记 FAILED 语义，否则记 INTERRUPTED。
    // 批量取消时单个任务收尾失败不得让同批其它任务漏掉中止：
    // 每一步各自兜异常，失败只降级为日志。
    private boolean abort(String reason, boolean failed) {
        if (cancelled) {
            return false;
        }
        cancelled = true;
        // 先终止已排期的子任务，返还库存移交后不会再发生世界写入。
        for (ScheduledTaskHandle task : effectTasks) {
            task.cancel(CancelReason.SETTLEMENT_ABORTED);
        }
        effectTasks.clear();
        // 尚未派发的汇总产物随结算中止丢弃；效果不恢复执行，未消耗源库存统一返还。
        itemOutputs.clear();
        blockOutputs.clear();
        // 中止即移交库存所有权：剩余库存改由恢复任务交付，本任务不再改写待返还量
        receipts.giveUpStock();
        try {
            // 待返还量以任务手里的结算库存为准：重启后只交付这一部分，不重放已开始的组
            receipts.writePendingDelivery(held.getCount());
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
                    receipts.fail(reason, level.getGameTime());
                } else {
                    receipts.interrupt(reason, level.getGameTime());
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

    // 计划：定组数上界 → 为第一组选候选 → 预留催化剂；
    // 预算耗尽时本刻不落决定，保持 PLANNING 下刻重试
    private StepResult plan(ServerTickBudget budget) {
        budget.charge(1);
        receipts.markStarted(level.getGameTime());
        if (candidates.isEmpty() || held.getCount() <= 0 || record.sourceCostPerGroup() > held.getCount()) {
            groupCount = 0;
            receipts.plan("", 0);
            phases.skipToDelivering();
            return StepResult.yield(1, 1);
        }
        int costPerGroup = record.sourceCostPerGroup();
        int sourceGroups = costPerGroup > 0 ? held.getCount() / costPerGroup : 1;
        // 催化剂固定成本上界：PENDING = 预算耗尽本刻不落决定
        int catalystLimit = catalystStage.groupLimit(budget);
        if (catalystLimit == SettlementCatalystStage.PENDING) {
            return StepResult.yield(1, 1);
        }
        int planned = Math.min(sourceGroups, catalystLimit);
        // 初始产出预算：组数先按计划期初始存量与声明理论产量截断。准备成本随组数增长，
        // 先确定预算再准备，准备量由已分配的理论额度约束。
        // 预算耗尽时本刻不落决定，已累计的进度跨刻保留。
        int committed = groupPlan.commit(planned, budget);
        if (committed == SettlementGroupPlan.CAPACITY_PENDING) {
            return StepResult.yield(1, 1);
        }
        // 本结算第一组的候选：计划期只落「第一组」的选择，后续组在派发期逐组重选
        int pick = SettlementGroupPlan.SELECT_EXHAUSTED;
        if (committed > 0) {
            pick = groupPlan.select(budget);
        }
        if (pick == SettlementGroupPlan.CAPACITY_PENDING) {
            // 容量搜索未完成（预算耗尽）：本刻不落任何决定
            return StepResult.yield(1, 1);
        }
        if (pick == SettlementGroupPlan.SELECT_EXHAUSTED) {
            return abandonAll();
        }
        pendingCandidate = candidates.get(pick);
        pendingCandidateIndex = pick;
        groupCount = committed;
        CatalystCost catalyst = catalystStage.cost();
        if (catalyst != null && catalyst.totalConsumeCount() > 0
                && reservations().tryReserve(record.id(), level, catalystStage.paymentGroups(groupCount)) == null) {
            // 防御：扫描所得可用量与预留结果不一致时整批返还，绝不半预留
            return abandonAll();
        }
        // 组数定稿后先登记催化剂成本再落计划；预留已完成，两者之间没有 yield 点
        receipts.catalystPlan(catalyst == null ? 0 : catalyst.totalConsumeCount());
        receipts.plan(pendingCandidate.id(), groupCount);
        if (RuntimeObserver.current().enabled() && catalyst != null) {
            RuntimeObserver.current().observe(source, "CATALYST_PLANNED", "record", record.id(), "rule", rule.id(),
                    "per_group", catalyst.totalConsumeCount(), "groups", groupCount, "reserved", groupCount * catalyst.totalConsumeCount());
        }
        if (RuntimeObserver.current().enabled()) {
            // 每次结算只观测一条 OUTCOME_PLANNED（candidate = 本结算第一组所选候选，groups = 组数上界）；
            // 多组结算的逐组候选切换由每组的 EFFECT_SCHEDULED / EFFECT_ONESHOT_SKIPPED 观测体现
            RuntimeObserver.current().observe(source, "OUTCOME_PLANNED", "rule", rule.id(), "candidate", pendingCandidate.id(),
                    "groups", groupCount, "source_cost_per_group", record.sourceCostPerGroup(), "available", held.getCount(),
                    "structure_version", structureVersion, "cursor_start", groupPlan.rotationStart(), "candidates", candidates.size());
        }
        // 计划定稿后进入准备：领先准备已覆盖的组直接复用，剩余组按 prepare_groups_per_step 分组续接。
        // 领先准备已覆盖全部组时不插入 PREPARING 空转步，单组结算的步进节奏与改前一致
        prepareGroupIndex = Math.min(leadPreparedGroups, groupCount);
        preparedGroupCount = prepareGroupIndex;
        phases.beginPreparingOrDispatching(prepareGroupIndex >= groupCount);
        return StepResult.yield(1, 1);
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
        phases.preparingCompleted();
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
                        triggerPosition, sourceSnapshot, candidate.fillOrigin());
                if (preparation == null) {
                    // 未迁移的类型不在准备阶段固定输入，其概率仍在首次执行时判定
                    continue;
                }
                preparation = sharedPreparations.share(key, preparation);
                preparations.put(key, preparation);
            }
        }
    }

    // 无法开出任何一组：整堆返还，不支付任何成本
    private StepResult abandonAll() {
        pendingCandidate = null;
        pendingCandidateIndex = -1;
        groupCount = 0;
        receipts.plan("", 0);
        if (RuntimeObserver.current().enabled()) {
            RuntimeObserver.current().observe(source, "OUTCOME_SKIPPED", "rule", rule.id(), "candidates", candidates.size());
        }
        phases.skipToDelivering();
        return StepResult.yield(1, 1);
    }

    // 派发：逐组支付成本并派发候选内效果；每刻派发量受 dispatch_batch_size 限制
    private StepResult dispatch(ServerTickBudget budget) {
        int batch = scheduler.config().dispatchBatchSize();
        // 最后一组已开组也必须继续派发剩余效果，预算让出后保留同一组游标。
        while (groupOpen || groupsStarted < groupCount) {
            if (!groupOpen) {
                GroupStep step = openNextGroup(budget);
                if (step == GroupStep.YIELD) {
                    return StepResult.yield(1, 1);
                }
                if (step == GroupStep.WAITING_FOR_CHUNKS) {
                    return StepResult.yield(1, LoadedChunks.RETRY_TICKS);
                }
                if (step == GroupStep.FAILED) {
                    return StepResult.failed(1);
                }
                if (step == GroupStep.TRUNCATE) {
                    break;
                }
                if (step == GroupStep.RETRY_SELECT) {
                    continue;
                }
            }
            if (budget.exhausted() || dispatchedThisStep >= batch) {
                // 预算耗尽或批量上限让出：返回前由步骤出口按当前所有权对齐待交付量
                return StepResult.yield(1, 1);
            }
            dispatchNextEffect(budget);
        }
        // 所有已支付组都已确定产物，再按桶派发；保留原定延迟，跨组尾堆只交付一次。
        while (!itemOutputs.isEmpty()) {
            if (budget.exhausted() || dispatchedThisStep >= batch) {
                return StepResult.yield(1, 1);
            }
            SettlementItemOutputs.Batch output = itemOutputs.next();
            scheduleItemBatch(output);
            budget.charge(1);
            dispatchedThisStep++;
        }
        while (!blockOutputs.isEmpty()) {
            if (budget.exhausted() || dispatchedThisStep >= batch) {
                return StepResult.yield(1, 1);
            }
            SettlementBlockOutputs.Batch output = blockOutputs.next();
            scheduleEffect(output.readyTick(), output.context(), output.effect(), output.preparation(), () -> {});
            budget.charge(1);
            dispatchedThisStep++;
        }
        // 派发结束：待交付量由步骤出口对齐，随后进入交付阶段
        phases.dispatchingCompleted();
        return StepResult.yield(1, 1);
    }

    // 开组：按剩余预算选组 → 门槛与流体复核 → 支付 → 记账扣库存。每段单一语义，失败按原因分流
    private GroupStep openNextGroup(ServerTickBudget budget) {
        if (pendingCandidate == null) {
            // 每组独立选候选：轮询从共享游标搜索能完成一组的候选，容量不足的候选跳过
            int pick = groupPlan.select(budget);
            if (pick == SettlementGroupPlan.CAPACITY_PENDING) {
                // 容量搜索未完成（预算耗尽）：本刻不开组，不落任何决定，下刻从同一游标重算
                return GroupStep.YIELD;
            }
            if (pick == SettlementGroupPlan.SELECT_EXHAUSTED) {
                // 所有候选都开不出一组：截断到已开始组数，剩余源库存走返还路径；
                // 若本刻重扫是门槛/流体让出触发的，截断原因沿用该原因，与容量不足区分
                truncateGroups(paymentBlockReason == null ? PayReason.CAPACITY : paymentBlockReason);
                return GroupStep.TRUNCATE;
            }
            pendingCandidate = candidates.get(pick);
            pendingCandidateIndex = pick;
        }
        if (rule.catalystCost() != null) {
            if (groupThreshold == null) {
                groupThreshold = catalystStage.thresholdCheck();
            }
            CatalystThresholdCheck threshold = groupThreshold;
            if (threshold != null && !threshold.advance(budget)) {
                if (threshold.timedOut()) {
                    // 先按唯一中止出口移交库存，调度器失败回调再登记恢复任务。
                    abort("catalyst_chunk_wait_timeout", true);
                    return GroupStep.FAILED;
                }
                return threshold.waitingForChunks() ? GroupStep.WAITING_FOR_CHUNKS : GroupStep.YIELD;
            }
        }
        if (rule.consumeFluid()) {
            if (groupFluid == null) {
                groupFluid = catalystStage.fluidSource();
            }
            FluidSourceResolver fluid = groupFluid;
            if (fluid != null && !fluid.advance(budget)) {
                return GroupStep.YIELD;
            }
        }
        // 输入统一复核后支付；流体写入与催化剂扣除之间不让出。
        CatalystReservations.PayResult pay = payment.pay(budget, record.id(), record.catalystCostPerGroup(),
                groupThreshold, groupFluid);
        if (pay.outcome() == CatalystReservations.PayOutcome.RETRY) {
            // 本刻预算耗尽：一件不扣、不开组，下刻从头重试
            return GroupStep.YIELD;
        }
        if (pay.outcome() == CatalystReservations.PayOutcome.CATALYST_SHORT) {
            // 真实催化剂不足（实体消失或件数被扣减）：不开该组（不扣源成本、不派发效果），
            // 组数截断为已开始组数，释放未支付预留，
            // 剩余源库存走正常返还路径（绝不「少扣照常产出」）
            truncateGroups(PayReason.CATALYST);
            return GroupStep.TRUNCATE;
        }
        if (pay.outcome() == CatalystReservations.PayOutcome.THRESHOLD_SHORT
                || pay.outcome() == CatalystReservations.PayOutcome.FLUID_SHORT) {
            // 门槛或流体在开组当刻不满足：只弃当前候选组并重扫一次，不放整批截断；
            // 重扫也开不出一组时才按该原因截断到已开始组数
            dropCandidateForPayment(pay.outcome() == CatalystReservations.PayOutcome.THRESHOLD_SHORT
                    ? PayReason.THRESHOLD : PayReason.FLUID);
            return GroupStep.RETRY_SELECT;
        }
        int paidCatalyst = pay.paid();
        groupThreshold = null;
        groupFluid = null;
        int cost = record.sourceCostPerGroup();
        receipts.groupStarted();
        sourceCharge = new SettlementSourceCharge(cost, this::consumeSources);
        if (pendingCandidate.effects().stream().noneMatch(PlaceBlockEffect.class::isInstance)) {
            sourceCharge.chargeFully();
        }
        // 催化剂已实际支付；源成本可继续随延迟方块的成功回执扣除。
        receipts.catalystGroupPaid(paidCatalyst);
        // 开组次数与源消费量分开记录；每次源消费同步减少账面待返还量。
        groupsStarted++;
        groupOpen = true;
        effectCursor = 0;
        // 本组候选定稿：容量递减 + 轮询游标推进（确定该组并开组后才推进，被跳过的候选不推进）
        candidate = pendingCandidate;
        pendingCandidate = null;
        groupPlan.reserve(pendingCandidateIndex);
        groupPlan.advanceRotation(pendingCandidateIndex);
        pendingCandidateIndex = -1;
        // 本组已开：此前的门槛/流体让出不再是截断原因，后续受阻按新的原因记录
        paymentBlockReason = null;
        groupContext = contextForGroup(groupsStarted);
        return GroupStep.OPENED;
    }

    // 派发本组的下一步：本组效果已派发完时关闭组，否则派发一个槽位
    private void dispatchNextEffect(ServerTickBudget budget) {
        List<Effect> effects = candidate.effects();
        if (effectCursor >= effects.size()) {
            groupOpen = false;
            return;
        }
        int slot = effectCursor++;
        Effect effect = effects.get(slot);
        if (isOneShotAttempted(candidate.id(), slot, effect)) {
            // 同一源请求内一次性效果只尝试一次：后续组跳过该槽，不重试也不重复计费
            if (RuntimeObserver.current().enabled()) {
                RuntimeObserver.current().observe(source, "EFFECT_ONESHOT_SKIPPED", "effect", effect.type(),
                        "outcome", candidate.id(), "slot", slot, "group", groupsStarted);
            }
            return;
        }
        dispatchEffect(effect, slot, groupContext, groupsStarted - 1);
        budget.charge(1);
        dispatchedThisStep++;
    }

    // 组数截断为已开始组数：剩余源库存走返还路径，未支付预留一并释放。
    // 原因写入日志与观测回执，便于区分容量、催化剂、门槛与流体不足。
    private void truncateGroups(PayReason reason) {
        int stoppedGroups = groupCount - groupsStarted;
        groupCount = groupsStarted;
        receipts.plan(record.candidateId(), groupCount);
        reservations().releaseOwner(record.id());
        pendingCandidate = null;
        pendingCandidateIndex = -1;
        groupThreshold = null;
        groupFluid = null;
        paymentBlockReason = null;
        LOGGER.warn("结算支付受阻：规则={} 记录={} 原因={} 已开始组={} 截断组={}",
                rule.id(), record.id(), reason.id(), groupsStarted, stoppedGroups);
        if (RuntimeObserver.current().enabled()) {
            RuntimeObserver.current().observe(source, "PAYMENT_BLOCKED", "record", record.id(), "rule", rule.id(),
                    "reason", reason.id(), "groups_started", groupsStarted, "group_count", groupCount,
                    "stopped_groups", stoppedGroups);
        }
    }

    // 门槛或流体在开组当刻不满足：弃当前候选组并让本结算不再选它（剩余容量置 0 是硬上界），
    // 随后由派发循环重扫其它候选；同一刻重复查询世界只会得到同一结论，
    // 故本结算内不再重试该候选。
    private void dropCandidateForPayment(PayReason reason) {
        if (pendingCandidateIndex >= 0) {
            groupPlan.deny(pendingCandidateIndex);
        }
        pendingCandidate = null;
        pendingCandidateIndex = -1;
        groupThreshold = null;
        groupFluid = null;
        paymentBlockReason = reason;
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
        // 方块预算尚未消费完时库存仍归效果所有；所有任务收尾后才交付剩余源物品。
        if (pendingEffects > 0) {
            return StepResult.yield(1, 1);
        }
        SettlementPayment.DeliveryOutcome outcome =
                payment.deliverRemaining(budget, scheduler.config().dispatchBatchSize());
        if (outcome == SettlementPayment.DeliveryOutcome.RETRY) {
            // 区块未加载：本刻未扣减待交付量，由步骤出口对齐后带退避重试
            return StepResult.retry(1, LoadedChunks.RETRY_TICKS);
        }
        if (outcome == SettlementPayment.DeliveryOutcome.YIELD) {
            // 位置搜索预算用尽或批量上限已到：保留待返还、下刻从断点继续，不整批放弃
            return StepResult.yield(1, 1);
        }
        // 返还已全部交付：完成记录并交出库存所有权（此后不再对齐待交付量）
        receipts.complete(level.getGameTime());
        receipts.giveUpStock();
        effectTasks.clear();
        // 防御：已完成记录的未支付预留必然为空（取消/不足路径已释放），这里幂等再释放一次
        reservations().releaseOwner(record.id());
        phases.deliveringCompleted();
        if (RuntimeObserver.current().enabled()) {
            RuntimeObserver.current().observe(source, "SETTLEMENT_COMPLETED", "record", record.id(), "rule", rule.id(),
                    "candidate", record.candidateId(), "groups", record.groupCount(),
                    "consumed_sources", record.consumedSources(), "applied_units", record.appliedUnits(),
                    "pending_units", record.pendingUnits(), "returned", record.deliveredReturns(),
                    "pending_delivery", record.pendingDelivery(), "prepared_groups", preparedGroupCount,
                    "pending_item_batches", pendingItemBatches,
                    // 账本写入合并口径：本记录的落账次数与被步内合并的变更次数，
                    // 供场景核对每步一次提交
                    "ledger_commits", receipts.commitCount(),
                    "ledger_deferred_mutations", receipts.deferredMutations());
        }
        if (RuntimeObserver.current().enabled()) {
            RuntimeObserver.current().observe(source, "REBATE_DELIVERED", "record", record.id(), "returned", record.deliveredReturns());
        }
        return StepResult.done(1);
    }

    // 本组上下文：组序号、组数、本组已付成本与真实回执入口
    private EffectContext contextForGroup(int groupIndex) {
        // 进度回执经受控句柄改账目：执行器持有的只是写入通道，拿不到可任意变更账目的裸入口
        IntConsumer sink = receipts.progressSink();
        return contextForGroup(groupIndex, sink);
    }

    private EffectContext contextForGroup(int groupIndex, IntConsumer sink) {
        int covered = record.sourceCostPerGroup() > 0 ? record.sourceCostPerGroup() : 1;
        // 候选级位置策略（safe_spawn / fill_origin）随组上下文传给执行器；位置用成功转化当刻的固定值
        return new RuntimeEffectContext(level, source, triggerPosition, rule.id(), scheduler,
                sourceSnapshot, sourceVelocity, 1, covered, candidate.id(), groupIndex - 1, groupCount,
                record.sourceCostPerGroup(), sink, candidate.safeSpawn(), candidate.fillOrigin());
    }

    // 效果派发：延迟交给调度器；成功转化当刻已固定输入的槽位按本组的固定输入执行，
    // 其余在首次执行时统一判定
    private void dispatchEffect(Effect effect, int slot, EffectContext context, int groupIndex) {
        EffectPreparation preparation = preparations.get(
                new ConversionRuntime.PreparedEffectKey(candidate.id(), slot, preparedGroupIndex(effect, groupIndex)));
        if (effect instanceof PlaceBlockEffect block) {
            if (preparation instanceof PlaceBlockExecutor.PlacementPlan plan && plan.block() != null) {
                context = contextForGroup(groupIndex + 1,
                        sourceCharge.placementProgress(block.count(), receipts.progressSink()));
            } else {
                // 概率落空与无效固定输入仍属于已支付的组尝试。
                sourceCharge.chargeFully();
            }
        } else {
            sourceCharge.chargeFully();
        }
        int delay = Math.max(0, effect.delayTicks());
        if (RuntimeObserver.current().enabled()) {
            RuntimeObserver.current().observe(source, "EFFECT_SCHEDULED", "effect", effect.type(),
                    "delay_ticks", delay, "chance", effect.chance(), "outcome", candidate.id());
        }
        long readyTick = level.getGameTime() + delay;
        if (itemOutputs.collect(candidate.id(), slot, effect, preparation, context, readyTick)) {
            return;
        }
        if (blockOutputs.collect(candidate.id(), slot, effect, preparation, context, readyTick)) {
            return;
        }
        scheduleEffect(readyTick, context, effect, preparation, () -> {});
    }

    // 所有效果共用生命周期计数，延迟方块、实体和世界效果的回执均在结算完成前入账。
    private void scheduleEffect(long readyTick, EffectContext context, Effect effect,
                                @Nullable EffectPreparation preparation, Runnable completed) {
        pendingEffects++;
        try {
            effectTasks.add(RuntimeObserver.current().schedule(source, scheduler, level.dimension(), readyTick,
                    new EffectTask(runtime, level, source, context, effect, preparation, this::account, () -> {
                        pendingEffects--;
                        completed.run();
                    })));
        } catch (RuntimeException failure) {
            pendingEffects--;
            throw failure;
        }
    }

    private void consumeSources(int count) {
        receipts.consumeSources(count);
        held.shrink(count);
    }

    // 一个汇总桶只注册一个效果任务；终态通知释放等待计数，产出回执只记录真实执行结果。
    private void scheduleItemBatch(SettlementItemOutputs.Batch output) {
        pendingItemBatches++;
        try {
            scheduleEffect(output.readyTick(), output.context(), output.effect(), output.preparation(), () -> {
                pendingItemBatches--;
                observeItemBatch("ITEM_BATCH_FINISHED", output);
            });
        } catch (RuntimeException failure) {
            pendingItemBatches--;
            throw failure;
        }
        observeItemBatch("ITEM_BATCH_SCHEDULED", output);
    }

    // 每桶只在排期与终态各观测一次，等待期间不逐 tick 上报。
    private void observeItemBatch(String event, SettlementItemOutputs.Batch output) {
        if (RuntimeObserver.current().enabled()) {
            SpawnEntityExecutor.ItemBatchPlan batch = output.preparation();
            RuntimeObserver.current().observe(source, event, "record", record.id(),
                    "candidate", output.context().outcomeId(), "item", BuiltInRegistries.ITEM.getKey(batch.item()),
                    "items", batch.count(), "ready_tick", output.readyTick(), "pending_item_batches", pendingItemBatches);
        }
    }

    // 固定输入的准备组序号：一次性效果只在首次尝试时固定一份，因此任何组都取第 0 份；
    // 其余效果按本组序号各取自己的一份，缺失即表示本组没有固定输入
    private int preparedGroupIndex(Effect effect, int groupIndex) {
        EffectType<?> definition = runtime.effectDefinition(effect.type());
        return definition != null && definition.oneShot() ? 0 : groupIndex;
    }

    // 终态回执不再落账，避免把已完成或已裁剪的记录重新登记。
    private void account(EffectResult result) {
        if (cancelled || record.completed()) {
            return;
        }
        receipts.apply(result);
        // 回执只更新本记录的效果计数（applied/pending units），不触碰待交付量；
        // 这里沿用任务出口是为了在仍持有库存时补一次所有权对齐
        commit();
        if (result != null && RuntimeObserver.current().enabled()) {
            RuntimeObserver.current().observe(source, "EFFECT_SETTLED", "outcome", result.outcome(),
                    "applied", result.appliedUnits(), "pending", result.pendingUnits(), "detail", result.detail());
        }
    }

    // 预留表挂在运行期实例上（停服清空）；这里只取引用，不缓存成字段以免与生命周期错位
    private CatalystReservations reservations() {
        return runtime.catalystReservations();
    }
}
