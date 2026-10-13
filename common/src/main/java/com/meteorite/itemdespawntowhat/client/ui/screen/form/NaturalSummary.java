package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EffectEditorRegistry;
import com.meteorite.itemdespawntowhat.client.edit.FieldNumbers;
import com.meteorite.itemdespawntowhat.client.edit.ConditionEditorRegistry;
import com.meteorite.itemdespawntowhat.client.edit.JsonSummary;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.client.edit.RuleNaming;
import com.meteorite.itemdespawntowhat.client.ui.shared.BuiltinConditionSummaries;
import com.meteorite.itemdespawntowhat.client.ui.shared.RuleDisplayLabels;
import com.meteorite.itemdespawntowhat.client.ui.shared.SummaryFormats;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/***
 * 自然语言摘要：把规则/条件/效果 JSON 渲染成玩家可读的一行中文。
 * 纯只读：不修改草稿，也不依赖后端 record 类型，第三方类型按原始字段名展示。
 */
public final class NaturalSummary {

    // 摘要前缀 key
    private static final String PREFIX = "gui.itemdespawntowhat.edit.summary.";

    // 工具类
    private NaturalSummary() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 规则级摘要：显示名（或 id）：条件 → 效果
    public static Component rule(RuleValue rule) {
        return Component.translatable(PREFIX + "rule", RuleNaming.ruleTitle(rule, RuleDisplayLabels::label),
                conditions(rule.child(RuleFields.CONDITIONS)), results(rule));
    }

    // 候选结果保留边界，不能把每个候选误报成“没有效果”。
    private static Component results(RuleValue rule) {
        RuleValue outcomes = rule.child(RuleFields.OUTCOMES);
        if (outcomes == null || !outcomes.isList() || outcomes.items().isEmpty()) {
            return effects(rule.child(RuleFields.EFFECTS));
        }
        List<Component> candidates = new ArrayList<>();
        int ordinal = 1;
        for (RuleValue candidate : outcomes.items()) {
            RuleValue body = candidate.isMapping() ? candidate.child(RuleFields.CANDIDATE_EFFECTS) : null;
            candidates.add(Component.translatable(PREFIX + "candidate", ordinal++, effects(body)));
        }
        return SummaryFormats.join(candidates);
    }

    // 条件树摘要
    public static Component conditions(@Nullable RuleValue raw) {
        if (raw == null || raw.isNull() || !raw.isMapping()) {
            return Component.translatable(PREFIX + "any_condition");
        }
        return node(raw);
    }

    // 效果列表摘要
    public static Component effects(@Nullable RuleValue raw) {
        if (raw == null || !raw.isList() || raw.items().isEmpty()) {
            return Component.translatable(PREFIX + "no_effect");
        }
        List<Component> parts = new ArrayList<>();
        for (RuleValue element : raw.items()) {
            parts.add(effect(element));
        }
        return SummaryFormats.join(parts);
    }

    // 单个效果摘要
    public static Component effect(@Nullable RuleValue raw) {
        if (raw == null || !raw.isMapping()) {
            return Component.literal("?");
        }
        ResourceLocation type = typeOf(raw);
        if (type == null) {
            return Component.translatable(PREFIX + "unknown_effect");
        }
        List<Component> parts = new ArrayList<>();
        RuleValue chance = raw.child(RuleFields.CHANCE);
        if (chance != null && chance.isScalar()) {
            parts.add(Component.translatable(PREFIX + "chance", percentText(chance)));
        }
        RuleValue delay = raw.child(RuleFields.DELAY_TICKS);
        if (delay != null && delay.isScalar()) {
            parts.add(Component.translatable(PREFIX + "delay", intValue(delay)));
        }
        Component label = TypeLabels.effectLabel(type);
        Component params = fields(raw, type, "effect");
        return params == null ? Component.translatable(PREFIX + "effect", label, SummaryFormats.join(parts))
                : Component.translatable(PREFIX + "effect_with_params", label, params, SummaryFormats.join(parts));
    }

    // 递归渲染条件节点
    private static Component node(RuleValue node) {
        RuleValue opValue = node.child(RuleFields.OP);
        String op = opValue != null && opValue.isScalar() ? opValue.asText() : "";
        if (RuleFields.OP_LEAF.equals(op)) {
            RuleValue condition = node.child(RuleFields.CONDITION);
            return conditionLeaf(condition != null && condition.isMapping() ? condition : RuleValue.mapping().build());
        }
        if (RuleFields.OP_INVERTED.equals(op)) {
            RuleValue term = node.child(RuleFields.TERM);
            return Component.translatable(PREFIX + "not", term == null || !term.isMapping()
                    ? Component.translatable(PREFIX + "empty") : node(term));
        }
        if (RuleFields.OP_ALL_OF.equals(op) || RuleFields.OP_ANY_OF.equals(op)) {
            List<Component> parts = new ArrayList<>();
            RuleValue terms = node.child(RuleFields.TERMS);
            if (terms != null && terms.isList()) {
                for (RuleValue element : terms.items()) {
                    if (element.isMapping()) {
                        parts.add(node(element));
                    }
                }
            }
            if (parts.isEmpty()) {
                return Component.translatable(PREFIX + "empty");
            }
            return Component.translatable(PREFIX + (RuleFields.OP_ANY_OF.equals(op) ? "any" : "all"), SummaryFormats.join(parts));
        }
        return Component.literal(op.isEmpty() ? "?" : op);
    }

