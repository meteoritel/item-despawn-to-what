package com.meteorite.itemdespawntowhat.core.network.transport;

import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEditProtocol;
import com.meteorite.itemdespawntowhat.core.service.RuleEditService;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleEditRequest;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleEditReply;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleSaveStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/** 无状态网络入口：把已解码帧映射为请求，交给当前服务端的业务与传输实例。 */
public final class RuleEditServerHandler {
    private RuleEditServerHandler() { }

    /** 当前上下文的业务能力是否可用。 */
    public static boolean isReady(@Nullable RuleEditServerContext context) {
        return context != null && context.editService() != null && context.transport() != null
                && context.overlayRoot() != null && context.typeRegistries() != null;
    }

    /** 握手版本属于传输契约，校验成功后才确认业务会话。 */
    public static void handleConfirm(ServerPlayer player, RuleEditServerContext context, ConfirmRuleEditorPayload payload) {
        if (payload.protocolVersion() != RuleEditProtocol.VERSION) {
            context.sendTo(player, new RuleSessionEventPayload(payload.sessionId(), RuleSaveStatus.INVALID_REQUEST.id()));
            return;
        }
        handle(player, context, new RuleEditRequest.Confirm(payload.sessionId()));
    }

    /** 将 HeartbeatRuleEditorPayload 转交业务请求入口。 */
    public static void handleHeartbeat(ServerPlayer player, RuleEditServerContext context, HeartbeatRuleEditorPayload payload) {
        handle(player, context, new RuleEditRequest.Heartbeat(payload.sessionId()));
    }

    /** 将 CloseRuleEditorPayload 转交业务请求入口。 */
    public static void handleClose(ServerPlayer player, RuleEditServerContext context, CloseRuleEditorPayload payload) {
        handle(player, context, new RuleEditRequest.Close(payload.sessionId()));
    }

    /** 将 RequestRuleSnapshotPayload 转交业务请求入口。 */
    public static void handleSnapshotRequest(ServerPlayer player, RuleEditServerContext context, RequestRuleSnapshotPayload payload) {
        handle(player, context, new RuleEditRequest.Snapshot(payload.sessionId(), payload.requestId()));
    }

    /** 将 RequestRuleCatalogPayload 转交业务请求入口。 */
    public static void handleCatalogRequest(ServerPlayer player, RuleEditServerContext context, RequestRuleCatalogPayload payload) {
        handle(player, context, new RuleEditRequest.Catalog(payload.sessionId(), payload.requestId(), payload.catalogType(), payload.filter(), payload.page(), payload.pageSize()));
    }

    /** 将 RequestRuleSourceViewPayload 转交业务请求入口。 */
    public static void handleSourceViewRequest(ServerPlayer player, RuleEditServerContext context, RequestRuleSourceViewPayload payload) {
        handle(player, context, new RuleEditRequest.SourceView(payload.sessionId(), payload.requestId(), payload.ruleId()));
    }

    /** 将 RequestRuleLifespanPayload 转交业务请求入口。 */
    public static void handleLifespanRequest(ServerPlayer player, RuleEditServerContext context, RequestRuleLifespanPayload payload) {
        handle(player, context, new RuleEditRequest.Lifespan(payload.sessionId(), payload.requestId()));
    }

    /** 将 CancelRuleSavePayload 转交业务请求入口。 */
    public static void handleCancelSave(ServerPlayer player, RuleEditServerContext context, CancelRuleSavePayload payload) {
        RuleEditTransport transport = context.transport();
        if (transport != null) { transport.cancel(player, context, new RuleEditRequest.Cancel(payload.sessionId(), payload.operationId())); }
        else { handle(player, context, new RuleEditRequest.Cancel(payload.sessionId(), payload.operationId())); }
    }

    /** 直发文本经过通道限额检查后进入保存。 */
    public static void handleChangeSet(ServerPlayer player, RuleEditServerContext context, SaveRuleChangeSetPayload payload) {
        if (rejectUnavailableSave(player, context, payload.sessionId(), payload.operationId())) { return; }
        java.util.Objects.requireNonNull(context.transport()).save(player, context, payload.sessionId(), payload.operationId(), payload::changeSetJson);
    }

