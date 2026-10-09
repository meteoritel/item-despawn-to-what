package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.debug.DebugMode;
import com.meteorite.itemdespawntowhat.core.debug.DebugScenarioManager;
import com.meteorite.itemdespawntowhat.core.api.ConditionContext;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecutor;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.config.ServerConfig;
import com.meteorite.itemdespawntowhat.core.config.NearbyProductLimits;
import com.meteorite.itemdespawntowhat.core.load.LoadedRule;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.model.TriggerKind;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.QueueStats;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ScheduledTask;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.SchedulerConfig;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.SchedulerStats;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerScheduler;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerTickBudgetSnapshot;
import com.meteorite.itemdespawntowhat.core.service.BuiltinTypeRegistries;
import com.meteorite.itemdespawntowhat.core.state.DamageClassification;
import com.meteorite.itemdespawntowhat.core.state.DropState;
import com.meteorite.itemdespawntowhat.core.state.DropStateStore;
import net.minecraft.core.BlockPos;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 转化运行时：规则索引 + 维度级追踪与调度。
 * 设计要点（对应规划书 Q12/Q13/Q22/Q38/Q39）：
 * - 触发时刻 = min(trigger_after_seconds, 自然消失时刻)，在自然 discard 之前判定；
 * - 计时按**绝对存活时间**，条件不满足则退避重试（1s→2s→4s→封顶）；
 * - 状态按维度 key 索引；任务上下文持有维度对象，维度卸载即整体释放；
 * - 同一掉落物只执行优先级最高的一条命中规则，效果列表顺序全执行、异常隔离；
 * - delay_ticks / chance / 效果级 conditions 由本层统一处理，执行器只做"做什么"。
 */
public final class ConversionRuntime {

    // 所有结算共用启动时读取的服务端产物准入配置。
    NearbyProductLimits nearbyProducts() {
        return config.nearbyProducts();
    }

    private static final Logger LOGGER = LogManager.getLogger();
    // 每秒刻数：trigger_after_seconds 与 tick 的换算
    private static final int TICKS_PER_SECOND = 20;
    // 结算账本裁剪间隔：1200 tick = 60 秒（已完成的旧记录按 tick 过期，未结清记录一律保留）
    private static final long SETTLEMENT_PRUNE_INTERVAL_TICKS = 1200L;

    private final ServerConfig config;
    private final BuiltinTypeRegistries types;
    private final LifespanProvider lifespanProvider;
    // 公共调度器：整个服务器唯一实例，所有维度共享同一份每 tick 时间与工作量预算（ADR-0002）
    private final ServerScheduler scheduler;
    // 催化剂预留表：任务级不落盘的运行期结构，停服时整体清空（D31）
    private final CatalystReservations catalystReservations = new CatalystReservations();
    private final Map<ResourceKey<Level>, LevelState> levels = new HashMap<>();
    private volatile RuleIndex index = RuleIndex.empty();
    // 恢复幂等位：同一结算记录同一时刻只允许一个待返还交付任务，重复扫描/重复入队都不会多发
    private final Set<String> activeRecoveries = new HashSet<>();
    // 首次服务端 tick 后做一次全局恢复扫描；之后新加载的维度由 rescan 补扫
    private boolean recoveryScanned;
    // 上次扫描中因维度未加载而保持不动的记录数（低频观测，阶段7 使用）
    private long recoverySkippedDimensions;

    // 单个维度的运行时状态；任务本身归公共调度器所有，这里只保存缓存与追踪表
    private static final class LevelState {
        private final Map<UUID, TrackedState> tracked = new HashMap<>();
        private RuntimeTagLookup tags;
        private RuntimeClimateSampler climate;
    }

    // 单个掉落物的追踪状态
    private static final class TrackedState {
        private int failureCount;
        private boolean expiryPending;
        private ScheduledTask task;
        private boolean locked;
        // 下次到期判定的游戏刻（仅用于 debug 输出，-1 表示未排期）
        private long nextCheckTick = -1L;
    }

    // 掉落物追踪状态快照，供开发场景实时日志只读使用
    public record TrackedItemInfo(boolean tracked, int failureCount, boolean locked, long nextCheckTick) {
    }

    public ConversionRuntime(ServerConfig config, BuiltinTypeRegistries types) {
        this(config, types, LifespanProvider.vanillaDefault());
    }

