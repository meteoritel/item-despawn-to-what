package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/***
 * 规则草稿：original（服务器基线）/ working（工作副本）/ dirty 判断 / 删除标记。
 * <p>JSON 是唯一事实来源：草稿不建立与后端 record 平行的强类型模型，
 * 因此未识别字段与第三方扩展字段都能原样保留。字段修改优先使用
 * {@link #setAt(String, JsonElement)}、{@link #mergeAt(String, JsonObject)} 这类路径级写入；
 * 只有用户显式做结构调整（树编辑器整体变更）时才调用 {@link #setConditions} 整体替换。
 * <p>路径语法：以 {@code .} 分隔字段名，数组下标写在字段名后的方括号中，
 * 例如 {@code conditions.terms[0].condition}、{@code effects[2].chance}。
 */
public final class RuleDraft {

    // 服务器下发的原始 JSON，作为 dirty 判定基线
    private final JsonObject original;
    // 当前工作副本
    private JsonObject working;
    // 覆盖层删除标记（不写入 working，由 {@link #toOverlayJson()} 表达）
    private boolean deleted;

    private RuleDraft(JsonObject original, JsonObject working) {
        this.original = original;
        this.working = working;
    }

    // 从服务器下发的规则 JSON 建立草稿
    public static RuleDraft of(JsonObject source) {
        return new RuleDraft(source.deepCopy(), source.deepCopy());
    }

    // ---- 基本状态 ----

    // 规则 id
    public String id() {
        return getString(RuleFields.ID, "");
    }

    // 原始基线（深拷贝）
    public JsonObject original() {
        return original.deepCopy();
    }

    // 工作副本的内部引用，只读用途；需要修改请走本类的写入方法
    public JsonObject view() {
        return working;
    }

    // 工作副本（深拷贝），可直接交给保存流程
    public JsonObject toJson() {
        return working.deepCopy();
    }

    // 覆盖层保存形态：标记删除时附加 delete=true
    public JsonObject toOverlayJson() {
        JsonObject copy = working.deepCopy();
        if (deleted) {
            copy.addProperty(RuleFields.DELETE, true);
        }
        return copy;
    }

    // 是否标记删除
    public boolean isDeleted() {
        return deleted;
    }

    // 设置删除标记
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    // 是否有未保存改动
    public boolean isDirty() {
        return deleted || !original.equals(working);
    }

    // 丢弃改动，回到服务器基线
    public void revert() {
        this.working = original.deepCopy();
        this.deleted = false;
    }

    // ---- 顶层字段读写 ----

    // 是否包含字段
    public boolean has(String field) {
        return working.has(field);
    }

    // 读取字段，缺失返回 null
    public @Nullable JsonElement get(String field) {
        return working.get(field);
    }

    // 写入字段
    public void set(String field, JsonElement value) {
        working.add(field, value);
    }

    // 删除字段
    public boolean remove(String field) {
        return working.remove(field) != null;
    }

    // 读取字符串，缺失或不是字符串时返回 fallback
    public String getString(String field, String fallback) {
        JsonElement element = working.get(field);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : fallback;
    }

    // 读取布尔值，缺失或类型不符时返回 fallback
    public boolean getBoolean(String field, boolean fallback) {
        JsonElement element = working.get(field);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                ? element.getAsBoolean() : fallback;
    }

    // 读取整数，缺失或类型不符时返回 fallback
    public int getInt(String field, int fallback) {
        JsonElement element = working.get(field);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsInt() : fallback;
    }

    // 读取小数，缺失或类型不符时返回 fallback
    public double getDouble(String field, double fallback) {
        JsonElement element = working.get(field);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsDouble() : fallback;
    }

    // 写入字符串字段
    public void setString(String field, String value) {
        working.addProperty(field, value);
    }

    // 写入布尔字段
    public void setBoolean(String field, boolean value) {
        working.addProperty(field, value);
    }

    // 写入整数字段
    public void setInt(String field, int value) {
        working.addProperty(field, value);
    }

    // 写入小数字段
    public void setDouble(String field, double value) {
        working.addProperty(field, value);
    }

    // ---- 路径级读写（保留未识别字段的关键） ----

    // 按路径读取，路径不存在返回 null
    public @Nullable JsonElement getAt(String path) {
        return read(working, parsePath(path));
    }

    // 按路径写入，缺失的中间对象/数组自动创建
    public boolean setAt(String path, JsonElement value) {
        return write(working, parsePath(path), value);
    }

