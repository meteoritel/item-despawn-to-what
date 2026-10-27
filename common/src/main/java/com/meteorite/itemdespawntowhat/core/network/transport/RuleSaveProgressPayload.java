package com.meteorite.itemdespawntowhat.core.network.transport;

import com.meteorite.itemdespawntowhat.core.service.edit.RuleSaveProgress;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * S2C 保存进度；客户端按会话、操作和递增序号丢弃过时样本。
 * @param sessionId 会话标识
 * @param operationId 保存操作标识
 * @param sequence 本操作内递增序号
 * @param progress 不可变进度样本
 */
public record RuleSaveProgressPayload(String sessionId, String operationId, long sequence, RuleSaveProgress progress)
        implements CustomPacketPayload {
    /** 保存进度通道。 */
    public static final Type<RuleSaveProgressPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RuleEditLimits.NAMESPACE, "rule_save_progress"));
    /** 有界字段编解码器。 */
    public static final StreamCodec<FriendlyByteBuf, RuleSaveProgressPayload> STREAM_CODEC = StreamCodec.of(
            RuleSaveProgressPayload::encode, RuleSaveProgressPayload::decode);

    private static void encode(FriendlyByteBuf buffer, RuleSaveProgressPayload payload) {
        buffer.writeUtf(payload.sessionId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeUtf(payload.operationId(), RuleEditLimits.MAX_ID_CHARS);
        buffer.writeVarLong(payload.sequence());
        RuleSaveProgress progress = payload.progress();
        buffer.writeEnum(progress.stage());
        buffer.writeVarInt(progress.completed());
        buffer.writeVarInt(progress.total());
        buffer.writeUtf(RuleEditPayloadCodec.diagnosticText(progress.current(), RuleEditLimits.MAX_PROGRESS_CURRENT_CHARS), RuleEditLimits.MAX_PROGRESS_CURRENT_CHARS);
        buffer.writeBoolean(progress.cancellable());
    }

    private static RuleSaveProgressPayload decode(FriendlyByteBuf buffer) {
        return new RuleSaveProgressPayload(buffer.readUtf(RuleEditLimits.MAX_ID_CHARS),
                buffer.readUtf(RuleEditLimits.MAX_ID_CHARS), buffer.readVarLong(),
                new RuleSaveProgress(buffer.readEnum(RuleSaveProgress.Stage.class), buffer.readVarInt(),
                        buffer.readVarInt(), buffer.readUtf(RuleEditLimits.MAX_PROGRESS_CURRENT_CHARS), buffer.readBoolean()));
    }

    /** @return 保存进度载荷类型 */
    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
