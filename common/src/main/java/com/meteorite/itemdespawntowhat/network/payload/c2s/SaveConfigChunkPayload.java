package com.meteorite.itemdespawntowhat.network.payload.c2s;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.network.codec.ResourceLocationStreamCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端提交指定类型的大配置分片。
 */
public record SaveConfigChunkPayload(
        ResourceLocation typeId,
        String transferId,
        int chunkIndex,
        int chunkCount,
        String chunkData
) implements CustomPacketPayload {

    public static final Type<SaveConfigChunkPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "save_config_chunk")
    );

    public static final StreamCodec<ByteBuf, SaveConfigChunkPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocationStreamCodec.INSTANCE,
            SaveConfigChunkPayload::typeId,
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
