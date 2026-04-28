package com.meteorite.itemdespawntowhat.network.payload.s2c;

import com.meteorite.itemdespawntowhat.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

// S2C：服务端通知客户端编辑会话超时，强制关闭编辑器。
public record ForceCloseEditorPayload() implements CustomPacketPayload {

    public static final Type<ForceCloseEditorPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "force_close_editor"));

    public static final StreamCodec<FriendlyByteBuf, ForceCloseEditorPayload> STREAM_CODEC =
            StreamCodec.unit(new ForceCloseEditorPayload());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
