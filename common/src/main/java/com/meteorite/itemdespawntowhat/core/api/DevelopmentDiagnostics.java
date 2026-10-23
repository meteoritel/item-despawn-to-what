package com.meteorite.itemdespawntowhat.core.api;

import com.google.gson.JsonObject;

/** 开发诊断的空接缝；生产代码只上报数据，输出实现由开发源集装配。 */
public final class DevelopmentDiagnostics {
    /** 编辑器首载链路共用的场景标识。 */
    public static final String SCENE_FIRST_LOAD = "first_load";
    private static final Sink NONE = new Sink() {};
    private static volatile Sink sink = NONE;

    private DevelopmentDiagnostics() {}

    /** 可选的开发输出端，默认关闭且不输出。 */
    public interface Sink {
        /** 当前是否需要构造诊断数据。 */
        default boolean enabled() { return false; }

        /** 消费已构造的诊断事件。 */
        default void event(String scene, String event, JsonObject data) {}
    }

    /** 在开发装配或停止时安装输出端，null 恢复空实现。 */
    public static void install(Sink value) { sink = value == null ? NONE : value; }

    /** 是否启用开发输出。 */
    public static boolean enabled() { return sink.enabled(); }

    /** 启用时记录计时起点，关闭时返回 0。 */
    public static long start() { return enabled() ? System.nanoTime() : 0L; }

    /** 将有效起点换算为微秒，0 起点返回 0。 */
    public static long micros(long startedNanos) {
        return startedNanos == 0L ? 0L : (System.nanoTime() - startedNanos) / 1000L;
    }

    /** 将事件交给可选的开发输出端。 */
    public static void event(String scene, String event, JsonObject data) {
        if (enabled()) { sink.event(scene, event, data); }
    }
}
