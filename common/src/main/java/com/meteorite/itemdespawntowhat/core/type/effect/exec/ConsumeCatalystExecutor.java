package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.model.CatalystCost;
import com.meteorite.itemdespawntowhat.core.runtime.CatalystPaymentExecution;
import com.meteorite.itemdespawntowhat.core.type.effect.ConsumeCatalystEffect;

/** consume_catalyst 执行器：为每次效果创建独立的预算付款状态，条件与概率由运行时处理。 */
public final class ConsumeCatalystExecutor {
    private static final String BUDGET_REQUIRED = "budget_required";
    private ConsumeCatalystExecutor() { throw new UnsupportedOperationException("Utility class"); }

    /**
     * 拒绝没有共享预算的直接付款，避免外部调用绕过服务端调度。
     * @param effect 效果参数
     * @param context 执行上下文
     * @return 表示必须使用预算入口的失败回执，不扣除任何物品
     */
    public static EffectResult execute(ConsumeCatalystEffect effect, EffectContext context) {
        return EffectResult.failed(BUDGET_REQUIRED + ": " + effect.type() + " @ " + context.ruleId());
    }

    /**
     * 创建独立付款状态；实际世界查询和计算在 advance 的共享预算内开始。
     * @param effect 催化剂需求
     * @param context 本组上下文
     * @return 可跨 tick 续接的付款状态
     */
    public static EffectExecution start(ConsumeCatalystEffect effect, EffectContext context) {
        return new CatalystPaymentExecution(new CatalystCost(
                effect.items(), effect.count(), effect.radius(), effect.counts()), context);
    }
}
