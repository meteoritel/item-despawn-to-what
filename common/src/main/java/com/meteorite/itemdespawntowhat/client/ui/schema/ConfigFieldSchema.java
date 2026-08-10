package com.meteorite.itemdespawntowhat.client.ui.schema;

import com.meteorite.itemdespawntowhat.client.ui.support.FieldValidator;
import com.meteorite.itemdespawntowhat.client.ui.support.SuggestionProvider;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * 一个配置字段的声明式描述。
 */
public record ConfigFieldSchema<T extends BaseConversionConfig>(
        String key,
        String labelSuffix,
        ConfigFieldType type,
        Function<T, String> getter,
        BiConsumer<T, String> setter,
        @Nullable FieldValidator validator,
        @Nullable SuggestionProvider suggestionProvider,
        @Nullable Function<SchemaConfigFormSection<T>, BooleanSupplier> visibilityFactory
) {
    public ConfigFieldSchema {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(labelSuffix, "labelSuffix");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(getter, "getter");
        Objects.requireNonNull(setter, "setter");
    }

    public static <T extends BaseConversionConfig> Builder<T> builder(
            String key, String labelSuffix, ConfigFieldType type,
            Function<T, String> getter, BiConsumer<T, String> setter) {
        return new Builder<>(key, labelSuffix, type, getter, setter);
    }

    /** 字段构造器。 */
    public static final class Builder<T extends BaseConversionConfig> {
        private final String key;
        private final String labelSuffix;
        private final ConfigFieldType type;
        private final Function<T, String> getter;
        private final BiConsumer<T, String> setter;
        private FieldValidator validator;
        private SuggestionProvider suggestionProvider;
        private Function<SchemaConfigFormSection<T>, BooleanSupplier> visibilityFactory;

        private Builder(String key, String labelSuffix, ConfigFieldType type,
                        Function<T, String> getter, BiConsumer<T, String> setter) {
            this.key = key;
            this.labelSuffix = labelSuffix;
            this.type = type;
            this.getter = getter;
            this.setter = setter;
        }

        public Builder<T> validateWith(FieldValidator validator) {
            this.validator = validator;
            return this;
        }

        public Builder<T> suggestWith(SuggestionProvider suggestionProvider) {
            this.suggestionProvider = suggestionProvider;
            return this;
        }

        public Builder<T> visibleWhen(Function<SchemaConfigFormSection<T>, BooleanSupplier> visibilityFactory) {
            this.visibilityFactory = visibilityFactory;
            return this;
        }

        public ConfigFieldSchema<T> build() {
            return new ConfigFieldSchema<>(key, labelSuffix, type, getter, setter,
                    validator, suggestionProvider, visibilityFactory);
        }
    }
}
