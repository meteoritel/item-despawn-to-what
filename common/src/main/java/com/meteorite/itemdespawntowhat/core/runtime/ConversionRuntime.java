package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.ConditionContext;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecutor;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.EffectStep;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.config.ServerConfig;
import com.meteorite.itemdespawntowhat.core.config.NearbyProductLimits;
import com.meteorite.itemdespawntowhat.core.load.LoadedRule;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.OutcomeCandidate;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.model.TriggerKind;
import com.meteorite.itemdespawntowhat.core.api.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.QueueStats;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ScheduledTaskHandle;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.SchedulerConfig;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.SchedulerStats;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerScheduler;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepResult;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepWorkCounter;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudgetSnapshot;
import com.meteorite.itemdespawntowhat.core.api.RuntimeObserver;
import com.meteorite.itemdespawntowhat.core.service.BuiltinTypeRegistries;
import com.meteorite.itemdespawntowhat.core.state.DamageClassification;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.VisualFeedback;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 转化运行时：规则索引 + 维度级追踪与调度。
 * 设计要点：
 * - 触发时刻 = min(trigger_after_seconds, 自然消失时刻)，在自然 discard 之前判定；
 * - 计时按绝对存活时间，条件不满足则退避重试（1s→2s→4s→封顶）；
 * - 状态按维度 key 索引；任务上下文持有维度对象，维度卸载即整体释放；
 * - 同一掉落物只执行优先级最高的一条命中规则，效果列表顺序全执行、异常隔离；
 * - delay_ticks / chance 由本层统一处理，执行器只做"做什么"。
 */
public final class ConversionRuntime {

    // 所有结算共用启动时读取的服务端产物准入配置。
    NearbyProductLimits nearbyProducts() {
        return config.nearbyProducts();
    }

    private static final Logger LOGGER = LogManager.getLogger();
    // 每秒刻数：trigger_after_seconds 与 tick 的换算
    static final int TICKS_PER_SECOND = 20;
    // 结算账本裁剪间隔：1200 tick = 60 秒（已完成记录按 tick 过期，未结清记录一律保留）
    private static final long SETTLEMENT_PRUNE_INTERVAL_TICKS = 1200L;
    // 概率落空的回执与固定输入原因：属已支付组内的正常结果
    private static final String CHANCE_MISSED = "chance_missed";

    private final ServerConfig config;
    private final BuiltinTypeRegistries types;
    private final LifespanProvider lifespanProvider;
    // 公共调度器：整个服务器唯一实例，所有维度共享同一份每 tick 时间与工作量预算
    private final ServerScheduler scheduler;
    // 催化剂预留表：任务级不落盘的运行期结构，停服时整体清空
    private final CatalystReservations catalystReservations = new CatalystReservations();
    private final Map<ResourceKey<Level>, LevelState> levels = new HashMap<>();
    // 规则发布：生效索引、发布代次与候选结构摘要全归它所有
    private final RulePublication publication;
    // 结算恢复队列：哪些记录在交付、哪些在等退避、重试与告警计数，全归它所有
    private final SettlementRecoveryQueue recoveries;
    // 维度回扫：已加载掉落物索引与分步回扫续接状态全归它所有
    private final WorldRescan rescan;
    // 转化门禁：一次到期判定的裁决逻辑，无状态，装配时注入候选与条件上下文两个查询
    private final ConversionGate gate;

    // 单个维度的运行时状态；任务本身归公共调度器所有，这里只保存缓存与追踪表
    private static final class LevelState {
        private final Map<UUID, TrackedState> tracked = new HashMap<>();
        // 已加载掉落物索引与回扫续接状态：登记、注销与分步回扫全归 WorldRescan
        private final WorldRescan.State rescan = new WorldRescan.State();
        private RuntimeTagLookup tags;
        private RuntimeClimateSampler climate;
    }

    // 追踪条目：检查句柄加上失败计数与到期补算标记；句柄进入终态表示排期链已断，需要重建追踪。
    private static final class TrackedState {
        private int failureCount;
        private boolean expiryPending;
        private ScheduledTaskHandle task;
        private boolean locked;
        // 下次到期判定的游戏刻（仅用于 debug 输出，-1 表示未排期）
        private long nextCheckTick = -1L;
    }

    /**
     * 掉落物追踪状态快照，供开发场景实时日志只读使用。
     * {@code taskTerminal} 用于核对追踪条目与调度句柄是否自洽：追踪存在但句柄已终态说明排期链已断。
     *
     * @param tracked 该掉落物当前是否仍被追踪
     * @param failureCount 条件检查的连续失败次数
     * @param locked 是否处于锁定状态（不再改动排期）
     * @param nextCheckTick 下次到期检查的游戏刻，-1 表示未排期
     * @param taskTerminal 追踪句柄是否已进入终态
     */
    public record TrackedItemInfo(boolean tracked, int failureCount, boolean locked, long nextCheckTick,
                                  boolean taskTerminal) {
    }

    /**
     * 运行期结算恢复队列快照，供开发场景与诊断命令断言恢复进度。
     *
     * @param queueSize 当前排队等待重试的记录数
     * @param attempts 累计已启动的恢复尝试次数
     * @param abandoned 超过尝试上限后放弃的次数
     * @param alerts 超限告警次数（每记录每次会话至多一次）
     * @param maxRetryDelayTicks 已使用的最大退避 tick
     */
    public record SettlementRecoveryStats(int queueSize, long attempts, long abandoned, long alerts,
                                          int maxRetryDelayTicks) {
    }

    /** 便捷构造：寿命提供器取原版默认值，自然消失时刻按原版寿命规则推算 */
    public ConversionRuntime(ServerConfig config, BuiltinTypeRegistries types) {
        this(config, types, LifespanProvider.vanillaDefault());
    }

