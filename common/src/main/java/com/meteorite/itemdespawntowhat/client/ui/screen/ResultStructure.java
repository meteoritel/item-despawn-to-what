package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.DraftPaths;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * 结果结构（顶层 effects / outcomes 候选）的只读模型。
 * <p>顶层 effects 展示为「单一隐式候选」；outcomes 为非空候选列表。读取一律不修改数据，
 * 结构转换与写入都交给 {@link com.meteorite.itemdespawntowhat.client.edit.RuleDraft}。
 */
public final class ResultStructure {

    // 结构类型
    public enum Kind {
        // 两种结构都没有
        EMPTY,
        // 只有顶层 effects：单一隐式结果
        FLAT,
        // 只有 outcomes：多候选结果
        OUTCOMES,
        // 两种同时存在（非法，本地校验拦截）
        MIXED
    }

    // 工具类
    private ResultStructure() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 结构类型
    public static Kind kind(@Nullable RuleValue rule) {
        boolean flat = hasFlat(rule);
        boolean outcomes = hasOutcomes(rule);
        if (flat && outcomes) {
            return Kind.MIXED;
        }
        if (outcomes) {
            return Kind.OUTCOMES;
        }
        return flat ? Kind.FLAT : Kind.EMPTY;
    }

    // 是否存在顶层 effects（非空）
    public static boolean hasFlat(@Nullable RuleValue rule) {
        RuleValue array = arrayOf(rule, RuleFields.EFFECTS);
        return array != null && !array.isEmpty();
    }

    // 是否存在非空 outcomes
    public static boolean hasOutcomes(@Nullable RuleValue rule) {
        RuleValue array = arrayOf(rule, RuleFields.OUTCOMES);
        return array != null && !array.isEmpty();
    }

    // 候选数量：outcomes 非空按数组长度，否则视为 1 个隐式候选
    public static int candidateCount(@Nullable RuleValue rule) {
        RuleValue array = arrayOf(rule, RuleFields.OUTCOMES);
        return array != null && !array.isEmpty() ? array.size() : 1;
    }

    // 候选体（缺省或越界返回 null）
    public static @Nullable RuleValue candidateAt(@Nullable RuleValue rule, int candidate) {
        RuleValue array = arrayOf(rule, RuleFields.OUTCOMES);
        if (array == null) {
            return null;
        }
        List<RuleValue> items = array.items();
        if (candidate < 0 || candidate >= items.size()) {
            return null;
        }
        RuleValue element = items.get(candidate);
        return element != null && element.isMapping() ? element : null;
    }

    // 候选的效果数组路径（RuleDraft 路径语法）
    public static String listPath(@Nullable RuleValue rule, int candidate) {
        if (hasOutcomes(rule)) {
            return DraftPaths.child(DraftPaths.indexPath(RuleFields.OUTCOMES, Math.max(0, candidate)),
                    RuleFields.CANDIDATE_EFFECTS);
        }
        return RuleFields.EFFECTS;
    }

    // 候选的效果数组（顶层 effects 结构下返回顶层数组）
    public static @Nullable RuleValue effectsAt(@Nullable RuleValue rule, int candidate) {
        RuleValue body = candidateAt(rule, candidate);
        if (body != null) {
            return arrayOf(body, RuleFields.CANDIDATE_EFFECTS);
        }
        return arrayOf(rule, RuleFields.EFFECTS);
    }

    // 候选的效果条数
    public static int effectCount(@Nullable RuleValue rule, int candidate) {
        RuleValue array = effectsAt(rule, candidate);
        return array == null ? 0 : array.size();
    }

    // 全规则效果总数（顶层 effects 与各候选之和）
    public static int totalEffectCount(@Nullable RuleValue rule) {
        int total = 0;
        RuleValue outcomes = arrayOf(rule, RuleFields.OUTCOMES);
        if (outcomes != null && !outcomes.isEmpty()) {
            int candidates = outcomes.size();
            for (int i = 0; i < candidates; i++) {
                total += effectCount(rule, i);
            }
            return total;
        }
        return effectCount(rule, 0);
    }

    // 读取数组字段
    private static @Nullable RuleValue arrayOf(@Nullable RuleValue owner, String name) {
        if (owner == null) {
            return null;
        }
        RuleValue element = owner.child(name);
        return element != null && element.isList() ? element : null;
    }
}
