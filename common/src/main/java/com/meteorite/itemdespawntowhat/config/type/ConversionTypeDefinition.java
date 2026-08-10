package com.meteorite.itemdespawntowhat.config.type;

import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.io.ConfigJsonCodec;
import com.meteorite.itemdespawntowhat.config.io.JsonConfigRepository;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/**
 * 集中描述一种配置的模型类型、JSON 类型和默认内容。
 */
public final class ConversionTypeDefinition<T extends BaseConversionConfig> {
    private final ConfigType type;
    private final ConfigJsonCodec<T> codec;
    private final Supplier<List<T>> defaultEntries;

    public ConversionTypeDefinition(ConfigType type, Type listType, Supplier<List<T>> defaultEntries) {
        this.type = type;
        this.codec = new ConfigJsonCodec<>(listType);
        this.defaultEntries = defaultEntries;
    }

    public ConfigType type() {
        return type;
    }

    public ConfigJsonCodec<T> codec() {
        return codec;
    }

    public List<T> createDefaultEntries() {
        return defaultEntries.get();
    }

    public JsonConfigRepository<T> repository(Path configDir) {
        return new JsonConfigRepository<>(configDir, this);
    }
}