    // 按路径删除，路径不存在返回 false
    public boolean removeAt(String path) {
        return remove(working, parsePath(path));
    }

    // 按路径合并：只覆盖 patch 中出现的字段，目标对象里的其他字段（含第三方未知字段）保留
    public boolean mergeAt(String path, JsonObject patch) {
        JsonElement existing = getAt(path);
        JsonObject target = existing instanceof JsonObject object ? object.deepCopy() : new JsonObject();
        for (Map.Entry<String, JsonElement> entry : patch.entrySet()) {
            target.add(entry.getKey(), entry.getValue().deepCopy());
        }
        return setAt(path, target);
    }

    // ---- 条件树 ----

    // conditions 字段的原始 JSON（缺失或 null 返回 null）
    public @Nullable JsonElement rawConditions() {
        return rawConditionsAt("");
    }

    // 解码可续编的条件草稿；无法识别的形状或类型返回 null，调用方保留原始 JSON。
    public @Nullable ConditionExpression conditionsOrNull(TypeRegistry<ConditionType<?>> conditionTypes) {
        return conditionsAt("", conditionTypes);
    }

    // 整体替换条件树：空表达式时删除 conditions 字段（契约要求不得输出 null）
    public boolean setConditions(ConditionExpression expression, TypeRegistry<ConditionType<?>> conditionTypes) {
        return setConditionsAt("", expression, conditionTypes);
    }

    // ---- 路径级条件树（效果级 conditions 与规则级共用同一入口）----

    // 任意位置条件树的原始 JSON；path 为空表示规则级 conditions
    public @Nullable JsonElement rawConditionsAt(String path) {
        JsonElement raw = isRootPath(path) ? working.get(RuleFields.CONDITIONS) : getAt(path);
        return raw == null || raw.isJsonNull() ? null : raw;
    }

    // 解码任意位置的条件草稿；空组与缺项 NOT 保留，正式配置解码仍由后端严格执行。
    public @Nullable ConditionExpression conditionsAt(String path, TypeRegistry<ConditionType<?>> conditionTypes) {
        JsonElement raw = rawConditionsAt(path);
        if (raw == null) {
            return ConditionExpression.EMPTY;
        }
        return decodeConditions(raw, conditionTypes);
    }

    // 替换任意位置的条件树；空表达式删除该字段（契约要求不得输出 null）
    public boolean setConditionsAt(String path, ConditionExpression expression, TypeRegistry<ConditionType<?>> conditionTypes) {
        if (expression.isEmpty()) {
            return isRootPath(path) ? remove(RuleFields.CONDITIONS) : removeAt(path);
        }
        JsonElement encoded = encodeConditions(expression, conditionTypes);
        if (encoded == null) {
            return false;
        }
        if (isRootPath(path)) {
            working.add(RuleFields.CONDITIONS, encoded);
            return true;
        }
        return setAt(path, encoded);
    }

