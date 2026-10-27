package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文本载荷的直发与分片切分器：快照、来源视图、变更集三个通道共用一套判定与字节计量。
 * 判定顺序：空文本 → 通道总量上限 → 不超过直发上限则整包 → 按 UTF-8 字节切片 → 分片总数上限。
 * 返回空列表表示无可发送内容，由调用方按各自契约回失败回执或拒绝提交。
 */
public final class RuleTextChunker {

    /**
     * 文本传输通道族：决定总量上限、分片通道 id 与直发载荷形态。
     */
    public enum Family {
        CHANGE_SET, SNAPSHOT, SOURCE_VIEW;

        /** 本 family 的总字节限额来自通道声明。 */
        public int maxBytes() { return RuleEditChannels.text(this).maxBytes(); }
        /** 本 family 的分片类型来自通道声明。 */
        public CustomPacketPayload.Type<RuleChunkPayload> chunkType() { return RuleEditChannels.text(this).chunkType(); }
        RuleTextPayload direct(String sessionId, String correlationId, String json) {
            return RuleEditChannels.text(this).direct().create(sessionId, correlationId, json);
        }
    }

    private RuleTextChunker() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 切分并生成待发送载荷；sessionId 与 correlationId 原样带入每一片。
     * @param family 通道族
     * @param sessionId 编辑会话 id
     * @param correlationId 请求标识（requestId 或 operationId）
     * @param json 完整文本
     * @return 整包单条、分片列表，或空列表表示无可发送内容
     */
    public static List<CustomPacketPayload> split(Family family, String sessionId, String correlationId, String json) {
        if (json == null || json.isEmpty()) {
            return List.of();
        }
        int bytes = RuleEditLimits.encodedLength(json);
        if (bytes > family.maxBytes()) {
            return List.of();
        }
        if (bytes <= RuleEditLimits.MAX_DIRECT_PACKET_BYTES) {
            return List.of(family.direct(sessionId, correlationId, json));
        }
        List<String> chunks = RuleEditTextChunks.splitByUtf8Bytes(json, RuleEditLimits.MAX_CHUNK_BYTES);
        if (chunks.isEmpty() || chunks.size() > RuleEditLimits.MAX_CHUNK_COUNT) {
            // 无法作为一次传输下发（接收侧同样拒收），返回空让调用方回结构化失败
            return List.of();
        }
        String transferId = UUID.randomUUID().toString();
        int count = chunks.size();
        List<CustomPacketPayload> payloads = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            payloads.add(new RuleChunkPayload(sessionId, correlationId, transferId, index, count, chunks.get(index), family.chunkType()));
        }
        return List.copyOf(payloads);
    }

    /**
     * 单个载荷的文本字节数；非文本载荷按 0 计。
     * @param payload 待计量载荷
     * @return UTF-8 字节数
     */
    public static int encodedBytes(@Nullable CustomPacketPayload payload) {
        return payload instanceof RuleTextPayload text ? RuleEditLimits.encodedLength(text.text()) : 0;
    }

    /**
     * 一组载荷的文本字节数之和。
     * @param packets 待计量载荷
     * @return UTF-8 字节数之和
     */
    public static long encodedBytes(List<CustomPacketPayload> packets) {
        long bytes = 0L;
        for (CustomPacketPayload payload : packets) {
            bytes += encodedBytes(payload);
        }
        return bytes;
    }
}