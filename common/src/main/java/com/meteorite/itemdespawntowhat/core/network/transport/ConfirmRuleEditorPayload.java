package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：客户端确认已进入编辑界面（契约 §3.3），服务端据此把会话从 OPENING 推进到 ACTIVE。
 */
public record ConfirmRuleEditorPayload(String sessionId, int protocolVersion) implements CustomPacketPayload {

    public static final Type<ConfirmRuleEditorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "confirm_rule_editor")
    );

    public static final StreamCodec<FriendlyByteBuf, ConfirmRuleEditorPayload> STREAM_CODEC =
            StreamCodec.of(ConfirmRuleEditorPayload::encode, ConfirmRuleEditorPayload::decode);

    public ConfirmRuleEditorPayload {
        sessionId = sessionId == null ? "" : sessionId;
    }

    // 编码
    private static void encode(FriendlyByteBuf buffer, ConfirmRuleEditorPayload payload) {
        buffer.writeUtf(payload.sessionId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeVarInt(payload.protocolVersion());
    }

    // 解码
    private static ConfirmRuleEditorPayload decode(FriendlyByteBuf buffer) {
        return new ConfirmRuleEditorPayload(
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readVarInt());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
