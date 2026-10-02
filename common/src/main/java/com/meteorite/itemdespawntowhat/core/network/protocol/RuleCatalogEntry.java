package com.meteorite.itemdespawntowhat.core.network.protocol;

import com.google.gson.JsonObject;

/***
 * 目录条目：id 为注册表 id 或标签 id，label 为展示名（已本地化 key 或原文）。
 * subLabel/icon 允许空串，客户端按缺省渲染。
 */
public record RuleCatalogEntry(String id, String label, String subLabel, String icon) {

    // 线上 JSON 键；字段名与契约 §3.6 一一对应
    public static final String KEY_ID = "id";
    public static final String KEY_LABEL = "label";
    public static final String KEY_SUB_LABEL = "sub_label";
    public static final String KEY_ICON = "icon";

    public RuleCatalogEntry {
        id = id == null ? "" : id;
        label = label == null ? "" : label;
        subLabel = subLabel == null ? "" : subLabel;
        icon = icon == null ? "" : icon;
    }

    // 条目序列化
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty(KEY_ID, id);
        json.addProperty(KEY_LABEL, label);
        json.addProperty(KEY_SUB_LABEL, subLabel);
        json.addProperty(KEY_ICON, icon);
        return json;
    }
}
