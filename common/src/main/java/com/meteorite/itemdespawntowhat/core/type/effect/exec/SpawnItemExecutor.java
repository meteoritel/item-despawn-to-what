package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepWorkCounter;
import com.meteorite.itemdespawntowhat.core.type.effect.EntityProduct;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** 掉落物产出子类执行器：产出量按物品件数计算，受限时按件数分批生成。 */
public final class SpawnItemExecutor {

    /**
     * 单批最多写入的掉落物实体数：行为上界，取值与语义保持不变，只限制单步产出量。
     * 每个真正写入世界的 ItemEntity 按 1 个工作单位回执（见 {@link StepWorkCounter#report(int)}），
     * 故该上界同时是一个调度步骤最多计入的计费单位数。
     */
    private static final int ITEM_SPAWNS_PER_RUN = 16;

    private SpawnItemExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 单组一轮：产出量 = count × rounds，分批过程由 ItemBatch 按实际生成件数回执
    public static EffectResult execute(EntityProduct.Item effect, EffectContext context) {
        Item item = EffectTargets.resolveOrThrow(effect.item(), BuiltInRegistries.ITEM, context.random(),
                "item");
        // 单组一轮：产出量 = count × rounds
        int count = EffectTargets.saturatedMultiply(effect.count(), context.rounds());
        new ItemBatch(context, item, count).run();
        return EffectResult.deferred(count, "batched");
    }

    /** 分批生成掉落物，仅保留剩余物品数，每批最多生成 {@link #ITEM_SPAWNS_PER_RUN} 个实体。 */
    private static final class ItemBatch implements Runnable {
        private final EffectContext context;
        private final Item item;
        private int remaining;
        private ItemBatch(EffectContext context, Item item, int count) {
            this.context = context; this.item = item; this.remaining = count;
        }
        @Override
        public void run() {
            ServerLevel level = context.level();
            Vec3 position = context.position();
            if (!com.meteorite.itemdespawntowhat.core.runtime.LoadedChunks.containsArea(level, BlockPos.containing(position), 1)) {
                context.schedule(20, this); return;
            }
            int capacity = remaining;
            if (capacity <= 0) { return; }
            int maxStack = Math.max(1, item.getDefaultMaxStackSize());
            for (int i = 0; i < ITEM_SPAWNS_PER_RUN && capacity > 0; i++) {
                int size = Math.min(capacity, maxStack);
                ItemEntity entity = new ItemEntity(level,
                        position.x + (level.random.nextDouble() - 0.5) * 0.3, position.y + 0.1,
                        position.z + (level.random.nextDouble() - 0.5) * 0.3, new ItemStack(item, size));
                entity.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.1, 0.2,
                        (level.random.nextDouble() - 0.5) * 0.1);
                // 转化产物：走显式产物入口，授予临时保护与冷却
                EffectTargets.addConversionProduct(context, entity);
                // 真实完成量回执：按实际写入世界的件数计
                context.reportProgress(size);
                // 计价：一个真正写入世界的实体记 1 个工作单位
                StepWorkCounter.report(1);
                capacity -= size;
                remaining -= size;
            }
            if (remaining > 0) { context.schedule(1, this); }
        }
    }
}
