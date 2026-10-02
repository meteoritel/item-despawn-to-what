package com.meteorite.itemdespawntowhat.client.net;

import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalog;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEditProtocol;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSaveStatus;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshot;
import com.meteorite.itemdespawntowhat.core.network.transport.CloseRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.ConfirmRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.HeartbeatRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.OpenRuleEditorPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RequestRuleCatalogPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RequestRuleSnapshotPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleCatalogPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleChangeSetChunker;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditPayloadRouter;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSaveResultPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSnapshotChunkPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSnapshotPayload;
import com.meteorite.itemdespawntowhat.core.service.RuleEditSessionLimits;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/***
 * 客户端编辑会话工作区：client/net 对界面层的唯一入口（契约 §5.3 的"协议层"一侧）。
 * 职责：镜像 §3.1 的状态机、维持心跳、提交变更集（自动分片）、接收并缓存快照/目录、组装分片、在会话失效时关闭界面。
 * 约束：本包不 import 任何 client/ui 或 client/edit 类；界面实现由 {@link EditorScreenHooks} 注册，未注册时只记日志。
 * 线程：所有方法都在客户端主线程调用（平台接收器已切到客户端线程），内部按单线程加锁。
 */
public final class RuleEditClientWorkspace {

    // 上行发送器：两平台的 ClientPlayNetworking 不同，由客户端初始化时注入
    public interface PacketSender {

        // 发送上行载荷
        void send(CustomPacketPayload payload);
    }

    // 日志
    private static final Logger LOGGER = LogManager.getLogger("itemdespawntowhat-client-net");

    // 单例：客户端只存在一个编辑会话
    private static final RuleEditClientWorkspace INSTANCE = new RuleEditClientWorkspace();

    // 快照分片重组缓冲
    private final RuleSnapshotChunkBuffer chunkBuffer = new RuleSnapshotChunkBuffer();

    // 最近一次收到的目录页，按类型缓存
    private final Map<RuleCatalogType, RuleCatalog> catalogs = new EnumMap<>(RuleCatalogType.class);

    // 上行发送器；未注入时所有上行报文丢弃并记日志
    private @Nullable PacketSender sender;

    // S2C 消费者是否已安装
    private boolean sinksInstalled;

    // 当前状态
    private RuleEditClientState state = RuleEditClientState.FREE;

    // 当前会话 id；无会话时为空串
    private String sessionId = "";

    // 服务端给出的上下文中枢修订号
    private int contextRevision;

    // 最近一次成功打开的请求（供界面层读取会话参数）
    private @Nullable EditorOpenRequest openRequest;

    // 最近一次被拒绝的打开请求（如 LOCK_BUSY），供界面层提示持有者
    private @Nullable EditorOpenRequest openFailure;

    // 最近一次收到的快照
    private @Nullable RuleSnapshot snapshot;

    // 最近一次保存回执
    private @Nullable RuleSaveResultPayload lastResult;

    // 最近一次发送心跳的客户端 tick
    private long lastHeartbeatTick;

    // 会话收尾重入保护：界面 close 回调里若再次调用本工作区的 close/reset，直接忽略
    private boolean endingSession;

    private RuleEditClientWorkspace() {
    }

    // 工作区单例
    public static RuleEditClientWorkspace instance() {
        return INSTANCE;
    }

    // 注入/清除上行发送器（客户端初始化时调用）
    public static void installSender(@Nullable PacketSender sender) {
        INSTANCE.setSender(sender);
    }

    // 安装 S2C 消费者：客户端初始化时调用一次；重复调用安全
    public static void installSinks() {
        INSTANCE.installRouterSinks();
    }

    // 当前会话状态
    public synchronized RuleEditClientState state() {
        return state;
    }

    // 当前是否持有可编辑会话（ACTIVE 或 APPLYING）
    public synchronized boolean active() {
        return state == RuleEditClientState.ACTIVE || state == RuleEditClientState.APPLYING;
    }

