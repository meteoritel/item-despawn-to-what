package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.type.effect.ConsumeCatalystEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import com.meteorite.itemdespawntowhat.core.model.CatalystCost;
import com.meteorite.itemdespawntowhat.core.runtime.CatalystAllocation;

/**
 * consume_catalyst 执行器：在 radius 范围内分别支付每个引用的数量，任何引用不足则整轮不扣。
 * rounds 语义：产出/消耗按 context.rounds() 缩放，并受既有 limit/radius 与可扣数量收敛。
 * 约定：delay_ticks / chance / 效果级 conditions 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 */
public final class ConsumeCatalystExecutor {

    private static final Logger LOGGER = LogManager.getLogger();

    private ConsumeCatalystExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 此处借用 Minecraft 管理的实例，生命周期由游戏负责，不能在此关闭。
    @SuppressWarnings("resource")
    public static EffectResult execute(ConsumeCatalystEffect effect, EffectContext context) {
        ServerLevel level = context.level();
        ItemEntity source = context.source();
        Vec3 position = context.position();
        BlockPos center = BlockPos.containing(position.x, position.y, position.z);
        AABB box = EffectTargets.blockBox(center, effect.radius());
        List<ItemEntity> candidates = level.getEntitiesOfClass(ItemEntity.class, box,
                entity -> entity != source && entity.isAlive() && !entity.getItem().isEmpty()
                        && matchesAny(effect.items(), entity.getItem()));
        if (!candidates.isEmpty()) {
            // 由近及远消耗，使结果不依赖实体遍历顺序
            candidates.sort(Comparator.comparingDouble((ItemEntity entity) -> entity.distanceToSqr(position)));
        }
        Map<ItemEntity, Integer> payment = CatalystAllocation.payment(new CatalystCost(
                effect.items(), effect.count(), effect.radius(), effect.counts()), candidates, context.rounds());
        if (payment.isEmpty()) {
            LOGGER.debug("consume_catalyst 数量不足，整轮不扣：规则={}", context.ruleId());
            return EffectResult.skipped("catalyst_absent");
        }
        int consumed = 0;
        for (var entry : payment.entrySet()) {
            ItemStack stack = entry.getKey().getItem();
            stack.shrink(entry.getValue());
            consumed += entry.getValue();
            if (stack.isEmpty()) entry.getKey().discard();
        }
        return EffectResult.applied(consumed);
    }

    // 物品/标签匹配统一委托 EffectTargets.matchesAny：催化剂固定成本与效果式消耗必须同口径，禁止在此复刻逻辑
    private static boolean matchesAny(List<TaggedId> references, ItemStack stack) {
        return EffectTargets.matchesAny(references, stack);
    }
}
