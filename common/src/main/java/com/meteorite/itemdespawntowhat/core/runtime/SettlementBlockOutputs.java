package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.type.effect.PlaceBlockEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.PlaceBlockExecutor;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** 同候选、槽位、目标方块合并为一次扫描；每组保留独立的到期刻与源成本进度。 */
final class SettlementBlockOutputs {
    private record Key(String candidate, int slot, Block block) {}

    record Batch(PlaceBlockEffect effect, EffectContext context, PlaceBlockExecutor.BatchPlan preparation,
                 long readyTick) {}

    private final Map<Key, Batch> batches = new LinkedHashMap<>();

    boolean collect(String candidate, int slot, Effect effect, @Nullable EffectPreparation preparation,
                    EffectContext context, long readyTick) {
        if (!(effect instanceof PlaceBlockEffect block)
                || !(preparation instanceof PlaceBlockExecutor.PlacementPlan plan) || plan.block() == null) {
            return false;
        }
        Batch batch = batches.computeIfAbsent(new Key(candidate, slot, plan.block()),
                ignored -> new Batch(block, context, new PlaceBlockExecutor.BatchPlan(plan), readyTick));
        batch.preparation().add(block.count(), context, readyTick);
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
