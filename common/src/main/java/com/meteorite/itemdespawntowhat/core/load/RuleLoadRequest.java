package com.meteorite.itemdespawntowhat.core.load;

import com.meteorite.itemdespawntowhat.core.api.RuleDecoder;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 一次规则加载的输入参数。
 * 三层来源均为可选：resourceManager 提供内置 + 世界数据包层，overlayRoot 提供 config 覆盖层；
 * 解码实现由调用方以 RuleDecoder 注入（模型层提供），包层判定由 PackLayerResolver 注入。
 */
public record RuleLoadRequest<T>(
        // 数据包层资源管理器（MinecraftServer#getResourceManager）；为 null 时跳过数据包层
        @Nullable ResourceManager resourceManager,
        // 覆盖层根目录（config/itemdespawntowhat）；为 null 时跳过覆盖层
        @Nullable Path overlayRoot,
        // 覆盖层文件缺省 id 使用的命名空间（通常为模组 id）
        String overlayDefaultNamespace,
        // 单条规则解码实现（模型层注入）
        RuleDecoder<T> decoder,
        // 数据包层来源判定
        PackLayerResolver layerResolver
) {

    public RuleLoadRequest {
        Objects.requireNonNull(overlayDefaultNamespace, "overlayDefaultNamespace");
        Objects.requireNonNull(decoder, "decoder");
        Objects.requireNonNull(layerResolver, "layerResolver");
    }

    // 仅加载数据包层（内置 + 世界数据包）
    public static <T> RuleLoadRequest<T> datapacks(ResourceManager resourceManager,
                                                   RuleDecoder<T> decoder,
                                                   PackLayerResolver layerResolver,
                                                   String overlayDefaultNamespace) {
        return new RuleLoadRequest<>(resourceManager, null, overlayDefaultNamespace, decoder, layerResolver);
    }

    // 仅加载 config 覆盖层
    public static <T> RuleLoadRequest<T> overlay(Path overlayRoot,
                                                 String overlayDefaultNamespace,
                                                 RuleDecoder<T> decoder) {
        return new RuleLoadRequest<>(null, overlayRoot, overlayDefaultNamespace, decoder,
                PackLayerResolver.allWorld());
    }
}
