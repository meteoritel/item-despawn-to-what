package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/***
 * 消耗语义摘要（forms.md §7）：判断当前效果列表属于隐式消耗、显式消耗、显式消耗来源，还是重复声明。
 * 只读草稿 JSON，不修改任何数据。
 */
public final class ConsumptionSummary {

    // 消耗语义类别
    public enum Kind {
        // 未声明任何消耗效果：隐式消耗 1 个来源物品
        IMPLICIT,
        // 声明了 consume_source：显式消耗来源物品
        EXPLICIT_SOURCE,
        // 声明了其他消耗效果：抑制隐式消耗
        EXPLICIT,
        // 同一消耗效果重复声明（非法）
        DUPLICATE
    }

    // 三类消耗效果的 id 路径
    private static final String CONSUME_SOURCE = "itemdespawntowhat:consume_source";
    private static final String CONSUME_CATALYST = "itemdespawntowhat:consume_catalyst";
    private static final String CONSUME_FLUID = "itemdespawntowhat:consume_fluid";

    // 工具类
    private ConsumptionSummary() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 判定消耗语义类别
    public static Kind kind(@Nullable JsonElement effects) {
        int source = 0;
        int catalyst = 0;
        int fluid = 0;
        if (effects != null && effects.isJsonArray()) {
            for (JsonElement element : effects.getAsJsonArray()) {
                String type = typeOf(element);
                if (CONSUME_SOURCE.equals(type)) {
                    source++;
                } else if (CONSUME_CATALYST.equals(type)) {
                    catalyst++;
                } else if (CONSUME_FLUID.equals(type)) {
                    fluid++;
                }
            }
        }
        if (source > 1 || catalyst > 1 || fluid > 1) {
            return Kind.DUPLICATE;
        }
        if (source == 0 && catalyst == 0 && fluid == 0) {
            return Kind.IMPLICIT;
        }
        return source > 0 ? Kind.EXPLICIT_SOURCE : Kind.EXPLICIT;
    }

    // 消耗语义提示文案 key（gui.itemdespawntowhat.edit.consumption.hint.*）
    public static Component hint(@Nullable JsonElement effects) {
        return switch (kind(effects)) {
            case IMPLICIT -> Component.translatable("gui.itemdespawntowhat.edit.consumption.hint.implicit");
            case EXPLICIT_SOURCE -> Component.translatable("gui.itemdespawntowhat.edit.consumption.hint.explicit_source");
            case EXPLICIT -> Component.translatable("gui.itemdespawntowhat.edit.consumption.hint.explicit");
            case DUPLICATE -> Component.translatable("gui.itemdespawntowhat.edit.consumption.hint.duplicate");
        };
    }

    // 固定源成本优先；重复消耗只在各效果列表内部判断，不把不同候选混为重复。
    public static Component hintRule(JsonObject rule) {
        JsonElement cost = rule.get(RuleFields.SOURCE_COST);
        if (cost != null && cost.isJsonObject()) {
            return Component.translatable("gui.itemdespawntowhat.edit.consumption.hint.fixed");
        }
        JsonArray all = new JsonArray();
        JsonElement flat = rule.get(RuleFields.EFFECTS);
        if (flat != null && flat.isJsonArray()) {
            all.addAll(flat.getAsJsonArray());
            if (isDuplicate(flat)) {
                return hint(flat);
            }
        }
        JsonElement outcomes = rule.get(RuleFields.OUTCOMES);
        if (outcomes != null && outcomes.isJsonArray()) {
            for (JsonElement candidate : outcomes.getAsJsonArray()) {
                JsonElement effects = candidate.isJsonObject() ? candidate.getAsJsonObject().get(RuleFields.CANDIDATE_EFFECTS) : null;
                if (effects != null && effects.isJsonArray()) {
                    if (isDuplicate(effects)) {
                        return hint(effects);
                    }
                    all.addAll(effects.getAsJsonArray());
                }
            }
        }
        boolean source = false;
        boolean explicit = false;
        for (JsonElement effect : all) {
            String type = typeOf(effect);
            source |= CONSUME_SOURCE.equals(type);
            explicit |= CONSUME_SOURCE.equals(type) || CONSUME_CATALYST.equals(type) || CONSUME_FLUID.equals(type);
        }
        String key = source ? "explicit_source" : explicit ? "explicit" : "implicit";
        return Component.translatable("gui.itemdespawntowhat.edit.consumption.hint." + key);
    }

    // 是否重复声明（保存前拦截用）
    public static boolean isDuplicate(@Nullable JsonElement effects) {
        return kind(effects) == Kind.DUPLICATE;
    }

    // 读取效果类型
    private static @Nullable String typeOf(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        return object.has("type") && object.get("type").isJsonPrimitive() ? object.get("type").getAsString() : null;
    }

    // 统计消耗效果条数
    public static int consumeEffectCount(@Nullable JsonArray effects) {
        int count = 0;
        if (effects != null) {
            for (JsonElement element : effects) {
                String type = typeOf(element);
                if (CONSUME_SOURCE.equals(type) || CONSUME_CATALYST.equals(type) || CONSUME_FLUID.equals(type)) {
                    count++;
                }
            }
        }
        return count;
    }
}
