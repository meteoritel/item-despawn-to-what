package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.api.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepResult;
import com.meteorite.itemdespawntowhat.core.api.RuntimeObserver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import org.jetbrains.annotations.Nullable;
import java.util.function.Consumer;

/**
 * 一次效果的预算任务：创建状态一次，最终回执一次；取消不补做世界写入。
 * 结束与失败先置终态、摘掉执行状态引用，再各通知账目与执行状态一次；
 * 取消同样先置终态、摘引用并取消执行状态，不产生回执也不入账。
 * 终态通知独立于产出回执，汇总批次据此释放所属结算的等待计数。
 * 因此回调重入或回调异常都不会重放世界操作、不会重复入账。
 * 触发位置区块连续未加载达到等待上限时给出失败回执并终结，不再保留任务等待重新加载。
 */
final class EffectTask implements ServerTask {
    private static final String CHUNK_WAIT_TIMEOUT = "chunk_wait_timeout";
    private final ChunkWait chunkWait = new ChunkWait();
    private final ConversionRuntime runtime;
    private final ServerLevel level;
    private final ItemEntity source;
    private final EffectContext context;
    private final Effect effect;
    // 成功转化当刻固定的本次输入；null 表示本类型没有准备阶段，条件与概率仍在首次执行时判定
    private final @Nullable EffectPreparation preparation;
    private final Consumer<EffectResult> resultSink;
    private final Runnable completionSink;
    private EffectExecution execution;
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
    }

    @Override public ServerTaskKind kind() { return ServerTaskKind.EFFECT; }
    @Override public String tracingName() { return "effect/" + context.ruleId() + "/" + effect.type(); }

    @Override public StepResult step(ServerTickBudget budget) {
        if (finished) { return StepResult.done(1); }
        if (budget.exhausted()) { return StepResult.yield(1, 1); }
        if (!LoadedChunks.contains(level, BlockPos.containing(context.position()))) {
            if (chunkWait.expired(budget.gameTime())) {
                finish(EffectResult.failed(CHUNK_WAIT_TIMEOUT));
                return StepResult.done(1);
            }
            return StepResult.yield(1, LoadedChunks.RETRY_TICKS);
        }
        chunkWait.reset();
        if (execution == null) { execution = runtime.startEffect(level, source, context, effect, preparation); }
        if (budget.exhausted()) { return StepResult.yield(1, 1); }
        EffectResult result;
        try {
            result = execution.advance(budget);
        } catch (RuntimeException failure) {
            if (RuntimeObserver.current().enabled()) RuntimeObserver.current().observe(source, "ERROR", "effect", effect.type(),
                    "error", failure.toString());
            org.apache.logging.log4j.LogManager.getLogger().error(
                    "效果执行失败：规则={} 效果={} 位置={}", context.ruleId(), effect.type(), context.position(), failure);
            result = EffectResult.failed(failure.toString());
        }
        if (result == null) { return StepResult.yield(1, 1); }
        if (RuntimeObserver.current().enabled()) RuntimeObserver.current().observe(source, "EFFECT_EXECUTOR_RETURNED",
                "effect", effect.type(), "outcome", result.outcome());
        finish(result);
        return StepResult.done(1);
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
