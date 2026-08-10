package com.meteorite.itemdespawntowhat.client.ui.schema;

import com.meteorite.itemdespawntowhat.client.ui.form.ConfigFormContext;
import com.meteorite.itemdespawntowhat.client.ui.form.ConfigFormSection;
import com.meteorite.itemdespawntowhat.client.ui.support.FieldValidator;
import com.meteorite.itemdespawntowhat.client.ui.support.SuggestionProvider;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import net.minecraft.client.gui.components.EditBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * 将字段 schema 渲染为现有表单生命周期可消费的 FormSection。
 */
public final class SchemaConfigFormSection<T extends BaseConversionConfig> implements ConfigFormSection<T> {
    private final List<ConfigFieldSchema<T>> schemas;
    private final boolean showsResultId;
    private final FieldValidator resultValidator;
    private final SuggestionProvider resultSuggestion;
    private final Map<String, EditBox> inputs = new LinkedHashMap<>();
    private final Map<String, BooleanSupplier> visibility = new LinkedHashMap<>();
    private ConfigFormContext context;
    private boolean visibleStateInitialized;

    public SchemaConfigFormSection(List<ConfigFieldSchema<T>> schemas) {
        this(schemas, true, null, null);
    }

    public SchemaConfigFormSection(List<ConfigFieldSchema<T>> schemas, boolean showsResultId,
                                   FieldValidator resultValidator, SuggestionProvider resultSuggestion) {
        this.schemas = List.copyOf(schemas);
        this.showsResultId = showsResultId;
        this.resultValidator = resultValidator;
        this.resultSuggestion = resultSuggestion;
    }

    @Override
    public void initialize(ConfigFormContext context) {
        this.context = context;
        inputs.clear();
        visibility.clear();
        lastVisible.clear();
        visibleStateInitialized = false;
        for (ConfigFieldSchema<T> schema : schemas) {
            EditBox input = createInput(schema.type(), context);
            inputs.put(schema.key(), input);
            if (schema.validator() != null) {
                context.registerValidator(input, schema.validator());
            }
            if (schema.suggestionProvider() != null) {
                context.registerSuggestion(input, schema.suggestionProvider());
            }
            if (schema.visibilityFactory() != null) {
                visibility.put(schema.key(), schema.visibilityFactory().apply(this));
            }
        }
        refresh();
    }

    @Override
    public void writeTo(T config) {
        for (ConfigFieldSchema<T> schema : schemas) {
            EditBox input = inputs.get(schema.key());
            if (input != null) {
                schema.setter().accept(config, input.getValue());
            }
        }
    }

    @Override
    public void readFrom(T config) {
        for (ConfigFieldSchema<T> schema : schemas) {
            EditBox input = inputs.get(schema.key());
            if (input != null) {
                input.setValue(schema.getter().apply(config));
            }
        }
        refresh();
    }

    @Override
    public void clear() {
        inputs.values().forEach(input -> input.setValue(""));
        refresh();
    }

    @Override
    public boolean showsResultId() {
        return showsResultId;
    }

    @Override
    public void registerValidators(ConfigFormContext context) {
        if (resultValidator != null) {
            context.registerValidator(context.resultIdInput(), resultValidator);
        }
    }

    @Override
    public void registerSuggestions(ConfigFormContext context) {
        if (resultSuggestion != null) {
            context.registerSuggestion(context.resultIdInput(), resultSuggestion);
        }
    }

    @Override
    public void refresh() {
        if (context == null) {
            return;
        }
        boolean changed = !visibleStateInitialized;
        Map<String, Boolean> current = new LinkedHashMap<>();
        for (ConfigFieldSchema<T> schema : schemas) {
            BooleanSupplier condition = visibility.get(schema.key());
            boolean visible = condition == null || condition.getAsBoolean();
            current.put(schema.key(), visible);
            if (!visibleStateInitialized || visible != isVisible(schema.key())) {
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        visibleStateInitialized = true;
        context.rebuildConditional(() -> {
            for (ConfigFieldSchema<T> schema : schemas) {
                if (current.getOrDefault(schema.key(), true)) {
                    context.addConditional(schema.labelSuffix(), inputs.get(schema.key()));
                }
            }
        });
        lastVisible.putAll(current);
    }

    public String value(String key) {
        EditBox input = inputs.get(key);
        return input == null ? "" : input.getValue();
    }

    private final Map<String, Boolean> lastVisible = new LinkedHashMap<>();

    private boolean isVisible(String key) {
        return lastVisible.getOrDefault(key, true);
    }

    private static EditBox createInput(ConfigFieldType type, ConfigFormContext context) {
        return switch (type) {
            case TEXT -> context.textBox();
            case INTEGER -> context.numericBox();
            case POSITIVE_INTEGER -> context.positiveIntBox();
            case DECIMAL -> context.positiveDecimalBox();
        };
    }
}
