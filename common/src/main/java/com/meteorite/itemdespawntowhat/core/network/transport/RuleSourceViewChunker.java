package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 来源视图下发切分器：把序列化后的来源视图文本切成整包或若干分片。
 * 小视图直发，大视图按 UTF-8 字节切分且不拆代理对；超过总量上限时拒绝下发，由调用方回失败回执。
 */
public final class RuleSourceViewChunker {

    private RuleSourceViewChunker() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 切分并生成下发载荷；sessionId/requestId 原样带入每一片，json 为空时返回空列表
    public static List<CustomPacketPayload> split(String sessionId, String requestId, String json) {
        if (json == null || json.isEmpty()) {
            return List.of();
        }
        int bytes = RuleEditLimits.encodedLength(json);
        if (bytes > RuleEditLimits.MAX_SOURCE_VIEW_BYTES) { return List.of(); }
        if (bytes <= RuleEditLimits.MAX_DIRECT_PACKET_BYTES) {
            return List.of(new RuleSourceViewPayload(sessionId, requestId, "SUCCESS", json));
        }
        List<String> chunks = RuleEditTextChunks.splitByUtf8Bytes(json, RuleEditLimits.MAX_CHUNK_BYTES);
        if (chunks.size() <= 1) {
            return List.of(new RuleSourceViewPayload(sessionId, requestId, "SUCCESS", json));
        }
        if (chunks.size() > RuleEditLimits.MAX_CHUNK_COUNT) {
            return List.of();
        }
        String transferId = UUID.randomUUID().toString();
        int count = chunks.size();
        List<CustomPacketPayload> payloads = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            payloads.add(new RuleSourceViewChunkPayload(sessionId, requestId, transferId, index, count, chunks.get(index)));
        }
        return List.copyOf(payloads);
    }
}
