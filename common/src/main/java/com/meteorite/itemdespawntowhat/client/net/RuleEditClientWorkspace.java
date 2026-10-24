package com.meteorite.itemdespawntowhat.client.net;

import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.DevelopmentDiagnostics;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalog;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogEntry;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEditProtocol;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSaveProgress;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSaveStatus;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshot;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSourceView;
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
import com.meteorite.itemdespawntowhat.core.network.transport.RuleChangeSetChunker;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditLimits;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditPayloadRouter;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleLifespanPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSaveProgressPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSaveResultPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSnapshotChunkPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSnapshotPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSourceViewChunkPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSourceViewPayload;
import com.meteorite.itemdespawntowhat.core.service.RuleEditSessionLimits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 客户端编辑会话工作区：client/net 对界面层的唯一入口。
 * 职责：镜像服务端状态机、维持心跳、提交变更集（自动分片）、接收并缓存快照/目录、组装分片、在会话失效时关闭界面。
 * 约束：本包不 import 任何 client/ui 或 client/edit 类；界面实现由 {@link EditorScreenHooks} 注册，未注册时只记日志。
 * 线程：所有方法都在客户端主线程调用（平台接收器已切到客户端线程），内部按单线程加锁。
 */
public final class RuleEditClientWorkspace {

    /** 上行发送器：两平台的 ClientPlayNetworking 不同，由各平台客户端初始化时注入；未注入时上行载荷被丢弃并记 warn。 */
    public interface PacketSender {

        void send(CustomPacketPayload payload);
    }

    /**
     * 单条规则的来源视图加载状态：requestId 非空表示请求在途，view 非空表示已就绪，
     * failed 表示请求失败或超时（编辑页据此降级来源对照）。不对外暴露，界面只读三态查询。
     */
    private static final class SourceViewLoad {
        private String requestId = "";
        private boolean failed;
        private long deadline;
        private @Nullable RuleSourceView view;

        // 请求在途：有请求 id 且尚未失败或超时
        private boolean loading() {
            return !requestId.isEmpty() && !failed;
        }
    }

    /** 保存合并条目和正在等待的页，失败不丢弃已到达的部分。 */
    private static final class DirectoryLoad {
        final LinkedHashMap<String, RuleCatalogEntry> entries = new LinkedHashMap<>();
        int page;
        int revision = -1;
        boolean waiting;
        boolean failed;
        boolean complete;
        boolean validating;
        long deadline;
        RuleCatalog snapshot;
    }

    // ---- 日志与单例 ----
    // 日志
    private static final Logger LOGGER = LogManager.getLogger(Constants.MOD_ID + "-client-net");

    // 单例：客户端只存在一个编辑会话
    private static final RuleEditClientWorkspace INSTANCE = new RuleEditClientWorkspace();

    // ---- 来源视图参数 ----
    // 来源视图缓存上限：只保留最近 8 条，命中不重复请求
    private static final int SOURCE_VIEW_CACHE_LIMIT = 8;
    // 来源视图请求超时：超时即降级为「来源对照不可用」，规则编辑与保存不受影响
    private static final long SOURCE_VIEW_TIMEOUT_MILLIS = 5_000L;

    // ---- 上行通道 ----
    // 上行发送器；未注入时所有上行报文丢弃并记日志
    private @Nullable PacketSender sender;

    // S2C 消费者是否已安装
    private boolean sinksInstalled;

    // ---- 会话状态 ----
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

    // 最近一次快照解析失败的原因；受理新快照或结束会话后清空，供界面区分「还在加载」与「加载失败」
    private @Nullable String snapshotFailure;

    // 最近一次保存回执
    private @Nullable RuleSaveResultPayload lastResult;
    private String savingOperationId = "";
    private @Nullable RuleSaveProgress saveProgress;
    private long progressSequence;
    private boolean cancelRequested;
    // 最近一次发送心跳的客户端 tick
    private long lastHeartbeatTick;
    // 会话收尾重入保护：界面 close 回调里若再次调用本工作区的 close/reset，直接忽略
    private boolean endingSession;

