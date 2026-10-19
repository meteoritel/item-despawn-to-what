package com.meteorite.itemdespawntowhat.core.service;

import com.meteorite.itemdespawntowhat.core.api.RuleRuntimeLimits;
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
 * 这是装配层唯一的入参容器：三层来源、注册表、注册表访问器与服务端运行期上限都在这里显式传入，
 * 装配层本身不持有全局状态。
 */
public record RuleLoadContext(
        @Nullable ResourceManager resourceManager,
        @Nullable Path overlayRoot,
        String overlayNamespace,
        RegistryAccess registryAccess,
        TypeRegistry<EffectType<?>> effectTypes,
        TypeRegistry<ConditionType<?>> conditionTypes,
        PackLayerResolver layerResolver,
        @Nullable net.minecraft.server.MinecraftServer server,
        RuleRuntimeLimits runtimeLimits
) {

    public RuleLoadContext {
        Objects.requireNonNull(overlayNamespace, "overlayNamespace");
        Objects.requireNonNull(registryAccess, "registryAccess");
        Objects.requireNonNull(effectTypes, "effectTypes");
        Objects.requireNonNull(conditionTypes, "conditionTypes");
        Objects.requireNonNull(layerResolver, "layerResolver");
        runtimeLimits = runtimeLimits == null ? RuleRuntimeLimits.UNLIMITED : runtimeLimits;
    }

    // 平台运行时附加当前服务端，动态引用以实际已加载内容为准。
    public RuleLoadContext withServer(net.minecraft.server.MinecraftServer currentServer) {
        return new RuleLoadContext(resourceManager, overlayRoot, overlayNamespace, registryAccess,
                effectTypes, conditionTypes, layerResolver, currentServer, runtimeLimits);
    }

    /**
     * 附加当前服务端生效的运行期上限：加载与保存校验据此拒载超出配置上限的规则。
     *
     * @param limits 服务端配置换算出的运行期上限
     * @return 仅替换上限的新上下文
     */
    public RuleLoadContext withRuntimeLimits(RuleRuntimeLimits limits) {
        return new RuleLoadContext(resourceManager, overlayRoot, overlayNamespace, registryAccess,
                effectTypes, conditionTypes, layerResolver, server, limits);
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
                registryAccess, effectTypes, conditionTypes, layerResolver, null, RuleRuntimeLimits.UNLIMITED);
    }
}
