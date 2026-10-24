package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.ChunkRequirement;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.EffectStep;
import com.meteorite.itemdespawntowhat.core.api.RuntimeObserver;
import com.meteorite.itemdespawntowhat.core.api.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepResult;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 一次效果的预算任务：创建状态一次，最终回执一次；取消不补做世界写入。
 * 结束与失败先置终态、摘掉执行状态引用，再各通知账目与执行状态一次；
 * 取消同样先置终态、摘引用并取消执行状态，不产生回执也不入账。
 * 终态通知独立于产出回执，每个效果任务据此释放所属结算的等待计数。
 * 因此回调重入或回调异常都不会重放世界操作、不会重复入账。
 * 触发位置或执行器目标区块连续未加载达到等待上限时给出失败回执并终结，不再保留任务等待重新加载。
 */
final class EffectTask implements ServerTask {
    private static final String CHUNK_WAIT_TIMEOUT = "chunk_wait_timeout";
    private final ChunkWait originChunkWait = new ChunkWait();
    private final ChunkWait executionChunkWait = new ChunkWait();
    private final ConversionRuntime runtime;
    private final ServerLevel level;
    private final ItemEntity source;
    private final EffectContext context;
    private final Effect effect;
    // 成功转化当刻固定的本次输入；null 表示本类型没有准备阶段，概率仍在首次执行时判定
    private final @Nullable EffectPreparation preparation;
    private final Consumer<EffectResult> resultSink;
    private final Runnable completionSink;
    private EffectExecution execution;
    private final ChunkRequirement originChunks;
    private ChunkRequirement pendingChunks;
    private long readyAt = Long.MIN_VALUE;
    private boolean finished;

    EffectTask(ConversionRuntime runtime, ServerLevel level, ItemEntity source, EffectContext context,
               Effect effect, @Nullable EffectPreparation preparation, Consumer<EffectResult> resultSink) {
        this(runtime, level, source, context, effect, preparation, resultSink, () -> {});
    }

    EffectTask(ConversionRuntime runtime, ServerLevel level, ItemEntity source, EffectContext context,
               Effect effect, @Nullable EffectPreparation preparation, Consumer<EffectResult> resultSink,
               Runnable completionSink) {
        this.runtime = runtime; this.level = level; this.source = source; this.context = context;
        this.effect = effect; this.preparation = preparation; this.resultSink = resultSink;
        this.completionSink = completionSink;
        this.originChunks = ChunkRequirement.at(BlockPos.containing(context.position()));
    }

    @Override public ServerTaskKind kind() { return ServerTaskKind.EFFECT; }
    @Override public String tracingName() { return "effect/" + context.ruleId() + "/" + effect.type(); }

    @Override public StepResult step(ServerTickBudget budget) {
        if (finished) { return StepResult.done(1); }
        if (budget.exhausted()) { return StepResult.yield(1, 1); }
        long now = budget.gameTime();
        if (now < readyAt) {
            return StepResult.yield(1, delayUntil(readyAt, now));
        }
        ChunkWait.Status origin = originChunkWait.poll(level, originChunks, now);
        if (origin != ChunkWait.Status.READY) { return waitForChunks(originChunkWait, origin, now); }
        if (execution == null) { execution = runtime.startEffect(source, context, effect, preparation); }
        if (budget.exhausted()) { return StepResult.yield(1, 1); }
        EffectStep step;
        try {
            ChunkRequirement required = execution.requiredChunks();
            // 共享游标可能已由其它组推进，当前完整需求优先于旧断点请求。
            ChunkRequirement current = required != null ? required : pendingChunks;
            if (current != null) {
                ChunkWait.Status status = executionChunkWait.poll(level, current, now);
                if (status != ChunkWait.Status.READY) {
                    return waitForChunks(executionChunkWait, status, now);
                }
            }
            pendingChunks = null;
            executionChunkWait.reset();
            step = execution.advance(budget);
        } catch (RuntimeException failure) {
            if (RuntimeObserver.current().enabled()) RuntimeObserver.current().observe(source, "ERROR", "effect", effect.type(),
                    "error", failure.toString());
            org.apache.logging.log4j.LogManager.getLogger().error(
                    "效果执行失败：规则={} 效果={} 位置={}", context.ruleId(), effect.type(), context.position(), failure);
            step = EffectStep.done(EffectResult.failed(failure.toString()));
        }
        if (step instanceof EffectStep.AwaitChunks(var requirement)) {
            pendingChunks = requirement;
            // 执行器已在断点检查过不可用，从本刻计时；后续只由任务复查和排期。
            return waitForChunks(executionChunkWait, executionChunkWait.unavailable(now), now);
        }
        if (step instanceof EffectStep.Delay(var dueTick)) {
            readyAt = dueTick;
            return StepResult.yield(1, delayUntil(readyAt, now));
        }
        if (step == EffectStep.Yield.INSTANCE) {
            return StepResult.yield(1, 1);
        }
        EffectResult result = ((EffectStep.Done) step).result();
        if (RuntimeObserver.current().enabled()) RuntimeObserver.current().observe(source, "EFFECT_EXECUTOR_RETURNED",
                "effect", effect.type(), "outcome", result.outcome());
        finish(result);
        return StepResult.done(1);
    }

    private StepResult waitForChunks(ChunkWait wait, ChunkWait.Status status, long now) {
        if (status == ChunkWait.Status.TIMED_OUT) {
            finish(EffectResult.failed(CHUNK_WAIT_TIMEOUT));
            return StepResult.done(1);
        }
        return StepResult.yield(1, wait.retryDelay(now));
    }

    private static int delayUntil(long due, long now) {
        return Math.clamp(due - now, 1, Integer.MAX_VALUE);
    }

    @Override public void onCancelled(CancelReason reason) {
        if (finished) { return; }
        // 取消不产生回执也不再入账：只摘引用并通知本次执行状态一次
        finished = true;
        EffectExecution active = execution;
        execution = null;
        try {
            if (active != null) { active.onCancelled(reason); }
        } finally {
            completionSink.run();
        }
    }

    @Override public void onFailed(Throwable failure) {
        finish(EffectResult.failed(failure == null ? "execution_failed" : failure.toString()));
    }

    private void finish(EffectResult result) {
        if (finished) { return; }
        // 先置终态、摘引用再回调：回调异常不会重放世界效果，也不会重复入账
        finished = true;
        EffectExecution active = execution;
        execution = null;
        try {
            resultSink.accept(result);
        } finally {
            // 账目回调抛出也必须完成一次执行状态通知，且此后再无路径重入
            try {
                if (active != null) { active.onFinished(result); }
            } finally {
                completionSink.run();
            }
        }
    }
}
