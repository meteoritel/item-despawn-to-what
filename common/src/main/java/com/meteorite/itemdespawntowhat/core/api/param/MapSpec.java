package com.meteorite.itemdespawntowhat.core.api.param;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;

/**
 * 注册表引用到整数的映射参数声明（如按物品/标签指定消耗数量）。
 * <p>键的真实性由类型在 validateParams 里查 RefChecks；值的区间在解码期即生效。</p>
 */
public record MapSpec(ParamMeta meta, String keyRegistry, int minValue, int maxValue) implements ParamSpec {

    // 可省略映射：缺失为空表
    public static MapSpec defaultedEmpty(String name, String keyRegistry, int minValue, int maxValue) {
        return new MapSpec(ParamMeta.of(name, ParamPresence.DEFAULTED, false), keyRegistry, minValue, maxValue);
    }

    public MapSpec label(String suffix) {
        return withMeta(meta.withLabel(suffix));
    }

    public MapSpec hint(String suffix) {
        return withMeta(meta.withHint(suffix));
    }

    public <O> RecordCodecBuilder<O, Map<TaggedId, Integer>> fieldFor(
            Function<O, Map<TaggedId, Integer>> getter) {
        Codec<Map<TaggedId, Integer>> codec = Codec.unboundedMap(TaggedId.CODEC, Codec.intRange(minValue, maxValue));
        return codec.optionalFieldOf(name(), Map.of()).forGetter(getter);
    }

    @Override
    public boolean check(@Nullable Object value, IssueCollector issues, String fieldPath) {
        return true;
    }

    @Override
    public @Nullable Object defaultValue() {
        return Map.of();
    }

    private MapSpec withMeta(ParamMeta value) {
        return new MapSpec(value, keyRegistry, minValue, maxValue);
    }
}
