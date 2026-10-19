package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.type.effect.PlaceBlockEffect;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/***
 * 内置类型的新建初值与省略字段的展示默认值。
 * 必填的 id、标签与列表留空，由表单校验定位；新建初值不代表已保存规则的缺省语义，
 * {@link #effectiveValue(RuleValue, String)} 按后端语义展示省略字段。
 */
public final class BuiltinEditorDefaults {

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
        applyEffectDefaults(body, type.getPath());
        fillDescriptorDefaults(body, EffectEditorRegistry.descriptorFor(type));
        return RuleValue.wrap(body);
    }

    /** 仅用于展示省略字段的有效默认值（RuleValue 入口：界面层不再持有 JsonElement），不写入草稿。 */
    public static @Nullable RuleValue effectiveValue(RuleValue object, String field) {
        if (object != null && object.has(RuleFields.TYPE)) {
            ResourceLocation type = ResourceLocation.tryParse(object.text(RuleFields.TYPE, ""));
            if (type != null && TypeLabels.OWN_NAMESPACE.equals(type.getNamespace())) {
                if ("chance".equals(field)) return RuleValue.decimal(1.0D);
                if ("delay_ticks".equals(field)) return RuleValue.integer(0);
                // 放置上限省略表示不限制数量，新建时也不写初值。
                if (PlaceBlockEffect.ID.equals(type) && PlaceBlockEffect.LIMIT_FIELD.equals(field)) return null;
                RuleValue defaults = effectBody(type);
                if ("spawn_entity".equals(type.getPath())) {
                    if ("amount".equals(field)) return RuleValue.integer(1);
                    if ("age".equals(field)) return RuleValue.integer(0);
                }
                return defaults.child(field);
            }
        }
        return switch (field) {
            case "enabled" -> RuleValue.flag(true);
            case "priority" -> RuleValue.integer(0);
            case "trigger_after_seconds" -> RuleValue.integer(com.meteorite.itemdespawntowhat.core.model.RuleCodecs.DEFAULT_TRIGGER_AFTER_SECONDS);
            default -> null;
        };
    }

    /** 新建条件叶的默认节点（含 op=leaf 外壳，可直接交给条件表达式编解码器） */
    public static JsonObject conditionLeafJson(ResourceLocation type) {
        JsonObject condition = new JsonObject();
        condition.addProperty(RuleFields.TYPE, type.toString());
        applyConditionDefaults(condition, type.getPath());
        fillDescriptorDefaults(condition, ConditionEditorRegistry.descriptorFor(type));
        JsonObject leaf = new JsonObject();
        leaf.addProperty(RuleFields.OP, RuleFields.OP_LEAF);
        leaf.add(RuleFields.CONDITION, condition);
        return leaf;
    }

    // 条件类型的文档默认值
    private static void applyConditionDefaults(JsonObject body, String path) {
        switch (path) {
            case "weather" -> body.addProperty("weather", "clear");
            case "time_of_day" -> {
                body.addProperty("from", 0);
                body.addProperty("to", 23999);
            }
            case "y_level" -> {
                // 新增高度条件必须带明确约束，不创建两端留空的恒真叶。
                body.addProperty("min", BuiltinEditorDescriptors.Y_LEVEL_SLIDER_MIN);
                body.addProperty("max", BuiltinEditorDescriptors.Y_LEVEL_SLIDER_MAX);
            }
            case "light_level", "outdoor", "fluid_present", "surrounding_blocks" -> {
                // 无默认参数：以上类型要么无参，要么留空即为「不约束」/待用户选择
            }
            case "dimension" -> body.add("dimensions", new JsonArray());
            case "biome" -> {
                body.addProperty("mode", "exact");
                body.add("biomes", new JsonArray());
            }
            // 新叶不预写门槛：count 缺失表示「未填写」，由运行投影按消耗配置解析默认值
            case "catalyst_present" -> body.add("items", new JsonArray());
            default -> {
                // 第三方条件类型：只给 type，其余交给只读回退或用户填写
            }
        }
    }

    // 新建效果的界面初值。
    private static void applyEffectDefaults(JsonObject body, String path) {
        switch (path) {
            // 消耗源物品沿用固定成本默认值。
            case "spawn_entity" -> {
                body.addProperty("variant", "item");
                body.addProperty("count", 1);
            }
            case "place_block" -> {
                // block 与 use_source_block 至少要有一个：use_source_block 后端默认 false，保持缺省语义
                body.addProperty(PlaceBlockEffect.USE_SOURCE_BLOCK_FIELD, PlaceBlockEffect.DEFAULT_USE_SOURCE_BLOCK);
                body.addProperty("shape", "square");
                body.addProperty(PlaceBlockEffect.COUNT_FIELD, PlaceBlockEffect.DEFAULT_COUNT);
                body.addProperty(PlaceBlockEffect.RADIUS_FIELD, PlaceBlockEffect.DEFAULT_RADIUS);
                // 不写 limit：省略即不限制数量，写死上界会超出当前半径的滑块窗口而像一条硬上限

            }
            case "loot_table" -> body.addProperty("luck", 0.0F);
            case "lightning" -> body.addProperty("count", 1);
            case "arrow_rain" -> {
                body.addProperty("count", 16);
                body.addProperty("pickup", "disallowed");
                body.add("potion_effects", new JsonArray());
            }
            case "weather" -> {
                body.addProperty("mode", "rain");
                body.addProperty("duration_ticks", 6000);
                body.addProperty("thundering", false);
            }
            case "explosion" -> {
                body.addProperty("power", 3.0F);
                body.addProperty("fire", false);
                body.addProperty("visual_only", false);
            }
            default -> {
                // 第三方效果类型：只给 type
            }
        }
    }

    // 用描述符补齐仍然缺失的「必填且可推导」字段：枚举取首值、布尔取假、数值取下界
    private static void fillDescriptorDefaults(JsonObject body, TypeEditorDescriptor descriptor) {
        if (descriptor == null || descriptor.readOnly()) {
            return;
        }
        for (EditorField field : descriptor.fields()) {
            if (RuleFields.TYPE.equals(field.name()) || body.has(field.name()) || !field.required()) {
                continue;
            }
            switch (field.type()) {
                case BOOLEAN -> body.addProperty(field.name(), false);
                case ENUM -> {
                    var values = field.enumValues();
                    if (!values.isEmpty()) {
                        body.addProperty(field.name(), values.getFirst());
                    }
                }
                case INTEGER, TICKS, AMPLIFIER -> body.addProperty(field.name(), field.intMin(0));
                case DECIMAL, PERCENT -> body.addProperty(field.name(), field.doubleMin(0.0D));
                default -> {
                    // 文本/资源位置/列表类必填字段不猜值，交给本地校验定位
                }
            }
        }
    }
}