    /** 构造运行时：调度器预算从 config 推导，维度状态在首次登记时按需创建 */
    public ConversionRuntime(ServerConfig config, BuiltinTypeRegistries types, LifespanProvider lifespanProvider) {
        this.config = config;
        this.types = types;
        this.lifespanProvider = lifespanProvider;
        this.scheduler = new ServerScheduler(SchedulerConfig.from(config));
        this.recoveries = new SettlementRecoveryQueue(config, scheduler, catalystReservations);
        this.rescan = new WorldRescan(config, scheduler,
                level -> {
                    LevelState state = levels.get(level.dimension());
                    return state == null ? null : state.rescan;
                },
                this::resetTracking, this::onItemAdded, this::trackedCount, recoveries);
        this.publication = new RulePublication(config, types, rescan, this::rescanLevel, this::reloadLevels);
        this.gate = new ConversionGate(config, types, lifespanProvider, this::candidatesFor, this::conditionContext);
    }

    /** 公共调度器，供开发场景读取跨维度预算与队列指标 */
    public ServerScheduler scheduler() {
        return scheduler;
    }

    // ========== 规则生命周期 ==========

    /**
     * 规则热替换：重建索引并清空全部追踪状态；启动等没有并发旧索引的链路使用。
     * 只换入，不排队回扫——启动期维度尚未就绪，由宿主逐维 {@link #rebuild(ServerLevel)} 做世界扫描。
     */
    public void replaceRules(List<LoadedRule<Rule>> rules, IssueCollector issues) {
        publication.replace(rules, issues);
    }

    /**
     * 预构建规则发布物：只读规则与注册表视图，构建新索引与候选结构摘要，
     * 不触碰在跑的索引、追踪表与任务队列，因此构建失败时旧规则仍完整可用。
     *
     * @param rules 已加载规则
     * @param issues 问题收集器：构建期问题记录在此，不改动在跑索引
     * @param registries 当前注册表视图，用于编码候选结构摘要
     * @return 尚未发布的索引与候选摘要
     */
    public PreparedRules prepareRules(List<LoadedRule<Rule>> rules, IssueCollector issues,
                                      HolderLookup.Provider registries) {
        return publication.prepare(rules, issues, registries);
    }

    /**
     * 发布预构建物：换入索引与候选结构摘要，逐维排队回扫重建追踪。数据包重载使用本入口。
     * 发布只做内存容器替换，不在方法内构建索引。
     *
     * @param server 当前服务端，用于定位已加载维度
     * @param prepared 已构建完成的发布物
     * @return 发布收尾观测任务；保存链路在公共预算内推进它，其余调用方可直接丢弃
     */
    public ServerTask publish(MinecraftServer server, PreparedRules prepared) {
        return publication.publish(server, prepared);
    }

    /**
     * 发布已解码索引：保存与启停链路只持有索引，候选结构摘要随整索引重建而整表失效。
     * 其余行为与 {@link #publish(MinecraftServer, PreparedRules)} 相同。
     *
     * @param server 当前服务端，用于定位已加载维度
     * @param prepared 完整校验后的索引
     * @return 发布收尾观测任务
     */
    public ServerTask publishDecoded(MinecraftServer server, RuleIndex prepared) {
        return publication.publishDecoded(server, prepared);
    }

    /** 当前生效的规则索引（replaceRules / publish / publishDecoded 时整体替换） */
    public RuleIndex index() {
        return publication.index();
    }

    /**
     * 预构建的规则发布物：新索引 + 每条可运行规则的候选结构摘要。
     * 对调用方不透明：只能在构建它的运行时上发布，发布时一次换入、不重新构建。
     */
    public static final class PreparedRules {
        private final RuleIndex index;
        private final Map<Rule, Integer> candidateVersions;

        PreparedRules(RuleIndex index, Map<Rule, Integer> candidateVersions) {
            this.index = index;
            this.candidateVersions = candidateVersions;
        }

        RuleIndex index() {
            return index;
        }

        Map<Rule, Integer> candidateVersions() {
            return candidateVersions;
        }
    }

    // ========== 事件入口（由平台层调用） ==========

    /** 掉落物进入世界：仅当存在候选规则时纳入追踪 */
    public void onItemAdded(ServerLevel level, ItemEntity entity) {
        LevelState state = stateOf(level);
        state.rescan.register(entity);
        if (publication.index().isEmpty() && RuntimeObserver.current().inactive(level.getServer())) { return; }
        if (excluded(entity)) {
            if (RuntimeObserver.current().enabled()) { RuntimeObserver.current().observe(entity, "EXCLUDED", "death_locked",
                    entity.getTags().contains(Constants.CHECK_LOCK_TAG), "unlimited_lifetime", entity.getAge() == -32768); }
            return;
        }
        // 已追踪则不再重复排期（ENTITY_LOAD 在区块重新追踪时会重复触发）；
        // 但句柄已终态说明排期链已断（失败或被取消），实体重新加载是重建追踪的时机，这里补排一次。
        TrackedState existing = state.tracked.get(entity.getUUID());
        if (existing != null) {
            if (existing.task == null || existing.task.terminal()) {
                List<Rule> candidates = candidatesFor(entity);
                if (candidates.isEmpty()) {
                    releaseTracking(state, entity.getUUID(), existing, CancelReason.TRACKING_RELEASED);
                } else {
                    scheduleInitial(level, entity, existing, candidates);
                }
            }
            return;
        }
        List<Rule> candidates = candidatesFor(entity);
        if (candidates.isEmpty()) {
            return;
        }
        TrackedState tracked = new TrackedState();
        state.tracked.put(entity.getUUID(), tracked);
        scheduleInitial(level, entity, tracked, candidates);
        if (RuntimeObserver.current().enabled()) { RuntimeObserver.current().observe(entity, "TRACKED", "next_check_tick", tracked.nextCheckTick,
                "candidates", candidates.size(), "lifespan_ticks", lifespanTicks(level, entity)); }
    }

