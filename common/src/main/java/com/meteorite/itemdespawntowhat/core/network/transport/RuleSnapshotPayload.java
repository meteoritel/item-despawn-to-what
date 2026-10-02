package com.meteorite.itemdespawntowhat.core.network.transport;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：服务端下发规则快照（内容为 {@code RuleSnapshot.serialize()} 的 JSON 文本）。
 * 快照条目形状由 core/service 的 RuleSnapshotAssembler 冻结，网络层不解析内容。
 */
public record RuleSnapshotPayload(String snapshotJson) implements CustomPacketPayload {

    public static final Type<RuleSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "rule_snapshot")
    );

    public static final StreamCodec<ByteBuf, RuleSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(RuleEditLimits.MAX_SNAPSHOT_CHARS),
            RuleSnapshotPayload::snapshotJson,
            RuleSnapshotPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
