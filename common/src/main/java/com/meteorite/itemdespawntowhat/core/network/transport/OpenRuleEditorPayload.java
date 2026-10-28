package com.meteorite.itemdespawntowhat.core.network.transport;

import com.meteorite.itemdespawntowhat.core.api.RuleRuntimeLimits;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleSaveStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * S2C：服务端要求客户端打开规则编辑入口，并携带会话握手信息。
 * statusCode 为 {@link RuleSaveStatus} 名；取锁失败时同样下发本载荷，客户端按码提示持有者。
 * limits 携带放置半径和三轴整格偏移上限，界面与服务端使用同一份限额。
 */
public record OpenRuleEditorPayload(String sessionId, String targetId, int protocolVersion,
                                    int contextRevision, RuleRuntimeLimits limits, String statusCode,
                                    List<String> messageArgs, String fallbackMessage)
        implements CustomPacketPayload {

    public static final Type<OpenRuleEditorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "open_rule_editor")
    );

    public static final StreamCodec<FriendlyByteBuf, OpenRuleEditorPayload> STREAM_CODEC =
            StreamCodec.of(OpenRuleEditorPayload::encode, OpenRuleEditorPayload::decode);

    public OpenRuleEditorPayload {
        sessionId = sessionId == null ? "" : sessionId;
        targetId = targetId == null ? "" : targetId;
        statusCode = statusCode == null ? RuleSaveStatus.UNAVAILABLE.id() : statusCode;
        messageArgs = messageArgs == null ? List.of() : List.copyOf(messageArgs);
        fallbackMessage = fallbackMessage == null ? "" : fallbackMessage;
    }

    // 编码：字段顺序固定
    private static void encode(FriendlyByteBuf buffer, OpenRuleEditorPayload payload) {
        buffer.writeUtf(payload.sessionId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.targetId(), RuleEditLimits.MAX_CODE_CHARS);
        buffer.writeVarInt(payload.protocolVersion());
        buffer.writeVarInt(payload.contextRevision());
        buffer.writeVarInt(payload.limits().maxPlaceRadius());
        buffer.writeVarInt(payload.limits().maxEffectOffsetX());
        buffer.writeVarInt(payload.limits().maxEffectOffsetY());
        buffer.writeVarInt(payload.limits().maxEffectOffsetZ());
        buffer.writeUtf(payload.statusCode(), RuleEditLimits.MAX_CODE_CHARS);
        RuleEditPayloadCodec.writeStrings(buffer, payload.messageArgs());
        buffer.writeUtf(payload.fallbackMessage(), RuleEditLimits.MAX_FALLBACK_CHARS);
    }

    // 解码
    private static OpenRuleEditorPayload decode(FriendlyByteBuf buffer) {
        return new OpenRuleEditorPayload(
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_CODE_CHARS),
                buffer.readVarInt(),
                buffer.readVarInt(),
                new RuleRuntimeLimits(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()),
                buffer.readUtf(RuleEditLimits.MAX_CODE_CHARS),
                RuleEditPayloadCodec.readStrings(buffer),
                buffer.readUtf(RuleEditLimits.MAX_FALLBACK_CHARS));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
