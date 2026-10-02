package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：目录分页结果（契约 §3.3、§3.6）。json 为 {@code RuleCatalog.serialize()} 文本，
 * P3 阶段为骨架（条目为空、lastPage=true），目录数据由 P4/P5 接入。
 */
public record RuleCatalogPayload(String sessionId, String requestId, String catalogType,
                                 int revision, String json, boolean lastPage) implements CustomPacketPayload {

    public static final Type<RuleCatalogPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "rule_catalog")
    );

    public static final StreamCodec<FriendlyByteBuf, RuleCatalogPayload> STREAM_CODEC =
            StreamCodec.of(RuleCatalogPayload::encode, RuleCatalogPayload::decode);

    public RuleCatalogPayload {
        sessionId = sessionId == null ? "" : sessionId;
        requestId = requestId == null ? "" : requestId;
        catalogType = catalogType == null ? "" : catalogType;
        json = json == null ? "" : json;
    }

    // 编码
    private static void encode(FriendlyByteBuf buffer, RuleCatalogPayload payload) {
        buffer.writeUtf(payload.sessionId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.requestId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.catalogType(), RuleEditLimits.MAX_CODE_CHARS);
        buffer.writeVarInt(payload.revision());
        buffer.writeUtf(payload.json(), RuleEditLimits.MAX_SNAPSHOT_CHARS + RuleEditLimits.PACKET_SLACK_BYTES);
        buffer.writeBoolean(payload.lastPage());
    }

    // 解码
    private static RuleCatalogPayload decode(FriendlyByteBuf buffer) {
        return new RuleCatalogPayload(
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_CODE_CHARS),
                buffer.readVarInt(),
                buffer.readUtf(RuleEditLimits.MAX_SNAPSHOT_CHARS + RuleEditLimits.PACKET_SLACK_BYTES),
                buffer.readBoolean());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
