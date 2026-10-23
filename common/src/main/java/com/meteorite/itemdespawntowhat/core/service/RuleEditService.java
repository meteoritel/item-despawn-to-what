package com.meteorite.itemdespawntowhat.core.service;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.Issue;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.IssueSeverity;
import com.meteorite.itemdespawntowhat.core.api.DevelopmentDiagnostics;
import com.meteorite.itemdespawntowhat.core.load.RuleSourceIndex;
import com.meteorite.itemdespawntowhat.core.network.catalog.RuleCatalogService;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalog;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEditProtocol;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleIssue;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSaveStatus;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSaveProgress;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSaveProgressPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.CancelRuleSavePayload;
import com.meteorite.itemdespawntowhat.core.network.transport.CloseRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.ConfirmRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.HeartbeatRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.OpenRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RequestRuleCatalogPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RequestRuleSnapshotPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RequestRuleSourceViewPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleCatalogPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditChunkAccumulator;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditLimits;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerContext;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSaveResultPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSnapshotChunkPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSnapshotPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSourceViewChunker;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSourceViewPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.SaveRuleChangeSetChunkPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.SaveRuleChangeSetPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
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
import com.meteorite.itemdespawntowhat.core.network.transport.RequestRuleLifespanPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleLifespanPayload;

