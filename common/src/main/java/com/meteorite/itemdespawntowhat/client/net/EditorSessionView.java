package com.meteorite.itemdespawntowhat.client.net;

import com.meteorite.itemdespawntowhat.core.service.edit.RuleSnapshot;
import org.jetbrains.annotations.Nullable;

/** 工作区当前会话的只读值快照；规则内容沿用已发布快照，消费者不得修改共享 JSON。 */
public record EditorSessionView(RuleEditClientState state, String sessionId, int contextRevision,
                                @Nullable EditorOpenRequest openRequest, @Nullable RuleSnapshot snapshot,
                                @Nullable String snapshotFailure, int referenceLifespanTicks) {
    /** 只有持有 ACTIVE 会话时才允许发起编辑操作。 */
    public boolean editable() { return state == RuleEditClientState.ACTIVE; }
}
