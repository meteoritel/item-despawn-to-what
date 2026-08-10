package com.meteorite.itemdespawntowhat.config.conversion;

import com.meteorite.itemdespawntowhat.config.type.ConversionType;

/**
 * 带结果数量限制字段的实体类转换配置基类。
 */
public abstract class BaseItemToEntityConfig extends BaseLimitedConversionConfig{

    protected BaseItemToEntityConfig(ConversionType type) {
        super(type);
    }

    protected BaseItemToEntityConfig(ConversionType type, String item, String result) {
        super(type, item, result);
    }

}
