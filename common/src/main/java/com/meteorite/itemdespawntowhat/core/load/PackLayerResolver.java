package com.meteorite.itemdespawntowhat.core.load;

/**
 * 数据包来源层判定：把 ResourceManager 中某个包判定为内置数据包或世界数据包。
 * core/load 不假设平台（NeoForge / Fabric）的包 id 命名习惯，由引导层注入实现。
 */
@FunctionalInterface
public interface PackLayerResolver {

    // 依据包 id（Resource.sourcePackId()）返回该包所属的层；只允许返回 BUILTIN 或 WORLD
    RuleSourceLayer resolve(String packId);

    // 包 id 含指定 token（通常是模组 id）判为内置数据包，其余判为世界数据包
    static PackLayerResolver byPackIdToken(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("包 id token 不能为空");
        }
        return packId -> packId != null && packId.contains(token)
                ? RuleSourceLayer.BUILTIN
                : RuleSourceLayer.WORLD;
    }

    // 全部判为世界数据包：不区分内置包时的退化实现（层优先级退化为包优先级）
    static PackLayerResolver allWorld() {
        return packId -> RuleSourceLayer.WORLD;
    }
}
