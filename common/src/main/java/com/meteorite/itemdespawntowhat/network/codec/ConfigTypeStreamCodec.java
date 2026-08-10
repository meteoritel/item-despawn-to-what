package com.meteorite.itemdespawntowhat.network.codec;

import com.meteorite.itemdespawntowhat.config.ConfigType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 使用稳定字符串 ID 编解码配置类型。
 */
public final class ConfigTypeStreamCodec {
    public static final StreamCodec<ByteBuf, ConfigType> INSTANCE = ByteBufCodecs.STRING_UTF8.map(
            ConfigType::fromSerializedId,
            ConfigType::getSerializedId);

    private ConfigTypeStreamCodec() {
        throw new UnsupportedOperationException("Utility class");
    }
}
