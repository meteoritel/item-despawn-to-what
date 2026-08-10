package com.meteorite.itemdespawntowhat.client.ui.presentation;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import net.minecraft.network.chat.MutableComponent;

/**
 * 为配置列表 tooltip 追加类型专属内容。
 */
@FunctionalInterface
public interface ConfigTooltipProvider<T extends BaseConversionConfig> {
    void append(T config, MutableComponent tooltip);
}
