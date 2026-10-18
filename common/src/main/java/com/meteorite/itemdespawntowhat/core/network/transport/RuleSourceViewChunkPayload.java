package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：大来源视图的分片。序列化后不超过直发上限时仍走整包 {@link RuleSourceViewPayload}；
 * 超限时改发 N 片：同一 transferId、index 从 0 到 count-1，客户端收齐按顺序拼接后走 RuleSourceView.parse。
 */
public record RuleSourceViewChunkPayload(String sessionId, String requestId, String transferId,
                                         int index, int count, String chunk)
        implements RuleChunkPayload {

    public static final Type<RuleSourceViewChunkPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "rule_source_view_chunk")
    );

    public static final StreamCodec<FriendlyByteBuf, RuleSourceViewChunkPayload> STREAM_CODEC =
            StreamCodec.of(RuleSourceViewChunkPayload::encode, RuleSourceViewChunkPayload::decode);

    public RuleSourceViewChunkPayload {
        sessionId = sessionId == null ? "" : sessionId;
        requestId = requestId == null ? "" : requestId;
        transferId = transferId == null ? "" : transferId;
        chunk = chunk == null ? "" : chunk;
    }

    // 编码
    private static void encode(FriendlyByteBuf buffer, RuleSourceViewChunkPayload payload) {
        buffer.writeUtf(payload.sessionId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.requestId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.transferId(), RuleEditLimits.MAX_TRANSFER_ID_LENGTH + RuleEditLimits.PACKET_SLACK_BYTES);
        buffer.writeVarInt(payload.index());
        buffer.writeVarInt(payload.count());
        buffer.writeUtf(payload.chunk(), RuleEditLimits.MAX_CHUNK_CHARS + RuleEditLimits.PACKET_SLACK_BYTES);
    }

    // 解码
    private static RuleSourceViewChunkPayload decode(FriendlyByteBuf buffer) {
        return new RuleSourceViewChunkPayload(
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_TRANSFER_ID_LENGTH + RuleEditLimits.PACKET_SLACK_BYTES),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readUtf(RuleEditLimits.MAX_CHUNK_CHARS + RuleEditLimits.PACKET_SLACK_BYTES));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
