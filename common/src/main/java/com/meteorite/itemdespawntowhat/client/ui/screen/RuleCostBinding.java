package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.DraftPaths;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * 消耗类效果在草稿中的定位。
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
    public static @Nullable Ref find(@Nullable RuleValue rule, String effectId) {
        if (rule == null) {
            return null;
        }
        RuleValue flat = arrayOf(rule, RuleFields.EFFECTS);
        if (flat != null) {
            int index = indexOf(flat, effectId);
            if (index >= 0) {
                String path = DraftPaths.indexPath(RuleFields.EFFECTS, index);
                return new Ref(RuleFields.EFFECTS, index, path, null, -1);
            }
        }
        RuleValue outcomes = arrayOf(rule, RuleFields.OUTCOMES);
        if (outcomes == null) {
            return null;
        }
        List<RuleValue> candidates = outcomes.items();
        for (int candidate = 0; candidate < candidates.size(); candidate++) {
            RuleValue element = candidates.get(candidate);
            if (element == null || !element.isMapping()) {
                continue;
            }
            RuleValue effects = arrayOf(element, RuleFields.CANDIDATE_EFFECTS);
            if (effects == null) {
                continue;
            }
            int index = indexOf(effects, effectId);
            if (index >= 0) {
                String listPath = DraftPaths.child(DraftPaths.indexPath(RuleFields.OUTCOMES, candidate),
                        RuleFields.CANDIDATE_EFFECTS);
                return new Ref(listPath, index, DraftPaths.indexPath(listPath, index), stringOf(element), candidate);
            }
        }
        return null;
    }

    // 定位消耗来源物品效果
    public static @Nullable Ref consumeSource(@Nullable RuleValue rule) {
        return find(rule, CONSUME_SOURCE);
    }

    // 定位消耗催化剂效果
    public static @Nullable Ref consumeCatalyst(@Nullable RuleValue rule) {
        return find(rule, CONSUME_CATALYST);
    }

    // 效果是否为指定类型（字符串比较，不做资源名解析）
    public static boolean isType(@Nullable RuleValue effect, String effectId) {
        String type = typeOf(effect);
        return type != null && type.equals(effectId);
    }

    // 读取效果类型字符串
    public static @Nullable String typeOf(@Nullable RuleValue effect) {
        if (effect == null || !effect.isMapping()) {
            return null;
        }
        RuleValue type = effect.child(RuleFields.TYPE);
        if (type == null || !type.isText()) {
            return null;
        }
        return type.asText();
    }

    // 查找指定类型的下标
    private static int indexOf(RuleValue array, String effectId) {
        List<RuleValue> items = array.items();
        for (int i = 0; i < items.size(); i++) {
            if (isType(items.get(i), effectId)) {
                return i;
            }
        }
        return -1;
    }

    // 读取数组字段
    private static @Nullable RuleValue arrayOf(RuleValue owner, String name) {
        RuleValue element = owner.child(name);
        return element != null && element.isList() ? element : null;
    }

    // 读取候选稳定 id（缺失或类型不符时返回 null）
    private static @Nullable String stringOf(RuleValue owner) {
        RuleValue element = owner.child(RuleFields.CANDIDATE_ID);
        if (element == null || !element.isText()) {
            return null;
        }
        return element.asText();
    }
}
