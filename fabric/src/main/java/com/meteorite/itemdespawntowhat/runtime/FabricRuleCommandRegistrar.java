package com.meteorite.itemdespawntowhat.runtime;

import com.meteorite.itemdespawntowhat.core.command.RuleCommandTree;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * 新链路命令树的 Fabric 注册入口（阶段⑤）。
 * 与旧链路 /idtw edit|reload|inspect 并列注册：Brigadier 对同名子节点做合并，
 * 因此 /idtw 下新旧子命令同时可用，旧命令实现不做任何改动。
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
                RuleCommandTree.register(dispatcher, RuleRuntimeHost.commandContext()));
    }
}
