package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import net.minecraft.resources.ResourceLocation;

/***
 * 内置类型的新建默认参数（对齐 docs/plan/plan-frontend-rewrite-forms.md §5/§6）。
 * 策略：只填入文档给出默认值的字段（枚举/布尔/数值）；必填的 id、标签与列表不凭空捏造，
 * 留空由表单的本地校验定位到具体字段，避免把猜测值写进覆盖层文件。
 */
public final class BuiltinEditorDefaults {

    // 工具类
    private BuiltinEditorDefaults() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 新建规则的默认体：与空白/模板共用 {@link EditorFactories} 的候选工厂
    // 只写稳定 id、空的源列表、固定源成本 1、当前结构版本与一个初始候选；
    // 其余字段（enabled/priority/triggers/combination 等）依赖后端默认，保持省略。
    public static JsonObject ruleBody(String ruleId) {
        return EditorFactories.blankRule(ruleId);
    }

    // 复制/另存为：深拷贝规则体并换 id；候选同时分配新身份
    public static JsonObject copyOf(String newRuleId, JsonObject source) {
        return EditorFactories.copyRule(newRuleId, source);
    }

    // 新建效果的默认体
    public static JsonObject effectBody(ResourceLocation type) {
        JsonObject body = new JsonObject();
        body.addProperty(RuleFields.TYPE, type.toString());
        applyEffectDefaults(body, type.getPath());
        fillDescriptorDefaults(body, EffectEditorRegistry.descriptorFor(type));
        return body;
    }

    // 新建条件叶的默认节点（含 op=leaf 外壳，可直接交给条件表达式编解码器）
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

    // 条件类型的文档默认值（forms.md §5）
    private static void applyConditionDefaults(JsonObject body, String path) {
        switch (path) {
            case "weather" -> body.addProperty("weather", "clear");
            case "time_of_day" -> {
                body.addProperty("from", 0);
                body.addProperty("to", 23999);
            }
            case "y_level", "light_level", "outdoor", "fluid_present", "surrounding_blocks" -> {
                // 无默认参数：以上类型要么无参，要么留空即为「不约束」/待用户选择
            }
            case "dimension" -> body.add("dimensions", new JsonArray());
            case "biome" -> {
                body.addProperty("mode", "exact");
                body.add("biomes", new JsonArray());
            }
            case "catalyst_present" -> {
                body.add("items", new JsonArray());
                body.addProperty("count", 1);
            }
            default -> {
                // 第三方条件类型：只给 type，其余交给只读回退或用户填写
            }
        }
    }

    // 效果类型的文档默认值（forms.md §6）
    private static void applyEffectDefaults(JsonObject body, String path) {
        switch (path) {
            // spawn_item 的 radius 可省略：运行期有 limit 时的有效默认不写入 JSON
            // （界面展示不等于用户已编辑）；consume_source 只有 count 一个默认值
            case "spawn_item", "consume_source" -> body.addProperty("count", 1);
            case "spawn_entity" -> {
                body.addProperty("count", 1);
                body.addProperty("age", 0);
            }
            case "place_block" -> {
                // block 与 use_source_block 至少要有一个：use_source_block 后端默认 false，保持缺省语义
                body.addProperty("use_source_block", false);
                body.addProperty("shape", "square");
                body.addProperty("count", 1);
                body.addProperty("radius", 6);
            }
            case "spawn_xp" -> {
                body.addProperty("amount", 1);
                body.addProperty("per_source_item", false);
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
            case "consume_catalyst" -> {
                body.add("items", new JsonArray());
                body.addProperty("count", 1);
                body.addProperty("radius", 1);
            }
            case "consume_fluid" -> body.addProperty("require_source", true);
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