    // 当前会话 id；无会话时为空串
    public synchronized String sessionId() {
        return sessionId;
    }

    // 服务端给出的上下文中枢修订号
    public synchronized int contextRevision() {
        return contextRevision;
    }

    // 最近一次成功打开的请求参数；无会话时为 null
    public synchronized @Nullable EditorOpenRequest openRequest() {
        return openRequest;
    }

    // 最近一次被拒绝的打开请求（如 LOCK_BUSY）；未被拒绝过时为 null
    public synchronized @Nullable EditorOpenRequest openFailure() {
        return openFailure;
    }

    // 最近一次收到的快照；尚未收到时为 null
    public synchronized @Nullable RuleSnapshot snapshot() {
        return snapshot;
    }

    // 最近一次保存回执；尚未提交过时为 null
    public synchronized @Nullable RuleSaveResultPayload lastResult() {
        return lastResult;
    }

    // 最近一次收到的指定类型目录页；未请求过时为 null
    public synchronized @Nullable RuleCatalog catalog(RuleCatalogType type) {
        return type == null ? null : catalogs.get(type);
    }

    // 生成新的操作 id（服务端据此做幂等）
    public static String newOperationId() {
        return UUID.randomUUID().toString();
    }

    // 请求最新快照：仅在持有会话时有效
    public synchronized boolean requestSnapshot() {
        if (sessionId.isEmpty() || !connected()) {
            return false;
        }
        return send(new RequestRuleSnapshotPayload(sessionId, UUID.randomUUID().toString()));
    }

    // 请求目录页：仅在持有会话时有效
    public synchronized boolean requestCatalog(RuleCatalogType type, @Nullable String filter, int page, int pageSize) {
        if (sessionId.isEmpty() || type == null || !connected()) {
            return false;
        }
        return send(new RequestRuleCatalogPayload(sessionId, UUID.randomUUID().toString(),
                type.id(), filter == null ? "" : filter, page, pageSize));
    }

    // 提交变更集：超过直发上限时自动分片；返回是否已发出
    public synchronized boolean save(@Nullable String operationId, @Nullable String changeSetJson) {
        if (state != RuleEditClientState.ACTIVE || sessionId.isEmpty() || !connected()) {
            LOGGER.warn("无可用会话，忽略变更集提交: state={}", state);
            return false;
        }
        if (operationId == null || operationId.isEmpty() || changeSetJson == null || changeSetJson.isEmpty()) {
            return false;
        }
        List<CustomPacketPayload> packets = RuleChangeSetChunker.split(sessionId, operationId, changeSetJson);
        if (packets.isEmpty()) {
            LOGGER.warn("变更集为空或超过上限，未提交: bytes={}",
                    com.meteorite.itemdespawntowhat.core.network.transport.RuleEditLimits.encodedLength(changeSetJson));
            return false;
        }
        state = RuleEditClientState.APPLYING;
        return sendAll(packets);
    }

    // 关闭会话：界面关闭或玩家主动退出时调用
    public synchronized void close(@Nullable String reasonCode) {
        if (!sessionId.isEmpty() && connected()) {
            send(new CloseRuleEditorPayload(sessionId, reasonCode == null ? "" : reasonCode));
        }
        endSession();
    }

    // 清空工作区：断线/退出世界时调用（不清除已注入的发送器与消费者）
    public synchronized void reset() {
        endSession();
        lastResult = null;
        openRequest = null;
        openFailure = null;
    }

    // 客户端 tick 钩子：按心跳间隔上行心跳
    public synchronized void tick() {
        if (state == RuleEditClientState.FREE || sessionId.isEmpty()) {
            return;
        }
        if (!connected()) {
            LOGGER.info("客户端已断开服务器连接，重置编辑会话工作区");
            reset();
            return;
        }
        long tick = currentTick();
        if (tick - lastHeartbeatTick >= RuleEditSessionLimits.HEARTBEAT_INTERVAL_TICKS) {
            lastHeartbeatTick = tick;
            send(new HeartbeatRuleEditorPayload(sessionId));
        }
    }

