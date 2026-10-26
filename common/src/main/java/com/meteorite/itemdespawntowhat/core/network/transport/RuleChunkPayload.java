package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * 文本分片载荷：快照、来源视图与变更集三个通道共用同一份六字段编码，通道差异只来自 {@link #type()}。
 * 同一次传输由 sessionId + correlationId 定位请求（快照与来源视图为 requestId，变更为 operationId），
 * transferId 区分批次，index 从 0 到 count-1；客户端与服务端收齐后按 index 顺序拼接。
 * <p>
 * {@code type} 只标识通道 id，不参与编码；三个通道 id 与字段写序属于线上格式，不得改动。
 */
public record RuleChunkPayload(String sessionId, String correlationId, String transferId,
                               int index, int count, String chunk,
                               Type<RuleChunkPayload> type) implements RuleTextPayload {

    // S2C：大快照的分片
    public static final Type<RuleChunkPayload> SNAPSHOT = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "rule_snapshot_chunk")
    );

    // S2C：大来源视图的分片
    public static final Type<RuleChunkPayload> SOURCE_VIEW = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "rule_source_view_chunk")
    );

    // C2S：大变更集的分片
    public static final Type<RuleChunkPayload> CHANGE_SET = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "save_rule_change_set_chunk")
    );

    /**
     * 指定通道的编解码器：三个通道的字段写序完全相同，只有类型 id 不同。
     * @param type 通道 id
     * @return 该通道的编解码器
     */
    public static StreamCodec<FriendlyByteBuf, RuleChunkPayload> codec(Type<RuleChunkPayload> type) {
        Objects.requireNonNull(type, "type");
        return StreamCodec.of(RuleChunkPayload::encode, buffer -> decode(buffer, type));
    }

    public RuleChunkPayload {
        sessionId = sessionId == null ? "" : sessionId;
        correlationId = correlationId == null ? "" : correlationId;
        transferId = transferId == null ? "" : transferId;
        chunk = chunk == null ? "" : chunk;
        Objects.requireNonNull(type, "type");
    }

    @Override
    public String text() {
        return chunk;
    }

    // 编码
    private static void encode(FriendlyByteBuf buffer, RuleChunkPayload payload) {
        buffer.writeUtf(payload.sessionId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.correlationId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.transferId(), RuleEditLimits.MAX_TRANSFER_ID_LENGTH + RuleEditLimits.PACKET_SLACK_BYTES);
        buffer.writeVarInt(payload.index());
        buffer.writeVarInt(payload.count());
        buffer.writeUtf(payload.chunk(), RuleEditLimits.MAX_CHUNK_CHARS + RuleEditLimits.PACKET_SLACK_BYTES);
    }

    // 解码
    private static RuleChunkPayload decode(FriendlyByteBuf buffer, Type<RuleChunkPayload> type) {
        return new RuleChunkPayload(
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_TRANSFER_ID_LENGTH + RuleEditLimits.PACKET_SLACK_BYTES),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readUtf(RuleEditLimits.MAX_CHUNK_CHARS + RuleEditLimits.PACKET_SLACK_BYTES),
                type);
    }
}