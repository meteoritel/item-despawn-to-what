package com.meteorite.itemdespawntowhat.runtime;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.core.command.RuleCommandTree;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /idtw 命令树的 NeoForge 注册入口：在 RegisterCommandsEvent 上把 core/command 的完整命令树挂到调度器。
 * 命令分组、权限口径与反馈出口都在 RuleCommandTree，本类只负责平台事件接线。
 */
@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID)
public final class NeoForgeRuleCommandRegistrar {

    private NeoForgeRuleCommandRegistrar() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 注册命令树；事件只在服务端启动时触发一次
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RuleCommandTree.register(event.getDispatcher(), RuleRuntimeHost.core().commandContext());
    }
}
