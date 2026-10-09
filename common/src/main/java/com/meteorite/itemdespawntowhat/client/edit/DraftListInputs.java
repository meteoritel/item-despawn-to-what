package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** 候选与动作结构变化的输入路径快照；不按内容相等性猜测重复对象的身份。 */
public final class DraftListInputs {
    private final Map<JsonObject, String> objects = new IdentityHashMap<>();

    private DraftListInputs(JsonObject rule) {
        collectEffects(rule.get(RuleFields.EFFECTS), RuleFields.EFFECTS);
        if (rule.get(RuleFields.OUTCOMES) instanceof JsonArray candidates) {
            for (int index = 0; index < candidates.size(); index++) {
                if (!(candidates.get(index) instanceof JsonObject candidate)) continue;
                String path = RuleFields.OUTCOMES + "[" + index + "]";
                objects.put(candidate, path);
                collectEffects(candidate.get(RuleFields.CANDIDATE_EFFECTS), path + "." + RuleFields.CANDIDATE_EFFECTS);
            }
        }
    }

    public static DraftListInputs capture(JsonObject rule) {
        return new DraftListInputs(rule);
    }

    private void collectEffects(JsonElement value, String path) {
        if (!(value instanceof JsonArray effects)) return;
        for (int index = 0; index < effects.size(); index++) {
            if (effects.get(index) instanceof JsonObject effect) objects.put(effect, path + "[" + index + "]");
        }
    }

    /** 必须在修改列表的同一个 apply 内调用，映射也记录没有缓冲的存活对象。 */
    public void remap(EditSession session) {
        DraftListInputs after = capture(session.draft().view());
        Map<String, JsonObject> byPath = new LinkedHashMap<>();
        after.objects.forEach((object, path) -> byPath.put(path, object));
        Map<String, String> paths = new LinkedHashMap<>();
        objects.forEach((object, previous) -> {
            String current = after.objects.get(object);
            if (current != null) {
                paths.put(previous, current);
            } else {
                JsonObject replacement = byPath.get(previous);
                // 同路径的新对象属于字段替换（如产物子类切换），保留该作用域的输入。
                if (replacement == null || objects.containsKey(replacement)) session.clearPendingInputs(previous);
            }
        });
        session.remapPendingInputs(paths);
    }
}
