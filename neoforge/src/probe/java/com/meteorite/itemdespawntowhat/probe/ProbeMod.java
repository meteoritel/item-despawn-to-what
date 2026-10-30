package com.meteorite.itemdespawntowhat.probe;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.DevelopmentDiagnostics;
import com.meteorite.itemdespawntowhat.core.api.RuntimeObserver;
import com.meteorite.itemdespawntowhat.core.debug.BackendProbe;
import com.meteorite.itemdespawntowhat.core.debug.DebugProfile;
import com.meteorite.itemdespawntowhat.core.debug.BackendRuntimeObserver;
import com.meteorite.itemdespawntowhat.core.debug.DebugSessionManager;
import com.meteorite.itemdespawntowhat.core.command.RuleCommandTree;
import com.meteorite.itemdespawntowhat.core.debug.RuleDebugCommands;
import com.meteorite.itemdespawntowhat.runtime.RuleRuntimeHost;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 只由 NeoForge 开发类目录提供的事件入口，发布产物没有此类。 */
@EventBusSubscriber(modid = Constants.MOD_ID)
public final class ProbeMod {
    private ProbeMod() {}

    /** 在正式宿主启动前装配观测器，生产事件只依赖空接缝。 */
    @SubscribeEvent
    public static void starting(ServerAboutToStartEvent event) {
        BackendProbe.starting();
        RuntimeObserver.install(new BackendRuntimeObserver());
        DevelopmentDiagnostics.install(new DevelopmentDiagnostics.Sink() {
            @Override public boolean enabled() { return DebugProfile.enabled(); }
            @Override public void event(String scene, String name, com.google.gson.JsonObject data) {
                DebugProfile.event(scene, name, data);
            }
        });
    }

    /** 开发命令与正式根命令合并，生产命令树没有探针依赖。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(RuleCommandTree.ROOT).then(RuleDebugCommands.build(RuleRuntimeHost.core().diagnosticsContext())));
    }

    /** 正式运行时已推进后再推进开发夹具，世界读写仍在服务端线程。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        BackendProbe.observeTick(event.getServer());
        DebugSessionManager.tick(event.getServer(), RuleRuntimeHost.core().diagnosticsContext());
        BackendProbe.tick(event.getServer());
    }

    /** 开发场景先收尾，再让正式宿主停止。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void stopping(ServerStoppingEvent event) {
        BackendProbe.stopping();
        DebugSessionManager.shutdown(event.getServer(), RuleRuntimeHost.core().diagnosticsContext());
    }

    /** 释放装配引用，下一次存档启动重新安装。 */
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        RuntimeObserver.install(null);
        DevelopmentDiagnostics.install(null);
    }
}
