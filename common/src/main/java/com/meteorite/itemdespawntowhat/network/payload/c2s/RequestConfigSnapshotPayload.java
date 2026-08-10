package com.meteorite.itemdespawntowhat.network.payload.c2s;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.config.type.ConversionType;
import com.meteorite.itemdespawntowhat.network.codec.ConversionTypeStreamCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

// C2S：客户端请求服务端下发当前配置快照，用于编辑界面初始化。
public record RequestConfigSnapshotPayload(ConversionType configType) implements CustomPacketPayload {
    public static final Type<RequestConfigSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "request_config_snapshot")
    );

    public static final StreamCodec<ByteBuf, RequestConfigSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ConversionTypeStreamCodec.INSTANCE,
            RequestConfigSnapshotPayload::configType,
            RequestConfigSnapshotPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
