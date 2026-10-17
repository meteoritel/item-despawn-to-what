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
     * @return 最终真实回执；null 表示尚未完成，需要在后续 tick 续接
     */
    @Nullable EffectResult advance(ServerTickBudget budget);

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
