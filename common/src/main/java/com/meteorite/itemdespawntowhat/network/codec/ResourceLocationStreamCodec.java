package com.meteorite.itemdespawntowhat.network.codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 网络中传输转换类型 ID 的编解码器。
 */
public final class ResourceLocationStreamCodec {
    public static final StreamCodec<ByteBuf, ResourceLocation> INSTANCE = ByteBufCodecs.STRING_UTF8.map(
            ResourceLocation::parse,
            ResourceLocation::toString
    );

    private ResourceLocationStreamCodec() {
    }
}
