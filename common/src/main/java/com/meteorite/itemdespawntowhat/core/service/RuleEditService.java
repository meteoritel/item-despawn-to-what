package com.meteorite.itemdespawntowhat.core.service;

import com.meteorite.itemdespawntowhat.core.api.RuleRuntimeLimits;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalogService;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalog;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalogType;
import com.meteorite.itemdespawntowhat.core.service.edit.*;
import com.meteorite.itemdespawntowhat.core.api.Issue;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.IssueSeverity;
import com.meteorite.itemdespawntowhat.core.load.RuleSourceIndex;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleIssue;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleSaveStatus;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleSaveProgress;
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
import java.util.function.Predicate;

/**
 * 独占编辑会话的业务编排：打开/确认/心跳/关闭/快照/目录/保存。
 * 本类产出语义应答；主线程持有可变业务状态，后台只交付文件和装配结果。
 * 所有授权检查点都校验 sessionId 与持有者，失败回 SESSION_EXPIRED / LOCK_NOT_OWNED。
 * 时钟一律使用服务端活动 tick，由调用方（tick 事件）传入。
 */
public final class RuleEditService {

    private static final Logger LOGGER = LogManager.getLogger();

    // 独占会话、覆盖层文件与本次服务端的目录
    private final EditSessionManager sessions;
    private final RuleOverlayStore store;
    private final RuleCatalogService catalogs;
    private volatile boolean closed;
    // 统一准入：会话号 → 持有者 → 确认状态 → 编辑权限
    private final EditAdmission admission;
    // 幂等回执：同一会话最近 MAX_OPERATION_HISTORY 个 operationId -> 上次回执
    private final Map<String, RuleEditReply.Save> recentResults = new LinkedHashMap<>();
    // 幂等表所属会话；会话变化即整体失效
    private String resultsSessionId;
    private @Nullable UUID lifespanOwner;
    private @Nullable RuleEditRequest.Lifespan pendingLifespan;
    private RuleSaveTask pendingSave;
    private String savingSessionId;
    private String savingOperationId;
    private UUID savingOwner;
    private RuleEditReply.Save completedSave;
    private static final int PROGRESS_INTERVAL_TICKS = 5;
    private long progressTick;
    private long progressSequence;
    private RuleSaveProgress sentProgress;
    private Runnable deferredReload;
    private RuleSnapshotTask pendingSnapshot;
    // 当前编辑来源视图：快照或独立来源读取成功后发布，按修订与数据包上下文复用
    private @Nullable RuleSourceIndex sourceIndex;
    private int sourceVersion = -1;
    private RuleSaveReloadContext sourceContext;
    private final java.util.ArrayDeque<SaveReply> sourceReplies = new java.util.ArrayDeque<>();
    private SnapshotRequest requestedSnapshot;
    private SnapshotRequest deliveringSnapshot;
    private RuleEditReply completedSnapshot;
    private record SnapshotRequest(UUID owner, String sessionId, String requestId) { }

    /**
     * 已采样后需要交付给玩家的参考寿命应答。
     * @param player 应答接收者
     * @param payload 缓存的寿命应答
     */
    public record LifespanReply(ServerPlayer player, RuleEditReply.Lifespan payload) { }


