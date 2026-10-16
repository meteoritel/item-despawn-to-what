package com.meteorite.itemdespawntowhat.core.model;

import org.jetbrains.annotations.Nullable;

/**
 * 一次效果在「成功转化当刻」预先固定的输入。
 * gate 非空表示本次不执行、按该原因记 SKIPPED（条件不满足、概率落空等已支付组内的正常结果）；
 * gate 为空表示本次照常执行，执行期只按本结果推进，不重新判定。
 * 执行器契约的 prepare 入口产出本对象；未产出准备的类型不在成功转化当刻固定，
 * 条件与概率仍在首次执行时判定。
 */
public record EffectPreparation(@Nullable String gate) {

    // 本次照常执行
    public static EffectPreparation ready() {
        return new EffectPreparation(null);
    }

    // 本次不执行，按原因记跳过
    public static EffectPreparation gated(String reason) {
        return new EffectPreparation(reason);
    }
}
