package com.meteorite.itemdespawntowhat.network.payload.s2c;

import com.meteorite.itemdespawntowhat.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

// S2C：服务端通知客户端打开配置选择界面。
public record OpenGuiPayload() implements CustomPacketPayload {

    public static final Type<OpenGuiPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "open_config_selection"));

    public static final StreamCodec<FriendlyByteBuf, OpenGuiPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenGuiPayload());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
