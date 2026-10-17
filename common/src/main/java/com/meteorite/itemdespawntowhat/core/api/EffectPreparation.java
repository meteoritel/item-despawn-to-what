package com.meteorite.itemdespawntowhat.core.api;

import org.jetbrains.annotations.Nullable;

/**
 * 一次效果在「成功转化当刻」预先固定的输入：跳过判定与类型自有的固定输入。
 * gate 非空表示本次不执行、按该原因记 SKIPPED（条件不满足、概率落空等已支付组内的正常结果）；
 * gate 为空表示本次照常执行，执行期只按本结果推进，不重新判定。
 * 运行时按「候选 + 槽位 + 组序号」各固定一份，因此多组派发不会复用同一份随机判定；
 * 一次性效果只尝试一组，固定一份即可。
 * 类型自有的固定输入由该类型在自己的实现里承载：每个效果类型声明一个不可变记录实现本接口，
 * 只放成功转化当刻已经确定的值（例如已解析的方块状态、已抽取的药水效果）；
 * 执行器按自身类型的实现取回，不使用无类型的通用容器，本接口也不依赖任何模型类型。
 * 未在本阶段固定输入的类型不产出准备，其条件与概率仍在首次执行时判定。
 */
public interface EffectPreparation {

    /**
     * 本次是否执行。
     *
     * @return 跳过原因；null 表示本次照常执行
     */
    default @Nullable String gate() {
        return null;
    }

    /**
     * 本次照常执行，且本类型没有额外固定输入。
     *
     * @return gate 为空的固定结果
     */
    static EffectPreparation ready() {
        return Gate.PASS;
    }

    /**
     * 本次不执行，按给定原因记跳过。
     *
     * @param reason 跳过原因，随回执 detail 交给结算层
     * @return gate 为给定原因的固定结果
     */
    static EffectPreparation gated(String reason) {
        return new Gate(reason);
    }

    /**
     * 只承载跳过判定的通用实现；自身没有固定输入的类型可直接使用。
     *
     * @param gate 跳过原因；null 表示照常执行
     */
    record Gate(@Nullable String gate) implements EffectPreparation {

        /** 照常执行且无额外输入的共享实例。 */
        static final Gate PASS = new Gate(null);
    }
}
