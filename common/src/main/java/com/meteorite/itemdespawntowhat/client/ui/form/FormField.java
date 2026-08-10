package com.meteorite.itemdespawntowhat.client.ui.form;

import com.meteorite.itemdespawntowhat.client.ui.support.SuggestionProvider;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 描述一个配置字段的控件、数据映射和交互规则。
 */
public final class FormField<C> {
    private final String key;
    private final Component label;
    private final FormFieldInput<?> input;
    private final java.util.function.Consumer<C> reader;
    private final java.util.function.Consumer<C> writer;
    private final java.util.function.Supplier<@Nullable Component> validator;
    private final Predicate<FormDefinition<C>> visibility;
    private final boolean clearWhenHidden;
    private final List<SuggestionBinding> suggestions;

    private <V> FormField(Builder<C, V> builder) {
        this.key = builder.key;
        this.label = builder.label;
        this.input = builder.input;
        this.reader = config -> builder.input.setValue(builder.getter.apply(config));
        this.writer = config -> builder.setter.accept(config, builder.input.value());
        this.validator = builder.validator == null
                ? () -> null
                : () -> builder.validator.apply(builder.input.value());
        this.visibility = builder.visibility;
        this.clearWhenHidden = builder.clearWhenHidden;
        this.suggestions = List.copyOf(builder.suggestions);
    }

    public static <C, V> Builder<C, V> builder(
            String key,
            Component label,
            FormFieldInput<V> input,
            Function<C, V> getter,
            BiConsumer<C, V> setter) {
        return new Builder<>(key, label, input, getter, setter);
    }

    public String key() {
        return key;
    }

    public Component label() {
        return label;
    }

    public FormFieldInput<?> input() {
        return input;
    }

    public void readFrom(C config) {
        reader.accept(config);
    }

    public void writeTo(C config) {
        writer.accept(config);
    }

    public void clear() {
        input.clear();
    }

    public @Nullable Component validate() {
        return validator.get();
    }

    public boolean isVisible(FormDefinition<C> definition) {
        return visibility.test(definition);
    }

    public boolean clearWhenHidden() {
        return clearWhenHidden;
    }

    List<SuggestionBinding> suggestions() {
        return suggestions;
    }

    /**
     * 分步构造字段的可选校验、建议和可见性行为。
     */
    public static final class Builder<C, V> {
        private final String key;
        private final Component label;
        private final FormFieldInput<V> input;
        private final Function<C, V> getter;
        private final BiConsumer<C, V> setter;
        private Function<V, @Nullable Component> validator;
        private Predicate<FormDefinition<C>> visibility = definition -> true;
        private boolean clearWhenHidden;
        private final List<SuggestionBinding> suggestions = new ArrayList<>();

        private Builder(String key, Component label, FormFieldInput<V> input,
                        Function<C, V> getter, BiConsumer<C, V> setter) {
            this.key = Objects.requireNonNull(key, "key");
            this.label = Objects.requireNonNull(label, "label");
            this.input = Objects.requireNonNull(input, "input");
            this.getter = Objects.requireNonNull(getter, "getter");
            this.setter = Objects.requireNonNull(setter, "setter");
        }

        public Builder<C, V> validateWith(Function<V, @Nullable Component> validator) {
            this.validator = Objects.requireNonNull(validator, "validator");
            return this;
        }

        public Builder<C, V> suggestWith(EditBox editBox, SuggestionProvider provider) {
            return suggestWith(editBox, provider, false);
        }

        public Builder<C, V> suggestWith(EditBox editBox, SuggestionProvider provider, boolean commaSeparated) {
            suggestions.add(new SuggestionBinding(editBox, provider, commaSeparated));
            return this;
        }

        public Builder<C, V> visibleWhen(Predicate<FormDefinition<C>> visibility) {
            this.visibility = Objects.requireNonNull(visibility, "visibility");
            return this;
        }

        public Builder<C, V> clearWhenHidden() {
            this.clearWhenHidden = true;
            return this;
        }

        public FormField<C> build() {
            return new FormField<>(this);
        }
    }

    record SuggestionBinding(EditBox editBox, SuggestionProvider provider, boolean commaSeparated) {
    }
}
