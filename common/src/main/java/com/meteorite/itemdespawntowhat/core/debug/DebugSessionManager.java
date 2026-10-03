package com.meteorite.itemdespawntowhat.core.debug;

import com.meteorite.itemdespawntowhat.core.command.RuleCommandContext;
import net.minecraft.server.MinecraftServer;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** 两端已有事件的开发诊断入口；发布环境直接返回，停服释放场景与服务器引用。 */
public final class DebugSessionManager {
    private static final Set<MinecraftServer> READY = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<MinecraftServer, RuntimeTiming> TIMINGS = new IdentityHashMap<>();

    /** 服务端线程复用单个采样槽，避免每tick创建时间对象；不包含后续开发场景推进。 */
    private static final class RuntimeTiming {
        private int tick;
        private long started;
        private long elapsed = -1;
    }

    // 工具类不创建实例。
    private DebugSessionManager() {}

    // 两平台在结束事件中推进运行时前调用；发布环境不计时或分配采样槽。
    public static void beginRuntimeTick(MinecraftServer server) {
        if (!DebugMode.ENABLED) { return; }
        RuntimeTiming timing = TIMINGS.computeIfAbsent(server, ignored -> new RuntimeTiming());
        timing.tick = server.getTickCount();
        timing.started = System.nanoTime();
        timing.elapsed = -1;
    }

    // 只返回同一tick已完成的运行时成本，缺失采样必须显式标记。
    static long runtimeTickNanos(MinecraftServer server) {
        RuntimeTiming timing = TIMINGS.get(server);
        return timing == null || timing.tick != server.getTickCount() ? -1 : timing.elapsed;
    }

    // 首次就绪记录当前参数，其后只推进已经由命令创建的场景。
    public static void tick(MinecraftServer server, RuleCommandContext context) {
        if (!DebugMode.ENABLED) { return; }
        RuntimeTiming timing = TIMINGS.get(server);
        if (timing != null && timing.tick == server.getTickCount() && timing.elapsed < 0) {
            timing.elapsed = Math.max(0, System.nanoTime() - timing.started);
        }
        if (context.runtime() != null && READY.add(server)) { DebugLog.ready(server, context); }
        DebugScenarioManager.tick(server, context);
        DebugPipelineManager.tick(server, context);
    }

    // 场景清理必须先于平台运行时关闭，才能取消本轮延迟任务。
    public static void shutdown(MinecraftServer server, RuleCommandContext context) {
        if (!DebugMode.ENABLED) { return; }
        try {
            DebugPipelineManager.shutdown(server, context);
            DebugScenarioManager.shutdown(server, context);
        }
        finally {
            READY.remove(server);
            TIMINGS.remove(server);
        }
    }
}
