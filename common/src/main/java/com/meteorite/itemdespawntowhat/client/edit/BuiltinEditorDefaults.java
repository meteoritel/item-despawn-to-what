package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TypeDefinition;
import com.meteorite.itemdespawntowhat.core.api.param.ParamList;
import com.meteorite.itemdespawntowhat.core.api.param.ParamSpec;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.type.effect.EntityProduct;
import com.meteorite.itemdespawntowhat.core.type.effect.PlaceBlockEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.SpawnEntityEffect;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 内置类型的新建初值与省略字段的展示默认值。
 * <p>新建初值来自 core 侧参数声明的 initialValue，省略字段的展示默认值来自 defaultValue；
 * 两者都不代表已保存规则的缺省语义，只用于新建预填与界面提示。
 */
public final class BuiltinEditorDefaults {

    // 实体产出子类字段名（由产出分发器引入，不属于任何参数声明）
    private static final String VARIANT = "variant";

    // 工具类
    private BuiltinEditorDefaults() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 新建规则的默认体：使用 {@link EditorFactories} 的空白规则工厂
    // 只写稳定 id、空的源列表、固定源成本 1、当前结构版本与一个初始候选；
    /** 其余字段（enabled/priority/triggers/combination 等）依赖后端默认，保持省略。 */
    public static RuleValue ruleBody(String ruleId) {
        return RuleValue.wrap(EditorFactories.blankRule(ruleId));
    }

    /** 复制/另存为：深拷贝规则体并换 id；候选同时分配新身份 */
    public static RuleValue copyOf(String newRuleId, @Nullable RuleValue source) {
        return RuleValue.wrap(EditorFactories.copyRule(newRuleId, source == null ? null : source.unwrap().getAsJsonObject()));
    }

    /** 新建效果的默认体 */
    public static RuleValue effectBody(ResourceLocation type) {
        JsonObject body = new JsonObject();
        body.addProperty(RuleFields.TYPE, type.toString());
        TypeDefinition<?> definition = ClientTypeRegistries.effects().getOrNull(type);
        if (definition != null) {
            if (SpawnEntityEffect.ID.equals(type)) {
                body.addProperty(VARIANT, EntityProduct.DEFAULT_VARIANT);
                writeInitials(body, EntityProduct.variantParams(EntityProduct.DEFAULT_VARIANT));
            } else {
                writeInitials(body, definition.params());
            }
        }
        if (PlaceBlockEffect.ID.equals(type)) {
            // 上限初值取当前半径方框的容量：滑条停在最大值，半径变化时由界面联动改写
            body.addProperty(PlaceBlockEffect.LIMIT_FIELD,
                    PlaceBlockEffect.limitUpperBound(PlaceBlockEffect.DEFAULT_RADIUS));
        }
        return RuleValue.wrap(body);
    }

    /** 仅用于展示省略字段的有效默认值（RuleValue 入口：界面层不再持有 JsonElement），不写入草稿。 */
    public static @Nullable RuleValue effectiveValue(RuleValue object, String field) {
        if (object != null && object.has(RuleFields.TYPE)) {
            ResourceLocation type = ResourceLocation.tryParse(object.text(RuleFields.TYPE, ""));
            if (type != null && TypeLabels.OWN_NAMESPACE.equals(type.getNamespace())) {
                TypeDefinition<?> definition = ClientTypeRegistries.effects().getOrNull(type);
                if (definition != null) {
                    ParamSpec spec = definition.params().get(field);
                    if (spec == null && SpawnEntityEffect.ID.equals(type)) {
                        // variant 由产出分发决定，不属于外层参数声明；按当前子类展示默认值
                        String variant = object.text(VARIANT, EntityProduct.DEFAULT_VARIANT);
                        spec = EntityProduct.variantParams(variant).get(field);
                    }
                    if (spec != null) {
                        return toRuleValue(spec.defaultValue());
                    }
                    return null;
                }
            }
        }
        return switch (field) {
            case "enabled" -> RuleValue.flag(true);
            case "priority" -> RuleValue.integer(0);
            case "trigger_after_seconds" -> RuleValue.integer(RuleCodecs.DEFAULT_TRIGGER_AFTER_SECONDS);
            default -> null;
        };
    }

    /** 新建条件叶的默认节点（含 op=leaf 外壳，可直接交给条件表达式编解码器） */
    public static JsonObject conditionLeafJson(ResourceLocation type) {
        JsonObject condition = new JsonObject();
        condition.addProperty(RuleFields.TYPE, type.toString());
        TypeDefinition<?> definition = ClientTypeRegistries.conditions().getOrNull(type);
        if (definition != null) {
            writeInitials(condition, definition.params());
        }
        JsonObject leaf = new JsonObject();
        leaf.addProperty(RuleFields.OP, RuleFields.OP_LEAF);
        leaf.add(RuleFields.CONDITION, condition);
        return leaf;
    }

    // 把声明里的新建初值写入 JSON；没有初值的字段保持省略，交给校验定位
    private static void writeInitials(JsonObject body, ParamList params) {
        for (ParamList.ParamValue value : params.initialValues()) {
            JsonElement element = toJson(value.value());
            if (element != null) {
                body.add(value.name(), element);
            }
        }
    }

    // 声明里的初值/默认值转为 JSON；无法表达的形态返回 null（不写入）
    private static @Nullable JsonElement toJson(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case Boolean flag -> new JsonPrimitive(flag);
            case Integer number -> new JsonPrimitive(number);
            case Long number -> new JsonPrimitive(number);
            case Float number -> new JsonPrimitive(number);
            case Double number -> new JsonPrimitive(number);
            case Enum<?> constant -> new JsonPrimitive(constant.name().toLowerCase(java.util.Locale.ROOT));
            case String text -> new JsonPrimitive(text);
            case List<?> list -> {
                JsonArray array = new JsonArray();
                for (Object item : list) {
                    JsonElement element = toJson(item);
                    if (element != null) {
                        array.add(element);
                    }
                }
                yield array;
            }
            case Map<?, ?> map -> {
                JsonObject object = new JsonObject();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    JsonElement element = toJson(entry.getValue());
                    if (element != null) {
                        object.add(String.valueOf(entry.getKey()), element);
                    }
                }
                yield object;
            }
            default -> null;
        };
    }

    // JSON 元素转规则值；null 表示该字段没有可展示的默认值
    private static @Nullable RuleValue toRuleValue(@Nullable Object value) {
        JsonElement element = toJson(value);
        return element == null ? null : RuleValue.wrap(element);
    }
}
