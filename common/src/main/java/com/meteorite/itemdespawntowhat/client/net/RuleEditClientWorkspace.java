package com.meteorite.itemdespawntowhat.client.net;

import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.DevelopmentDiagnostics;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalog;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalogType;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEditProtocol;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleSaveStatus;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleSnapshot;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleSourceView;
import com.meteorite.itemdespawntowhat.core.network.transport.CancelRuleSavePayload;
import com.meteorite.itemdespawntowhat.core.network.transport.CloseRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.ConfirmRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.HeartbeatRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.OpenRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RequestRuleCatalogPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RequestRuleLifespanPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RequestRuleSnapshotPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RequestRuleSourceViewPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleCatalogPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleChunkPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditLimits;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditPayloadRouter;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleLifespanPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSaveProgressPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSaveResultPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSnapshotPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSourceViewPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleTextChunker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditClientReceiver;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleQueryResultPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSessionEventPayload;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleEditRequest;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 客户端编辑会话工作区：client/net 对界面层的唯一入口。
 * 职责：镜像服务端状态机、维持心跳、提交变更集（自动分片）、
 * 接收并缓存快照/目录、组装分片、在会话失效时关闭界面。
 * 约束：本包不 import 任何 client/ui 或 client/edit 类；
 * 界面实现由 {@link EditorScreenHooks} 注册，未注册时只记日志。
 * 线程：平台接收器切到客户端主线程；同步边界保留在聚合入口，内部状态所有者不另加锁。
 */
public final class RuleEditClientWorkspace {

    /**
     * 上行发送器：两平台的 ClientPlayNetworking 不同，由各平台客户端初始化时注入；
     * 未注入时上行载荷被丢弃并记 warn。
     */
    public interface PacketSender {

        void send(CustomPacketPayload payload);
    }

    // ---- 日志与单例 ----
    private static final Logger LOGGER = LogManager.getLogger(Constants.MOD_ID + "-client-net");

    // 单例：客户端只存在一个编辑会话
    private static final RuleEditClientWorkspace INSTANCE = new RuleEditClientWorkspace();

    private @Nullable PacketSender sender;
    private @Nullable Connection receiverConnection;
    private final SessionLease lease = new SessionLease();
    private final SaveTicket ticket = new SaveTicket();
    private final SourceViewRequests sources = new SourceViewRequests();
    private final CatalogDirectory directories = new CatalogDirectory();
    private final RuleChunkBuffer chunkBuffer = new RuleChunkBuffer();
    private String snapshotRequestId = "";
    private boolean snapshotPending;
    private @Nullable RuleSnapshot snapshot;
    private @Nullable String snapshotFailure;
    private boolean endingSession;
    private long firstLoadStartedNanos;
    private long snapshotRecvStartedNanos;
    private String snapshotRecvTransferId = "";
    private int snapshotRecvChunks;
    private String lifespanRequestId = "";
    private int queriedLifespanTicks;
    private @Nullable ClientPacketListener lifespanConnection;

    private RuleEditClientWorkspace() {
    }

    /** 客户端只存在一个编辑工作区，由平台连接与 tick 事件驱动。 */
    public static RuleEditClientWorkspace instance() {
        return INSTANCE;
    }

    /** 当前会话只读快照；状态未变化时复用同一实例。 */
    public synchronized EditorSessionView session() { return lease.view(snapshot, snapshotFailure, queriedLifespanTicks); }

    /** 当前保存只读快照；读取不推进结果序号。 */
    public synchronized EditorSaveView saving() { return ticket.view(); }

    /** 安装两端各自的上行 adapter；连接重置保留发送能力。 */
    public static void installSender(@Nullable PacketSender sender) {
        INSTANCE.setSender(sender);
    }

    /** 创建保存幂等关联标识。 */
    public static String newOperationId() {
        return UUID.randomUUID().toString();
    }

