package com.meteorite.itemdespawntowhat.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 模组配置的包内字段声明：同一默认值与区间同时构造 Codec 和参考事实。
 * 声明仅在类初始化时创建，配置加载时用于识别未知键，参考投影仅由构建工具读取。
 */
@SuppressWarnings("SameParameterValue")
record ConfigField<T>(String name, MapCodec<T> codec, T defaultValue, String type,
                      JsonElement documentedDefault, @Nullable Integer min, @Nullable Integer max,
                      @Nullable String fallback, List<ConfigField<?>> children) {

    static ConfigField<Integer> integer(String name, int value, int min, int max) {
        return new ConfigField<>(name, Codec.intRange(min, max).optionalFieldOf(name, value), value,
                "integer", new JsonPrimitive(value), min, max, null, List.of());
    }

    static ConfigField<Optional<Integer>> optionalInteger(String name, int min, int max,
                                                         ConfigField<Integer> fallback) {
        return new ConfigField<>(name, Codec.intRange(min, max).optionalFieldOf(name), Optional.empty(),
                "integer", JsonNull.INSTANCE, min, max, fallback.name(), List.of());
    }

    static ConfigField<Boolean> bool(String name, boolean value) {
        return new ConfigField<>(name, Codec.BOOL.optionalFieldOf(name, value), value,
                "boolean", new JsonPrimitive(value), null, null, null, List.of());
    }

    static ConfigField<String> string(String name, String value) {
        return new ConfigField<>(name, Codec.STRING.optionalFieldOf(name, value), value,
                "string", new JsonPrimitive(value), null, null, null, List.of());
    }

    static <T> ConfigField<T> object(String name, Codec<T> codec, T value, List<ConfigField<?>> children) {
        return new ConfigField<>(name, codec.optionalFieldOf(name, value), value,
                "object", defaults(children), null, null, null, List.copyOf(children));
    }

    // 完整等价默认示例包含默认字段，但保留依赖其它字段的缺省值为空缺。
    static JsonObject defaults(List<ConfigField<?>> fields) {
        JsonObject result = new JsonObject();
        for (ConfigField<?> field : fields) {
            if (field.fallback() == null) {
                result.add(field.name(), field.documentedDefault().deepCopy());
            }
        }
        return result;
    }

    // 构建工具在 tools 下单独编译，IDE 的生产源集引用索引不包含该调用点。
    @SuppressWarnings("unused")
    JsonObject reference() {
        JsonObject result = new JsonObject();
        result.addProperty("key", name);
        result.addProperty("type", type);
        result.add("default", documentedDefault.deepCopy());
        if (min != null) {
            result.addProperty("min", min);
            result.addProperty("max", max);
        }
        if (fallback != null) {
            result.addProperty("fallback", fallback);
        }
        if (!children.isEmpty()) {
            var nested = new com.google.gson.JsonArray();
            children.forEach(field -> nested.add(field.reference()));
            result.add("children", nested);
        }
        return result;
    }

    static void warnUnknown(JsonElement json, List<ConfigField<?>> fields, IssueCollector issues,
                            String origin, String prefix) {
        if (!json.isJsonObject()) {
            return;
        }
        for (var entry : json.getAsJsonObject().entrySet()) {
            String path = prefix + entry.getKey();
            ConfigField<?> field = fields.stream().filter(candidate -> candidate.name().equals(entry.getKey()))
                    .findFirst().orElse(null);
            if (field == null) {
                issues.warn("server.json 未知配置键: " + path, origin, path);
            } else if (!field.children().isEmpty()) {
                warnUnknown(entry.getValue(), field.children(), issues, origin, path + ".");
            }
        }
    }
}
