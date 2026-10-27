package com.meteorite.itemdespawntowhat.core.service.edit;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.RuleDecodeBudget;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 单条规则的来源视图：基底与覆盖的原始 JSON 及各自的来源元数据，编辑页按需请求。
 * 只承载与客户端一致的来源坐标，不含状态与问题清单（它们仍由快照条目下发）。
 * base / overlay 为 null 表示该层没有非控制条目副本。
 */
public record RuleSourceView(ResourceLocation id, @Nullable View base, @Nullable View overlay) {

    // 线上 JSON 字段键
    public static final String KEY_ID = "id";
    public static final String KEY_BASE = "base";
    public static final String KEY_OVERLAY = "overlay";

    public RuleSourceView {
        Objects.requireNonNull(id, "id");
    }

    /**
     * 单份来源视图：所属层（builtin/world/overlay）、数据包 id（覆盖层为 null）与文件路径。
     */
    public record View(String layer, @Nullable String packId, String path, JsonObject body) {

        // 线上 JSON 字段键
        public static final String KEY_LAYER = "layer";
        public static final String KEY_PACK = "pack";
        public static final String KEY_PATH = "path";
        public static final String KEY_BODY = "body";

        public View {
            layer = layer == null ? "" : layer;
            packId = packId == null || packId.isBlank() ? null : packId;
            path = path == null ? "" : path;
            Objects.requireNonNull(body, "body");
        }

        // 视图序列化：内容深拷贝，避免调用方后续改动影响已编码结果
        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty(KEY_LAYER, layer);
            if (packId == null) {
                json.add(KEY_PACK, JsonNull.INSTANCE);
            } else {
                json.addProperty(KEY_PACK, packId);
            }
            json.addProperty(KEY_PATH, path);
            json.add(KEY_BODY, body.deepCopy());
            return json;
        }

        // 视图反序列化；结构非法返回 null
        public static @Nullable View fromJson(@Nullable JsonElement element) {
            if (element == null || !element.isJsonObject()) {
                return null;
            }
            JsonObject json = element.getAsJsonObject();
            if (!json.has(KEY_BODY) || !json.get(KEY_BODY).isJsonObject()) {
                return null;
            }
            String pack = json.has(KEY_PACK) && json.get(KEY_PACK).isJsonPrimitive()
                    ? json.get(KEY_PACK).getAsString() : null;
            return new View(
                    string(json, KEY_LAYER, ""),
                    pack,
                    string(json, KEY_PATH, ""),
                    json.getAsJsonObject(KEY_BODY).deepCopy());
        }

        // 读取字符串字段；缺失或非字符串返回默认值
        private static String string(JsonObject json, String key, String fallback) {
            if (json.has(key) && json.get(key).isJsonPrimitive()) {
                return json.get(key).getAsString();
            }
            return fallback;
        }
    }

    // 两层来源是否都不存在
    public boolean isEmpty() {
        return base == null && overlay == null;
    }

    // 序列化为 JSON 文本（网络层只搬运文本，不解析内容）
    public String serialize() {
        JsonObject root = new JsonObject();
        root.addProperty(KEY_ID, id.toString());
        root.add(KEY_BASE, base == null ? JsonNull.INSTANCE : base.toJson());
        root.add(KEY_OVERLAY, overlay == null ? JsonNull.INSTANCE : overlay.toJson());
        return root.toString();
    }

    // 解析 JSON 文本；结构非法时返回失败结果
    public static DataResult<RuleSourceView> parse(String text) {
        if (text == null || text.isBlank()) {
            return DataResult.error(() -> "来源视图为空");
        }
        RuleDecodeBudget.ParseResult parsed = RuleDecodeBudget.parse(text);
        if (!parsed.ok()) {
            return DataResult.error(() -> "来源视图解析失败: " + parsed.failureReason());
        }
        try {
            JsonElement element = parsed.element();
            if (element == null || !element.isJsonObject()) {
                return DataResult.error(() -> "来源视图不是 JSON 对象");
            }
            JsonObject root = element.getAsJsonObject();
            ResourceLocation id = root.has(KEY_ID) && root.get(KEY_ID).isJsonPrimitive()
                    ? ResourceLocation.tryParse(root.get(KEY_ID).getAsString()) : null;
            if (id == null) {
                return DataResult.error(() -> "来源视图缺少合法规则 id");
            }
            return DataResult.success(new RuleSourceView(id,
                    View.fromJson(root.get(KEY_BASE)),
                    View.fromJson(root.get(KEY_OVERLAY))));
        } catch (RuntimeException e) {
            return DataResult.error(() -> "来源视图解析失败: " + e.getMessage());
        }
    }
}
