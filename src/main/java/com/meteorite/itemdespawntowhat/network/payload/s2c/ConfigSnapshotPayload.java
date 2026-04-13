package com.meteorite.itemdespawntowhat.network.payload.s2c;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

// S2C：服务端下发配置快照 JSON，供客户端构建编辑界面。
public record ConfigSnapshotPayload(ConfigType configType, String configJson) implements CustomPacketPayload {
    public static final Type<ConfigSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ItemDespawnToWhat.MOD_ID, "config_snapshot")
    );

    public static final StreamCodec<ByteBuf, ConfigSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(
                    ordinal -> ConfigType.values()[ordinal],
                    ConfigType::ordinal
            ),
            ConfigSnapshotPayload::configType,
            ByteBufCodecs.STRING_UTF8,
            ConfigSnapshotPayload::configJson,
            ConfigSnapshotPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
