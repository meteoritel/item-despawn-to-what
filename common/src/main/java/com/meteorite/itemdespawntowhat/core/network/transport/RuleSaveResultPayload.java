package com.meteorite.itemdespawntowhat.core.network.transport;

import com.meteorite.itemdespawntowhat.core.network.protocol.RuleIssue;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSaveStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * S2C：保存/请求回执（契约 §3.3、§3.4、§3.5）。客户端只按 statusCode 分支，禁止解析文案。
 * messageCode 为全限定翻译 key，fallbackMessage 供缺失翻译时兜底展示。
 */
public record RuleSaveResultPayload(String sessionId, String operationId, String statusCode,
                                    String messageCode, List<String> messageArgs, int resultVersion,
                                    boolean writtenToDisk, boolean reloaded, List<RuleIssue> issues)
        implements CustomPacketPayload {

    public static final Type<RuleSaveResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "rule_save_result")
    );

    public static final StreamCodec<FriendlyByteBuf, RuleSaveResultPayload> STREAM_CODEC =
            StreamCodec.of(RuleSaveResultPayload::encode, RuleSaveResultPayload::decode);

    public RuleSaveResultPayload {
        sessionId = sessionId == null ? "" : sessionId;
        operationId = operationId == null ? "" : operationId;
        statusCode = statusCode == null ? RuleSaveStatus.UNAVAILABLE.id() : statusCode;
        messageCode = messageCode == null ? "" : messageCode;
        messageArgs = messageArgs == null ? List.of() : List.copyOf(messageArgs);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    // 便捷构造：按状态码组装回执
    public static RuleSaveResultPayload of(String sessionId, String operationId, RuleSaveStatus status,
                                           String messageCode, List<String> messageArgs, int resultVersion,
                                           boolean writtenToDisk, boolean reloaded, List<RuleIssue> issues) {
        return new RuleSaveResultPayload(sessionId, operationId, status.id(), messageCode, messageArgs,
                resultVersion, writtenToDisk, reloaded, issues);
    }

    // 编码：字段顺序与契约 §3.3 一致
    private static void encode(FriendlyByteBuf buffer, RuleSaveResultPayload payload) {
        buffer.writeUtf(payload.sessionId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.operationId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.statusCode(), RuleEditLimits.MAX_CODE_CHARS);
        buffer.writeUtf(payload.messageCode(), RuleEditLimits.MAX_CODE_CHARS);
        RuleEditPayloadCodec.writeStrings(buffer, payload.messageArgs());
        buffer.writeVarInt(payload.resultVersion());
        buffer.writeBoolean(payload.writtenToDisk());
        buffer.writeBoolean(payload.reloaded());
        RuleEditPayloadCodec.writeIssues(buffer, payload.issues());
    }

    // 解码
    private static RuleSaveResultPayload decode(FriendlyByteBuf buffer) {
        return new RuleSaveResultPayload(
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_CODE_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_CODE_CHARS),
                RuleEditPayloadCodec.readStrings(buffer),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                RuleEditPayloadCodec.readIssues(buffer));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
