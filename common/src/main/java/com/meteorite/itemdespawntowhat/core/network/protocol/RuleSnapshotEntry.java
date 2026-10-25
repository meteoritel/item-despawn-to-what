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
 * origin 取值 overlay/datapack/mixed，用于列表的来源筛选；source 进一步区分数据包的物理层
 * （builtin/world/overlay/mixed），客户端据此判定条目是否只读。
 * status 取生效值语义：active（生效启用）/ disabled（生效停用）/ invalid（内容非法）。
 * editable 只表示内容可写：来源可写且持有可打开的规则内容；启停对所有条目可用，不受它限制。
 * defaultEnabled 是规则内容声明的默认启用值，enabled 是本服生效值（本服覆盖 ?? 默认启用），
 * enabledOverride 是本服覆盖值：null 表示继承默认，非 null 表示本服显式设过（含与默认相同的情况）。
 * category 为装配后的分类：显式声明优先（覆盖层视图优先于数据包视图），未声明时按生效效果派生；
 * 无法派生时为 null。分类只供列表分组，不参与运行时解码与执行。
 * hasBase/hasOverlay 仅表示该条目在对应层是否有副本，不承担内容语义。
 */
public record RuleSnapshotEntry(ResourceLocation id, String origin, String source, String status,
                                boolean editable,
                                boolean defaultEnabled, boolean enabled, @Nullable Boolean enabledOverride,
                                @Nullable String category,
                                @Nullable JsonObject effective,
                                boolean hasBase, boolean hasOverlay,
                                List<RuleIssue> issues) {

    // 来源取值
    public static final String ORIGIN_OVERLAY = "overlay";
    public static final String ORIGIN_DATAPACK = "datapack";
    public static final String ORIGIN_MIXED = "mixed";

    // 物理来源取值：builtin / world 为只读数据包，overlay / mixed 含用户覆盖内容
    public static final String SOURCE_BUILTIN = "builtin";
    public static final String SOURCE_WORLD = "world";
    public static final String SOURCE_OVERLAY = "overlay";
    public static final String SOURCE_MIXED = "mixed";
    // 无法判定具体数据包层时的保守回退：按只读处理
    public static final String SOURCE_DATAPACK = "datapack";

    // 状态取值：按生效启用值判定，invalid 表示内容非法
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_DISABLED = "disabled";
    public static final String STATUS_INVALID = "invalid";

    // 线上 JSON 字段键
    public static final String KEY_ID = "id";
    public static final String KEY_ORIGIN = "origin";
    public static final String KEY_SOURCE = "source";
    public static final String KEY_STATUS = "status";
    public static final String KEY_EDITABLE = "editable";
    public static final String KEY_DEFAULT_ENABLED = "default_enabled";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_ENABLED_OVERRIDE = "enabled_override";
    public static final String KEY_CATEGORY = "category";
    public static final String KEY_EFFECTIVE = "effective";
    public static final String KEY_HAS_BASE = "has_base";
    public static final String KEY_HAS_OVERLAY = "has_overlay";
    public static final String KEY_ISSUES = "issues";

    public RuleSnapshotEntry {
        origin = origin == null || origin.isBlank() ? ORIGIN_DATAPACK : origin;
        source = normalizeSource(source, origin);
        status = status == null || status.isBlank() ? STATUS_ACTIVE : status;
        category = category == null || category.isBlank() ? null : category;
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    /**
     * 条目是否只读：只有携带用户覆盖内容（overlay / mixed）的来源可写；
     * builtin / world 数据包与未知来源一律按只读处理，客户端据此禁用编辑与删除。
     *
     * @return 是否只读
     */
    public boolean readOnly() {
        return !isWritableSource(source);
    }

    /**
     * 来源是否允许 GUI 写入。
     *
     * @param source 物理来源标识
     * @return true 表示 overlay / mixed，false 表示数据包层或未知来源
     */
    public static boolean isWritableSource(@Nullable String source) {
        return SOURCE_OVERLAY.equals(source) || SOURCE_MIXED.equals(source);
    }

    // 缺失来源时按 origin 回退：只有覆盖与混合来源可写，其余按只读数据包处理
    private static String normalizeSource(@Nullable String source, String origin) {
        if (source != null && !source.isBlank()) {
            return source;
        }
        return switch (origin) {
            case ORIGIN_OVERLAY -> SOURCE_OVERLAY;
            case ORIGIN_MIXED -> SOURCE_MIXED;
            default -> SOURCE_DATAPACK;
        };
    }

    // 条目序列化：生效视图缺失时输出 null，客户端按「无此视图」处理；未声明分类同样输出 null
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty(KEY_ID, id.toString());
        json.addProperty(KEY_ORIGIN, origin);
        json.addProperty(KEY_SOURCE, source);
        json.addProperty(KEY_STATUS, status);
        json.addProperty(KEY_EDITABLE, editable);
        json.addProperty(KEY_DEFAULT_ENABLED, defaultEnabled);
        json.addProperty(KEY_ENABLED, enabled);
        if (enabledOverride == null) {
            json.add(KEY_ENABLED_OVERRIDE, JsonNull.INSTANCE);
        } else {
            json.addProperty(KEY_ENABLED_OVERRIDE, enabledOverride);
        }
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
                string(json, KEY_SOURCE, ""),
                string(json, KEY_STATUS, STATUS_ACTIVE),
                bool(json, KEY_EDITABLE, false),
                bool(json, KEY_DEFAULT_ENABLED, true),
                bool(json, KEY_ENABLED, true),
                override(json),
                category(json),
                object(json, KEY_EFFECTIVE),
                bool(json, KEY_HAS_BASE, false),
                bool(json, KEY_HAS_OVERLAY, false),
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

    // 读取布尔字段；缺失或非布尔返回给定默认值
    private static boolean bool(JsonObject json, String key, boolean fallback) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            return fallback;
        }
        return value.getAsBoolean();
    }

    // 读取可空布尔：null 或非布尔表示没有本服覆盖
    private static @Nullable Boolean override(JsonObject json) {
        JsonElement value = json.get(KEY_ENABLED_OVERRIDE);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            return null;
        }
        return value.getAsBoolean();
    }

    // 读取对象字段；缺失或非对象返回 null
    private static @Nullable JsonObject object(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonObject() ? json.getAsJsonObject(key).deepCopy() : null;
    }
}
