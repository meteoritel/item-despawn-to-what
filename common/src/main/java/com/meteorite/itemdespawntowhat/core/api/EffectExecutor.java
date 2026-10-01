package com.meteorite.itemdespawntowhat.core.api;

/**
 * 效果执行器契约：一种效果类型对应一个执行器。
 * 执行器只负责"做什么"，异常隔离、消耗语义与调度由运行时统一处理。
 */
@FunctionalInterface
public interface EffectExecutor<P> {

    // 执行效果；实现不得吞掉异常，由运行时统一捕获记录
    void execute(P effect, EffectContext context);
}