    // 注入发送器
    private synchronized void setSender(@Nullable PacketSender sender) {
        this.sender = sender;
    }

    // 安装 S2C 消费者（幂等）
    private synchronized void installRouterSinks() {
        if (sinksInstalled) {
            return;
        }
        sinksInstalled = true;
        RuleEditPayloadRouter.installSnapshotPayloadSink(this::onSnapshot);
        RuleEditPayloadRouter.installChunkSink(this::onSnapshotChunk);
        RuleEditPayloadRouter.installResultPayloadSink(this::onResult);
        RuleEditPayloadRouter.installCatalogSink(this::onCatalog);
        OpenRuleEditorPayload.installOpenEditorPayloadSink(this::onOpen);
    }

    // 收到打开/拒绝授权：成功则确认、进入 ACTIVE、请求快照并打开界面；失败只记录（界面由服务端指令回执提示）
    private synchronized void onOpen(@Nullable OpenRuleEditorPayload payload) {
        if (payload == null || payload.sessionId().isEmpty()) {
            return;
        }
        EditorOpenRequest request = new EditorOpenRequest(payload.sessionId(), payload.targetId(),
                payload.contextRevision(), payload.protocolVersion(), payload.statusCode(),
                payload.messageArgs(), payload.fallbackMessage());
        if (payload.sessionId().isEmpty()) {
            // 取锁失败（LOCK_BUSY/UNAVAILABLE 等，服务端不带会话号）：只记录失败原因供界面提示
            openFailure = request;
            LOGGER.info("服务端未授权编辑: status={} holderArgs={}", payload.statusCode(), payload.messageArgs());
            return;
        }
        if (payload.protocolVersion() != RuleEditProtocol.VERSION) {
            openFailure = request;
            LOGGER.warn("编辑协议版本不匹配，拒绝打开编辑器: server={} client={}",
                    payload.protocolVersion(), RuleEditProtocol.VERSION);
            return;
        }
        if (RuleSaveStatus.fromId(payload.statusCode()) != RuleSaveStatus.SUCCESS) {
            openFailure = request;
            LOGGER.info("服务端未授权编辑: status={} holderArgs={}", payload.statusCode(), payload.messageArgs());
            return;
        }
        sessionId = payload.sessionId();
        contextRevision = payload.contextRevision();
        openRequest = request;
        openFailure = null;
        snapshot = null;
        catalogs.clear();
        lastResult = null;
        lastHeartbeatTick = currentTick();
        state = RuleEditClientState.OPENING;
        send(new ConfirmRuleEditorPayload(sessionId, RuleEditProtocol.VERSION));
        state = RuleEditClientState.ACTIVE;
        requestSnapshot();
        EditorScreenHooks.open(request);
    }

    // 收到整包快照
    private synchronized void onSnapshot(@Nullable RuleSnapshotPayload payload) {
        if (payload == null) {
            return;
        }
        if (!payload.sessionId().equals(sessionId)) {
            LOGGER.debug("忽略非当前会话的快照: session={}", payload.sessionId());
            return;
        }
        RuleSnapshot parsed = RuleSnapshot.parse(payload.snapshotJson()).result().orElse(null);
        if (parsed == null) {
            LOGGER.warn("规则快照解析失败: bytes={}", payload.snapshotJson().length());
            return;
        }
        snapshot = parsed;
        contextRevision = parsed.contextRevision();
    }

    // 收到快照分片：收齐后按整包处理
    private synchronized void onSnapshotChunk(@Nullable RuleSnapshotChunkPayload payload) {
        if (payload == null || !payload.sessionId().equals(sessionId)) {
            return;
        }
        String json = chunkBuffer.accept(payload, currentTick());
        if (json != null) {
            onSnapshot(new RuleSnapshotPayload(payload.sessionId(), payload.requestId(), json));
        }
    }

