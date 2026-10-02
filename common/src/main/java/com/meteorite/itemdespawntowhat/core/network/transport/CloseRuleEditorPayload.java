package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：客户端退出编辑器（契约 §3.3），服务端立即释放会话；reasonCode 供日志与统计使用。
 */
public record CloseRuleEditorPayload(String sessionId, String reasonCode) implements CustomPacketPayload {

    public static final Type<CloseRuleEditorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "close_rule_editor")
    );

    public static final StreamCodec<FriendlyByteBuf, CloseRuleEditorPayload> STREAM_CODEC =
            StreamCodec.of(CloseRuleEditorPayload::encode, CloseRuleEditorPayload::decode);

    public CloseRuleEditorPayload {
        sessionId = sessionId == null ? "" : sessionId;
        reasonCode = reasonCode == null ? "" : reasonCode;
    }

    // 编码
    private static void encode(FriendlyByteBuf buffer, CloseRuleEditorPayload payload) {
        buffer.writeUtf(payload.sessionId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.reasonCode(), RuleEditLimits.MAX_CODE_CHARS);
    }

    // 解码
    private static CloseRuleEditorPayload decode(FriendlyByteBuf buffer) {
        return new CloseRuleEditorPayload(
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_CODE_CHARS));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
