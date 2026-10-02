package com.meteorite.itemdespawntowhat.core.service;

import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.load.PackLayerResolver;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 一次规则加载所需的全部外部依赖。
 * 这是装配层唯一的入参容器：三层来源、注册表与注册表访问器都在这里显式传入，装配层本身不持有全局状态。
 */
public record RuleLoadContext(
        @Nullable ResourceManager resourceManager,
        @Nullable Path overlayRoot,
        String overlayNamespace,
        RegistryAccess registryAccess,
        TypeRegistry<EffectType<?>> effectTypes,
        TypeRegistry<ConditionType<?>> conditionTypes,
        PackLayerResolver layerResolver,
        @Nullable net.minecraft.server.MinecraftServer server
) {

    public RuleLoadContext {
        Objects.requireNonNull(overlayNamespace, "overlayNamespace");
        Objects.requireNonNull(registryAccess, "registryAccess");
        Objects.requireNonNull(effectTypes, "effectTypes");
        Objects.requireNonNull(conditionTypes, "conditionTypes");
        Objects.requireNonNull(layerResolver, "layerResolver");
    }

    // 平台运行时附加当前服务端，动态引用以实际已加载内容为准。
    public RuleLoadContext withServer(net.minecraft.server.MinecraftServer currentServer) {
        return new RuleLoadContext(resourceManager, overlayRoot, overlayNamespace, registryAccess,
                effectTypes, conditionTypes, layerResolver, currentServer);
    }

    // 同时加载内置/世界数据包与 config 覆盖层
    public static RuleLoadContext full(ResourceManager resourceManager,
                                       Path overlayRoot,
                                       String overlayNamespace,
                                       RegistryAccess registryAccess,
                                       TypeRegistry<EffectType<?>> effectTypes,
                                       TypeRegistry<ConditionType<?>> conditionTypes,
                                       PackLayerResolver layerResolver) {
        return new RuleLoadContext(resourceManager, overlayRoot, overlayNamespace,
                registryAccess, effectTypes, conditionTypes, layerResolver, null);
    }

    // 只加载 config 覆盖层（单人本地调试与命令校验使用）
    public static RuleLoadContext overlayOnly(Path overlayRoot,
                                              String overlayNamespace,
                                              RegistryAccess registryAccess,
                                              TypeRegistry<EffectType<?>> effectTypes,
                                              TypeRegistry<ConditionType<?>> conditionTypes) {
        return new RuleLoadContext(null, overlayRoot, overlayNamespace,
                registryAccess, effectTypes, conditionTypes, PackLayerResolver.allWorld(), null);
    }
}