    public ConversionRuntime(ServerConfig config, BuiltinTypeRegistries types, LifespanProvider lifespanProvider) {
        this.config = config;
        this.types = types;
        this.lifespanProvider = lifespanProvider;
        this.scheduler = new ServerScheduler(SchedulerConfig.from(config));
    }

    // 公共调度器，供开发场景读取跨维度预算与队列指标
    public ServerScheduler scheduler() {
        return scheduler;
    }

    // ========== 规则生命周期 ==========

    // 规则热替换：重建索引并清空全部追踪状态（由随后的 rescan 重建）
    public void replaceRules(List<LoadedRule<Rule>> rules, IssueCollector issues) {
        this.index = RuleIndex.build(rules, issues);
        for (LevelState state : levels.values()) {
            state.tracked.clear();
            // 标签与气候缓存必须随 reload 失效，否则条件求值会读到旧数据包的标签成员
            state.tags = null;
            state.climate = null;
        }
        // 旧实现只清检查队列，效果队列会跨 reload 残留；现在按维度统一带原因取消
        for (ResourceKey<Level> key : levels.keySet()) {
            scheduler.cancelRealm(key, CancelReason.RULE_RELOAD);
        }
        if (config.debugLogging()) {
            LOGGER.info("规则索引已重建：{} 条可运行规则", index.ordered().size());
        }
    }

    public RuleIndex index() {
        return index;
    }

    // ========== 事件入口（由平台层调用） ==========

    // 掉落物进入世界：仅当存在候选规则时纳入追踪
    public void onItemAdded(ServerLevel level, ItemEntity entity) {
        if (index.isEmpty() && DebugScenarioManager.inactive(level.getServer())) { return; }
        if (excluded(entity)) {
            if (DebugMode.ENABLED) { DebugScenarioManager.observe(entity, "EXCLUDED", "death_locked",
                    entity.getTags().contains(Constants.CHECK_LOCK_TAG), "unlimited_lifetime", entity.getAge() == -32768); }
            return;
        }
        List<Rule> candidates = candidatesFor(entity);
        if (candidates.isEmpty()) {
            return;
        }
        LevelState state = stateOf(level);
        // 已追踪则不再重复排期（ENTITY_LOAD 在区块重新追踪时会重复触发）
        if (state.tracked.containsKey(entity.getUUID())) {
            return;
        }
        TrackedState tracked = new TrackedState();
        state.tracked.put(entity.getUUID(), tracked);
        scheduleInitial(level, entity, tracked, candidates);
        if (DebugMode.ENABLED) { DebugScenarioManager.observe(entity, "TRACKED", "next_check_tick", tracked.nextCheckTick,
                "candidates", candidates.size(), "lifespan_ticks", lifespanTicks(level, entity)); }
    }

    // 环境致死入口（平台层只在真实致死分支调用）：先按伤害原因归类，不属于三类环境伤害时不请求
    public void requestEnvironmentalConversion(ServerLevel level, ItemEntity item, DamageSource source) {
        TriggerKind kind = DamageClassification.classify(source);
        if (kind != null) {
            requestConversion(level, item, kind);
        }
    }

    // 公共请求入口：自然消失与三类环境销毁共用同一条检查/执行路径（PLAN §4.4）
    // 环境致死时本方法在 hurt 调用栈内同步执行「条件检查 + 规则选择」（单实体一次，量小）；
    // 效果一律经调度器排队并在共享 tick 预算内执行（见 ConversionSettlement#dispatchEffect）。这是阶段 3 的设计选择，
    // 环境集中销毁是否把峰值转移到这里留阶段 7 观测。
    public void requestConversion(ServerLevel level, ItemEntity item, TriggerKind kind) {
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
        if (DebugMode.ENABLED) { DebugScenarioManager.observe(item, "ENV_DESTROY_REQUEST", "trigger", kind.key()); }
        attempt(level, item.getUUID(), kind);
    }

    // 每 tick 推进一次公共预算：所有维度的检查与效果共享同一份时间与工作量额度。
    // 时钟取主世界游戏刻（各维度 DerivedLevelData 共用同一值），与旧的按维度推进时刻一致。
    public void onServerTick(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        scheduler.tick(gameTime);
        SettlementLedger ledger = SettlementLedger.get(server.overworld());
        // 持久返还恢复：服务端已开始 tick 才尝试；未加载维度的记录保持不动（由 rescan 补扫），不空转重试
        if (!recoveryScanned) {
            recoveryScanned = true;
            recoverSettlements(server, ledger);
        }
        // 周期性清理已完成结算记录：账本只保留近期完成记录与全部未结记录，避免无界增长
        if (gameTime % SETTLEMENT_PRUNE_INTERVAL_TICKS == 0L) {
            ledger.prune(gameTime);
        }
    }

