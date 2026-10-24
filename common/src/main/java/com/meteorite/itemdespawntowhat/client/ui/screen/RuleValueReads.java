package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.CombinationMode;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * {@link RuleValue} 的只读工具：安全取值、组合方式读取与条件树宿主需要的结构快照。
 *
 * <p>所有方法都接受 null 或形态不符的值并回落到调用方给出的默认值，不做写入。</p>
 */
final class RuleValueReads {

    // 工具类，禁止实例化
    private RuleValueReads() {
    }

    static List<String> stringList(@Nullable RuleValue element) {
        List<String> values = new ArrayList<>();
        if (element == null || !element.isList()) {
            return values;
        }
        for (RuleValue entry : element.items()) {
            if (entry.isScalar()) {
                values.add(entry.asText());
            }
        }
        return values;
    }

    static String stringField(@Nullable RuleValue object, String name) {
        RuleValue value = object == null ? null : object.child(name);
        return value != null && value.isScalar() ? value.asText() : "";
    }
    static RuleValue conditionOwnerShape(RuleValue rule) {
        RuleValue.Mapping shape = RuleValue.mapping()
                .put(RuleFields.EFFECTS, effectTypeSequence(rule == null ? null : rule.child(RuleFields.EFFECTS)));
        RuleValue.Sequence candidates = RuleValue.list();
        RuleValue outcomes = rule == null ? null : rule.child(RuleFields.OUTCOMES);
        if (outcomes != null && outcomes.isList()) {
            for (RuleValue raw : outcomes.items()) {
                RuleValue.Mapping candidate = RuleValue.mapping();
                if (raw != null && raw.isMapping()) {
                    candidate.put(RuleFields.CANDIDATE_ID, raw.childOr(RuleFields.CANDIDATE_ID, RuleValue.nil()));
                    candidate.put(RuleFields.EFFECTS, effectTypeSequence(raw.child(RuleFields.EFFECTS)));
                }
                candidates.add(candidate.build());
            }
        }
        return shape.put(RuleFields.OUTCOMES, candidates.build()).build();
    }

    static RuleValue effectTypeSequence(@Nullable RuleValue raw) {
        RuleValue.Sequence types = RuleValue.list();
        if (raw != null && raw.isList()) {
            for (RuleValue effect : raw.items()) {
                types.add(effect != null && effect.isMapping()
                        ? effect.childOr(RuleFields.TYPE, RuleValue.nil()) : RuleValue.nil());
            }
        }
        return types.build();
    }
    static String combinationOf(@Nullable RuleValue rule) {
        if (rule == null) {
            return CombinationMode.ROUND_ROBIN.key();
        }
        RuleValue element = rule.child(RuleFields.COMBINATION);
        return element != null && element.isText() ? element.asText() : CombinationMode.ROUND_ROBIN.key();
    }
    static boolean boolOf(@Nullable RuleValue owner, String name, boolean fallback) {
        RuleValue element = owner == null ? null : owner.child(name);
        return element != null && element.isFlag() ? element.asFlag(fallback) : fallback;
    }
    static String stringOf(@Nullable RuleValue owner) {
        RuleValue element = owner == null ? null : owner.child(RuleFields.ID);
        return element != null && element.isText() ? element.asText() : "";
    }
}