package com.meteorite.itemdespawntowhat.client.ui.view;

import java.util.List;

/**
 * 参数声明式规格：字段名、i18n key、控件种类、默认值与是否必填。
 * 效果表单与条件参数编辑器共用同一套规格，新增类型只需登记规格。
 */
public record ParamSpec(String key, String labelKey, Kind kind, String defaultValue,
                        List<String> enumValues, boolean required) {

    // 参数控件种类
    public enum Kind {
        TEXT,
        INTEGER,
        DECIMAL,
        BOOLEAN,
        ENUM,
        // 逗号分隔文本 <-> 字符串数组
        LIST
    }

    public static ParamSpec text(String key, String labelKey, boolean required) {
        return new ParamSpec(key, labelKey, Kind.TEXT, "", List.of(), required);
    }

    public static ParamSpec integer(String key, String labelKey, String defaultValue) {
        return new ParamSpec(key, labelKey, Kind.INTEGER, defaultValue, List.of(), false);
    }

    public static ParamSpec decimal(String key, String labelKey, String defaultValue) {
        return new ParamSpec(key, labelKey, Kind.DECIMAL, defaultValue, List.of(), false);
    }

    public static ParamSpec bool(String key, String labelKey, String defaultValue) {
        return new ParamSpec(key, labelKey, Kind.BOOLEAN, defaultValue, List.of(), false);
    }

    public static ParamSpec enumOf(String key, String labelKey, String defaultValue, List<String> values) {
        return new ParamSpec(key, labelKey, Kind.ENUM, defaultValue, List.copyOf(values), false);
    }

    public static ParamSpec list(String key, String labelKey, boolean required) {
        return new ParamSpec(key, labelKey, Kind.LIST, "", List.of(), required);
    }
}
