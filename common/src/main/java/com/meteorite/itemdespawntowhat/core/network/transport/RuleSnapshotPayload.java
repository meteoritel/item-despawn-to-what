package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：服务端下发规则快照（json 为 {@code RuleSnapshot.serialize()} 的 JSON 文本，契约 §3.3）。
 * requestId 原样回带，客户端据此把响应配到请求上。
 */
public record RuleSnapshotPayload(String sessionId, String requestId, String snapshotJson)
        implements CustomPacketPayload {

    public static final Type<RuleSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "rule_snapshot")
    );

    public static final StreamCodec<FriendlyByteBuf, RuleSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(RuleSnapshotPayload::encode, RuleSnapshotPayload::decode);

    public RuleSnapshotPayload {
        sessionId = sessionId == null ? "" : sessionId;
        requestId = requestId == null ? "" : requestId;
        snapshotJson = snapshotJson == null ? "" : snapshotJson;
    }

    // 编码
    private static void encode(FriendlyByteBuf buffer, RuleSnapshotPayload payload) {
        buffer.writeUtf(payload.sessionId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.requestId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.snapshotJson(), RuleEditLimits.MAX_SNAPSHOT_CHARS + RuleEditLimits.PACKET_SLACK_BYTES);
    }

    // 解码
    private static RuleSnapshotPayload decode(FriendlyByteBuf buffer) {
        return new RuleSnapshotPayload(
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_SNAPSHOT_CHARS + RuleEditLimits.PACKET_SLACK_BYTES));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
