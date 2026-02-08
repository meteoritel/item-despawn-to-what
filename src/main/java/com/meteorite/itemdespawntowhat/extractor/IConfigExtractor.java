package com.meteorite.itemdespawntowhat.extractor;

import com.meteorite.itemdespawntowhat.config.BaseConversionConfig;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

@Deprecated
public interface IConfigExtractor <T extends BaseConversionConfig> {

    Set<ResourceLocation> getAllItemIds(List<T> configs);

    // 获取配置类型
    String getConfigType();

    // 检查配置是否有效
    boolean isValidConfig(T config);

    // 生成配置的条件检查缓存键
    String generateCacheKey(T config);

    //
    List<T> getItemConfigs(List<T> configs, ResourceLocation itemId);

    T getConfigByInternalId(List<T> configs, String internalId);
}
