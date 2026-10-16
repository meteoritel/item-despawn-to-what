package com.meteorite.itemdespawntowhat.core.load;

import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 数据包来源层判定：把 ResourceManager 中某个包判定为内置数据包或世界数据包。
 * core/load 不依赖平台加载器：判定实现由引导层在本平台的包来源元数据上构建后注入。
 */
@FunctionalInterface
public interface PackLayerResolver {

    // 依据包 id（Resource.sourcePackId()）返回该包所属的层；只允许返回 BUILTIN 或 WORLD
    RuleSourceLayer resolve(String packId);

    /**
     * 依据本次实际加载包的来源元数据构建分类快照：只有 KnownPack 命名空间为本平台正式注册命名空间的包判为内置数据包，
     * 其余（世界目录包、vanilla/feature 包、未注册来源）一并按世界数据包处理。
     * 快照只取本次 ResourceManager 已加载的包，与本次规则读取同源；同包 id 出现来源歧义（既内置又世界）时按世界数据包处理，
     * 不因同名可信包提升未知来源；来源元数据不作为防伪造凭据。
     *
     * @param resourceManager 本次规则读取使用的数据包资源管理器
     * @param modPackNamespace 本平台正式注册路径写入的 KnownPack 命名空间
     * @return 包 id → 层的判定器；快照中不存在的包按世界数据包处理
     */
    static PackLayerResolver fromModPacks(ResourceManager resourceManager, String modPackNamespace) {
        Map<String, RuleSourceLayer> layers = new HashMap<>();
        // 枚举失败不做降级：来源元数据缺失会把内置包错判为世界数据包并改变层优先级，交由调用方的加载失败边界处理
        try (Stream<PackResources> packs = resourceManager.listPacks()) {
            for (PackResources pack : packs.toList()) {
                boolean builtin = pack.knownPackInfo()
                        .map(knownPack -> modPackNamespace.equals(knownPack.namespace()))
                        .orElse(false);
                RuleSourceLayer layer = builtin ? RuleSourceLayer.BUILTIN : RuleSourceLayer.WORLD;
                layers.merge(pack.packId(), layer, (first, second) -> first == second ? first : RuleSourceLayer.WORLD);
            }
        }
        Map<String, RuleSourceLayer> snapshot = Map.copyOf(layers);
        return packId -> snapshot.getOrDefault(packId, RuleSourceLayer.WORLD);
    }
}
