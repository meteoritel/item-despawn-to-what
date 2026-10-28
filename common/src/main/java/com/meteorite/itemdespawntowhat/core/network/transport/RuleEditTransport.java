package com.meteorite.itemdespawntowhat.core.network.transport;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.DevelopmentDiagnostics;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEditProtocol;
import com.meteorite.itemdespawntowhat.core.service.RuleEditService;
import com.meteorite.itemdespawntowhat.core.service.RuleOverlayStore;
import com.meteorite.itemdespawntowhat.core.service.RuleSaveReloadContext;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleEditReply;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleEditRequest;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleSaveStatus;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** 一次服务端的编辑传输：拥有分片状态、出站映射和大文本投递节拍。 */
public final class RuleEditTransport {
    private final RuleEditService service;
    private final RuleOverlayStore store;
    private final RuleEditChunkAccumulator chunks = new RuleEditChunkAccumulator();
    private final List<TextDelivery> deliveries = new ArrayList<>();
    private boolean closed;

    /** 与本次编辑服务和唯一文件队列共同创建。 */
    public RuleEditTransport(RuleEditService service, RuleOverlayStore store) {
        this.service = service;
        this.store = store;
    }

    /** 完整文本通过传输总量检查后才提交业务请求。 */
    public void save(ServerPlayer player, RuleEditServerContext context, String sessionId, String operationId, Supplier<String> text) {
        String json = text.get();
        RuleEditService.Outbound reply = RuleEditLimits.encodedLength(json) > RuleEditLimits.MAX_CHANGE_SET_BYTES
                ? service.rejectSave(sessionId, operationId, "change set too large")
                : service.handle(player, context, player.getServer(), new RuleEditRequest.Save(sessionId, operationId, () -> json), tick(player));
        send(context, player, reply);
    }

    /** 取消仅撤回原持有者的操作，运行时暂不可用时仍可送达在途保存。 */
    public void cancel(ServerPlayer player, RuleEditServerContext context, RuleEditRequest.Cancel request) {
        chunks.clear(player.getUUID());
        send(context, player, service.handle(player, context, player.getServer(), request, tick(player)));
    }

    /** 分片先按会话准入，再进行帧校验和重组；完整后才进入保存。 */
    public void chunk(ServerPlayer player, RuleEditServerContext context, RuleChunkPayload payload) {
        var admission = service.admitTransfer(player, payload.sessionId(), payload.correlationId(), tick(player));
        if (!admission.accepted()) { send(context, player, admission.outbound()); return; }
        RuleChunkAssembly.Chunk validated = RuleChunkAssembly.validate(payload);
        if (validated == null) { send(context, player, service.rejectSave(payload.sessionId(), payload.correlationId(), "chunk")); return; }
        var accepted = chunks.acceptValidated(player, validated, tick(player));
        if (accepted.rejected()) { send(context, player, service.rejectSave(payload.sessionId(), payload.correlationId(), "change set too large")); }
        else if (accepted.complete()) { save(player, context, payload.sessionId(), payload.correlationId(), accepted::json); }
    }

    /** 按业务顺序交付本次请求的应答。 */
    public void send(RuleEditServerContext context, ServerPlayer player, RuleEditService.Outbound outbound) {
        for (RuleEditReply reply : outbound.replies()) { send(context, player, reply); }
    }

