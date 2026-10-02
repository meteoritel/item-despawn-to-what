package com.meteorite.itemdespawntowhat.client.ui.view;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;

import java.util.ArrayList;
import java.util.List;

/**
 * 源匹配的视图模型：包裹 source 对象的协议 JSON，提供匹配项与排除项的读写。
 * 本层只认识协议 JSON，不引用 core/model 的服务端模型类。
 */
public final class SourceView {

    private final JsonObject json;

    private SourceView(JsonObject json) {
        this.json = json;
    }

    // 绑定到既有 JSON 对象（不复制），供 RuleView 暴露可写嵌套视图
    static SourceView wrap(JsonObject json) {
        return new SourceView(json);
    }

    // 空源匹配
    public static SourceView empty() {
        return new SourceView(new JsonObject());
    }

    // 由协议 JSON 解析；缺失或类型不符时返回空视图
    public static SourceView fromJson(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return empty();
        }
        return wrap(element.getAsJsonObject().deepCopy());
    }

    // 匹配项，元素为物品 id 或 #tag
    public List<String> items() {
        return readStrings(RuleFields.SOURCE_ITEMS);
    }

    public void setItems(List<String> values) {
        writeStrings(RuleFields.SOURCE_ITEMS, values);
    }

    // 排除项，优先级高于匹配项
    public List<String> exclude() {
        return readStrings(RuleFields.SOURCE_EXCLUDE);
    }

    public void setExclude(List<String> values) {
        writeStrings(RuleFields.SOURCE_EXCLUDE, values);
    }

    // 逗号分隔文本形态，供表单文本框直接读写
    public String itemsText() {
        return String.join(", ", items());
    }

    public void setItemsText(String text) {
        setItems(split(text));
    }

    public String excludeText() {
        return String.join(", ", exclude());
    }

    public void setExcludeText(String text) {
        setExclude(split(text));
    }

    // 是否至少有一个匹配项（服务端要求 items 非空）
    public boolean hasItems() {
        return !items().isEmpty();
    }

    public JsonObject toJson() {
        return json.deepCopy();
    }

    // 逗号分隔文本切分：去空白、丢弃空段
    public static List<String> split(String text) {
        List<String> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        for (String piece : text.split(",")) {
            String trimmed = piece.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private List<String> readStrings(String key) {
        List<String> result = new ArrayList<>();
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return result;
        }
        for (JsonElement entry : json.getAsJsonArray(key)) {
            if (entry.isJsonPrimitive()) {
                result.add(entry.getAsString());
            }
        }
        return result;
    }

    private void writeStrings(String key, List<String> values) {
        JsonArray array = new JsonArray();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    array.add(new JsonPrimitive(value.trim()));
                }
            }
        }
        json.add(key, array);
    }
}
