package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.type.effect.ArrowRainEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.ArrowRainExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** 同候选、同槽位的多组箭雨合并为一个延迟任务；每组保留独立的到期刻与药水抽取结果。 */
final class SettlementArrowRainOutputs {
    private record Key(String candidate, int slot) {}

    record Batch(ArrowRainEffect effect, EffectContext context, ArrowRainExecutor.SeriesBatchPlan preparation,
                 long readyTick) {}

    private final Map<Key, Batch> batches = new LinkedHashMap<>();

    boolean collect(String candidate, int slot, Effect effect, @Nullable EffectPreparation preparation,
                    EffectContext context, long readyTick) {
        // 药水引用无法解析的组在成功转化当刻已定失败原因，按失败回执走，不进入合并
        if (!(effect instanceof ArrowRainEffect rain)
                || !(preparation instanceof ArrowRainEffect.Prepared(
                java.util.List<net.minecraft.world.effect.MobEffectInstance> potionEffects, String failure
        )) || failure != null) {
            return false;
        }
        Batch batch = batches.computeIfAbsent(new Key(candidate, slot),
                ignored -> new Batch(rain, context, new ArrowRainExecutor.SeriesBatchPlan(), readyTick));
        batch.preparation().add(context, potionEffects, readyTick);
        return true;
    }

    Batch next() {
        var iterator = batches.values().iterator();
        Batch batch = iterator.next();
        iterator.remove();
        return batch;
    }

    boolean isEmpty() { return batches.isEmpty(); }
    void clear() { batches.clear(); }
}