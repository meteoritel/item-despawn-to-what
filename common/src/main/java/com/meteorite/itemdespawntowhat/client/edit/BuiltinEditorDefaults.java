package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

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

    // 新建规则的默认体（source.items 与 effects 留空，由校验提示补全）
    public static JsonObject ruleBody(String ruleId) {
        JsonObject body = new JsonObject();
        body.addProperty(RuleFields.ID, ruleId);
        body.addProperty(RuleFields.ENABLED, true);
        body.addProperty(RuleFields.PRIORITY, 0);
        JsonObject source = new JsonObject();
        source.add(RuleFields.SOURCE_ITEMS, new JsonArray());
        body.add(RuleFields.SOURCE, source);
        body.addProperty(RuleFields.TRIGGER_AFTER_SECONDS, 300);
        body.add(RuleFields.EFFECTS, new JsonArray());
        return body;
    }

    // 复制/另存为：深拷贝规则体并换 id
    public static JsonObject copyOf(String newRuleId, JsonObject source) {
        JsonObject body = source.deepCopy();
        body.addProperty(RuleFields.ID, newRuleId);
        return body;
    }

    // 新建效果的默认体
    public static JsonObject effectBody(ResourceLocation type) {
        JsonObject body = new JsonObject();
        body.addProperty(RuleFields.TYPE, type.toString());
        applyEffectDefaults(body, type.getPath());
        fillDescriptorDefaults(body, EffectEditorRegistry.descriptorFor(type), RuleFields.TYPE);
        return body;
    }

    // 新建条件叶的默认节点（含 op=leaf 外壳，可直接交给条件表达式编解码器）
    public static @Nullable JsonObject conditionLeafJson(ResourceLocation type) {
        JsonObject condition = new JsonObject();
        condition.addProperty(RuleFields.TYPE, type.toString());
        applyConditionDefaults(condition, type.getPath());
        fillDescriptorDefaults(condition, ConditionEditorRegistry.descriptorFor(type), RuleFields.TYPE);
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
            case "spawn_item" -> {
                body.addProperty("count", 1);
                body.addProperty("radius", 6);
            }
            case "spawn_entity" -> {
                body.addProperty("count", 1);
                body.addProperty("age", 0);
                body.addProperty("radius", 6);
            }
            case "place_block" -> {
                // block 与 use_source_block 至少要有一个：默认用「放置来源方块」保证新建即可用
                body.addProperty("use_source_block", true);
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
            case "consume_source" -> body.addProperty("count", 1);
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
    private static void fillDescriptorDefaults(JsonObject body, TypeEditorDescriptor descriptor, String typeField) {
        if (descriptor == null || descriptor.readOnly()) {
            return;
        }
        for (EditorField field : descriptor.fields()) {
            if (typeField.equals(field.name()) || body.has(field.name()) || !field.required()) {
                continue;
            }
            switch (field.type()) {
                case BOOLEAN -> body.addProperty(field.name(), false);
                case ENUM -> {
                    var values = field.enumValues();
                    if (!values.isEmpty()) {
                        body.addProperty(field.name(), values.get(0));
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
