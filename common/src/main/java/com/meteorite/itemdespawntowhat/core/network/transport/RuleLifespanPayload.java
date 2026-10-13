package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：服务端返回普通物品参考寿命，单位为 tick。
 * @param sessionId 已确认的编辑会话
 * @param requestId 用于丢弃过时响应的请求标识
 * @param lifespanTicks 参考自然寿命；零表示尚未采样
 */
public record RuleLifespanPayload(String sessionId, String requestId, int lifespanTicks) implements CustomPacketPayload {
    public static final Type<RuleLifespanPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "rule_lifespan"));
    public static final StreamCodec<FriendlyByteBuf, RuleLifespanPayload> STREAM_CODEC =
            StreamCodec.of(RuleLifespanPayload::encode, RuleLifespanPayload::decode);

    private static void encode(FriendlyByteBuf buffer, RuleLifespanPayload payload) {
        buffer.writeUtf(payload.sessionId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.requestId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeVarInt(payload.lifespanTicks());
    }

    private static RuleLifespanPayload decode(FriendlyByteBuf buffer) {
        return new RuleLifespanPayload(buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS), buffer.readVarInt());
    }

    /** @return 本寿命查询通道的载荷类型 */
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
