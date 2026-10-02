package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;
import java.util.function.Function;

/**
 * 效果与条件共用的字段编解码片段。
 * 各具体类型在自己的 RecordCodecBuilder 中直接引用这些片段，避免通用字段在各类型里重复定义。
 * 两点约定：
 * 1) 返回类型必须是 RecordCodecBuilder（而非自由的 MapCodec）：forGetter 的结果绑定在所属记录类型 O 上；
 * 2) **禁止任何 codec 产出 null**——DFU 的 DataResult 内部使用 Optional.of，null 会在解码期抛 NPE。
 *    因此可选字段在 codec 层一律以 Optional 承载，由具体类型在 apply 时用 orElse(null) 落回可空字段。
 */
public final class CommonFields {

    // 效果延迟默认值：0 刻（立即执行）
    public static final int DEFAULT_DELAY_TICKS = 0;
    // 效果概率默认值：1.0（必定执行）
    public static final double DEFAULT_CHANCE = 1.0D;

    private CommonFields() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 效果延迟字段（刻）
    public static <O> RecordCodecBuilder<O, Integer> delayTicks(Function<O, Integer> getter) {
        return Codec.INT.optionalFieldOf(RuleFields.DELAY_TICKS, DEFAULT_DELAY_TICKS).forGetter(getter);
    }

    // 效果概率字段，取值被限制在 [0,1]
    public static <O> RecordCodecBuilder<O, Double> chance(Function<O, Double> getter) {
        return Codec.doubleRange(0.0D, 1.0D).optionalFieldOf(RuleFields.CHANCE, DEFAULT_CHANCE).forGetter(getter);
    }

    // 效果级条件字段：codec 层以 Optional 承载，调用方在 apply 中用 orElse(null) 落回可空字段
    public static <O> RecordCodecBuilder<O, Optional<ConditionExpression>> optionalConditions(
            Function<O, ConditionExpression> getter,
            Codec<ConditionExpression> expressionCodec
    ) {
        return expressionCodec.optionalFieldOf(RuleFields.CONDITIONS)
                .forGetter(value -> Optional.ofNullable(getter.apply(value)));
    }
}