    /** 环境致死入口（平台层只在真实致死分支调用）：先按伤害原因归类，不属于三类环境伤害时不请求 */
    public void requestEnvironmentalConversion(ServerLevel level, ItemEntity item, DamageSource source) {
        TriggerKind kind = DamageClassification.classify(source);
        if (kind != null) {
            requestConversion(level, item, kind);
        }
    }

    // 环境销毁的请求入口：补齐本刻追踪后交给 attempt 同步判定（自然消失由到期检查直接调用 attempt，不走本方法）。
    // 条件检查与规则选择在 hurt 调用栈内同步完成，单实体一次；效果一律经调度器排队，在共享 tick 预算内执行。
    private void requestConversion(ServerLevel level, ItemEntity item, TriggerKind kind) {
        if (item == null || kind == null || excluded(item)) {
            return;
        }
        LevelState state = stateOf(level);
        TrackedState tracked = state.tracked.get(item.getUUID());
        if (tracked == null) {
            // 同 tick 内新生成即被销毁等情形：按需补齐候选与排期；无候选规则则不转化
            onItemAdded(level, item);
            tracked = state.tracked.get(item.getUUID());
            if (tracked == null) {
                return;
            }
        }
        // 请求去重：同一掉落物正在转化时忽略重复请求（一次死亡可能带多份伤害调用）
        if (tracked.locked) {
            return;
        }
        if (RuntimeObserver.current().enabled()) { RuntimeObserver.current().observe(item, "ENV_DESTROY_REQUEST", "trigger", kind.key()); }
        attempt(level, item.getUUID(), kind, null);
    }

    /**
     * 每 tick 推进一次公共预算：所有维度的检查与效果共享同一份时间与工作量额度。
     * 时钟取主世界游戏刻（各维度 DerivedLevelData 共用同一值），所有维度按同一时刻推进。
     */
    public void onServerTick(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        scheduler.tick(gameTime);
        // 持久返还恢复：服务端已开始 tick 才尝试；未加载维度的记录保持不动（由回扫补扫），不空转重试
        recoveries.ensureScanned(server);
        // 周期性清理已完成结算记录：账本只保留近期完成记录与全部未结记录，避免无界增长
        if (gameTime % SETTLEMENT_PRUNE_INTERVAL_TICKS == 0L) {
            SettlementLedger.get(server.overworld()).prune(gameTime);
        }
    }

    // ========== 持久返还恢复 ==========

    // 结算在当前会话内失败时的重试入口：队列按记录去重并按退避重排，不必等到下次启动
    void enqueueRecovery(MinecraftServer server, String recordId, String reason) {
        recoveries.enqueue(server, recordId, reason);
    }

    /** 运行期结算恢复队列快照，供开发场景断言重试与告警。 */
    public SettlementRecoveryStats settlementRecoveryStats() {
        return recoveries.stats();
    }
    /** 在飞的返还恢复任务数观测 */
    public int activeRecoveryCount() {
        return recoveries.activeCount();
    }

    /** 观测：上次恢复扫描中因维度未加载而保持不动的记录数 */
    public long recoverySkippedDimensions() {
        return recoveries.skippedDimensionCount();
    }

    /**
     * 启动与维度加载回扫：这些实体的 join 事件可能早于运行时装配，必须主动扫描世界补登记。
     * 入口只收集一次已加载掉落物的引用快照（不改追踪状态），分步任务按快照切片登记，
     * 既不必每步重扫整维实体，也不跨 tick 持有世界迭代器。
     */
    public void rebuild(ServerLevel level) {
        rescan.rebuild(level, stateOf(level).rescan);
    }

    /** 维度卸载：整体释放该维度的状态与任务 */
    public void clear(ServerLevel level) {
        LevelState removed = levels.remove(level.dimension());
        if (removed != null) {
            removed.tracked.clear();
        }
        // 维度卸载：摘除该维度 realm，句柄交给清理任务逐槽取消；待返还库存由恢复任务补交
        scheduler.scheduleMaintenance(scheduler.detachRealm(level.dimension(), CancelReason.DIMENSION_UNLOAD), 0);
    }

    // 预留表访问入口：只返回引用；写操作只发生在结算任务与停服兜底里
    CatalystReservations catalystReservations() {
        return catalystReservations;
    }

    /** 服务端停止：释放全部状态 */
    public void shutdown() {
        for (LevelState state : levels.values()) {
            state.tracked.clear();
        }
        levels.clear();
        // 停服没有下一 tick：就地排空全部句柄，取消回调必须在最终存档前完成
        scheduler.drainAll(CancelReason.SERVER_STOP);
        // 预留只在运行期存在：停服清空即可，不产生任何世界写入或件数返还
        catalystReservations.clear();
        // 运行期恢复队列只在本次会话有效：停服清空，剩余记录留在账本由下次启动扫描接手
        recoveries.close();
        publication.close();
    }

    // ========== 统计（供开发场景实时日志与性能窗口使用） ==========

    /** 该维度当前被追踪的掉落物数量 */
    public int trackedCount(ServerLevel level) {
        LevelState state = levels.get(level.dimension());
        return state == null ? 0 : state.tracked.size();
    }

    /** 该维度是否仍有分步回扫在推进（供开发场景核对让出与最终一致性） */
    public boolean rescanPending(ServerLevel level) {
        return rescan.isPending(level);
    }

    /** 该维度最近一轮回扫已执行的分步数（供开发场景核对回扫确实分段推进） */
    public int rescanSteps(ServerLevel level) {
        return rescan.steps(level);
    }

    /** 单个掉落物的追踪状态（命令层只读） */
    public TrackedItemInfo debugInfo(ServerLevel level, ItemEntity entity) {
        LevelState state = levels.get(level.dimension());
        TrackedState tracked = state == null ? null : state.tracked.get(entity.getUUID());
        if (tracked == null) {
            return new TrackedItemInfo(false, 0, false, -1L, false);
        }
        return new TrackedItemInfo(true, tracked.failureCount, tracked.locked, tracked.nextCheckTick,
                tracked.task != null && tracked.task.terminal());
    }

