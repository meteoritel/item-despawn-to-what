package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：客户端请求选择目录的一页（契约 §3.3、§3.6）。
 * P3 阶段服务端只做会话校验与骨架应答，目录内容由 P4/P5 接入。
 */
public record RequestRuleCatalogPayload(String sessionId, String requestId, String catalogType,
                                        String filter, int page, int pageSize) implements CustomPacketPayload {

    // 单页条目数上限（超出按 INVALID_REQUEST 拒绝）
    public static final int MAX_PAGE_SIZE = 200;

    public static final Type<RequestRuleCatalogPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "request_rule_catalog")
    );

    public static final StreamCodec<FriendlyByteBuf, RequestRuleCatalogPayload> STREAM_CODEC =
            StreamCodec.of(RequestRuleCatalogPayload::encode, RequestRuleCatalogPayload::decode);

    public RequestRuleCatalogPayload {
        sessionId = sessionId == null ? "" : sessionId;
        requestId = requestId == null ? "" : requestId;
        catalogType = catalogType == null ? "" : catalogType;
        filter = filter == null ? "" : filter;
    }

    // 编码
    private static void encode(FriendlyByteBuf buffer, RequestRuleCatalogPayload payload) {
        buffer.writeUtf(payload.sessionId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.requestId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.catalogType(), RuleEditLimits.MAX_CODE_CHARS);
        buffer.writeUtf(payload.filter(), RuleEditLimits.MAX_FALLBACK_CHARS);
        buffer.writeVarInt(payload.page());
        buffer.writeVarInt(payload.pageSize());
    }

    // 解码
    private static RequestRuleCatalogPayload decode(FriendlyByteBuf buffer) {
        return new RequestRuleCatalogPayload(
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_CODE_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_FALLBACK_CHARS),
                buffer.readVarInt(),
                buffer.readVarInt());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
