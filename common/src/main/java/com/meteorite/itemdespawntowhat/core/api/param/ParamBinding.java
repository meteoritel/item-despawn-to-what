package com.meteorite.itemdespawntowhat.core.api.param;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;
import java.util.function.Function;

/**
 * 一个声明与其参数对象 getter 的绑定；编解码和校验从同一处读取字段。
 *
 * @param <P> 参数所有者类型
 * @param <V> 字段值类型
 * @param spec 单字段声明
 * @param getter 可直接读取 nullable 值的 getter
 */
public record ParamBinding<P, V>(ParamSpec<V> spec, Function<? super P, ? extends V> getter) {

    /** 生成非空字段构件，供所属记录的 RecordCodecBuilder 使用。 */
    public RecordCodecBuilder<P, V> field() {
        return spec.meta().field(spec.valueCodec(), spec.defaultValue()).forGetter(getter::apply);
    }

    /** 生成可空字段构件；codec 不产出 null。 */
    public RecordCodecBuilder<P, Optional<V>> nullableField() {
        return spec.meta().nullableField(spec.valueCodec()).forGetter(owner -> Optional.ofNullable(getter.apply(owner)));
    }

    /** 对所属对象执行一次单字段声明校验。 */
    public boolean check(P owner, IssueCollector issues, String path) {
        return spec.check(getter.apply(owner), issues, path);
    }
}
