package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.EffectExecutor;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.RuleRuntimeLimits;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * 由「id + 参数编解码器 + 参数校验器 + 运行期上限校验器 + 执行器 + 一次性标记 + 产出类别解析器
 * + 容量需求解析器」组成的通用效果类型定义。
 * 内置效果类型用它一行完成注册，无需为每种类型各写一个匿名实现。
 * oneShot 是该类型注册时声明的一次性准入信息：为真表示不参与候选容量计算、同一源最多尝试一组，
 * 运行时按此判定，不读取执行回执上的同名字段。
 * productKind 是该类型的产出类别解析器：默认非产出，产出子类随参数变化的类型按实例参数解析。
 * capacityDemand 是该类型的每轮一组容量需求解析器：默认不占容量，产出类型未知的效果按保守下界声明。
 */
public record SimpleEffectType<P extends Effect>(
        ResourceLocation id,
        MapCodec<P> codec,
        Validator<P> validator,
        RuntimeLimitValidator<P> runtimeLimitValidator,
        EffectExecutor<P> executor,
        boolean oneShot,
        Function<P, ProductKind> productKind,
        Function<P, CapacityDemand> capacityDemand
) implements EffectType<P> {

    public SimpleEffectType {
        runtimeLimitValidator = runtimeLimitValidator == null ? (params, limits, issues, fieldPath) -> true : runtimeLimitValidator;
        productKind = productKind == null ? params -> ProductKind.NONE : productKind;
        capacityDemand = capacityDemand == null ? params -> CapacityDemand.NONE : capacityDemand;
    }

    // 四参构造：未给出一次性标记时按普通效果处理（oneShot = false）
    public SimpleEffectType(ResourceLocation id, MapCodec<P> codec, Validator<P> validator,
                            EffectExecutor<P> executor) {
        this(id, codec, validator, executor, false);
    }

    // 五参构造：只声明一次性标记，产出类别按非产出处理
    public SimpleEffectType(ResourceLocation id, MapCodec<P> codec, Validator<P> validator,
                            EffectExecutor<P> executor, boolean oneShot) {
        this(id, codec, validator, null, executor, oneShot, params -> ProductKind.NONE, params -> CapacityDemand.NONE);
    }

    // 五参构造：只声明固定产出类别，按普通效果处理
    public SimpleEffectType(ResourceLocation id, MapCodec<P> codec, Validator<P> validator,
                            EffectExecutor<P> executor, ProductKind productKind) {
        this(id, codec, validator, null, executor, false, params -> productKind, params -> CapacityDemand.NONE);
    }

    // 六参构造：同时声明一次性标记与固定产出类别
    public SimpleEffectType(ResourceLocation id, MapCodec<P> codec, Validator<P> validator,
                            EffectExecutor<P> executor, boolean oneShot, ProductKind productKind) {
        this(id, codec, validator, null, executor, oneShot, params -> productKind, params -> CapacityDemand.NONE);
    }

    // 六参构造：同时声明一次性标记与随参数变化的产出类别解析器（运行期上限校验按通过处理）
    public SimpleEffectType(ResourceLocation id, MapCodec<P> codec, Validator<P> validator,
                            EffectExecutor<P> executor, boolean oneShot, Function<P, ProductKind> productKind) {
        this(id, codec, validator, null, executor, oneShot, productKind, params -> CapacityDemand.NONE);
    }

    // 六参构造：声明运行期上限校验器与固定产出类别，一次性标记按普通效果处理
    public SimpleEffectType(ResourceLocation id, MapCodec<P> codec, Validator<P> validator,
                            RuntimeLimitValidator<P> runtimeLimitValidator, EffectExecutor<P> executor,
                            ProductKind productKind) {
        this(id, codec, validator, runtimeLimitValidator, executor, false, params -> productKind, params -> CapacityDemand.NONE);
    }

    // 六参构造：声明固定产出类别与容量需求解析器，按普通效果处理
    public SimpleEffectType(ResourceLocation id, MapCodec<P> codec, Validator<P> validator,
                            EffectExecutor<P> executor, ProductKind productKind,
                            Function<P, CapacityDemand> capacityDemand) {
        this(id, codec, validator, null, executor, false, params -> productKind, capacityDemand);
    }

    // 七参构造：同时声明一次性标记、产出类别解析器与容量需求解析器
    public SimpleEffectType(ResourceLocation id, MapCodec<P> codec, Validator<P> validator,
                            EffectExecutor<P> executor, boolean oneShot, Function<P, ProductKind> productKind,
                            Function<P, CapacityDemand> capacityDemand) {
        this(id, codec, validator, null, executor, oneShot, productKind, capacityDemand);
    }

    @Override
    public ProductKind productKind(P params) {
        return productKind.apply(params);
    }

    /**
     * {@inheritDoc}
     *
     * <p>由注册时声明的解析器按实例参数给出；未声明时视为不占容量。</p>
     */
    @Override
    public CapacityDemand capacityDemand(P params) {
        return capacityDemand.apply(params);
    }

    @Override
    public boolean validateParams(P params, IssueCollector issues, String fieldPath) {
        return validator.validate(params, issues, fieldPath);
    }

    @Override
    public boolean validateRuntimeLimits(P params, RuleRuntimeLimits limits, IssueCollector issues, String fieldPath) {
        return runtimeLimitValidator.validate(params, limits, issues, fieldPath);
    }

    // 参数校验器：返回 false 表示参数非法（具体问题由实现写入 issues）
    @FunctionalInterface
    public interface Validator<P> {
        boolean validate(P params, IssueCollector issues, String fieldPath);
    }

    // 运行期上限校验器：返回 false 表示超出服务端配置允许的范围（具体问题由实现写入 issues）
    @FunctionalInterface
    public interface RuntimeLimitValidator<P> {
        boolean validate(P params, RuleRuntimeLimits limits, IssueCollector issues, String fieldPath);
    }
}