    // ========== 阶段6：持久返还恢复 ==========

    // 全局恢复扫描：只交付记录里的待返还库存，不恢复旧效果队列；维度未加载的记录保持不动
    private void recoverSettlements(MinecraftServer server, SettlementLedger ledger) {
        int skipped = 0;
        for (SettlementRecord record : ledger.unsettled()) {
            if (activeRecoveries.contains(record.id())) {
                continue;
            }
            ServerLevel level = levelOf(server, record.dimension());
            if (level == null) {
                // 维度未加载：记录留在账本等待该维度加载时由 rescan 补扫，这里不重试、不空转
                skipped++;
                continue;
            }
            startRecovery(level, ledger, record);
        }
        recoverySkippedDimensions = skipped;
        if (skipped > 0 && config.debugLogging()) {
            LOGGER.info("返还恢复扫描：{} 条待返还记录所在维度尚未加载，保持在账本中等待", skipped);
        }
    }

    // 按记录里的维度 key 找已加载维度；未加载返回 null
    private ServerLevel levelOf(MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimension)) {
                return level;
            }
        }
        return null;
    }

    // 单维度恢复扫描：同一记录重复入队由 activeRecoveries 幂等位拦住
    private void recoverLevel(ServerLevel level, SettlementLedger ledger) {
        String dimension = level.dimension().location().toString();
        for (SettlementRecord record : ledger.unsettled(dimension)) {
            if (activeRecoveries.contains(record.id())) {
                continue;
            }
            startRecovery(level, ledger, record);
        }
    }

    // 交付任务入队：REBATE 车道；世界写入只会在 scheduler.tick 推进时发生且受公共预算约束。
    // 口径（lead 裁决 A）：停机或维度卸载时未交付的库存（含尚未开始组的源库存）不在停服瞬间返还，
    // 而是作为待返还记录留在账本里，于下一次服务端启动、且目标维度已加载后由本路径交付。
    private void startRecovery(ServerLevel level, SettlementLedger ledger, SettlementRecord record) {
        // 重启后源实体已不存在：位置取自记录；旧存档缺位置字段时退回该维度出生点
        Vec3 position = record.hasPosition()
                ? new Vec3(record.positionX(), record.positionY(), record.positionZ())
                : Vec3.atBottomCenterOf(level.getSharedSpawnPos());
        SettlementRecovery recovery = new SettlementRecovery(this, scheduler, level, record, ledger, position);
        activeRecoveries.add(record.id());
        scheduler.scheduleAt(level.dimension(), recovery, level.getGameTime());
        LOGGER.debug("返还恢复已入队：记录={} 维度={} 待交付={} 位置={}",
                record.id(), record.dimension(), record.pendingDelivery(), position);
    }

    // 恢复任务收尾（交付完成或取消）后释放幂等位，允许后续扫描重新入队剩余库存
    void onRecoveryFinished(String id) {
        activeRecoveries.remove(id);
    }

    // 在飞的返还恢复任务数（阶段7 观测）
    public int activeRecoveryCount() {
        return activeRecoveries.size();
    }

    // 上次恢复扫描中因维度未加载而保持不动的记录数（阶段7 观测）
    public long recoverySkippedDimensions() {
        return recoverySkippedDimensions;
    }

    // reload 后回扫已加载实体重建追踪（修复热重载对已存在掉落物无效）
    public void rescan(ServerLevel level) {
        LevelState state = stateOf(level);
        state.tracked.clear();
        // 重扫会重建追踪，先按维度取消旧任务（检查与效果一起），不留静默丢弃
        scheduler.cancelRealm(level.dimension(), CancelReason.RULE_RELOAD);
        // 维度加载/重扫时补扫该维度的待返还记录：只交付库存，不恢复旧效果队列（与规则内容无关）
        recoverLevel(level, SettlementLedger.get(level));
        if (index.isEmpty() && DebugScenarioManager.inactive(level.getServer())) {
            return;
        }
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ItemEntity item && !item.isRemoved()) {
                onItemAdded(level, item);
            }
        }
        if (config.debugLogging()) {
            LOGGER.info("回扫完成：维度 {} 现有 {} 个被追踪掉落物", level.dimension().location(), state.tracked.size());
        }
    }

    // 维度卸载：整体释放该维度的状态与任务
    public void clear(ServerLevel level) {
        LevelState removed = levels.remove(level.dimension());
        if (removed != null) {
            removed.tracked.clear();
        }
        scheduler.releaseRealm(level.dimension(), CancelReason.DIMENSION_UNLOAD);
    }

    // 预留表访问入口：只返回引用；写操作只发生在结算任务与停服兜底里
    CatalystReservations catalystReservations() {
        return catalystReservations;
    }

    // 服务端停止：释放全部状态
    public void shutdown() {
        for (LevelState state : levels.values()) {
            state.tracked.clear();
        }
        levels.clear();
        scheduler.clear(CancelReason.SERVER_STOP);
        // 预留只在运行期存在：停服清空即可，不产生任何世界写入或件数返还
        catalystReservations.clear();
        activeRecoveries.clear();
        recoveryScanned = false;
        index = RuleIndex.empty();
    }

    // ========== 统计（供开发场景实时日志与性能窗口使用） ==========

    public int trackedCount(ServerLevel level) {
        LevelState state = levels.get(level.dimension());
        return state == null ? 0 : state.tracked.size();
    }

    // 单个掉落物的追踪状态（命令层只读）
    public TrackedItemInfo debugInfo(ServerLevel level, ItemEntity entity) {
        LevelState state = levels.get(level.dimension());
        TrackedState tracked = state == null ? null : state.tracked.get(entity.getUUID());
        if (tracked == null) {
            return new TrackedItemInfo(false, 0, false, -1L);
        }
        return new TrackedItemInfo(true, tracked.failureCount, tracked.locked, tracked.nextCheckTick);
    }

    // 某个掉落物的候选规则（已按优先级 → 条件叶数 → 定义序排序），供 /idtw debug why 使用
    public List<Rule> candidatesFor(ItemEntity entity) {
        if (DebugMode.ENABLED) {
            List<Rule> scene = DebugScenarioManager.candidates(entity);
            if (scene != null) { return scene; }
        }
        return index.candidates(itemIdOf(entity));
    }

    public int pendingTasks(ServerLevel level) {
        return scheduler.pendingCount(level.dimension());
    }

    // 诊断复用实际平台寿命提供器，不能用通用兜底值冒充 NeoForge 当前寿命。
    public int lifespanTicks(ServerLevel level, ItemEntity item) {
        return Math.max(1, lifespanProvider.lifespanTicks(level, item));
    }

    // 两类队列单独观测，压测时可定位是条件检查还是世界操作形成积压。
    public QueueStats queueStats(ServerLevel level, boolean effects) {
        return scheduler.queueStats(level.dimension(),
                effects ? ServerTaskKind.EFFECT : ServerTaskKind.CONDITION_CHECK);
    }

    // 任意任务种类的单维度队列观测（阶段7：把 checks/effects 两类口径扩到返还与维护等全部种类）；只读，不改变调度行为
    public QueueStats queueStats(ServerLevel level, ServerTaskKind kind) {
        return scheduler.queueStats(level.dimension(), kind);
    }

    // 跨维度合并的调度指标（含预算使用、耗尽原因、按种类与取消计数）
    public SchedulerStats schedulerStats() {
        return scheduler.stats();
    }

    // 最近一次推进的预算快照
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

    // 登记一次到期检查：到期 tick 按当前游戏刻推导为绝对值，保留原始时间不被改写
    private ScheduledTask scheduleCheck(ServerLevel level, ItemEntity entity, int delayTicks) {
        long dueTick = level.getGameTime() + Math.max(0, delayTicks);
        UUID uuid = entity.getUUID();
        return DebugScenarioManager.schedule(entity, scheduler, level.dimension(), ServerTaskKind.CONDITION_CHECK,
                dueTick, "check", () -> attempt(level, uuid, TriggerKind.NATURAL));
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
        tracked.nextCheckTick = level.getGameTime() + delay;
        tracked.task = scheduleCheck(level, entity, (int) Math.min(delay, Integer.MAX_VALUE));
    }

    // 退避重试：条件不满足时不放弃，按退避序列重试直到自然消失
    private void reschedule(ServerLevel level, ItemEntity entity, TrackedState tracked, int delayTicks) {
        int remaining = Math.max(1, lifespanProvider.lifespanTicks(level, entity) - entity.getAge() - 1);
        delayTicks = Math.min(delayTicks, remaining);
        tracked.nextCheckTick = level.getGameTime() + delayTicks;
        tracked.task = scheduleCheck(level, entity, delayTicks);
        if (DebugMode.ENABLED) { DebugScenarioManager.observe(entity, "RETRY", "delay_ticks", delayTicks,
                "failure_count", tracked.failureCount, "next_check_tick", tracked.nextCheckTick); }
        if (config.debugLogging() && DebugScenarioManager.allowsRuntimeLogging(entity)) {
            LOGGER.info("掉落物 {} 条件未满足，{} tick 后重试（第 {} 次失败）",
                    entity.getUUID(), delayTicks, tracked.failureCount);
        }
    }

    // 一次到期判定：选规则 → 执行；条件不满足则退避重试（kind 为本次请求的消失方式）
    // 调用点有两类：调度器到期检查（自然消失）与环境致死请求（在 hurt 调用栈内同步调用）；
    // 两者共用同一份追踪、预算与调度器，效果派发统一走 ConversionSettlement（阶段 7 再评估环境峰值）。
    private void attempt(ServerLevel level, UUID uuid, TriggerKind kind) {
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
            state.tracked.remove(uuid);
            return;
        }
        if (!level.isPositionEntityTicking(item.blockPosition())) {
            reschedule(level, item, tracked, config.checkIntervalTicks());
            return;
        }
        if (tracked.locked) {
            reschedule(level, item, tracked, 1);
            return;
        }
        // 转化门禁：永久禁转（返还物）在实体生命周期内阻止转化；临时冷却到期前不启动新转化（任务清单 5）
        DropState dropState = DropStateStore.get(item);
        if (dropState.permanentConversionBan()) {
            state.tracked.remove(uuid);
            return;
        }
        long cooldownRemaining = dropState.cooldownRemaining(level.getGameTime());
        if (cooldownRemaining > 0L) {
            reschedule(level, item, tracked, (int) Math.min(cooldownRemaining, Integer.MAX_VALUE));
            return;
        }
        List<Rule> candidates = candidatesFor(item);
        if (candidates.isEmpty()) {
            state.tracked.remove(uuid);
            return;
        }
        Rule chosen = select(level, item, candidates, tracked.expiryPending, kind);
        if (chosen == null) {
            tracked.failureCount++;
            if (tracked.expiryPending || pastExpiry(level, item)) {
                state.tracked.remove(uuid);
                if (tracked.expiryPending) { item.discard(); }
            } else {
                int delay = config.backoffTicks(tracked.failureCount);
                long nextAge = Long.MAX_VALUE;
                boolean eligible = false;
                for (Rule rule : candidates) {
                    long due = dueAge(level, item, rule);
                    if (item.getAge() >= due) { eligible = true; }
                    else { nextAge = Math.min(nextAge, due - item.getAge()); }
                }
                if (!eligible) { tracked.failureCount--; delay = (int) Math.min(nextAge, Integer.MAX_VALUE); }
                else if (nextAge != Long.MAX_VALUE) { delay = (int) Math.min(delay, nextAge); }
                reschedule(level, item, tracked, Math.max(1, delay));
            }
            return;
        }
        performConversion(level, state, item, tracked, chosen);
        if (tracked.expiryPending && !item.isRemoved()) { item.discard(); }
    }

    // 是否已到自然消失前一刻（到点后不再重试，交给原版机制）
    private boolean pastExpiry(ServerLevel level, ItemEntity item) {
        int lifespan = Math.max(1, lifespanProvider.lifespanTicks(level, item));
        return item.getAge() >= lifespan - 1;
    }

    // 规则选择：候选已按优先级排序，取第一条声明了该消失方式且条件成立者（同一掉落物只执行一条）
    private Rule select(ServerLevel level, ItemEntity item, List<Rule> candidates, boolean expiring, TriggerKind kind) {
        ConditionContext context = conditionContext(level, item);
        for (Rule rule : candidates) {
            // 规则未声明该消失方式时不参与（阶段 1 契约：未声明按 natural 处理）
            if (!rule.effectiveTriggers().contains(kind)) {
                continue;
            }
            // 年龄门槛只对自然消失生效：环境致死在当前刻立即判定，与存活时长无关
            if (!expiring && kind == TriggerKind.NATURAL && !isEligible(level, item, rule)) {
                if (DebugMode.ENABLED) { DebugScenarioManager.observe(item, "AGE_NOT_READY", "rule", rule.id(),
                        "due_age_ticks", dueAge(level, item, rule)); }
                continue;
            }
            boolean matched = ExpressionEvaluator.matches(rule.conditions(), context, types.conditionTypes());
            if (DebugMode.ENABLED) { DebugScenarioManager.observe(item, "CANDIDATE", "rule", rule.id(), "matched", matched); }
            if (matched) {
                return rule;
            }
        }
        return null;
    }

    // 调试命令复用实际年龄门槛，避免条件成立却尚未到期时显示为命中。
    public boolean isEligible(ServerLevel level, ItemEntity item, Rule rule) {
        return !excluded(item) && item.getAge() >= dueAge(level, item, rule);
    }

    private long dueAge(ServerLevel level, ItemEntity item, Rule rule) {
        return Math.min((long) rule.triggerAfterSeconds() * TICKS_PER_SECOND,
                Math.max(1, lifespanProvider.lifespanTicks(level, item)) - 1L);
    }

    // 执行一条规则：计划期把整堆源物品转入结算库存，随后由 ConversionSettlement 按组支付成本并交付返还
    private void performConversion(ServerLevel level, LevelState state, ItemEntity item,
                                   TrackedState tracked, Rule rule) {
        ItemStack source = item.getItem();
        int available = source.getCount();
        if (available <= 0) {
            state.tracked.remove(item.getUUID());
            return;
        }
        // 每组固定源成本 c：显式 source_cost 优先，其次隐式 1 个，再次各 consume_source 之和（Q1 真实结算）
        int costPerGroup = types.perRoundSourceConsumption(rule, source);
        tracked.locked = true;
        try {
            // 所有权转移：整堆进入结算库存，源实体本刻清空（与原实现「本刻即完成扣减」的时间点一致）
            ItemStack held = source.copy();
            source.setCount(0);
            item.addTag(Constants.CONVERTED_TAG);
            SettlementLedger ledger = SettlementLedger.get(level);
            // 记录源位置：重启后源实体已不存在，返还交付只能以记录位置为搜索起点
            SettlementRecord record = new SettlementRecord(UUID.randomUUID().toString(), rule.id().toString(),
                    level.dimension().location().toString(), held, available, costPerGroup,
                    true, item.getX(), item.getY(), item.getZ());
            ledger.put(record);
            if (DebugMode.ENABLED) {
                int estimated = costPerGroup > 0 ? Math.max(1, available / costPerGroup) : 1;
                DebugScenarioManager.converted(item, rule, estimated);
            }
            ConversionSettlement settlement = new ConversionSettlement(this, scheduler, level, item, rule, held, record, ledger);
            scheduler.scheduleAt(level.dimension(), settlement, level.getGameTime());
            if (config.debugLogging() && DebugScenarioManager.allowsRuntimeLogging(item)) {
                LOGGER.info("掉落物 {} 已按规则 {} 进入结算（可用源 {}，每组成本 {}）",
                        item.getUUID(), rule.id(), available, costPerGroup);
            }
        } finally {
            tracked.locked = false;
            state.tracked.remove(item.getUUID());
        }
    }

    // 单效果执行：效果级条件 → 概率 → 执行器；回执原样交给结算层记账，异常被隔离并记失败
    EffectResult runEffect(ServerLevel level, ItemEntity item, EffectContext base, Effect effect) {
        var conditions = effect.conditions();
        if (conditions != null && !conditions.isEmpty()) {
            ConditionContext context = conditionContext(level, item, BlockPos.containing(base.position()));
            if (!ExpressionEvaluator.matches(conditions, context, types.conditionTypes())) {
                if (DebugMode.ENABLED) { DebugScenarioManager.observe(item, "EFFECT_CONDITION_SKIPPED", "effect", effect.type()); }
                return EffectResult.skipped("conditions_unmet");
            }
        }
        if (effect.chance() < 1.0D && base.random().nextDouble() >= effect.chance()) {
            if (DebugMode.ENABLED) { DebugScenarioManager.observe(item, "EFFECT_CHANCE_SKIPPED", "effect", effect.type(), "chance", effect.chance()); }
            // 概率落空仍支付本组固定成本：不免费重试、不返还（ADR-0001）
            return EffectResult.skipped("chance_missed");
        }
        EffectType<?> definition = types.effectTypes().getOrNull(effect.type());
        if (definition == null) {
            if (DebugMode.ENABLED) { DebugScenarioManager.observe(item, "ERROR", "effect", effect.type(), "reason", "type_not_registered"); }
            LOGGER.error("规则 {} 引用了未注册的效果类型 {}", base.ruleId(), effect.type());
            return EffectResult.failed("type_not_registered");
        }
        try {
            if (DebugMode.ENABLED) { DebugScenarioManager.observe(item, "EFFECT_BEGIN", "effect", effect.type()); }
            EffectResult result = executorOf(definition).execute(effect, base);
            if (DebugMode.ENABLED) { DebugScenarioManager.observe(item, "EFFECT_EXECUTOR_RETURNED", "effect", effect.type(),
                    "outcome", result == null ? "null" : result.outcome()); }
            return result == null ? EffectResult.failed("no_result") : result;
        } catch (RuntimeException e) {
            if (DebugMode.ENABLED) { DebugScenarioManager.observe(item, "ERROR", "effect", effect.type(), "error", e.toString()); }
            LOGGER.error("效果执行失败：规则={} 效果={} 位置={}", base.ruleId(), effect.type(), base.position(), e);
            return EffectResult.failed(String.valueOf(e));
        }
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

    // 内置类型注册表：结算任务复用规则编解码器计算候选结构摘要时需要（只读，不参与规则语义）
    BuiltinTypeRegistries types() {
        return types;
    }

    // 条件上下文：标签与气候采样对象按维度复用（各自带缓存）
    private ConditionContext conditionContext(ServerLevel level, ItemEntity item) {
        return conditionContext(level, item, item.blockPosition());
    }

    // 指定位置的条件上下文（效果级条件可能作用在非源位置）
    private ConditionContext conditionContext(ServerLevel level, ItemEntity item, BlockPos pos) {
        LevelState state = stateOf(level);
        if (state.tags == null) {
            state.tags = new RuntimeTagLookup(level);
        }
        if (state.climate == null) {
            state.climate = new RuntimeClimateSampler(level);
        }
        return new RuntimeConditionContext(level, item, pos, level.random, state.tags, state.climate);
    }

    // 排除死亡掉落、已提交转化的实体和原版无限寿命物品。
    private static boolean excluded(ItemEntity item) {
        return item.isRemoved() || item.getItem().isEmpty() || item.getAge() == -32768
                || item.getTags().contains(Constants.CHECK_LOCK_TAG)
                || item.getTags().contains(Constants.CONVERTED_TAG);
    }

    // 离开维度或区块卸载时立即取消检查，释放任务捕获对象。
    public void onItemRemoved(ServerLevel level, ItemEntity item) {
        if (DebugMode.ENABLED) { DebugScenarioManager.observe(item, "REMOVED", "reason", item.getRemovalReason()); }
        LevelState state = levels.get(level.dimension());
        if (state == null) { return; }
        TrackedState tracked = state.tracked.remove(item.getUUID());
        if (tracked != null && tracked.task != null) { tracked.task.cancel(CancelReason.ENTITY_REMOVED); }
    }

    // 自然消失入口只预留最后一次有预算的检查，不在实体 tick 内执行昂贵效果。
    public boolean deferNaturalExpiry(ServerLevel level, ItemEntity item) {
        if (excluded(item)) { return false; }
        LevelState state = levels.get(level.dimension());
        if (state == null) { return false; }
        TrackedState tracked = state.tracked.get(item.getUUID());
        if (tracked == null) { return false; }
        if (!tracked.expiryPending) {
            if (DebugMode.ENABLED) { DebugScenarioManager.observe(item, "NATURAL_EXPIRY_DEFERRED"); }
            tracked.expiryPending = true;
            if (tracked.task != null) { tracked.task.cancel(CancelReason.ENTITY_REMOVED); }
            tracked.nextCheckTick = level.getGameTime();
            tracked.task = scheduleCheck(level, item, 0);
        }
        return true;
    }
}
