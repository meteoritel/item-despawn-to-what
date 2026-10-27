package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.ConditionContext;
import com.meteorite.itemdespawntowhat.core.api.RuntimeObserver;
import com.meteorite.itemdespawntowhat.core.api.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.config.ServerConfig;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.model.TriggerKind;
import com.meteorite.itemdespawntowhat.core.service.BuiltinTypeRegistries;
import com.meteorite.itemdespawntowhat.core.state.DropState;
import com.meteorite.itemdespawntowhat.core.state.DropStateStore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 转化门禁：一次到期判定的纯裁决，回答「这个掉落物现在该怎么处理」，不持有也不回写追踪状态。
 * 设计要点：
 * - 判定所需的外部事实（候选规则、条件上下文、平台寿命）由宿主以函数注入，门禁只按固定顺序消费；
 * - 判定结果收敛成 {@link Decision} 四个值，宿主据此决定执行转化、重排退避、释放追踪或保持原排期；
 * - 自然消失前一天到期、源量不足一组、规则未声明该消失方式等分支的差异全部落在结论里，调用方不再重复判定。
 */
final class ConversionGate {

    /** 门禁只读的追踪快照：锁定、失败档位与是否处于到期补算，都是判定的输入而非输出。 */
    record Tracked(boolean locked, int failureCount, boolean expiryPending) {
    }

    /** 一次判定可能的四种结论；执行语义由宿主负责，门禁自身不产生任何副作用。 */
    sealed interface Decision permits Convert, Wait, Release, Idle {
    }

    /** 规则命中：执行该规则，{@code discardAfter} 表示这是自然消失前最后一次判定，执行后需销毁源实体。 */
    record Convert(Rule rule, boolean discardAfter) implements Decision {
    }

    /** 本刻不执行但保留追踪：按 {@code delayTicks} 重排，{@code countsAsFailure} 为真说明本刻具备命中资格、要推退避档。 */
    record Wait(int delayTicks, boolean countsAsFailure) implements Decision {
    }

    /** 追踪到此为止：按原因取消句柄并移除条目，{@code discardEntity} 为真时把销毁交还原版机制。 */
    record Release(CancelReason reason, boolean discardEntity) implements Decision {
    }

    /** 保留既有排期、不改动句柄：环境同步入口没有对应规则时的默认结论。 */
    record Idle() implements Decision {
    }

    private final ServerConfig config;
    private final BuiltinTypeRegistries types;
    private final LifespanProvider lifespan;
    private final Function<ItemEntity, List<Rule>> candidates;
    private final BiFunction<ServerLevel, ItemEntity, ConditionContext> conditions;

    ConversionGate(ServerConfig config, BuiltinTypeRegistries types, LifespanProvider lifespan,
                   Function<ItemEntity, List<Rule>> candidates,
                   BiFunction<ServerLevel, ItemEntity, ConditionContext> conditions) {
        this.config = config;
        this.types = types;
        this.lifespan = lifespan;
        this.candidates = candidates;
        this.conditions = conditions;
    }

    /**
     * 判定一次到期请求的处理方式。
     * 调用方必须已完成「追踪存在、实体仍是未排除的掉落物」两项前置检查；判定顺序固定：
     * 实体可 tick → 未锁定 → 未被永久禁转 → 不在冷却 → 有候选 → 能选中规则。
     */
    Decision decide(ServerLevel level, ItemEntity item, TriggerKind kind, Tracked tracked) {
        // 非实体 tick 区块内的实体拿不到可靠的环境事实，只按检查间隔延后，不消耗失败档
        if (!level.isPositionEntityTicking(item.blockPosition())) {
            return new Wait(config.checkIntervalTicks(), false);
        }
        // 结算提交当刻锁定：效果还在跑，此时再判定会重复消耗源物品
        if (tracked.locked()) {
            return new Wait(1, false);
        }
        DropState dropState = DropStateStore.get(item);
        if (dropState.permanentConversionBan()) {
            return new Release(CancelReason.TRACKING_RELEASED, false);
        }
        long cooldownRemaining = dropState.cooldownRemaining(level.getGameTime());
        if (cooldownRemaining > 0L) {
            return new Wait((int) Math.min(cooldownRemaining, Integer.MAX_VALUE), false);
        }
        List<Rule> candidates = this.candidates.apply(item);
        if (candidates.isEmpty()) {
            return new Release(CancelReason.TRACKING_RELEASED, false);
        }
        return chooseOrWait(level, item, candidates, kind, tracked);
    }

