package com.meteorite.itemdespawntowhat.client.edit.draft;

import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleEdit;
import org.jetbrains.annotations.Nullable;

/**
 * 主线程上的待保留状态；内容可缺席，只读沙盒与本服启停意图分别表达。
 * <p>Journal 同步判断保留策略并捕获快照，后台写入者不持有本对象或实时草稿。
 */
public record DraftState(String targetId, @Nullable EditSession content, boolean created,
                         boolean readOnly, boolean unresolvedConflict, @Nullable RuleEdit.Action enableIntent) {
}
