package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/***
 * 变更集提交切分器：不超过直发上限时返回单个 {@link SaveRuleChangeSetPayload}；
 * 超限时按 UTF-8 字节切成 {@link SaveRuleChangeSetChunkPayload} 分片，服务端按 transferId 重组后走同一保存流程。
 * 超过契约上限（{@link RuleEditLimits#MAX_CHANGE_SET_BYTES}）或 json 为空时返回空列表，由调用方按"请求不合法/无内容"处理。
 */
public final class RuleChangeSetChunker {

    private RuleChangeSetChunker() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 切分并生成上行载荷
    public static List<CustomPacketPayload> split(String sessionId, String operationId, String json) {
        if (json == null || json.isEmpty()) {
            return List.of();
        }
        int bytes = RuleEditLimits.encodedLength(json);
        if (bytes > RuleEditLimits.MAX_CHANGE_SET_BYTES) {
            return List.of();
        }
        if (bytes <= RuleEditLimits.MAX_DIRECT_PACKET_BYTES) {
            return List.of(new SaveRuleChangeSetPayload(sessionId, operationId, json));
        }
        List<String> chunks = RuleEditTextChunks.splitByUtf8Bytes(json, RuleEditLimits.MAX_CHUNK_BYTES);
        if (chunks.isEmpty() || chunks.size() > RuleEditLimits.MAX_CHUNK_COUNT) {
            return List.of();
        }
        String transferId = UUID.randomUUID().toString();
        int count = chunks.size();
        List<CustomPacketPayload> payloads = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            payloads.add(new SaveRuleChangeSetChunkPayload(sessionId, operationId, transferId, index, count, chunks.get(index)));
        }
        return List.copyOf(payloads);
    }
}
