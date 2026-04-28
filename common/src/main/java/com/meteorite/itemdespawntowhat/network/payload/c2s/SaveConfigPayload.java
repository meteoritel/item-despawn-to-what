package com.meteorite.itemdespawntowhat.network.payload.c2s;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

// C2S：客户端提交最终配置 JSON，由服务端负责校验、落盘和刷新缓存。
public record SaveConfigPayload(ConfigType configType, String configData) implements CustomPacketPayload {

    public static final Type<SaveConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "save_config")
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