    // 某个掉落物的候选规则（已按优先级 → 条件叶数 → 定义序排序），只服务本类的排期与门禁
    private List<Rule> candidatesFor(ItemEntity entity) {
        if (RuntimeObserver.current().enabled()) {
            // 接缝刻意返回未知元素类型：候选场景的语义是规则列表，在此按契约收窄一次
            @SuppressWarnings("unchecked")
            List<Rule> scene = (List<Rule>) RuntimeObserver.current().candidates(entity);
            if (scene != null) { return scene; }
        }
        return publication.index().candidates(itemIdOf(entity));
    }

    /** 该维度在调度器中尚未开始的任务数 */
    public int pendingTasks(ServerLevel level) {
        return scheduler.pendingCount(level.dimension());
    }

    /** 诊断复用实际平台寿命提供器，不能用通用兜底值冒充 NeoForge 当前寿命。 */
    public int lifespanTicks(ServerLevel level, ItemEntity item) {
        return Math.max(1, lifespanProvider.lifespanTicks(level, item));
    }

    /** 两类队列单独观测，压测时可定位是条件检查还是世界操作形成积压。 */
    public QueueStats queueStats(ServerLevel level, boolean effects) {
        return scheduler.queueStats(level.dimension(),
                effects ? ServerTaskKind.EFFECT : ServerTaskKind.CONDITION_CHECK);
    }

    /** 任意任务种类的单维度队列观测：把 checks/effects 两类口径扩到返还与维护等全部种类；只读，不改变调度行为 */
    public QueueStats queueStats(ServerLevel level, ServerTaskKind kind) {
        return scheduler.queueStats(level.dimension(), kind);
    }

    /** 跨维度合并的调度指标（含预算使用、耗尽原因、按种类与取消计数） */
    public SchedulerStats schedulerStats() {
        return scheduler.stats();
    }

    /** 最近一次推进的预算快照 */
    public ServerTickBudgetSnapshot budgetSnapshot() {
        return scheduler.budgetSnapshot();
    }

    // ========== 内部实现 ==========

    private LevelState stateOf(ServerLevel level) {
        ResourceKey<Level> key = level.dimension();
        LevelState state = levels.get(key);
        if (state != null) {
            return state;
        }
        // realm 只决定公平轮转与搬运分片，不持有独立预算
        scheduler.registerRealm(key);
        LevelState created = new LevelState();
        levels.put(key, created);
        return created;
    }

    // 逐维重建追踪：分步回扫由 WorldRescan 排队，这里只负责取（必要时创建）维度状态
    void rescanLevel(ServerLevel level) {
        rescan.rescan(level, stateOf(level).rescan);
    }

    // 换入后统一失效各维度的追踪与惰性缓存；摘除只按维度 realm，发布可能发生在保存任务自己的 APPLY 阶段内
    private void reloadLevels() {
        for (var entry : levels.entrySet()) {
            LevelState state = entry.getValue();
            state.tracked.clear();
            // 标签与气候缓存必须随 reload 失效，否则条件求值会读到重新加载前的标签成员
            state.tags = null;
            state.climate = null;
            // 本 tick 的维度轮转立即失效，取消量与取消回调都摊进后续 tick 的共享预算
            scheduler.scheduleMaintenance(scheduler.detachRealm(entry.getKey(), CancelReason.RULE_RELOAD), 0);
        }
    }

    // 回扫开始前重置追踪表与惰性条件上下文：旧实体引用与旧规则求值缓存都必须丢弃
    private void resetTracking(ServerLevel level) {
        LevelState state = stateOf(level);
        state.tracked.clear();
        state.tags = null;
        state.climate = null;
    }

    // 登记一次到期检查：到期 tick 按当前游戏刻推导为绝对值，保留原始时间不被改写。
    // 检查任务自带失败收尾：调度器先把句柄置终态再回调 onFailed，若这里只是普通 Runnable，
    // 追踪会继续指向一个已终态的句柄，物品既不再被检查、也永久拦住原版到期销毁。
    private ScheduledTaskHandle scheduleCheck(ServerLevel level, ItemEntity entity, int delayTicks) {
        long dueTick = level.getGameTime() + Math.max(0, delayTicks);
        return RuntimeObserver.current().schedule(entity, scheduler, level.dimension(), dueTick,
                new CheckTask(level, entity.getUUID()));
    }

    private static ResourceLocation itemIdOf(ItemEntity entity) {
        return BuiltInRegistries.ITEM.getKey(entity.getItem().getItem());
    }

    // 首次登记：触发时刻 = min(最早规则的 trigger_after_seconds, 自然消失前一刻)
    private void scheduleInitial(ServerLevel level, ItemEntity entity,
                                 TrackedState tracked, List<Rule> candidates) {
        int lifespan = Math.max(1, lifespanProvider.lifespanTicks(level, entity));
        long earliestTrigger = Long.MAX_VALUE;
        for (Rule rule : candidates) {
            earliestTrigger = Math.clamp((long) rule.triggerAfterSeconds() * TICKS_PER_SECOND, 0L, earliestTrigger);
        }
        long dueAge = Math.min(earliestTrigger, lifespan - 1L);
        long delay = Math.max(0, dueAge - entity.getAge());
        replaceCheck(level, entity, tracked, (int) Math.min(delay, Integer.MAX_VALUE), CancelReason.RESCHEDULED);
    }

    // 退避重试：条件不满足时不放弃，按退避序列重试直到自然消失
    private void reschedule(ServerLevel level, ItemEntity entity, TrackedState tracked, int delayTicks) {
        int remaining = Math.max(1, lifespanProvider.lifespanTicks(level, entity) - entity.getAge() - 1);
        delayTicks = Math.min(delayTicks, remaining);
        replaceCheck(level, entity, tracked, delayTicks, CancelReason.RESCHEDULED);
        if (RuntimeObserver.current().enabled()) { RuntimeObserver.current().observe(entity, "RETRY", "delay_ticks", delayTicks,
                "failure_count", tracked.failureCount, "next_check_tick", tracked.nextCheckTick); }
        if (config.debugLogging() && RuntimeObserver.current().allowsRuntimeLogging(entity)) {
            LOGGER.info("掉落物 {} 条件未满足，{} tick 后重试（第 {} 次失败）",
                    entity.getUUID(), delayTicks, tracked.failureCount);
        }
    }

