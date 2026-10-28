package com.meteorite.itemdespawntowhat.runtime;

import com.meteorite.itemdespawntowhat.core.config.ServerConfig;
import com.meteorite.itemdespawntowhat.core.host.RuleRuntimeHostCore;
import com.meteorite.itemdespawntowhat.core.load.PackLayerResolver;
import com.meteorite.itemdespawntowhat.core.runtime.LifespanProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 新链路（core/runtime）在 NeoForge 端的引导与生命周期持有者。
 * 引导时序、事件语义与失败降级由 {@link RuleRuntimeHostCore} 统一实现，本类只保留平台适配与共享宿主的装配入口。
 * 平台差异：寿命取加载器提供的 ItemStack#getEntityLifespan，支持其它模组对掉落物寿命的修改。
 */
public final class RuleRuntimeHost {

    // NeoForge 正式模组包注册路径写入的 KnownPack 命名空间：
    // 普通模组包见 net.neoforged.neoforge.resource.ResourcePackLoader#packFinder，
    // 可选内置数据包见 net.neoforged.neoforge.event.AddPackFindersEvent#addPackFinders
    private static final String MOD_PACK_KNOWN_NAMESPACE = "neoforge";

    // 公共宿主实现：进程内单实例，状态对应当前存档生命周期
    private static final RuleRuntimeHostCore CORE = new RuleRuntimeHostCore(new NeoForgePlatformAdapter());

    private RuleRuntimeHost() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** NeoForge 平台适配：寿命走加载器物品寿命；包层判定按本次加载包的 KnownPack 注册命名空间；启动时采样参考寿命。 */
    private static final class NeoForgePlatformAdapter implements RuleRuntimeHostCore.PlatformAdapter {

        // 服务端缓存的 GUI 参考寿命 tick；尚未采样时为零
        private int referenceLifespanTicks;

        @Override
        public LifespanProvider lifespan(ServerConfig config) {
            return (level, entity) -> entity.lifespan;
        }

        @Override
        public PackLayerResolver packLayerResolver(ResourceManager resourceManager) {
            return PackLayerResolver.fromModPacks(resourceManager, MOD_PACK_KNOWN_NAMESPACE);
        }

        @Override
        public int referenceLifespanTicks() {
            return referenceLifespanTicks;
        }

        @Override
        public void sampleReferenceLifespan(MinecraftServer server) {
            // 普通物品仅在启动时查询一次；GUI 参考值不参与实际实体调度或配置合法性校验。
            ServerLevel referenceLevel = server.overworld();
            ItemEntity referenceItem = new ItemEntity(referenceLevel, 0, 0, 0,
                    new ItemStack(Items.STONE));
            referenceLifespanTicks = Math.max(1, referenceItem.lifespan);
        }

        @Override
        public void clearReferenceLifespan() {
            referenceLifespanTicks = 0;
        }
    }

    /** @return 本端装配的共享宿主；内部状态随服务端启停创建和释放。 */
    public static RuleRuntimeHostCore core() {
        return CORE;
    }
}
