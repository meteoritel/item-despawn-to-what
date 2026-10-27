package com.meteorite.itemdespawntowhat.client.edit.draft;

import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleEdit;
import org.jetbrains.annotations.Nullable;

/** 恢复的业务状态；值均为独立快照，content 缺席表示只承载启停意图。 */
public record RestoredDraft(String targetId, boolean created, boolean deleted,
                            @Nullable RuleEdit.Action enableIntent, @Nullable RuleValue baseline, @Nullable RuleValue content) {
}
