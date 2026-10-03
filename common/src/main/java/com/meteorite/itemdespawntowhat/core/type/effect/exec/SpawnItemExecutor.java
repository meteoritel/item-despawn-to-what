package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.type.effect.SpawnItemEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * spawn_item 执行器：在触发位置生成物品，并按 limit/radius 做邻近累积检测。
 * rounds 语义：产出/消耗按 context.rounds() 缩放，并受既有 limit/radius 与可扣数量收敛。
 * 阶段 4 起每批真实生成的件数通过 context.reportProgress 回执，计划量与完成量分开记账。
 * 约定：delay_ticks / chance / 效果级 conditions 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 */
public final class SpawnItemExecutor {

    private static final Logger LOGGER = LogManager.getLogger();

    // 配置了 limit 但未配置 radius 时使用的邻近检测半径
    public static final int DEFAULT_SEARCH_RADIUS = 6;

    private SpawnItemExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static EffectResult execute(SpawnItemEffect effect, EffectContext context) {
        Item item = EffectTargets.resolveOrThrow(effect.item(), BuiltInRegistries.ITEM, context.random(),
                SpawnItemEffect.ITEM_FIELD);
        // 单组一轮：产出量 = count × rounds
        int requested = EffectTargets.saturatedMultiply(effect.count(), context.rounds());
        int count = allowedCount(effect, context, item, requested);
        if (count < requested) {
            LOGGER.debug("spawn_item 产出被邻近上限收敛：规则={} 期望={} 实际={}", context.ruleId(), requested, count);
        }
        if (count <= 0) {
            return EffectResult.skipped("limit_reached");
        }
        spawnItems(effect, context, item, count);
        return EffectResult.deferred(count, "batched");
    }

    // 结合 limit/radius 计算本次实际可产出的物品数量；limit 为空表示不限制
    private static int allowedCount(SpawnItemEffect effect, EffectContext context, Item item, int requested) {
        if (effect.limit() == null) {
            return requested;
        }
        int radius = effect.radius() == null ? DEFAULT_SEARCH_RADIUS : effect.radius();
        int existing = countNearby(context, item, radius, effect.limit());
        return Math.max(0, Math.min(requested, effect.limit() - existing));
    }

    // 统计半径内同类产物总量；达到 limit 立即返回，避免无谓遍历
    // 此处借用 Minecraft 管理的实例，生命周期由游戏负责，不能在此关闭。
    @SuppressWarnings("resource")
    private static int countNearby(EffectContext context, Item item, int radius, int limit) {
        Vec3 position = context.position();
        AABB box = EffectTargets.blockBox(BlockPos.containing(position.x, position.y, position.z), radius);
        int[] total = {0};
        context.level().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(ItemEntity.class), box,
                nearby -> {
                    if (nearby.isAlive() && nearby.getItem().is(item)) {
                        total[0] = EffectTargets.saturatedAdd(total[0], nearby.getItem().getCount());
                    }
                    return total[0] >= limit;
                }, new java.util.ArrayList<>(1), 1);
        return total[0];
    }

    // 每批重新计算邻近余量，多个转化任务交错时仍受 limit 收敛。
    private static void spawnItems(SpawnItemEffect effect, EffectContext context, Item item, int count) {
        new ItemBatch(effect, context, item, count).run();
    }

    /** 分批生成掉落物，仅保留剩余物品数，每批最多生成 16 个实体。 */
    private static final class ItemBatch implements Runnable {
        private final SpawnItemEffect effect;
        private final EffectContext context;
        private final Item item;
        private int remaining;
        private ItemBatch(SpawnItemEffect effect, EffectContext context, Item item, int count) {
            this.effect = effect; this.context = context; this.item = item; this.remaining = count;
        }
        @Override
        public void run() {
            ServerLevel level = context.level();
            Vec3 position = context.position();
            if (!com.meteorite.itemdespawntowhat.core.runtime.LoadedChunks.containsArea(level, BlockPos.containing(position), 1)) {
                context.schedule(20, this); return;
            }
            int capacity = allowedCount(effect, context, item, remaining);
            if (capacity <= 0) { return; }
            int maxStack = Math.max(1, item.getDefaultMaxStackSize());
            for (int i = 0; i < 16 && capacity > 0; i++) {
                int size = Math.min(capacity, maxStack);
                ItemEntity entity = new ItemEntity(level,
                        position.x + (level.random.nextDouble() - 0.5) * 0.3, position.y + 0.1,
                        position.z + (level.random.nextDouble() - 0.5) * 0.3, new ItemStack(item, size));
                entity.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.1, 0.2,
                        (level.random.nextDouble() - 0.5) * 0.1);
                // 转化产物：走显式产物入口，授予临时保护与冷却（D18）
                EffectTargets.addConversionProduct(context, entity);
                // 真实完成量回执：按实际写入世界的件数计
                context.reportProgress(size);
                capacity -= size;
                remaining -= size;
            }
            if (remaining > 0) { context.schedule(1, this); }
        }
    }
}
