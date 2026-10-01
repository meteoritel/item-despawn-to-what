package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.type.effect.ConsumeSourceEffect;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * consume_source 执行器：按 count 扣减源掉落物的物品堆叠，扣空后移除该掉落物。
 * 约定：delay_ticks / chance / 效果级 conditions 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 */
public final class ConsumeSourceExecutor {

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
        int consumed = Math.min(effect.count(), stack.getCount());
        if (consumed <= 0) {
            return;
        }
        stack.shrink(consumed);
        if (stack.isEmpty()) {
            source.discard();
        }
    }
}
