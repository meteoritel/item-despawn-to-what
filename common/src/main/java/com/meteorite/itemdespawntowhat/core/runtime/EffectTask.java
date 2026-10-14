package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.debug.DebugMode;
import com.meteorite.itemdespawntowhat.core.debug.DebugScenarioManager;
import com.meteorite.itemdespawntowhat.core.api.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import java.util.function.Consumer;

/** 一次效果的预算任务：创建状态一次，最终回执一次；取消不补做世界写入。 */
final class EffectTask implements ServerTask {
    private static final int CHUNK_RETRY_TICKS = 20;
    private final ConversionRuntime runtime;
    private final ServerLevel level;
    private final ItemEntity source;
    private final EffectContext context;
    private final Effect effect;
    private final Consumer<EffectResult> resultSink;
    private EffectExecution execution;
    private boolean finished;

    EffectTask(ConversionRuntime runtime, ServerLevel level, ItemEntity source, EffectContext context,
               Effect effect, Consumer<EffectResult> resultSink) {
        this.runtime = runtime; this.level = level; this.source = source; this.context = context;
        this.effect = effect; this.resultSink = resultSink;
    }

    @Override public ServerTaskKind kind() { return ServerTaskKind.EFFECT; }
    @Override public String tracingName() { return "effect/" + context.ruleId() + "/" + effect.type(); }

    @Override public StepResult step(ServerTickBudget budget) {
        if (finished) { return StepResult.done(1); }
        if (budget.exhausted()) { return StepResult.yield(1, 1); }
        if (!LoadedChunks.contains(level, BlockPos.containing(context.position()))) {
            return StepResult.yield(1, CHUNK_RETRY_TICKS);
        }
        if (execution == null) { execution = runtime.startEffect(level, source, context, effect); }
        if (budget.exhausted()) { return StepResult.yield(1, 1); }
        EffectResult result;
        try {
            result = execution.advance(budget);
        } catch (RuntimeException failure) {
            if (DebugMode.ENABLED) DebugScenarioManager.observe(source, "ERROR", "effect", effect.type(),
                    "error", failure.toString());
            org.apache.logging.log4j.LogManager.getLogger().error(
                    "效果执行失败：规则={} 效果={} 位置={}", context.ruleId(), effect.type(), context.position(), failure);
            result = EffectResult.failed(failure.toString());
        }
        if (result == null) { return StepResult.yield(1, 1); }
        if (DebugMode.ENABLED) DebugScenarioManager.observe(source, "EFFECT_EXECUTOR_RETURNED",
                "effect", effect.type(), "outcome", result.outcome());
        finish(result);
        return StepResult.done(1);
    }

    @Override public void onCancelled(CancelReason reason) { finished = true; execution = null; }
    @Override public void onFailed(Throwable failure) {
        finish(EffectResult.failed(failure == null ? "execution_failed" : failure.toString()));
    }

    private void finish(EffectResult result) {
        if (finished) { return; }
        // 先置终态再回调，回调异常也不能导致世界效果重放或重复入账。
        finished = true;
        execution = null;
        resultSink.accept(result);
    }
}
