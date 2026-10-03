package com.meteorite.itemdespawntowhat.runtime;

import com.meteorite.itemdespawntowhat.core.debug.DebugSessionManager;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.world.entity.item.ItemEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 新链路（core/runtime）在 Fabric 端的事件入口。
 * 本类只做事件到 RuleRuntimeHost 的转发，不含业务逻辑（规划书 3.12：平台层只做入口）。
 */
public final class RuleRuntimeEvents {

    private static final Logger LOGGER = LogManager.getLogger();
    private static boolean registered;

    private RuleRuntimeEvents() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 注册新链路全部事件钩子；重复调用安全
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        // 服务端就绪：此时全部维度已创建，可安全回扫；启动期的首次数据包加载不作为重载处理
        ServerLifecycleEvents.SERVER_STARTED.register(RuleRuntimeHost::start);

        // 服务端停止：释放全部维度的追踪状态与到期任务
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            DebugSessionManager.shutdown(server, RuleRuntimeHost.commandContext());
            RuleRuntimeHost.shutdown();
        });

        // 数据包重载：该回调在服务端线程（handleAsync(server)）触发，成功后重建索引并回扫
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                RuleRuntimeHost.reload(server, resourceManager);
            } else {
                LOGGER.warn("数据包重载失败，保留上一版规则索引");
            }
        });

        // 掉落物进入世界（含从磁盘加载的实体）：纳入追踪
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ItemEntity item) {
                RuleRuntimeHost.onItemAdded(world, item);
            }
        });

        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof ItemEntity item) { RuleRuntimeHost.onItemRemoved(world, item); }
        });

        // 服务器 tick 结束：只推进一次公共预算（所有维度共享，ADR-0002）
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            RuleRuntimeHost.tickServer(server);
            DebugSessionManager.tick(server, RuleRuntimeHost.commandContext());
            if (server.getTickCount() % 20 == 0) {
                com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerHandler
                        .expireIdle(server, RuleRuntimeHost.editContext());
            }
        });

        // 维度加载：对后加载的维度补一次回扫
        ServerWorldEvents.LOAD.register((server, world) -> RuleRuntimeHost.levelLoaded(world));

        // 维度卸载：释放该维度状态
        ServerWorldEvents.UNLOAD.register((server, world) -> RuleRuntimeHost.levelUnloaded(world));
    }
}
