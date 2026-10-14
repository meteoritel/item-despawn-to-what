package com.meteorite.itemdespawntowhat.core.api;

import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerTickBudget;
import org.jetbrains.annotations.Nullable;

/** 效果的一次执行状态；仅在服务端线程推进，取消后直接丢弃，不重放已执行的世界操作。 */
@FunctionalInterface
public interface EffectExecution {
    /**
     * 在公共预算内推进执行，调用方只在获得最终回执后记账。
     * @param budget 当前服务器 tick 的共享预算，不得保存到下次调用
     * @return 最终真实回执；null 表示尚未完成，需要在后续 tick 续接
     */
    @Nullable EffectResult advance(ServerTickBudget budget);
}
