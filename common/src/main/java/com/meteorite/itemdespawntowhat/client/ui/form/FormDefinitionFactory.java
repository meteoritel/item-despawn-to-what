package com.meteorite.itemdespawntowhat.client.ui.form;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;

/**
 * 在 Screen 初始化时使用当前字体创建完整表单定义。
 */
@FunctionalInterface
public interface FormDefinitionFactory<T extends BaseConversionConfig> {
    FormDefinition<T> create(FormFieldContext context);
}
