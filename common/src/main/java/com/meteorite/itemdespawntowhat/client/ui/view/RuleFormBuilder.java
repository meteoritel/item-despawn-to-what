package com.meteorite.itemdespawntowhat.client.ui.view;

import com.meteorite.itemdespawntowhat.client.ui.form.FormDefinition;
import com.meteorite.itemdespawntowhat.client.ui.form.FormField;
import com.meteorite.itemdespawntowhat.client.ui.form.FormFieldContext;
import com.meteorite.itemdespawntowhat.client.ui.form.FormFieldInputs;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 单效果编辑表单的构造器：规则级通用字段 + 效果通用字段 + 按声明式规格生成的效果参数字段。
 * 新增效果类型只需在 EffectParams 登记规格，本类无需改动。
 */
public final class RuleFormBuilder {

    private static final String EDIT = "gui.itemdespawntowhat.edit.";
    private static final String RULE = "gui.itemdespawntowhat.rule.";

    private RuleFormBuilder() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 构造表单定义；条件输入控件单独收集，供界面注入「打开子屏前回写草稿」的钩子
    public static RuleForm build(FormFieldContext context, @Nullable ResourceLocation effectType) {
        List<RuleForm.ConditionBinding> conditionBindings = new ArrayList<>();
        FormDefinition.Builder<RuleView> builder = FormDefinition.builder();

        builder.add(FormField.<RuleView, String>builder("rule_id", text(EDIT + "rule_id"),
                        FormFieldInputs.text(context.textBox()),
                        RuleView::id, (rule, value) -> rule.setId(value))
                .validateWith(value -> ResourceLocation.tryParse(value == null ? "" : value.trim()) == null
                        ? Component.translatable(RULE + "id_invalid") : null)
                .build());

        builder.add(FormField.<RuleView, String>builder("source_items", text(EDIT + "item_id"),
                        FormFieldInputs.text(context.textBox()),
                        rule -> rule.source().itemsText(),
                        (rule, value) -> rule.source().setItemsText(value))
                .validateWith(value -> SourceView.split(value).isEmpty()
                        ? Component.translatable(RULE + "source_required") : null)
                .build());

        builder.add(FormField.<RuleView, String>builder("source_exclude", text(EDIT + "source_exclude"),
                        FormFieldInputs.text(context.textBox()),
                        rule -> rule.source().excludeText(),
                        (rule, value) -> rule.source().setExcludeText(value))
                .build());

        builder.add(FormField.<RuleView, String>builder("trigger_after_seconds", text(EDIT + "conversion_time"),
                        FormFieldInputs.text(context.positiveIntegerBox()),
                        rule -> String.valueOf(rule.triggerAfterSeconds()),
                        (rule, value) -> rule.setTriggerAfterSeconds(EffectParams.parseInt(value, 300)))
                .build());

        builder.add(FormField.<RuleView, String>builder("priority", text(EDIT + "priority"),
                        FormFieldInputs.text(context.integerBox()),
                        rule -> String.valueOf(rule.priority()),
                        (rule, value) -> rule.setPriority(EffectParams.parseInt(value, 0)))
                .build());

        builder.add(FormField.<RuleView, Boolean>builder("enabled", text(EDIT + "enabled"),
                        FormFieldInputs.cycle(context.booleanButton("enabled"), Boolean.TRUE),
                        RuleView::enabled, (rule, value) -> rule.setEnabled(value))
                .build());

        builder.add(FormField.<RuleView, String>builder("notes", text(EDIT + "notes"),
                        FormFieldInputs.text(context.textBox()),
                        RuleView::notesText, (rule, value) -> rule.setNotes(value))
                .build());

        ConditionFieldInput ruleConditions = new ConditionFieldInput();
        conditionBindings.add(new RuleForm.ConditionBinding(ruleConditions, RuleView::setConditions));
        builder.add(FormField.<RuleView, ConditionView>builder("rule_conditions", text(EDIT + "conditions"),
                        ruleConditions, RuleView::conditions, RuleView::setConditions)
                .build());

        for (ParamSpec spec : EffectParams.specsOf(effectType)) {
            addParamField(builder, context, spec);
        }

        builder.add(FormField.<RuleView, String>builder("effect_delay_ticks", text(EDIT + "effect.delay_ticks"),
                        FormFieldInputs.text(context.integerBox()),
                        rule -> {
                            EffectView effect = rule.primaryEffect();
                            return effect == null ? "0" : String.valueOf(effect.delayTicks());
                        },
                        (rule, value) -> {
                            EffectView effect = rule.primaryEffect();
                            if (effect != null) {
                                effect.setDelayTicks(EffectParams.parseInt(value, 0));
                            }
                        })
                .build());

        builder.add(FormField.<RuleView, String>builder("effect_chance", text(EDIT + "effect.chance"),
                        FormFieldInputs.text(decimalBox(context)),
                        rule -> {
                            EffectView effect = rule.primaryEffect();
                            return effect == null ? "1.0" : String.valueOf(effect.chance());
                        },
                        (rule, value) -> {
                            EffectView effect = rule.primaryEffect();
                            if (effect != null) {
                                effect.setChance(EffectParams.parseDouble(value, 1.0D));
                            }
                        })
                .build());

        ConditionFieldInput effectConditions = new ConditionFieldInput();
        conditionBindings.add(new RuleForm.ConditionBinding(effectConditions, (rule, conditions) -> {
            EffectView effect = rule.primaryEffect();
            if (effect != null) {
                effect.setConditions(conditions);
            }
        }));
        builder.add(FormField.<RuleView, ConditionView>builder("effect_conditions", text(EDIT + "effect.conditions"),
                        effectConditions,
                        rule -> {
                            EffectView effect = rule.primaryEffect();
                            return effect == null ? ConditionView.empty() : effect.conditions();
                        },
                        (rule, value) -> {
                            EffectView effect = rule.primaryEffect();
                            if (effect != null) {
                                effect.setConditions(value);
                            }
                        })
                .build());

        return new RuleForm(builder.build(), conditionBindings);
    }

