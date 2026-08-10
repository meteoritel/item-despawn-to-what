package com.meteorite.itemdespawntowhat.config.type;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.execution.ConversionExecutor;
import com.meteorite.itemdespawntowhat.config.io.ConfigJsonCodec;
import com.meteorite.itemdespawntowhat.config.io.JsonConfigRepository;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/**
 * 转换类型的 DTO、序列化和执行元数据。
 */
public final class ConversionTypeDefinition<T extends BaseConversionConfig> {
    private ConversionType type;
    private final ConfigJsonCodec<T> codec;
    private final Supplier<List<T>> defaultEntries;
    private final ConversionExecutor<? super T> executor;

    public ConversionTypeDefinition(Type listType, Supplier<List<T>> defaultEntries,
                                    ConversionExecutor<? super T> executor) {
        this.codec = new ConfigJsonCodec<>(listType);
        this.defaultEntries = defaultEntries;
        this.executor = executor;
    }

    void bindType(ConversionType type) {
        if (this.type != null && this.type != type) {
            throw new IllegalStateException("Conversion type already bound: " + this.type);
        }
        this.type = type;
    }

    public ConversionType type() {
        if (type == null) {
            throw new IllegalStateException("Definition is not registered");
        }
        return type;
    }

    public ConversionExecutor<? super T> executor() {
        return executor;
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
