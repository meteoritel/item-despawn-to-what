package com.meteorite.itemdespawntowhat.runtime;

import com.meteorite.itemdespawntowhat.core.debug.DebugSessionManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.meteorite.itemdespawntowhat.core.state.DropStateStore;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;
import net.minecraft.world.entity.player.Player;
import com.meteorite.itemdespawntowhat.Constants;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import static com.meteorite.itemdespawntowhat.ItemDespawnToWhat.MOD_ID;

/**
 * 新链路（core/runtime）在 NeoForge 端的事件入口。
 * 本类只做事件到 RuleRuntimeHost 的转发，不含业务逻辑（规划书 3.12：平台层只做入口）。
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
        DebugSessionManager.shutdown(event.getServer(), RuleRuntimeHost.commandContext());
        RuleRuntimeHost.shutdown();
    }

    // PlayerList.reloadResources 在新资源与标签切换后发布事件；登录同步不需要重建索引。
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            var server = event.getPlayerList().getServer();
            RuleRuntimeHost.reload(server, server.getResourceManager());
        }
    }

    // 掉落物进入世界（含从磁盘加载的实体）：纳入追踪
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof ItemEntity item) {
            RuleRuntimeHost.onItemAdded(level, item);
        }
    }

    // 原生事件保证死亡背包物品入世界前带有排除标记。
    @SubscribeEvent
    public static void onDeathDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Player) {
            event.getDrops().forEach(item -> item.addTag(Constants.CHECK_LOCK_TAG));
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof ItemEntity item) {
            RuleRuntimeHost.onItemRemoved(level, item);
        }
    }

    // 原生自然消失事件：让预算队列完成最后判定后再释放实体。
    @SubscribeEvent
    public static void onItemExpire(ItemExpireEvent event) {
        ItemEntity item = event.getEntity();
        if (item.level() instanceof ServerLevel level && RuleRuntimeHost.deferNaturalExpiry(level, item)) {
            // NeoForge 将寿命钳制为 32766；达到边界时短暂重置年龄，队列仍按 expiryPending 判定并最终 discard。
            if (item.getAge() >= 32765) { item.setExtendedLifetime(); }
            event.addExtraLife(1);
        }
    }

    // 三类环境伤害保护（D2/D4）：原版 Entity#isInvulnerableTo 调用时触发，
    // 只豁免 in_fire/on_fire/lava/cactus（D1），且仅在保护期内；其余伤害原样放行。
    @SubscribeEvent
    public static void onInvulnerabilityCheck(EntityInvulnerabilityCheckEvent event) {
        // 只处理掉落物：避免为其它实体创建持久数据，也避免无谓的状态读取
        if (!(event.getEntity() instanceof ItemEntity item)) {
            return;
        }
        if (item.level() instanceof ServerLevel level
                && DropStateStore.blocksEnvironmentalDamage(item, event.getSource(), level.getGameTime())) {
            event.setInvulnerable(true);
        }
    }

    // 服务器 tick 结束：统一推进一次公共预算（所有维度共享，ADR-0002），再跑开发场景与周期性失效清理
    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        RuleRuntimeHost.tickServer(event.getServer());
        DebugSessionManager.tick(event.getServer(), RuleRuntimeHost.commandContext());
        if (event.getServer().getTickCount() % 20 == 0) {
            com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerHandler
                    .expireIdle(event.getServer(), RuleRuntimeHost.editContext());
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
