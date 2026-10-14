package com.meteorite.itemdespawntowhat.runtime;

import com.meteorite.itemdespawntowhat.core.command.RuleCommandContext;
import com.meteorite.itemdespawntowhat.core.config.ServerConfig;
import com.meteorite.itemdespawntowhat.core.host.RuleRuntimeHostCore;
import com.meteorite.itemdespawntowhat.core.load.PackLayerResolver;
import com.meteorite.itemdespawntowhat.core.load.RuleLoadResult;
import com.meteorite.itemdespawntowhat.core.load.RuleSourceLayer;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerContext;
import com.meteorite.itemdespawntowhat.core.runtime.LifespanProvider;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * 新链路（core/runtime）在 Fabric 端的引导与生命周期持有者。
 * 引导时序、事件语义与失败降级由 {@link RuleRuntimeHostCore} 统一实现，本类只保留平台适配与事件转发。
 * 平台差异：Fabric 没有加载器提供的物品寿命 API，寿命使用 server.json 的兜底刻数，参考寿命由运行期事件采样。
 */
public final class RuleRuntimeHost {

    // 公共宿主实现：进程内单实例，状态对应当前存档生命周期
    private static final RuleRuntimeHostCore CORE = new RuleRuntimeHostCore(new FabricPlatformAdapter());

    private RuleRuntimeHost() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Fabric 平台适配：寿命取运行时采样值并回退兜底刻数；包层判定按模组表匹配；参考寿命由比较事件采样。 */
    private static final class FabricPlatformAdapter implements RuleRuntimeHostCore.PlatformAdapter {

        @Override
        public LifespanProvider lifespan(ServerConfig config) {
            return (level, entity) -> {
                int observed = FabricLifespanReference.ticks();
                return observed > 0 ? observed : config.fabricLifespanFallbackTicks();
            };
        }

        @Override
        public PackLayerResolver packLayerResolver() {
            return packId -> FabricLoader.getInstance().getAllMods().stream()
                    .anyMatch(mod -> mod.getMetadata().getId().equals(packId))
                    ? RuleSourceLayer.BUILTIN
                    : RuleSourceLayer.WORLD;
        }

        @Override
        public int referenceLifespanTicks() {
            return FabricLifespanReference.ticks();
        }

        @Override
        public void sampleReferenceLifespan(MinecraftServer server) {
            // Fabric 由 ItemLifespanReferenceMixin 在首次寿命比较时采样，启动阶段无加载器寿命 API 可查
        }

        @Override
        public void clearReferenceLifespan() {
            FabricLifespanReference.reset();
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

    // 数据包重载：重建规则索引并回扫全部已加载维度，使已存在的掉落物立即按新规则重选（A3）
    // 返回本次加载结果供命令层回显；运行时未就绪或重载失败时返回 null
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

    // 环境致死请求入口：平台层只在真实致死分支调用（Fabric 由 ItemEntityMixin 注入 onDestroyed 转发）
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
