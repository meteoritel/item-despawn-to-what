package com.meteorite.itemdespawntowhat.core.api;

import com.meteorite.itemdespawntowhat.core.api.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import org.jetbrains.annotations.Nullable;

/**
 * 效果的一次执行状态；仅在服务端线程推进，取消后直接丢弃，不重放已执行的世界操作。
 * 调用方在最终回执、失败与取消三条路径上各通知一次；实现只需在被要求时释放自身资源。
 */
@FunctionalInterface
public interface EffectExecution {
    /**
     * 在公共预算内推进执行，调用方只在获得最终回执后记账。
     * @param budget 当前服务器 tick 的共享预算，不得保存到下次调用
     * @return 完成、预算续接、正常延时或区块等待；必须明确给出推进原因
     */
    EffectStep advance(ServerTickBudget budget);

    /**
     * 声明每次推进前需要的区块；调用方检查就绪后才调用 advance。
     * 固定范围应缓存该值；逐位置处理途中遇到新需求时返回 EffectStep.AwaitChunks。
     * 非 null 声明是本次推进的完整需求，替代上次返回的等待请求；共享游标可据此更新断点。
     * @return 本次推进所需区块；null 时沿用上次等待请求，无等待请求时仅检查触发位置
     */
    default @Nullable ChunkRequirement requiredChunks() {
        return null;
    }

    /**
     * 最终回执通知：本执行已产出终态、不会再次推进时调用一次，用于释放执行态持有的资源。
     * 调用方先置终态再通知，实现不得借此回写账目或重放世界操作。
     * @param result 已计入账目的最终回执
     */
    default void onFinished(EffectResult result) {
    }

    /**
     * 取消通知：本执行被取消、不会再推进时调用一次；取消不补做世界写入，也不再产生回执。
     * @param reason 取消原因
     */
    default void onCancelled(CancelReason reason) {
    }
}
