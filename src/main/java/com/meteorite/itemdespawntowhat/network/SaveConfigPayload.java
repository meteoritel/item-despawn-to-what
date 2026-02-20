package com.meteorite.itemdespawntowhat.network;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record SaveConfigPayload(ConfigType configType, String configData) implements CustomPacketPayload {

    public static final Type<SaveConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ItemDespawnToWhat.MOD_ID, "save_config")
    );

    public static final StreamCodec<ByteBuf, SaveConfigPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(
                    ordinal -> ConfigType.values()[ordinal],
                    ConfigType::ordinal
            ),
            SaveConfigPayload::configType,
            ByteBufCodecs.STRING_UTF8,
            SaveConfigPayload::configData,
            SaveConfigPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
