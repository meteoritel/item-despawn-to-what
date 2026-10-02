package com.meteorite.itemdespawntowhat.core.network.transport;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：大变更集的分片。
 * 服务端按 transferId 重组（{@link RuleEditChunkAccumulator}），收齐后走与直发完全相同的保存流程。
 */
public record SaveRuleChangeSetChunkPayload(
        String transferId,
        int chunkIndex,
        int chunkCount,
        String chunkData
) implements CustomPacketPayload {

    public static final Type<SaveRuleChangeSetChunkPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "save_rule_change_set_chunk")
    );

    public static final StreamCodec<ByteBuf, SaveRuleChangeSetChunkPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(RuleEditLimits.MAX_TRANSFER_ID_LENGTH),
            SaveRuleChangeSetChunkPayload::transferId,
            ByteBufCodecs.VAR_INT,
            SaveRuleChangeSetChunkPayload::chunkIndex,
            ByteBufCodecs.VAR_INT,
            SaveRuleChangeSetChunkPayload::chunkCount,
            ByteBufCodecs.stringUtf8(RuleEditLimits.MAX_CHUNK_CHARS),
            SaveRuleChangeSetChunkPayload::chunkData,
            SaveRuleChangeSetChunkPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
