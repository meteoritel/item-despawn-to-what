package com.meteorite.itemdespawntowhat.core.runtime;

/** 恢复交付与恢复队列共用的指数退避口径，尝试次数从 1 开始。 */
final class RecoveryBackoff {
    private static final int BASE_DELAY_TICKS = 20;
    private static final int MAX_DELAY_TICKS = 1200;

    private RecoveryBackoff() {}

    static int delayTicks(int attempts) {
        int shift = Math.clamp(attempts - 1, 0, 6);
        return Math.min(MAX_DELAY_TICKS, BASE_DELAY_TICKS << shift);
    }
}
