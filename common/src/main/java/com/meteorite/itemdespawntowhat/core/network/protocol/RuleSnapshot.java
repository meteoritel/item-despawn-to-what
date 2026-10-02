package com.meteorite.itemdespawntowhat.core.network.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端下发的规则快照：覆盖层当前全部规则的原始 JSON + 版本戳 + 加载期问题文本。
 * 客户端只需按 JSON 渲染与编辑，不依赖服务端模型类（视图模型自行解析）。
 */
public record RuleSnapshot(int version, List<JsonObject> rules, List<String> issues) {

    public RuleSnapshot {
        rules = List.copyOf(rules);
        issues = List.copyOf(issues);
    }

    public String serialize() {
        JsonObject root = new JsonObject();
        root.addProperty("version", version);
        JsonArray array = new JsonArray();
        for (JsonObject rule : rules) {
            array.add(rule.deepCopy());
        }
        root.add("rules", array);
        JsonArray issueArray = new JsonArray();
        for (String issue : issues) {
            issueArray.add(issue);
        }
        root.add("issues", issueArray);
        return root.toString();
    }

    public static DataResult<RuleSnapshot> parse(String text) {
        if (text == null || text.isBlank()) {
            return DataResult.error(() -> "快照为空");
        }
        try {
            JsonElement element = JsonParser.parseString(text);
            if (!element.isJsonObject()) {
                return DataResult.error(() -> "快照不是 JSON 对象");
            }
            JsonObject root = element.getAsJsonObject();
            int version = root.has("version") ? root.get("version").getAsInt() : -1;
            List<JsonObject> rules = new ArrayList<>();
            if (root.has("rules")) {
                for (JsonElement entry : root.getAsJsonArray("rules")) {
                    if (entry.isJsonObject()) {
                        rules.add(entry.getAsJsonObject().deepCopy());
                    }
                }
            }
            List<String> issues = new ArrayList<>();
            if (root.has("issues")) {
                for (JsonElement entry : root.getAsJsonArray("issues")) {
                    issues.add(entry.getAsString());
                }
            }
            return DataResult.success(new RuleSnapshot(version, rules, issues));
        } catch (RuntimeException e) {
            return DataResult.error(() -> "快照解析失败: " + e.getMessage());
        }
    }
}