    // 统一重排入口：先按重排原因取消旧句柄，再排新句柄。
    // 旧句柄可能是环境致死同步路径下仍在队列里的自然检查；只覆盖 tracked.task 会让它变成孤儿，
    // 到期后再跑一次判定，重复累加失败计数并把退避推高一档。
    private void replaceCheck(ServerLevel level, ItemEntity entity, TrackedState tracked, int delayTicks,
                              CancelReason cancelReason) {
        ScheduledTaskHandle previous = tracked.task;
        if (previous != null) {
            previous.cancel(cancelReason);
        }
        tracked.nextCheckTick = level.getGameTime() + delayTicks;
        tracked.task = scheduleCheck(level, entity, delayTicks);
    }

    // 追踪结束的统一出口：先取消句柄再移除条目，保证环境致死、自然到期释放等同步入口
    // 不会留下指向已结束追踪的孤儿检查。
    private void releaseTracking(LevelState state, UUID uuid, TrackedState tracked, CancelReason reason) {
        if (tracked.task != null) {
            tracked.task.cancel(reason);
        }
        state.tracked.remove(uuid);
    }

    // 一次到期检查的可失败载体：step 抛出异常时由调度器按任务终态收尾并回调 onFailed，
    // 失败上下文记录本次链路已确定的规则与结算标识，供结构化失败事件指到具体规则与结算。
    private final class CheckTask implements ServerTask {
        private final ServerLevel level;
        private final UUID uuid;
        private final FailureContext failure = new FailureContext();

        private CheckTask(ServerLevel level, UUID uuid) {
            this.level = level;
            this.uuid = uuid;
        }

        @Override
        public ServerTaskKind kind() {
            return ServerTaskKind.CONDITION_CHECK;
        }

        @Override
        public String tracingName() {
            return "check";
        }

        @Override
        public StepResult step(ServerTickBudget budget) {
            attempt(level, uuid, TriggerKind.NATURAL, failure);
            return StepResult.done(1);
        }

        @Override
        public void onFailed(@Nullable Throwable failure) {
            recoverCheckFailure(level, uuid, this.failure, failure);
        }
    }

    // 失败定位上下文：规则在判定命中时确定、结算标识在登记后才有，两者都只是事件字段来源，不改变业务状态
    private static final class FailureContext {
        private @Nullable String ruleId;
        private @Nullable String settlementId;
    }

    // 检查失败的守恒收尾：追踪要么重排到下一次检查，要么释放交还原版机制。
    // 失败发生在源物品扣减之前（准备阶段异常在 setCount(0) 之前抛出），所以这里无需返还库存；
    // 关键是让追踪不再指向已终态的句柄，避免物品既不再被检查、又永久拦住原版到期销毁。
    private void recoverCheckFailure(ServerLevel level, UUID uuid, FailureContext context, @Nullable Throwable failure) {
        LevelState state = levels.get(level.dimension());
        if (state == null) {
            return;
        }
        TrackedState tracked = state.tracked.get(uuid);
        if (tracked == null) {
            // 同一 tick 内环境致死、区块卸载等入口可能已释放追踪：失败恢复必须幂等
            return;
        }
        Entity entity = level.getEntity(uuid);
        if (!(entity instanceof ItemEntity item) || excluded(item)) {
            releaseTracking(state, uuid, tracked, CancelReason.ENTITY_REMOVED);
            return;
        }
        tracked.failureCount++;
        // 已到自然消失边缘（含拦截到期后等待下一次检查）：不再重试，直接释放追踪。
        // 拦截到期时的那次待办销毁在这里补上，否则超龄物品会因一次失败永久留在世界上。
        if (tracked.expiryPending || gate.pastExpiry(level, item)) {
            boolean deferredExpiry = tracked.expiryPending;
            releaseTracking(state, uuid, tracked, CancelReason.TRACKING_RELEASED);
            if (deferredExpiry) {
                item.discard();
            }
            observeCheckFailure(item, tracked, context, failure, "released", 0);
            return;
        }
        // 首次失败固定下一 tick 重试（一次异常不是业务结论）；连续失败沿用条件未满足的退避序列，防止热循环
        int delay = tracked.failureCount <= 1 ? 1 : Math.max(1, config.backoffTicks(tracked.failureCount));
        int remaining = Math.max(1, lifespanProvider.lifespanTicks(level, item) - item.getAge() - 1);
        delay = Math.min(delay, remaining);
        reschedule(level, item, tracked, delay);
        observeCheckFailure(item, tracked, context, failure, "retried", delay);
    }

    // 检查失败的结构化观测：原因、定位标识与恢复动作都要能独立核对，日志与场景断言共用同一组字段
    private void observeCheckFailure(ItemEntity item, TrackedState tracked, FailureContext context,
                                     @Nullable Throwable failure, String recovery, int retryDelayTicks) {
        if (!RuntimeObserver.current().enabled()) {
            return;
        }
        RuntimeObserver.current().observe(item, "CHECK_TASK_FAILED",
                "rule", context.ruleId == null ? "" : context.ruleId,
                "settlement", context.settlementId == null ? "" : context.settlementId,
                "reason", failure == null ? "failed_without_exception" : failure.toString(),
                "recovery", recovery,
                "retry_delay_ticks", retryDelayTicks,
                "failure_count", tracked.failureCount);
    }

