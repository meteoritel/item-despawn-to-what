package com.meteorite.itemdespawntowhat.client.ui.view;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshot;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 快照中的一条规则条目：规则视图 + 来源标注 + 是否可编辑。
 * 兼容两种快照形状：
 * 1) 冻结形状 { "rule": {...}, "origin": "...", "editable": true }；
 * 2) 裸规则对象（无 envelope 字段时按可编辑的覆盖层条目处理）。
 */
public record RuleEntry(RuleView rule, String origin, boolean editable) {

    // 由快照解析全部条目
    public static List<RuleEntry> listFrom(@Nullable RuleSnapshot snapshot) {
        List<RuleEntry> result = new ArrayList<>();
        if (snapshot == null) {
            return result;
        }
        for (JsonObject raw : snapshot.rules()) {
            result.add(fromJson(raw));
        }
        return result;
    }

    // 单条解析：识别 envelope 形状，缺省视为可编辑的裸规则
    public static RuleEntry fromJson(JsonObject raw) {
        if (raw.has("rule") && raw.get("rule").isJsonObject()) {
            String origin = raw.has("origin") && raw.get("origin").isJsonPrimitive()
                    ? raw.get("origin").getAsString() : "";
            boolean editable = !raw.has("editable") || !raw.get("editable").isJsonPrimitive()
                    || raw.get("editable").getAsBoolean();
            return new RuleEntry(RuleView.fromJson(raw.getAsJsonObject("rule")), origin, editable);
        }
        return new RuleEntry(RuleView.fromJson(raw), "", true);
    }

    // 规则 id 文本
    public String ruleId() {
        return rule.id();
    }

    // 来源标注文本（缺失时返回空串）
    public String originText() {
        return origin == null ? "" : origin;
    }
}
