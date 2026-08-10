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
 * 客户端提交指定类型的完整配置 JSON。
 */
public record SaveConfigPayload(ResourceLocation typeId, String configData) implements CustomPacketPayload {

    public static final Type<SaveConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "save_config")
    );

    public static final StreamCodec<ByteBuf, SaveConfigPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocationStreamCodec.INSTANCE,
            SaveConfigPayload::typeId,
            ByteBufCodecs.STRING_UTF8,
            SaveConfigPayload::configData,
            SaveConfigPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
