package com.meteorite.itemdespawntowhat.core.runtime.scheduler;

import com.meteorite.itemdespawntowhat.core.config.ServerConfig;

/**
 * 调度器的预算参数：把模组级配置换算成纳秒与工作量单位。
 * 配置只在构造调度器时读取一次，改动需要重载或重启服务器。
 */
public record SchedulerConfig(long budgetNanos, int maxWorkUnitsPerTick, int effectsWorkUnitsPerTick,
                             int checkSpreadWindowTicks, int dispatchBatchSize, int positionSearchChecksPerTick) {

    // 紧凑构造器做下界保护，避免非法配置造成零预算或零搬运。
    public SchedulerConfig {
        budgetNanos = Math.max(1L, budgetNanos);
        maxWorkUnitsPerTick = Math.max(1, maxWorkUnitsPerTick);
        effectsWorkUnitsPerTick = Math.max(1, effectsWorkUnitsPerTick);
        checkSpreadWindowTicks = Math.max(1, checkSpreadWindowTicks);
        dispatchBatchSize = Math.max(1, dispatchBatchSize);
        positionSearchChecksPerTick = Math.max(1, positionSearchChecksPerTick);
    }

    // 从模组级服务端配置换算；工作量上限缺省回退到旧的 max_checks_per_tick。
    public static SchedulerConfig from(ServerConfig config) {
        return new SchedulerConfig(
                Math.max(1L, config.serverBudgetUs()) * 1000L,
                config.effectiveMaxWorkUnitsPerTick(),
                config.effectsWorkUnitsPerTick(),
                config.checkSpreadWindowTicks(),
                config.dispatchBatchSize(),
                config.positionSearchChecksPerTick());
    }

    public long budgetMicros() {
        return budgetNanos / 1000L;
    }
}
