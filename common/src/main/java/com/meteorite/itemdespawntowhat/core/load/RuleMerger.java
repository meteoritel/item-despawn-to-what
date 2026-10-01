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
 * 语义（依据规划书 3.2 / Q11）：
 * 1. 不同 id 叠加；
 * 2. 同 id 后者胜（跨层顺序由层优先级决定：内置 → 世界数据包 → 覆盖层）；
 * 3. 覆盖层的 disabled=true 停用同 id 基底规则（保留基底内容，标记为已停用，最终来源记为覆盖层）；
 * 4. 覆盖层的 delete=true 删除同 id 基底规则（幂等，基底不存在时不做任何事）。
 * 同一来源层内一律先应用普通规则条目、再应用控制条目（disabled/delete），
 * 因此停用/删除语义不依赖文件名的字典序；合并结果保留每条规则最终生效的来源标识。
 */
public final class RuleMerger {

    private RuleMerger() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 合并原始条目：跨层按层优先级升序处理，层内先普通条目后控制条目，返回结果保持 id 首次出现的顺序
    public static List<RawRuleEntry> merge(List<RawRuleEntry> entries, IssueCollector issues) {
        Map<ResourceLocation, RawRuleEntry> merged = new LinkedHashMap<>();
        for (List<RawRuleEntry> layerEntries : groupByLayerThenControls(entries)) {
            for (RawRuleEntry entry : layerEntries) {
                apply(entry, merged, issues);
            }
        }
        return List.copyOf(merged.values());
    }

    // 按来源层分组（层优先级升序），层内稳定重排为普通条目在前、控制条目在后
    private static List<List<RawRuleEntry>> groupByLayerThenControls(List<RawRuleEntry> entries) {
        Map<RuleSourceLayer, List<RawRuleEntry>> buckets = new TreeMap<>(Comparator.comparingInt(RuleSourceLayer::priority));
        for (RawRuleEntry entry : entries) {
            buckets.computeIfAbsent(entry.origin().layer(), layer -> new ArrayList<>()).add(entry);
        }
        List<List<RawRuleEntry>> result = new ArrayList<>(buckets.size());
        for (List<RawRuleEntry> bucket : buckets.values()) {
            List<RawRuleEntry> normals = new ArrayList<>(bucket.size());
            List<RawRuleEntry> controls = new ArrayList<>();
            for (RawRuleEntry entry : bucket) {
                (entry.isControl() ? controls : normals).add(entry);
            }
            normals.addAll(controls);
            result.add(normals);
        }
        return result;
    }

    // 应用单条条目：普通条目覆盖，delete 移除，disabled 停用同 id 基底规则
    private static void apply(RawRuleEntry entry, Map<ResourceLocation, RawRuleEntry> merged, IssueCollector issues) {
        ResourceLocation id = entry.id();
        if (id == null) {
            issues.error("规则缺少 id，已拒载", entry.origin().display(), entry.fieldPath());
            return;
        }
        if (entry.delete()) {
            merged.remove(id);
            return;
        }
        if (entry.disabled()) {
            RawRuleEntry base = merged.get(id);
            if (base == null) {
                issues.warn("disabled 声明找不到同 id 的基底规则，已忽略",
                        entry.origin().display(), entry.fieldPath());
                return;
            }
            // 保留基底内容，但最终状态与来源以控制条目为准
            merged.put(id, new RawRuleEntry(id, base.body(), entry.origin(), true, false, entry.fieldPath()));
            return;
        }
        merged.put(id, entry);
    }
}
