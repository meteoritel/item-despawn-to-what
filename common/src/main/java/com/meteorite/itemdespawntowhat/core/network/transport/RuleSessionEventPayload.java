package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/** S2C：会话生命周期事件，与保存 operationId 分开交付。 */
public record RuleSessionEventPayload(String sessionId, String statusCode) implements CustomPacketPayload {
    public static final Type<RuleSessionEventPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "rule_session_event"));
    public static final StreamCodec<FriendlyByteBuf, RuleSessionEventPayload> STREAM_CODEC = StreamCodec.of(RuleSessionEventPayload::encode, RuleSessionEventPayload::decode);

    private static void encode(FriendlyByteBuf buffer, RuleSessionEventPayload value) {
        buffer.writeUtf(value.sessionId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(value.statusCode(), RuleEditLimits.MAX_CODE_CHARS);
    }

    private static RuleSessionEventPayload decode(FriendlyByteBuf buffer) {
        return new RuleSessionEventPayload(buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_CODE_CHARS));
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