    // 条件树 → JSON（供表单控件比较与写回；编码失败返回 null）
    public static @Nullable JsonElement encodeConditions(ConditionExpression expression,
            TypeRegistry<ConditionType<?>> conditionTypes) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }
        return new ConditionDraftCodec(conditionTypes).encode(expression);
    }

    // JSON → 可续编条件草稿（无法识别返回 null，调用方保留原文）。
    public static @Nullable ConditionExpression decodeConditions(@Nullable JsonElement raw,
            TypeRegistry<ConditionType<?>> conditionTypes) {
        if (raw == null || raw.isJsonNull()) {
            return ConditionExpression.EMPTY;
        }
        return new ConditionDraftCodec(conditionTypes).decode(raw);
    }

    // 规则级 conditions 视作根路径
    private static boolean isRootPath(@Nullable String path) {
        return path == null || path.isBlank();
    }

    // 用快照整体替换工作副本（撤销/重做与草稿恢复使用，不改变 original 基线）
    public void replaceWith(JsonObject source) {
        this.working = source == null ? new JsonObject() : source.deepCopy();
    }

    // ---- 效果列表（顶层 effects 与候选内 effects 共用同一套操作） ----

    // 效果列表（深拷贝）：默认取顶层 effects
    public List<JsonObject> effects() {
        return effectsAt(RuleFields.EFFECTS);
    }

    // 效果数量：默认取顶层 effects
    public int effectCount() {
        return effectCountAt(RuleFields.EFFECTS);
    }

    // 整体替换效果列表：默认写顶层 effects
    public void setEffects(List<JsonObject> effects) {
        setEffectsAt(RuleFields.EFFECTS, effects);
    }

    // 追加效果：默认追加到顶层 effects
    public void addEffect(JsonObject effect) {
        addEffectAt(RuleFields.EFFECTS, effect);
    }

    // 删除效果，返回被删除的对象（越界返回 null）
    public @Nullable JsonObject removeEffect(int index) {
        return removeEffectAt(RuleFields.EFFECTS, index);
    }

    // 移动效果位置（数组重排）
    public boolean moveEffect(int from, int to) {
        return moveInList(RuleFields.EFFECTS, from, to);
    }

    // 候选结果内效果列表的路径
    public static String candidateEffectsPath(int candidateIndex) {
        return RuleFields.OUTCOMES + "[" + candidateIndex + "]." + RuleFields.CANDIDATE_EFFECTS;
    }

    // 任意位置效果列表（深拷贝）；listPath 为 "effects" 或 "outcomes[i].effects"
    public List<JsonObject> effectsAt(String listPath) {
        List<JsonObject> out = new ArrayList<>();
        if (getAt(listPath) instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (element.isJsonObject()) {
                    out.add(element.getAsJsonObject().deepCopy());
                }
            }
        }
        return out;
    }

    // 任意位置效果数量（按原始数组长度计数，畸变元素同样占位，保证下标与 JSON 一致）
    public int effectCountAt(String listPath) {
        return getAt(listPath) instanceof JsonArray array ? array.size() : 0;
    }

    // 整体替换任意位置的效果列表
    public boolean setEffectsAt(String listPath, List<JsonObject> effects) {
        JsonArray array = new JsonArray();
        for (JsonObject effect : effects) {
            array.add(effect.deepCopy());
        }
        return setAt(listPath, array);
    }

    // 追加效果到任意位置的效果列表（列表缺失时按 listPath 创建）
    public boolean addEffectAt(String listPath, JsonObject effect) {
        JsonElement raw = getAt(listPath);
        JsonArray array = raw instanceof JsonArray existing ? existing : new JsonArray();
        if (!(raw instanceof JsonArray)) {
            setAt(listPath, array);
        }
        array.add(effect.deepCopy());
        return true;
    }

    // 删除任意位置效果列表中的一项，返回被删除的对象（越界返回 null）
    public @Nullable JsonObject removeEffectAt(String listPath, int index) {
        if (!(getAt(listPath) instanceof JsonArray array) || index < 0 || index >= array.size()) {
            return null;
        }
        JsonElement removed = array.remove(index);
        return removed != null && removed.isJsonObject() ? removed.getAsJsonObject() : null;
    }

    // 任意列表路径的重排（effects / outcomes / outcomes[i].effects / conditions.terms 共用）
    public boolean moveInList(String listPath, int from, int to) {
        if (from == to) {
            return false;
        }
        JsonElement raw = getAt(listPath);
        if (!(raw instanceof JsonArray array)) {
            return false;
        }
        if (from < 0 || from >= array.size() || to < 0 || to >= array.size()) {
            return false;
        }
        JsonElement element = array.remove(from);
        if (element == null) {
            return false;
        }
        // Gson 的 JsonArray 没有按索引插入，用 set/add 组合实现「移动到 to」
        if (to < array.size()) {
            array.set(to, element);
        } else {
            array.add(element);
        }
        return true;
    }

    // ---- 候选结果（outcomes） ----

    // 是否声明了顶层 effects
    public boolean hasFlatEffects() {
        return effectCount() > 0;
    }

    // 是否声明了候选结果
    public boolean hasOutcomes() {
        return outcomeCount() > 0;
    }

    // 结构互斥：effects 与 outcomes 是否同时声明（该形状服务端按错误拒绝）
    public boolean hasConflictingStructures() {
        return hasFlatEffects() && hasOutcomes();
    }

    // 候选列表（深拷贝；非对象元素不进入结果，但仍占住数组下标）
    public List<JsonObject> outcomes() {
        List<JsonObject> out = new ArrayList<>();
        JsonElement raw = working.get(RuleFields.OUTCOMES);
        if (raw instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (element.isJsonObject()) {
                    out.add(element.getAsJsonObject().deepCopy());
                }
            }
        }
        return out;
    }

    // 候选数量（按原始数组长度计数）
    public int outcomeCount() {
        JsonElement raw = working.get(RuleFields.OUTCOMES);
        return raw instanceof JsonArray array ? array.size() : 0;
    }

    // 取第 index 个候选（越界或不是对象返回 null）
    public @Nullable JsonObject outcomeAt(int index) {
        JsonElement raw = working.get(RuleFields.OUTCOMES);
        if (!(raw instanceof JsonArray array) || index < 0 || index >= array.size()) {
            return null;
        }
        JsonElement element = array.get(index);
        return element != null && element.isJsonObject() ? element.getAsJsonObject().deepCopy() : null;
    }

    // 整体替换候选列表
    public void setOutcomes(List<JsonObject> outcomes) {
        JsonArray array = new JsonArray();
        for (JsonObject candidate : outcomes) {
            array.add(candidate.deepCopy());
        }
        working.add(RuleFields.OUTCOMES, array);
    }

    // 追加候选
    public void addOutcome(JsonObject candidate) {
        outcomeArrayOrCreate().add(candidate.deepCopy());
    }

    // 删除候选，返回被删除的对象（越界返回 null）
    public @Nullable JsonObject removeOutcome(int index) {
        JsonElement raw = working.get(RuleFields.OUTCOMES);
        if (!(raw instanceof JsonArray array) || index < 0 || index >= array.size()) {
            return null;
        }
        JsonElement removed = array.remove(index);
        return removed != null && removed.isJsonObject() ? removed.getAsJsonObject() : null;
    }

    // 移动候选位置（数组重排）
    public boolean moveOutcome(int from, int to) {
        return moveInList(RuleFields.OUTCOMES, from, to);
    }

    // 已声明的候选 id 集合（非字符串 id 不入集合）
    public Set<String> candidateIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (JsonObject candidate : outcomes()) {
            JsonElement id = candidate.get(RuleFields.CANDIDATE_ID);
            if (id != null && id.isJsonPrimitive() && id.getAsJsonPrimitive().isString()) {
                ids.add(id.getAsString());
            }
        }
        return ids;
    }

    // 候选 id 是否已被占用（同一规则内候选 id 必须唯一）
    public boolean candidateIdExists(String candidateId) {
        return candidateId != null && !candidateId.isBlank() && candidateIds().contains(candidateId);
    }

    // 取 outcomes 数组，没有则创建
    private JsonArray outcomeArrayOrCreate() {
        JsonElement raw = working.get(RuleFields.OUTCOMES);
        if (raw instanceof JsonArray array) {
            return array;
        }
        JsonArray array = new JsonArray();
        working.add(RuleFields.OUTCOMES, array);
        return array;
    }

    // ---- 结构互斥的原子转换 ----

    // 顶层 effects → outcomes：一次操作内删除 effects、写入只含一个候选的 outcomes，
    // 原效果对象逐字保留（由 EditSession.apply 包成一条撤销记录）；
    // 已经声明 outcomes 时返回 false，避免覆盖既有候选。
    public boolean convertToOutcomes(String candidateId) {
        if (hasOutcomes() || candidateId == null || candidateId.isBlank()) {
            return false;
        }
        JsonElement raw = working.get(RuleFields.EFFECTS);
        JsonArray effects = raw instanceof JsonArray array ? array : new JsonArray();
        JsonObject candidate = new JsonObject();
        candidate.addProperty(RuleFields.CANDIDATE_ID, candidateId);
        candidate.add(RuleFields.CANDIDATE_EFFECTS, effects);
        JsonArray outcomes = new JsonArray();
        outcomes.add(candidate);
        working.remove(RuleFields.EFFECTS);
        working.add(RuleFields.OUTCOMES, outcomes);
        return true;
    }

    // ---- 路径解析与读写（实现见 DraftPaths，条件树与效果路径共用同一套语法） ----

    // 解析路径：字段名 + 可选一个数组下标
    private static List<DraftPaths.Segment> parsePath(@Nullable String path) {
        return DraftPaths.parse(path);
    }

    // 按路径读取
    private static @Nullable JsonElement read(@Nullable JsonElement root, List<DraftPaths.Segment> segments) {
        return DraftPaths.read(root, segments);
    }

    // 按路径写入，必要时创建中间容器
    private static boolean write(JsonObject root, List<DraftPaths.Segment> segments, JsonElement value) {
        return DraftPaths.write(root, segments, value);
    }

    // 按路径删除
    private static boolean remove(JsonObject root, List<DraftPaths.Segment> segments) {
        return DraftPaths.remove(root, segments);
    }

    // 便于测试与外部构造基本类型：不做模型校验，仅保证 JSON 合法
    static JsonPrimitive primitive(String value) {
        return new JsonPrimitive(value);
    }
}
