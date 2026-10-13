package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：查询服务端缓存的普通物品参考寿命，不写入规则。
 * @param sessionId 已确认的编辑会话
 * @param requestId 用于丢弃过时响应的请求标识
 */
public record RequestRuleLifespanPayload(String sessionId, String requestId) implements CustomPacketPayload {
    public static final Type<RequestRuleLifespanPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "request_rule_lifespan"));
    public static final StreamCodec<FriendlyByteBuf, RequestRuleLifespanPayload> STREAM_CODEC =
            StreamCodec.of(RequestRuleLifespanPayload::encode, RequestRuleLifespanPayload::decode);

    private static void encode(FriendlyByteBuf buffer, RequestRuleLifespanPayload payload) {
        buffer.writeUtf(payload.sessionId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.requestId(), RuleEditLimits.MAX_ID_CHARS);
    }

    private static RequestRuleLifespanPayload decode(FriendlyByteBuf buffer) {
        return new RequestRuleLifespanPayload(buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS));
    }

    /** @return 本寿命查询通道的载荷类型 */
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
