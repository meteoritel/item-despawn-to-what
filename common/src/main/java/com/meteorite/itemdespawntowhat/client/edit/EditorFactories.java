package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 规则与候选结果的工厂：空白新建、模板创建、复制全部走同一套身份分配与默认体构造。
 * <p>身份分配（主计划 §6 / ADR-0021）：规则 id 形如 {@code itemdespawntowhat:rule_<32位小写UUID>}，
 * 候选 id 形如 {@code result_<32位小写UUID>}；模板创建与复制一律分配全新身份，不做「沿用旧 id」的猜测。
 * <p>未改动的后端默认值不写进 JSON（fields §1/§2）：新规则只落 id、source、固定源成本、
 * schema_version 与一个初始候选；enabled / priority / trigger_after_seconds / triggers / combination
 * 全部依赖服务端默认，避免把默认值固化进覆盖层文件。
 * <p>safe_spawn 与 fill_origin 也不写入，保持后端默认（false / true）。
 */
public final class EditorFactories {

    // 新规则 id 前缀（含命名空间）
    public static final String RULE_ID_PREFIX = TypeLabels.OWN_NAMESPACE + ":rule_";

    // 新候选 id 前缀
    public static final String CANDIDATE_ID_PREFIX = "result_";

    // 工具类
    private EditorFactories() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 生成新的规则 id
    public static String newRuleId() {
        return RULE_ID_PREFIX + token();
    }

    // 生成新的候选 id
    public static String newCandidateId() {
        return CANDIDATE_ID_PREFIX + token();
    }

    // 32 位小写 UUID（去掉连字符）
    private static String token() {
        return UUID.randomUUID().toString().replace("-", "").toLowerCase(java.util.Locale.ROOT);
    }

    // 空白规则：一个初始候选、固定源成本 1、当前 schema_version
    public static JsonObject blankRule() {
        return blankRule(newRuleId());
    }

    // 空白规则（指定 id，便于调用方先算身份再建体）
    public static JsonObject blankRule(String ruleId) {
        JsonObject body = new JsonObject();
        body.addProperty(RuleFields.ID, ruleId);
        JsonObject source = new JsonObject();
        source.add(RuleFields.SOURCE_ITEMS, new JsonArray());
        body.add(RuleFields.SOURCE, source);
        body.addProperty(RuleFields.SOURCE_COST, 1);
        body.addProperty(RuleFields.SCHEMA_VERSION, RuleCodecs.DEFAULT_SCHEMA_VERSION);
        JsonArray outcomes = new JsonArray();
        outcomes.add(candidateBody(newCandidateId()));
        body.add(RuleFields.OUTCOMES, outcomes);
        return body;
    }

    // 候选体：只写身份与效果列表，其余字段依赖后端默认
    public static JsonObject candidateBody(String candidateId) {
        JsonObject candidate = new JsonObject();
        candidate.addProperty(RuleFields.CANDIDATE_ID, candidateId);
        candidate.add(RuleFields.CANDIDATE_EFFECTS, new JsonArray());
        return candidate;
    }

    // 模板创建规则：保留模板结构，只分配新身份
    public static JsonObject templateRule(JsonObject template) {
        return templateRule(newRuleId(), template);
    }

    // 模板创建规则（指定 id）
    public static JsonObject templateRule(String ruleId, JsonObject template) {
        if (template == null) {
            return blankRule(ruleId);
        }
        return reassignIdentity(ruleId, template);
    }

    // 复制规则：规则与候选都分配新身份
    public static JsonObject copyRule(String newRuleId, JsonObject source) {
        if (source == null) {
            return blankRule(newRuleId);
        }
        return reassignIdentity(newRuleId, source);
    }

    /**
     * 深拷贝规则体并换身份：规则 id 换成给定值，已声明候选的 id 全部换成新的。
     * <p>未声明 outcomes 时只换规则 id（顶层 effects 没有身份）。
     */
    public static JsonObject reassignIdentity(String ruleId, JsonObject source) {
        JsonObject body = source.deepCopy();
        body.addProperty(RuleFields.ID, ruleId);
        JsonElement raw = body.get(RuleFields.OUTCOMES);
        if (!(raw instanceof JsonArray array)) {
            return body;
        }
        Set<String> used = new HashSet<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject candidate = element.getAsJsonObject();
            String id = uniqueCandidateId(used);
            candidate.addProperty(RuleFields.CANDIDATE_ID, id);
            used.add(id);
        }
        return body;
    }

    // 在已用 id 集合之外生成候选 id（同一次创建内不重复）
    public static String uniqueCandidateId(Set<String> used) {
        Set<String> taken = used == null ? Set.of() : used;
        String id = newCandidateId();
        while (taken.contains(id)) {
            id = newCandidateId();
        }
        return id;
    }

    // 规则体内已声明的候选 id 集合
    public static Set<String> candidateIds(JsonObject body) {
        Set<String> ids = new LinkedHashSet<>();
        if (body == null) {
            return ids;
        }
        JsonElement raw = body.get(RuleFields.OUTCOMES);
        if (!(raw instanceof JsonArray array)) {
            return ids;
        }
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonElement id = element.getAsJsonObject().get(RuleFields.CANDIDATE_ID);
            if (id != null && id.isJsonPrimitive() && id.getAsJsonPrimitive().isString()) {
                ids.add(id.getAsString());
            }
        }
        return ids;
    }
}
