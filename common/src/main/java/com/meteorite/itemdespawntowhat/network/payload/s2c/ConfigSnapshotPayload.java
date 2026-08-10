package com.meteorite.itemdespawntowhat.network.payload.s2c;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.network.codec.ResourceLocationStreamCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * 服务端下发指定类型的配置快照 JSON。
 */
public record ConfigSnapshotPayload(ResourceLocation typeId, String configJson) implements CustomPacketPayload {
    public static final Type<ConfigSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "config_snapshot")
    );

    public static final StreamCodec<ByteBuf, ConfigSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocationStreamCodec.INSTANCE,
            ConfigSnapshotPayload::typeId,
            ByteBufCodecs.STRING_UTF8,
            ConfigSnapshotPayload::configJson,
            ConfigSnapshotPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
