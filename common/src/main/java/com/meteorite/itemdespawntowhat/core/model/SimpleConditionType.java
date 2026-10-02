package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.ConditionContext;
import com.meteorite.itemdespawntowhat.core.api.ConditionEvaluator;
import com.meteorite.itemdespawntowhat.core.api.Evaluability;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 由「id + 参数编解码器 + 参数校验器 + 求值器 + 可求值性检查」组成的通用条件类型定义。
 * 内置条件类型用它一行完成注册；不需要上下文门禁的类型用四参构造，可求值性恒为 AVAILABLE。
 */
public record SimpleConditionType<P extends Condition>(
        ResourceLocation id,
        MapCodec<P> codec,
        Validator<P> validator,
        ConditionEvaluator<P> evaluator,
        EvaluabilityCheck<P> evaluabilityCheck
) implements ConditionType<P> {

    // 四参构造：可求值性恒为 AVAILABLE
    public SimpleConditionType(ResourceLocation id,
                               MapCodec<P> codec,
                               Validator<P> validator,
                               ConditionEvaluator<P> evaluator) {
        this(id, codec, validator, evaluator, (params, context) -> Evaluability.AVAILABLE);
    }

    @Override
    public boolean validateParams(P params, IssueCollector issues, String fieldPath) {
        return validator.validate(params, issues, fieldPath);
    }

    // 可求值性门禁委托给检查器
    @Override
    public Evaluability evaluability(P params, ConditionContext context) {
        return evaluabilityCheck.check(params, context);
    }

    // 参数校验器：返回 false 表示参数非法（具体问题由实现写入 issues）
    @FunctionalInterface
    public interface Validator<P> {
        boolean validate(P params, IssueCollector issues, String fieldPath);
    }

    // 可求值性检查器：返回 UNAVAILABLE 表示当前上下文不足以判定该条件
    @FunctionalInterface
    public interface EvaluabilityCheck<P> {
        Evaluability check(P params, ConditionContext context);
    }
}
