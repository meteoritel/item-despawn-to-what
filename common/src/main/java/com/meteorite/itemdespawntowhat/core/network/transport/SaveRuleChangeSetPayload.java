package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：客户端提交规则变更集（json 为 {@code RuleEditChangeSet.serialize()} 文本，契约 §3.3）。
 * operationId 由客户端生成，服务端对同一会话保留最近 16 个用于幂等；
 * 超过直发上限的变更集走 {@link SaveRuleChangeSetChunkPayload} 分片。
 */
public record SaveRuleChangeSetPayload(String sessionId, String operationId, String changeSetJson)
        implements CustomPacketPayload {

    public static final Type<SaveRuleChangeSetPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "save_rule_change_set")
    );

    public static final StreamCodec<FriendlyByteBuf, SaveRuleChangeSetPayload> STREAM_CODEC =
            StreamCodec.of(SaveRuleChangeSetPayload::encode, SaveRuleChangeSetPayload::decode);

    public SaveRuleChangeSetPayload {
        sessionId = sessionId == null ? "" : sessionId;
        operationId = operationId == null ? "" : operationId;
        changeSetJson = changeSetJson == null ? "" : changeSetJson;
    }

    // 编码
    private static void encode(FriendlyByteBuf buffer, SaveRuleChangeSetPayload payload) {
        buffer.writeUtf(payload.sessionId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.operationId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.changeSetJson(), RuleEditLimits.MAX_DIRECT_CHARS + RuleEditLimits.PACKET_SLACK_BYTES);
    }

    // 解码
    private static SaveRuleChangeSetPayload decode(FriendlyByteBuf buffer) {
        return new SaveRuleChangeSetPayload(
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_DIRECT_CHARS + RuleEditLimits.PACKET_SLACK_BYTES));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
