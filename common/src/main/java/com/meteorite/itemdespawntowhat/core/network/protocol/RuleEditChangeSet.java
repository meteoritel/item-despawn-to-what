package com.meteorite.itemdespawntowhat.core.network.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端提交的规则变更集（服务端权威保存协议的入参）。
 * expectedVersion 为客户端视图的版本戳；服务端版本不一致时拒绝整批并回传差异，避免覆盖他人改动。
 */
public record RuleEditChangeSet(int expectedVersion, List<RuleEdit> edits) {

    public RuleEditChangeSet {
        edits = List.copyOf(edits);
    }

    public boolean isEmpty() {
        return edits.isEmpty();
    }

    // 序列化为协议 JSON 文本（网络包只传这一个字符串）
    public String serialize() {
        JsonObject root = new JsonObject();
        root.addProperty("expected_version", expectedVersion);
        JsonArray array = new JsonArray();
        for (RuleEdit edit : edits) {
            array.add(edit.toJson());
        }
        root.add("edits", array);
        return root.toString();
    }

    // 解析协议 JSON 文本；失败时返回可读错误
    public static DataResult<RuleEditChangeSet> parse(String text) {
        if (text == null || text.isBlank()) {
            return DataResult.error(() -> "变更集为空");
        }
        try {
            JsonElement element = JsonParser.parseString(text);
            if (!element.isJsonObject()) {
                return DataResult.error(() -> "变更集不是 JSON 对象");
            }
            JsonObject root = element.getAsJsonObject();
            int version = root.has("expected_version") ? root.get("expected_version").getAsInt() : -1;
            List<RuleEdit> edits = new ArrayList<>();
            if (root.has("edits")) {
                for (JsonElement entry : root.getAsJsonArray("edits")) {
                    edits.add(RuleEdit.fromJson(entry.getAsJsonObject()));
                }
            }
            return DataResult.success(new RuleEditChangeSet(version, edits));
        } catch (RuntimeException e) {
            return DataResult.error(() -> "变更集解析失败: " + e.getMessage());
        }
    }
}
