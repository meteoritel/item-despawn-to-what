package com.meteorite.itemdespawntowhat.client.net;

import com.meteorite.itemdespawntowhat.core.service.RuleEditSessionLimits;
import org.jetbrains.annotations.Nullable;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleSnapshot;
import java.util.Objects;

/** 客户端独占会话的身份、阶段与心跳；由工作区在客户端线程推进。 */
final class SessionLease {
    private RuleEditClientState state = RuleEditClientState.FREE;
    private String id = "";
    private int revision;
    private @Nullable EditorOpenRequest request;
    private long heartbeatTick;
    private boolean invalidated;
    private @Nullable EditorSessionView published;

    RuleEditClientState state() { return state; }
    String id() { return id; }
    boolean invalidated() { return invalidated; }
    boolean held() { return !id.isEmpty(); }
    boolean matches(String sessionId) { return held() && id.equals(sessionId); }
    boolean canQuery(boolean connected) { return state == RuleEditClientState.ACTIVE && held() && connected && !invalidated; }

    void enter(EditorOpenRequest open, long tick) {
        id = open.sessionId();
        revision = open.contextRevision();
        request = open;
        state = RuleEditClientState.OPENING;
        heartbeatTick = tick;
        invalidated = false;
    }

    void activate() { state = RuleEditClientState.ACTIVE; }
    void beginSave() { state = RuleEditClientState.APPLYING; }
    void revision(int value) { revision = value; }
    void invalidate() { invalidated = true; }

    boolean heartbeatDue(long tick) {
        if (!held() || tick - heartbeatTick < RuleEditSessionLimits.HEARTBEAT_INTERVAL_TICKS) return false;
        heartbeatTick = tick;
        return true;
    }

    EditorSessionView view(@Nullable RuleSnapshot snapshot, @Nullable String failure, int lifespan) {
        if (published == null || published.state() != state || !published.sessionId().equals(id)
                || published.contextRevision() != revision || published.openRequest() != request
                || published.snapshot() != snapshot || !Objects.equals(published.snapshotFailure(), failure)
                || published.referenceLifespanTicks() != lifespan) {
            published = new EditorSessionView(state, id, revision, request, snapshot, failure, lifespan);
        }
        return published;
    }

    void end() {
        state = RuleEditClientState.FREE;
        id = "";
        revision = 0;
        request = null;
        heartbeatTick = 0;
        invalidated = false;
    }
}
