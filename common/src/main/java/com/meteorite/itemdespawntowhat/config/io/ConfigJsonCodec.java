package com.meteorite.itemdespawntowhat.config.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonElement;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.util.JsonOrderTypeAdapterFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 负责单一配置类型列表的 JSON 编解码。
 */
public final class ConfigJsonCodec<T extends BaseConversionConfig> {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapterFactory(new JsonOrderTypeAdapterFactory())
            .create();

    private final Type listType;
    private final Set<String> allowedTopLevelFields;

    public ConfigJsonCodec(Type listType) {
        this.listType = listType;
        this.allowedTopLevelFields = collectAllowedFields(listType);
    }

    public String serialize(List<? extends BaseConversionConfig> configs) {
        return GSON.toJson(configs);
    }

    public JsonElement toJsonTree(BaseConversionConfig config) {
        return GSON.toJsonTree(config);
    }

    public List<T> deserialize(String json) {
        try {
            return deserializeStrict(json);
        } catch (Exception e) {
            LOGGER.warn("Failed to deserialize conversion config", e);
            return new ArrayList<>();
        }
    }

    public List<T> deserializeStrict(String json) {
        return deserializeWithMigration(json).entries();
    }

    public DecodeResult<T> deserializeWithMigration(String json) {
        if (json == null) {
            throw new JsonParseException("Config JSON cannot be null");
        }

        JsonElement parsed = GSON.fromJson(json, JsonElement.class);
        ConfigMigrator.MigrationResult migration = ConfigMigrator.migrate(parsed);
        validateTopLevelFields(migration.json());
        List<T> entries = GSON.fromJson(migration.json(), listType);
        if (entries == null) {
            throw new JsonParseException("Config JSON must be an array");
        }
        return new DecodeResult<>(entries, GSON.toJson(migration.json()), migration.migrated());
    }

    private void validateTopLevelFields(JsonElement root) {
        for (JsonElement element : root.getAsJsonArray()) {
            for (String field : element.getAsJsonObject().keySet()) {
                if (!allowedTopLevelFields.contains(field)) {
                    throw new JsonParseException("Unknown config field: " + field);
                }
            }
        }
    }

    private static Set<String> collectAllowedFields(Type listType) {
        if (!(listType instanceof ParameterizedType parameterized)
                || parameterized.getActualTypeArguments().length != 1
                || !(parameterized.getActualTypeArguments()[0] instanceof Class<?> entryType)) {
            throw new IllegalArgumentException("Config list type must contain a concrete entry class");
        }

        Set<String> names = new HashSet<>();
        for (Class<?> current = entryType; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                var serializedName = field.getAnnotation(com.google.gson.annotations.SerializedName.class);
                names.add(serializedName == null ? field.getName() : serializedName.value());
                if (serializedName != null) {
                    names.addAll(List.of(serializedName.alternate()));
                }
            }
        }
        return Set.copyOf(names);
    }

    /** 保存解码结果以及可用于安全写回的迁移后 JSON。 */
    public record DecodeResult<T>(List<T> entries, String migratedJson, boolean migrated) {
    }
}
