package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：客户端心跳（契约 §3.3），服务端刷新会话租约；缺少心跳超过 LEASE_SECONDS 即释放。
 */
public record HeartbeatRuleEditorPayload(String sessionId) implements CustomPacketPayload {

    public static final Type<HeartbeatRuleEditorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "heartbeat_rule_editor")
    );

    public static final StreamCodec<FriendlyByteBuf, HeartbeatRuleEditorPayload> STREAM_CODEC =
            StreamCodec.of(HeartbeatRuleEditorPayload::encode, HeartbeatRuleEditorPayload::decode);

    public HeartbeatRuleEditorPayload {
        sessionId = sessionId == null ? "" : sessionId;
    }

    // 编码
    private static void encode(FriendlyByteBuf buffer, HeartbeatRuleEditorPayload payload) {
        buffer.writeUtf(payload.sessionId(), RuleEditLimits.MAX_ID_CHARS);
    }

    // 解码
    private static HeartbeatRuleEditorPayload decode(FriendlyByteBuf buffer) {
        return new HeartbeatRuleEditorPayload(buffer.readUtf(RuleEditLimits.MAX_ID_CHARS));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
