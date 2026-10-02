package com.meteorite.itemdespawntowhat.core.network.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;

import java.util.ArrayList;
import java.util.List;

/***
 * 服务端下发的规则快照：版本戳 + 上下文修订 + 逐条规则的来源视图 + 整体问题清单。
 * rules: List&lt;JsonObject&gt; 与 issues: List&lt;String&gt; 已被契约 §3.6 的 entries/issues 取代。
 */
public record RuleSnapshot(int version, int contextRevision, List<RuleSnapshotEntry> entries, List<RuleIssue> issues) {

    // 线上 JSON 键；字段名与契约 §3.6 一一对应
    public static final String KEY_VERSION = "version";
    public static final String KEY_CONTEXT_REVISION = "context_revision";
    public static final String KEY_ENTRIES = "entries";
    public static final String KEY_ISSUES = "issues";

    public RuleSnapshot {
        entries = entries == null ? List.of() : List.copyOf(entries);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    // 序列化为 JSON 文本（网络层只搬运文本，不解析内容）
    public String serialize() {
        JsonObject root = new JsonObject();
        root.addProperty(KEY_VERSION, version);
        root.addProperty(KEY_CONTEXT_REVISION, contextRevision);
        JsonArray entryArray = new JsonArray();
        for (RuleSnapshotEntry entry : entries) {
            entryArray.add(entry.toJson());
        }
        root.add(KEY_ENTRIES, entryArray);
        JsonArray issueArray = new JsonArray();
        for (RuleIssue issue : issues) {
            issueArray.add(issue.toJson());
        }
        root.add(KEY_ISSUES, issueArray);
        return root.toString();
    }

    // 解析 JSON 文本；结构非法时返回失败结果
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
            int version = root.has(KEY_VERSION) ? root.get(KEY_VERSION).getAsInt() : -1;
            int contextRevision = root.has(KEY_CONTEXT_REVISION) ? root.get(KEY_CONTEXT_REVISION).getAsInt() : -1;
            List<RuleSnapshotEntry> entries = new ArrayList<>();
            if (root.has(KEY_ENTRIES) && root.get(KEY_ENTRIES).isJsonArray()) {
                for (JsonElement item : root.getAsJsonArray(KEY_ENTRIES)) {
                    RuleSnapshotEntry entry = RuleSnapshotEntry.fromJson(item);
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
            }
            List<RuleIssue> issues = new ArrayList<>();
            if (root.has(KEY_ISSUES) && root.get(KEY_ISSUES).isJsonArray()) {
                for (JsonElement item : root.getAsJsonArray(KEY_ISSUES)) {
                    RuleIssue issue = RuleIssue.fromJson(item);
                    if (issue != null) {
                        issues.add(issue);
                    }
                }
            }
            return DataResult.success(new RuleSnapshot(version, contextRevision, entries, issues));
        } catch (RuntimeException e) {
            return DataResult.error(() -> "快照解析失败: " + e.getMessage());
        }
    }
}
