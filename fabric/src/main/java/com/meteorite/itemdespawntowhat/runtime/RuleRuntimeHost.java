package com.meteorite.itemdespawntowhat.runtime;

import com.meteorite.itemdespawntowhat.core.config.ServerConfig;
import com.meteorite.itemdespawntowhat.core.host.RuleRuntimeHostCore;
import com.meteorite.itemdespawntowhat.core.load.PackLayerResolver;
import com.meteorite.itemdespawntowhat.core.runtime.LifespanProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 新链路（core/runtime）在 Fabric 端的引导与生命周期持有者。
 * 引导时序、事件语义与失败降级由 {@link RuleRuntimeHostCore} 统一实现，本类只保留平台适配与共享宿主的装配入口。
 * 平台差异：Fabric 使用运行期采样的参考寿命，采样前使用 server.json 的兜底刻数。
 */
public final class RuleRuntimeHost {

    // Fabric 正式模组包注册路径写入的 KnownPack 命名空间（普通模组包与可选内置数据包共用）
    private static final String MOD_PACK_KNOWN_NAMESPACE = "fabric";

    // 公共宿主实现：进程内单实例，状态对应当前存档生命周期
    private static final RuleRuntimeHostCore CORE = new RuleRuntimeHostCore(new FabricPlatformAdapter());

    private RuleRuntimeHost() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Fabric 平台适配：寿命取运行时采样值并回退兜底刻数；包层判定按本次加载包的 KnownPack 注册命名空间；参考寿命由比较事件采样。 */
    private static final class FabricPlatformAdapter implements RuleRuntimeHostCore.PlatformAdapter {

        @Override
        public LifespanProvider lifespan(ServerConfig config) {
            return (level, entity) -> {
                int observed = FabricLifespanReference.ticks();
                return observed > 0 ? observed : config.fabricLifespanFallbackTicks();
            };
        }

        @Override
        public PackLayerResolver packLayerResolver(ResourceManager resourceManager) {
            return PackLayerResolver.fromModPacks(resourceManager, MOD_PACK_KNOWN_NAMESPACE);
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

    /** @return 本端装配的共享宿主；内部状态随服务端启停创建和释放。 */
    public static RuleRuntimeHostCore core() {
        return CORE;
    }
}
