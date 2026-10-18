package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：编辑页打开某条规则时请求该规则的来源视图（基底与覆盖原始内容及来源元数据）。
 * 会话校验不通过时服务端回会话失效类状态，不在请求路径上创建会话。
 */
public record RequestRuleSourceViewPayload(String sessionId, String requestId, String ruleId)
        implements CustomPacketPayload {

    public static final Type<RequestRuleSourceViewPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "request_rule_source_view")
    );

    public static final StreamCodec<FriendlyByteBuf, RequestRuleSourceViewPayload> STREAM_CODEC =
            StreamCodec.of(RequestRuleSourceViewPayload::encode, RequestRuleSourceViewPayload::decode);

    public RequestRuleSourceViewPayload {
        sessionId = sessionId == null ? "" : sessionId;
        requestId = requestId == null ? "" : requestId;
        ruleId = ruleId == null ? "" : ruleId;
    }

    // 编码
    private static void encode(FriendlyByteBuf buffer, RequestRuleSourceViewPayload payload) {
        buffer.writeUtf(payload.sessionId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.requestId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.ruleId(), RuleEditLimits.MAX_ID_CHARS);
    }

    // 解码
    private static RequestRuleSourceViewPayload decode(FriendlyByteBuf buffer) {
        return new RequestRuleSourceViewPayload(
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