/**
 * 独占编辑会话的业务编排：打开/确认/心跳/关闭/快照/目录/保存。
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
    private @Nullable UUID lifespanOwner;
    private @Nullable RequestRuleLifespanPayload pendingLifespan;
    private RuleSaveTask pendingSave;
    private String savingSessionId;
    private String savingOperationId;
    private UUID savingOwner;
    private RuleSaveResultPayload completedSave;
    private static final int PROGRESS_INTERVAL_TICKS = 5;
    private long progressTick;
    private long progressSequence;
    private RuleSaveProgress sentProgress;
    private Runnable deferredReload;
    private RuleSnapshotTask pendingSnapshot;
    // 最近一次快照装配使用的来源分层索引：来源视图按需应答的数据源，不为单条规则再读盘
    private @Nullable RuleSourceIndex sourceIndex;
    private SnapshotRequest requestedSnapshot;
    private SnapshotRequest deliveringSnapshot;
    private final java.util.ArrayDeque<CustomPacketPayload> snapshotPackets = new java.util.ArrayDeque<>();
    // 首载分段观测：本次投递的起点、总包数、字节与已投递数；关闭时保持零值
    private long deliveryStartedNanos;
    private long deliveryStartTick;
    private long deliveryLastTick;
    private int deliveryPackets;
    private long deliveryBytes;
    private int deliverySent;
    private record SnapshotRequest(UUID owner, String sessionId, String requestId) { }

    /**
     * 已采样后需要交付给玩家的参考寿命应答。
     * @param player 应答接收者
     * @param payload 缓存的寿命应答
     */
    public record LifespanReply(ServerPlayer player, RuleLifespanPayload payload) { }


    // 构造编排服务：会话状态机由调用方注入；网络与加载依赖一律经方法参数传入
    public RuleEditService(EditSessionManager sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    // 底层的会话状态机（命令层查询状态用）
    public EditSessionManager sessions() {
        return sessions;
    }

    /**
     * 判断覆盖层是否被保存或恢复隔离占用，供磁盘读取入口避免观察不完整目录。
     * @return 是否应暂缓覆盖层读取
     */
    public boolean saving() { return pendingSave != null || sessions.store().unavailable(); }

    /**
     * 一次请求要下发的载荷集合。
     */
    public record Outbound(List<CustomPacketPayload> packets) {

        public static final Outbound EMPTY = new Outbound(List.of());

        // 便利工厂：可变参数按传入顺序组装成不可变载荷集合
        public static Outbound of(CustomPacketPayload... packets) {
            return new Outbound(List.of(packets));
        }

        // 拷贝入参列表，保证回执载荷集合不可变
        public static Outbound of(List<CustomPacketPayload> packets) {
            return new Outbound(List.copyOf(packets));
        }
    }

    /**
     * 打开结果：成功时 acquired=true，失败时携带当前持有者名字。
     */
    public record OpenOutcome(boolean acquired, String holderName, OpenRuleEditorPayload payload) {
    }

    /**
     * 超时释放结果：target 为需要被通知的玩家（无人可通知时为 null），outbound 为要下发的载荷。
     */
    public record Expired(@Nullable ServerPlayer target, Outbound outbound) {
    }

    // 「打开编辑器」唯一入口：先原子取锁，成功或失败都要把结论下发给客户端
    public synchronized OpenOutcome openEditor(ServerPlayer player, long tick, int maxPlaceBlockRadius) {
        EditSessionManager.AcquireResult acquired =
                sessions.acquire(player.getUUID(), player.getName().getString(), tick);
        if (acquired.acquired()) {
            OpenRuleEditorPayload payload = new OpenRuleEditorPayload(
                    acquired.session().sessionId(),
                    RuleEditSessionLimits.TARGET_ID,
                    RuleEditProtocol.VERSION,
                    sessions.version(),
                    maxPlaceBlockRadius,
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
                maxPlaceBlockRadius,
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

    // C2S 关闭：保存期间延后释放，回滚与清理结束后才发送终态
    public Outbound close(ServerPlayer player, CloseRuleEditorPayload payload) {
        if (!sessions.owns(payload.sessionId()) || !sessions.isOwner(player.getUUID())) {
            return Outbound.of(sessionLost(payload.sessionId(), player));
        }
        if (pendingSave != null) { forceRelease(); return Outbound.EMPTY; }
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
        if (!sessions.confirmed(payload.sessionId())) {
            // 会话状态优先于请求参数：未确认的 OPENING 会话不接受目录请求
            return Outbound.of(sessionLost(payload.sessionId(), player));
        }
        if (payload.page() < 0 || payload.pageSize() <= 0
                || payload.pageSize() > RequestRuleCatalogPayload.MAX_PAGE_SIZE) {
            return Outbound.of(result(payload.sessionId(), "", RuleSaveStatus.INVALID_REQUEST,
                    RuleSaveStatus.INVALID_REQUEST.messageKey(),
                    args("page=" + payload.page() + ", pageSize=" + payload.pageSize()),
                    List.of(), false, false));
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

    /**
     * C2S 来源视图请求：编辑页打开某条规则时按需取回该规则的基底与覆盖原始内容及来源元数据。
     * 数据来自最近一次快照装配的来源分层索引，不再读盘；索引未就绪、规则不存在或内容超限时回失败回执。
     *
     * @param player 会话持有者
     * @param payload 来源视图请求
     * @param tick 服务端活动刻
     * @return 来源视图应答（整包或分片），或会话失效回执
     */
    public Outbound requestSourceView(ServerPlayer player, RequestRuleSourceViewPayload payload, long tick) {
        if (!sessions.owns(payload.sessionId()) || !sessions.isOwner(player.getUUID())
                || !sessions.confirmed(payload.sessionId())) {
            return Outbound.of(sessionLost(payload.sessionId(), player));
        }
        sessions.heartbeat(payload.sessionId(), tick);
        ResourceLocation id = ResourceLocation.tryParse(payload.ruleId());
        if (id == null) {
            return Outbound.of(sourceViewFailure(payload, RuleSaveStatus.INVALID_REQUEST));
        }
        RuleSourceIndex index = sourceIndex;
        if (index == null) {
            return Outbound.of(sourceViewFailure(payload, RuleSaveStatus.UNAVAILABLE));
        }
        RuleSourceIndex.Entry entry = null;
        for (RuleSourceIndex.Entry candidate : index.entries()) {
            if (candidate.id().equals(id)) { entry = candidate; break; }
        }
        if (entry == null) {
            return Outbound.of(sourceViewFailure(payload, RuleSaveStatus.INVALID_REQUEST));
        }
        String json = RuleSnapshotAssembler.sourceView(entry).serialize();
        List<CustomPacketPayload> packets = RuleSourceViewChunker.split(payload.sessionId(), payload.requestId(), json);
        if (packets.isEmpty()) {
            // 超过来源视图总量上限或分片数上限：回结构化失败，不让请求静默消失
            return Outbound.of(sourceViewFailure(payload, RuleSaveStatus.INVALID_REQUEST));
        }
        return Outbound.of(packets);
    }

    // 来源视图失败回执：空 JSON + 状态码，客户端只按状态码提示
    private RuleSourceViewPayload sourceViewFailure(RequestRuleSourceViewPayload payload, RuleSaveStatus status) {
        return new RuleSourceViewPayload(payload.sessionId(), payload.requestId(), status.id(), "");
    }

    /**
     * 获取服务端缓存的参考寿命；尚未采样时保留最近请求并先应答零。
     * @param player 会话持有者
     * @param context 平台参考寿命来源
     * @param payload 参考寿命请求
     * @param tick 服务端活动刻
     * @return 缓存应答或会话失败回执
     */
    public Outbound requestLifespan(ServerPlayer player, RuleEditServerContext context,
            RequestRuleLifespanPayload payload, long tick) {
        if (!sessions.owns(payload.sessionId()) || !sessions.isOwner(player.getUUID())
                || !sessions.confirmed(payload.sessionId())) {
            return Outbound.of(sessionLost(payload.sessionId(), player));
        }
        sessions.heartbeat(payload.sessionId(), tick);
        int ticks = context.referenceLifespanTicks();
        pendingLifespan = ticks == 0 ? payload : null;
        lifespanOwner = ticks == 0 ? player.getUUID() : null;
        return Outbound.of(new RuleLifespanPayload(payload.sessionId(), payload.requestId(), ticks));
    }

    /**
     * 平台完成首次采样后补发一次结果；已结束的会话不会接收过期应答。
     * @param server 当前服务端
     * @param context 平台参考寿命来源
     * @return 可交付应答；未采样或会话无效时为空
     */
    public @Nullable LifespanReply pollLifespan(MinecraftServer server, RuleEditServerContext context) {
        RequestRuleLifespanPayload pending = pendingLifespan;
        UUID owner = lifespanOwner;
        if (pending == null || owner == null) return null;
        if (!sessions.owns(pending.sessionId()) || !sessions.isOwner(owner)
                || !sessions.confirmed(pending.sessionId())) {
            pendingLifespan = null;
            lifespanOwner = null;
            return null;
        }
        int ticks = context.referenceLifespanTicks();
        if (ticks == 0) return null;
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        pendingLifespan = null;
        lifespanOwner = null;
        return player == null ? null : new LifespanReply(player,
                new RuleLifespanPayload(pending.sessionId(), pending.requestId(), ticks));
    }

    // C2S 变更集：完整的保存编排（幂等 → 会话/权限 → 版本戳 → 校验 → 落盘 → 重载 → 回执 + 新快照）
    public Outbound submitChangeSet(ServerPlayer player, RuleEditServerContext context, MinecraftServer server,
                                    SaveRuleChangeSetPayload payload, long tick) {
        return submit(player, context, server, payload.sessionId(), payload.operationId(),
                payload::changeSetJson, tick);
    }

    // C2S 分片：先校验会话，再重组；收齐后复用直发保存流程
    public Outbound submitChunk(ServerPlayer player, RuleEditServerContext context, MinecraftServer server,
                                SaveRuleChangeSetChunkPayload payload, long tick) {
        if (!sessions.owns(payload.sessionId()) || !sessions.isOwner(player.getUUID())) {
            return Outbound.of(sessionLost(payload.sessionId(), player, payload.operationId()));
        }
        RuleEditChunkAccumulator.ValidatedChunk validated = RuleEditChunkAccumulator.validate(payload);
        if (validated == null) {
            return Outbound.of(result(payload.sessionId(), payload.operationId(), RuleSaveStatus.INVALID_REQUEST,
                    RuleSaveStatus.INVALID_REQUEST.messageKey(), args("chunk"), List.of(), false, false));
        }
        RuleSaveResultPayload previous = recentResult(payload.sessionId(), payload.operationId());
        if (previous != null) { return Outbound.of(previous); }
        sessions.heartbeat(payload.sessionId(), tick);
        RuleEditChunkAccumulator.AcceptResult accepted = RuleEditChunkAccumulator.acceptValidated(player, validated, tick);
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
        return submit(player, context, server, payload.sessionId(), payload.operationId(), () -> String.join("", accepted.chunks()), tick);
    }

    // 保存主流程
    private synchronized Outbound submit(ServerPlayer player, RuleEditServerContext context, MinecraftServer server,
                                         String rawSessionId, String rawOperationId, java.util.function.Supplier<String> json, long tick) {
        String sessionId = rawSessionId == null ? "" : rawSessionId;
        String operationId = rawOperationId == null ? "" : rawOperationId;
        if (operationId.isEmpty() || RuleEditLimits.encodedLength(operationId) > RuleEditLimits.MAX_ID_CHARS) {
            return Outbound.of(result(sessionId, operationId, RuleSaveStatus.INVALID_REQUEST,
                    RuleSaveStatus.INVALID_REQUEST.messageKey(), args("operationId"), List.of(), false, false));
        }
        if (!sessions.owns(sessionId)) {
            return Outbound.of(sessionLost(sessionId, player, operationId));
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

        if (pendingSave != null) {
            if (sessionId.equals(savingSessionId) && operationId.equals(savingOperationId)) { return Outbound.EMPTY; }
            return Outbound.of(result(sessionId, operationId, RuleSaveStatus.INVALID_REQUEST,
                    RuleSaveStatus.INVALID_REQUEST.messageKey(), args("apply in progress"), List.of(), false, false));
        }
        if (server == null || context.saveReloadContext() == null || sessions.store().unavailable()) {
            return Outbound.of(result(sessionId, operationId, RuleSaveStatus.UNAVAILABLE,
                    RuleSaveStatus.UNAVAILABLE.messageKey(), List.of(), List.of(), false, false));
        }
        if (!sessions.beginApply(sessionId)) { return Outbound.of(sessionLost(sessionId, player, operationId)); }
        sessions.heartbeat(sessionId, tick);
        if (pendingSnapshot != null) { pendingSnapshot.invalidate(); }
        requestedSnapshot = null;
        clearSnapshotPackets("apply");
        savingSessionId = sessionId;
        savingOperationId = operationId;
        savingOwner = player.getUUID();
        progressTick = tick - PROGRESS_INTERVAL_TICKS;
        progressSequence = 0;
        sentProgress = null;
        pendingSave = new RuleSaveTask(sessions, context, server, sessionId, player.getUUID(), json, outcome -> {
            if (outcome.written()) { RuleCatalogService.invalidate(); }
            completedSave = result(sessionId, operationId, outcome.status(), outcome.status().messageKey(),
                    switch (outcome.status()) {
                        case SUCCESS -> args(Integer.toString(outcome.edited()), Integer.toString(outcome.files()));
                        case VERSION_CONFLICT -> args(Integer.toString(sessions.version()), Integer.toString(outcome.expectedVersion()));
                        default -> args(outcome.detail());
                    }, toIssues(outcome.issues()), outcome.written(),
                    outcome.status() == RuleSaveStatus.SUCCESS);
        });
        pendingSave.schedule();
        return Outbound.EMPTY;
    }

    /**
     * 保存期间的数据包重载延后至文件工作结束，撤销尚未提交的保存；只保留最近一次重载。
     * @param reload 服务端线程上的重载动作
     * @return 是否已接管重载
     */
    public boolean deferReload(Runnable reload) {
        if (sessions.store().unavailable()) {
            LOGGER.error("覆盖层文件工作不可用，拒绝重载并保留当前运行时索引");
            return true;
        }
        if (pendingSave == null) { return false; }
        pendingSave.invalidate();
        deferredReload = reload;
        return true;
    }

    /**
     * 每个服务端 tick 交付异步保存回执，世界与网络访问只发生在调用线程。
     * @param server 当前服务端
     * @return 回执接收者与载荷，尚未完成时为 null
     */
    public @Nullable SaveReply pollSave(MinecraftServer server) {
        sessions.store().flushRevision();
        if (pendingSave == null) { return null; }
        pendingSave.pollCompletion();
        if (!sessions.owns(savingSessionId)) { pendingSave.invalidate(); }
        ServerPlayer owner = server.getPlayerList().getPlayer(savingOwner);
        if (!com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerHandler.canEdit(owner)) {
            pendingSave.invalidate();
            sessions.release();
        }
        if (!pendingSave.finished()) { return null; }
        RuleSaveResultPayload payload = completedSave;
        sessions.finishApply(savingSessionId, server.getTickCount());
        // 会话释放只结束锁归属；保存任务已确定的终态仍按真实写盘与重载结果返回。
        if (payload != null) { remember(payload); }
        pendingSave = null;
        completedSave = null;
        Runnable reload = deferredReload;
        deferredReload = null;
        if (reload != null) { reload.run(); }
        if (payload != null && sessions.owns(payload.sessionId())
                && (reload != null || RuleSaveStatus.VERSION_CONFLICT.id().equals(payload.statusCode())
                    || RuleSaveStatus.SAVED_NOT_RELOADED.id().equals(payload.statusCode()))) {
            requestedSnapshot = new SnapshotRequest(savingOwner, payload.sessionId(), "");
        }
        return owner == null || payload == null ? null : new SaveReply(owner, Outbound.of(payload));
    }

    /**
     * 请求取消当前玩家的对应保存；提交确认后只返回不可取消进度，不能回滚已发布规则。
     * @param player 请求玩家
     * @param payload 保存操作标识
     * @return 在途保存无立即终态；未开始写盘或已完成的操作可直接交付取消／幂等回执
     */
    public Outbound cancelSave(ServerPlayer player, CancelRuleSavePayload payload) {
        if (!sessions.owns(payload.sessionId()) || !sessions.isOwner(player.getUUID())) { return Outbound.EMPTY; }
        if (pendingSave != null) {
            if (payload.sessionId().equals(savingSessionId) && payload.operationId().equals(savingOperationId)) {
                pendingSave.cancel();
                sentProgress = null;
            }
            return Outbound.EMPTY;
        }
        RuleSaveResultPayload previous = recentResult(payload.sessionId(), payload.operationId());
        if (previous != null) { return Outbound.of(previous); }
        RuleEditChunkAccumulator.clear(player.getUUID());
        RuleSaveResultPayload cancelled = result(payload.sessionId(), payload.operationId(), RuleSaveStatus.CANCELLED,
                RuleSaveStatus.CANCELLED.messageKey(), List.of(), List.of(), false, false);
        remember(cancelled);
        return Outbound.of(cancelled);
    }

    /**
     * 读取最新进度样本；最多每五个活动 tick 发送一次，不逐条广播后台状态。
     * @param server 当前服务端
     * @return 待交付进度，样本未变或保存已完成时为 null
     */
    public @Nullable SaveReply pollSaveProgress(MinecraftServer server) {
        if (pendingSave == null || pendingSave.finished() || server.getTickCount() - progressTick < PROGRESS_INTERVAL_TICKS) {
            return null;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(savingOwner);
        if (!com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerHandler.canEdit(player)) { return null; }
        RuleSaveProgress progress = pendingSave.progress();
        if (progress.equals(sentProgress)) { return null; }
        sentProgress = progress;
        progressTick = server.getTickCount();
        return new SaveReply(player, Outbound.of(new RuleSaveProgressPayload(savingSessionId, savingOperationId,
                ++progressSequence, progress)));
    }

    /**
     * 已完成保存的网络交付。
     * @param player 接收者
     * @param outbound 结果载荷
     */
    public record SaveReply(ServerPlayer player, Outbound outbound) { }

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
        if (pendingSave != null) { pendingSave.invalidate(); }
        return sessions.release();
    }

    // 玩家断开：保存期间请求延后释放并等待回滚；其它会话由原租约回收
    public void clearPlayer(UUID playerId) {
        if (pendingSave != null && playerId.equals(savingOwner)) { forceRelease(); }
        RuleEditChunkAccumulator.clear(playerId);
    }

    // 停服等待不持有服务层监视器；超时作业仍由文件所有者登记并阻止下一次启动。
    public void reset() {
        if (pendingSave != null) { pendingSave.invalidate(); }
        if (pendingSnapshot != null) { pendingSnapshot.invalidate(); }
        sessions.store().close();
        pendingSave = null;
        completedSave = null;
        deferredReload = null;
        pendingSnapshot = null;
        requestedSnapshot = null;
        sourceIndex = null;
        clearSnapshotPackets("reset");
        RuleEditChunkAccumulator.clearAll();
        recentResults.clear();
        resultsSessionId = null;
        pendingLifespan = null;
        lifespanOwner = null;
    }

    // 快照请求只保留最新一个；读盘、解码、编码与分片由续接任务处理。
    private Outbound snapshotOutbound(RuleEditServerContext context, MinecraftServer server,
                                      String sessionId, String requestId) {
        if (server == null || context.saveReloadContext() == null) {
            return Outbound.of(result(sessionId, "", RuleSaveStatus.UNAVAILABLE,
                    RuleSaveStatus.UNAVAILABLE.messageKey(), List.of(), List.of(), false, false));
        }
        EditSessionManager.SessionInfo session = sessions.session();
        if (session != null) {
            requestedSnapshot = new SnapshotRequest(session.ownerUuid(), sessionId, requestId);
            clearSnapshotPackets("superseded");
            startSnapshot(server, context);
        }
        return Outbound.EMPTY;
    }

    /**
     * 每 tick 最多交付 {@link RuleEditLimits#SNAPSHOT_CHUNKS_PER_TICK} 个快照分片包，按入队顺序下发，
     * 分片顺序与 transferId 语义不变；在途快照与待启动请求各最多一个，保存期间暂缓准备。
     * @param server 当前服务端
     * @param context 平台上下文
     * @return 本 tick 的网络交付，暂无数据时为 null
     */
    public @Nullable SaveReply pollSnapshot(MinecraftServer server, RuleEditServerContext context) {
        if (pendingSnapshot != null) { pendingSnapshot.pollCompletion(); }
        if (pendingSave != null) { return null; }
        startSnapshot(server, context);
        if (snapshotPackets.isEmpty() || deliveringSnapshot == null) { return null; }
        if (!sessions.owns(deliveringSnapshot.sessionId())) { clearSnapshotPackets("session_lost"); return null; }
        ServerPlayer player = server.getPlayerList().getPlayer(deliveringSnapshot.owner());
        if (!com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerHandler.canEdit(player)) {
            clearSnapshotPackets("no_permission"); return null;
        }
        int limit = Math.min(RuleEditLimits.SNAPSHOT_CHUNKS_PER_TICK, snapshotPackets.size());
        List<CustomPacketPayload> packets = new ArrayList<>(limit);
        for (int index = 0; index < limit; index++) {
            packets.add(snapshotPackets.removeFirst());
        }
        if (DevelopmentDiagnostics.enabled()) {
            deliverySent += packets.size();
            deliveryLastTick = server.getTickCount();
            if (snapshotPackets.isEmpty()) { logDelivery(); }
        }
        return new SaveReply(player, Outbound.of(packets));
    }

    // 本次投递的观测起点：包数与字节一次性统计，逐 tick 只累加已投递数
    private void startDelivery(List<CustomPacketPayload> packets, long tick) {
        if (!DevelopmentDiagnostics.enabled()) { return; }
        deliveryStartedNanos = DevelopmentDiagnostics.start();
        deliveryStartTick = tick;
        deliveryLastTick = tick;
        deliveryPackets = packets.size();
        deliveryBytes = 0L;
        deliverySent = 0;
        for (CustomPacketPayload payload : packets) { deliveryBytes += packetBytes(payload); }
    }

    // 首载分段观测：投递完成的包数、字节与跨 tick 数，用于声明节拍是否匹配每 tick 包数
    private void logDelivery() {
        JsonObject data = new JsonObject();
        data.addProperty("packets", deliveryPackets);
        data.addProperty("sent", deliverySent);
        data.addProperty("bytes", deliveryBytes);
        long ticks = Math.max(0L, deliveryLastTick - deliveryStartTick);
        data.addProperty("ticks", ticks);
        data.addProperty("per_tick", ticks == 0L ? deliveryPackets : (double) deliveryPackets / ticks);
        data.addProperty("total_ms", DevelopmentDiagnostics.micros(deliveryStartedNanos) / 1000.0);
        DevelopmentDiagnostics.event(DevelopmentDiagnostics.SCENE_FIRST_LOAD, "SERVER_DELIVER", data);
    }

    // 丢弃排队中的分片：观测开启且确有未投递分片时记录一次中止
    private void clearSnapshotPackets(String reason) {
        if (DevelopmentDiagnostics.enabled() && !snapshotPackets.isEmpty()) {
            JsonObject data = new JsonObject();
            data.addProperty("packets", deliveryPackets);
            data.addProperty("sent", deliverySent);
            data.addProperty("bytes", deliveryBytes);
            data.addProperty("ticks", Math.max(0L, deliveryLastTick - deliveryStartTick));
            data.addProperty("aborted", true);
            data.addProperty("reason", reason);
            DevelopmentDiagnostics.event(DevelopmentDiagnostics.SCENE_FIRST_LOAD, "SERVER_DELIVER", data);
        }
        snapshotPackets.clear();
    }

    // 下发字节按 UTF-8 计，与分片阈值口径一致
    private static long packetBytes(CustomPacketPayload payload) {
        if (payload instanceof RuleSnapshotPayload direct) {
            return RuleEditLimits.encodedLength(direct.snapshotJson());
        }
        if (payload instanceof RuleSnapshotChunkPayload chunk) {
            return RuleEditLimits.encodedLength(chunk.chunk());
        }
        return 0L;
    }

    // 请求到达即开始纯文件工作；保存和在途准备仍串行，Codec 只由公共调度器执行。
    private void startSnapshot(MinecraftServer server, RuleEditServerContext context) {
        if (pendingSave != null) { return; }
        if (requestedSnapshot != null && pendingSnapshot == null) {
            SnapshotRequest current = requestedSnapshot;
            if (!sessions.owns(current.sessionId()) || context.saveReloadContext() == null) {
                requestedSnapshot = null;
                return;
            }
            pendingSnapshot = new RuleSnapshotTask(context, server, sessions, current.sessionId(), current.requestId(), delivery -> {
                pendingSnapshot = null;
                // 来源索引与快照同源：即使本次下发被替换，最新装配结果仍可作为来源视图的数据源
                if (delivery.index() != null) { sourceIndex = delivery.index(); }
                if (requestedSnapshot != current) { return; }
                requestedSnapshot = null;
                deliveringSnapshot = current;
                if (delivery.packets().isEmpty()) {
                    snapshotPackets.add(result(current.sessionId(), "", RuleSaveStatus.UNAVAILABLE,
                            "itemdespawntowhat.edit.snapshot_failed", List.of(), List.of(), false, false));
                } else {
                    startDelivery(delivery.packets(), server.getTickCount());
                    snapshotPackets.addAll(delivery.packets());
                }
            });
            pendingSnapshot.schedule();
        }
    }

    // 会话已失效的统一回执
    private RuleSaveResultPayload sessionLost(String sessionId, ServerPlayer player) {
        return sessionLost(sessionId, player, "");
    }

    private RuleSaveResultPayload sessionLost(String sessionId, ServerPlayer player, String operationId) {
        RuleSaveStatus status = sessions.isOwner(player.getUUID())
                ? RuleSaveStatus.SESSION_EXPIRED : RuleSaveStatus.LOCK_NOT_OWNED;
        return result(sessionId, operationId, status, status.messageKey(), List.of(), List.of(), false, false);
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

}