    // 一次到期判定：选规则 → 执行；条件不满足则退避重试（kind 为本次请求的消失方式）
    // 调用点有两类：调度器到期检查（自然消失）与环境致死请求（在 hurt 调用栈内同步调用）；
    // 两者共用同一份追踪、预算与调度器，效果派发统一走 ConversionSettlement。
    private void attempt(ServerLevel level, UUID uuid, TriggerKind kind, @Nullable FailureContext context) {
        LevelState state = levels.get(level.dimension());
        if (state == null) {
            return;
        }
        TrackedState tracked = state.tracked.get(uuid);
        if (tracked == null) {
            return;
        }
        Entity entity = level.getEntity(uuid);
        if (!(entity instanceof ItemEntity item) || excluded(item)) {
            releaseTracking(state, uuid, tracked, CancelReason.ENTITY_REMOVED);
            return;
        }
        ConversionGate.Tracked snapshot = new ConversionGate.Tracked(tracked.locked, tracked.failureCount, tracked.expiryPending);
        applyDecision(level, state, item, tracked, context, gate.decide(level, item, kind, snapshot));
    }

    // 门禁裁决的执行端：追踪状态与调度器只在这里被写，判定本身留在 ConversionGate。
    private void applyDecision(ServerLevel level, LevelState state, ItemEntity item, TrackedState tracked,
                               @Nullable FailureContext context, ConversionGate.Decision decision) {
        switch (decision) {
            case ConversionGate.Convert convert -> {
                performConversion(level, state, item, tracked, convert.rule(), context);
                if (convert.discardAfter() && !item.isRemoved()) { item.discard(); }
            }
            case ConversionGate.Wait wait -> {
                // 先推退避档再重排：门禁已按「本次失败之后」的档位算出延迟，重排日志读到的计数必须与之同档
                if (wait.countsAsFailure()) { tracked.failureCount++; }
                reschedule(level, item, tracked, wait.delayTicks());
            }
            case ConversionGate.Release release -> {
                releaseTracking(state, item.getUUID(), tracked, release.reason());
                if (release.discardEntity()) { item.discard(); }
            }
            case ConversionGate.Idle ignored -> { }
        }
    }

    // 执行一条规则：计划期把整堆源物品转入结算库存，随后由 ConversionSettlement 按组支付成本并交付返还。
    // 「扣减 → 登记 → 调度注册」是一个补偿单元：任一步抛异常都把整堆库存写回源实体并撤销登记，
    // 只有全部成功才把所有权交给结算任务（移除所有权追踪，后续交付与恢复依据由记录承担）。
    private void performConversion(ServerLevel level, LevelState state, ItemEntity item,
                                   TrackedState tracked, Rule rule, @Nullable FailureContext context) {
        ItemStack source = item.getItem();
        int available = source.getCount();
        if (available <= 0) {
            releaseTracking(state, item.getUUID(), tracked, CancelReason.TRACKING_RELEASED);
            return;
        }
        // 规则在本方法入口即已确定：任何后续阶段的失败事件都应能指到具体规则
        if (context != null) {
            context.ruleId = rule.id().toString();
        }
        // 每组固定源成本 c：显式 source_cost 优先，未声明时为 1 个
        int costPerGroup = types.perRoundSourceConsumption(rule, source);
        tracked.locked = true;
        // 成功转化当刻的触发点与源速度：都必须在源实体清空前读取，清空后位置与运动都不再可信
        Vec3 triggerPosition = item.position();
        Vec3 triggerVelocity = item.getDeltaMovement();
        // 源实体清空前要固定的效果输入：概率在此判定一次，结果随任务携带，清空后不再重判。
        // 判定异常按同一补偿单元处理：整堆源物品写回并保留所有权追踪。
        Map<PreparedEffectKey, EffectPreparation> preparations;
        // 领先准备的组数：按可用源算出的组数上界只是理论值，实际准备只做本步允许的组数；
        // 其余组的固定输入由结算任务在 PREPARING 阶段分组续接，大堆叠的重活不会全压在检查步骤里
        int leadGroups = Math.min(costPerGroup > 0 ? available / costPerGroup : 1, scheduler.config().prepareGroupsPerStep());
        try {
            preparations = prepareEffects(level, item, rule, triggerPosition, leadGroups, source.copy());
        } catch (RuntimeException failure) {
            tracked.locked = false;
            // 准备阶段异常发生在源物品扣减之前，守恒天然成立；仍需结构化事件把规则与原因定位下来，
            // 否则一次准备失败在观测侧只剩调度器日志，无法核对是哪条规则、哪个效果出的问题
            if (RuntimeObserver.current().enabled()) {
                RuntimeObserver.current().observe(item, "CONVERSION_PREPARE_FAILED",
                        "rule", rule.id().toString(), "settlement", "", "reason", failure.toString());
            }
            throw failure;
        }
        // 首次登记与失败补偿都经受控句柄：账目变更与账本双索引、标脏在同一次调用内完成
        SettlementRecord.Writer writer = null;
        boolean transferred = false;
        try {
            // 所有权转移：整堆进入结算库存，源实体本刻清空（扣减时间点保持在源实体清空的本刻）
            ItemStack held = source.copy();
            source.setCount(0);
            item.addTag(Constants.CONVERTED_TAG);
            // 记录源位置：重启后源实体已不存在，返还交付只能以记录位置为搜索起点
            writer = SettlementRecord.Writer.register(SettlementLedger.get(level), UUID.randomUUID().toString(),
                    rule.id().toString(), level.dimension().location().toString(), held, available, costPerGroup,
                    true, item.getX(), item.getY(), item.getZ());
            // 登记之后才有结算标识：此后的失败事件据此指到具体结算，便于与账本核对
            if (context != null) {
                context.settlementId = writer.record().id();
            }
            if (RuntimeObserver.current().enabled()) {
                int estimated = costPerGroup > 0 ? Math.max(1, available / costPerGroup) : 1;
                RuntimeObserver.current().converted(item, rule.id().toString(), estimated);
            }
            ConversionSettlement settlement = new ConversionSettlement(this, scheduler, level, item, rule, held, writer,
                    triggerPosition, triggerVelocity, preparations, leadGroups);
            scheduler.scheduleAt(level.dimension(), settlement, level.getGameTime());
            transferred = true;
            VisualFeedback.conversion(level, item.position());
            if (config.debugLogging() && RuntimeObserver.current().allowsRuntimeLogging(item)) {
                LOGGER.info("掉落物 {} 已按规则 {} 进入结算（可用源 {}，每组成本 {}）",
                        item.getUUID(), rule.id(), available, costPerGroup);
            }
        } finally {
            tracked.locked = false;
            if (transferred) {
                // 结算任务接手所有权，检查句柄随追踪一起释放：环境致死同步路径此前挂起的检查
                // 可能仍在队列里，只移除追踪会让它变成孤儿并在后续 tick 空跑一次。
                releaseTracking(state, item.getUUID(), tracked, CancelReason.TRACKING_RELEASED);
            } else {
                // 补偿：撤销登记（恢复任务不得再交付这堆库存）、撤销「已转化」标记，
                // 再把整堆源物品写回源实体并保留所有权追踪，等待下一次转化机会
                if (writer != null) {
                    writer.discard();
                }
                item.removeTag(Constants.CONVERTED_TAG);
                source.setCount(available);
            }
        }
    }

