package com.meteorite.itemdespawntowhat.network.codec;

import com.meteorite.itemdespawntowhat.config.type.ConversionType;
import com.meteorite.itemdespawntowhat.config.type.ConversionTypeRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 按 ResourceLocation id 编解码可扩展转换类型。
 */
public final class ConversionTypeStreamCodec {
    public static final StreamCodec<ByteBuf, ConversionType> INSTANCE = ByteBufCodecs.STRING_UTF8.map(
            ConversionTypeRegistry::byId,
            ConversionType::getSerializedId);

    private ConversionTypeStreamCodec() {
    }
}
