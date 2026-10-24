package com.meteorite.itemdespawntowhat.core.api;

import org.jetbrains.annotations.Nullable;

/**
 * 一次效果在「成功转化当刻」预先固定的输入：跳过判定与类型自有的固定输入。
 * gate 非空表示本次不执行、按该原因记 SKIPPED（概率落空属已支付组内的正常结果）；
 * gate 为空表示本次照常执行，执行期只按本结果推进，不重新判定。
 * 运行时按「候选 + 槽位 + 组序号」各固定一份，因此多组派发的概率判定不会复用同一份随机结果；
 * 一次性效果只尝试一组，固定一份即可。声明 {@link SharedPreparation} 的类型在组间复用同一份固定输入；
 * 声明 {@link GroupedPreparation} 的类型保留各组固定输入，只连接类型自己的执行期状态。
 * 类型自有的固定输入由该类型在自己的实现里承载：每个效果类型声明一个不可变记录实现本接口，
 * 只放成功转化当刻已经确定的值（例如已解析的方块状态、已抽取的药水效果）；
 * 执行器按自身类型的实现取回，不使用无类型的通用容器，本接口也不依赖任何模型类型。
 * 未在本阶段固定输入的类型不产出准备，其概率仍在首次执行时判定。
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
     * 固定输入可跨组共享的标记：同一候选同一槽位的各组共用同一份实例，
     * 因此实例在执行期累计的状态（例如多组共同消耗的邻域计数账）对各组都可见。
     * 只有固定输入求值不含按组独立随机结果的类型才可声明本接口：声明后该输入在整个转化里只求值一次，
     * 其中依赖随机源的选择也随之只做一次；概率的跳过判定仍按组独立，不受影响。
     * 未声明本接口的类型保持每组各固定一份，随机判定互不复用。
     */
    interface SharedPreparation extends EffectPreparation {
    }

    /**
     * 各组保留独立固定输入、仅共享执行期状态的准备结果。
     * 运行时只连接同一结算、同一候选、同一槽位的结果，且在效果执行前完成连接。
     * 随机抽取的目标仍归各组所有，类型实现按目标分别维护共享额度。
     */
    interface GroupedPreparation extends EffectPreparation {
        /**
         * 连接同槽位首份准备结果的执行状态，不替换本组已经固定的输入。
         *
         * @param first 同一槽位最先登记的准备结果
         */
        void shareStateWith(GroupedPreparation first);
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
