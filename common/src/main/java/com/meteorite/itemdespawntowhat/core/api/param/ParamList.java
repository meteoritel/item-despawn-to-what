package com.meteorite.itemdespawntowhat.core.api.param;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个类型的有序参数声明集合。
 * <p>顺序即 codec 字段顺序与表单展示顺序；同一类型内参数名不得重复。</p>
 */
public final class ParamList {

    // 空声明：没有参数的类型（如 outdoor）与第三方类型共用
    public static final ParamList EMPTY = new ParamList(List.of());

    private final List<ParamSpec> specs;
    private final Map<String, ParamSpec> byName;

    private ParamList(List<ParamSpec> specs) {
        this.specs = List.copyOf(specs);
        Map<String, ParamSpec> index = new LinkedHashMap<>();
        for (ParamSpec spec : this.specs) {
            if (index.putIfAbsent(spec.name(), spec) != null) {
                throw new IllegalArgumentException("参数名重复: " + spec.name());
            }
        }
        this.byName = Map.copyOf(index);
    }

    // 按声明顺序构造参数集合
    public static ParamList of(ParamSpec... specs) {
        return specs.length == 0 ? EMPTY : new ParamList(List.of(specs));
    }

    // 追加另一组声明（用于公共字段拼接），保持追加在后的顺序
    public ParamList plus(ParamList other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        List<ParamSpec> merged = new ArrayList<>(specs);
        merged.addAll(other.specs);
        return new ParamList(merged);
    }

    // 全部声明（不可变、按声明顺序）
    public List<ParamSpec> all() {
        return specs;
    }

    // 按字段名取声明；未声明返回 null
    public @Nullable ParamSpec get(String name) {
        return byName.get(name);
    }

    // 是否没有参数
    public boolean isEmpty() {
        return specs.isEmpty();
    }

    // 新建规则需要预写的初值（只含有 initialValue 的参数，按声明顺序）
    public List<ParamValue> initialValues() {
        List<ParamValue> values = new ArrayList<>();
        for (ParamSpec spec : specs) {
            if (spec.initialValue() != null) {
                values.add(new ParamValue(spec, spec.initialValue()));
            }
        }
        return values;
    }

    /** 参数声明与取值：用于新建初值；编码时直接取声明自带的 wire codec。 */
    public record ParamValue(ParamSpec spec, @Nullable Object value) {

        /** 字段名，等同 {@code spec.name()}。 */
        public String name() {
            return spec.name();
        }
    }
}
