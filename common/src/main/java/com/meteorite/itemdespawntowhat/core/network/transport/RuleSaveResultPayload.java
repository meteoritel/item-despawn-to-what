package com.meteorite.itemdespawntowhat.core.network.transport;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：保存结果或冲突说明文本（成功回执、版本冲突差异、无权限等）。
 * 统一用一段人类可读文本承载，客户端门面直接转交界面层展示。
 */
public record RuleSaveResultPayload(String text) implements CustomPacketPayload {

    public static final Type<RuleSaveResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "rule_save_result")
    );

    public static final StreamCodec<ByteBuf, RuleSaveResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(RuleEditLimits.MAX_SNAPSHOT_CHARS),
            RuleSaveResultPayload::text,
            RuleSaveResultPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
