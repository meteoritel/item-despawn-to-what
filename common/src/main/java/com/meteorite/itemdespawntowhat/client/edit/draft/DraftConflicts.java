package com.meteorite.itemdespawntowhat.client.edit.draft;

import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import org.jetbrains.annotations.Nullable;

/** 草稿恢复时的整条规则冲突分类，不修改草稿、基线或界面状态。 */
public final class DraftConflicts {
    private DraftConflicts() {
    }

    /** 只读条目只恢复启停意图；可写条目按创建意图、目标存在性和原始基线分类。 */
    public static @Nullable DraftConflict classify(RestoredDraft draft, @Nullable RuleValue serverBody, boolean readOnly) {
        if (readOnly || draft.content() == null) {
            return null;
        }
        DraftConflict.Reason reason;
        if (draft.created() && serverBody != null) {
            reason = DraftConflict.Reason.TARGET_EXISTS;
        } else if (!draft.created() && serverBody == null) {
            reason = DraftConflict.Reason.TARGET_MISSING;
        } else if (serverBody != null && (draft.baseline() == null || !draft.baseline().equals(serverBody))) {
            reason = DraftConflict.Reason.REMOTE_CHANGED;
        } else {
            return null;
        }
        return new DraftConflict(draft.targetId(), reason);
    }
}