    // 单个条件叶摘要
    public static Component conditionLeaf(RuleValue condition) {
        ResourceLocation type = typeOf(condition);
        if (type == null) {
            return Component.translatable(PREFIX + "unknown_condition");
        }
        // 内建类型改用玩家可读的短摘要；第三方/未注册类型仍按字段名兜底
        Component builtin = BuiltinConditionSummaries.summary(condition, type);
        if (builtin != null) {
            return builtin;
        }
        return conditionTreeLeaf(condition);
    }

    /**
     * 条件树摘要里的叶标签：树摘要没有卡片标题行，因此把条件名一并带上。
     *
     * @param condition 条件叶 JSON
     * @return 条件名，或「条件名（参数摘要）」；没有可展示参数时只返回条件名
     */
    public static Component conditionTreeLeaf(RuleValue condition) {
        ResourceLocation type = typeOf(condition);
        if (type == null) {
            return Component.translatable(PREFIX + "unknown_condition");
        }
        Component label = TypeLabels.conditionLabel(type);
        Component params = conditionParams(condition);
        return params == null ? label : Component.translatable(PREFIX + "leaf", label, params);
    }

    /**
     * 条件叶的参数摘要：内建类型给玩家可读的短摘要，第三方/未注册类型按字段名兜底。
     *
     * @param condition 条件叶 JSON
     * @return 参数摘要；类型未知或没有可展示参数时返回 null
     */
    public static @Nullable Component conditionParams(RuleValue condition) {
        ResourceLocation type = typeOf(condition);
        if (type == null) {
            return null;
        }
        Component builtin = BuiltinConditionSummaries.summary(condition, type);
        return builtin != null ? builtin : fields(condition, type, "condition");
    }

    // 按描述符顺序渲染类型专属字段；无参条件返回 null
    private static @Nullable Component fields(RuleValue object, ResourceLocation type, String kind) {
        boolean condition = "condition".equals(kind);
        var descriptor = condition ? ConditionEditorRegistry.descriptorFor(type) : EffectEditorRegistry.descriptorFor(type);
        RuleValue variant = object.child("variant");
        if (!condition && type.equals(ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, "spawn_entity"))
                && variant != null && variant.isScalar()) {
            descriptor = com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDescriptors.entityDescriptor(variant.asText());
        }
        List<Component> parts = new ArrayList<>();
        if (!descriptor.readOnly()) {
            for (EditorField field : descriptor.fields()) {
                RuleValue fieldValue = object.child(field.name());
                if (RuleFields.TYPE.equals(field.name()) || fieldValue == null) {
                    continue;
                }
                parts.add(Component.translatable(PREFIX + "field",
                        Component.translatable(field.labelKey()), value(fieldValue)));
            }
        }
        // 描述符未覆盖的字段（第三方类型）按原始键名展示，保证信息不丢
        for (Map.Entry<String, RuleValue> entry : object.entries().entrySet()) {
            if (RuleFields.TYPE.equals(entry.getKey()) || "variant".equals(entry.getKey())) {
                continue;
            }
            boolean known = descriptor.hasField(entry.getKey());
            if (!known) {
                parts.add(Component.translatable(PREFIX + "field",
                        Component.literal(entry.getKey()), value(entry.getValue())));
            }
        }
        return parts.isEmpty() ? null : SummaryFormats.join(parts);
    }

    // 取值文本
    private static Component value(@Nullable RuleValue value) {
        if (value == null || value.isNull()) {
            return Component.translatable(PREFIX + "value_empty");
        }
        if (value.isList()) {
            List<Component> parts = new ArrayList<>();
            for (RuleValue element : value.items()) {
                parts.add(value(element));
            }
            return parts.isEmpty() ? Component.translatable(PREFIX + "value_empty") : SummaryFormats.join(parts);
        }
        if (value.isMapping()) {
            return Component.literal(JsonSummary.compact(value, 32));
        }
        return Component.literal(value.asText());
    }

    // 读 type 字段
    private static @Nullable ResourceLocation typeOf(RuleValue object) {
        RuleValue type = object.child(RuleFields.TYPE);
        if (type == null || !type.isScalar()) {
            return null;
        }
        return ResourceLocation.tryParse(type.asText());
    }

    // 概率显示为百分比（JSON 存 0..1）：与字段显示共用同一纯函数，不做 %.1f 舍入
    private static String percentText(RuleValue chance) {
        try {
            return FieldNumbers.percentText(Double.parseDouble(chance.asText().trim()));
        } catch (RuntimeException exception) {
            return chance.asText();
        }
    }

    // 读取整数标量：数字与字符串数字都按原语义解析
    private static int intValue(RuleValue value) {
        try {
            return Integer.parseInt(value.asText().trim());
        } catch (RuntimeException exception) {
            return value.asInteger(0);
        }
    }

}
