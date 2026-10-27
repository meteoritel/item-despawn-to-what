package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/** S2C：独立查询失败：种类、会话号和请求号共同定位在途请求。 */
public record RuleQueryResultPayload(String kind, String sessionId, String requestId, String statusCode) implements CustomPacketPayload {
    public static final Type<RuleQueryResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "rule_query_result"));
    public static final StreamCodec<FriendlyByteBuf, RuleQueryResultPayload> STREAM_CODEC = StreamCodec.of(RuleQueryResultPayload::encode, RuleQueryResultPayload::decode);

    private static void encode(FriendlyByteBuf buffer, RuleQueryResultPayload value) {
        buffer.writeUtf(value.kind(), RuleEditLimits.MAX_CODE_CHARS);
        buffer.writeUtf(value.sessionId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(value.requestId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(value.statusCode(), RuleEditLimits.MAX_CODE_CHARS);
    }

    private static RuleQueryResultPayload decode(FriendlyByteBuf buffer) {
        return new RuleQueryResultPayload(buffer.readUtf(RuleEditLimits.MAX_CODE_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_CODE_CHARS));
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
