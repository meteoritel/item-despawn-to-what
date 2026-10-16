package com.meteorite.itemdespawntowhat.runtime;

import com.meteorite.itemdespawntowhat.core.command.RuleCommandContext;
import com.meteorite.itemdespawntowhat.core.config.ServerConfig;
import com.meteorite.itemdespawntowhat.core.host.RuleRuntimeHostCore;
import com.meteorite.itemdespawntowhat.core.load.PackLayerResolver;
import com.meteorite.itemdespawntowhat.core.load.RuleLoadResult;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerContext;
import com.meteorite.itemdespawntowhat.core.runtime.LifespanProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 新链路（core/runtime）在 NeoForge 端的引导与生命周期持有者。
 * 引导时序、事件语义与失败降级由 {@link RuleRuntimeHostCore} 统一实现，本类只保留平台适配与事件转发。
 * 平台差异：寿命取加载器提供的 ItemStack#getEntityLifespan，支持其它模组对掉落物寿命的修改。
 * 当前唯一转化链路；旧 JSON 仅供显式迁移命令读取。
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

    // 网络编辑层入口：以窄接口暴露运行时的覆盖层路径与重建能力
    public static RuleEditServerContext editContext() {
        return CORE.editContext();
    }

    // 命令层入口：以窄接口暴露运行时、模组级配置与编辑层统计
    public static RuleCommandContext commandContext() {
        return CORE.commandContext();
    }

    // 服务端就绪：读配置 → 建注册表 → 加载规则 → 建运行时 → 对全部已加载维度回扫
    public static void start(MinecraftServer server) {
        CORE.start(server);
    }

    // 数据包重载：按当前服务端资源管理器重建索引；未就绪或失败时返回 null 供命令层提示
    public static RuleLoadResult<Rule> reload(MinecraftServer server, ResourceManager resourceManager) {
        return CORE.reload(server, resourceManager);
    }

    // 服务端停止：释放全部维度的追踪状态与到期任务
    public static void shutdown() {
        CORE.shutdown();
    }

    // 掉落物进入世界：仅当存在候选规则时才纳入追踪
    public static void onItemAdded(ServerLevel level, ItemEntity entity) {
        CORE.onItemAdded(level, entity);
    }

    // 实体卸载与自然消失入口由平台事件或最小 Mixin 转发。
    public static void onItemRemoved(ServerLevel level, ItemEntity item) {
        CORE.onItemRemoved(level, item);
    }

    // 环境致死请求入口：平台层只在真实致死分支调用（NeoForge 由事件监听器转发）
    public static void requestEnvironmentalConversion(ServerLevel level, ItemEntity item, DamageSource source) {
        CORE.requestEnvironmentalConversion(level, item, source);
    }

    // 自然消失延后判定入口：返回是否已延后
    public static boolean deferNaturalExpiry(ServerLevel level, ItemEntity item) {
        return CORE.deferNaturalExpiry(level, item);
    }

    // 服务器 tick 结束：推进唯一的公共预算；所有维度、所有任务种类共享同一份额度
    public static void tickServer(MinecraftServer server) {
        CORE.tickServer(server);
    }

    // 维度加载：对后加载的维度补一次回扫（启动期维度已由 start 统一回扫）
    public static void levelLoaded(ServerLevel level) {
        CORE.levelLoaded(level);
    }

    // 维度卸载：释放该维度的追踪状态与到期任务，避免强引用维度对象（A4）
    public static void levelUnloaded(ServerLevel level) {
        CORE.levelUnloaded(level);
    }
}