    // ---- 下行分片重组 ----
    // 快照分片重组缓冲
    private final RuleChunkBuffer chunkBuffer = new RuleChunkBuffer();
    // 来源视图分片重组缓冲
    private final RuleChunkBuffer sourceViewChunks = new RuleChunkBuffer();

    // ---- 目录与来源视图缓存 ----
    // 来源视图按需缓存（访问顺序 LRU，最多 8 条）；会话结束整体清空
    private final Map<String, SourceViewLoad> sourceViews = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, SourceViewLoad> eldest) {
            return size() > SOURCE_VIEW_CACHE_LIMIT;
        }
    };
    // 最近一次收到的目录页，按类型缓存
    private final Map<RuleCatalogType, RuleCatalog> catalogs = new EnumMap<>(RuleCatalogType.class);
    // 每种目录最近一次请求的 requestId：用于丢弃过时响应
    private final Map<RuleCatalogType, String> catalogRequestIds = new EnumMap<>(RuleCatalogType.class);
    // 全目录按类型懒加载，单个类型一次只发送一个 200 条请求。
    private final Map<RuleCatalogType, DirectoryLoad> directories = new EnumMap<>(RuleCatalogType.class);

    // ---- 首载分段观测 ----
    // 首载分段观测：确认发出时刻与分片接收进度；关闭开关时不取时间也不计数
    private long firstLoadStartedNanos;
    private long snapshotRecvStartedNanos;
    private String snapshotRecvTransferId = "";
    private int snapshotRecvChunks;

    // ---- 参考寿命采样缓存 ----
    private String lifespanRequestId = "";
    private int queriedLifespanTicks;
    private @Nullable ClientPacketListener lifespanConnection;

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

    // 生成新的操作 id（服务端据此做幂等）
    public static String newOperationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 获取当前连接的统一参考寿命；首次请求后等待应答，切换规则或源物品均复用缓存。
     * @return 参考寿命 tick；尚未采样时为零
     */
    public synchronized int referenceLifespanTicks() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        // 连接身份隔离缓存，即使断线时编辑器已关闭，也不会把参考值带到下一台服务器。
        if (connection != lifespanConnection) {
            queriedLifespanTicks = 0;
            lifespanRequestId = "";
            lifespanConnection = connection;
        }
        if (state != RuleEditClientState.ACTIVE || connection == null) return 0;
        if (queriedLifespanTicks > 0) return queriedLifespanTicks;
        if (lifespanRequestId.isEmpty()) {
            lifespanRequestId = UUID.randomUUID().toString();
            if (!send(new RequestRuleLifespanPayload(sessionId, lifespanRequestId))) lifespanRequestId = "";
        }
        return queriedLifespanTicks;
    }

    /** @return 当前保存进度；无在途保存时为 null */
    public synchronized @Nullable RuleSaveProgress saveProgress() { return saveProgress; }

    /** @return 是否已请求取消并等待服务端确认 */
    public synchronized boolean cancelRequested() { return cancelRequested; }

    /**
     * 请求取消当前保存，不提前结束 APPLYING 或释放会话。
     * @return 取消信号是否已发出
     */
    public synchronized boolean cancelSave() {
        if (state != RuleEditClientState.APPLYING || saveProgress == null || !saveProgress.cancellable()
                || cancelRequested || !connected()) { return false; }
        try {
            if (!send(new CancelRuleSavePayload(sessionId, savingOperationId))) { return false; }
        } catch (RuntimeException failure) {
            LOGGER.warn("发送保存取消请求失败，保持保存状态并等待服务端终态", failure);
            return false;
        }
        cancelRequested = true;
        return true;
    }

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

    // 最近一次快照解析失败的原因；没有失败时为 null
    public synchronized @Nullable String snapshotFailure() {
        return snapshotFailure;
    }

    // 最近一次保存回执；尚未提交过时为 null
    public synchronized @Nullable RuleSaveResultPayload lastResult() {
        return lastResult;
    }

    // 最近一次收到的指定类型目录页；未请求过时为 null
    public synchronized @Nullable RuleCatalog catalog(RuleCatalogType type) {
        return type == null ? null : catalogs.get(type);
    }

    public synchronized @Nullable RuleCatalog directory(RuleCatalogType type) {
        if (type == null || sessionId.isEmpty()) return null;
        DirectoryLoad load = directories.computeIfAbsent(type, ignored -> new DirectoryLoad());
        if (load.waiting && System.currentTimeMillis() > load.deadline) { load.failed = true; load.waiting = false; }
        if (!load.waiting && !load.failed && !load.complete && state == RuleEditClientState.ACTIVE) requestDirectory(type, load);
        return load.snapshot;
    }

    // 重新打开目录时只探测第一页修订号；未变化则继续复用完整缓存。
    public synchronized void refreshDirectory(RuleCatalogType type) {
        DirectoryLoad load = directories.get(type);
        if (load == null || !load.complete || load.waiting) { directory(type); return; }
        load.validating = true; load.page = 0;
        requestDirectory(type, load);
    }

    public synchronized boolean directoryFailed(RuleCatalogType type) {
        DirectoryLoad load = directories.get(type);
        return load != null && load.failed;
    }

    public synchronized void retryDirectory(RuleCatalogType type) {
        DirectoryLoad load = directories.get(type);
        if (load != null) { load.failed = false; load.waiting = false; requestDirectory(type, load); }
        else directory(type);
    }

    public synchronized void invalidateDirectories() { directories.clear(); }

    // 请求最新快照：仅在持有会话时有效
    public synchronized boolean requestSnapshot() {
        if (sessionId.isEmpty() || !connected()) {
            return false;
        }
        return send(new RequestRuleSnapshotPayload(sessionId, UUID.randomUUID().toString()));
    }

    // ---- 来源视图（按需加载） ----

    /**
     * 请求某条规则的来源视图：编辑页打开规则时调用；已就绪或在途时不重复请求，
     * 失败条目在新一次打开时重新请求，界面也可显式调用 {@link #retrySourceView(String)}。
     *
     * @param ruleId 规则 id；空值或会话不可用时忽略
     */
    public synchronized void requestSourceView(@Nullable String ruleId) {
        if (ruleId == null || ruleId.isEmpty() || state != RuleEditClientState.ACTIVE
                || sessionId.isEmpty() || !connected()) {
            return;
        }
        SourceViewLoad existing = sourceViews.get(ruleId);
        if (existing != null && (existing.view != null || existing.loading())) {
            return;
        }
        SourceViewLoad load = new SourceViewLoad();
        sourceViews.put(ruleId, load);
        sendSourceViewRequest(ruleId, load);
    }

    /**
     * 读取已取回的来源视图。
     *
     * @param ruleId 规则 id
     * @return 来源视图；尚未取回、失败或超时时为 null
     */
    public synchronized @Nullable RuleSourceView sourceView(@Nullable String ruleId) {
        SourceViewLoad load = ruleId == null ? null : sourceViews.get(ruleId);
        if (load == null) {
            return null;
        }
        expireSourceView(load);
        return load.view;
    }

    /**
     * @param ruleId 规则 id
     * @return 是否正在等待来源视图应答
     */
    public synchronized boolean sourceViewLoading(@Nullable String ruleId) {
        SourceViewLoad load = ruleId == null ? null : sourceViews.get(ruleId);
        if (load == null) {
            return false;
        }
        expireSourceView(load);
        return load.loading();
    }

    /**
     * @param ruleId 规则 id
     * @return 来源视图是否请求失败或超时（来源对照不可用）
     */
    public synchronized boolean sourceViewFailed(@Nullable String ruleId) {
        SourceViewLoad load = ruleId == null ? null : sourceViews.get(ruleId);
        if (load == null) {
            return false;
        }
        expireSourceView(load);
        return load.failed;
    }

    /**
     * 重试失败的来源视图请求；成功取回前来源对照继续保持不可用。
     *
     * @param ruleId 规则 id；空值或会话不可用时忽略
     */
    public synchronized void retrySourceView(@Nullable String ruleId) {
        if (ruleId == null || ruleId.isEmpty() || state != RuleEditClientState.ACTIVE
                || sessionId.isEmpty() || !connected()) {
            return;
        }
        SourceViewLoad load = sourceViews.get(ruleId);
        if (load == null) {
            requestSourceView(ruleId);
            return;
        }
        if (load.view != null || load.loading()) {
            return;
        }
        load.failed = false;
        sendSourceViewRequest(ruleId, load);
    }

    // 请求目录页：仅在持有会话时有效
    public synchronized boolean requestCatalog(RuleCatalogType type, @Nullable String filter, int page, int pageSize) {
        if (sessionId.isEmpty() || type == null || !connected()) {
            return false;
        }
        String requestId = UUID.randomUUID().toString();
        if (!send(new RequestRuleCatalogPayload(sessionId, requestId, type.id(), filter == null ? "" : filter, page,
                pageSize))) {
            return false;
        }
        catalogRequestIds.put(type, requestId);
        return true;
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
                    RuleEditLimits.encodedLength(changeSetJson));
            return false;
        }
        if (sender == null) { return false; }
        savingOperationId = operationId;
        progressSequence = 0;
        cancelRequested = false;
        saveProgress = new RuleSaveProgress(RuleSaveProgress.Stage.PREPARING, 0, 0, "", true);
        lastResult = null;
        state = RuleEditClientState.APPLYING;
        try { return sendAll(packets); }
        catch (RuntimeException failure) {
            LOGGER.warn("发送保存请求失败，等待连接状态或服务端取消确认", failure);
            if (!connected()) { reset(); return false; }
            cancelSave();
            return true;
        }
    }

    // 关闭会话：界面关闭或玩家主动退出时调用
    public synchronized void close(@Nullable String reasonCode) {
        if (state == RuleEditClientState.APPLYING) { return; }
        if (!sessionId.isEmpty() && connected()) {
            send(new CloseRuleEditorPayload(sessionId, reasonCode == null ? "" : reasonCode));
        }
        endSession();
    }

    // 清空工作区：断线/退出世界时调用（不清除已注入的发送器与消费者）
    public synchronized void reset() {
        endSession();
        queriedLifespanTicks = 0;
        lifespanConnection = null;
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
        RuleEditPayloadRouter.installProgressSink(this::onProgress);
        RuleEditPayloadRouter.installCatalogSink(this::onCatalog);
        RuleEditPayloadRouter.installLifespanSink(this::onLifespan);
        RuleEditPayloadRouter.installSourceViewSink(this::onSourceView);
        RuleEditPayloadRouter.installSourceViewChunkSink(this::onSourceViewChunk);
        OpenRuleEditorPayload.installOpenEditorPayloadSink(this::onOpen);
    }

    private void requestDirectory(RuleCatalogType type, DirectoryLoad load) {
        load.waiting = requestCatalog(type, "", load.page, 200);
        load.failed = !load.waiting;
        load.deadline = System.currentTimeMillis() + 15000;
    }

    private void acceptDirectory(RuleCatalogType type, RuleCatalog catalog) {
        DirectoryLoad load = directories.get(type);
        if (load == null || !load.waiting) return;
        load.waiting = false;
        if (load.validating && load.revision == catalog.revision()) { load.validating = false; return; }
        load.validating = false;
        if (load.revision >= 0 && load.revision != catalog.revision()) {
            load.entries.clear(); load.page = 0; load.revision = -1; load.snapshot = null; load.complete = false;
            requestDirectory(type, load); return;
        }
        load.revision = catalog.revision();
        catalog.entries().forEach(entry -> load.entries.putIfAbsent(entry.id(), entry));
        load.complete = catalog.lastPage();
        load.snapshot = new RuleCatalog(type, load.revision, List.copyOf(load.entries.values()), load.complete);
        load.page++;
        if (!load.complete && state == RuleEditClientState.ACTIVE) requestDirectory(type, load);
    }

    // 发送来源视图请求；发送失败直接标记失败，避免界面停留在「加载中」
    private void sendSourceViewRequest(String ruleId, SourceViewLoad load) {
        String requestId = UUID.randomUUID().toString();
        load.view = null;
        load.failed = false;
        load.deadline = 0L;
        if (!send(new RequestRuleSourceViewPayload(sessionId, requestId, ruleId))) {
            load.requestId = "";
            load.failed = true;
            return;
        }
        load.requestId = requestId;
        load.deadline = System.currentTimeMillis() + SOURCE_VIEW_TIMEOUT_MILLIS;
    }

    // 超时丢弃：在途请求超过期限即降级为失败，由界面提示来源对照不可用
    private static void expireSourceView(SourceViewLoad load) {
        if (load.loading() && System.currentTimeMillis() > load.deadline) {
            load.requestId = "";
            load.failed = true;
            load.deadline = 0L;
        }
    }

    // 收到整包来源视图：按 requestId 配到在途请求上，失败状态或解析失败都标记降级
    private synchronized void onSourceView(@Nullable RuleSourceViewPayload payload) {
        if (payload == null || !payload.sessionId().equals(sessionId)) {
            return;
        }
        SourceViewLoad load = pendingSourceView(payload.requestId());
        if (load == null) {
            LOGGER.debug("忽略过时的来源视图应答: requestId={}", payload.requestId());
            return;
        }
        load.requestId = "";
        load.deadline = 0L;
        RuleSourceView view = RuleSaveStatus.fromId(payload.statusCode()) == RuleSaveStatus.SUCCESS
                ? RuleSourceView.parse(payload.sourceViewJson()).result().orElse(null)
                : null;
        load.view = view;
        load.failed = view == null;
        if (view == null) {
            LOGGER.warn("来源视图取回失败: status={} bytes={}", payload.statusCode(),
                    payload.sourceViewJson().length());
        }
    }

    // 收到来源视图分片：收齐后按整包处理
    private synchronized void onSourceViewChunk(@Nullable RuleSourceViewChunkPayload payload) {
        if (payload == null || !payload.sessionId().equals(sessionId)) {
            return;
        }
        String json = sourceViewChunks.accept(payload, currentTick());
        if (json != null) {
            onSourceView(new RuleSourceViewPayload(payload.sessionId(), payload.requestId(),
                    RuleSaveStatus.SUCCESS.id(), json));
        }
    }

    // 按请求 id 找在途的来源视图缓存项；找不到即视为过时应答
    private @Nullable SourceViewLoad pendingSourceView(@Nullable String requestId) {
        if (requestId == null || requestId.isEmpty()) {
            return null;
        }
        for (SourceViewLoad load : sourceViews.values()) {
            if (requestId.equals(load.requestId)) {
                return load;
            }
        }
        return null;
    }

    private synchronized void onLifespan(RuleLifespanPayload payload) {
        if (!sessionId.isEmpty() && sessionId.equals(payload.sessionId()) && lifespanRequestId.equals(payload.requestId())) {
            queriedLifespanTicks = Math.max(0, payload.lifespanTicks());
        }
    }

    // 首份快照由确认应答提供，不再额外请求，避免替换在途请求后重复准备。
    private synchronized void onOpen(@Nullable OpenRuleEditorPayload payload) {
        if (payload == null) {
            return;
        }
        EditorOpenRequest request = new EditorOpenRequest(payload.sessionId(), payload.targetId(),
                payload.contextRevision(), payload.protocolVersion(), payload.maxPlaceBlockRadius(), payload.statusCode(),
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
        snapshotFailure = null;
        snapshotRecvStartedNanos = 0L;
        snapshotRecvTransferId = "";
        snapshotRecvChunks = 0;
        catalogs.clear();
        directories.clear();
        catalogRequestIds.clear();
        sourceViews.clear();
        sourceViewChunks.clear();
        lastResult = null;
        lastHeartbeatTick = currentTick();
        state = RuleEditClientState.OPENING;
        // 首载起点取确认发出时刻：客户端此后只在等快照，端到端耗时可与同一行日志对读
        firstLoadStartedNanos = DevelopmentDiagnostics.start();
        send(new ConfirmRuleEditorPayload(sessionId, RuleEditProtocol.VERSION));
        state = RuleEditClientState.ACTIVE;
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
            snapshotFailure = outcome.error().map(error -> error.message()).orElse("未知原因");
            LOGGER.warn("规则快照解析失败: bytes={} reason={}", json.length(), snapshotFailure);
            return;
        }
        snapshotFailure = null;
        // 修订变化意味着来源内容可能已被保存或数据包重载改写：来源视图缓存整体失效，按需重新取回
        if (snapshot == null || snapshot.version() != parsed.version()) {
            sourceViews.clear();
            sourceViewChunks.clear();
        }
        snapshot = parsed;
        contextRevision = parsed.contextRevision();
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
    private synchronized void onSnapshotChunk(@Nullable RuleSnapshotChunkPayload payload) {
        if (payload == null || !payload.sessionId().equals(sessionId)) {
            return;
        }
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
            applySnapshot(json, true);
        }
    }

    private synchronized void onProgress(RuleSaveProgressPayload payload) {
        if (state != RuleEditClientState.APPLYING || !sessionId.equals(payload.sessionId())
                || !savingOperationId.equals(payload.operationId()) || payload.sequence() <= progressSequence) { return; }
        progressSequence = payload.sequence();
        saveProgress = payload.progress();
        if (saveProgress.stage() != RuleSaveProgress.Stage.CANCELLING
                && saveProgress.stage() != RuleSaveProgress.Stage.ROLLING_BACK && !saveProgress.cancellable()) {
            // 提交确认先于取消信号：继续显示保存完成阶段，不显示取消成功。
            cancelRequested = false;
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
        if (state == RuleEditClientState.APPLYING) {
            if (!sessionId.equals(payload.sessionId()) || !savingOperationId.equals(payload.operationId())) { return; }
        } else if (!payload.operationId().isEmpty()) {
            // 本次保存已结束；迟到的重复回执不能覆盖后续请求或版本。
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
                savingOperationId = "";
                saveProgress = null;
                cancelRequested = false;
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
        // 过时响应直接丢弃：只接受与本类型最近一次请求 requestId 相符的响应；
        // requestId 缺失（旧路径/兼容）时退回仅按 sessionId 接受
        String latestRequestId = catalogRequestIds.get(type);
        String responseRequestId = payload.requestId();
        if (latestRequestId != null && responseRequestId != null && !responseRequestId.isEmpty()
                && !latestRequestId.equals(responseRequestId)) {
            LOGGER.debug("忽略过时目录响应: type={} requestId={} latest={}", payload.catalogType(), responseRequestId,
                    latestRequestId);
            return;
        }
        catalogs.put(type, parsed);
        acceptDirectory(type, parsed);
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
            savingOperationId = "";
            saveProgress = null;
            progressSequence = 0;
            cancelRequested = false;
            sessionId = "";
            contextRevision = 0;
            snapshot = null;
            snapshotFailure = null;
            catalogs.clear();
            directories.clear();
            catalogRequestIds.clear();
            sourceViews.clear();
            sourceViewChunks.clear();
            lifespanRequestId = "";
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

    // 批量发送上行载荷
    private boolean sendAll(List<CustomPacketPayload> packets) {
        boolean sent = true;
        for (CustomPacketPayload payload : packets) {
            sent &= send(payload);
        }
        return sent;
    }
}
