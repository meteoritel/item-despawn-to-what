package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TypeDefinition;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.api.param.ParamList;
import com.meteorite.itemdespawntowhat.core.api.param.ParamSpec;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.type.effect.EntityProduct;
import com.meteorite.itemdespawntowhat.core.type.effect.PlaceBlockEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.SpawnEntityEffect;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 客户端已发现类型的新建初值与省略字段的展示默认值。
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

    /** 展示效果或规则省略字段的默认值；效果类型按完整 id 查询本地类型表。 */
    public static @Nullable RuleValue effectiveValue(RuleValue object, String field) {
        return effectiveValue(object, field, ClientTypeRegistries.effects());
    }

    /** 展示条件省略字段的声明默认值；条件与效果的 id 命名空间分别查询。 */
    public static @Nullable RuleValue effectiveConditionValue(RuleValue object, String field) {
        return effectiveValue(object, field, ClientTypeRegistries.conditions());
    }

    private static @Nullable RuleValue effectiveValue(RuleValue object, String field,
                                                       TypeRegistry<? extends TypeDefinition<?>> types) {
        if (object != null && object.has(RuleFields.TYPE)) {
            ResourceLocation type = ResourceLocation.tryParse(object.text(RuleFields.TYPE, ""));
            if (type != null) {
                TypeDefinition<?> definition = types.getOrNull(type);
                if (definition != null) {
                    ParamSpec<?> spec = definition.params().get(field);
                    if (spec == null && definition instanceof EffectType<?> && SpawnEntityEffect.ID.equals(type)) {
                        // variant 由产出分发决定，不属于外层参数声明；按当前子类展示默认值
                        String variant = object.text(VARIANT, EntityProduct.DEFAULT_VARIANT);
                        spec = EntityProduct.variantParams(variant).get(field);
                    }
                    if (spec != null) {
                        return toRuleValue(spec, spec.defaultValue());
                    }
                    return null;
                }
            }
        }
        return switch (field) {
            case RuleFields.ENABLED -> RuleValue.flag(true);
            case RuleFields.PRIORITY -> RuleValue.integer(0);
            case RuleFields.TRIGGER_AFTER_SECONDS -> RuleValue.integer(RuleCodecs.DEFAULT_TRIGGER_AFTER_SECONDS);
            default -> null;
        };
    }

    /** 新建本地已发现条件类型的默认节点；未知类型返回 null，已知类型按声明写入初值。 */
    public static @Nullable JsonObject conditionLeafJson(ResourceLocation type) {
        TypeDefinition<?> definition = ClientTypeRegistries.conditions().getOrNull(type);
        if (definition == null) {
            return null;
        }
        JsonObject condition = new JsonObject();
        condition.addProperty(RuleFields.TYPE, type.toString());
        writeInitials(condition, definition.params());
        JsonObject leaf = new JsonObject();
        leaf.addProperty(RuleFields.OP, RuleFields.OP_LEAF);
        leaf.add(RuleFields.CONDITION, condition);
        return leaf;
    }

    // 把声明里的新建初值按各自的 wire codec 写入 JSON；没有初值的字段保持省略，交给校验定位
    private static void writeInitials(JsonObject body, ParamList<?> params) {
        for (ParamList.ParamValue value : params.initialValues()) {
            JsonElement element = encode(value.spec(), value.value());
            if (element != null) {
                body.add(value.name(), element);
            }
        }
    }

    // 用声明自带的 codec 编码初值/默认值，避免编辑器维护第二套类型转换规则；编码失败返回 null（不写入）
    private static @Nullable JsonElement encode(ParamSpec<?> spec, @Nullable Object value) {
        if (value == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Codec<Object> codec = (Codec<Object>) spec.valueCodec();
        return codec.encodeStart(JsonOps.INSTANCE, value).result().orElse(null);
    }

    /** 将参数声明的缺省值投影为展示值，保持未编辑字段的省略语义。 */
    public static @Nullable RuleValue defaultValue(ParamSpec<?> spec) {
        return toRuleValue(spec, spec.defaultValue());
    }

    // 规则值出口：null 表示该字段没有可展示的默认值
    private static @Nullable RuleValue toRuleValue(ParamSpec<?> spec, @Nullable Object value) {
        JsonElement element = encode(spec, value);
        return element == null ? null : RuleValue.wrap(element);
    }
}
