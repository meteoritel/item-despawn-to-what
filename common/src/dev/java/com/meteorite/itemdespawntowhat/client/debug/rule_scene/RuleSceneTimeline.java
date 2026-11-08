package com.meteorite.itemdespawntowhat.client.debug.rule_scene;

/** 规则场景的确定性时间轴：所有动效只由经过的毫秒推导，暂停即冻结。 */
public final class RuleSceneTimeline {

    /** 一个完整演示周期。 */
    public static final long CYCLE_MILLIS = 7200L;

    /** 五个阶段；顺序即演示顺序。 */
    public enum Phase {
        BUILD, ITEMS, TRIGGER, RESULT, HOLD
    }

    private static final long BUILD_END = 900L;
    private static final long ITEMS_END = 2400L;
    private static final long TRIGGER_END = 3200L;
    private static final long RESULT_END = 4200L;

    private RuleSceneTimeline() { }

    /** 定位到指定阶段的中点，供数字键与探针使用。 */
    public static long phaseStart(Phase phase) {
        return switch (phase) {
            case BUILD -> 0L;
            case ITEMS -> BUILD_END;
            case TRIGGER -> ITEMS_END;
            case RESULT -> TRIGGER_END;
            case HOLD -> RESULT_END;
        };
    }

    /** 阶段中点毫秒，截图验收先暂停到这个位置。 */
    public static long phaseMidpoint(Phase phase) {
        long start = phaseStart(phase);
        long end = switch (phase) {
            case BUILD -> BUILD_END;
            case ITEMS -> ITEMS_END;
            case TRIGGER -> TRIGGER_END;
            case RESULT -> RESULT_END;
            case HOLD -> CYCLE_MILLIS;
        };
        return (start + end) / 2;
    }

    /** 当前阶段。 */
    public static Phase phaseOf(long elapsed) {
        long time = Math.floorMod(elapsed, CYCLE_MILLIS);
        if (time < BUILD_END) return Phase.BUILD;
        if (time < ITEMS_END) return Phase.ITEMS;
        if (time < TRIGGER_END) return Phase.TRIGGER;
        if (time < RESULT_END) return Phase.RESULT;
        return Phase.HOLD;
    }

    /** 阶段内进度 0..1。 */
    public static float progress(long elapsed, Phase phase) {
        long time = Math.floorMod(elapsed, CYCLE_MILLIS);
        long start = phaseStart(phase);
        long end = switch (phase) {
            case BUILD -> BUILD_END;
            case ITEMS -> ITEMS_END;
            case TRIGGER -> TRIGGER_END;
            case RESULT -> RESULT_END;
            case HOLD -> CYCLE_MILLIS;
        };
        if (time < start) return 0.0F;
        if (time >= end) return 1.0F;
        return (float) (time - start) / (float) (end - start);
    }

    /** 先快后慢的缓出，用于入场。 */
    public static float easeOut(float value) {
        float clamped = Math.clamp(value, 0.0F, 1.0F);
        float inverse = 1.0F - clamped;
        return 1.0F - inverse * inverse * inverse;
    }

}
