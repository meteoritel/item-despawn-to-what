package com.meteorite.itemdespawntowhat.network.configedit.c2s;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record RequestConfigSnapshotPayload(ConfigType configType) implements CustomPacketPayload {
    public static final Type<RequestConfigSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ItemDespawnToWhat.MOD_ID, "request_config_snapshot")
    );

    public static final StreamCodec<ByteBuf, RequestConfigSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(
                    ordinal -> ConfigType.values()[ordinal],
                    ConfigType::ordinal
            ),
            RequestConfigSnapshotPayload::configType,
            RequestConfigSnapshotPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
