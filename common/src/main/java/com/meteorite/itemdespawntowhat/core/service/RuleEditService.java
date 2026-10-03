package com.meteorite.itemdespawntowhat.core.service;

import com.meteorite.itemdespawntowhat.core.api.Issue;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.IssueSeverity;
import com.meteorite.itemdespawntowhat.core.load.PackLayerResolver;
import com.meteorite.itemdespawntowhat.core.load.RuleLoadResult;
import com.meteorite.itemdespawntowhat.core.load.RuleSourceIndex;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.network.catalog.RuleCatalogService;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalog;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEditChangeSet;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEditProtocol;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleIssue;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSaveStatus;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshot;
import com.meteorite.itemdespawntowhat.core.network.transport.CloseRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.ConfirmRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.HeartbeatRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.OpenRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RequestRuleCatalogPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RequestRuleSnapshotPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleCatalogPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditChunkAccumulator;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditLimits;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerContext;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSaveResultPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSnapshotChunkPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSnapshotChunker;
import com.meteorite.itemdespawntowhat.core.network.transport.SaveRuleChangeSetChunkPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.SaveRuleChangeSetPayload;
import com.mojang.serialization.DataResult;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 独占编辑会话的业务编排（契约 §3）：打开/确认/心跳/关闭/快照/目录/保存。
 * 本类只产出"要下发的载荷"，不直接触碰网络；平台 registrar 与命令层负责发送。
 * 所有授权检查点都校验 sessionId 与持有者，失败回 SESSION_EXPIRED / LOCK_NOT_OWNED。
 * 时钟一律使用服务端活动 tick，由调用方（tick 事件）传入。
 */
public final class RuleEditService {

    private static final Logger LOGGER = LogManager.getLogger();

    // 会话状态机与版本戳
    private final EditSessionManager sessions;
    // 幂等回执：同一会话最近 MAX_OPERATION_HISTORY 个 operationId -> 上次回执
    private final Map<String, RuleSaveResultPayload> recentResults = new LinkedHashMap<>();
    // 幂等表所属会话；会话变化即整体失效
    private String resultsSessionId;

