package com.meteorite.itemdespawntowhat.runtime;

import com.meteorite.itemdespawntowhat.core.command.RuleCommandTree;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * /idtw 命令树的 Fabric 注册入口：把 core/command 的完整命令树挂到调度器。
 * 命令分组、权限口径与反馈出口都在 RuleCommandTree，本类只负责平台事件接线。
 */
public final class FabricRuleCommandRegistrar {

    private static boolean registered;

    private FabricRuleCommandRegistrar() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 注册命令树；重复调用安全
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                RuleCommandTree.register(dispatcher, RuleRuntimeHost.core().commandContext()));
    }
}
