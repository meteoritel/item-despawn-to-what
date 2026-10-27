package com.meteorite.itemdespawntowhat.core.api.param;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 一个参数所有者的有序声明与取值绑定，供 codec、批量校验和客户端声明投影共同消费。
 * 同一组内名称唯一；字段构件只接受本组登记的同一个声明实例。
 *
 * @param <P> 参数对象类型
 */
public final class ParamList<P> {
    private final List<ParamBinding<? super P, ?>> bindings;
    private final List<ParamSpec<?>> specs;
    private final Map<String, ParamBinding<? super P, ?>> byName;

    private ParamList(List<ParamBinding<? super P, ?>> bindings) {
        this.bindings = List.copyOf(bindings);
        List<ParamSpec<?>> declarations = new ArrayList<>();
        Map<String, ParamBinding<? super P, ?>> index = new LinkedHashMap<>();
        for (ParamBinding<? super P, ?> binding : bindings) {
            if (index.putIfAbsent(binding.spec().name(), binding) != null) {
                throw new IllegalArgumentException("参数名重复: " + binding.spec().name());
            }
            declarations.add(binding.spec());
        }
        specs = List.copyOf(declarations);
        byName = Map.copyOf(index);
    }

    /** 空声明供无参类型与未声明参数的扩展类型使用。 */
    public static <P> ParamList<P> empty() {
        return new ParamList<>(List.of());
    }

    /** 按声明顺序组装绑定；允许复用基类型的公共字段。 */
    @SafeVarargs
    @SuppressWarnings({"varargs", "Convert2Diamond"})
    public static <P> ParamList<P> of(ParamBinding<? super P, ?>... bindings) {
        // javac 对逆变绑定数组的嵌套推断会丢失 P，构造时显式保留所有者类型。
        return new ParamList<P>(Arrays.asList(bindings));
    }

    /** 追加公共声明，保留两组顺序并检查名称唯一。 */
    public ParamList<P> plus(ParamList<? super P> other) {
        List<ParamBinding<? super P, ?>> merged = new ArrayList<>(bindings);
        merged.addAll(other.bindings);
        return new ParamList<>(merged);
    }

    /** 只读声明视图，客户端无需持有参数对象或读取 getter。 */
    public List<ParamSpec<?>> all() {
        return specs;
    }

    /** 按名称查声明，未声明时返回 null。 */
    public @Nullable ParamSpec<?> get(String name) {
        ParamBinding<? super P, ?> binding = byName.get(name);
        return binding == null ? null : binding.spec();
    }

    /** 是否没有字段声明。 */
    public boolean isEmpty() {
        return bindings.isEmpty();
    }

    /** 执行全部单字段检查；不会因前一字段失败而跳过后续字段。 */
    public boolean check(P owner, IssueCollector issues, String path) {
        boolean valid = true;
        for (ParamBinding<? super P, ?> binding : bindings) {
            valid &= binding.check(owner, issues, path);
        }
        return valid;
    }

    /** 从本组绑定生成非空字段构件，不重复登记 getter。 */
    public <V> RecordCodecBuilder<P, V> field(ParamSpec<V> spec) {
        return binding(spec).field();
    }

    /** 从本组绑定生成可空字段构件。 */
    public <V> RecordCodecBuilder<P, Optional<V>> nullableField(ParamSpec<V> spec) {
        return binding(spec).nullableField();
    }

    // 构造时名称唯一，且校验为同一声明实例，因此该绑定的 V 与传入 spec 的 V 一致。
    @SuppressWarnings("unchecked")
    private <V> ParamBinding<P, V> binding(ParamSpec<V> spec) {
        ParamBinding<? super P, ?> binding = byName.get(spec.name());
        if (binding == null || binding.spec() != spec) {
            throw new IllegalArgumentException("参数未在本组绑定: " + spec.name());
        }
        return (ParamBinding<P, V>) binding;
    }

    /** 需要预写的新建初值；编码继续使用对应声明的 wire codec。 */
    public List<ParamValue> initialValues() {
        List<ParamValue> values = new ArrayList<>();
        for (ParamSpec<?> spec : specs) {
            if (spec.initialValue() != null) {
                values.add(new ParamValue(spec, spec.initialValue()));
            }
        }
        return values;
    }

    /** 新建初值及其来源声明。 */
    public record ParamValue(ParamSpec<?> spec, @Nullable Object value) {
        /** 初值写入的 JSON 字段名。 */
        public String name() { return spec.name(); }
    }
}
