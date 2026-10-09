package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.type.effect.ConsumeSourceEffect;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * consume_source 执行器：按上下文的每组源成本扣减堆叠，未提供源成本时采用 count，扣空后移除实体。
 * rounds 语义：产出/消耗按 context.rounds() 缩放，并受既有 limit/radius 与可扣数量收敛。
 * 阶段 4 起该效果同时是「每组源成本」的声明：结算任务按规则成本统一扣减源库存，
 * 若仍被派发则以本执行器的真实扣减量为准，避免与结算层重复记账。
 * 约定：delay_ticks / chance / 效果级 conditions 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 */
public final class ConsumeSourceExecutor {

    private static final Logger LOGGER = LogManager.getLogger();

    private ConsumeSourceExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static EffectResult execute(ConsumeSourceEffect effect, EffectContext context) {
        ItemEntity source = context.source();
        if (source == null || !source.isAlive()) {
            return EffectResult.skipped("source_absent");
        }
        // 使用实体上的实时堆叠，sourceStack() 只是触发时刻的快照
        ItemStack stack = source.getItem();
        // 上下文的组成本已按当前源物品解析 counts；源成本缺省时采用效果 count。
        int perRound = context.groupSourceCost() > 0 ? context.groupSourceCost() : effect.count();
        int requested = EffectTargets.saturatedMultiply(perRound, context.rounds());
        int consumed = consumeFromStack(stack, requested);
        if (consumed < requested) {
            LOGGER.debug("consume_source 可扣数量不足，按实际收敛：规则={} 期望={} 实际={}",
                    context.ruleId(), requested, consumed);
        }
        if (consumed > 0 && stack.isEmpty()) {
            source.discard();
        }
        return EffectResult.applied(consumed, consumed < requested ? "short_of_source" : "");
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