    // 概率只在开始时判断一次，续接状态归本次效果任务独占。
    // preparation 非空表示本组输入已在成功转化当刻固定（含概率判定结果），本方法不再重判、也不再读源实体；
    // 为空的只有未声明准备阶段的类型，它们保持原有的首次执行时判定时机。
    EffectExecution startEffect(ItemEntity item, EffectContext base, Effect effect,
                                @Nullable EffectPreparation preparation) {
        // 已判定不执行的固定输入：直接给出跳过回执，不重判也不做世界写入
        if (preparation != null && preparation.gate() != null) {
            return gatedExecution(preparation.gate());
        }
        // 成功转化当刻已固定输入时不就地重判概率；未固定的类型保持原有判定时机
        boolean preEvaluated = preparation != null;
        if (!preEvaluated) {
            if (effect.chance() < 1.0D && base.random().nextDouble() >= effect.chance()) {
                if (RuntimeObserver.current().enabled()) { RuntimeObserver.current().observe(item, "EFFECT_CHANCE_SKIPPED", "effect", effect.type(), "chance", effect.chance()); }
                // 概率落空仍支付本组固定成本：不免费重试、不返还
                return budget -> EffectStep.done(EffectResult.skipped(CHANCE_MISSED));
            }
        }
        EffectType<?> definition = types.effectTypes().getOrNull(effect.type());
        if (definition == null) {
            if (RuntimeObserver.current().enabled()) { RuntimeObserver.current().observe(item, "ERROR", "effect", effect.type(), "reason", "type_not_registered"); }
            LOGGER.error("规则 {} 引用了未注册的效果类型 {}", base.ruleId(), effect.type());
            return budget -> EffectStep.done(EffectResult.failed("type_not_registered"));
        }
        try {
            if (RuntimeObserver.current().enabled()) { RuntimeObserver.current().observe(item, "EFFECT_BEGIN", "effect", effect.type()); }
            EffectExecution execution = executorOf(definition).start(effect, base, preparation);
            return execution == null ? budget -> EffectStep.done(EffectResult.failed("no_execution")) : execution;
        } catch (RuntimeException e) {
            if (RuntimeObserver.current().enabled()) { RuntimeObserver.current().observe(item, "ERROR", "effect", effect.type(), "error", e.toString()); }
            LOGGER.error("效果执行失败：规则={} 效果={} 位置={}", base.ruleId(), effect.type(), base.position(), e);
            return budget -> EffectStep.done(EffectResult.failed(String.valueOf(e)));
        }
    }

    // 已判定为不执行的固定输入：直接给出带原因的跳过回执，不做世界写入也不重判
    private static EffectExecution gatedExecution(String reason) {
        return budget -> EffectStep.done(EffectResult.skipped(reason));
    }

    // 成功转化当刻为「需要先固定输入」的类型逐槽生成固定输入：概率在这里判定一次，
    // 结果随结算任务携带，源实体清空后不再重新判定。没有准备阶段的类型不产出，保持原有判定时机。
    // preparedGroups 只是领先准备的组数上界，未覆盖的组由结算任务按组续接准备。
    private Map<PreparedEffectKey, EffectPreparation> prepareEffects(ServerLevel level, ItemEntity item, Rule rule,
                                                                    Vec3 position, int preparedGroups,
                                                                    ItemStack sourceSnapshot) {
        Map<PreparedEffectKey, EffectPreparation> preparations = new HashMap<>();
        // 组间共享的固定输入：同一候选同一槽位只保留首份，各组共用它承载执行期的跨组状态
        PreparedEffectSharing sharedPreparations = new PreparedEffectSharing();
        if (preparedGroups <= 0) {
            return preparations;
        }
        for (OutcomeCandidate candidate : rule.effectiveOutcomes()) {
            List<Effect> effects = candidate.effects();
            for (int slot = 0; slot < effects.size(); slot++) {
                Effect effect = effects.get(slot);
                EffectType<?> definition = types.effectTypes().getOrNull(effect.type());
                if (definition == null) {
                    continue;
                }
                // 一次性效果同一源只尝试一组，固定一份即可；其余类型按潜在组各固定一份，
                // 概率判定互不复用，声明可共享的固定输入在组间复用首份
                int groups = definition.oneShot() ? 1 : preparedGroups;
                for (int group = 0; group < groups; group++) {
                    EffectPreparation preparation = prepareEffectSlot(definition, effect, level, item, position,
                            sourceSnapshot, candidate.fillOrigin());
                    if (preparation == null) {
                        // 未迁移的类型不在成功转化当刻固定，其概率仍在首次执行时判定
                        break;
                    }
                    PreparedEffectKey key = new PreparedEffectKey(candidate.id(), slot, group);
                    preparation = sharedPreparations.share(key, preparation);
                    preparations.put(key, preparation);
                }
            }
        }
        return preparations;
    }

