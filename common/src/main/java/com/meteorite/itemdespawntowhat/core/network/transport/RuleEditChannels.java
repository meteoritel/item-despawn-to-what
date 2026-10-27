package com.meteorite.itemdespawntowhat.core.network.transport;

import com.meteorite.itemdespawntowhat.core.service.edit.RuleSaveStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.HashSet;
import java.util.function.BiConsumer;

/** 编辑通道唯一声明：两个平台消费相同类型、codec、方向、处理器和文本分片元数据。 */
public final class RuleEditChannels {
    private RuleEditChannels() { }

    /** 服务端处理面，平台负责切到本次服务端线程。 */
    @FunctionalInterface public interface ServerHandler<P extends CustomPacketPayload> {
        /** 处理已解码的入站帧。 */
        void accept(ServerPlayer player, RuleEditServerContext context, P payload);
    }
    /** 直发文本的类型安全工厂。 */
    @FunctionalInterface public interface TextFactory {
        /** 创建对应 family 的直发载荷。 */
        RuleTextPayload create(String sessionId, String correlationId, String json);
    }
    /** 分片能力只附着于支持大文本的直发和 chunk 通道。 */
    public record Text(RuleTextChunker.Family family, int maxBytes, CustomPacketPayload.Type<RuleChunkPayload> chunkType, TextFactory direct) { }
    /** C2S 声明；普通载荷的 text 为空。 */
    public record Serverbound<P extends CustomPacketPayload>(CustomPacketPayload.Type<P> type, StreamCodec<FriendlyByteBuf, P> codec,
                                                            ServerHandler<P> handler, @Nullable Text text) { }
    /** S2C 声明；接收对象由平台按连接捕捉。 */
    public record Clientbound<P extends CustomPacketPayload>(CustomPacketPayload.Type<P> type, StreamCodec<FriendlyByteBuf, P> codec,
                                                            BiConsumer<RuleEditClientReceiver, P> handler, @Nullable Text text) { }

    private static final Text CHANGE_SET = new Text(RuleTextChunker.Family.CHANGE_SET, RuleEditLimits.MAX_CHANGE_SET_BYTES,
            RuleChunkPayload.CHANGE_SET, SaveRuleChangeSetPayload::new);
    private static final Text SNAPSHOT = new Text(RuleTextChunker.Family.SNAPSHOT, RuleEditLimits.MAX_SNAPSHOT_BYTES,
            RuleChunkPayload.SNAPSHOT, RuleSnapshotPayload::new);
    private static final Text SOURCE_VIEW = new Text(RuleTextChunker.Family.SOURCE_VIEW, RuleEditLimits.MAX_SOURCE_VIEW_BYTES,
            RuleChunkPayload.SOURCE_VIEW, (session, request, json) -> new RuleSourceViewPayload(session, request, RuleSaveStatus.SUCCESS.id(), json));

    private static final List<Serverbound<?>> SERVERBOUND = List.of(
            new Serverbound<>(ConfirmRuleEditorPayload.TYPE, ConfirmRuleEditorPayload.STREAM_CODEC, RuleEditServerHandler::handleConfirm, null),
            new Serverbound<>(HeartbeatRuleEditorPayload.TYPE, HeartbeatRuleEditorPayload.STREAM_CODEC, RuleEditServerHandler::handleHeartbeat, null),
            new Serverbound<>(CloseRuleEditorPayload.TYPE, CloseRuleEditorPayload.STREAM_CODEC, RuleEditServerHandler::handleClose, null),
            new Serverbound<>(RequestRuleSnapshotPayload.TYPE, RequestRuleSnapshotPayload.STREAM_CODEC, RuleEditServerHandler::handleSnapshotRequest, null),
            new Serverbound<>(RequestRuleCatalogPayload.TYPE, RequestRuleCatalogPayload.STREAM_CODEC, RuleEditServerHandler::handleCatalogRequest, null),
            new Serverbound<>(RequestRuleLifespanPayload.TYPE, RequestRuleLifespanPayload.STREAM_CODEC, RuleEditServerHandler::handleLifespanRequest, null),
            new Serverbound<>(RequestRuleSourceViewPayload.TYPE, RequestRuleSourceViewPayload.STREAM_CODEC, RuleEditServerHandler::handleSourceViewRequest, null),
            new Serverbound<>(SaveRuleChangeSetPayload.TYPE, SaveRuleChangeSetPayload.STREAM_CODEC, RuleEditServerHandler::handleChangeSet, CHANGE_SET),
            new Serverbound<>(CancelRuleSavePayload.TYPE, CancelRuleSavePayload.STREAM_CODEC, RuleEditServerHandler::handleCancelSave, null),
            new Serverbound<>(RuleChunkPayload.CHANGE_SET, RuleChunkPayload.codec(RuleChunkPayload.CHANGE_SET), RuleEditServerHandler::handleChangeSetChunk, CHANGE_SET));

