package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.type.effect.SpawnEntityEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * spawn_entity 执行器：在触发位置生成实体，并按 limit/radius 做邻近累积检测。
 * rounds 语义：产出/消耗按 context.rounds() 缩放，并受既有 limit/radius 与可扣数量收敛。
 * 约定：delay_ticks / chance / 效果级 conditions 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 */
public final class SpawnEntityExecutor {

    private static final Logger LOGGER = LogManager.getLogger();

    // 配置了 limit 但未配置 radius 时使用的邻近检测半径
    public static final int DEFAULT_SEARCH_RADIUS = 6;

    private SpawnEntityExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void execute(SpawnEntityEffect effect, EffectContext context) {
        EntityType<?> type = EffectTargets.resolveOrThrow(effect.entity(), BuiltInRegistries.ENTITY_TYPE,
                context.random(), SpawnEntityEffect.ENTITY_FIELD);
        // 整堆一次性转化：产出量 = count × rounds
        int requested = EffectTargets.saturatedMultiply(effect.count(), context.rounds());
        int count = allowedCount(effect, context, type, requested);
        if (count < requested) {
            LOGGER.debug("spawn_entity 产出被邻近上限收敛：规则={} 期望={} 实际={}", context.ruleId(), requested, count);
        }
        if (count <= 0) {
            return;
        }
        ServerLevel level = context.level();
        Vec3 position = context.position();
        for (int i = 0; i < count; i++) {
            Entity spawned = type.create(level);
            if (spawned == null) {
                throw new IllegalStateException(effect.entity().serialized() + " 在该维度无法创建实体");
            }
            spawned.moveTo(position.x + (level.random.nextDouble() - 0.5) * 0.5,
                    position.y,
                    position.z + (level.random.nextDouble() - 0.5) * 0.5,
                    0.0F, 0.0F);
            if (spawned instanceof AgeableMob ageableMob) {
                ageableMob.setAge(effect.age());
            }
            level.addFreshEntity(spawned);
        }
    }

    // 结合 limit/radius 计算本次实际可产出的实体数量；limit 为空表示不限制
    private static int allowedCount(SpawnEntityEffect effect, EffectContext context, EntityType<?> type, int requested) {
        if (effect.limit() == null) {
            return requested;
        }
        int radius = effect.radius() == null ? DEFAULT_SEARCH_RADIUS : effect.radius();
        Vec3 position = context.position();
        AABB box = EffectTargets.blockBox(BlockPos.containing(position.x, position.y, position.z), radius);
        int existing = Math.min(effect.limit(), context.level().getEntities(type, box, Entity::isAlive).size());
        return Math.max(0, Math.min(requested, effect.limit() - existing));
    }
}