    // 收到保存回执：会话失效类状态结束会话并关闭界面，其余回到 ACTIVE
    private synchronized void onResult(@Nullable RuleSaveResultPayload payload) {
        if (payload == null) {
            return;
        }
        if (!payload.sessionId().isEmpty() && !payload.sessionId().equals(sessionId)) {
            LOGGER.debug("忽略非当前会话的回执: session={}", payload.sessionId());
            return;
        }
        lastResult = payload;
        RuleSaveStatus status = RuleSaveStatus.fromId(payload.statusCode());
        if (status == RuleSaveStatus.SESSION_EXPIRED || status == RuleSaveStatus.LOCK_NOT_OWNED
                || status == RuleSaveStatus.LOCK_BUSY) {
            LOGGER.info("编辑会话已失效: status={}", payload.statusCode());
            endSession();
            return;
        }
        // 只有属于当前会话的回执才能推进本地状态与版本号；空会话号的服务端拒绝不得污染上下文修订
        if (!sessionId.isEmpty() && payload.sessionId().equals(sessionId)) {
            if (state == RuleEditClientState.APPLYING) {
                state = RuleEditClientState.ACTIVE;
            }
            contextRevision = payload.resultVersion();
            // 保存成功/无变化时服务端不附送快照，回到 ACTIVE 后主动补一次快照请求，
            // 使列表内容与 contextRevision 同源；VERSION_CONFLICT/SAVED_NOT_RELOADED 由服务端随回执推送，不再重复请求
            if (state == RuleEditClientState.ACTIVE && needsSnapshotRefresh(status)) {
                requestSnapshot();
            }
        }
    }

    // 收到目录页
    private synchronized void onCatalog(@Nullable RuleCatalogPayload payload) {
        if (payload == null || !payload.sessionId().equals(sessionId)) {
            return;
        }
        RuleCatalogType type = RuleCatalogType.fromId(payload.catalogType());
        if (type == null) {
            return;
        }
        RuleCatalog parsed = RuleCatalog.parse(payload.json()).result().orElse(null);
        if (parsed == null) {
            LOGGER.warn("规则目录解析失败: type={} bytes={}", payload.catalogType(), payload.json().length());
            return;
        }
        catalogs.put(type, parsed);
    }

    // 保存回执后是否需要客户端补拉快照：成功与无变化没有随回执附带的快照
    private static boolean needsSnapshotRefresh(@Nullable RuleSaveStatus status) {
        return status == RuleSaveStatus.SUCCESS || status == RuleSaveStatus.NO_CHANGES;
    }

    // 结束会话：清空会话态并关闭界面；界面 close 回调再次进来时直接忽略（防递归）
    private void endSession() {
        if (endingSession) {
            return;
        }
        endingSession = true;
        try {
            boolean hadSession = !sessionId.isEmpty();
            state = RuleEditClientState.FREE;
            sessionId = "";
            contextRevision = 0;
            snapshot = null;
            catalogs.clear();
            chunkBuffer.clear();
            lastHeartbeatTick = 0L;
            if (hadSession) {
                EditorScreenHooks.close();
            }
        } finally {
            endingSession = false;
        }
    }

    // 当前客户端 tick；未进入世界时返回 0
    private long currentTick() {
        Minecraft client = Minecraft.getInstance();
        return client == null || client.level == null ? 0L : client.level.getGameTime();
    }

    // 是否已连接到服务器：未连接时任何上行报文都必须丢弃，否则平台发送器会抛异常
    private boolean connected() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.getConnection() != null;
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

    // 批量发送上行载荷
    private boolean sendAll(List<CustomPacketPayload> packets) {
        boolean sent = true;
        for (CustomPacketPayload payload : packets) {
            sent &= send(payload);
        }
        return sent;
    }
}
