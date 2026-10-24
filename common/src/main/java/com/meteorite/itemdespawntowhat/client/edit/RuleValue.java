package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * 规则值对象：编辑层与界面层之间的动态值中介。
 *
 * <p><b>职责</b>：把规则草稿里的值以「标量 / 映射 / 列表」三种形态交给界面，界面只消费本类型
 * 与 Java 值，不再接触任何 JSON 库类型。JSON 仍是唯一事实源，本类型只是它的只读视图与构造器，
 * 不建立平行的强类型模型，因此未识别字段与第三方扩展字段可以原样保留。</p>
 *
 * <p><b>三态键</b>：{@link #child(String)} 返回 {@code null} 表示「键不存在」，
 * 返回 {@link #nil()} 表示「键存在且值为显式 null」，两者可区分；写入时同样如此。</p>
 *
 * <p><b>保真约定</b>：读取路径与迁移前的 Gson 语义一致——数值保留原始字面量
 * （{@code 1.0} 不会变成 {@code 1}），{@link #asText()} 对非标量抛
 * {@link UnsupportedOperationException}（与 {@code JsonElement#getAsString()} 相同），
 * {@link #asInteger(int)}/{@link #asDecimal(double)}/{@link #asFlag(boolean)} 对非对应标量返回兜底值。</p>
 *
 * <p><b>可变性</b>：本类型是不可变句柄；读取方法看到的是当前草稿的实时内容，
 * 需要快照请调用 {@link #deepCopy()}。</p>
 */
public final class RuleValue {

    /** 值形态判别：决定 {@link #kind()} 与各 is* 方法走哪条分支；形态按当前元素内容判定，不缓存。 */
    public enum Kind {
        // 显式 null
        NULL,
        // 字符串
        TEXT,
        // 数值（整数或小数）
        NUMBER,
        // 布尔
        FLAG,
        // 映射（字段名 -> 值，保持插入顺序）
        MAPPING,
        // 列表
        LIST
    }

    // 文本输出用的 Gson 实例：只在本包内部使用，界面不感知
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    // 唯一事实载体；读取不复制，写入由 RuleDraft 负责深拷贝
    private final JsonElement element;

    private RuleValue(JsonElement element) {
        this.element = element;
    }

    // ---- 构造 ----

    // 显式 null
    public static RuleValue nil() {
        return new RuleValue(JsonNull.INSTANCE);
    }

    // 字符串
    public static RuleValue text(String value) {
        return new RuleValue(new JsonPrimitive(Objects.requireNonNull(value, "value")));
    }

    // 整数
    public static RuleValue integer(long value) {
        return new RuleValue(new JsonPrimitive(value));
    }

    // 小数
    public static RuleValue decimal(double value) {
        return new RuleValue(new JsonPrimitive(value));
    }

    // 布尔
    public static RuleValue flag(boolean value) {
        return new RuleValue(new JsonPrimitive(value));
    }

    // 空映射构造器
    public static Mapping mapping() {
        return new Mapping();
    }

    // 以现有映射为底稿的构造器（用于「复制后覆盖若干键」）
    public static Mapping mapping(RuleValue source) {
        Mapping builder = new Mapping();
        if (source != null && source.isMapping()) {
            for (Map.Entry<String, JsonElement> entry : source.element.getAsJsonObject().entrySet()) {
                builder.values.put(entry.getKey(), wrap(entry.getValue()));
            }
        }
        return builder;
    }

    // 空列表构造器
    public static Sequence list() {
        return new Sequence();
    }

    // 以现有列表为底稿的构造器
    public static Sequence list(RuleValue source) {
        Sequence builder = new Sequence();
        if (source != null && source.isList()) {
            for (JsonElement item : source.element.getAsJsonArray()) {
                builder.values.add(wrap(item));
            }
        }
        return builder;
    }

    // 字符串列表
    public static Sequence textList(List<String> values) {
        Sequence builder = new Sequence();
        for (String value : values) {
            builder.add(text(value));
        }
        return builder;
    }

    // 解析 JSON 文本；语法错误返回 null（错误类型不越过本包）
    public static @Nullable RuleValue parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            return parsed == null || parsed.isJsonNull() ? null : wrap(parsed);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    // 从读取器解析 JSON；为空、语法错误或 JSON null 时返回 null（错误类型不越过本包）
    public static @Nullable RuleValue parse(Reader json) {
        if (json == null) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseReader(json);
            return parsed == null || parsed.isJsonNull() ? null : wrap(parsed);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    // ---- 形态判定 ----

    public Kind kind() {
        if (element.isJsonNull()) return Kind.NULL;
        if (element.isJsonArray()) return Kind.LIST;
        if (element.isJsonObject()) return Kind.MAPPING;
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) return Kind.FLAG;
        return primitive.isNumber() ? Kind.NUMBER : Kind.TEXT;
    }

    public boolean isNull() { return element.isJsonNull(); }
    public boolean isMapping() { return element.isJsonObject(); }
    public boolean isList() { return element.isJsonArray(); }
    public boolean isText() { return kind() == Kind.TEXT; }
    public boolean isNumber() { return kind() == Kind.NUMBER; }
    public boolean isFlag() { return kind() == Kind.FLAG; }

    // 是否标量（文本 / 数值 / 布尔）
    public boolean isScalar() {
        return element.isJsonPrimitive();
    }

    // 映射或列表是否为空
    public boolean isEmpty() {
        if (isMapping()) return element.getAsJsonObject().isEmpty();
        if (isList()) return element.getAsJsonArray().isEmpty();
        return false;
    }

    // ---- 映射读取 ----

    // 键是否存在（显式 null 也算存在）
    public boolean has(String key) {
        return isMapping() && element.getAsJsonObject().has(key);
    }

    // 子值；键不存在返回 null，显式 null 返回 nil()
    public @Nullable RuleValue child(String key) {
        if (!isMapping()) return null;
        JsonElement child = element.getAsJsonObject().get(key);
        return child == null ? null : wrap(child);
    }

    // 子值；键不存在或形态不符时返回兜底
    public RuleValue childOr(String key, RuleValue fallback) {
        RuleValue child = child(key);
        return child == null ? fallback : child;
    }

    // 键集合（保持插入顺序）
    public Set<String> keys() {
        if (!isMapping()) return Set.of();
        return new LinkedHashSet<>(element.getAsJsonObject().keySet());
    }

    // 键值表（保持插入顺序）
    public Map<String, RuleValue> entries() {
        if (!isMapping()) return Map.of();
        Map<String, RuleValue> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            out.put(entry.getKey(), wrap(entry.getValue()));
        }
        return out;
    }

    // 映射键数或列表长度
    public int size() {
        if (isMapping()) return element.getAsJsonObject().size();
        if (isList()) return element.getAsJsonArray().size();
        return 0;
    }

    // ---- 列表读取 ----

    // 列表元素；非列表返回空表
    public List<RuleValue> items() {
        if (!isList()) return List.of();
        List<RuleValue> out = new ArrayList<>(element.getAsJsonArray().size());
        for (JsonElement item : element.getAsJsonArray()) {
            out.add(wrap(item));
        }
        return out;
    }

    // 指定下标元素；越界或非列表返回 null
    public @Nullable RuleValue item(int index) {
        if (!isList()) return null;
        JsonArray array = element.getAsJsonArray();
        return index < 0 || index >= array.size() ? null : wrap(array.get(index));
    }

    // ---- 标量读取 ----

    // 子字段文本；缺失或非字符串返回兜底
    public String text(String key, String fallback) {
        RuleValue child = child(key);
        return child != null && child.isText() ? child.asText() : fallback;
    }

    // 子字段整数；缺失或非数值返回兜底
    public int integer(String key, int fallback) {
        RuleValue child = child(key);
        return child != null && child.isNumber() ? child.element.getAsInt() : fallback;
    }

    // 子字段小数；缺失或非数值返回兜底
    public double decimal(String key, double fallback) {
        RuleValue child = child(key);
        return child != null && child.isNumber() ? child.element.getAsDouble() : fallback;
    }

    // 子字段布尔；缺失或非布尔返回兜底
    public boolean flag(String key, boolean fallback) {
        RuleValue child = child(key);
        return child != null && child.isFlag() ? child.element.getAsBoolean() : fallback;
    }

    // 自身文本；非标量抛 UnsupportedOperationException（与迁移前的 getAsString 一致）
    public String asText() {
        return element.getAsString();
    }

    // 自身文本；非标量返回兜底
    public String asText(String fallback) {
        return isScalar() ? element.getAsString() : fallback;
    }

    // 自身整数；非数值返回兜底
    public int asInteger(int fallback) {
        return isNumber() ? element.getAsInt() : fallback;
    }

    // 自身小数；非数值返回兜底
    public double asDecimal(double fallback) {
        return isNumber() ? element.getAsDouble() : fallback;
    }

    // 自身布尔；非布尔返回兜底
    public boolean asFlag(boolean fallback) {
        return isFlag() ? element.getAsBoolean() : fallback;
    }

    // 列表里的文本元素；非文本元素跳过
    public List<String> textItems() {
        if (!isList()) return List.of();
        List<String> out = new ArrayList<>();
        for (RuleValue item : items()) {
            if (item.isText()) out.add(item.asText());
        }
        return out;
    }

    // ---- 输出 ----

    // 紧凑 JSON 文本（供源码编辑器与调试展示）
    public String toJson() {
        return element.toString();
    }

    // 美化 JSON 文本（缩进两格，供源码编辑器展示）
    public String toPrettyJson() {
        return PRETTY.toJson(element);
    }

    // 深拷贝快照；与草稿后续修改解耦
    public RuleValue deepCopy() {
        return new RuleValue(element.deepCopy());
    }

    // 用给定编解码器解码当前值；失败返回 null（界面层借此调用编解码器而不接触 JSON 树类型）
    public <T> @Nullable T decode(Codec<T> codec) {
        return codec.parse(JsonOps.INSTANCE, element).result().orElse(null);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RuleValue value && element.equals(value.element);
    }

    @Override
    public int hashCode() {
        return element.hashCode();
    }

    @Override
    public String toString() {
        return element.toString();
    }

    // ---- 编辑层内部桥接（界面不可见 Json* 类型） ----

    // 包装草稿里的元素；不复制，读取即实时视图
    static RuleValue wrap(JsonElement element) {
        return new RuleValue(element);
    }

    // 交给 RuleDraft 写入前使用；调用方负责深拷贝
    JsonElement unwrap() {
        return element;
    }

    /** 映射构造器：按插入顺序保存键值，重复键覆盖。 */
    public static final class Mapping {

        private final Map<String, RuleValue> values = new LinkedHashMap<>();

        private Mapping() { }

        public Mapping put(String key, RuleValue value) {
            values.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            return this;
        }

        // 值为 null 时跳过：便于「有则写入」的调用点
        public Mapping putIfPresent(String key, @Nullable RuleValue value) {
            if (value != null) put(key, value);
            return this;
        }

        public Mapping remove(String key) {
            values.remove(key);
            return this;
        }

        public boolean has(String key) {
            return values.containsKey(key);
        }

        public RuleValue build() {
            JsonObject object = new JsonObject();
            for (Map.Entry<String, RuleValue> entry : values.entrySet()) {
                object.add(entry.getKey(), entry.getValue().element.deepCopy());
            }
            return new RuleValue(object);
        }
    }

    /** 列表构造器：按加入顺序保存元素。 */
    public static final class Sequence {

        private final List<RuleValue> values = new ArrayList<>();

        private Sequence() { }

        public Sequence add(RuleValue value) {
            values.add(Objects.requireNonNull(value, "value"));
            return this;
        }

        public Sequence addAll(List<RuleValue> items) {
            for (RuleValue item : items) add(item);
            return this;
        }

        public int size() {
            return values.size();
        }

        public RuleValue build() {
            JsonArray array = new JsonArray();
            for (RuleValue value : values) {
                array.add(value.element.deepCopy());
            }
            return new RuleValue(array);
        }
    }
}
