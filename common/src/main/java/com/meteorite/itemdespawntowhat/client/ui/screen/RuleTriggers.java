package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * 触发原因（triggers）的只读解析与写入（字段见 docs/dev/backend/modules/rule-model.md）。
 * <p>JSON 是唯一事实源：字段省略时按「有效自然触发」展示；用户改动后写为显式数组，
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
    public static boolean isExplicit(@Nullable RuleValue rule) {
        if (rule == null) {
            return false;
        }
        RuleValue element = rule.child(RuleFields.TRIGGERS);
        return element != null && element.isList();
    }

    // 是否显式写成空数组（本地校验拦截）
    public static boolean isExplicitlyEmpty(@Nullable RuleValue rule) {
        RuleValue triggers = rule == null ? null : rule.child(RuleFields.TRIGGERS);
        return isExplicit(rule) && triggers != null && triggers.isEmpty();
    }

    /**
     * 有效展示集合：省略时仅自然触发；显式数组去重后已知取值按固定顺序在前、未知取值按原顺序在后。
     * 空数组保留全不选的草稿状态，由本地校验阻止提交。
     *
     * @param rule 规则草稿，可为空
     * @return 当前展示的触发方式，显式空数组返回空集合
     */
    public static List<String> effective(@Nullable RuleValue rule) {
        RuleValue triggers = rule == null ? null : rule.child(RuleFields.TRIGGERS);
        if (triggers == null || !triggers.isList()) {
            return List.of(NATURAL);
        }
        List<String> declared = new ArrayList<>();
        for (RuleValue element : triggers.items()) {
            if (element == null || !element.isText()) {
                continue;
            }
            String value = element.asText();
            if (!declared.contains(value)) {
                declared.add(value);
            }
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
    public static boolean allowsDelay(@Nullable RuleValue rule) {
        return effective(rule).contains(NATURAL);
    }

    // 生成显式数组：已知取值按固定顺序，未知取值按原顺序追加在后
    public static RuleValue toArray(List<String> values) {
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
        RuleValue.Sequence array = RuleValue.list();
        for (String value : ordered) {
            array.add(RuleValue.text(value));
        }
        return array.build();
    }
}
