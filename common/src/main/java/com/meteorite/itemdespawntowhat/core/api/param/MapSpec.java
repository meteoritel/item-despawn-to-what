package com.meteorite.itemdespawntowhat.core.api.param;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.mojang.serialization.Codec;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 注册表引用到整数的映射参数声明（如按物品/标签指定消耗数量）。
 * <p>键的真实性由类型在 validateParams 里查 RefChecks；编辑器按 {@code keyRegistry} 取候选与展示名，
 * 值的区间在 codec 与声明校验中保持一致。</p>
 */
public record MapSpec(ParamMeta meta, String keyRegistry, int minValue, int maxValue) implements ParamSpec<Map<TaggedId, Integer>> {

    // 可省略映射：缺失为空表
    public static MapSpec defaultedEmpty(String name, String keyRegistry, int minValue, int maxValue) {
        return new MapSpec(ParamMeta.of(name, ParamPresence.DEFAULTED, false), keyRegistry, minValue, maxValue);
    }

    public MapSpec label(String suffix) {
        return withMeta(meta.withLabel(suffix));
    }

    /** 设置编辑器展示层级。 */
    public MapSpec tier(ParamTier value) { return withMeta(meta.withTier(value)); }

    public MapSpec hint(String suffix) {
        return withMeta(meta.withHint(suffix));
    }

    @Override
    public Codec<Map<TaggedId, Integer>> valueCodec() {
        return Codec.unboundedMap(TaggedId.CODEC, Codec.intRange(minValue, maxValue));
    }

    @Override
    public boolean check(@Nullable Map<TaggedId, Integer> value, IssueCollector issues, String fieldPath) {
        if (value == null) {
            return !required() || ParamChecks.required(null, name(), issues, ParamChecks.child(fieldPath, name()));
        }
        boolean valid = true;
        String path = ParamChecks.child(fieldPath, name());
        for (Map.Entry<TaggedId, Integer> entry : value.entrySet()) {
            valid &= ParamChecks.inRange(entry.getValue(), minValue, maxValue, name(), issues,
                    ParamChecks.child(path, entry.getKey().serialized()));
        }
        return valid;
    }

    @Override
    public Map<TaggedId, Integer> defaultValue() {
        return Map.of();
    }

    private MapSpec withMeta(ParamMeta value) {
        return new MapSpec(value, keyRegistry, minValue, maxValue);
    }
}
