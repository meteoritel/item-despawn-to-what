package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
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

    public static void execute(SpawnItemEffect effect, EffectContext context) {
        Item item = EffectTargets.resolveOrThrow(effect.item(), BuiltInRegistries.ITEM, context.random(),
                SpawnItemEffect.ITEM_FIELD);
        // 整堆一次性转化：产出量 = count × rounds
        int requested = EffectTargets.saturatedMultiply(effect.count(), context.rounds());
        int count = allowedCount(effect, context, item, requested);
        if (count < requested) {
            LOGGER.debug("spawn_item 产出被邻近上限收敛：规则={} 期望={} 实际={}", context.ruleId(), requested, count);
        }
        if (count <= 0) {
            return;
        }
        spawnItems(context, item, count);
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
    private static int countNearby(EffectContext context, Item item, int radius, int limit) {
        Vec3 position = context.position();
        AABB box = EffectTargets.blockBox(BlockPos.containing(position.x, position.y, position.z), radius);
        int total = 0;
        for (ItemEntity nearby : context.level().getEntitiesOfClass(ItemEntity.class, box, ItemEntity::isAlive)) {
            if (!nearby.getItem().is(item)) {
                continue;
            }
            total = EffectTargets.saturatedAdd(total, nearby.getItem().getCount());
            if (total >= limit) {
                return total;
            }
        }
        return total;
    }

    // 按物品最大堆叠数拆分为若干掉落物实体
    private static void spawnItems(EffectContext context, Item item, int count) {
        ServerLevel level = context.level();
        Vec3 position = context.position();
        int maxStackSize = Math.max(1, item.getDefaultMaxStackSize());
        int remaining = count;
        while (remaining > 0) {
            int size = Math.min(remaining, maxStackSize);
            ItemEntity entity = new ItemEntity(level,
                    position.x + (level.random.nextDouble() - 0.5) * 0.3,
                    position.y + 0.1,
                    position.z + (level.random.nextDouble() - 0.5) * 0.3,
                    new ItemStack(item, size));
            entity.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.1, 0.2,
                    (level.random.nextDouble() - 0.5) * 0.1);
            level.addFreshEntity(entity);
            remaining -= size;
        }
    }
}
