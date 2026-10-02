package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    // 解码条件树；无法解码（未知类型、旧格式等）时返回 null，调用方回退只读 JSON 展示
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

    // 解码任意位置的条件树；无法解码返回 null，调用方回退只读 JSON 展示
    public @Nullable ConditionExpression conditionsAt(String path, TypeRegistry<ConditionType<?>> conditionTypes) {
        JsonElement raw = rawConditionsAt(path);
        if (raw == null) {
            return ConditionExpression.EMPTY;
        }
        return RuleCodecs.conditionExpressionCodec(conditionTypes).parse(JsonOps.INSTANCE, raw).result().orElse(null);
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
        return RuleCodecs.conditionExpressionCodec(conditionTypes)
                .encodeStart(JsonOps.INSTANCE, expression).result().orElse(null);
    }

    // JSON → 条件树（无法解码返回 null，调用方回退只读展示）
    public static @Nullable ConditionExpression decodeConditions(@Nullable JsonElement raw,
            TypeRegistry<ConditionType<?>> conditionTypes) {
        if (raw == null || raw.isJsonNull()) {
            return ConditionExpression.EMPTY;
        }
        return RuleCodecs.conditionExpressionCodec(conditionTypes)
                .parse(JsonOps.INSTANCE, raw).result().orElse(null);
    }

    // 规则级 conditions 视作根路径
    private static boolean isRootPath(@Nullable String path) {
        return path == null || path.isBlank();
    }

    // 用快照整体替换工作副本（撤销/重做与草稿恢复使用，不改变 original 基线）
    public void replaceWith(JsonObject source) {
        this.working = source == null ? new JsonObject() : source.deepCopy();
    }

    // ---- 效果列表 ----

    // 效果列表（深拷贝）
    public List<JsonObject> effects() {
        List<JsonObject> out = new ArrayList<>();
        JsonElement raw = working.get(RuleFields.EFFECTS);
        if (raw instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (element.isJsonObject()) {
                    out.add(element.getAsJsonObject().deepCopy());
                }
            }
        }
        return out;
    }

    // 效果数量
    public int effectCount() {
        JsonElement raw = working.get(RuleFields.EFFECTS);
        return raw instanceof JsonArray array ? array.size() : 0;
    }

    // 整体替换效果列表
    public void setEffects(List<JsonObject> effects) {
        JsonArray array = new JsonArray();
        for (JsonObject effect : effects) {
            array.add(effect.deepCopy());
        }
        working.add(RuleFields.EFFECTS, array);
    }

    // 追加效果
    public void addEffect(JsonObject effect) {
        effectArrayOrCreate().add(effect.deepCopy());
    }

    // 删除效果，返回被删除的对象（越界返回 null）
    public @Nullable JsonObject removeEffect(int index) {
        JsonElement raw = working.get(RuleFields.EFFECTS);
        if (!(raw instanceof JsonArray array) || index < 0 || index >= array.size()) {
            return null;
        }
        JsonElement removed = array.remove(index);
        return removed != null && removed.isJsonObject() ? removed.getAsJsonObject() : null;
    }

    // 移动效果位置（数组重排）
    public boolean moveEffect(int from, int to) {
        JsonElement raw = working.get(RuleFields.EFFECTS);
        if (!(raw instanceof JsonArray array)) {
            return false;
        }
        if (from < 0 || from >= array.size() || to < 0 || to >= array.size() || from == to) {
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

    // 取 effects 数组，没有则创建
    private JsonArray effectArrayOrCreate() {
        JsonElement raw = working.get(RuleFields.EFFECTS);
        if (raw instanceof JsonArray array) {
            return array;
        }
        JsonArray array = new JsonArray();
        working.add(RuleFields.EFFECTS, array);
        return array;
    }

    // ---- 路径解析与读写 ----

    // 路径段：字段名与可选数组下标（-1 表示没有下标）
    private record Segment(String name, int index) {}

    // 解析路径：字段名 + 可选一个数组下标
    private static List<Segment> parsePath(String path) {
        List<Segment> segments = new ArrayList<>();
        if (path == null || path.isEmpty()) {
            return segments;
        }
        for (String part : path.split("\\.")) {
            if (part.isEmpty()) {
                continue;
            }
            int open = part.indexOf('[');
            if (open < 0) {
                segments.add(new Segment(part, -1));
                continue;
            }
            int close = part.indexOf(']', open);
            String name = part.substring(0, open);
            String digits = close < 0 ? part.substring(open + 1) : part.substring(open + 1, close);
            int index;
            try {
                index = Integer.parseInt(digits.trim());
            } catch (NumberFormatException ignored) {
                index = -1;
            }
            segments.add(new Segment(name, index));
        }
        return segments;
    }

    // 按路径读取
    private static @Nullable JsonElement read(JsonElement root, List<Segment> segments) {
        JsonElement current = root;
        for (Segment segment : segments) {
            current = readStep(current, segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    // 单步下降：先取字段名，再取数组下标
    private static @Nullable JsonElement readStep(@Nullable JsonElement current, Segment segment) {
        if (current == null) {
            return null;
        }
        if (!segment.name().isEmpty()) {
            if (!current.isJsonObject()) {
                return null;
            }
            current = current.getAsJsonObject().get(segment.name());
            if (current == null) {
                return null;
            }
        }
        if (segment.index() >= 0) {
            if (!current.isJsonArray()) {
                return null;
            }
            JsonArray array = current.getAsJsonArray();
            if (segment.index() >= array.size()) {
                return null;
            }
            current = array.get(segment.index());
        }
        return current;
    }

    // 按路径写入，必要时创建中间容器
    private static boolean write(JsonObject root, List<Segment> segments, JsonElement value) {
        if (segments.isEmpty()) {
            return false;
        }
        JsonObject object = root;
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            boolean last = i == segments.size() - 1;
            if (segment.index() < 0) {
                if (last) {
                    object.add(segment.name(), value);
                    return true;
                }
                JsonElement member = object.get(segment.name());
                JsonObject next = member != null && member.isJsonObject() ? member.getAsJsonObject() : new JsonObject();
                object.add(segment.name(), next);
                object = next;
                continue;
            }
            JsonElement member = object.get(segment.name());
            JsonArray array = member instanceof JsonArray existing ? existing : new JsonArray();
            object.add(segment.name(), array);
            while (array.size() <= segment.index()) {
                array.add(new JsonObject());
            }
            if (last) {
                array.set(segment.index(), value);
                return true;
            }
            JsonElement slot = array.get(segment.index());
            if (!slot.isJsonObject()) {
                slot = new JsonObject();
                array.set(segment.index(), slot);
            }
            object = slot.getAsJsonObject();
        }
        return false;
    }

    // 按路径删除
    private static boolean remove(JsonObject root, List<Segment> segments) {
        if (segments.isEmpty()) {
            return false;
        }
        JsonElement parent = root;
        for (int i = 0; i < segments.size() - 1; i++) {
            parent = readStep(parent, segments.get(i));
            if (parent == null) {
                return false;
            }
        }
        if (!parent.isJsonObject()) {
            return false;
        }
        Segment last = segments.get(segments.size() - 1);
        if (last.index() < 0) {
            return parent.getAsJsonObject().remove(last.name()) != null;
        }
        JsonElement member = parent.getAsJsonObject().get(last.name());
        if (!(member instanceof JsonArray array) || last.index() >= array.size()) {
            return false;
        }
        array.remove(last.index());
        return true;
    }

    // 便于测试与外部构造基本类型：不做模型校验，仅保证 JSON 合法
    static JsonPrimitive primitive(String value) {
        return new JsonPrimitive(value);
    }
}
