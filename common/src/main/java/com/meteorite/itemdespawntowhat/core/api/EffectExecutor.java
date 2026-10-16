package com.meteorite.itemdespawntowhat.core.api;

import com.meteorite.itemdespawntowhat.core.model.EffectPreparation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

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
     * 在成功转化当刻固定本次效果的输入，供源实体清空后仍按固定输入执行。
     * 产出非 null 结果即表示本类型采用「成功转化当刻固定输入」的路径：效果级条件与概率在成功转化当刻判定一次并随任务携带。
     * 默认返回 null，这类效果的条件与概率仍在首次执行时判定，结果不随任务携带。
     * 覆盖本方法的类型必须通过匿名实现暴露（lambda 注册只表达 execute），并与按准备结果创建执行状态的重载成对覆盖。
     * 传入的是成功转化当刻仍可读的输入，不包含尚未确定的世界状态。
     * @param effect 效果参数
     * @param level 触发维度
     * @param position 成功当刻固定的触发点位置
     * @return 本次执行的固定输入；null 表示本类型没有准备阶段
     */
    default @Nullable EffectPreparation prepare(P effect, ServerLevel level, Vec3 position) {
        return null;
    }

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

    /**
     * 按成功转化当刻固定的输入创建执行状态；覆盖 prepare 的类型必须成对覆盖本方法。
     * 准备为「跳过」时按该原因直接产出跳过回执，不重新判定条件或概率，也不做世界写入。
     * @param effect 效果参数
     * @param context 本组上下文
     * @param preparation 成功转化当刻固定的输入；null 表示无准备阶段
     * @return 仅供本次效果使用的执行状态
     */
    default EffectExecution start(P effect, EffectContext context, @Nullable EffectPreparation preparation) {
        if (preparation != null && preparation.gate() != null) {
            String reason = preparation.gate();
            return budget -> EffectResult.skipped(reason);
        }
        return start(effect, context);
    }
}
