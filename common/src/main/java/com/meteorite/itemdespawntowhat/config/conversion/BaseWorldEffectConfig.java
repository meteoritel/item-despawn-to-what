package com.meteorite.itemdespawntowhat.config.conversion;

import com.meteorite.itemdespawntowhat.config.type.ConversionType;

/**
 * 不需要 result 字段的世界效果转化配置基类。
 */
public abstract class BaseWorldEffectConfig extends BaseConversionConfig {
    protected BaseWorldEffectConfig(ConversionType type) {
        super(type);
    }

    @Override
    protected final boolean isResultIdRequired() {
        return false;
    }
}
