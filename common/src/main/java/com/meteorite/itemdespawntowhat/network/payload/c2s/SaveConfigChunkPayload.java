package com.meteorite.itemdespawntowhat.network.payload.c2s;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

// C2S：客户端提交大配置保存的分包，服务端按 transferId 重组后落盘。
public record SaveConfigChunkPayload(
        ConfigType configType,
        String transferId,
        int chunkIndex,
        int chunkCount,
        String chunkData
) implements CustomPacketPayload {

    public static final Type<SaveConfigChunkPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "save_config_chunk")
    );

    public static final StreamCodec<ByteBuf, SaveConfigChunkPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(
                    ordinal -> ConfigType.values()[ordinal],
                    ConfigType::ordinal
            ),
            SaveConfigChunkPayload::configType,
            ByteBufCodecs.STRING_UTF8,
            SaveConfigChunkPayload::transferId,
            ByteBufCodecs.VAR_INT,
            SaveConfigChunkPayload::chunkIndex,
            ByteBufCodecs.VAR_INT,
            SaveConfigChunkPayload::chunkCount,
            ByteBufCodecs.STRING_UTF8,
            SaveConfigChunkPayload::chunkData,
            SaveConfigChunkPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
