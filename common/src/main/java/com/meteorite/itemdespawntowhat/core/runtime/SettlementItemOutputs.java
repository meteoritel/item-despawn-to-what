package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.type.effect.EntityProduct;
import com.meteorite.itemdespawntowhat.core.type.effect.SpawnEntityEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.SpawnEntityExecutor;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 已支付转化组的直接物品产出汇总：同一候选、槽位与物品共用一份总件数。
 * 标签成员与概率由各组固定输入决定；本类只接收成功输入，不重抽、不占用未开始组的产出。
 * 所有组派发完后封口，交付不早于桶内最晚的原定执行时刻，尾堆因此只生成一次。
 * 当前直接物品产出只带默认组件；战利品等带独立堆叠数据的产出沿用其专用交付。
 */
final class SettlementItemOutputs {
    private record Key(String candidate, int slot, Item item) {}

    private final Map<Key, Batch> batches = new LinkedHashMap<>();

    static final class Batch {
        private final Effect effect;
        private final EffectContext context;
        private final Item item;
        private long count;
        private long readyTick;

        private Batch(Effect effect, EffectContext context, Item item) {
            this.effect = effect;
            this.context = context;
            this.item = item;
        }

        Effect effect() { return effect; }
        EffectContext context() { return context; }
        long readyTick() { return readyTick; }
        SpawnEntityExecutor.ItemBatchPlan preparation() {
            return new SpawnEntityExecutor.ItemBatchPlan(item, count);
        }
    }

    // 返回 true 表示已接管本组产物；跳过、失败及其他效果继续走原效果任务。
    boolean collect(String candidate, int slot, Effect effect, @Nullable EffectPreparation preparation,
                    EffectContext context, long readyTick) {
        if (!(effect instanceof SpawnEntityEffect spawn) || !(spawn.product() instanceof EntityProduct.Item)
                || !(preparation instanceof SpawnEntityExecutor.SpawnPlan plan)
                || plan.failure() != null || plan.item() == null) {
            return false;
        }
        Key key = new Key(candidate, slot, plan.item());
        Batch batch = batches.computeIfAbsent(key, ignored -> new Batch(effect, context, plan.item()));
        batch.count = Math.addExact(batch.count, (long) plan.count() * context.rounds());
        batch.readyTick = Math.max(batch.readyTick, readyTick);
        return true;
    }

    @Nullable Batch next() {
        var iterator = batches.values().iterator();
        if (!iterator.hasNext()) { return null; }
        Batch batch = iterator.next();
        iterator.remove();
        return batch;
    }

    boolean isEmpty() { return batches.isEmpty(); }
    void clear() { batches.clear(); }
}
