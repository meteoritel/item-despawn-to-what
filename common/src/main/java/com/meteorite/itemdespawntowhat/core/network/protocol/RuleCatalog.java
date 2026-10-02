package com.meteorite.itemdespawntowhat.core.network.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;

import java.util.ArrayList;
import java.util.List;

/***
 * 目录分页结果：P3 只做骨架收发（条目可为空），目录数据由 P4 的目录来源填充。
 * entries 的 JSON 文本经 RuleCatalogPayload 下发，客户端自行解析。
 */
public record RuleCatalog(RuleCatalogType type, int revision, List<RuleCatalogEntry> entries, boolean lastPage) {

    // 线上 JSON 键；字段名与契约 §3.6 一一对应
    public static final String KEY_TYPE = "type";
    public static final String KEY_REVISION = "revision";
    public static final String KEY_ENTRIES = "entries";
    public static final String KEY_LAST_PAGE = "last_page";

    public RuleCatalog {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    // 空目录（P3 骨架：类型合法但内容未接入）
    public static RuleCatalog empty(RuleCatalogType type, int revision) {
        return new RuleCatalog(type, revision, List.of(), true);
    }

    // 序列化为 JSON 文本
    public String serialize() {
        JsonObject root = new JsonObject();
        root.addProperty(KEY_TYPE, type == null ? "" : type.id());
        root.addProperty(KEY_REVISION, revision);
        JsonArray array = new JsonArray();
        for (RuleCatalogEntry entry : entries) {
            array.add(entry.toJson());
        }
        root.add(KEY_ENTRIES, array);
        root.addProperty(KEY_LAST_PAGE, lastPage);
        return root.toString();
    }

    // 解析 JSON 文本；类型未知或结构非法时返回失败结果
    public static DataResult<RuleCatalog> parse(String text) {
        if (text == null || text.isBlank()) {
            return DataResult.error(() -> "目录为空");
        }
        try {
            JsonElement element = JsonParser.parseString(text);
            if (!element.isJsonObject()) {
                return DataResult.error(() -> "目录不是 JSON 对象");
            }
            JsonObject root = element.getAsJsonObject();
            RuleCatalogType type = RuleCatalogType.fromId(root.has(KEY_TYPE) ? root.get(KEY_TYPE).getAsString() : null);
            if (type == null) {
                return DataResult.error(() -> "目录类型非法");
            }
            int revision = root.has(KEY_REVISION) ? root.get(KEY_REVISION).getAsInt() : -1;
            List<RuleCatalogEntry> entries = new ArrayList<>();
            if (root.has(KEY_ENTRIES) && root.get(KEY_ENTRIES).isJsonArray()) {
                for (JsonElement item : root.getAsJsonArray(KEY_ENTRIES)) {
                    if (!item.isJsonObject()) {
                        continue;
                    }
                    JsonObject json = item.getAsJsonObject();
                    entries.add(new RuleCatalogEntry(
                            json.has(RuleCatalogEntry.KEY_ID) ? json.get(RuleCatalogEntry.KEY_ID).getAsString() : "",
                            json.has(RuleCatalogEntry.KEY_LABEL) ? json.get(RuleCatalogEntry.KEY_LABEL).getAsString() : "",
                            json.has(RuleCatalogEntry.KEY_SUB_LABEL) ? json.get(RuleCatalogEntry.KEY_SUB_LABEL).getAsString() : "",
                            json.has(RuleCatalogEntry.KEY_ICON) ? json.get(RuleCatalogEntry.KEY_ICON).getAsString() : ""));
                }
            }
            boolean lastPage = !root.has(KEY_LAST_PAGE) || root.get(KEY_LAST_PAGE).getAsBoolean();
            return DataResult.success(new RuleCatalog(type, revision, entries, lastPage));
        } catch (RuntimeException e) {
            return DataResult.error(() -> "目录解析失败: " + e.getMessage());
        }
    }
}
