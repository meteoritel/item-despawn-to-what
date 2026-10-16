package com.meteorite.itemdespawntowhat.core.api;

import org.jetbrains.annotations.Nullable;

/**
 * 一次效果在「成功转化当刻」预先固定的输入。
 * gate 非空表示本次不执行、按该原因记 SKIPPED（条件不满足、概率落空等已支付组内的正常结果）；
 * gate 为空表示本次照常执行，执行期只按本结果推进，不重新判定。
 * 执行器契约的 prepare 入口产出本对象；未产出准备的类型不在成功转化当刻固定，
 * 条件与概率仍在首次执行时判定。
 * @param gate 跳过原因；null 表示本次照常执行
 */
public record EffectPreparation(@Nullable String gate) {

    /**
     * 本次照常执行。
     * @return gate 为空的固定输入
     */
    public static EffectPreparation ready() {
        return new EffectPreparation(null);
    }

    /**
     * 本次不执行，按给定原因记跳过。
     * @param reason 跳过原因，随回执 detail 交给结算层
     * @return gate 为给定原因的固定输入
     */
    public static EffectPreparation gated(String reason) {
        return new EffectPreparation(reason);
    }
}
