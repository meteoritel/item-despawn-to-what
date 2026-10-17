package com.meteorite.itemdespawntowhat.core.network.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 快照中的一条规则：只携带列表与编辑所需的生效视图、来源、状态、可编辑标记、分类与问题清单，
 * 以及「是否存在基底 / 覆盖副本」两个标记；基底与覆盖内容改由 {@link RuleSourceView} 按需请求下发。
 * origin 取值 overlay/datapack/mixed；status 取值 active/disabled/masked/invalid。
 * category 为装配后的分类：显式声明优先（覆盖层视图优先于数据包视图），未声明时按生效效果派生；
 * 无法派生（如已删除条目）时为 null。分类只供列表分组，不参与运行时解码与执行。
 * hasBase/hasOverlay 仅表示该条目在对应层是否有非控制条目副本，不承担内容语义。
 */
public record RuleSnapshotEntry(ResourceLocation id, String origin, String status,
                                boolean editable, @Nullable String category,
                                @Nullable JsonObject effective,
                                boolean hasBase, boolean hasOverlay,
                                List<RuleIssue> issues) {

    // 来源取值
    public static final String ORIGIN_OVERLAY = "overlay";
    public static final String ORIGIN_DATAPACK = "datapack";
    public static final String ORIGIN_MIXED = "mixed";

    // 状态取值
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_DISABLED = "disabled";
    public static final String STATUS_MASKED = "masked";
    public static final String STATUS_INVALID = "invalid";

    // 线上 JSON 字段键
    public static final String KEY_ID = "id";
    public static final String KEY_ORIGIN = "origin";
    public static final String KEY_STATUS = "status";
    public static final String KEY_EDITABLE = "editable";
    public static final String KEY_CATEGORY = "category";
    public static final String KEY_EFFECTIVE = "effective";
    public static final String KEY_HAS_BASE = "has_base";
    public static final String KEY_HAS_OVERLAY = "has_overlay";
    public static final String KEY_ISSUES = "issues";

    public RuleSnapshotEntry {
        origin = origin == null || origin.isBlank() ? ORIGIN_DATAPACK : origin;
        status = status == null || status.isBlank() ? STATUS_ACTIVE : status;
        category = category == null || category.isBlank() ? null : category;
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    // 条目序列化：生效视图缺失时输出 null，客户端按「无此视图」处理；未声明分类同样输出 null
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty(KEY_ID, id.toString());
        json.addProperty(KEY_ORIGIN, origin);
        json.addProperty(KEY_STATUS, status);
        json.addProperty(KEY_EDITABLE, editable);
        if (category == null) {
            json.add(KEY_CATEGORY, JsonNull.INSTANCE);
        } else {
            json.addProperty(KEY_CATEGORY, category);
        }
        json.add(KEY_EFFECTIVE, effective == null ? JsonNull.INSTANCE : effective.deepCopy());
        json.addProperty(KEY_HAS_BASE, hasBase);
        json.addProperty(KEY_HAS_OVERLAY, hasOverlay);
        JsonArray issueArray = new JsonArray();
        for (RuleIssue issue : issues) {
            issueArray.add(issue.toJson());
        }
        json.add(KEY_ISSUES, issueArray);
        return json;
    }

    // 条目反序列化；id 非法或缺失返回 null，调用方跳过该条
    public static @Nullable RuleSnapshotEntry fromJson(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject json = element.getAsJsonObject();
        ResourceLocation id = json.has(KEY_ID) && json.get(KEY_ID).isJsonPrimitive()
                ? ResourceLocation.tryParse(json.get(KEY_ID).getAsString()) : null;
        if (id == null) {
            return null;
        }
        List<RuleIssue> issues = new ArrayList<>();
        if (json.has(KEY_ISSUES) && json.get(KEY_ISSUES).isJsonArray()) {
            for (JsonElement issue : json.getAsJsonArray(KEY_ISSUES)) {
                RuleIssue parsed = RuleIssue.fromJson(issue);
                if (parsed != null) {
                    issues.add(parsed);
                }
            }
        }
        return new RuleSnapshotEntry(
                id,
                string(json, KEY_ORIGIN, ORIGIN_DATAPACK),
                string(json, KEY_STATUS, STATUS_ACTIVE),
                bool(json, KEY_EDITABLE),
                category(json),
                object(json, KEY_EFFECTIVE),
                bool(json, KEY_HAS_BASE),
                bool(json, KEY_HAS_OVERLAY),
                issues);
    }

    // 读取分类字段；缺失、非字符串或空白一律视为未声明
    private static @Nullable String category(JsonObject json) {
        JsonElement value = json.get(KEY_CATEGORY);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return null;
        }
        String text = value.getAsString();
        return text.isBlank() ? null : text;
    }

    // 读取字符串字段；缺失或非字符串返回默认值
    private static String string(JsonObject json, String key, String fallback) {
        if (json.has(key) && json.get(key).isJsonPrimitive()) {
            return json.get(key).getAsString();
        }
        return fallback;
    }

    // 读取布尔字段；缺失或非布尔返回 false
    private static boolean bool(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive()
                && json.get(key).getAsJsonPrimitive().isBoolean()
                && json.get(key).getAsBoolean();
    }

    // 读取对象字段；缺失或非对象返回 null
    private static @Nullable JsonObject object(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonObject() ? json.getAsJsonObject(key).deepCopy() : null;
    }
}