    /**
     * 构造编排服务：会话状态机由调用方注入；加载与发布能力经业务上下文传入
     */
    public RuleEditService(EditSessionManager sessions, RuleOverlayStore store,
                           RuleCatalogService catalogs, Predicate<ServerPlayer> permitted) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.store = Objects.requireNonNull(store, "store");
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
        this.admission = new EditAdmission(sessions, Objects.requireNonNull(permitted, "permitted"));
    }

    /**
     * 底层的会话状态机（命令层查询状态用）
     */
    public EditSessionManager sessions() {
        return sessions;
    }

    /**
     * 判断覆盖层是否被保存或恢复隔离占用，供磁盘读取入口避免观察不完整目录。
     * @return 是否应暂缓覆盖层读取
     */
    public boolean saving() { return pendingSave != null || store.unavailable(); }

    /**
     * 一次请求产生的语义应答集合。
     */
    public record Outbound(List<RuleEditReply> replies) {

        public static final Outbound EMPTY = new Outbound(List.of());

        // 便利工厂：可变参数按传入顺序组装成不可变应答集合
        public static Outbound of(RuleEditReply... replies) {
            return new Outbound(List.of(replies));
        }

        // 拷贝入参列表，保证业务应答集合不可变
        public static Outbound of(List<RuleEditReply> replies) {
            return new Outbound(List.copyOf(replies));
        }
    }

    /**
     * 打开结果：成功时 acquired=true，失败时携带当前持有者名字。
     */
    public record OpenOutcome(boolean acquired, String holderName, RuleEditReply.Open payload) {
    }

    /**
     * 超时释放结果：target 为需要被通知的玩家（无人可通知时为 null），outbound 为待交付的语义应答。
     */
    public record Expired(@Nullable ServerPlayer target, Outbound outbound) {
    }

    /** 执行已解码的业务请求；调用方在本次服务端主线程上提交。 */
    public Outbound handle(ServerPlayer player, RuleAuthoringContext context, MinecraftServer server, RuleEditRequest request, long tick) {
        return switch (request) {
            case RuleEditRequest.Confirm value -> confirm(player, context, server, value, tick);
            case RuleEditRequest.Heartbeat value -> heartbeat(player, value, tick);
            case RuleEditRequest.Close value -> close(player, value);
            case RuleEditRequest.Snapshot value -> requestSnapshot(player, context, server, value, tick);
            case RuleEditRequest.Catalog value -> requestCatalog(player, value, tick);
            case RuleEditRequest.SourceView value -> requestSourceView(player, context, server, value, tick);
            case RuleEditRequest.Lifespan value -> requestLifespan(player, context, value, tick);
            case RuleEditRequest.Save value -> submitChangeSet(player, context, server, value, tick);
            case RuleEditRequest.Cancel value -> cancelSave(player, value);
        };
    }

    /**
     * 「打开编辑器」唯一入口：先原子取锁，成功或失败都要把结论下发给客户端
     */
    public OpenOutcome openEditor(ServerPlayer player, long tick, RuleRuntimeLimits limits) {
        EditSessionManager.AcquireResult acquired =
                sessions.acquire(player.getUUID(), player.getName().getString(), tick);
        if (acquired.acquired()) {
            RuleEditReply.Open payload = new RuleEditReply.Open(acquired.session().sessionId(),
                    RuleEditSessionLimits.TARGET_ID, store.version(), limits, RuleSaveStatus.SUCCESS, List.of());
            return new OpenOutcome(true, null, payload);
        }
        String holder = acquired.holderName() == null ? "" : acquired.holderName();
        RuleEditReply.Open payload = new RuleEditReply.Open("", RuleEditSessionLimits.TARGET_ID,
                store.version(), limits, RuleSaveStatus.LOCK_BUSY, args(holder));
        return new OpenOutcome(false, holder, payload);
    }

    /**
     * C2S 确认：校验协议版本与会话，成功后进入 ACTIVE 并回发最新快照
     */
    public Outbound confirm(ServerPlayer player, RuleAuthoringContext context, MinecraftServer server,
                            RuleEditRequest.Confirm payload, long tick) {
        EditAdmission.Refusal refusal = admission.admitOwner(player, payload.sessionId());
        if (refusal != null) {
            return Outbound.of(lost(refusal, payload.sessionId(), ""));
        }
        if (!sessions.confirm(payload.sessionId(), tick)) {
            return Outbound.of(sessionExpired(payload.sessionId(), ""));
        }
        return snapshotOutbound(context, server, payload.sessionId(), "");
    }

    /**
     * C2S 心跳：成功不回执，失败回 SESSION_EXPIRED（客户端据此回到未持有状态）
     */
    public Outbound heartbeat(ServerPlayer player, RuleEditRequest.Heartbeat payload, long tick) {
        EditAdmission.Refusal refusal = admission.admitMember(player, payload.sessionId());
        if (refusal != null) {
            return Outbound.of(lost(refusal, payload.sessionId(), ""));
        }
        sessions.heartbeat(payload.sessionId(), tick);
        return Outbound.EMPTY;
    }

    /**
     * C2S 关闭：保存期间延后释放，回滚与清理结束后才发送终态
     */
    public Outbound close(ServerPlayer player, RuleEditRequest.Close payload) {
        EditAdmission.Refusal refusal = admission.admitMember(player, payload.sessionId());
        if (refusal != null) {
            return Outbound.of(lost(refusal, payload.sessionId(), ""));
        }
        if (pendingSave != null) { forceRelease(); return Outbound.EMPTY; }
        sessions.release();
        return Outbound.of(new RuleEditReply.SessionEvent(payload.sessionId(), RuleSaveStatus.SUCCESS));
    }

    /**
     * C2S 快照请求：必须是当前会话持有者
     */
    public Outbound requestSnapshot(ServerPlayer player, RuleAuthoringContext context, MinecraftServer server,
                                    RuleEditRequest.Snapshot payload, long tick) {
        EditAdmission.Refusal refusal = admission.admitConfirmed(player, payload.sessionId());
        if (refusal != null) {
            // 未确认的 OPENING 会话不接受快照请求，避免绕过确认握手
            return Outbound.of(new RuleEditReply.QueryResult(RuleEditRequest.Kind.SNAPSHOT, payload.sessionId(), payload.requestId(), refusal.status()));
        }
        sessions.heartbeat(payload.sessionId(), tick);
        return snapshotOutbound(context, server, payload.sessionId(), payload.requestId());
    }

    /**
     * C2S 目录请求：按来源分层缓存分页下发；来源实现为空时返回空目录 + lastPage=true
     */
    public Outbound requestCatalog(ServerPlayer player, RuleEditRequest.Catalog payload, long tick) {
        EditAdmission.Refusal refusal = admission.admitConfirmed(player, payload.sessionId());
        if (refusal != null) {
            // 会话状态优先于请求参数：未确认的 OPENING 会话不接受目录请求
            return Outbound.of(new RuleEditReply.QueryResult(RuleEditRequest.Kind.CATALOG, payload.sessionId(), payload.requestId(), refusal.status()));
        }
        if (payload.page() < 0 || payload.pageSize() <= 0
                || payload.pageSize() > RuleCatalogService.MAX_PAGE_SIZE) {
            return Outbound.of(new RuleEditReply.QueryResult(RuleEditRequest.Kind.CATALOG, payload.sessionId(), payload.requestId(), RuleSaveStatus.INVALID_REQUEST));
        }
        RuleCatalogType type = RuleCatalogType.fromId(payload.catalogType());
        if (type == null) {
            return Outbound.of(new RuleEditReply.QueryResult(RuleEditRequest.Kind.CATALOG, payload.sessionId(), payload.requestId(), RuleSaveStatus.INVALID_REQUEST));
        }
        sessions.heartbeat(payload.sessionId(), tick);
        RuleCatalog catalog = catalogs.page(type, payload.filter(),
                payload.page(), payload.pageSize());
        return Outbound.of(new RuleEditReply.Catalog(payload.sessionId(), payload.requestId(), catalog));
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
    public Outbound requestSourceView(ServerPlayer player, RuleAuthoringContext context, MinecraftServer server, RuleEditRequest.SourceView payload, long tick) {
        EditAdmission.Refusal refusal = admission.admitConfirmed(player, payload.sessionId());
        if (refusal != null) {
            return Outbound.of(new RuleEditReply.QueryResult(RuleEditRequest.Kind.SOURCE_VIEW, payload.sessionId(), payload.requestId(), refusal.status()));
        }
        sessions.heartbeat(payload.sessionId(), tick);
        ResourceLocation id = ResourceLocation.tryParse(payload.ruleId());
        if (id == null) {
            return Outbound.of(sourceViewFailure(payload, RuleSaveStatus.INVALID_REQUEST));
        }
        if (sourceIndex != null && sourceVersion == store.version() && sourceContext == context.saveReloadContext()) {
            return sourceView(payload, sourceIndex.find(id));
        }
        readSources(context, server, sources -> {
            EditAdmission.Refusal currentRefusal = admission.admitConfirmed(player, payload.sessionId());
            if (currentRefusal != null) {
                sourceReplies.add(new SaveReply(player, Outbound.of(sourceViewFailure(payload, currentRefusal.status()))));
                return;
            }
            if (sources == null) {
                sourceReplies.add(new SaveReply(player, Outbound.of(sourceViewFailure(payload, RuleSaveStatus.UNAVAILABLE))));
                return;
            }
            // 已显示的快照修订变化时先刷新快照，来源请求由客户端随新快照重新发起。
            if (sourceIndex != null && sourceVersion != store.version()) {
                snapshotOutbound(context, server, payload.sessionId(), "");
                sourceReplies.add(new SaveReply(player, Outbound.of(sourceViewFailure(payload, RuleSaveStatus.VERSION_CONFLICT))));
                return;
            }
            sourceIndex = sources.read().index(); sourceVersion = store.version(); sourceContext = context.saveReloadContext();
            sourceReplies.add(new SaveReply(player, sourceView(payload, sources.read().index().find(id))));
        });
        return Outbound.EMPTY;
    }

    private Outbound sourceView(RuleEditRequest.SourceView payload, RuleSourceIndex.Entry entry) {
        if (entry == null) { return Outbound.of(sourceViewFailure(payload, RuleSaveStatus.INVALID_REQUEST)); }
        return Outbound.of(new RuleEditReply.SourceView(payload.sessionId(), payload.requestId(), RuleSnapshotAssembler.sourceView(entry)));
    }

    /** 交付按需来源读取已完成的应答。 */
    public @Nullable SaveReply pollSource() { return sourceReplies.pollFirst(); }

    /** 命令详情的原始来源结果；JSON 在文件队列序列化，启用覆盖由命令单独展示。 */
    public record RawRuleResult(RuleSaveStatus status, String json, String origin) { }

    /** 按 id 异步读取原始来源；最终反馈回到本次服务端主线程。 */
    public void readRawRule(RuleAuthoringContext context, MinecraftServer server, ResourceLocation id,
                            java.util.function.Consumer<RawRuleResult> completion) {
        RuleSaveReloadContext captured = context.saveReloadContext();
        if (captured == null || saving()) { completion.accept(new RawRuleResult(RuleSaveStatus.UNAVAILABLE, "", "")); return; }
        store.submit("rule-show", () -> {
            RuleOverlayStore.Sources sources = store.readSources(captured, () -> !closed);
            RuleSourceIndex.Entry entry = sources.read().index().find(id);
            var winner = entry == null ? null : entry.overlay() != null ? entry.overlay() : entry.base();
            return new RawRuleResult(winner == null ? RuleSaveStatus.INVALID_REQUEST : RuleSaveStatus.SUCCESS,
                    winner == null ? "" : winner.body().toString(), winner == null ? "" : winner.origin().display());
        }).whenComplete((result, failure) -> server.execute(() -> {
            if (closed) { return; }
            completion.accept(failure != null || context.saveReloadContext() != captured || saving()
                    ? new RawRuleResult(RuleSaveStatus.UNAVAILABLE, "", "") : result);
        }));
    }

    private void readSources(RuleAuthoringContext context, MinecraftServer server,
                              java.util.function.Consumer<RuleOverlayStore.Sources> completion) {
        RuleSaveReloadContext captured = context.saveReloadContext();
        if (captured == null || saving()) { completion.accept(null); return; }
        store.submit("source-read", () -> store.readSources(captured, () -> !closed))
                .whenComplete((sources, failure) -> server.execute(() -> {
                    if (closed) { return; }
                    if (failure != null || context.saveReloadContext() != captured || saving()) { completion.accept(null); return; }
                    store.acceptRevision(sources.revision(), false);
                    completion.accept(sources);
                }));
    }

    // 来源视图失败回执：空 JSON + 状态码，客户端只按状态码提示
    private static RuleEditReply.QueryResult sourceViewFailure(RuleEditRequest.SourceView payload, RuleSaveStatus status) {
        return new RuleEditReply.QueryResult(RuleEditRequest.Kind.SOURCE_VIEW, payload.sessionId(), payload.requestId(), status);
    }

    /**
     * 获取服务端缓存的参考寿命；尚未采样时保留最近请求并先应答零。
     * @param player 会话持有者
     * @param context 平台参考寿命来源
     * @param payload 参考寿命请求
     * @param tick 服务端活动刻
     * @return 缓存应答或会话失败回执
     */
    public Outbound requestLifespan(ServerPlayer player, RuleAuthoringContext context,
            RuleEditRequest.Lifespan payload, long tick) {
        EditAdmission.Refusal refusal = admission.admitConfirmed(player, payload.sessionId());
        if (refusal != null) {
            return Outbound.of(new RuleEditReply.QueryResult(RuleEditRequest.Kind.LIFESPAN, payload.sessionId(), payload.requestId(), refusal.status()));
        }
        sessions.heartbeat(payload.sessionId(), tick);
        int ticks = context.referenceLifespanTicks();
        pendingLifespan = ticks == 0 ? payload : null;
        lifespanOwner = ticks == 0 ? player.getUUID() : null;
        return Outbound.of(new RuleEditReply.Lifespan(payload.sessionId(), payload.requestId(), ticks));
    }

    /**
     * 平台完成首次采样后补发一次结果；已结束的会话不会接收过期应答。
     * @param server 当前服务端
     * @param context 平台参考寿命来源
     * @return 可交付应答；未采样或会话无效时为空
     */
    public @Nullable LifespanReply pollLifespan(MinecraftServer server, RuleAuthoringContext context) {
        RuleEditRequest.Lifespan pending = pendingLifespan;
        UUID owner = lifespanOwner;
        if (pending == null || owner == null) return null;
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (admission.admitConfirmed(player, pending.sessionId()) != null) {
            pendingLifespan = null;
            lifespanOwner = null;
            return null;
        }
        int ticks = context.referenceLifespanTicks();
        if (ticks == 0) return null;
        pendingLifespan = null;
        lifespanOwner = null;
        return player == null ? null : new LifespanReply(player,
                new RuleEditReply.Lifespan(pending.sessionId(), pending.requestId(), ticks));
    }

    /**
     * C2S 变更集：完整的保存编排（幂等 → 会话/权限 → 版本戳 → 校验 → 落盘 → 重载 → 回执 + 新快照）
     */
    public Outbound submitChangeSet(ServerPlayer player, RuleAuthoringContext context, MinecraftServer server,
                                    RuleEditRequest.Save payload, long tick) {
        return submit(player, context, server, payload.sessionId(), payload.operationId(),
                payload.text(), tick);
    }

    /** 分片进入容器前复用业务准入与幂等，不把未收齐文本交给保存任务。 */
    public TransferAdmission admitTransfer(ServerPlayer player, String sessionId, String operationId, long tick) {
        EditAdmission.Refusal refusal = admission.admitMember(player, sessionId);
        if (refusal != null) {
            if (abortReplayedSave(player, sessionId, operationId)) { return new TransferAdmission(false, Outbound.EMPTY); }
            return new TransferAdmission(false, Outbound.of(lost(refusal, sessionId, operationId)));
        }
        RuleEditReply.Save previous = recentResult(sessionId, operationId);
        if (previous != null) { return new TransferAdmission(false, Outbound.of(previous)); }
        sessions.heartbeat(sessionId, tick);
        return new TransferAdmission(true, Outbound.EMPTY);
    }

    /** 传输准入结论；拒绝时携带需交付的业务应答。 */
    public record TransferAdmission(boolean accepted, Outbound outbound) { }

    /** 对可关联的非法保存输入给出业务拒绝。 */
    public Outbound rejectSave(String sessionId, String operationId, String detail) {
        return Outbound.of(result(sessionId, operationId, RuleSaveStatus.INVALID_REQUEST, args(detail), List.of(), false, false));
    }

    // 保存主流程
    private Outbound submit(ServerPlayer player, RuleAuthoringContext context, MinecraftServer server,
                                         String rawSessionId, String rawOperationId, java.util.function.Supplier<String> json, long tick) {
        String sessionId = rawSessionId == null ? "" : rawSessionId;
        String operationId = rawOperationId == null ? "" : rawOperationId;
        if (operationId.isEmpty()) {
            return Outbound.of(result(sessionId, operationId, RuleSaveStatus.INVALID_REQUEST, args("operationId"), List.of(), false, false));
        }
        EditAdmission.Refusal refusal = admission.admitConfirmed(player, sessionId);
        if (refusal != null) {
            // 在途保存期间的重发不新增回执：先中止该次保存，由收尾统一回终态
            if (abortReplayedSave(player, sessionId, operationId)) { return Outbound.EMPTY; }
            return Outbound.of(lost(refusal, sessionId, operationId));
        }

        // 幂等：同一 operationId 直接回放上次回执，不重复写盘
        RuleEditReply.Save previous = recentResult(sessionId, operationId);
        if (previous != null) {
            return Outbound.of(previous);
        }

        if (pendingSave != null) {
            if (sessionId.equals(savingSessionId) && operationId.equals(savingOperationId)) { return Outbound.EMPTY; }
            return Outbound.of(result(sessionId, operationId, RuleSaveStatus.INVALID_REQUEST, args("apply in progress"), List.of(), false, false));
        }
        if (server == null || context.saveReloadContext() == null || store.unavailable()) {
            return Outbound.of(result(sessionId, operationId, RuleSaveStatus.UNAVAILABLE, List.of(), List.of(), false, false));
        }
        // 准入已通过；此处失败来自 ACTIVE→APPLYING 状态迁移被拒，回 SESSION_EXPIRED
        if (!sessions.beginApply(sessionId)) { return Outbound.of(sessionExpired(sessionId, operationId)); }
        sessions.heartbeat(sessionId, tick);
        if (pendingSnapshot != null) { pendingSnapshot.invalidate(); }
        requestedSnapshot = null;
        completedSnapshot = null;
        savingSessionId = sessionId;
        savingOperationId = operationId;
        savingOwner = player.getUUID();
        progressTick = tick - PROGRESS_INTERVAL_TICKS;
        progressSequence = 0;
        sentProgress = null;
        pendingSave = new RuleSaveTask(store, admission, context, server, sessionId, player.getUUID(), json, outcome -> {
            if (closed) { return; }
            if (outcome.written()) { catalogs.invalidate(); }
            completedSave = result(sessionId, operationId, outcome.status(),
                    switch (outcome.status()) {
                        case SUCCESS -> args(Integer.toString(outcome.edited()), Integer.toString(outcome.files()));
                        case VERSION_CONFLICT -> args(Integer.toString(store.version()), Integer.toString(outcome.expectedVersion()));
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
        if (store.unavailable()) {
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
        store.flushRevision();
        if (pendingSave == null) { return null; }
        pendingSave.pollCompletion();
        ServerPlayer owner = server.getPlayerList().getPlayer(savingOwner);
        EditAdmission.Refusal refusal = admission.admitMember(owner, savingSessionId);
        if (refusal != null) {
            pendingSave.invalidate();
            // 玩家离线或失权后回执已无接收者，释放会话让下一位玩家取锁
            if (refusal.reason() == EditAdmission.Reason.PLAYER_ABSENT
                    || refusal.reason() == EditAdmission.Reason.NO_PERMISSION) {
                sessions.release();
            }
        }
        if (!pendingSave.finished()) { return null; }
        RuleEditReply.Save payload = completedSave;
        sessions.finishApply(savingSessionId, server.getTickCount());
        // 会话释放只结束锁归属；保存任务已确定的终态仍按真实写盘与重载结果返回。
        if (payload != null) { remember(payload); }
        pendingSave = null;
        completedSave = null;
        Runnable reload = deferredReload;
        deferredReload = null;
        if (reload != null) { reload.run(); }
        if (payload != null && admission.sessionMatches(payload.sessionId())
                && (reload != null || payload.status() == RuleSaveStatus.VERSION_CONFLICT
                    || payload.status() == RuleSaveStatus.SAVED_NOT_RELOADED)) {
            requestedSnapshot = new SnapshotRequest(savingOwner, payload.sessionId(), "");
        }
        if (owner == null || payload == null) { return null; }
        return new SaveReply(owner, admission.sessionMatches(payload.sessionId()) ? Outbound.of(payload)
                : Outbound.of(payload, new RuleEditReply.SessionEvent(payload.sessionId(), RuleSaveStatus.SESSION_EXPIRED)));
    }

    /**
     * 请求取消当前玩家的对应保存；提交确认后只返回不可取消进度，不能回滚已发布规则。
     * @param player 请求玩家
     * @param payload 保存操作标识
     * @return 在途保存无立即终态；未开始写盘或已完成的操作可直接交付取消／幂等回执
     */
    public Outbound cancelSave(ServerPlayer player, RuleEditRequest.Cancel payload) {
        // 取消只撤回本人仍在途的请求，不新增写权限，因此只判会话号与持有者
        if (admission.admitOwner(player, payload.sessionId()) != null) { return Outbound.EMPTY; }
        if (pendingSave != null) {
            if (payload.sessionId().equals(savingSessionId) && payload.operationId().equals(savingOperationId)) {
                pendingSave.cancel();
                sentProgress = null;
            }
            return Outbound.EMPTY;
        }
        RuleEditReply.Save previous = recentResult(payload.sessionId(), payload.operationId());
        if (previous != null) { return Outbound.of(previous); }
        RuleEditReply.Save cancelled = result(payload.sessionId(), payload.operationId(), RuleSaveStatus.CANCELLED, List.of(), List.of(), false, false);
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
        if (admission.admitMember(player, savingSessionId) != null) { return null; }
        RuleSaveProgress progress = pendingSave.progress();
        if (progress.equals(sentProgress)) { return null; }
        sentProgress = progress;
        progressTick = server.getTickCount();
        return new SaveReply(player, Outbound.of(new RuleEditReply.Progress(savingSessionId, savingOperationId,
                ++progressSequence, progress)));
    }

    /**
     * 已完成保存的网络交付。
     * @param player 接收者
     * @param outbound 结果载荷
     */
    public record SaveReply(ServerPlayer player, Outbound outbound) { }

    /**
     * 租约与确认窗口检查：超时释放后把 SESSION_EXPIRED 回执交给持有者
     */
    public Expired expireSessions(MinecraftServer server, long tick) {
        EditSessionManager.SessionInfo expired = sessions.expire(tick);
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
        return new Expired(owner, Outbound.of(new RuleEditReply.SessionEvent(expired.sessionId(), RuleSaveStatus.SESSION_EXPIRED)));
    }

    /**
     * 强制释放（管理员指令）：返回被释放的会话，空闲时返回 null
     */
    public EditSessionManager.SessionInfo forceRelease() {
        if (pendingSave != null) { pendingSave.invalidate(); }
        return sessions.release();
    }

    /**
     * 在途保存失去准入后重发：撤销锁归属，由原保存任务交付真实终态。
     * 判定依据是服务层记录的在途会话号与持有者。
     *
     * @param player 请求玩家
     * @param sessionId 请求会话号
     * @param operationId 请求操作号
     * @return 是否确有同一请求在途并被中止
     */
    public boolean abortReplayedSave(ServerPlayer player, String sessionId, String operationId) {
        if (pendingSave == null || !sessionId.equals(savingSessionId) || !player.getUUID().equals(savingOwner)
                || operationId.isEmpty()) {
            return false;
        }
        forceRelease();
        return true;
    }

    /**
     * 玩家断开：保存期间请求延后释放并等待回滚；其它会话由原租约回收
     */
    public void clearPlayer(UUID playerId) {
        if (pendingSave != null && playerId.equals(savingOwner)) { forceRelease(); }
    }

    /**
     * 先终止交付；文件作业的等待与超时退役由宿主持有的 store 负责。
     */
    public void reset() {
        closed = true;
        if (pendingSave != null) { pendingSave.invalidate(); }
        if (pendingSnapshot != null) { pendingSnapshot.invalidate(); }
        pendingSave = null;
        completedSave = null;
        deferredReload = null;
        pendingSnapshot = null;
        requestedSnapshot = null;
        sourceIndex = null;
        sourceContext = null;
        sourceVersion = -1;
        sourceReplies.clear();
        completedSnapshot = null;
        recentResults.clear();
        resultsSessionId = null;
        pendingLifespan = null;
        lifespanOwner = null;
    }

    // 快照请求只保留最新一个；读盘、解码、编码与装配由续接任务处理。
    private Outbound snapshotOutbound(RuleAuthoringContext context, MinecraftServer server,
                                      String sessionId, String requestId) {
        if (server == null || context.saveReloadContext() == null) {
            return Outbound.of(new RuleEditReply.QueryResult(RuleEditRequest.Kind.SNAPSHOT, sessionId, requestId, RuleSaveStatus.UNAVAILABLE));
        }
        EditSessionManager.SessionInfo session = sessions.session();
        if (session != null) {
            requestedSnapshot = new SnapshotRequest(session.ownerUuid(), sessionId, requestId);
            completedSnapshot = null;
            startSnapshot(server, context);
        }
        return Outbound.EMPTY;
    }

    /** 取得一个已完成的列表内容，线上切分与节拍由传输实例负责。 */
    public @Nullable SaveReply pollSnapshot(MinecraftServer server, RuleAuthoringContext context) {
        if (pendingSnapshot != null) { pendingSnapshot.pollCompletion(); }
        if (pendingSave != null) { return null; }
        startSnapshot(server, context);
        if (completedSnapshot == null || deliveringSnapshot == null) { return null; }
        ServerPlayer player = server.getPlayerList().getPlayer(deliveringSnapshot.owner());
        RuleEditReply reply = completedSnapshot; completedSnapshot = null;
        return admission.admitMember(player, deliveringSnapshot.sessionId()) != null ? null : new SaveReply(player, Outbound.of(reply));
    }

    /** 业务应答只交付给仍有权限的当前会话持有者。 */
    public boolean acceptsReply(@Nullable ServerPlayer player, String sessionId) {
        return !closed && admission.admitMember(player, sessionId) == null;
    }

    /** 成功重载后使编辑来源失效，并安排当前持有者刷新列表。 */
    public void runtimeReloaded() {
        sourceIndex = null; sourceVersion = -1; sourceContext = null;
        var session = sessions.session();
        if (session != null && session.state() == EditSessionManager.SessionState.ACTIVE) {
            requestedSnapshot = new SnapshotRequest(session.ownerUuid(), session.sessionId(), "");
            completedSnapshot = null;
        }
    }

    /** 传输交付前核对这次快照仍是当前会话所需的版本。 */
    public boolean acceptsSnapshot(String sessionId, String requestId) {
        return !closed && pendingSave == null && requestedSnapshot == null && deliveringSnapshot != null
                && deliveringSnapshot.sessionId().equals(sessionId) && deliveringSnapshot.requestId().equals(requestId)
                && admission.sessionMatches(sessionId);
    }

    // 请求到达即开始纯文件工作；保存和在途准备仍串行，Codec 只由公共调度器执行。
    private void startSnapshot(MinecraftServer server, RuleAuthoringContext context) {
        if (pendingSave != null) { return; }
        if (requestedSnapshot != null && pendingSnapshot == null) {
            SnapshotRequest current = requestedSnapshot;
            if (!admission.sessionMatches(current.sessionId())
                || context.saveReloadContext() == null) {
                requestedSnapshot = null;
                return;
            }
            pendingSnapshot = new RuleSnapshotTask(context, server, store, admission, current.sessionId(), delivery -> {
                if (closed) { return; }
                pendingSnapshot = null;
                // 仅发布仍被当前请求接纳的来源索引，与对应快照保持同源
                if (requestedSnapshot == current && delivery.json() != null && delivery.index() != null) {
                    sourceIndex = delivery.index(); sourceVersion = store.version(); sourceContext = context.saveReloadContext();
                }
                if (requestedSnapshot != current) { return; }
                requestedSnapshot = null;
                deliveringSnapshot = current;
                completedSnapshot = delivery.json() == null
                        ? new RuleEditReply.QueryResult(RuleEditRequest.Kind.SNAPSHOT, current.sessionId(), current.requestId(), RuleSaveStatus.UNAVAILABLE)
                        : new RuleEditReply.Snapshot(current.sessionId(), current.requestId(), delivery.json());
            });
            pendingSnapshot.schedule();
        }
    }

    // 准入拒绝的统一回执：状态码与文案 key 由 EditAdmission 决定，这里只补会话号与操作号
    private RuleEditReply lost(EditAdmission.Refusal refusal, String sessionId, String operationId) {
        return operationId.isEmpty() ? new RuleEditReply.SessionEvent(sessionId, refusal.status())
                : result(sessionId, operationId, refusal.status(), List.of(), List.of(), false, false);
    }

    // 准入已通过但会话状态机拒绝推进（例如已进入 APPLYING）：按会话过期回执
    private RuleEditReply sessionExpired(String sessionId, String operationId) {
        return operationId.isEmpty() ? new RuleEditReply.SessionEvent(sessionId, RuleSaveStatus.SESSION_EXPIRED)
                : result(sessionId, operationId, RuleSaveStatus.SESSION_EXPIRED, List.of(), List.of(), false, false);
    }

    // 组装回执载荷
    private RuleEditReply.Save result(String sessionId, String operationId, RuleSaveStatus status,
                                         List<String> messageArgs, List<RuleIssue> issues,
                                         boolean writtenToDisk, boolean reloaded) {
        return new RuleEditReply.Save(sessionId, operationId, status, List.copyOf(messageArgs),
                store.version(), writtenToDisk, reloaded, List.copyOf(issues));
    }

    // 幂等查询：会话变化时整体失效
    private RuleEditReply.Save recentResult(String sessionId, String operationId) {
        if (!Objects.equals(resultsSessionId, sessionId)) {
            recentResults.clear();
            resultsSessionId = sessionId;
            return null;
        }
        return recentResults.get(operationId);
    }

    // 记录回执（保留最近 16 条）
    private void remember(RuleEditReply.Save payload) {
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
                    null,
                    RuleIssue.ORIGIN_RUNTIME,
                    issue.fieldPath() == null ? "" : issue.fieldPath(),
                    "itemdespawntowhat.edit.issue.generic",
                    List.of(),
                    issue.format()));
        }
        return List.copyOf(issues);
    }

    private static List<String> args(String... values) {
        return java.util.Arrays.stream(values).map(value -> value == null ? "" : value).toList();
    }
}
