package com.meteorite.itemdespawntowhat.runtime;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.core.command.RuleCommandTree;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 新链路命令树的 NeoForge 注册入口（阶段⑤）。
 * 与旧链路 /idtw edit|reload|inspect 并列注册：Brigadier 对同名子节点做合并，
 * 因此 /idtw 下新旧子命令同时可用，旧命令实现不做任何改动。
 */
@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID)
public final class NeoForgeRuleCommandRegistrar {

    private NeoForgeRuleCommandRegistrar() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 注册命令树；事件只在服务端启动时触发一次
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RuleCommandTree.register(event.getDispatcher(), RuleRuntimeHost.commandContext());
    }
}
