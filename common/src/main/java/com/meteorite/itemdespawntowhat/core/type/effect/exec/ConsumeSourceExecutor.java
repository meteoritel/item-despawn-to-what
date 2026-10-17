package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.type.effect.ConsumeSourceEffect;

/**
 * consume_source 执行器：把本组已支付的源成本作为本次真实完成量回执。
 * 该效果同时是「每组源成本」的声明：整堆源物品在成功转化当刻已转入结算库存，
 * 每组开组时由结算层从库存扣减并记为已消费（{@code consumedSources}），
 * 因此本执行器只读上下文里的本组已付成本，绝不再从实体或库存二次扣减。
 * rounds 语义：产出/消耗按 context.rounds() 缩放（当前恒为 1，一轮 = 一组）。
 * 约定：delay_ticks / chance / 效果级 conditions 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 */
public final class ConsumeSourceExecutor {

    private ConsumeSourceExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 按本组实际已支付的源成本回执真实完成量，不读取源实体、不再扣减库存。
     * @param effect 效果参数
     * @param context 本组上下文
     * @return 本组已付源件数的完成回执
     */
    public static EffectResult execute(ConsumeSourceEffect effect, EffectContext context) {
        // 上下文的组成本已按当前源物品解析 counts；源成本缺省时采用效果 count。
        int perGroup = context.groupSourceCost() > 0 ? context.groupSourceCost() : effect.count();
        return EffectResult.applied(EffectTargets.saturatedMultiply(perGroup, context.rounds()));
    }
}
