package com.meteorite.itemdespawntowhat.network;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record OpenGuiPayload() implements CustomPacketPayload {

    public static final Type<OpenGuiPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ItemDespawnToWhat.MOD_ID, "open_config_selection"));

    public static final StreamCodec<FriendlyByteBuf, OpenGuiPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenGuiPayload());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
