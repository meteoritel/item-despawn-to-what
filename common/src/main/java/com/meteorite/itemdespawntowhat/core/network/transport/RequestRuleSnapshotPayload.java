package com.meteorite.itemdespawntowhat.core.network.transport;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：客户端请求覆盖层规则快照（无载荷字段）。
 * 服务端收到后按 per-player 打开编辑会话并下发 {@link RuleSnapshotPayload}。
 */
public record RequestRuleSnapshotPayload() implements CustomPacketPayload {

    public static final Type<RequestRuleSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "request_rule_snapshot")
    );

    public static final StreamCodec<ByteBuf, RequestRuleSnapshotPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestRuleSnapshotPayload());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
