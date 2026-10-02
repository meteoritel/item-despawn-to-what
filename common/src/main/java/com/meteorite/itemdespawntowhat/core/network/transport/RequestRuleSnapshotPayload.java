package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：客户端在已持有会话的前提下请求规则快照（契约 §3.3）。
 * 会话校验不通过时服务端回 SESSION_EXPIRED/LOCK_NOT_OWNED，不在请求路径上创建会话。
 */
public record RequestRuleSnapshotPayload(String sessionId, String requestId) implements CustomPacketPayload {

    public static final Type<RequestRuleSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "request_rule_snapshot")
    );

    public static final StreamCodec<FriendlyByteBuf, RequestRuleSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(RequestRuleSnapshotPayload::encode, RequestRuleSnapshotPayload::decode);

    public RequestRuleSnapshotPayload {
        sessionId = sessionId == null ? "" : sessionId;
        requestId = requestId == null ? "" : requestId;
    }

    // 编码
    private static void encode(FriendlyByteBuf buffer, RequestRuleSnapshotPayload payload) {
        buffer.writeUtf(payload.sessionId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.requestId(), RuleEditLimits.MAX_ID_CHARS);
    }

    // 解码
    private static RequestRuleSnapshotPayload decode(FriendlyByteBuf buffer) {
        return new RequestRuleSnapshotPayload(
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
