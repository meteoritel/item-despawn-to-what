package com.meteorite.itemdespawntowhat.runtime;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import static com.meteorite.itemdespawntowhat.ItemDespawnToWhat.MOD_ID;

/**
 * 新链路（core/runtime）在 NeoForge 端的事件入口。
 * 本类只做事件到 RuleRuntimeHost 的转发，不含业务逻辑（规划书 3.12：平台层只做入口）。
 * 与旧链路 server.event.ItemConversionEvent 并列注册，两者互不读写对方状态。
 */
@EventBusSubscriber(modid = MOD_ID)
public final class RuleRuntimeEvents {

    private RuleRuntimeEvents() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 服务端就绪：此时全部维度已创建，可安全回扫；启动期的首次数据包加载不作为重载处理
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        RuleRuntimeHost.start(event.getServer());
    }

    // 服务端停止：释放全部维度的追踪状态与到期任务
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        RuleRuntimeHost.shutdown();
    }

    // 数据包重载：登记监听器，apply 阶段在服务端线程重建索引并回扫
    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new RuleRuntimeHost.ReloadListener(event.getRegistryAccess()));
    }

    // 掉落物进入世界（含从磁盘加载的实体）：纳入追踪
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof ItemEntity item) {
            RuleRuntimeHost.onItemAdded(level, item);
        }
    }

    // 维度 tick 结束：执行到期任务与周期性失效清理
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RuleRuntimeHost.tickLevel(level);
        }
    }

    // 维度加载：对后加载的维度补一次回扫（本事件两端都会触发，只处理服务端维度）
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RuleRuntimeHost.levelLoaded(level);
        }
    }

    // 维度卸载：释放该维度状态
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RuleRuntimeHost.levelUnloaded(level);
        }
    }
}
