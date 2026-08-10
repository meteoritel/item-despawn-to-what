package com.meteorite.itemdespawntowhat.client.ui.form;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;

/**
 * 定义一种配置独有字段的组件、映射与交互行为。
 */
public interface ConfigFormSection<T extends BaseConversionConfig> {
    void initialize(ConfigFormContext context);

    void writeTo(T config);

    void readFrom(T config);

    void clear();

    default boolean showsResultId() {
        return true;
    }

    default void registerValidators(ConfigFormContext context) {
    }

    default void registerSuggestions(ConfigFormContext context) {
    }

    default void registerFocus(ConfigFormContext context) {
    }

    default void refresh() {
    }
}