    /** 把语义应答映射为线上载荷；大文本在既有有界后台队列切分。 */
    public void send(RuleEditServerContext context, ServerPlayer player, RuleEditReply reply) {
        if (closed) { return; }
        switch (reply) {
            case RuleEditReply.Open value -> context.sendTo(player, new OpenRuleEditorPayload(value.sessionId(), value.targetId(),
                    RuleEditProtocol.VERSION, value.resultVersion(), value.limits(), value.status().id(), value.messageArgs(), ""));
            case RuleEditReply.Save value -> context.sendTo(player, RuleSaveResultPayload.of(value.sessionId(), value.operationId(), value.status(),
                    RuleEditMessages.key(value.status()), value.messageArgs(), value.resultVersion(), value.writtenToDisk(), value.reloaded(), value.issues()));
            case RuleEditReply.QueryResult value -> context.sendTo(player, new RuleQueryResultPayload(value.kind().name(), value.sessionId(), value.requestId(), value.status().id()));
            case RuleEditReply.SessionEvent value -> {
                chunks.clear(player.getUUID());
                context.sendTo(player, new RuleSessionEventPayload(value.sessionId(), value.status().id()));
            }
            case RuleEditReply.Lifespan value -> context.sendTo(player, new RuleLifespanPayload(value.sessionId(), value.requestId(), value.ticks()));
            case RuleEditReply.Progress value -> context.sendTo(player, new RuleSaveProgressPayload(value.sessionId(), value.operationId(), value.sequence(), value.progress()));
            case RuleEditReply.Catalog value -> {
                var catalog = value.catalog();
                String json = catalog.serialize();
                if (RuleEditLimits.encodedLength(json) > RuleEditLimits.MAX_SNAPSHOT_BYTES) {
                    send(context, player, new RuleEditReply.QueryResult(RuleEditRequest.Kind.CATALOG, value.sessionId(), value.requestId(), RuleSaveStatus.INVALID_REQUEST));
                } else { context.sendTo(player, new RuleCatalogPayload(value.sessionId(), value.requestId(), catalog.type().id(), catalog.revision(), json, catalog.lastPage())); }
            }
            case RuleEditReply.Snapshot value -> enqueue(context, player, value.sessionId(), value.requestId(), RuleEditRequest.Kind.SNAPSHOT,
                    () -> RuleTextChunker.split(RuleTextChunker.Family.SNAPSHOT, value.sessionId(), value.requestId(), value.json()));
            case RuleEditReply.SourceView value -> enqueue(context, player, value.sessionId(), value.requestId(), RuleEditRequest.Kind.SOURCE_VIEW,
                    () -> RuleTextChunker.split(RuleTextChunker.Family.SOURCE_VIEW, value.sessionId(), value.requestId(), value.view().serialize()));
        }
    }

    private void enqueue(RuleEditServerContext context, ServerPlayer player, String sessionId, String requestId,
                         RuleEditRequest.Kind kind, Supplier<List<CustomPacketPayload>> preparation) {
        if (kind == RuleEditRequest.Kind.SNAPSHOT) { deliveries.removeIf(delivery -> delivery.kind == kind); }
        deliveries.add(new TextDelivery(player.getUUID(), sessionId, requestId, kind, context.saveReloadContext(), store.version(),
                store.submit("edit-transport", preparation::get)));
    }

    /** 推进业务完成事件，再按每 tick 上限交付列表分片。 */
    public void tick(MinecraftServer server, RuleEditServerContext context) {
        if (closed) { return; }
        deliver(context, service.pollSource());
        deliver(context, service.pollSaveProgress(server));
        deliver(context, service.pollSave(server));
        deliver(context, service.pollSnapshot(server, context));
        var iterator = deliveries.iterator();
        while (iterator.hasNext()) {
            TextDelivery delivery = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(delivery.owner);
            if (!service.acceptsReply(player, delivery.sessionId)
                    || delivery.kind == RuleEditRequest.Kind.SNAPSHOT && !service.acceptsSnapshot(delivery.sessionId, delivery.requestId)) {
                delivery.profile(true); iterator.remove(); continue;
            }
            if (delivery.context != context.saveReloadContext() || delivery.version != store.version()) {
                send(context, player, new RuleEditReply.QueryResult(delivery.kind, delivery.sessionId, delivery.requestId, RuleSaveStatus.VERSION_CONFLICT));
                delivery.profile(true); iterator.remove(); continue;
            }
            if (!delivery.preparing.isDone()) { continue; }
            if (!delivery.started) {
                delivery.started = true;
                try { delivery.packets.addAll(delivery.preparing.join()); }
                catch (RuntimeException failure) {
                    org.apache.logging.log4j.LogManager.getLogger().error("编辑应答准备失败", failure);
                    send(context, player, new RuleEditReply.QueryResult(delivery.kind, delivery.sessionId, delivery.requestId, RuleSaveStatus.UNAVAILABLE));
                    iterator.remove(); continue;
                }
                if (delivery.packets.isEmpty()) {
                    send(context, player, new RuleEditReply.QueryResult(delivery.kind, delivery.sessionId, delivery.requestId, RuleSaveStatus.INVALID_REQUEST));
                    iterator.remove(); continue;
                }
                delivery.total = delivery.packets.size(); delivery.bytes = RuleTextChunker.encodedBytes(List.copyOf(delivery.packets));
                delivery.startedNanos = DevelopmentDiagnostics.start(); delivery.firstTick = server.getTickCount();
            }
            int limit = delivery.kind == RuleEditRequest.Kind.SNAPSHOT ? RuleEditLimits.SNAPSHOT_CHUNKS_PER_TICK : delivery.packets.size();
            for (int index = 0; index < limit && !delivery.packets.isEmpty(); index++) {
                context.sendTo(player, delivery.packets.removeFirst()); delivery.sent++;
            }
            delivery.lastTick = server.getTickCount();
            if (delivery.packets.isEmpty()) { delivery.profile(false); iterator.remove(); }
        }
    }

