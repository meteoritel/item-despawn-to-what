package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * 规则草稿：original（服务器基线）/ working（工作副本）/ dirty 判断 / 删除标记。
 * <p>JSON 是唯一事实来源：草稿不建立与后端 record 平行的强类型模型，
 * 因此未识别字段与第三方扩展字段都能原样保留。通用字段通过
 * {@link #valueAt(String)}、{@link #setAt(String, RuleValue)} 与 {@link #removeAt(String)} 统一访问；
 * 只有用户显式做结构调整（树编辑器整体变更）时才调用 {@link #setConditions} 整体替换。
 * <p>路径语法：以 {@code .} 分隔字段名，数组下标写在字段名后的方括号中，
 * 例如 {@code conditions.terms[0].condition}、{@code effects[2].chance}。
 */
public final class RuleDraft {

    // 服务器下发的原始 JSON，作为 dirty 判定基线
    private final JsonObject original;
    private JsonObject working;
    // 覆盖层删除意图（不写入 working，提交时转换为 DELETE 动作）
    private boolean deleted;
    // 草稿内容变更计数：只在内容真正变化时递增（写入前判等），无变化操作不留撤销记录
    private long mutations;
    // 上次判定 dirty 时的变更计数与判定结果（-1 表示尚未判定）
    private long dirtyMutations = -1L;
    private boolean dirty;

    private RuleDraft(JsonObject original, JsonObject working) {
        this.original = original;
        this.working = working;
    }

    /**
     * 从服务器下发的规则 JSON 建立草稿
     */
    public static RuleDraft of(JsonObject source) {
        return new RuleDraft(source.deepCopy(), source.deepCopy());
    }

    /**
     * 从动态值对象建立草稿（界面层入口）：非映射值按空规则处理
     */
    public static RuleDraft of(RuleValue source) {
        return of(source == null || !source.isMapping() ? new JsonObject() : source.unwrap().getAsJsonObject());
    }

    // 内容变更计数：只在内容真正变化时推进，供变更检测与脏标记缓存使用
    long mutationStamp() {
        return this.mutations;
    }

    // ---- 基本状态 ----

    /**
     * 规则 id
     */
    public String id() {
        return root().text(RuleFields.ID, "");
    }

    /**
     * 原始基线（深拷贝）
     */
    public JsonObject original() {
        return original.deepCopy();
    }

    /**
     * 工作副本（深拷贝），可直接交给保存流程
     */
    public JsonObject toJson() {
        return working.deepCopy();
    }

    public boolean isDeleted() {
        return deleted;
    }

    /**
     * 设置删除标记；标记未变化时不产生改动
     */
    public void setDeleted(boolean deleted) {
        if (this.deleted == deleted) {
            return;
        }
        this.deleted = deleted;
        this.mutations++;
    }

    /**
     * 是否有未保存改动（当前内容偏离原始基线，或标记删除）。
     *
     * <p>判定结果按内容变更计数缓存：界面头部与按钮每帧/tick 多次查询脏标记，
     * 反复执行整棵 JSON 比较会随规则体积线性放大。只有内容真正变化时计数才推进，
     * 因此缓存始终等于「当前内容是否偏离基线」——撤销回基线、载入新草稿与删除标记翻转都会重算。</p>
     *
     * @return 是否有未保存改动
     */
    public boolean isDirty() {
        if (this.dirtyMutations != this.mutations) {
            this.dirty = this.deleted || !this.original.equals(this.working);
            this.dirtyMutations = this.mutations;
        }
        return this.dirty;
    }

    /**
     * 丢弃改动，回到服务器基线；已与基线一致时不产生改动
     */
    public void revert() {
        if (!isDirty()) {
            return;
        }
        this.working = original.deepCopy();
        this.deleted = false;
        this.mutations++;
    }

    // ---- 值对象读写（界面侧入口） ----

    /**
     * 工作副本的实时值视图；不复制，随草稿修改而变化
     */
    public RuleValue root() {
        return RuleValue.wrap(working);
    }

    // ---- 路径级读写（保留未识别字段的关键） ----

    // 按路径读取原始元素（包内私有：界面请使用 valueAt）
    private @Nullable JsonElement readAt(String path) {
        return read(working, parsePath(path));
    }

    // 按路径写入原始元素（包内私有：界面请使用 setAt）
    private boolean writeAt(String path, JsonElement value) {
        // 与现有内容一致视为已写入：无变化操作不应产生撤销记录
        if (!isRootPath(path)) {
            JsonElement current = readAt(path);
            if (current != null && current.equals(value)) {
                return true;
            }
        }
        boolean written = write(working, parsePath(path), value);
        if (written) {
            this.mutations++;
        }
        return written;
    }

    /**
     * 按路径删除，路径不存在返回 false
     */
    public boolean removeAt(String path) {
        boolean removed = remove(working, parsePath(path));
        if (removed) {
            this.mutations++;
        }
        return removed;
    }

    /**
     * 按路径读取值；路径不存在返回 null
     */
    public @Nullable RuleValue valueAt(String path) {
        JsonElement element = readAt(path);
        return element == null ? null : RuleValue.wrap(element);
    }

    /**
     * 按路径写入值
     */
    public boolean setAt(String path, RuleValue value) {
        return writeAt(path, value.unwrap().deepCopy());
    }

    // ---- 条件树 ----

    /**
     * 规则级条件树的值视图；缺失返回 null
     */
    public @Nullable RuleValue conditionsValue() {
        return conditionsValueAt("");
    }

    /**
     * 任意位置条件树的值视图；path 为空表示规则级 conditions
     */
    public @Nullable RuleValue conditionsValueAt(String path) {
        JsonElement raw = conditionsElementAt(path);
        return raw == null ? null : RuleValue.wrap(raw);
    }

    /**
     * 解码可续编的条件草稿；无法识别的形状或类型返回 null，调用方保留原始 JSON。
     */
    public @Nullable ConditionExpression conditionsOrNull(TypeRegistry<ConditionType<?>> conditionTypes) {
        return conditionsAt("", conditionTypes);
    }

    /**
     * 整体替换条件树：空表达式时删除 conditions 字段（契约要求不得输出 null）
     */
    public boolean setConditions(ConditionExpression expression, TypeRegistry<ConditionType<?>> conditionTypes) {
        return setConditionsAt("", expression, conditionTypes);
    }

    // ---- 规则级条件树 ----

    // 原始条件元素（包内私有：界面请使用 conditionsValueAt）
    private @Nullable JsonElement conditionsElementAt(String path) {
        JsonElement raw = isRootPath(path) ? working.get(RuleFields.CONDITIONS) : readAt(path);
        return raw == null || raw.isJsonNull() ? null : raw;
    }

    /**
     * 解码任意位置的条件草稿；空组与缺项 NOT 保留，正式配置解码仍由后端严格执行。
     */
    public @Nullable ConditionExpression conditionsAt(String path, TypeRegistry<ConditionType<?>> conditionTypes) {
        JsonElement raw = conditionsElementAt(path);
        if (raw == null) {
            return ConditionExpression.EMPTY;
        }
        return decodeConditionsElement(raw, conditionTypes);
    }

    /**
     * 替换任意位置的条件树；空表达式删除该字段（契约要求不得输出 null）
     */
    public boolean setConditionsAt(String path, ConditionExpression expression, TypeRegistry<ConditionType<?>> conditionTypes) {
        if (expression.isEmpty()) {
            if (!isRootPath(path)) {
                return removeAt(path);
            }
            return removeAt(RuleFields.CONDITIONS);
        }
        JsonElement encoded = encodeConditionsElement(expression, conditionTypes);
        if (encoded == null) {
            return false;
        }
        if (isRootPath(path)) {
            if (encoded.equals(working.get(RuleFields.CONDITIONS))) {
                return true;
            }
            working.add(RuleFields.CONDITIONS, encoded);
            this.mutations++;
            return true;
        }
        return writeAt(path, encoded);
    }

    /**
     * 条件树 → 值对象（供表单控件比较与写回；编码失败返回 null）
     */
    public static @Nullable RuleValue encodeConditionsValue(ConditionExpression expression,
            TypeRegistry<ConditionType<?>> conditionTypes) {
        JsonElement encoded = encodeConditionsElement(expression, conditionTypes);
        return encoded == null ? null : RuleValue.wrap(encoded);
    }

    /**
     * 值对象 → 可续编条件草稿（无法识别返回 null，调用方保留原文）
     */
    public static @Nullable ConditionExpression decodeConditions(@Nullable RuleValue raw,
            TypeRegistry<ConditionType<?>> conditionTypes) {
        return raw == null ? ConditionExpression.EMPTY : decodeConditionsElement(raw.unwrap(), conditionTypes);
    }

    // 条件树编码实现（包内私有：界面请使用 encodeConditionsValue）
    private static @Nullable JsonElement encodeConditionsElement(ConditionExpression expression,
            TypeRegistry<ConditionType<?>> conditionTypes) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }
        return new ConditionDraftCodec(conditionTypes).encode(expression);
    }

    // 条件树解码实现（包内私有：界面请使用 decodeConditions(RuleValue, TypeRegistry)）
    private static @Nullable ConditionExpression decodeConditionsElement(@Nullable JsonElement raw,
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

    /**
     * 用快照整体替换工作副本（撤销/重做与草稿恢复使用，不改变 original 基线）
     */
    public void replaceWith(JsonObject source) {
        this.working = source == null ? new JsonObject() : source.deepCopy();
        this.mutations++;
    }

    /**
     * 用动态值对象整体替换工作副本（界面层入口）：非映射值按空规则处理
     */
    public void replaceWith(RuleValue source) {
        replaceWith(source == null || !source.isMapping() ? new JsonObject() : source.unwrap().getAsJsonObject());
    }

    // ---- 效果列表（顶层 effects 与候选内 effects 共用同一套操作） ----

    /**
     * 效果数量：默认取顶层 effects
     */
    public int effectCount() {
        return effectCountAt(RuleFields.EFFECTS);
    }

    /**
     * 移动效果位置（数组重排）
     */
    public boolean moveEffect(int from, int to) {
        return moveInList(RuleFields.EFFECTS, from, to);
    }

    /**
     * 候选结果内效果列表的路径
     */
    public static String candidateEffectsPath(int candidateIndex) {
        return DraftPaths.child(DraftPaths.indexPath(RuleFields.OUTCOMES, candidateIndex), RuleFields.CANDIDATE_EFFECTS);
    }

    /**
     * 任意位置效果数量（按原始数组长度计数，畸变元素同样占位，保证下标与 JSON 一致）
     */
    public int effectCountAt(String listPath) {
        return readAt(listPath) instanceof JsonArray array ? array.size() : 0;
    }

    /**
     * 任意位置效果列表的值视图（保序，含非对象元素）；非数组返回空表
     */
    public List<RuleValue> effectValuesAt(String listPath) {
        JsonElement raw = readAt(listPath);
        if (!(raw instanceof JsonArray array)) {
            return List.of();
        }
        List<RuleValue> out = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            out.add(RuleValue.wrap(element));
        }
        return out;
    }

    /**
     * 任意列表路径的重排（effects / outcomes / outcomes[i].effects / conditions.terms 共用）
     */
    public boolean moveInList(String listPath, int from, int to) {
        if (from == to) {
            return false;
        }
        JsonElement raw = readAt(listPath);
        if (!(raw instanceof JsonArray array)) {
            return false;
        }
        if (from < 0 || from >= array.size() || to < 0 || to >= array.size()) {
            return false;
        }
        JsonElement element = array.get(from);
        boolean changesOrder = false;
        for (int index = Math.min(from, to); index <= Math.max(from, to); index++) {
            if (!element.equals(array.get(index))) {
                changesOrder = true;
                break;
            }
        }
        // 区间内容完全相同时是无变化操作，不能仅交换对象身份及其输入缓冲。
        if (!changesOrder) return false;
        // to 是移动后的最终下标；逐项平移保留列表长度与所有元素对象。
        if (from < to) {
            for (int index = from; index < to; index++) array.set(index, array.get(index + 1));
        } else {
            for (int index = from; index > to; index--) array.set(index, array.get(index - 1));
        }
        array.set(to, element);
        this.mutations++;
        return true;
    }

    // ---- 候选结果（outcomes） ----

    public boolean hasFlatEffects() {
        return effectCount() > 0;
    }

    public boolean hasOutcomes() {
        return outcomeCount() > 0;
    }

    /**
     * 结构互斥：effects 与 outcomes 是否同时声明（该形状服务端按错误拒绝）
     */
    public boolean hasConflictingStructures() {
        return hasFlatEffects() && hasOutcomes();
    }

    /**
     * 候选数量（按原始数组长度计数）
     */
    public int outcomeCount() {
        JsonElement raw = working.get(RuleFields.OUTCOMES);
        return raw instanceof JsonArray array ? array.size() : 0;
    }

    /**
     * 候选列表的值视图（保序，含非对象元素）
     */
    public List<RuleValue> outcomeValues() {
        JsonElement raw = working.get(RuleFields.OUTCOMES);
        if (!(raw instanceof JsonArray array)) {
            return List.of();
        }
        List<RuleValue> out = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            out.add(RuleValue.wrap(element));
        }
        return out;
    }

    /**
     * 取第 index 个候选的值视图（越界返回 null）
     */
    public @Nullable RuleValue outcomeValueAt(int index) {
        JsonElement raw = working.get(RuleFields.OUTCOMES);
        if (!(raw instanceof JsonArray array) || index < 0 || index >= array.size()) {
            return null;
        }
        return RuleValue.wrap(array.get(index));
    }

    /**
     * 移动候选位置（数组重排）
     */
    public boolean moveOutcome(int from, int to) {
        return moveInList(RuleFields.OUTCOMES, from, to);
    }

    /**
     * 已声明的候选 id 集合（非字符串 id 不入集合）
     */
    public Set<String> candidateIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (RuleValue candidate : outcomeValues()) {
            RuleValue id = candidate.child(RuleFields.CANDIDATE_ID);
            if (id != null && id.isText()) {
                ids.add(id.asText());
            }
        }
        return ids;
    }

    /**
     * 候选 id 是否已被占用（同一规则内候选 id 必须唯一）
     */
    public boolean candidateIdExists(String candidateId) {
        return candidateId != null && !candidateId.isBlank() && candidateIds().contains(candidateId);
    }

    // ---- 结构互斥的原子转换 ----

    /**
     * 顶层 effects → outcomes：一次操作内删除 effects、写入只含一个候选的 outcomes，
     * 原效果对象逐字保留（由 EditSession.apply 包成一条撤销记录）；
     * 已经声明 outcomes 时返回 false，避免覆盖既有候选。
     */
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
        this.mutations++;
        return true;
    }

    // ---- 编辑层内部入口（包内可见；界面请使用 root()/valueAt()） ----

    // 工作副本引用：撤销快照与落盘在同一包内使用。只读视图——任何写入都必须走本类 mutator，
    // 否则变更计数、脏标记缓存与撤销记录都不会更新
    JsonObject workingView() {
        return working;
    }

    // 按路径读取元素：包内工具（DraftListInputs / EditSession）使用
    @Nullable JsonElement elementAt(String path) {
        return read(working, parsePath(path));
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

}
