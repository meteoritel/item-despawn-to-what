package com.meteorite.itemdespawntowhat.core.api;

/**
 * 效果执行器契约：一种效果类型对应一个执行器。
 * 执行器只负责"做什么"，异常隔离、消耗语义与调度由运行时统一处理。
 * 执行器必须返回真实回执（实际完成量 / 已受理量 / 跳过原因），
 * 结算层只按回执记账，禁止用计划数量冒充成功量。
 */
@FunctionalInterface
public interface EffectExecutor<P> {

    // 执行效果并返回真实回执；实现不得吞掉异常，由运行时统一捕获记录
    EffectResult execute(P effect, EffectContext context);

    /**
     * 为一次效果创建独立执行状态；长循环执行器应覆盖此方法，在共享预算内续接。
     * 默认适配同步执行器，保留现有 lambda 注册方式；一次原子调用仍受软预算限制。
     * @param effect 效果参数
     * @param context 本组上下文
     * @return 仅供本次效果使用的执行状态
     */
    default EffectExecution start(P effect, EffectContext context) {
        return budget -> {
            EffectResult result = execute(effect, context);
            return result == null ? EffectResult.failed("no_result") : result;
        };
    }
}
