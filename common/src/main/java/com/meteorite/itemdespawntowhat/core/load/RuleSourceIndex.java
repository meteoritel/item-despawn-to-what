package com.meteorite.itemdespawntowhat.core.load;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/***
 * 规则来源分层索引：把合并前的原始条目按 id 归并为 base（数据包层）/ overlay（覆盖层）/ control（停用·删除控制条目）三层视图。
 * 供快照装配器判定 origin（overlay|datapack|mixed）与 status（active|disabled|masked），并分别下发 base 与 overlay 视图。
 * 本类不读文件、不解码、不校验，只做归并；层内"后者胜"的顺序语义与 RuleMerger 保持一致。
 */
public final class RuleSourceIndex {

    // 按 id 排序后的分层条目
    private final List<Entry> entries;
    // id → 分层条目
    private final Map<ResourceLocation, Entry> byId;
    // 归并前的原始条目（顺序不变，用于问题定位）
    private final List<RawRuleEntry> rawEntries;

    private RuleSourceIndex(List<Entry> entries, Map<ResourceLocation, Entry> byId, List<RawRuleEntry> rawEntries) {
        this.entries = entries;
        this.byId = byId;
        this.rawEntries = rawEntries;
    }

    // 单条规则的分层视图：base 为数据包层胜出条目，overlay 为覆盖层胜出条目，control 为最后一次控制条目
    public record Entry(ResourceLocation id,
                        @Nullable RawRuleEntry base,
                        @Nullable RawRuleEntry overlay,
                        @Nullable RawRuleEntry control,
                        boolean controlWins,
                        boolean controlFromOverlay) {

        // 是否声明删除（且该控制条目仍是该 id 的最后一次声明）
        public boolean deleted() {
            return controlWins && control != null && control.delete();
        }

        // 是否声明停用（且该控制条目仍是该 id 的最后一次声明）
        public boolean disabled() {
            return controlWins && control != null && control.disabled();
        }

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

    // 归并原始条目：输入顺序必须是 RuleLoader.loadRaw 的输出顺序（层优先级升序），否则层内胜出者判定会错
    public static RuleSourceIndex build(List<RawRuleEntry> rawEntries) {
        Map<ResourceLocation, Builder> builders = new LinkedHashMap<>();
        List<RawRuleEntry> input = rawEntries == null ? List.of() : rawEntries;
        for (RawRuleEntry raw : input) {
            ResourceLocation id = raw.id();
            if (id == null) {
                // 无法确定 id 的条目已被解析层报错，无法归并，直接忽略
                continue;
            }
            Builder builder = builders.computeIfAbsent(id, ignored -> new Builder());
            boolean fromOverlay = raw.origin().layer() == RuleSourceLayer.OVERLAY;
            if (raw.isControl()) {
                builder.control = raw;
                builder.controlWins = true;
                builder.controlFromOverlay = fromOverlay;
            } else if (fromOverlay) {
                builder.overlay = raw;
                builder.controlWins = false;
            } else {
                builder.base = raw;
                builder.controlWins = false;
            }
        }

        List<Entry> entries = new ArrayList<>(builders.size());
        Map<ResourceLocation, Entry> byId = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Builder> bucket : builders.entrySet()) {
            Builder builder = bucket.getValue();
            if (builder.base == null && builder.overlay == null && builder.control == null) {
                continue;
            }
            Entry entry = new Entry(bucket.getKey(), builder.base, builder.overlay, builder.control,
                    builder.controlWins, builder.controlFromOverlay);
            entries.add(entry);
            byId.put(entry.id(), entry);
        }
        entries.sort(Comparator.comparing(entry -> entry.id().toString()));
        return new RuleSourceIndex(List.copyOf(entries), byId, List.copyOf(input));
    }

    // 空索引
    public static RuleSourceIndex empty() {
        return new RuleSourceIndex(List.of(), Map.of(), List.of());
    }

    // 全部分层条目（按 id 排序）
    public List<Entry> entries() {
        return entries;
    }

    // 按 id 取分层条目
    public @Nullable Entry byId(ResourceLocation id) {
        return id == null ? null : byId.get(id);
    }

    // 归并前的原始条目
    public List<RawRuleEntry> rawEntries() {
        return rawEntries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    // 归并过程中的可变桶
    private static final class Builder {
        private @Nullable RawRuleEntry base;
        private @Nullable RawRuleEntry overlay;
        private @Nullable RawRuleEntry control;
        private boolean controlWins;
        private boolean controlFromOverlay;
    }
}