    // 按参数种类生成对应控件；取值一律经 EffectView 的通用字段读写
    private static void addParamField(FormDefinition.Builder<RuleView> builder,
                                      FormFieldContext context, ParamSpec spec) {
        Component label = text(spec.labelKey());
        switch (spec.kind()) {
            case TEXT -> builder.add(FormField.<RuleView, String>builder(spec.key(), label,
                            FormFieldInputs.text(context.textBox()),
                            rule -> readParamText(rule, spec.key()),
                            (rule, value) -> writeParamString(rule, spec.key(), value))
                    .validateWith(value -> requiredError(spec, value))
                    .build());
            case INTEGER -> builder.add(FormField.<RuleView, String>builder(spec.key(), label,
                            FormFieldInputs.text(integerBox(context)),
                            rule -> readParamText(rule, spec.key()),
                            (rule, value) -> writeParamInteger(rule, spec.key(), value))
                    .validateWith(value -> requiredError(spec, value))
                    .build());
            case DECIMAL -> builder.add(FormField.<RuleView, String>builder(spec.key(), label,
                            FormFieldInputs.text(decimalBox(context)),
                            rule -> readParamText(rule, spec.key()),
                            (rule, value) -> writeParamDecimal(rule, spec.key(), value))
                    .validateWith(value -> requiredError(spec, value))
                    .build());
            case BOOLEAN -> {
                CycleButton<Boolean> button = CycleButton
                        .booleanBuilder(text(EDIT + "on"), text(EDIT + "off"))
                        .withInitialValue(Boolean.parseBoolean(spec.defaultValue()))
                        .create(0, 0, FormFieldContext.BOX_WIDTH, FormFieldContext.BUTTON_HEIGHT, label);
                builder.add(FormField.<RuleView, Boolean>builder(spec.key(), label,
                                FormFieldInputs.cycle(button, Boolean.parseBoolean(spec.defaultValue())),
                                rule -> readParamBoolean(rule, spec.key(), spec.defaultValue()),
                                (rule, value) -> writeParamBoolean(rule, spec.key(), value))
                        .build());
            }
            case ENUM -> {
                CycleButton<String> button = CycleButton
                        .<String>builder(value -> text(spec.labelKey() + "." + value))
                        .withValues(spec.enumValues())
                        .withInitialValue(spec.defaultValue())
                        .create(0, 0, FormFieldContext.BOX_WIDTH, FormFieldContext.BUTTON_HEIGHT, label);
                builder.add(FormField.<RuleView, String>builder(spec.key(), label,
                                FormFieldInputs.cycle(button, spec.defaultValue()),
                                rule -> readParamText(rule, spec.key()),
                                (rule, value) -> writeParamString(rule, spec.key(), value))
                        .build());
            }
            default -> throw new IllegalStateException("效果参数暂不支持该控件种类: " + spec.kind());
        }
    }

    // 必填参数留空时的可读错误
    private static @Nullable Component requiredError(ParamSpec spec, String value) {
        if (!spec.required() || (value != null && !value.isBlank())) {
            return null;
        }
        return Component.translatable(RULE + "param_required", text(spec.labelKey()));
    }

    private static String readParamText(RuleView rule, String key) {
        EffectView effect = rule.primaryEffect();
        return effect == null ? "" : effect.paramText(key);
    }

    private static void writeParamString(RuleView rule, String key, String value) {
        EffectView effect = rule.primaryEffect();
        if (effect != null) {
            effect.setStringParam(key, value);
        }
    }

    private static void writeParamInteger(RuleView rule, String key, String value) {
        EffectView effect = rule.primaryEffect();
        if (effect == null) {
            return;
        }
        if (value == null || value.isBlank()) {
            effect.removeParam(key);
        } else {
            effect.setIntParam(key, EffectParams.parseInt(value, 0));
        }
    }

    private static void writeParamDecimal(RuleView rule, String key, String value) {
        EffectView effect = rule.primaryEffect();
        if (effect == null) {
            return;
        }
        if (value == null || value.isBlank()) {
            effect.removeParam(key);
        } else {
            effect.setDoubleParam(key, EffectParams.parseDouble(value, 0.0D));
        }
    }

    private static boolean readParamBoolean(RuleView rule, String key, String defaultValue) {
        EffectView effect = rule.primaryEffect();
        return effect != null && effect.booleanParam(key, Boolean.parseBoolean(defaultValue));
    }

    private static void writeParamBoolean(RuleView rule, String key, boolean value) {
        EffectView effect = rule.primaryEffect();
        if (effect != null) {
            effect.setBooleanParam(key, value);
        }
    }

    private static EditBox integerBox(FormFieldContext context) {
        EditBox box = context.textBox();
        box.setFilter(value -> value.matches("-?\\d*"));
        return box;
    }

    private static EditBox decimalBox(FormFieldContext context) {
        EditBox box = context.textBox();
        box.setFilter(value -> value.matches("-?\\d*\\.?\\d*"));
        return box;
    }

    private static Component text(String key) {
        return Component.translatable(key);
    }
}
