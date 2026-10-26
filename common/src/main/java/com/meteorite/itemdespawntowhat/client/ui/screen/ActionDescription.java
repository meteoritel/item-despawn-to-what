package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDefaults;
import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDescriptors;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorRegistries;
import com.meteorite.itemdespawntowhat.client.edit.FieldNumbers;
import com.meteorite.itemdespawntowhat.client.edit.RuleNaming;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.shared.DescriptionLine;
import com.meteorite.itemdespawntowhat.client.ui.shared.DescriptionLine.Tone;
import com.meteorite.itemdespawntowhat.client.ui.shared.EntityPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.FieldCatalogTypes;
import com.meteorite.itemdespawntowhat.client.ui.shared.RuleDisplayLabels;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** 动作的玩家说明：按已注册字段的语义展示有效值，不枚举原始 JSON 键值。 */
final class ActionDescription {
    private static final String UI = RuleEditorPageSupport.UI;

    private ActionDescription() { }

    static List<DescriptionLine> lines(RuleValue action) {
        List<DescriptionLine> lines = new ArrayList<>();
        lines.add(DescriptionLine.title(RuleNaming.effectTitle(action, RuleDisplayLabels::label)));
        ResourceLocation type = ResourceLocation.tryParse(action.text(RuleFields.TYPE, ""));
        var descriptor = type == null ? null : EditorRegistries.EFFECTS.find(type);
        if (descriptor == null || descriptor.readOnly()) {
            lines.add(DescriptionLine.note(Component.translatable(UI + "results.description_unavailable"), 0));
            return lines;
        }
        if (TypeLabels.OWN_NAMESPACE.equals(type.getNamespace()) && "spawn_entity".equals(type.getPath())) {
            descriptor = BuiltinEditorDescriptors.entityDescriptor(action.text("variant", "item"));
        }
        for (EditorField field : descriptor.fields()) {
            // 条件不在动作说明里重复：条件树由触发页统一编辑与展示
            if (field.isConditionTree()) continue;
            RuleValue value = action.child(field.name());
            if (value == null) value = BuiltinEditorDefaults.effectiveValue(action, field.name());
            Component description = describe(field, value);
            if (description != null) {
                lines.add(DescriptionLine.field(Component.translatable(field.labelKey()), description,
                        toneOf(field), 0));
            }
        }
        if (action.has("entity") && EntityPreviewIcons.unavailable(action.text("entity", ""), action.integer("age", 0))) {
            lines.add(DescriptionLine.note(Component.translatable(UI + "preview.unavailable"), 0));
        }
        return lines;
    }

    // 引用型字段单独着色，让标签与数值在字段列里可被一眼区分；数值与说明保持各自色阶。
    private static Tone toneOf(EditorField field) {
        if (FieldCatalogTypes.catalogTypeOf(field) != null) {
            return Tone.REFERENCE;
        }
        if (field.isNote()) {
            return Tone.NOTE;
        }
        if (field.isNumeric() || field.isItemCounts() || field.isClimateRange()) {
            return Tone.METRIC;
        }
        return Tone.VALUE;
    }

    private static Component describe(EditorField field, RuleValue value) {
        // 缺省参数只展示已确认的有效默认值。
        if (value == null || value.isNull()) return null;
        if (field.isDisplayOnly()) {
            return null;
        }
        if (field.isBoolean()) {
            return Component.translatable(UI + "results.value." + (value.asFlag(false) ? "yes" : "no"));
        }
        if (field.isEnum()) {
            return Component.translatable(UI + "enum." + field.enumGroup() + "." + value.asText());
        }
        if (field.isPercent()) {
            return Component.literal(FieldNumbers.percentText(value.asDecimal(1.0D)));
        }
        if (field.isTicks()) {
            return value.asInteger(0) == 0 ? Component.translatable(UI + "results.immediate")
                    : Component.translatable(UI + "results.seconds", FieldNumbers.ticksToSecondsText(value.asInteger(0)));
        }
        if (field.isAmplifier()) {
            return Component.literal(Integer.toString(FieldNumbers.levelOf(value.asInteger(0))));
        }
        if (field.isItemCounts()) {
            return counts(value);
        }
        if (field.isSublist()) {
            return sublist(field, value);
        }
        if (field.isReference()) {
            return reference(field, value.asText());
        }
        if (field.isListShaped()) {
            return value.isList()
                    ? join(value.items().stream().map(item -> reference(field, item.asText())).toList()) : null;
        }
        return value.isScalar() ? Component.literal(value.asText()) : null;
    }

    private static Component counts(RuleValue value) {
        if (value.isScalar()) return Component.literal(value.asText());
        if (!value.isMapping()) return null;
        List<Component> parts = new ArrayList<>();
        for (var entry : value.entries().entrySet()) {
            parts.add(Component.translatable(UI + "results.quantity", itemLabel(entry.getKey()), entry.getValue().asText()));
        }
        return join(parts);
    }

    private static Component sublist(EditorField field, RuleValue value) {
        if (!value.isList()) return null;
        List<Component> parts = new ArrayList<>();
        for (RuleValue item : value.items()) {
            if (!item.isMapping()) continue;
            List<Component> fields = new ArrayList<>();
            for (EditorField child : field.subFields()) {
                Component text = describe(child, item.child(child.name()));
                if (text != null) fields.add(Component.translatable(UI + "results.parameter",
                        Component.translatable(child.labelKey()), text));
            }
            parts.add(join(fields));
        }
        return join(parts);
    }

    private static Component reference(EditorField field, String text) {
        ResourceLocation id = ResourceLocation.tryParse(text);
        RuleCatalogType catalog = FieldCatalogTypes.catalogTypeOf(field);
        Component label = id == null || catalog == null ? null : RuleDisplayLabels.catalogLabel(catalog, id);
        // 标签和未加载的资源保留注册名，不能把它们误认成同名的物品。
        return label == null ? Component.literal(text) : label;
    }

    private static Component itemLabel(String text) {
        ResourceLocation id = ResourceLocation.tryParse(text);
        Component label = id == null ? null : RuleDisplayLabels.catalogLabel(RuleCatalogType.ITEM, id);
        return label == null ? Component.literal(text) : label;
    }

    private static Component join(List<Component> parts) {
        if (parts.isEmpty()) return Component.translatable(UI + "results.value.none");
        var result = Component.empty();
        for (Component part : parts) {
            if (!result.getString().isEmpty()) result.append(Component.translatable(UI + "results.separator"));
            result.append(part);
        }
        return result;
    }
}
