package com.meteorite.itemdespawntowhat.core.load;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则来源分层索引：把合并前的原始条目按 id 归并为 base（数据包层）/ overlay（覆盖层）两层视图。
 * 供快照装配器判定来源与内容可写性，并分别下发 base 与 overlay 视图。
 * 启用状态不在这里计算：它属于本服状态层，由快照装配时按「本服覆盖 ?? 默认启用」另行下发。
 * 本类不读文件、不解码、不校验，只做归并；层内"后者胜"的顺序语义与 RuleMerger 保持一致。
 */
public final class RuleSourceIndex {

    // 按 id 排序后的分层条目
    private final List<Entry> entries;
    private final Map<ResourceLocation, Entry> byId;

    private RuleSourceIndex(List<Entry> entries) {
        this.entries = entries;
        Map<ResourceLocation, Entry> lookup = new LinkedHashMap<>();
        entries.forEach(entry -> lookup.put(entry.id(), entry));
        byId = Map.copyOf(lookup);
    }

    // 单条规则的分层视图：base 为数据包层胜出条目，overlay 为覆盖层胜出条目
    public record Entry(ResourceLocation id,
                        @Nullable RawRuleEntry base,
                        @Nullable RawRuleEntry overlay) {

        // 是否携带可下发的规则内容
        public boolean hasBody() {
            return base != null || overlay != null;
        }

        // 覆盖层视图的原始 JSON（无覆盖层副本时为 null）
        public @Nullable com.google.gson.JsonObject overlayBody() {
            return overlay == null ? null : overlay.body();
        }

        // 数据包视图的原始 JSON（无数据包副本时为 null）
        public @Nullable com.google.gson.JsonObject baseBody() {
            return base == null ? null : base.body();
        }
    }

    /**
     * 按合并器相同的层优先级归并来源视图。
     *
     * @param rawEntries 合并前原始条目；同类条目保持输入优先级，null 视为空集合
     * @return 按规则 id 排序的来源索引
     */
    public static RuleSourceIndex build(List<RawRuleEntry> rawEntries) {
        Map<ResourceLocation, Builder> builders = new LinkedHashMap<>();
        List<RawRuleEntry> input = rawEntries == null ? List.of() : rawEntries;
        for (RawRuleEntry raw : RuleMerger.orderedEntries(input)) {
            ResourceLocation id = raw.id();
            if (id == null) {
                // 无法确定 id 的条目已被解析层报错，无法归并，直接忽略
                continue;
            }
            Builder builder = builders.computeIfAbsent(id, ignored -> new Builder());
            if (raw.origin().layer() == RuleSourceLayer.OVERLAY) {
                builder.overlay = raw;
            } else {
                builder.base = raw;
            }
        }

        List<Entry> entries = new ArrayList<>(builders.size());
        for (Map.Entry<ResourceLocation, Builder> bucket : builders.entrySet()) {
            Builder builder = bucket.getValue();
            if (builder.base == null && builder.overlay == null) {
                continue;
            }
            entries.add(new Entry(bucket.getKey(), builder.base, builder.overlay));
        }
        entries.sort(Comparator.comparing(entry -> entry.id().toString()));
        return new RuleSourceIndex(List.copyOf(entries));
    }

    /** 按 id 取得同一分层条目，未找到时为空。 */
    public @Nullable Entry find(ResourceLocation id) { return byId.get(id); }

    // 全部分层条目（按 id 排序）
    public List<Entry> entries() {
        return entries;
    }

    // 归并过程中的可变桶
    private static final class Builder {
        private @Nullable RawRuleEntry base;
        private @Nullable RawRuleEntry overlay;
    }
}
