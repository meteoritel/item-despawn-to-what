package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：大快照的分片（契约 §3.3 的分片规则 + Lead 批准的新增载荷）。
 * 快照序列化后不超过 {@link RuleEditLimits#MAX_SNAPSHOT_BYTES} 时仍走整包 {@link RuleSnapshotPayload}；
 * 超限时改发 N 片：同一 transferId、index 从 0 到 count-1，客户端收齐按顺序拼接后走 RuleSnapshot.parse。
 * 服务端在途上限与"60 秒无进展即丢弃"沿用契约 §3.3 的规定。
 */
public record RuleSnapshotChunkPayload(String sessionId, String requestId, String transferId,
                                       int index, int count, String chunk) implements CustomPacketPayload {

    public static final Type<RuleSnapshotChunkPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "rule_snapshot_chunk")
    );

    public static final StreamCodec<FriendlyByteBuf, RuleSnapshotChunkPayload> STREAM_CODEC =
            StreamCodec.of(RuleSnapshotChunkPayload::encode, RuleSnapshotChunkPayload::decode);

    public RuleSnapshotChunkPayload {
        sessionId = sessionId == null ? "" : sessionId;
        requestId = requestId == null ? "" : requestId;
        transferId = transferId == null ? "" : transferId;
        chunk = chunk == null ? "" : chunk;
    }

    // 编码
    private static void encode(FriendlyByteBuf buffer, RuleSnapshotChunkPayload payload) {
        buffer.writeUtf(payload.sessionId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.requestId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.transferId(), RuleEditLimits.MAX_TRANSFER_ID_LENGTH + RuleEditLimits.PACKET_SLACK_BYTES);
        buffer.writeVarInt(payload.index());
        buffer.writeVarInt(payload.count());
        buffer.writeUtf(payload.chunk(), RuleEditLimits.MAX_CHUNK_CHARS + RuleEditLimits.PACKET_SLACK_BYTES);
    }

    // 解码
    private static RuleSnapshotChunkPayload decode(FriendlyByteBuf buffer) {
        return new RuleSnapshotChunkPayload(
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
