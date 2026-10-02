package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EffectEditorRegistry;
import com.meteorite.itemdespawntowhat.client.edit.ConditionEditorRegistry;
import com.meteorite.itemdespawntowhat.client.edit.JsonSummary;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/***
 * 自然语言摘要：把规则/条件/效果 JSON 渲染成玩家可读的一行中文（forms.md §10）。
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
    public static Component rule(JsonObject rule) {
        String id = rule.has(RuleFields.ID) && rule.get(RuleFields.ID).isJsonPrimitive()
                ? rule.get(RuleFields.ID).getAsString() : "?";
        String name = rule.has(RuleFields.DISPLAY_NAME) && rule.get(RuleFields.DISPLAY_NAME).isJsonPrimitive()
                ? rule.get(RuleFields.DISPLAY_NAME).getAsString() : id;
        return Component.translatable(PREFIX + "rule", name,
                conditions(rule.get(RuleFields.CONDITIONS)), effects(rule.get(RuleFields.EFFECTS)));
    }

    // 条件树摘要
    public static Component conditions(@Nullable JsonElement raw) {
        if (raw == null || raw.isJsonNull() || !raw.isJsonObject()) {
            return Component.translatable(PREFIX + "any_condition");
        }
        return node(raw.getAsJsonObject());
    }

    // 效果列表摘要
    public static Component effects(@Nullable JsonElement raw) {
        if (raw == null || !raw.isJsonArray() || raw.getAsJsonArray().isEmpty()) {
            return Component.translatable(PREFIX + "no_effect");
        }
        List<Component> parts = new ArrayList<>();
        for (JsonElement element : raw.getAsJsonArray()) {
            parts.add(effect(element));
        }
        return join(parts);
    }

    // 单个效果摘要
    public static Component effect(@Nullable JsonElement raw) {
        if (raw == null || !raw.isJsonObject()) {
            return Component.literal("?");
        }
        JsonObject object = raw.getAsJsonObject();
        ResourceLocation type = typeOf(object);
        if (type == null) {
            return Component.translatable(PREFIX + "unknown_effect");
        }
        List<Component> parts = new ArrayList<>();
        if (object.has(RuleFields.CHANCE) && object.get(RuleFields.CHANCE).isJsonPrimitive()) {
            parts.add(Component.translatable(PREFIX + "chance", percentText(object.get(RuleFields.CHANCE))));
        }
        if (object.has(RuleFields.DELAY_TICKS) && object.get(RuleFields.DELAY_TICKS).isJsonPrimitive()) {
            parts.add(Component.translatable(PREFIX + "delay", object.get(RuleFields.DELAY_TICKS).getAsInt()));
        }
        Component label = TypeLabels.effectLabel(type);
        Component params = fields(object, type, "effect");
        return params == null ? Component.translatable(PREFIX + "effect", label, join(parts))
                : Component.translatable(PREFIX + "effect_with_params", label, params, join(parts));
    }

    // 递归渲染条件节点
    private static Component node(JsonObject node) {
        String op = node.has(RuleFields.OP) && node.get(RuleFields.OP).isJsonPrimitive()
                ? node.get(RuleFields.OP).getAsString() : "";
        if (RuleFields.OP_LEAF.equals(op)) {
            JsonObject condition = node.has(RuleFields.CONDITION) && node.get(RuleFields.CONDITION).isJsonObject()
                    ? node.getAsJsonObject(RuleFields.CONDITION) : new JsonObject();
            return leaf(condition);
        }
        if (RuleFields.OP_INVERTED.equals(op)) {
            JsonObject term = node.has(RuleFields.TERM) && node.get(RuleFields.TERM).isJsonObject()
                    ? node.getAsJsonObject(RuleFields.TERM) : null;
            return Component.translatable(PREFIX + "not", term == null
                    ? Component.translatable(PREFIX + "empty") : node(term));
        }
        if (RuleFields.OP_ALL_OF.equals(op) || RuleFields.OP_ANY_OF.equals(op)) {
            List<Component> parts = new ArrayList<>();
            JsonElement terms = node.get(RuleFields.TERMS);
            if (terms != null && terms.isJsonArray()) {
                for (JsonElement element : terms.getAsJsonArray()) {
                    if (element.isJsonObject()) {
                        parts.add(node(element.getAsJsonObject()));
                    }
                }
            }
            if (parts.isEmpty()) {
                return Component.translatable(PREFIX + "empty");
            }
            return Component.translatable(PREFIX + (RuleFields.OP_ANY_OF.equals(op) ? "any" : "all"), join(parts));
        }
        return Component.literal(op.isEmpty() ? "?" : op);
    }

    // 单个条件叶摘要
    private static Component leaf(JsonObject condition) {
        ResourceLocation type = typeOf(condition);
        if (type == null) {
            return Component.translatable(PREFIX + "unknown_condition");
        }
        Component label = TypeLabels.conditionLabel(type);
        Component params = fields(condition, type, "condition");
        return params == null ? label : Component.translatable(PREFIX + "leaf", label, params);
    }

    // 按描述符顺序渲染类型专属字段；无参条件返回 null
    private static @Nullable Component fields(JsonObject object, ResourceLocation type, String kind) {
        boolean condition = "condition".equals(kind);
        var descriptor = condition ? ConditionEditorRegistry.descriptorFor(type) : EffectEditorRegistry.descriptorFor(type);
        List<Component> parts = new ArrayList<>();
        if (descriptor != null && !descriptor.readOnly()) {
            for (EditorField field : descriptor.fields()) {
                if (RuleFields.TYPE.equals(field.name()) || !object.has(field.name())) {
                    continue;
                }
                parts.add(Component.translatable(PREFIX + "field",
                        Component.translatable(field.labelKey()), value(object.get(field.name()))));
            }
        }
        // 描述符未覆盖的字段（第三方类型）按原始键名展示，保证信息不丢
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (RuleFields.TYPE.equals(entry.getKey())) {
                continue;
            }
            boolean known = descriptor != null && descriptor.hasField(entry.getKey());
            if (!known) {
                parts.add(Component.translatable(PREFIX + "field",
                        Component.literal(entry.getKey()), value(entry.getValue())));
            }
        }
        return parts.isEmpty() ? null : join(parts);
    }

    // 取值文本
    private static Component value(@Nullable JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return Component.translatable(PREFIX + "value_empty");
        }
        if (value.isJsonArray()) {
            List<Component> parts = new ArrayList<>();
            for (JsonElement element : value.getAsJsonArray()) {
                parts.add(value(element));
            }
            return parts.isEmpty() ? Component.translatable(PREFIX + "value_empty") : join(parts);
        }
        if (value.isJsonObject()) {
            return Component.literal(JsonSummary.compact(value, 32));
        }
        return Component.literal(value.getAsString());
    }

    // 读 type 字段
    private static @Nullable ResourceLocation typeOf(JsonObject object) {
        if (!object.has(RuleFields.TYPE) || !object.get(RuleFields.TYPE).isJsonPrimitive()) {
            return null;
        }
        return ResourceLocation.tryParse(object.get(RuleFields.TYPE).getAsString());
    }

    // 概率显示为百分比（JSON 存 0..1）
    private static String percentText(JsonElement chance) {
        try {
            double value = chance.getAsDouble();
            return String.format(java.util.Locale.ROOT, "%.1f%%", value * 100.0D);
        } catch (RuntimeException exception) {
            return chance.getAsString();
        }
    }

    // 用逗号连接
    private static Component join(List<Component> parts) {
        Component result = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                result = result.copy().append(Component.translatable(PREFIX + "separator"));
            }
            result = result.copy().append(parts.get(i));
        }
        return result;
    }
}
