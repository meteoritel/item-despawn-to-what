package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：来源视图应答。小的来源视图直发整包，超限时改发 {@link RuleChunkPayload} 分片。
 * requestId 原样回带，客户端据此把应答配到请求上；失败时 sourceViewJson 为空且 statusCode 非 SUCCESS。
 */
public record RuleSourceViewPayload(String sessionId, String requestId, String statusCode, String sourceViewJson)
        implements RuleTextPayload {

    public static final Type<RuleSourceViewPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "rule_source_view")
    );

    public static final StreamCodec<FriendlyByteBuf, RuleSourceViewPayload> STREAM_CODEC =
            StreamCodec.of(RuleSourceViewPayload::encode, RuleSourceViewPayload::decode);

    public RuleSourceViewPayload {
        sessionId = sessionId == null ? "" : sessionId;
        requestId = requestId == null ? "" : requestId;
        statusCode = statusCode == null ? "" : statusCode;
        sourceViewJson = sourceViewJson == null ? "" : sourceViewJson;
    }

    // 编码
    private static void encode(FriendlyByteBuf buffer, RuleSourceViewPayload payload) {
        buffer.writeUtf(payload.sessionId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.requestId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.statusCode(), RuleEditLimits.MAX_CODE_CHARS);
        buffer.writeUtf(payload.sourceViewJson(),
                RuleEditLimits.MAX_SOURCE_VIEW_CHARS + RuleEditLimits.PACKET_SLACK_BYTES);
    }

    // 解码
    private static RuleSourceViewPayload decode(FriendlyByteBuf buffer) {
        return new RuleSourceViewPayload(
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_CODE_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_SOURCE_VIEW_CHARS + RuleEditLimits.PACKET_SLACK_BYTES));
    }

    @Override
    public String text() {
        return sourceViewJson;
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
