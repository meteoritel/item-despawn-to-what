package com.meteorite.itemdespawntowhat.network.payload.c2s;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record ReleaseEditSessionPayload() implements CustomPacketPayload {
    public static final Type<ReleaseEditSessionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ItemDespawnToWhat.MOD_ID, "release_edit_session")
    );

    public static final StreamCodec<FriendlyByteBuf, ReleaseEditSessionPayload> STREAM_CODEC =
            StreamCodec.unit(new ReleaseEditSessionPayload());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
