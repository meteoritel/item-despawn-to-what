package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.type.effect.ConsumeSourceEffect;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * consume_source 执行器：按 count × rounds 扣减源掉落物的物品堆叠，扣空后移除该掉落物。
 * rounds 语义：产出/消耗按 context.rounds() 缩放，并受既有 limit/radius 与可扣数量收敛。
 * 约定：delay_ticks / chance / 效果级 conditions 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 */
public final class ConsumeSourceExecutor {

    private static final Logger LOGGER = LogManager.getLogger();

    private ConsumeSourceExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void execute(ConsumeSourceEffect effect, EffectContext context) {
        ItemEntity source = context.source();
        if (source == null || !source.isAlive()) {
            return;
        }
        // 使用实体上的实时堆叠，sourceStack() 只是触发时刻的快照
        ItemStack stack = source.getItem();
        // 整堆一次性转化：期望扣减 count × rounds，按实际可扣数量收敛（不足时不报错、只记录）
        int requested = EffectTargets.saturatedMultiply(effect.count(), context.rounds());
        int consumed = consumeFromStack(stack, requested);
        if (consumed < requested) {
            LOGGER.debug("consume_source 可扣数量不足，按实际收敛：规则={} 期望={} 实际={}",
                    context.ruleId(), requested, consumed);
        }
        if (consumed > 0 && stack.isEmpty()) {
            source.discard();
        }
    }

    // 从堆叠中扣减期望数量并返回实际扣减量；期望超过剩余数量时按实际收敛
    static int consumeFromStack(ItemStack stack, int requested) {
        int consumed = Math.min(Math.max(0, requested), stack.getCount());
        if (consumed > 0) {
            stack.shrink(consumed);
        }
        return consumed;
    }
}
