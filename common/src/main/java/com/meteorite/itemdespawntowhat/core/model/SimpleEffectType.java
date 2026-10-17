package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.EffectExecutor;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 由「id + 参数编解码器 + 参数校验器 + 执行器 + 一次性标记」组成的通用效果类型定义。
 * 内置效果类型用它一行完成注册，无需为每种类型各写一个匿名实现。
 * oneShot 是该类型注册时声明的一次性准入信息：为真表示不参与候选容量计算、同一源最多尝试一组，
 * 运行时按此判定，不读取执行回执上的同名字段。
 */
public record SimpleEffectType<P extends Effect>(
        ResourceLocation id,
        MapCodec<P> codec,
        Validator<P> validator,
        EffectExecutor<P> executor,
        boolean oneShot
) implements EffectType<P> {

    // 四参构造：未给出一次性标记时按普通效果处理（oneShot = false）
    public SimpleEffectType(ResourceLocation id, MapCodec<P> codec, Validator<P> validator,
                            EffectExecutor<P> executor) {
        this(id, codec, validator, executor, false);
    }

    @Override
    public boolean validateParams(P params, IssueCollector issues, String fieldPath) {
        return validator.validate(params, issues, fieldPath);
    }

    // 参数校验器：返回 false 表示参数非法（具体问题由实现写入 issues）
    @FunctionalInterface
    public interface Validator<P> {
        boolean validate(P params, IssueCollector issues, String fieldPath);
    }
}
