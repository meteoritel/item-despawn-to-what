package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import org.jetbrains.annotations.Nullable;

/**
 * 消耗类效果在草稿中的定位（主计划 §4 输入与成本、§5 现行结构与 JSON 编辑）。
 * <p>规则级固定成本（source_cost / catalyst_cost）与同名 consume 效果是两种表达方式：
 * 存在 consume 效果时成本区必须编辑那一条原效果、只更新原路径，并且禁止同时生成规则级固定成本字段。
 * <p>本类只做只读定位，绝不修改数据。
 */
public final class RuleCostBinding {

    // 消耗来源物品的效果 id
    public static final String CONSUME_SOURCE = "itemdespawntowhat:consume_source";
    // 消耗催化剂的效果 id
    public static final String CONSUME_CATALYST = "itemdespawntowhat:consume_catalyst";

    /**
     * 命中的消耗效果位置。
     *
     * @param listPath       效果数组路径（{@code effects} 或 {@code outcomes[i].effects}）
     * @param index          在数组中的下标
     * @param path           效果对象路径（{@code listPath[index]}）
     * @param candidateId    所属候选的稳定 id；顶层 effects 为 null
     * @param candidateIndex 所属候选下标；顶层 effects 为 -1
     */
    public record Ref(String listPath, int index, String path, @Nullable String candidateId, int candidateIndex) {

    }

    // 工具类
    private RuleCostBinding() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 定位第一个指定类型的效果：先顶层 effects，再按顺序扫描各候选
    public static @Nullable Ref find(@Nullable JsonObject rule, String effectId) {
        if (rule == null) {
            return null;
        }
        JsonArray flat = arrayOf(rule, RuleFields.EFFECTS);
        if (flat != null) {
            int index = indexOf(flat, effectId);
            if (index >= 0) {
                String path = RuleFields.EFFECTS + "[" + index + "]";
                return new Ref(RuleFields.EFFECTS, index, path, null, -1);
            }
        }
        JsonArray outcomes = arrayOf(rule, RuleFields.OUTCOMES);
        if (outcomes == null) {
            return null;
        }
        for (int candidate = 0; candidate < outcomes.size(); candidate++) {
            JsonElement element = outcomes.get(candidate);
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject body = element.getAsJsonObject();
            JsonArray effects = arrayOf(body, RuleFields.CANDIDATE_EFFECTS);
            if (effects == null) {
                continue;
            }
            int index = indexOf(effects, effectId);
            if (index >= 0) {
                String listPath = RuleFields.OUTCOMES + "[" + candidate + "]." + RuleFields.CANDIDATE_EFFECTS;
                return new Ref(listPath, index, listPath + "[" + index + "]", stringOf(body), candidate);
            }
        }
        return null;
    }

    // 定位消耗来源物品效果
    public static @Nullable Ref consumeSource(@Nullable JsonObject rule) {
        return find(rule, CONSUME_SOURCE);
    }

    // 定位消耗催化剂效果
    public static @Nullable Ref consumeCatalyst(@Nullable JsonObject rule) {
        return find(rule, CONSUME_CATALYST);
    }

    // 效果是否为指定类型（字符串比较，不做资源名解析）
    public static boolean isType(@Nullable JsonElement effect, String effectId) {
        String type = typeOf(effect);
        return type != null && type.equals(effectId);
    }

    // 读取效果类型字符串
    public static @Nullable String typeOf(@Nullable JsonElement effect) {
        if (effect == null || !effect.isJsonObject()) {
            return null;
        }
        JsonElement type = effect.getAsJsonObject().get(RuleFields.TYPE);
        if (type == null || !type.isJsonPrimitive() || !type.getAsJsonPrimitive().isString()) {
            return null;
        }
        return type.getAsString();
    }

    // 查找指定类型的下标
    private static int indexOf(JsonArray array, String effectId) {
        for (int i = 0; i < array.size(); i++) {
            if (isType(array.get(i), effectId)) {
                return i;
            }
        }
        return -1;
    }

    // 读取数组字段
    private static @Nullable JsonArray arrayOf(JsonObject owner, String name) {
        JsonElement element = owner.get(name);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    // 读取候选稳定 id（缺失或类型不符时返回 null）
    private static @Nullable String stringOf(JsonObject owner) {
        JsonElement element = owner.get(RuleFields.CANDIDATE_ID);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return null;
        }
        return element.getAsString();
    }
}
