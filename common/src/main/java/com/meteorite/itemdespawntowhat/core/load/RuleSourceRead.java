package com.meteorite.itemdespawntowhat.core.load;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 同批原始来源的读取产出：合并条目、分层索引与读取问题共同交接。 */
public record RuleSourceRead(List<RawRuleEntry> entries, RuleSourceIndex index, IssueCollector issues) {
    /** 合并已捕捉的数据包基底和当前覆盖层，保留可识别 id 的非法原始条目。 */
    public static RuleSourceRead read(List<RawRuleEntry> base, Path root, String namespace, IssueCollector issues) {
        List<RawRuleEntry> raw = new ArrayList<>(base);
        if (root != null) { raw.addAll(OverlayRuleReader.read(root, namespace, issues)); }
        return new RuleSourceRead(RuleMerger.merge(raw, issues), RuleSourceIndex.build(raw), issues);
    }
}