    public RuleEditService(EditSessionManager sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    // 底层的会话状态机（命令层查询状态用）
    public EditSessionManager sessions() {
        return sessions;
    }

    /***
     * 一次请求要下发的载荷集合。
     */
    public record Outbound(List<CustomPacketPayload> packets) {

        public static final Outbound EMPTY = new Outbound(List.of());

        public static Outbound of(CustomPacketPayload... packets) {
            return new Outbound(List.of(packets));
        }

        public static Outbound of(List<CustomPacketPayload> packets) {
            return new Outbound(List.copyOf(packets));
        }
    }

    /***
     * 打开结果：成功时 acquired=true，失败时携带当前持有者名字。
     */
    public record OpenOutcome(boolean acquired, String holderName, OpenRuleEditorPayload payload) {
    }

    /***
     * 超时释放结果：target 为需要被通知的玩家（无人可通知时为 null），outbound 为要下发的载荷。
     */
    public record Expired(@Nullable ServerPlayer target, Outbound outbound) {
    }

    // 「打开编辑器」唯一入口：先原子取锁，成功或失败都要把结论下发给客户端
    public synchronized OpenOutcome openEditor(ServerPlayer player, long tick) {
        EditSessionManager.AcquireResult acquired =
                sessions.acquire(player.getUUID(), player.getName().getString(), tick);
        if (acquired.acquired()) {
            OpenRuleEditorPayload payload = new OpenRuleEditorPayload(
                    acquired.session().sessionId(),
                    RuleEditSessionLimits.TARGET_ID,
                    RuleEditProtocol.VERSION,
                    sessions.version(),
                    RuleSaveStatus.SUCCESS.id(),
                    List.of(),
                    "Editor opened.");
            return new OpenOutcome(true, null, payload);
        }
        String holder = acquired.holderName() == null ? "" : acquired.holderName();
        OpenRuleEditorPayload payload = new OpenRuleEditorPayload(
                "",
                RuleEditSessionLimits.TARGET_ID,
                RuleEditProtocol.VERSION,
                sessions.version(),
                RuleSaveStatus.LOCK_BUSY.id(),
                args(holder),
                "Rules are being edited by " + holder + ".");
        return new OpenOutcome(false, holder, payload);
    }

    // C2S 确认：校验协议版本与会话，成功后进入 ACTIVE 并回发最新快照
    public Outbound confirm(ServerPlayer player, RuleEditServerContext context, MinecraftServer server,
                            ConfirmRuleEditorPayload payload, long tick) {
        if (payload.protocolVersion() != RuleEditProtocol.VERSION) {
            return Outbound.of(result(payload.sessionId(), "", RuleSaveStatus.INVALID_REQUEST,
                    "itemdespawntowhat.edit.protocol_mismatch",
                    args(Integer.toString(RuleEditProtocol.VERSION), Integer.toString(payload.protocolVersion())),
                    List.of(), false, false));
        }
        if (!sessions.owns(payload.sessionId())) {
            return Outbound.of(sessionLost(payload.sessionId(), player));
        }
        if (!sessions.isOwner(player.getUUID())) {
            return Outbound.of(result(payload.sessionId(), "", RuleSaveStatus.LOCK_NOT_OWNED,
                    RuleSaveStatus.LOCK_NOT_OWNED.messageKey(), List.of(), List.of(), false, false));
        }
        if (!sessions.confirm(payload.sessionId(), tick)) {
            return Outbound.of(sessionLost(payload.sessionId(), player));
        }
        return snapshotOutbound(context, server, payload.sessionId(), "");
    }

    // C2S 心跳：成功不回执，失败回 SESSION_EXPIRED（客户端据此回到未持有状态）
    public Outbound heartbeat(ServerPlayer player, HeartbeatRuleEditorPayload payload, long tick) {
        if (sessions.heartbeat(payload.sessionId(), tick) && sessions.isOwner(player.getUUID())) {
            return Outbound.EMPTY;
        }
        return Outbound.of(sessionLost(payload.sessionId(), player));
    }

    // C2S 关闭：持有者主动释放，回执后会话立即变 FREE
    public Outbound close(ServerPlayer player, CloseRuleEditorPayload payload) {
        if (!sessions.owns(payload.sessionId()) || !sessions.isOwner(player.getUUID())) {
            return Outbound.of(sessionLost(payload.sessionId(), player));
        }
        sessions.release();
        return Outbound.of(result(payload.sessionId(), "", RuleSaveStatus.SUCCESS,
                RuleSaveStatus.SUCCESS.messageKey(), List.of(), List.of(), false, false));
    }

    // C2S 快照请求：必须是当前会话持有者
    public Outbound requestSnapshot(ServerPlayer player, RuleEditServerContext context, MinecraftServer server,
                                    RequestRuleSnapshotPayload payload, long tick) {
        if (!sessions.owns(payload.sessionId()) || !sessions.isOwner(player.getUUID())) {
            return Outbound.of(sessionLost(payload.sessionId(), player));
        }
        if (!sessions.confirmed(payload.sessionId())) {
            // 未确认的 OPENING 会话不接受快照请求，避免绕过确认握手
            return Outbound.of(sessionLost(payload.sessionId(), player));
        }
        sessions.heartbeat(payload.sessionId(), tick);
        return snapshotOutbound(context, server, payload.sessionId(), payload.requestId());
    }

    // C2S 目录请求：按来源分层缓存分页下发；来源实现为空时返回空目录 + lastPage=true
    public Outbound requestCatalog(ServerPlayer player, RequestRuleCatalogPayload payload, long tick) {
        if (!sessions.owns(payload.sessionId()) || !sessions.isOwner(player.getUUID())) {
            return Outbound.of(sessionLost(payload.sessionId(), player));
        }
        if (payload.page() < 0 || payload.pageSize() <= 0
                || payload.pageSize() > RequestRuleCatalogPayload.MAX_PAGE_SIZE) {
            return Outbound.of(result(payload.sessionId(), "", RuleSaveStatus.INVALID_REQUEST,
                    RuleSaveStatus.INVALID_REQUEST.messageKey(),
                    args("page=" + payload.page() + ", pageSize=" + payload.pageSize()),
                    List.of(), false, false));
        }
        if (!sessions.confirmed(payload.sessionId())) {
            // 未确认的 OPENING 会话不接受目录请求
            return Outbound.of(sessionLost(payload.sessionId(), player));
        }
        RuleCatalogType type = RuleCatalogType.fromId(payload.catalogType());
        if (type == null) {
            return Outbound.of(result(payload.sessionId(), "", RuleSaveStatus.INVALID_REQUEST,
                    RuleSaveStatus.INVALID_REQUEST.messageKey(),
                    args(payload.catalogType()), List.of(), false, false));
        }
        sessions.heartbeat(payload.sessionId(), tick);
        RuleCatalog catalog = RuleCatalogService.page(player.getServer(), type, payload.filter(),
                payload.page(), payload.pageSize());
        return Outbound.of(new RuleCatalogPayload(payload.sessionId(), payload.requestId(),
                type.id(), catalog.revision(), catalog.serialize(), catalog.lastPage()));
    }

    // C2S 变更集：完整的保存编排（幂等 → 会话/权限 → 版本戳 → 校验 → 落盘 → 重载 → 回执 + 新快照）
    public Outbound submitChangeSet(ServerPlayer player, RuleEditServerContext context, MinecraftServer server,
                                    SaveRuleChangeSetPayload payload, long tick) {
        return submit(player, context, server, payload.sessionId(), payload.operationId(),
                payload.changeSetJson(), tick);
    }

    // C2S 分片：先校验会话，再重组；收齐后复用直发保存流程
    public Outbound submitChunk(ServerPlayer player, RuleEditServerContext context, MinecraftServer server,
                                SaveRuleChangeSetChunkPayload payload, long tick) {
        if (!sessions.owns(payload.sessionId()) || !sessions.isOwner(player.getUUID())) {
            return Outbound.of(sessionLost(payload.sessionId(), player));
        }
        if (!RuleEditChunkAccumulator.isShapeValid(payload)) {
            return Outbound.of(result(payload.sessionId(), payload.operationId(), RuleSaveStatus.INVALID_REQUEST,
                    RuleSaveStatus.INVALID_REQUEST.messageKey(), args("chunk"), List.of(), false, false));
        }
        sessions.heartbeat(payload.sessionId(), tick);
        RuleEditChunkAccumulator.AcceptResult accepted = RuleEditChunkAccumulator.accept(player, payload, tick);
        if (accepted.rejected()) {
            // 收齐后超过变更集总量上限：必须回结构化回执，不能让请求静默消失
            return Outbound.of(result(payload.sessionId(), payload.operationId(), RuleSaveStatus.INVALID_REQUEST,
                    RuleSaveStatus.INVALID_REQUEST.messageKey(), args("change set too large"),
                    List.of(), false, false));
        }
        if (!accepted.complete()) {
            // 未收齐：不回执，等待剩余分片或由客户端超时处理
            return Outbound.EMPTY;
        }
        return submit(player, context, server, payload.sessionId(), payload.operationId(), accepted.json(), tick);
    }

    // 保存主流程
    private synchronized Outbound submit(ServerPlayer player, RuleEditServerContext context, MinecraftServer server,
                                         String rawSessionId, String rawOperationId, String json, long tick) {
        String sessionId = rawSessionId == null ? "" : rawSessionId;
        String operationId = rawOperationId == null ? "" : rawOperationId;
        String body = json == null ? "" : json;
        if (operationId.isEmpty() || RuleEditLimits.encodedLength(operationId) > RuleEditLimits.MAX_ID_CHARS) {
            return Outbound.of(result(sessionId, operationId, RuleSaveStatus.INVALID_REQUEST,
                    RuleSaveStatus.INVALID_REQUEST.messageKey(), args("operationId"), List.of(), false, false));
        }
        if (RuleEditLimits.encodedLength(body) > RuleEditLimits.MAX_CHANGE_SET_BYTES) {
            return Outbound.of(result(sessionId, operationId, RuleSaveStatus.INVALID_REQUEST,
                    RuleSaveStatus.INVALID_REQUEST.messageKey(), args("change set too large"), List.of(), false, false));
        }
        if (!sessions.owns(sessionId)) {
            return Outbound.of(sessionLost(sessionId, player));
        }
        if (!sessions.isOwner(player.getUUID())) {
            return Outbound.of(result(sessionId, operationId, RuleSaveStatus.LOCK_NOT_OWNED,
                    RuleSaveStatus.LOCK_NOT_OWNED.messageKey(), List.of(), List.of(), false, false));
        }

        // 幂等：同一 operationId 直接回放上次回执，不重复写盘
        RuleSaveResultPayload previous = recentResult(sessionId, operationId);
        if (previous != null) {
            return Outbound.of(previous);
        }

        DataResult<RuleEditChangeSet> parsed = RuleEditChangeSet.parse(body);
        RuleEditChangeSet changeSet = parsed.result().orElse(null);
        if (changeSet == null) {
            String detail = parsed.error().map(DataResult.Error::message).orElse("unknown");
            RuleSaveResultPayload failed = result(sessionId, operationId, RuleSaveStatus.INVALID_REQUEST,
                    "itemdespawntowhat.edit.parse_failed", args(detail), List.of(), false, false);
            remember(failed);
            return Outbound.of(failed);
        }

        if (!sessions.beginApply(sessionId)) {
            EditSessionManager.SessionInfo current = sessions.session();
            if (current != null && sessionId.equals(current.sessionId())
                    && current.state() == EditSessionManager.SessionState.APPLYING) {
                // 正在应用上一批变更：拒绝新提交但保留会话，避免客户端误判失效而关闭界面
                return Outbound.of(result(sessionId, operationId, RuleSaveStatus.INVALID_REQUEST,
                        RuleSaveStatus.INVALID_REQUEST.messageKey(), args("apply in progress"),
                        List.of(), false, false));
            }
            return Outbound.of(sessionLost(sessionId, player));
        }
        RuleSaveResultPayload outcome;
        try {
            outcome = applyChangeSet(context, server, sessionId, operationId, changeSet);
        } finally {
            sessions.finishApply(sessionId, tick);
        }
        remember(outcome);

        // 版本冲突、会话失效等场景都要让客户端拿到最新快照
        if (RuleSaveStatus.VERSION_CONFLICT.id().equals(outcome.statusCode())
                || RuleSaveStatus.SAVED_NOT_RELOADED.id().equals(outcome.statusCode())) {
            List<CustomPacketPayload> packets = new ArrayList<>();
            packets.add(outcome);
            packets.addAll(snapshotPackets(context, server, sessionId, ""));
            return Outbound.of(List.copyOf(packets));
        }
        return Outbound.of(outcome);
    }

    // 落盘与重载：任何异常都不抛出到网络层，统一转成结构化回执
    private RuleSaveResultPayload applyChangeSet(RuleEditServerContext context, MinecraftServer server,
                                                 String sessionId, String operationId, RuleEditChangeSet changeSet) {
        if (changeSet.isEmpty()) {
            return result(sessionId, operationId, RuleSaveStatus.NO_CHANGES,
                    RuleSaveStatus.NO_CHANGES.messageKey(), List.of(), List.of(), false, false);
        }
        try {
            sessions.synchronizeDiskRevision();
        } catch (RuntimeException failure) {
            LOGGER.error("规则修订核对失败", failure);
            return result(sessionId, operationId, RuleSaveStatus.WRITE_FAILED,
                    RuleSaveStatus.WRITE_FAILED.messageKey(), args(describe(failure)), List.of(), false, false);
        }
        if (!versionMatches(changeSet)) {
            return result(sessionId, operationId, RuleSaveStatus.VERSION_CONFLICT,
                    RuleSaveStatus.VERSION_CONFLICT.messageKey(),
                    args(Integer.toString(sessions.version()), Integer.toString(changeSet.expectedVersion())),
                    List.of(), false, false);
        }

        IssueCollector issues = new IssueCollector();
        var registries = context.typeRegistries();
        if (registries == null) {
            return result(sessionId, operationId, RuleSaveStatus.UNAVAILABLE,
                    RuleSaveStatus.UNAVAILABLE.messageKey(), List.of(), List.of(), false, false);
        }
        if (!RuleSubmissionValidator.validate(changeSet, server, registries, issues)) {
            return result(sessionId, operationId, RuleSaveStatus.VALIDATION_FAILED,
                    RuleSaveStatus.VALIDATION_FAILED.messageKey(),
                    args(firstError(issues)), toIssues(issues), false, false);
        }
        var overlayRoot = context.overlayRoot();
        if (overlayRoot == null) {
            return result(sessionId, operationId, RuleSaveStatus.UNAVAILABLE,
                    RuleSaveStatus.UNAVAILABLE.messageKey(), List.of(), List.of(), false, false);
        }
        RuleOverlayWriter.ApplyResult applied =
                new RuleOverlayWriter(overlayRoot, context.overlayNamespace()).apply(changeSet, issues);
        if (!applied.conflicts().isEmpty() || !issues.errors().isEmpty()) {
            String detail = applied.conflicts().isEmpty() ? firstError(issues) : String.join("; ", applied.conflicts());
            return result(sessionId, operationId, RuleSaveStatus.WRITE_FAILED,
                    RuleSaveStatus.WRITE_FAILED.messageKey(), args(detail), toIssues(issues), false, false);
        }
        if (applied.writtenFiles() == 0) {
            return result(sessionId, operationId, RuleSaveStatus.NO_CHANGES,
                    RuleSaveStatus.NO_CHANGES.messageKey(), List.of(), toIssues(issues), false, false);
        }

        // 索引重建 + 全维度 rescan：已存在的掉落物立即按新规则重选
        try {
            context.rebuildAndRescan(server);
        } catch (RuntimeException failure) {
            sessions.bumpVersion();
            RuleCatalogService.invalidate();
            LOGGER.error("保存后重建规则索引失败（覆盖层已写入，保留上一版索引）", failure);
            return result(sessionId, operationId, RuleSaveStatus.SAVED_NOT_RELOADED,
                    RuleSaveStatus.SAVED_NOT_RELOADED.messageKey(), List.of(), toIssues(issues), true, false);
        }
        RuleCatalogService.invalidate();
        return result(sessionId, operationId, RuleSaveStatus.SUCCESS,
                RuleSaveStatus.SUCCESS.messageKey(),
                args(Integer.toString(changeSet.edits().size()), Integer.toString(applied.writtenFiles())),
                toIssues(issues), true, true);
    }

    // 租约与确认窗口检查：超时释放后把 SESSION_EXPIRED 回执交给持有者
    public Expired expireSessions(MinecraftServer server, long tick) {
        EditSessionManager.SessionInfo expired = sessions.expire(tick);
        RuleEditChunkAccumulator.expireIdle(tick);
        if (expired == null) {
            return new Expired(null, Outbound.EMPTY);
        }
        LOGGER.info("编辑会话已释放（超时）: session={} owner={}", expired.sessionId(), expired.ownerName());
        if (server == null || expired.ownerUuid() == null) {
            return new Expired(null, Outbound.EMPTY);
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(expired.ownerUuid());
        if (owner == null) {
            return new Expired(null, Outbound.EMPTY);
        }
        return new Expired(owner, Outbound.of(result(expired.sessionId(), "", RuleSaveStatus.SESSION_EXPIRED,
                RuleSaveStatus.SESSION_EXPIRED.messageKey(), List.of(), List.of(), false, false)));
    }

    // 强制释放（管理员指令）：返回被释放的会话，空闲时返回 null
    public synchronized EditSessionManager.SessionInfo forceRelease() {
        return sessions.release();
    }

    // 玩家断开：只清理分片缓存；契约要求会话在租约到期后释放
    public void clearPlayer(UUID playerId) {
        RuleEditChunkAccumulator.clear(playerId);
    }

    // 停服：清空分片与幂等记录（会话对象随下次启动重建）
    public synchronized void reset() {
        RuleEditChunkAccumulator.clearAll();
        recentResults.clear();
        resultsSessionId = null;
    }

    // 组装并下发快照；失败时回结构化回执
    private Outbound snapshotOutbound(RuleEditServerContext context, MinecraftServer server,
                                      String sessionId, String requestId) {
        List<CustomPacketPayload> packets = snapshotPackets(context, server, sessionId, requestId);
        if (!packets.isEmpty()) {
            return Outbound.of(packets);
        }
        return Outbound.of(result(sessionId, "", RuleSaveStatus.UNAVAILABLE,
                "itemdespawntowhat.edit.snapshot_failed", List.of(), List.of(), false, false));
    }

    // 生成快照下发载荷：不超过单包上限时一个整包，超限时按 UTF-8 字节切成多片；异常返回空列表（调用方转回执）
    private List<CustomPacketPayload> snapshotPackets(RuleEditServerContext context, MinecraftServer server,
                                                      String sessionId, String requestId) {
        try {
            var registries = context.typeRegistries();
            var overlayRoot = context.overlayRoot();
            if (registries == null || overlayRoot == null || server == null) {
                return List.of();
            }
            try {
                // 手工改过覆盖层也要在快照里给出权威修订号，否则客户端视图会拿着过期版本去提交
                sessions.synchronizeDiskRevision();
            } catch (RuntimeException failure) {
                LOGGER.error("规则修订核对失败", failure);
                return List.of();
            }
            RuleLoadResult<Rule> merged = context.loadMerged(server);
            IssueCollector sourceIssues = new IssueCollector();
            RuleLoadContext loadContext = RuleLoadContext.full(
                    server.getResourceManager(), overlayRoot, context.overlayNamespace(),
                    server.registryAccess(), registries.effectTypes(), registries.conditionTypes(),
                    PackLayerResolver.allWorld()).withServer(server);
            RuleSourceIndex index = RuleLoadingService.sourceIndex(loadContext, sourceIssues);
            List<Issue> issues = new ArrayList<>(merged.allIssues());
            issues.addAll(sourceIssues.issues());
            RuleSnapshot snapshot = RuleSnapshotAssembler.assemble(index, merged.rules(),
                    sessions.version(), sessions.version(),
                    registries.effectTypes(), registries.conditionTypes(), List.copyOf(issues));
            String text = snapshot.serialize();
            List<CustomPacketPayload> packets = RuleSnapshotChunker.split(
                    sessionId == null ? "" : sessionId, requestId == null ? "" : requestId, text);
            if (!packets.isEmpty() && packets.getFirst() instanceof RuleSnapshotChunkPayload) {
                LOGGER.info("规则快照超过单包上限，已分片下发: bytes={} chunks={}",
                        RuleEditLimits.encodedLength(text), packets.size());
            }
            return packets;
        } catch (RuntimeException failure) {
            LOGGER.error("生成规则快照失败", failure);
            return List.of();
        }
    }

    // 会话已失效的统一回执
    private RuleSaveResultPayload sessionLost(String sessionId, ServerPlayer player) {
        RuleSaveStatus status = sessions.isOwner(player.getUUID())
                ? RuleSaveStatus.SESSION_EXPIRED : RuleSaveStatus.LOCK_NOT_OWNED;
        return result(sessionId, "", status, status.messageKey(), List.of(), List.of(), false, false);
    }

    // 版本戳比较：客户端视图版本必须与服务端一致
    private boolean versionMatches(RuleEditChangeSet changeSet) {
        return sessions.version() == changeSet.expectedVersion();
    }

    // 组装回执载荷
    private RuleSaveResultPayload result(String sessionId, String operationId, RuleSaveStatus status,
                                         String messageCode, List<String> messageArgs, List<RuleIssue> issues,
                                         boolean writtenToDisk, boolean reloaded) {
        return RuleSaveResultPayload.of(sessionId, operationId, status, messageCode,
                args(messageArgs.toArray(new String[0])),
                sessions.version(), writtenToDisk, reloaded, capIssues(issues));
    }

    // 幂等查询：会话变化时整体失效
    private RuleSaveResultPayload recentResult(String sessionId, String operationId) {
        if (!Objects.equals(resultsSessionId, sessionId)) {
            recentResults.clear();
            resultsSessionId = sessionId;
            return null;
        }
        return recentResults.get(operationId);
    }

    // 记录回执（保留最近 16 条）
    private void remember(RuleSaveResultPayload payload) {
        if (payload == null || payload.operationId().isEmpty()) {
            return;
        }
        if (!Objects.equals(resultsSessionId, payload.sessionId())) {
            recentResults.clear();
            resultsSessionId = payload.sessionId();
        }
        recentResults.put(payload.operationId(), payload);
        while (recentResults.size() > RuleEditSessionLimits.MAX_OPERATION_HISTORY) {
            Iterator<String> iterator = recentResults.keySet().iterator();
            if (!iterator.hasNext()) {
                break;
            }
            iterator.next();
            iterator.remove();
        }
    }

    // core/api 的问题模型转结构化问题（messageCode 由后续阶段的校验器补齐）
    private static List<RuleIssue> toIssues(IssueCollector collector) {
        if (collector == null) {
            return List.of();
        }
        List<RuleIssue> issues = new ArrayList<>();
        for (Issue issue : collector.issues()) {
            boolean error = issue.severity() == IssueSeverity.ERROR;
            issues.add(new RuleIssue(
                    error ? RuleIssue.SEVERITY_ERROR : RuleIssue.SEVERITY_WARNING,
                    issue.origin(),
                    RuleIssue.ORIGIN_RUNTIME,
                    issue.fieldPath() == null ? "" : issue.fieldPath(),
                    "itemdespawntowhat.edit.issue.generic",
                    List.of(),
                    issue.format()));
        }
        return capIssues(issues);
    }

    // 回执携带的问题条数上限（编解码器上限以内）
    private static List<RuleIssue> capIssues(List<RuleIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return List.of();
        }
        return issues.size() <= RuleEditLimits.MAX_ISSUES_PER_RESULT
                ? List.copyOf(issues)
                : List.copyOf(issues.subList(0, RuleEditLimits.MAX_ISSUES_PER_RESULT));
    }

    // 消息参数：条数与单条长度都按编解码器上限裁剪（服务端出站数据，允许裁剪）
    private static List<String> args(String... values) {
        List<String> args = new ArrayList<>();
        for (String value : values) {
            if (args.size() >= RuleEditLimits.MAX_MESSAGE_ARGS) {
                break;
            }
            String text = value == null ? "" : value;
            args.add(text.length() > RuleEditLimits.MAX_MESSAGE_ARG_CHARS
                    ? text.substring(0, RuleEditLimits.MAX_MESSAGE_ARG_CHARS)
                    : text);
        }
        return List.copyOf(args);
    }

    // 首条错误文本
    private static String firstError(IssueCollector issues) {
        if (issues == null || issues.errors().isEmpty()) {
            return "";
        }
        return issues.errors().getFirst().format();
    }

    // 异常转可读文本
    private static String describe(RuntimeException failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