    // 单趟遍历：同一遍里完成选规、观测事件与退避档聚合。
    // 分成「先选规、再聚合退避」两趟的旧写法会让同一 (掉落物, 规则) 每刻至少算两次年龄门槛；
    // 合并后候选仍按优先级顺序推进，事件顺序与命中即早退都不变。
    private Decision chooseOrWait(ServerLevel level, ItemEntity item, List<Rule> candidates, TriggerKind kind,
                                  Tracked tracked) {
        // 年龄门槛只在自然触发且非到期补算时生效：环境致死在当前刻立即判定，与存活时长无关
        boolean ageGated = kind == TriggerKind.NATURAL && !tracked.expiryPending();
        int age = item.getAge();
        int sourceCount = item.getItem().getCount();
        ConditionContext context = null;
        boolean kindCovered = false;
        boolean relevant = false;
        boolean eligible = false;
        long nextAge = Long.MAX_VALUE;
        for (Rule rule : candidates) {
            // 规则未声明该消失方式时不参与（契约：未声明按 natural 处理）
            if (!rule.effectiveTriggers().contains(kind)) {
                continue;
            }
            kindCovered = true;
            long due = dueAge(level, item, rule);
            boolean ageReady = !ageGated || age >= due;
            // 退避资格与命中资格同口径：本触发方式参与且源数量够一组
            boolean enough = sourceCount >= types.perRoundSourceConsumption(rule, item.getItem());
            if (enough) {
                relevant = true;
                if (ageReady) { eligible = true; }
                else { nextAge = Math.min(nextAge, due - age); }
            }
            if (!ageReady) {
                if (RuntimeObserver.current().enabled()) { RuntimeObserver.current().observe(item, "AGE_NOT_READY", "rule", rule.id(),
                        "due_age_ticks", due); }
                continue;
            }
            boolean matched = false;
            if (enough) {
                if (context == null) { context = conditions.apply(level, item); }
                matched = ExpressionEvaluator.matches(rule.conditions(), context, types.conditionTypes());
            }
            if (RuntimeObserver.current().enabled()) { RuntimeObserver.current().observe(item, "CANDIDATE", "rule", rule.id(), "matched", matched); }
            if (matched) {
                return new Convert(rule, tracked.expiryPending());
            }
        }
        if (tracked.expiryPending() || pastExpiry(level, item)) {
            return new Release(CancelReason.TRACKING_RELEASED, tracked.expiryPending());
        }
        if (!relevant) {
            // 区分「本触发方式没有规则」与「有规则但源量暂时不足一组」：前者再检查也不会转化，释放追踪；
            // 后者数量可能随合并增长，保留追踪并按检查间隔等待，且不累加失败计数
            if (!kindCovered) {
                // 环境同步入口没有对应规则时保留既有自然排期，不改动句柄
                return kind == TriggerKind.NATURAL ? new Release(CancelReason.TRACKING_RELEASED, false) : new Idle();
            }
            return kind == TriggerKind.NATURAL ? new Wait(Math.max(1, config.checkIntervalTicks()), false) : new Idle();
        }
        if (eligible) {
            // 宿主在这一档之后会自增失败计数，因此退避延迟按「本次失败后」的档位计算
            int delay = config.backoffTicks(tracked.failureCount() + 1);
            if (nextAge != Long.MAX_VALUE) { delay = (int) Math.min(delay, nextAge); }
            return new Wait(Math.max(1, delay), true);
        }
        return new Wait(Math.max(1, (int) Math.min(nextAge, Integer.MAX_VALUE)), false);
    }

    // 是否已到自然消失前一刻（到点后不再重试，交给原版机制）；检查失败的恢复路径要复用同一口径
    boolean pastExpiry(ServerLevel level, ItemEntity item) {
        int lifespanTicks = Math.max(1, lifespan.lifespanTicks(level, item));
        return item.getAge() >= lifespanTicks - 1;
    }

    // 该规则在该掉落物上的年龄门槛：triggerAfterSeconds 换算为 tick，且不超过自然消失前一刻
    long dueAge(ServerLevel level, ItemEntity item, Rule rule) {
        return Math.min((long) rule.triggerAfterSeconds() * ConversionRuntime.TICKS_PER_SECOND,
                Math.max(1, lifespan.lifespanTicks(level, item)) - 1L);
    }
}
