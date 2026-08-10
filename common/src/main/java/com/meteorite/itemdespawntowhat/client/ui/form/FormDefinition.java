package com.meteorite.itemdespawntowhat.client.ui.form;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 保存一种配置表单的有序字段集合及当前可见状态。
 */
public final class FormDefinition<C> {
    private final List<FormField<C>> fields;
    private final Map<String, FormField<C>> fieldsByKey;
    private final Map<String, Boolean> visibleState = new LinkedHashMap<>();

    private FormDefinition(List<FormField<C>> fields) {
        this.fields = List.copyOf(fields);
        this.fieldsByKey = new LinkedHashMap<>();
        for (FormField<C> field : fields) {
            if (fieldsByKey.put(field.key(), field) != null) {
                throw new IllegalArgumentException("Duplicate form field key: " + field.key());
            }
            visibleState.put(field.key(), true);
        }
    }

    public static <C> Builder<C> builder() {
        return new Builder<>();
    }

    public List<FormField<C>> fields() {
        return fields;
    }

    public void readFrom(C config) {
        Objects.requireNonNull(config, "config");
        fields.forEach(field -> field.readFrom(config));
    }

    public void writeTo(C config) {
        Objects.requireNonNull(config, "config");
        fields.stream().filter(this::isVisible).forEach(field -> field.writeTo(config));
    }

    public void clear() {
        fields.forEach(FormField::clear);
    }

    @SuppressWarnings("unchecked")
    public <V> V value(String key) {
        FormField<C> field = fieldsByKey.get(key);
        if (field == null) {
            throw new IllegalArgumentException("Unknown form field: " + key);
        }
        return (V) field.input().value();
    }

    public boolean isVisible(FormField<C> field) {
        return visibleState.getOrDefault(field.key(), true);
    }

    void setVisible(FormField<C> field, boolean visible) {
        visibleState.put(field.key(), visible);
    }

    /**
     * 按显示顺序组装表单字段。
     */
    public static final class Builder<C> {
        private final List<FormField<C>> fields = new ArrayList<>();

        public Builder<C> add(FormField<C> field) {
            fields.add(Objects.requireNonNull(field, "field"));
            return this;
        }

        public Builder<C> addAll(Iterable<FormField<C>> newFields) {
            newFields.forEach(this::add);
            return this;
        }

        public FormDefinition<C> build() {
            return new FormDefinition<>(fields);
        }
    }
}
