package com.meteorite.itemdespawntowhat.core.network.transport;

import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSaveStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

/**
 * S2C：服务端要求客户端打开规则编辑入口，并携带会话握手信息（契约 §3.3）。
 * statusCode 为 {@link RuleSaveStatus} 名；取锁失败时同样下发本载荷，客户端按码提示持有者。
 */
public record OpenRuleEditorPayload(String sessionId, String targetId, int protocolVersion,
                                    int contextRevision, String statusCode, List<String> messageArgs,
                                    String fallbackMessage) implements CustomPacketPayload {

    public static final Type<OpenRuleEditorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "open_rule_editor")
    );

    public static final StreamCodec<FriendlyByteBuf, OpenRuleEditorPayload> STREAM_CODEC =
            StreamCodec.of(OpenRuleEditorPayload::encode, OpenRuleEditorPayload::decode);

    // 客户端结构化载荷消费者；由 client/net 的 RuleEditClientWorkspace 安装，是唯一的开屏路径
    private static volatile Consumer<OpenRuleEditorPayload> payloadSink;

    public OpenRuleEditorPayload {
        sessionId = sessionId == null ? "" : sessionId;
        targetId = targetId == null ? "" : targetId;
        statusCode = statusCode == null ? RuleSaveStatus.UNAVAILABLE.id() : statusCode;
        messageArgs = messageArgs == null ? List.of() : List.copyOf(messageArgs);
        fallbackMessage = fallbackMessage == null ? "" : fallbackMessage;
    }

    // 安装/清除结构化载荷消费者；传 null 表示清除
    public static void installOpenEditorPayloadSink(Consumer<OpenRuleEditorPayload> sink) {
        payloadSink = sink;
    }

    // 分发完整载荷：只交给结构化消费者（client/net 工作区），尚无消费者时静默丢弃
    public static void dispatchOpenEditor(OpenRuleEditorPayload payload) {
        if (payload == null) {
            return;
        }
        Consumer<OpenRuleEditorPayload> sink = payloadSink;
        if (sink != null) {
            sink.accept(payload);
        }
    }

    // 编码：字段顺序与契约 §3.3 一致
    private static void encode(FriendlyByteBuf buffer, OpenRuleEditorPayload payload) {
        buffer.writeUtf(payload.sessionId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.targetId(), RuleEditLimits.MAX_CODE_CHARS);
        buffer.writeVarInt(payload.protocolVersion());
        buffer.writeVarInt(payload.contextRevision());
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
                buffer.readUtf(RuleEditLimits.MAX_CODE_CHARS),
                RuleEditPayloadCodec.readStrings(buffer),
                buffer.readUtf(RuleEditLimits.MAX_FALLBACK_CHARS));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
