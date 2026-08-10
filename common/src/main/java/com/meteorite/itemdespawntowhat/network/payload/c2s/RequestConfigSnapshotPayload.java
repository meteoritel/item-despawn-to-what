package com.meteorite.itemdespawntowhat.network.payload.c2s;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.network.codec.ResourceLocationStreamCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端请求服务端下发指定类型的配置快照。
 */
public record RequestConfigSnapshotPayload(ResourceLocation typeId) implements CustomPacketPayload {
    public static final Type<RequestConfigSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "request_config_snapshot")
    );

    public static final StreamCodec<ByteBuf, RequestConfigSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocationStreamCodec.INSTANCE,
            RequestConfigSnapshotPayload::typeId,
            RequestConfigSnapshotPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
