package com.meteorite.itemdespawntowhat.client.ui.shared;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** 流体目录的名称与过滤策略；图标绘制由 PreviewIcons 统一分发。 */
public final class FluidPreviewIcons {
    private FluidPreviewIcons() { }

    // 未知引用仍由校验处理；选择目录过滤空流体与流动变体。
    /** 目录只展示非空源流体。 */
    public static boolean isExcluded(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key != null && BuiltInRegistries.FLUID.getOptional(key)
                .map(fluid -> fluid.defaultFluidState().isEmpty() || !fluid.defaultFluidState().isSource()).orElse(false);
    }

    /** 使用流体放置态方块的本地化名称。 */
    public static @Nullable Component label(ResourceLocation id) {
        return BuiltInRegistries.FLUID.getOptional(id)
                .map(fluid -> {
                    var state = fluid.defaultFluidState();
                    return state.createLegacyBlock().getBlock().getName();
                }).orElse(null);
    }

}
