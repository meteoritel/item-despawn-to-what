package com.meteorite.itemdespawntowhat.core.api;

import java.util.Objects;

/**
 * 效果推进结果：将终态、预算续接、正常间隔与区块等待分开交给调用方处理。
 * 等待结果只描述需求，不持有世界或执行回调；游标、固定目标和已完成量由执行状态保留。
 */
public sealed interface EffectStep {
    /**
     * 最终真实回执；调用方只结算一次。
     * @param result 最终回执
     */
    record Done(EffectResult result) implements EffectStep {
        /** 最终回执必须存在。 */
        public Done {
            Objects.requireNonNull(result);
        }
    }

    /** 本步预算或批次上限耗尽，下个 tick 从断点继续。 */
    enum Yield implements EffectStep {
        /** 无状态续接请求，可在所有执行状态之间共用。 */
        INSTANCE
    }

    /**
     * 效果正常节奏的到期刻，使用目标维度的 gameTime。
     * @param gameTime 下次推进的绝对游戏刻
     */
    record Delay(long gameTime) implements EffectStep {}

    /**
     * 本步遇到尚不可用的目标区块，调用方就绪后继续同一执行状态。
     * @param requirement 当前断点所需区块
     */
    record AwaitChunks(ChunkRequirement requirement) implements EffectStep {
        /** 等待必须指明所需区块。 */
        public AwaitChunks {
            Objects.requireNonNull(requirement);
        }
    }

    /**
     * 创建最终结果，供同步效果与提前跳过路径使用。
     * @param result 最终真实回执
     * @return 完成状态
     */
    static Done done(EffectResult result) {
        return new Done(result);
    }
}