    /** 租约和分片超时使用同一服务端活动刻。 */
    public void expire(MinecraftServer server, RuleEditServerContext context) {
        chunks.expireIdle(server.getTickCount());
        var expired = service.expireSessions(server, server.getTickCount());
        if (expired.target() != null) { send(context, expired.target(), expired.outbound()); }
        var lifespan = service.pollLifespan(server, context);
        if (lifespan != null) { send(context, lifespan.player(), lifespan.payload()); }
    }

    /** 断开时清理本实例的玩家传输与业务会话。 */
    public void clearPlayer(UUID owner) {
        chunks.clear(owner); deliveries.removeIf(delivery -> delivery.owner.equals(owner)); service.clearPlayer(owner);
    }

    private void deliver(RuleEditServerContext context, RuleEditService.SaveReply reply) {
        if (reply != null) { send(context, reply.player(), reply.outbound()); }
    }

    private static long tick(ServerPlayer player) { return java.util.Objects.requireNonNull(player.getServer()).getTickCount(); }

    /** 停止交付并释放本次传输持有的状态；文件队列由宿主关闭。 */
    public void close() { closed = true; chunks.clearAll(); deliveries.clear(); }

    private static final class TextDelivery {
        private final UUID owner;
        private final String sessionId;
        private final String requestId;
        private final RuleEditRequest.Kind kind;
        private final RuleSaveReloadContext context;
        private final int version;
        private final CompletableFuture<List<CustomPacketPayload>> preparing;
        private final ArrayDeque<CustomPacketPayload> packets = new ArrayDeque<>();
        private boolean started;
        private int total;
        private int sent;
        private long bytes;
        private long startedNanos;
        private long firstTick;
        private long lastTick;

        private TextDelivery(UUID owner, String sessionId, String requestId, RuleEditRequest.Kind kind, RuleSaveReloadContext context,
                             int version, CompletableFuture<List<CustomPacketPayload>> preparing) {
            this.owner = owner; this.sessionId = sessionId; this.requestId = requestId; this.kind = kind;
            this.context = context; this.version = version; this.preparing = preparing;
        }

        private void profile(boolean aborted) {
            if (kind != RuleEditRequest.Kind.SNAPSHOT || !DevelopmentDiagnostics.enabled() || !started) { return; }
            JsonObject data = new JsonObject();
            long ticks = Math.max(0L, lastTick - firstTick);
            data.addProperty("packets", total); data.addProperty("sent", sent); data.addProperty("bytes", bytes);
            data.addProperty("ticks", ticks); data.addProperty("per_tick", ticks == 0 ? total : (double) total / ticks);
            data.addProperty("aborted", aborted); data.addProperty("total_ms", DevelopmentDiagnostics.micros(startedNanos) / 1000.0);
            DevelopmentDiagnostics.event(DevelopmentDiagnostics.SCENE_FIRST_LOAD, "SERVER_DELIVER", data);
        }
    }
}
