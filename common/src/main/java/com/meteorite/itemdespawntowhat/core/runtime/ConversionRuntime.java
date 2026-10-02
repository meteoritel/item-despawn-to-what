package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.ConditionContext;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecutor;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.config.ServerConfig;
import com.meteorite.itemdespawntowhat.core.load.LoadedRule;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.service.BuiltinTypeRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private static final Logger LOGGER = LogManager.getLogger();
    // 每秒刻数：trigger_after_seconds 与 tick 的换算
    private static final int TICKS_PER_SECOND = 20;

    private final ServerConfig config;
    private final BuiltinTypeRegistries types;
    private final LifespanProvider lifespanProvider;
    private final Map<ResourceKey<Level>, LevelState> levels = new HashMap<>();
    private volatile RuleIndex index = RuleIndex.empty();

    // 单个维度的运行时状态；只保存调度器、缓存与追踪表，任务上下文按维度卸载释放
    private static final class LevelState {
        private final TickScheduler scheduler;
        private final TickScheduler effects;
        private final Map<UUID, TrackedState> tracked = new HashMap<>();
        private RuntimeTagLookup tags;
        private RuntimeClimateSampler climate;

        LevelState(int maxChecksPerTick) {
            this.scheduler = new TickScheduler(maxChecksPerTick);
            this.effects = new TickScheduler(maxChecksPerTick);
        }
    }

    // 单个掉落物的追踪状态
    private static final class TrackedState {
        private int failureCount;
        private boolean expiryPending;
        private TickScheduler.Task task;
        private boolean locked;
        // 下次到期判定的游戏刻（仅用于 debug 输出，-1 表示未排期）
        private long nextCheckTick = -1L;
    }

    // 掉落物追踪状态快照，供 /idtw debug inspect|why 只读使用
    public record TrackedItemInfo(boolean tracked, int failureCount, boolean locked, long nextCheckTick) {
    }

    public ConversionRuntime(ServerConfig config, BuiltinTypeRegistries types) {
        this(config, types, LifespanProvider.vanillaDefault());
    }

    public ConversionRuntime(ServerConfig config, BuiltinTypeRegistries types, LifespanProvider lifespanProvider) {
        this.config = config;
        this.types = types;
        this.lifespanProvider = lifespanProvider;
    }

    // ========== 规则生命周期 ==========

    // 规则热替换：重建索引并清空全部追踪状态（由随后的 rescan 重建）
    public void replaceRules(List<LoadedRule<Rule>> rules, IssueCollector issues) {
        this.index = RuleIndex.build(rules, issues);
        for (LevelState state : levels.values()) {
            state.tracked.clear();
            state.scheduler.clear();
            // 标签与气候缓存必须随 reload 失效，否则条件求值会读到旧数据包的标签成员
            state.tags = null;
            state.climate = null;
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
        if (index.isEmpty() || excluded(entity)) {
            return;
        }
        List<Rule> candidates = index.candidates(itemIdOf(entity));
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
        scheduleInitial(level, state, entity, tracked, candidates);
    }

    // 每 tick：检查与效果分别按预算推进；实体失效由离开事件及时清理。
    public void onLevelTick(ServerLevel level) {
        LevelState state = stateOf(level);
        long now = level.getGameTime();
        state.scheduler.runDue(now);
        state.effects.runDue(now);
    }

    // reload 后回扫已加载实体重建追踪（修复热重载对已存在掉落物无效）
    public void rescan(ServerLevel level) {
        LevelState state = stateOf(level);
        state.tracked.clear();
        state.scheduler.clear();
        if (index.isEmpty()) {
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
            removed.scheduler.clear();
            removed.effects.clear();
            removed.tracked.clear();
        }
    }

    // 服务端停止：释放全部状态
    public void shutdown() {
        for (LevelState state : levels.values()) {
            state.scheduler.clear();
            state.effects.clear();
            state.tracked.clear();
        }
        levels.clear();
        index = RuleIndex.empty();
    }

    // ========== 统计（供 /idtw debug stats 使用） ==========

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
        return index.candidates(itemIdOf(entity));
    }

    public int pendingTasks(ServerLevel level) {
        LevelState state = levels.get(level.dimension());
        return state == null ? 0 : state.scheduler.pending() + state.effects.pending();
    }

    // 两类队列单独观测，压测时可定位是条件检查还是世界操作形成积压。
    public TickScheduler.Stats queueStats(ServerLevel level, boolean effects) {
        LevelState state = levels.get(level.dimension());
        return state == null ? new TickScheduler.Stats(0, 0, 0, 0, 0, 0)
                : (effects ? state.effects : state.scheduler).stats();
    }

    // ========== 内部实现 ==========

    private LevelState stateOf(ServerLevel level) {
        return levels.computeIfAbsent(level.dimension(), key -> new LevelState(config.maxChecksPerTick()));
    }

    private static ResourceLocation itemIdOf(ItemEntity entity) {
        return BuiltInRegistries.ITEM.getKey(entity.getItem().getItem());
    }

    // 首次登记：触发时刻 = min(最早规则的 trigger_after_seconds, 自然消失前一刻)
    private void scheduleInitial(ServerLevel level, LevelState state, ItemEntity entity,
                                 TrackedState tracked, List<Rule> candidates) {
        int lifespan = Math.max(1, lifespanProvider.lifespanTicks(level, entity));
        long earliestTrigger = Long.MAX_VALUE;
        for (Rule rule : candidates) {
            earliestTrigger = Math.min(earliestTrigger, Math.max(0L, (long) rule.triggerAfterSeconds() * TICKS_PER_SECOND));
        }
        long dueAge = Math.min(earliestTrigger, lifespan - 1L);
        long delay = Math.max(0, dueAge - entity.getAge());
        tracked.nextCheckTick = level.getGameTime() + delay;
        UUID uuid = entity.getUUID();
        tracked.task = state.scheduler.schedule(level.getGameTime(), (int) Math.min(delay, Integer.MAX_VALUE),
                () -> attempt(level, uuid));
    }

    // 退避重试：条件不满足时不放弃，按退避序列重试直到自然消失
    private void reschedule(ServerLevel level, LevelState state, ItemEntity entity, TrackedState tracked, int delayTicks) {
        int remaining = Math.max(1, lifespanProvider.lifespanTicks(level, entity) - entity.getAge() - 1);
        delayTicks = Math.min(delayTicks, remaining);
        tracked.nextCheckTick = level.getGameTime() + delayTicks;
        UUID uuid = entity.getUUID();
        tracked.task = state.scheduler.schedule(level.getGameTime(), delayTicks, () -> attempt(level, uuid));
        if (config.debugLogging()) {
            LOGGER.info("掉落物 {} 条件未满足，{} tick 后重试（第 {} 次失败）",
                    entity.getUUID(), delayTicks, tracked.failureCount);
        }
    }

    // 一次到期判定：选规则 → 执行；条件不满足则退避重试
    private void attempt(ServerLevel level, UUID uuid) {
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
            reschedule(level, state, item, tracked, config.checkIntervalTicks());
            return;
        }
        if (tracked.locked) {
            reschedule(level, state, item, tracked, 1);
            return;
        }
        List<Rule> candidates = index.candidates(itemIdOf(item));
        if (candidates.isEmpty()) {
            state.tracked.remove(uuid);
            return;
        }
        Rule chosen = select(level, item, candidates, tracked.expiryPending);
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
                reschedule(level, state, item, tracked, Math.max(1, delay));
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

    // 规则选择：候选已按优先级排序，取第一条条件成立者（同一掉落物只执行一条）
    private Rule select(ServerLevel level, ItemEntity item, List<Rule> candidates, boolean expiring) {
        ConditionContext context = conditionContext(level, item);
        for (Rule rule : candidates) {
            if (!expiring && !isEligible(level, item, rule)) { continue; }
            if (ExpressionEvaluator.matches(rule.conditions(), context, types.conditionTypes())) {
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

    // 执行一条规则的全部效果；结束后该掉落物的追踪即终止
    private void performConversion(ServerLevel level, LevelState state, ItemEntity item,
                                   TrackedState tracked, Rule rule) {
        tracked.locked = true;
        item.addTag(Constants.CONVERTED_TAG);
        try {
            // 预先算好整堆能支持多少轮：rounds = 堆叠数 / 每轮源物品消耗量
            // （不消耗源物品的规则固定 1 轮），随后一次性扣减 rounds×消耗 并产出 rounds×结果
            int perRound = types.perRoundSourceConsumption(rule);
            int available = item.getItem().getCount();
            int rounds = perRound > 0 ? Math.max(1, available / perRound) : 1;
            // 实际覆盖的源物品数：必须按 available 收敛（堆叠不足一轮时只算实际可扣的数量），
            // 否则 spawn_xp 的 per_source_item 会多给经验；不消耗源物品的规则按 1 计
            int covered = perRound > 0 ? Math.min(rounds * perRound, available) : 1;
            EffectContext base = effectContext(level, item, rule.id(), rounds, covered);
            // 规则未声明任何 consume_* 时，按默认语义先隐式消耗 1 个源物品（Q8 / 规划书 3.1-4）
            if (rule.usesImplicitSourceConsumption()) {
                runEffect(level, item, base, types.implicitSourceConsumption());
            }
            for (Effect effect : rule.effects()) {
                dispatchEffect(level, item, base, effect);
            }
            if (config.debugLogging()) {
                LOGGER.info("掉落物 {} 已按规则 {} 转化（{} 轮 × {} 个效果）",
                        item.getUUID(), rule.id(), rounds, rule.effects().size());
            }
        } finally {
            tracked.locked = false;
            state.tracked.remove(item.getUUID());
        }
    }

    // 效果派发：延迟交给调度器，条件与概率在此统一判定
    private void dispatchEffect(ServerLevel level, ItemEntity item, EffectContext base, Effect effect) {
        int delay = Math.max(0, effect.delayTicks());
        base.schedule(delay, () -> runEffect(level, item, base, effect));
    }

    // 单效果执行：效果级条件 → 概率 → 执行器；异常被隔离，不影响后续效果
    private void runEffect(ServerLevel level, ItemEntity item, EffectContext base, Effect effect) {
        var conditions = effect.conditions();
        if (conditions != null && !conditions.isEmpty()) {
            ConditionContext context = conditionContext(level, item, BlockPos.containing(base.position()));
            if (!ExpressionEvaluator.matches(conditions, context, types.conditionTypes())) {
                return;
            }
        }
        if (effect.chance() < 1.0D && base.random().nextDouble() >= effect.chance()) {
            return;
        }
        EffectType<?> definition = types.effectTypes().getOrNull(effect.type());
        if (definition == null) {
            LOGGER.error("规则 {} 引用了未注册的效果类型 {}", base.ruleId(), effect.type());
            return;
        }
        try {
            executorOf(definition).execute(effect, base);
        } catch (RuntimeException e) {
            LOGGER.error("效果执行失败：规则={} 效果={} 位置={}", base.ruleId(), effect.type(), base.position(), e);
        }
    }

    // 泛型桥接：效果即类型定义声明的 P，执行器只读取参数
    @SuppressWarnings("unchecked")
    private static EffectExecutor<Effect> executorOf(EffectType<?> definition) {
        return ((EffectType<Effect>) definition).executor();
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

    private EffectContext effectContext(ServerLevel level, ItemEntity item, ResourceLocation ruleId,
                                       int rounds, int coveredSourceItems) {
        LevelState state = stateOf(level);
        return new RuntimeEffectContext(level, item, item.position(), ruleId, state.effects,
                rounds, coveredSourceItems);
    }

    // 排除死亡掉落、已提交转化的实体和原版无限寿命物品。
    private static boolean excluded(ItemEntity item) {
        return item.isRemoved() || item.getItem().isEmpty() || item.getAge() == -32768
                || item.getTags().contains(Constants.CHECK_LOCK_TAG)
                || item.getTags().contains(Constants.CONVERTED_TAG);
    }

    // 离开维度或区块卸载时立即取消检查，释放任务捕获对象。
    public void onItemRemoved(ServerLevel level, ItemEntity item) {
        LevelState state = levels.get(level.dimension());
        if (state == null) { return; }
        TrackedState tracked = state.tracked.remove(item.getUUID());
        if (tracked != null && tracked.task != null) { tracked.task.cancel(); }
    }

    // 自然消失入口只预留最后一次有预算的检查，不在实体 tick 内执行昂贵效果。
    public boolean deferNaturalExpiry(ServerLevel level, ItemEntity item) {
        if (excluded(item)) { return false; }
        LevelState state = levels.get(level.dimension());
        if (state == null) { return false; }
        TrackedState tracked = state.tracked.get(item.getUUID());
        if (tracked == null) { return false; }
        if (!tracked.expiryPending) {
            tracked.expiryPending = true;
            if (tracked.task != null) { tracked.task.cancel(); }
            UUID uuid = item.getUUID();
            tracked.nextCheckTick = level.getGameTime();
            tracked.task = state.scheduler.schedule(level.getGameTime(), 0, () -> attempt(level, uuid));
        }
        return true;
    }
}
