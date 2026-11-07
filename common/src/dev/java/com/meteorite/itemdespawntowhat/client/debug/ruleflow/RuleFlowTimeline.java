package com.meteorite.itemdespawntowhat.client.debug.ruleflow;

/**
 * 三阶段循环动画的确定性时间轴：只保存循环内的已播放毫秒数，全部动效都从它推导。
 *
 * <p>不读取系统时钟、不使用随机数：暂停后同一个 elapsed 必然得到同一帧，探针在暂停态截图
 * 因此可以逐像素重复。运行态用锚点补上两次推进之间经过的时间，暂停、seek 与重播只移动锚点
 * 或 elapsed，不改变循环长度。</p>
 */
final class RuleFlowTimeline {

    /** 一个循环的阶段顺序；HOLD 结束后回到 INPUT。 */
    enum Phase { INPUT, CHARGE, BURST, RESULT, HOLD }

    /** 整个循环的毫秒数，进度条与连接线动画共用同一长度。 */
    static final long CYCLE_MILLIS = 5200L;
    /** INPUT 阶段结束时刻。 */
    static final long INPUT_END = 900L;
    /** CHARGE 阶段结束时刻。 */
    static final long CHARGE_END = 2300L;
    /** BURST 阶段结束时刻。 */
    static final long BURST_END = 2600L;
    /** RESULT 阶段结束时刻，之后进入 HOLD。 */
    static final long RESULT_END = 3600L;

    /**
     * 一帧的只读快照；{@code phaseProgress} 是当前阶段内的 0..1 比例。
     *
     * <p>模块只消费这个快照，不自行读取时间，暂停与 seek 后重绘完全稳定。</p>
     */
    record Frame(Phase phase, float phaseProgress, long elapsed, boolean paused) { }

    private long elapsed;
    private long anchor;
    private boolean paused;
    private boolean resync = true;

    /** 推进到当前时刻；暂停时不推进，也不重算锚点。 */
    void update(long nowMillis) {
        if (paused) return;
        if (resync) {
            anchor = nowMillis - elapsed;
            resync = false;
        }
        elapsed = Math.floorMod(nowMillis - anchor, CYCLE_MILLIS);
    }

    /** 切换暂停；恢复时以当前 elapsed 重设锚点，暂停时长不会被算进循环。 */
    void setPaused(boolean paused, long nowMillis) {
        if (this.paused == paused) return;
        if (paused) {
            update(nowMillis);
            this.paused = true;
        } else {
            this.paused = false;
            anchor = nowMillis - elapsed;
            resync = false;
        }
    }

    /** 回到循环起点；保持当前暂停状态，运行态从 0 继续推进。 */
    void restart(long nowMillis) {
        elapsed = 0L;
        anchor = nowMillis;
        resync = false;
    }

    /** 绝对定位；越界值按循环取模，运行态下一次推进从新位置继续。 */
    void seek(long elapsedMillis) {
        elapsed = Math.floorMod(elapsedMillis, CYCLE_MILLIS);
        resync = true;
    }

    /** 定位到阶段中点，用稳定画面检查该阶段动效。 */
    void seekToPhase(Phase phase) {
        seek(switch (phase) {
            case INPUT -> 450L;
            case CHARGE -> 1600L;
            case BURST -> 2450L;
            case RESULT -> 3000L;
            case HOLD -> 4300L;
        });
    }

    long elapsed() { return elapsed; }

    boolean paused() { return paused; }

    /** 当前帧；同一 elapsed 与暂停态必然得到相同快照。 */
    Frame frame() { return frameAt(elapsed(), paused()); }

    private static Frame frameAt(long elapsed, boolean paused) {
        if (elapsed < INPUT_END) return new Frame(Phase.INPUT, ratio(elapsed, 0L, INPUT_END), elapsed, paused);
        if (elapsed < CHARGE_END) return new Frame(Phase.CHARGE, ratio(elapsed, INPUT_END, CHARGE_END), elapsed, paused);
        if (elapsed < BURST_END) return new Frame(Phase.BURST, ratio(elapsed, CHARGE_END, BURST_END), elapsed, paused);
        if (elapsed < RESULT_END) return new Frame(Phase.RESULT, ratio(elapsed, BURST_END, RESULT_END), elapsed, paused);
        return new Frame(Phase.HOLD, ratio(elapsed, RESULT_END, CYCLE_MILLIS), elapsed, paused);
    }

    private static float ratio(long value, long start, long end) {
        long span = end - start;
        if (span <= 0L) return 1f;
        return Math.clamp((value - start) / (float) span, 0f, 1f);
    }
}
