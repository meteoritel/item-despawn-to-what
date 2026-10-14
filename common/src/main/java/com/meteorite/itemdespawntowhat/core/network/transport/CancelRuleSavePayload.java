package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * C2S 请求取消对应保存；取消回执只有在文件工作退出后才视为终态。
 * @param sessionId 会话标识
 * @param operationId 保存操作标识
 */
public record CancelRuleSavePayload(String sessionId, String operationId) implements CustomPacketPayload {
    /** 保存取消通道。 */
    public static final Type<CancelRuleSavePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "cancel_rule_save"));
    /** 有界标识编解码器。 */
    public static final StreamCodec<FriendlyByteBuf, CancelRuleSavePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.sessionId(), RuleEditLimits.MAX_ID_CHARS);
                buffer.writeUtf(payload.operationId(), RuleEditLimits.MAX_ID_CHARS);
            }, buffer -> new CancelRuleSavePayload(buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                    buffer.readUtf(RuleEditLimits.MAX_ID_CHARS)));

    /** @return 保存取消载荷类型 */
    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
