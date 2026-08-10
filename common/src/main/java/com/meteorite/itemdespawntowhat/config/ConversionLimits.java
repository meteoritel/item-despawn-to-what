package com.meteorite.itemdespawntowhat.config;

/**
 * 转换规则在服务端执行时采用的安全数值上限。
 */
public final class ConversionLimits {

    public static final int MAX_SOURCE_MULTIPLE = 64;
    public static final int MAX_RESULT_MULTIPLE = 64;
    public static final int MAX_CATALYST_ENTRY_COUNT = 64;
    public static final int MAX_RESULT_UNITS = 1_024;
    /** @deprecated use MAX_RESULT_UNITS. */
    @Deprecated
    public static final int MAX_RESULT_LIMIT = MAX_RESULT_UNITS;
    public static final int MAX_SEARCH_RADIUS = 16;
    public static final int MAX_BLOCK_RADIUS = 6;
    public static final int MAX_BLOCK_PLACEMENTS = 2_197;
    public static final int MAX_XP_PER_ITEM = 10_000;
    public static final float MAX_EXPLOSION_POWER = 16.0F;
    public static final int MAX_WEATHER_DURATION_TICKS = 12_096_000;
    public static final int MAX_WORLD_EFFECT_EXECUTIONS = 64;

    private ConversionLimits() {
        throw new UnsupportedOperationException("Utility class");
    }
}
