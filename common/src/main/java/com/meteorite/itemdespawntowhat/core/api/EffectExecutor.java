package com.meteorite.itemdespawntowhat.core.api;

/**
 * 效果执行器契约：一种效果类型对应一个执行器。
 * 执行器只负责"做什么"，异常隔离、消耗语义与调度由运行时统一处理。
 * 阶段 4 起执行器必须返回真实回执（实际完成量 / 已受理量 / 跳过原因），
 * 结算层只按回执记账，禁止用计划数量冒充成功量。
 */
@FunctionalInterface
public interface EffectExecutor<P> {

    // 执行效果并返回真实回执；实现不得吞掉异常，由运行时统一捕获记录
    EffectResult execute(P effect, EffectContext context);
}
