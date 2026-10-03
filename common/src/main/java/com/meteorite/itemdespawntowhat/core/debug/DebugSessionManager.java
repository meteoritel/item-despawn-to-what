package com.meteorite.itemdespawntowhat.core.debug;

import com.meteorite.itemdespawntowhat.core.command.RuleCommandContext;
import net.minecraft.server.MinecraftServer;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** 两端已有事件的开发诊断入口；发布环境直接返回，停服释放场景与服务器引用。 */
public final class DebugSessionManager {
    private static final Set<MinecraftServer> READY = Collections.newSetFromMap(new IdentityHashMap<>());

    // 工具类不创建实例。
    private DebugSessionManager() {}

    // 首次就绪记录当前参数，其后只推进已经由命令创建的场景。
    public static void tick(MinecraftServer server, RuleCommandContext context) {
        if (!DebugMode.ENABLED) { return; }
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
        finally { READY.remove(server); }
    }
}