    private static final List<Clientbound<?>> CLIENTBOUND = List.of(
            new Clientbound<>(OpenRuleEditorPayload.TYPE, OpenRuleEditorPayload.STREAM_CODEC, RuleEditClientReceiver::open, null),
            new Clientbound<>(RuleSnapshotPayload.TYPE, RuleSnapshotPayload.STREAM_CODEC, RuleEditClientReceiver::snapshot, SNAPSHOT),
            new Clientbound<>(RuleChunkPayload.SNAPSHOT, RuleChunkPayload.codec(RuleChunkPayload.SNAPSHOT), RuleEditClientReceiver::chunk, SNAPSHOT),
            new Clientbound<>(RuleSourceViewPayload.TYPE, RuleSourceViewPayload.STREAM_CODEC, RuleEditClientReceiver::sourceView, SOURCE_VIEW),
            new Clientbound<>(RuleChunkPayload.SOURCE_VIEW, RuleChunkPayload.codec(RuleChunkPayload.SOURCE_VIEW), RuleEditClientReceiver::chunk, SOURCE_VIEW),
            new Clientbound<>(RuleSaveResultPayload.TYPE, RuleSaveResultPayload.STREAM_CODEC, RuleEditClientReceiver::result, null),
            new Clientbound<>(RuleSaveProgressPayload.TYPE, RuleSaveProgressPayload.STREAM_CODEC, RuleEditClientReceiver::progress, null),
            new Clientbound<>(RuleCatalogPayload.TYPE, RuleCatalogPayload.STREAM_CODEC, RuleEditClientReceiver::catalog, null),
            new Clientbound<>(RuleLifespanPayload.TYPE, RuleLifespanPayload.STREAM_CODEC, RuleEditClientReceiver::lifespan, null),
            new Clientbound<>(RuleQueryResultPayload.TYPE, RuleQueryResultPayload.STREAM_CODEC, RuleEditClientReceiver::queryResult, null),
            new Clientbound<>(RuleSessionEventPayload.TYPE, RuleSessionEventPayload.STREAM_CODEC, RuleEditClientReceiver::sessionEvent, null));

    static {
        var ids = new HashSet<net.minecraft.resources.ResourceLocation>();
        SERVERBOUND.forEach(channel -> { if (!ids.add(channel.type().id())) { throw new IllegalStateException("重复编辑通道: " + channel.type().id()); } });
        CLIENTBOUND.forEach(channel -> { if (!ids.add(channel.type().id())) { throw new IllegalStateException("重复编辑通道: " + channel.type().id()); } });
        SERVERBOUND.forEach(channel -> { java.util.Objects.requireNonNull(channel.codec()); java.util.Objects.requireNonNull(channel.handler()); });
        CLIENTBOUND.forEach(channel -> { java.util.Objects.requireNonNull(channel.codec()); java.util.Objects.requireNonNull(channel.handler()); });
        for (RuleTextChunker.Family family : RuleTextChunker.Family.values()) {
            Text text = text(family);
            var incoming = SERVERBOUND.stream().filter(channel -> channel.text() == text).map(Serverbound::type).toList();
            var outgoing = CLIENTBOUND.stream().filter(channel -> channel.text() == text).map(Clientbound::type).toList();
            if (!(incoming.size() == 2 && outgoing.isEmpty() || outgoing.size() == 2 && incoming.isEmpty())) {
                throw new IllegalStateException("文本 direct/chunk 必须属于同一方向: " + family);
            }
            var pair = incoming.isEmpty() ? outgoing : incoming;
            var directType = text.direct().create("", "", "").type();
            if (directType.id().equals(text.chunkType().id())
                    || pair.stream().filter(type -> type.id().equals(text.chunkType().id())).count() != 1
                    || pair.stream().filter(type -> type.id().equals(directType.id())).count() != 1) {
                throw new IllegalStateException("文本通道必须各声明一个 direct 与 chunk: " + family);
            }
        }
    }

    /** 全部服务端入站通道。 */
    public static List<Serverbound<?>> serverbound() { return SERVERBOUND; }
    /** 全部客户端入站通道。 */
    public static List<Clientbound<?>> clientbound() { return CLIENTBOUND; }
    /** 文本分片器与注册器消费同一声明。 */
    public static Text text(RuleTextChunker.Family family) {
        return switch (family) { case CHANGE_SET -> CHANGE_SET; case SNAPSHOT -> SNAPSHOT; case SOURCE_VIEW -> SOURCE_VIEW; };
    }
}
