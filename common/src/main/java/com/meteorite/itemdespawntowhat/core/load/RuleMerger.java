package com.meteorite.itemdespawntowhat.core.load;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 覆盖合并：按 id 逐条合并三层来源的原始条目。
 * 语义：
 * 1. 不同 id 叠加；
 * 2. 同 id 后者胜（跨层顺序由层优先级决定：内置 → 世界数据包 → 覆盖层）。
 * 合并结果保留每条规则最终生效的来源标识；启用状态不参与合并，
 * 它属于本服状态层，在解码后按「本服覆盖 ?? 默认启用」注入运行时对象。
 */
public final class RuleMerger {

    private RuleMerger() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 合并原始条目：跨层按层优先级升序处理，返回结果保持 id 首次出现的顺序
    public static List<RawRuleEntry> merge(List<RawRuleEntry> entries, IssueCollector issues) {
        Map<ResourceLocation, RawRuleEntry> merged = new LinkedHashMap<>();
        for (RawRuleEntry entry : orderedEntries(entries)) {
            apply(entry, merged, issues);
        }
        return List.copyOf(merged.values());
    }

    // 按来源层分组（层优先级升序）：层内顺序即文件读取顺序，后读到的同 id 条目胜出
    static List<RawRuleEntry> orderedEntries(List<RawRuleEntry> entries) {
        Map<RuleSourceLayer, List<RawRuleEntry>> buckets = new TreeMap<>(Comparator.comparingInt(RuleSourceLayer::priority));
        for (RawRuleEntry entry : entries) {
            buckets.computeIfAbsent(entry.origin().layer(), layer -> new ArrayList<>()).add(entry);
        }
        List<RawRuleEntry> result = new ArrayList<>(entries.size());
        for (List<RawRuleEntry> bucket : buckets.values()) {
            result.addAll(bucket);
        }
        return result;
    }

    // 应用单条条目：同 id 后到者覆盖先到者，缺 id 的条目拒载
    private static void apply(RawRuleEntry entry, Map<ResourceLocation, RawRuleEntry> merged, IssueCollector issues) {
        ResourceLocation id = entry.id();
        if (id == null) {
            issues.error("规则缺少 id，已拒载", entry.origin().display(), entry.fieldPath());
            return;
        }
        merged.put(id, entry);
    }
}
