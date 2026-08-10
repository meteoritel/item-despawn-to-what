package com.meteorite.itemdespawntowhat.config.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.util.JsonOrderTypeAdapterFactory;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 负责单一配置类型列表的 JSON 编解码。
 */
public final class ConfigJsonCodec<T extends BaseConversionConfig> {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapterFactory(new JsonOrderTypeAdapterFactory())
            .create();

    private final Type listType;

    public ConfigJsonCodec(Type listType) {
        this.listType = listType;
    }

    public String serialize(List<? extends BaseConversionConfig> configs) {
        return GSON.toJson(configs);
    }

    public List<T> deserialize(String json) {
        try {
            return deserializeStrict(json);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    public List<T> deserializeStrict(String json) {
        if (json == null) {
            throw new JsonParseException("Config JSON cannot be null");
        }

        List<T> entries = GSON.fromJson(json, listType);
        if (entries == null) {
            throw new JsonParseException("Config JSON must be an array");
        }
        return entries;
    }
}