    /** 分片在当前服务端的传输容器重组。 */
    public static void handleChangeSetChunk(ServerPlayer player, RuleEditServerContext context, RuleChunkPayload payload) {
        if (rejectUnavailableSave(player, context, payload.sessionId(), payload.correlationId())) { return; }
        java.util.Objects.requireNonNull(context.transport()).chunk(player, context, payload);
    }

    private static boolean rejectUnavailableSave(ServerPlayer player, RuleEditServerContext context, String sessionId, String operationId) {
        if (isReady(context)) { return false; }
        RuleEditService service = context.editService();
        if (service != null && service.abortReplayedSave(player, sessionId, operationId)) { return true; }
        context.sendTo(player, RuleSaveResultPayload.of(sessionId, operationId, RuleSaveStatus.UNAVAILABLE,
                RuleEditMessages.key(RuleSaveStatus.UNAVAILABLE), java.util.List.of(), 0, false, false, java.util.List.of()));
        return true;
    }

    private static void handle(ServerPlayer player, RuleEditServerContext context, RuleEditRequest request) {
        if (!isReady(context)) {
            RuleEditReply refusal = switch (request) {
                case RuleEditRequest.Snapshot value -> new RuleEditReply.QueryResult(RuleEditRequest.Kind.SNAPSHOT, value.sessionId(), value.requestId(), RuleSaveStatus.UNAVAILABLE);
                case RuleEditRequest.Catalog value -> new RuleEditReply.QueryResult(RuleEditRequest.Kind.CATALOG, value.sessionId(), value.requestId(), RuleSaveStatus.UNAVAILABLE);
                case RuleEditRequest.SourceView value -> new RuleEditReply.QueryResult(RuleEditRequest.Kind.SOURCE_VIEW, value.sessionId(), value.requestId(), RuleSaveStatus.UNAVAILABLE);
                case RuleEditRequest.Lifespan value -> new RuleEditReply.QueryResult(RuleEditRequest.Kind.LIFESPAN, value.sessionId(), value.requestId(), RuleSaveStatus.UNAVAILABLE);
                case RuleEditRequest.Cancel value -> new RuleEditReply.Save(value.sessionId(), value.operationId(), RuleSaveStatus.UNAVAILABLE, java.util.List.of(), 0, false, false, java.util.List.of());
                default -> new RuleEditReply.SessionEvent(request.sessionId(), RuleSaveStatus.UNAVAILABLE);
            };
            switch (refusal) {
                case RuleEditReply.QueryResult(var kind, var session, var requestId, var status) ->
                        context.sendTo(player, new RuleQueryResultPayload(kind.name(), session, requestId, status.id()));
                case RuleEditReply.Save(var session, var operation, var status, var args, var version, var written, var reloaded, var issues) ->
                        context.sendTo(player, RuleSaveResultPayload.of(session, operation, status, RuleEditMessages.key(status), args, version, written, reloaded, issues));
                default -> context.sendTo(player, new RuleSessionEventPayload(request.sessionId(), RuleSaveStatus.UNAVAILABLE.id()));
            }
            return;
        }
        java.util.Objects.requireNonNull(context.transport()).send(context, player, java.util.Objects.requireNonNull(context.editService()).handle(player, context, player.getServer(), request, tick(player)));
    }

    /** 推进当前宿主的业务完成与传输交付。 */
    public static void tick(MinecraftServer server, RuleEditServerContext context) {
        RuleEditTransport transport = context.transport();
        if (transport != null) { transport.tick(server, context); }
    }

    /** 检查本次服务端的会话与分片租约。 */
    public static void expireIdle(MinecraftServer server, RuleEditServerContext context) {
        RuleEditTransport transport = context.transport();
        if (transport != null) { transport.expire(server, context); }
    }

    /** 清理当前服务端的玩家断开状态。 */
    public static void clearPlayer(UUID owner, RuleEditServerContext context) {
        RuleEditTransport transport = context.transport();
        if (transport != null) { transport.clearPlayer(owner); }
    }

    private static long tick(ServerPlayer player) { return java.util.Objects.requireNonNull(player.getServer()).getTickCount(); }
}
