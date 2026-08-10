package com.meteorite.itemdespawntowhat.client.ui.presentation;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import net.minecraft.world.item.ItemStack;

/**
 * 将序列化配置转换为纯客户端列表展示数据。
 */
@FunctionalInterface
public interface ConfigPresenter<T extends BaseConversionConfig> {
    ConfigPresentation present(T config, ItemStack sourceIcon);
}
