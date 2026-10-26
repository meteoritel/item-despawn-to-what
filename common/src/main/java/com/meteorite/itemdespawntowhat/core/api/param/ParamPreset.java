package com.meteorite.itemdespawntowhat.core.api.param;

import java.util.Objects;

/**
 * 数值参数的常用取值按钮。
 *
 * @param value 写入 JSON 的取值文本
 * @param labelSuffix 界面本地化 key 中 {@code gui.itemdespawntowhat.edit.} 之后的片段
 */
public record ParamPreset(String value, String labelSuffix) {

    public ParamPreset {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(labelSuffix, "labelSuffix");
    }
}
