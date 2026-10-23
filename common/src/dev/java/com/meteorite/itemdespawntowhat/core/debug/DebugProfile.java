package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.JsonObject;

/**
 * 「打开编辑器 → 规则列表可用」首载链路的分段观测开关与计时入口。
 *
 * <p>开关默认关闭，且只在开发环境（{@link DebugMode#ENABLED}）下可用。关闭时本类的读取入口
 * 只做一次布尔判断，不取时间、不分配对象、不写日志，运行行为与日志输出与改动前一致。</p>
 *
 * <p>开启后，服务端快照准备、分片投递与客户端接收、模型装配、列表刷新各阶段输出
 * {@code [IDTW_DEBUG] profile scene=... event=...} 单行日志。调试页批量生成 10 / 200 / 1000 条
 * 测试规则后重新打开编辑器，即可在同一份日志里对比三档规模的分段耗时。开关用
 * {@code /idtw debug profile on|off} 切换，停服时自动回到关闭状态。</p>
 */
public final class DebugProfile {

    // 首载分段观测的日志场景名；同一条首载链路的全部单行日志共用它
    public static final String SCENE_FIRST_LOAD = "first_load";

    // 开关状态；只有开发环境可通过命令改变它
    private static volatile boolean enabled;

    // 工具类不创建实例。
    private DebugProfile() {}

    /**
     * 当前是否启用首载分段观测。
     *
     * @return 开发环境且开关打开时为 true
     */
    public static boolean enabled() {
        return DebugMode.ENABLED && enabled;
    }

    /**
     * 设置开关状态；发布环境即使写入也不会生效。
     *
     * @param value 是否启用
     */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * 记录分段观测起点。
     *
     * @return 启用时为单调时钟纳秒值；关闭时为 0，可直接交给 {@link #micros(long)}
     */
    public static long start() {
        return enabled() ? System.nanoTime() : 0L;
    }

    /**
     * 把起点换算成经过的微秒数。
     *
     * @param startedNanos {@link #start()} 的返回值；0 表示未启用
     * @return 经过的微秒数；未启用时为 0
     */
    public static long micros(long startedNanos) {
        return startedNanos == 0L ? 0L : (System.nanoTime() - startedNanos) / 1000L;
    }

    /**
     * 输出一条分段观测日志；关闭时直接返回。
     *
     * @param scene 观测场景，例如 {@link #SCENE_FIRST_LOAD}
     * @param event 阶段名，例如 {@code SERVER_PREP}
     * @param data  该阶段的计数与耗时字段
     */
    public static void event(String scene, String event, JsonObject data) {
        if (enabled()) {
            DebugLog.profile(scene, event, data);
        }
    }
}
