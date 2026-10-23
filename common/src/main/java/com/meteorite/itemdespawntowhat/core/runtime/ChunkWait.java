package com.meteorite.itemdespawntowhat.core.runtime;

/**
 * 连续区块等待窗口：以服务器调度刻计时，区块恢复后重新开始计时。
 * 超时只终结当前操作，未开始组的库存仍由账本恢复链路负责交付。
 */
final class ChunkWait {
    // 一分钟的加载窗口；与重试间隔分开，防止长期保留结算与效果引用。
    static final int MAX_WAIT_TICKS = LoadedChunks.RETRY_TICKS * 60;

    private long startedTick = Long.MIN_VALUE;

    boolean expired(long gameTime) {
        if (startedTick == Long.MIN_VALUE) {
            startedTick = gameTime;
        }
        return gameTime - startedTick >= MAX_WAIT_TICKS;
    }

    void reset() {
        startedTick = Long.MIN_VALUE;
    }
}
