package com.meteorite.itemdespawntowhat.client.ui.presentation;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 保存配置列表渲染一个转换结果所需的客户端数据。
 */
public record ConfigPresentation(
        ItemStack icon,
        Component name,
        @Nullable EntityType<?> entityType,
        Component summary
) {
}
