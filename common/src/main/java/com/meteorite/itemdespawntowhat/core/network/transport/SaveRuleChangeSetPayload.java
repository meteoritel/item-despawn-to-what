package com.meteorite.itemdespawntowhat.core.network.transport;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：客户端提交规则变更集（内容为 {@code RuleEditChangeSet.serialize()} 的 JSON 文本）。
 * 超过直发上限时改走 {@link SaveRuleChangeSetChunkPayload} 分片。
 */
public record SaveRuleChangeSetPayload(String changeSetJson) implements CustomPacketPayload {

    public static final Type<SaveRuleChangeSetPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "save_rule_change_set")
    );

    public static final StreamCodec<ByteBuf, SaveRuleChangeSetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(RuleEditLimits.MAX_DIRECT_CHARS),
            SaveRuleChangeSetPayload::changeSetJson,
            SaveRuleChangeSetPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
