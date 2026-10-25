package com.meteorite.itemdespawntowhat.core.network.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.RuleDecodeBudget;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditLimits;
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

    // 启用状态动作（enable/disable/reset）：只改本服状态层，不推进内容修订号
    public List<RuleEdit> enableEdits() {
        return edits.stream().filter(RuleEdit::enableEdit).toList();
    }

    // 是否含内容动作
    public boolean hasContentEdits() {
        return edits.stream().anyMatch(RuleEdit::contentEdit);
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
        RuleDecodeBudget.ParseResult parsed = RuleDecodeBudget.parse(text);
        if (!parsed.ok()) {
            // 统一有界解析入口报出的失败原因
            return DataResult.error(() -> "变更集解析失败: " + parsed.failureReason());
        }
        try {
            JsonElement element = parsed.element();
            if (element == null || !element.isJsonObject()) {
                return DataResult.error(() -> "变更集不是 JSON 对象");
            }
            JsonObject root = element.getAsJsonObject();
            int version = root.has("expected_version") ? root.get("expected_version").getAsInt() : -1;
            List<RuleEdit> edits = new ArrayList<>();
            if (root.has("edits")) {
                JsonArray rawEdits = root.getAsJsonArray("edits");
                if (rawEdits.size() > RuleEditLimits.MAX_EDITS_PER_CHANGE_SET) {
                    return DataResult.error(() -> "变更集条数超过上限: " + RuleEditLimits.MAX_EDITS_PER_CHANGE_SET);
                }
                for (JsonElement entry : rawEdits) {
                    edits.add(RuleEdit.fromJson(entry.getAsJsonObject()));
                }
            }
            return DataResult.success(new RuleEditChangeSet(version, edits));
        } catch (RuntimeException e) {
            return DataResult.error(() -> "变更集解析失败: " + e.getMessage());
        }
    }
}
