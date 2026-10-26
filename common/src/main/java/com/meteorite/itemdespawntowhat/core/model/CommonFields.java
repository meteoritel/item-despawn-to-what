package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.param.DoubleSpec;
import com.meteorite.itemdespawntowhat.core.api.param.IntSpec;
import com.meteorite.itemdespawntowhat.core.api.param.ParamList;
import com.meteorite.itemdespawntowhat.core.api.param.ParamUnit;

/**
 * 效果通用字段（延迟、概率）的参数声明。
 * 各具体类型在自己的参数声明里引用这些 spec、在 codec 里引用对应的 fieldFor 片段，避免通用字段在各类型里重复定义。
 * 两点约定：
 * 1) 字段构造器返回 RecordCodecBuilder（而非自由的 MapCodec）：forGetter 的结果绑定在所属记录类型 O 上；
 * 2) **禁止任何 codec 产出 null**——DFU 的 DataResult 内部使用 Optional.of，null 会在解码期抛 NPE。
 *    因此可选字段在 codec 层一律由 optionalFieldOf 补齐，可空字段另以 Optional 承载、由具体类型在 apply 时用 orElse(null) 落回。
 */
public final class CommonFields {

    // 效果延迟默认值：0 刻（立即执行）
    public static final int DEFAULT_DELAY_TICKS = 0;
    // 效果概率默认值：1.0（必定执行）
    public static final double DEFAULT_CHANCE = 1.0D;

    // 效果延迟字段（刻）：解码期接受任意非负整数，语义校验按 [0, 1200] 限制
    public static final IntSpec DELAY_TICKS = IntSpec.defaulted(RuleFields.DELAY_TICKS, DEFAULT_DELAY_TICKS,
                    0, Integer.MAX_VALUE)
            .widened()
            .unit(ParamUnit.TICKS)
            .window(0, 1200)
            .label("common.delay_ticks")
            .hint("common.delay_ticks.hint");

    // 效果概率字段，取值被限制在 [0,1]
    public static final DoubleSpec CHANCE = DoubleSpec.defaulted(RuleFields.CHANCE, DEFAULT_CHANCE, 0.0D, 1.0D)
            .unit(ParamUnit.PERCENT)
            .label("common.chance");

    // 效果参数声明的公共尾部（延迟 + 概率），具体类型按「专属字段 + 公共字段」拼接
    public static final ParamList EFFECT_PARAMS = ParamList.of(DELAY_TICKS, CHANCE);

    private CommonFields() {
        throw new UnsupportedOperationException("Utility class");
    }
}