    // 泛型桥接：准备入口只读效果参数与成功当刻可读的输入。
    // 结算任务的 PREPARING 阶段按组续接准备时复用同一入口，保证两处的概率与固定输入口径一致。
    @SuppressWarnings("unchecked")
    @Nullable EffectPreparation prepareEffectSlot(EffectType<?> definition, Effect effect, ServerLevel level,
                                                 ItemEntity item, Vec3 position,
                                                 ItemStack sourceSnapshot, boolean fillOrigin) {
        // 准备阶段注入点：注入失败必须沿转化补偿单元抛出，由检查任务的失败收尾释放或重排追踪
        if (RuntimeObserver.current().enabled()) {
            RuntimeObserver.current().effectPreparing(level.getServer(), effect.type());
        }
        EffectPreparation preparation = ((EffectType<Effect>) definition).executor()
                .prepare(effect, level, position, item, sourceSnapshot, fillOrigin);
        // 一次固定输入的准备本身就是一次真实开销（例如开战利品表）：按 1 个工作单位上报，
        // 让准备量进入调度器计价，而不是被当成零成本勾当
        StepWorkCounter.report(1);
        if (preparation == null || preparation.gate() != null) {
            return preparation;
        }
        // 概率落空在成功转化当刻就已确定：结果随任务携带，不被后续世界变化改写
        if (effect.chance() < 1.0D && level.random.nextDouble() >= effect.chance()) {
            if (RuntimeObserver.current().enabled()) { RuntimeObserver.current().observe(item, "EFFECT_CHANCE_SKIPPED", "effect", effect.type(), "chance", effect.chance()); }
            // 概率落空仍支付本组固定成本：不免费重试、不返还
            return EffectPreparation.gated(CHANCE_MISSED);
        }
        return preparation;
    }

    // 成功转化当刻固定的效果键：候选、候选内槽序号与组序号，逐组独立，不跨组复用随机判定
    record PreparedEffectKey(String candidateId, int slot, int groupIndex) {
    }

    // 泛型桥接：效果即类型定义声明的 P，执行器只读取参数
    @SuppressWarnings("unchecked")
    private static EffectExecutor<Effect> executorOf(EffectType<?> definition) {
        return ((EffectType<Effect>) definition).executor();
    }

    // 效果类型定义查询：结算层做容量规划时据此判断一次性效果与参数
    EffectType<?> effectDefinition(ResourceLocation type) {
        return types.effectTypes().getOrNull(type);
    }

    // 候选结构摘要（结算层建轮询游标时读取）：同一规则实例只编码一次，规则索引重建即失效
    int candidateStructureVersion(Rule rule, HolderLookup.Provider registries) {
        return publication.versionOf(rule, registries);
    }

    // 规则级条件上下文：标签与气候采样对象按维度复用（各自带缓存）
    private ConditionContext conditionContext(ServerLevel level, ItemEntity item) {
        LevelState state = stateOf(level);
        if (state.tags == null) {
            state.tags = new RuntimeTagLookup(level);
        }
        if (state.climate == null) {
            state.climate = new RuntimeClimateSampler(level);
        }
        return new RuntimeConditionContext(level, item, item.blockPosition(), level.random, state.tags, state.climate);
    }

    // 排除死亡掉落、已提交转化的实体和原版无限寿命物品。
    private static boolean excluded(ItemEntity item) {
        return item.isRemoved() || item.getItem().isEmpty() || item.getAge() == -32768
                || item.getTags().contains(Constants.CHECK_LOCK_TAG)
                || item.getTags().contains(Constants.CONVERTED_TAG);
    }

    /** 离开维度或区块卸载时立即取消检查，释放任务捕获对象。 */
    public void onItemRemoved(ServerLevel level, ItemEntity item) {
        if (RuntimeObserver.current().enabled()) { RuntimeObserver.current().observe(item, "REMOVED", "reason", item.getRemovalReason()); }
        LevelState state = levels.get(level.dimension());
        if (state == null) { return; }
        state.rescan.unregister(item);
        TrackedState tracked = state.tracked.get(item.getUUID());
        if (tracked != null) {
            releaseTracking(state, item.getUUID(), tracked, CancelReason.ENTITY_REMOVED);
        }
    }

    /** 自然消失入口只预留最后一次有预算的检查，不在实体 tick 内执行昂贵效果。 */
    public boolean deferNaturalExpiry(ServerLevel level, ItemEntity item) {
        if (excluded(item)) { return false; }
        LevelState state = levels.get(level.dimension());
        // 回扫可能延后数刻；临近原版消失的物品按新索引及时补追踪，避免等待回扫期间漏转化。
        if (state == null) { return false; }
        if (state.rescan.isPending()) { onItemAdded(level, item); }
        TrackedState tracked = state.tracked.get(item.getUUID());
        if (tracked == null) { return false; }
        // 转化提交当刻追踪即被释放，因此 tracked 存在等价于尚未提交转化。
        // 拦截只对有效追踪成立：句柄已终态说明排期链已断（检查失败或被取消），继续拦截会让
        // 超龄物品永久滞留；释放追踪交还原版销毁，至少按自然寿命消失。
        if (tracked.task == null || tracked.task.terminal()) {
            releaseTracking(state, item.getUUID(), tracked, CancelReason.TRACKING_RELEASED);
            return false;
        }
        if (!tracked.expiryPending) {
            if (RuntimeObserver.current().enabled()) { RuntimeObserver.current().observe(item, "NATURAL_EXPIRY_DEFERRED"); }
            tracked.expiryPending = true;
            replaceCheck(level, item, tracked, 0, CancelReason.NATURAL_EXPIRY_DEFERRED);
        }
        return true;
    }
}
