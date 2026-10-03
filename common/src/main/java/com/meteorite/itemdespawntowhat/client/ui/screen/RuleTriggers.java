package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * 触发原因（triggers）的只读解析与写入（主计划 §4 触发与条件、fields §2）。
 * <p>JSON 是唯一事实源：字段省略或写成空数组时按「有效自然触发」展示；用户改动后写为显式数组，
 * 空数组由屏幕的本地校验以阻塞问题拦截（不允许提交全不选）。
 * <p>未识别的取值原样保留：编辑动作只重排已知取值，不静默丢弃第三方或未来版本的取值。
 */
public final class RuleTriggers {

    // 自然消失（缺省）
    public static final String NATURAL = "natural";
    // 被火烧毁
    public static final String FIRE = "fire";
    // 被岩浆销毁
    public static final String LAVA = "lava";
    // 被仙人掌销毁
    public static final String CACTUS = "cactus";

    // 已知取值与界面顺序
    private static final List<String> KNOWN = List.of(NATURAL, FIRE, LAVA, CACTUS);

    // 工具类
    private RuleTriggers() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 已知触发原因（界面顺序）
    public static List<String> known() {
        return KNOWN;
    }

    // 是否显式声明了 triggers 数组
    public static boolean isExplicit(@Nullable JsonObject rule) {
        if (rule == null) {
            return false;
        }
        JsonElement element = rule.get(RuleFields.TRIGGERS);
        return element != null && element.isJsonArray();
    }

    // 是否显式写成空数组（本地校验拦截）
    public static boolean isExplicitlyEmpty(@Nullable JsonObject rule) {
        return isExplicit(rule) && rule.getAsJsonArray(RuleFields.TRIGGERS).isEmpty();
    }

    /**
     * 有效展示集合：省略或空数组 → 仅自然触发；显式数组 → 去重后已知取值按固定顺序在前、未知取值按原顺序在后。
     */
    public static List<String> effective(@Nullable JsonObject rule) {
        if (!isExplicit(rule)) {
            return List.of(NATURAL);
        }
        JsonArray array = rule.getAsJsonArray(RuleFields.TRIGGERS);
        List<String> declared = new ArrayList<>();
        for (JsonElement element : array) {
            if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                continue;
            }
            String value = element.getAsString();
            if (!declared.contains(value)) {
                declared.add(value);
            }
        }
        if (declared.isEmpty()) {
            return List.of(NATURAL);
        }
        List<String> ordered = new ArrayList<>();
        for (String value : KNOWN) {
            if (declared.contains(value)) {
                ordered.add(value);
            }
        }
        for (String value : declared) {
            if (!KNOWN.contains(value)) {
                ordered.add(value);
            }
        }
        return ordered;
    }

    // 是否允许 trigger_after_seconds：仅自然触发需要延时
    public static boolean allowsDelay(@Nullable JsonObject rule) {
        return effective(rule).contains(NATURAL);
    }

    // 生成显式数组：已知取值按固定顺序，未知取值按原顺序追加在后
    public static JsonArray toArray(List<String> values) {
        List<String> ordered = new ArrayList<>();
        for (String value : KNOWN) {
            if (values.contains(value)) {
                ordered.add(value);
            }
        }
        for (String value : values) {
            if (!KNOWN.contains(value) && !ordered.contains(value)) {
                ordered.add(value);
            }
        }
        JsonArray array = new JsonArray();
        for (String value : ordered) {
            array.add(value);
        }
        return array;
    }
}