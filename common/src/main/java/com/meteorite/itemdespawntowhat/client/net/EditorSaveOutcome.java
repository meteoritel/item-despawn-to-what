package com.meteorite.itemdespawntowhat.client.net;

import com.meteorite.itemdespawntowhat.core.service.edit.RuleIssue;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleSaveStatus;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** 已接纳保存终态；sequence 只随匹配的新回执递增，空状态表示当前没有终态。 */
public record EditorSaveOutcome(long sequence, @Nullable RuleSaveStatus status, @Nullable String messageCode,
                                List<String> messageArgs, int resultVersion, boolean writtenToDisk,
                                boolean reloaded, List<RuleIssue> issues) {
    /** 集合在发布时固定，读取不依赖线上 payload。 */
    public EditorSaveOutcome {
        messageArgs = List.copyOf(messageArgs);
        issues = List.copyOf(issues);
    }

    static EditorSaveOutcome empty(long sequence) {
        return new EditorSaveOutcome(sequence, null, null, List.of(), -1, false, false, List.of());
    }
}