    /** 显式请求当前连接的统一参考寿命；已有结果或在途请求时复用。 */
    public synchronized void requestReferenceLifespan() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        // 连接身份隔离缓存，即使断线时编辑器已关闭，也不会把参考值带到下一台服务器。
        if (connection != lifespanConnection) {
            queriedLifespanTicks = 0;
            lifespanRequestId = "";
            lifespanConnection = connection;
        }
        if (!lease.canQuery(connection != null)) return;
        if (queriedLifespanTicks > 0) return;
        if (lifespanRequestId.isEmpty()) {
            lifespanRequestId = UUID.randomUUID().toString();
            if (!send(new RequestRuleLifespanPayload(lease.id(), lifespanRequestId))) lifespanRequestId = "";
        }
    }

    /** 请求取消当前可取消工单，最终状态仍以服务端回执为准。 */
    public synchronized boolean cancelSave() {
        if (lease.state() != RuleEditClientState.APPLYING || !ticket.canCancel() || !connected()) return false;
        try {
            if (!send(new CancelRuleSavePayload(lease.id(), ticket.operationId()))) return false;
        } catch (RuntimeException failure) {
            LOGGER.warn("发送保存取消请求失败，保持保存状态并等待服务端终态", failure);
            return false;
        }
        ticket.requestCancel();
        return true;
    }

    /** 读取目录加载状态与已到达的部分数据，不发送请求。 */
    public synchronized ResourceView<RuleCatalog> directory(RuleCatalogType type) {
        return directories.view(type);
    }

    /** 显式启动或继续尚未完成的目录加载。 */
    public synchronized void requestDirectory(RuleCatalogType type) {
        if (lease.canQuery(connected())) directories.request(type, this::sendCatalog);
    }

    /** 重新打开目录时验证首屏修订，未变化继续复用完整缓存。 */
    public synchronized void refreshDirectory(RuleCatalogType type) {
        if (type != null && lease.canQuery(connected())) directories.refresh(type, this::sendCatalog);
    }

    /** 显式重试失败目录，保留已接收的部分数据。 */
    public synchronized void retryDirectory(RuleCatalogType type) {
        if (type != null && lease.canQuery(connected())) directories.retry(type, this::sendCatalog);
    }

    /** 资源或语言变化后使目录缓存失效。 */
    public synchronized void invalidateDirectories() { directories.clear(); }

    /** ACTIVE 会话显式请求快照，旧请求与分片同时失效。 */
    public synchronized void requestSnapshot() {
        if (!lease.canQuery(connected())) return;
        snapshotPending = true;
        snapshotRequestId = UUID.randomUUID().toString();
        snapshotFailure = null;
        chunkBuffer.clear();
        if (!send(new RequestRuleSnapshotPayload(lease.id(), snapshotRequestId))) {
            snapshotRequestId = "";
            snapshotPending = false;
            snapshotFailure = "snapshot_send_failed";
        }
    }

    /** 按需请求规则来源，已就绪或在途时不重复发送。 */
    public synchronized void requestSourceView(@Nullable String ruleId) {
        if (ruleId == null || ruleId.isEmpty() || !lease.canQuery(connected())) return;
        sources.request(ruleId, (id, request) -> send(new RequestRuleSourceViewPayload(lease.id(), request, id)));
    }

    /** 读取来源加载状态；超时由客户端 tick 推进。 */
    public synchronized ResourceView<RuleSourceView> sourceView(String ruleId) { return sources.view(ruleId); }

    /** 显式重试失败来源请求。 */
    public synchronized void retrySourceView(@Nullable String ruleId) { requestSourceView(ruleId); }

    /** 提交变更集并按上限分片；部分发送失败保留工单等待取消或断线。 */
    public synchronized boolean save(@Nullable String operationId, @Nullable String changeSetJson) {
        if (!lease.canQuery(connected())) {
            LOGGER.warn("无可用会话，忽略变更集提交: state={}", lease.state());
            return false;
        }
        if (operationId == null || operationId.isEmpty() || changeSetJson == null || changeSetJson.isEmpty()) {
            return false;
        }
        List<CustomPacketPayload> packets = RuleTextChunker.split(RuleTextChunker.Family.CHANGE_SET, lease.id(),
                operationId, changeSetJson);
        if (packets.isEmpty()) {
            LOGGER.warn("变更集为空或超过上限，未提交: bytes={}",
                    RuleEditLimits.encodedLength(changeSetJson));
            return false;
        }
        if (sender == null) { return false; }
        ticket.start(operationId);
        lease.beginSave();
        try { return sendAll(packets); }
        catch (RuntimeException failure) {
            LOGGER.warn("发送保存请求失败，等待连接状态或服务端取消确认", failure);
            if (!connected()) { reset(); return false; }
            cancelSave();
            return true;
        }
    }

    /** 关闭非保存中的会话；保存工单由终态负责释放。 */
    public synchronized void close(@Nullable String reasonCode) {
        if (lease.state() == RuleEditClientState.APPLYING) return;
        if (lease.held() && connected()) send(new CloseRuleEditorPayload(lease.id(), reasonCode == null ? "" : reasonCode));
        endSession();
    }

    /** 连接断开或替换时清理会话、结果与连接级寿命缓存。 */
    public synchronized void reset() {
        endSession();
        queriedLifespanTicks = 0;
        lifespanRequestId = "";
        lifespanConnection = null;
        ticket.reset();
    }

    /** 推进请求期限、目录续页与心跳；由客户端主线程调用。 */
    public synchronized void tick() {
        if (!lease.held()) return;
        if (!connected()) {
            LOGGER.info("客户端已断开服务器连接，重置编辑会话工作区");
            unbindConnection(receiverConnection);
            return;
        }
        long now = System.currentTimeMillis();
        sources.tick(now);
        directories.tick(now);
        if (lease.canQuery(connected())) directories.advance(this::sendCatalog);
        if (lease.heartbeatDue(currentTick())) send(new HeartbeatRuleEditorPayload(lease.id()));
    }

    private synchronized void setSender(@Nullable PacketSender sender) {
        this.sender = sender;
    }

    /** 绑定本连接接收器，旧连接的延后帧由 Binding 隔离。 */
    public synchronized void bindConnection(Connection connection) {
        if (receiverConnection == connection) { return; }
        if (receiverConnection != null) { RuleEditPayloadRouter.unbind(receiverConnection); }
        reset(); receiverConnection = connection;
        RuleEditPayloadRouter.bind(connection, new RuleEditClientReceiver() {
            @Override public void open(OpenRuleEditorPayload payload) { onOpen(payload); }
            @Override public void snapshot(RuleSnapshotPayload payload) { onSnapshot(payload); }
            @Override public void chunk(RuleChunkPayload payload) { onChunk(payload); }
            @Override public void result(RuleSaveResultPayload payload) { onResult(payload); }
            @Override public void progress(RuleSaveProgressPayload payload) { onProgress(payload); }
            @Override public void catalog(RuleCatalogPayload payload) { onCatalog(payload); }
            @Override public void lifespan(RuleLifespanPayload payload) { onLifespan(payload); }
            @Override public void sourceView(RuleSourceViewPayload payload) { onSourceView(payload); }
            @Override public void queryResult(RuleQueryResultPayload payload) { onQueryResult(payload); }
            @Override public void sessionEvent(RuleSessionEventPayload payload) { onSessionEvent(payload); }
        });
    }

    /** 只解绑当前连接，旧断开事件不清理新连接。 */
    public synchronized void unbindConnection(@Nullable Connection connection) {
        if (connection != null && receiverConnection != connection) { return; }
        RuleEditPayloadRouter.unbind(receiverConnection);
        receiverConnection = null;
        reset();
    }

    /**
     * 分片统一入口：快照、来源视图与变更集共用一条消费者，按 {@link RuleChunkPayload#type()} 的通道 id 分派到各自的重组缓冲。
     * 未知通道直接忽略，避免把别的通道的分片误喂进某个缓冲而挤掉在途批次。
     * @param payload 分片载荷
     */
    private synchronized void onChunk(@Nullable RuleChunkPayload payload) {
        if (payload == null) {
            return;
        }
        ResourceLocation typeId = payload.type().id();
        if (RuleChunkPayload.SNAPSHOT.id().equals(typeId)) {
            onSnapshotChunk(payload);
        } else if (RuleChunkPayload.SOURCE_VIEW.id().equals(typeId)) {
            onSourceViewChunk(payload);
        }
    }

    // 收到整包来源视图：按 requestId 配到在途请求上，失败状态或解析失败都标记降级
    private synchronized void onSourceView(@Nullable RuleSourceViewPayload payload) {
        if (payload == null || !lease.matches(payload.sessionId()) || !sources.pending(payload.requestId())) return;
        RuleSourceView view = RuleSaveStatus.fromId(payload.statusCode()) == RuleSaveStatus.SUCCESS
                ? RuleSourceView.parse(payload.sourceViewJson()).result().orElse(null) : null;
        sources.accept(payload.requestId(), view);
        if (view == null) LOGGER.warn("来源视图取回失败: status={} bytes={}", payload.statusCode(), payload.sourceViewJson().length());
    }

    // 收到来源视图分片：收齐后按整包处理
    private synchronized void onSourceViewChunk(@Nullable RuleChunkPayload payload) {
        if (payload == null || !lease.matches(payload.sessionId())) return;
        String json = sources.chunk(payload, currentTick());
        if (json != null) onSourceView(new RuleSourceViewPayload(payload.sessionId(), payload.correlationId(), RuleSaveStatus.SUCCESS.id(), json));
    }

    private synchronized void onLifespan(RuleLifespanPayload payload) {
        if (lease.matches(payload.sessionId()) && lifespanRequestId.equals(payload.requestId())) {
            queriedLifespanTicks = Math.max(0, payload.lifespanTicks());
        }
    }

    // 首份快照由确认应答提供，不再额外请求，避免替换在途请求后重复准备。
    private synchronized void onOpen(@Nullable OpenRuleEditorPayload payload) {
        if (payload == null) return;
        if (payload.sessionId().isEmpty() || RuleSaveStatus.fromId(payload.statusCode()) != RuleSaveStatus.SUCCESS) {
            LOGGER.info("服务端未授权编辑: status={} holderArgs={}", payload.statusCode(), payload.messageArgs());
            return;
        }
        if (payload.protocolVersion() != RuleEditProtocol.VERSION) {
            LOGGER.warn("编辑协议版本不匹配，拒绝打开编辑器: server={} client={}", payload.protocolVersion(), RuleEditProtocol.VERSION);
            return;
        }
        EditorOpenRequest request = new EditorOpenRequest(payload.sessionId(), payload.targetId(), payload.contextRevision(),
                payload.protocolVersion(), payload.maxPlaceBlockRadius(), payload.statusCode(), payload.messageArgs(), payload.fallbackMessage());
        clearSessionData();
        ticket.reset();
        lease.enter(request, currentTick());
        snapshotPending = true;
        firstLoadStartedNanos = DevelopmentDiagnostics.start();
        send(new ConfirmRuleEditorPayload(lease.id(), RuleEditProtocol.VERSION));
        lease.activate();
        EditorScreenHooks.open(request);
    }

    private synchronized void onSnapshot(@Nullable RuleSnapshotPayload payload) {
        if (payload == null) {
            return;
        }
        if (!lease.matches(payload.sessionId())) {
            LOGGER.debug("忽略非当前会话的快照: session={}", payload.sessionId());
            return;
        }
        if (!payload.requestId().isEmpty() && !payload.requestId().equals(snapshotRequestId)) { return; }
        snapshotRequestId = "";
        snapshotPending = false;
        applySnapshot(payload.snapshotJson(), false);
    }

    // 解析并接纳快照；chunked 表示来自分片重组，仅用于区分接收阶段的观测口径
    private synchronized void applySnapshot(String json, boolean chunked) {
        long parseStarted = DevelopmentDiagnostics.start();
        DataResult<RuleSnapshot> outcome = RuleSnapshot.parse(json);
        RuleSnapshot parsed = outcome.result().orElse(null);
        long parseMicros = DevelopmentDiagnostics.micros(parseStarted);
        if (parsed == null) {
            // 首载解析失败会让界面一直停在「加载中」：把原因留给界面做可见提示
            snapshotFailure = outcome.error().map(DataResult.Error::message).orElse("未知原因");
            LOGGER.warn("规则快照解析失败: bytes={} reason={}", json.length(), snapshotFailure);
            return;
        }
        snapshotFailure = null;
        // 修订变化意味着来源内容可能已被保存或数据包重载改写：来源视图缓存整体失效，按需重新取回
        if (snapshot == null || snapshot.version() != parsed.version()) {
            sources.clear();
        }
        snapshot = parsed;
        lease.revision(parsed.contextRevision());
        if (DevelopmentDiagnostics.enabled()) {
            logSnapshotReceived(json, parsed, chunked, parseMicros);
        }
    }

    // 首载分段观测：首片到收齐、确认到快照、解析耗时与快照规模
    private void logSnapshotReceived(String json, RuleSnapshot parsed, boolean chunked, long parseMicros) {
        JsonObject data = new JsonObject();
        data.addProperty("entries", parsed.entries() == null ? 0 : parsed.entries().size());
        data.addProperty("version", parsed.version());
        data.addProperty("chunked", chunked);
        data.addProperty("chunks", chunked ? snapshotRecvChunks : 0);
        data.addProperty("bytes", RuleEditLimits.encodedLength(json));
        data.addProperty("recv_ms", chunked ? DevelopmentDiagnostics.micros(snapshotRecvStartedNanos) / 1000.0 : 0.0);
        data.addProperty("parse_ms", parseMicros / 1000.0);
        data.addProperty("since_open_ms", DevelopmentDiagnostics.micros(firstLoadStartedNanos) / 1000.0);
        DevelopmentDiagnostics.event(DevelopmentDiagnostics.SCENE_FIRST_LOAD, "CLIENT_RECV", data);
        // 首份快照之后的重载不再计算「打开到快照」，避免把后续保存的等待也算进首载
        firstLoadStartedNanos = 0L;
        snapshotRecvStartedNanos = 0L;
        snapshotRecvTransferId = "";
        snapshotRecvChunks = 0;
    }

    // 收到快照分片：收齐后按整包处理
    private synchronized void onSnapshotChunk(@Nullable RuleChunkPayload payload) {
        if (payload == null || !lease.matches(payload.sessionId())) {
            return;
        }
        if (!payload.correlationId().isEmpty() && !payload.correlationId().equals(snapshotRequestId)) { return; }
        if (DevelopmentDiagnostics.enabled()) {
            // 新传输批次从首片重新计时，避免把上一批的空闲时间算进本次接收
            if (snapshotRecvStartedNanos == 0L || !payload.transferId().equals(snapshotRecvTransferId)) {
                snapshotRecvStartedNanos = DevelopmentDiagnostics.start();
                snapshotRecvTransferId = payload.transferId();
                snapshotRecvChunks = 0;
            }
            snapshotRecvChunks++;
        }
        String json = chunkBuffer.accept(payload, currentTick());
        if (json != null) {
            snapshotRequestId = "";
            snapshotPending = false;
            applySnapshot(json, true);
        }
    }

    private synchronized void onQueryResult(RuleQueryResultPayload payload) {
        if (!lease.matches(payload.sessionId())) return;
        RuleEditRequest.Kind kind;
        try { kind = RuleEditRequest.Kind.valueOf(payload.kind()); }
        catch (IllegalArgumentException unknown) { return; }
        boolean matched = switch (kind) {
            case SNAPSHOT -> {
                boolean pending = snapshotPending && snapshotRequestId.equals(payload.requestId());
                if (pending) {
                    snapshotPending = false;
                    snapshotRequestId = "";
                    chunkBuffer.clear();
                    snapshotFailure = payload.statusCode();
                }
                yield pending;
            }
            case SOURCE_VIEW -> sources.fail(payload.requestId());
            case CATALOG -> directories.fail(payload.requestId());
            case LIFESPAN -> {
                boolean pending = !lifespanRequestId.isEmpty() && lifespanRequestId.equals(payload.requestId());
                if (pending) lifespanRequestId = "";
                yield pending;
            }
        };
        if (matched && sessionLost(RuleSaveStatus.fromId(payload.statusCode()))) invalidateSession();
    }

    private synchronized void onSessionEvent(RuleSessionEventPayload payload) {
        if (lease.matches(payload.sessionId())) invalidateSession();
    }

    private void invalidateSession() {
        if (lease.state() == RuleEditClientState.APPLYING) { lease.invalidate(); cancelSave(); }
        else endSession();
    }

    private static boolean sessionLost(@Nullable RuleSaveStatus status) {
        return status == RuleSaveStatus.SESSION_EXPIRED || status == RuleSaveStatus.LOCK_NOT_OWNED
                || status == RuleSaveStatus.LOCK_BUSY || status == RuleSaveStatus.NO_PERMISSION;
    }

    private synchronized void onProgress(RuleSaveProgressPayload payload) {
        if (lease.state() == RuleEditClientState.APPLYING && lease.matches(payload.sessionId())) ticket.accept(payload);
    }

    // 收到保存回执：会话失效类状态结束会话并关闭界面，其余回到 ACTIVE
    private synchronized void onResult(@Nullable RuleSaveResultPayload payload) {
        if (payload == null || lease.state() != RuleEditClientState.APPLYING
                || !lease.matches(payload.sessionId()) || ticket.rejects(payload.operationId())) return;
        ticket.complete(payload);
        RuleSaveStatus status = RuleSaveStatus.fromId(payload.statusCode());
        if (lease.invalidated() || sessionLost(status)) { endSession(); return; }
        lease.activate();
        lease.revision(payload.resultVersion());
        if (status == RuleSaveStatus.VERSION_CONFLICT || status == RuleSaveStatus.SAVED_NOT_RELOADED) {
            snapshotPending = true;
            snapshotRequestId = "";
        }
        if (needsSnapshotRefresh(status)) requestSnapshot();
    }

    private synchronized void onCatalog(@Nullable RuleCatalogPayload payload) {
        if (payload == null || !lease.matches(payload.sessionId())) return;
        RuleCatalogType type = RuleCatalogType.fromId(payload.catalogType());
        if (type == null || directories.stale(type, payload.requestId())) return;
        RuleCatalog parsed = RuleCatalog.parse(payload.json()).result().orElse(null);
        if (parsed == null) LOGGER.warn("规则目录解析失败: type={} bytes={}", payload.catalogType(), payload.json().length());
        directories.accept(type, payload.requestId(), parsed, lease.canQuery(connected()), this::sendCatalog);
    }

    // 保存回执后是否需要客户端补拉快照：成功与无变化没有随回执附带的快照
    private static boolean needsSnapshotRefresh(@Nullable RuleSaveStatus status) {
        return status == RuleSaveStatus.SUCCESS || status == RuleSaveStatus.NO_CHANGES;
    }

    // 结束会话：清空会话态并关闭界面；界面 close 回调再次进来时直接忽略（防递归）
    private void endSession() {
        if (endingSession) return;
        endingSession = true;
        try {
            boolean hadSession = lease.held();
            lease.end();
            ticket.end();
            clearSessionData();
            if (hadSession) EditorScreenHooks.close();
        } finally { endingSession = false; }
    }

    private boolean sendCatalog(RuleCatalogType type, String requestId, String filter, int page, int pageSize) {
        return send(new RequestRuleCatalogPayload(lease.id(), requestId, type.id(), filter, page, pageSize));
    }

    private void clearSessionData() {
        snapshot = null;
        snapshotFailure = null;
        snapshotRequestId = "";
        snapshotPending = false;
        directories.clear();
        sources.clear();
        chunkBuffer.clear();
        lifespanRequestId = "";
        firstLoadStartedNanos = 0;
        snapshotRecvStartedNanos = 0;
        snapshotRecvTransferId = "";
        snapshotRecvChunks = 0;
    }

    // 当前客户端 tick；未进入世界时返回 0
    private long currentTick() {
        Minecraft client = Minecraft.getInstance();
        return client.level == null ? 0L : client.level.getGameTime();
    }

    // 是否已连接到服务器：未连接时任何上行报文都必须丢弃，否则平台发送器会抛异常
    private boolean connected() {
        Minecraft client = Minecraft.getInstance();
        return client.getConnection() != null;
    }

    // 发送单个上行载荷；未注入发送器时只记日志
    private boolean send(CustomPacketPayload payload) {
        PacketSender current = sender;
        if (current == null) {
            LOGGER.warn("未注入上行发送器，上行载荷被丢弃: {}", payload.type().id());
            return false;
        }
        current.send(payload);
        return true;
    }

    private boolean sendAll(List<CustomPacketPayload> packets) {
        boolean sent = true;
        for (CustomPacketPayload payload : packets) {
            sent &= send(payload);
        }
        return sent;
    }
}
