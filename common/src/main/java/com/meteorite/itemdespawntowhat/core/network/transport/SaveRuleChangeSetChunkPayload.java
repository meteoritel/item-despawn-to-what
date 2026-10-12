package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：大变更集的分片。transferId 标识一次传输，index 从 0 递增，count 为总分片数。
 * 服务端按 transferId 重组（{@link RuleEditChunkAccumulator}），收齐后走与直发完全相同的保存流程。
 */
public record SaveRuleChangeSetChunkPayload(String sessionId, String operationId, String transferId,
                                            int index, int count, String chunk) implements CustomPacketPayload {

    public static final Type<SaveRuleChangeSetChunkPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "save_rule_change_set_chunk")
    );

    public static final StreamCodec<FriendlyByteBuf, SaveRuleChangeSetChunkPayload> STREAM_CODEC =
            StreamCodec.of(SaveRuleChangeSetChunkPayload::encode, SaveRuleChangeSetChunkPayload::decode);

    public SaveRuleChangeSetChunkPayload {
        sessionId = sessionId == null ? "" : sessionId;
        operationId = operationId == null ? "" : operationId;
        transferId = transferId == null ? "" : transferId;
        chunk = chunk == null ? "" : chunk;
    }

    // 编码
    private static void encode(FriendlyByteBuf buffer, SaveRuleChangeSetChunkPayload payload) {
        buffer.writeUtf(payload.sessionId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.operationId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.transferId(), RuleEditLimits.MAX_TRANSFER_ID_LENGTH + RuleEditLimits.PACKET_SLACK_BYTES);
        buffer.writeVarInt(payload.index());
        buffer.writeVarInt(payload.count());
        buffer.writeUtf(payload.chunk(), RuleEditLimits.MAX_CHUNK_CHARS + RuleEditLimits.PACKET_SLACK_BYTES);
    }

    // 解码
    private static SaveRuleChangeSetChunkPayload decode(FriendlyByteBuf buffer) {
        return new SaveRuleChangeSetChunkPayload(
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
