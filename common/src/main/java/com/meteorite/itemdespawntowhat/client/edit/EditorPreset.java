package com.meteorite.itemdespawntowhat.client.edit;

import java.util.Objects;

/**
 * 字段预设值：一键填入某个数值（例如 spawn_entity.age 的「幼年」/「成年」）。
 * <p>预设只影响界面输入，写回 JSON 时仍然按字段类型转换。
 */
public record EditorPreset(String value, String labelKey) {

    public EditorPreset {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(labelKey, "labelKey");
        if (value.isBlank() || labelKey.isBlank()) {
            throw new IllegalArgumentException("预设值与 label key 不能为空");
        }
    }
}
