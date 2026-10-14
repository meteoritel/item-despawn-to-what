package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 快照下发切分器：把序列化后的快照文本切成整包或若干分片。
 * 小快照直发，大快照按 UTF-8 字节切分且不拆代理对；超过快照总量上限时拒绝下发。
 */
public final class RuleSnapshotChunker {

    private RuleSnapshotChunker() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 切分并生成下发载荷；sessionId/requestId 原样带入每一片，json 为空时返回空列表
    public static List<CustomPacketPayload> split(String sessionId, String requestId, String json) {
        if (json == null || json.isEmpty()) {
            return List.of();
        }
        int bytes = RuleEditLimits.encodedLength(json);
        if (bytes > RuleEditLimits.MAX_SNAPSHOT_BYTES) { return List.of(); }
        if (bytes <= RuleEditLimits.MAX_DIRECT_PACKET_BYTES) {
            return List.of(new RuleSnapshotPayload(sessionId, requestId, json));
        }
        List<String> chunks = RuleEditTextChunks.splitByUtf8Bytes(json, RuleEditLimits.MAX_CHUNK_BYTES);
        if (chunks.size() <= 1) {
            return List.of(new RuleSnapshotPayload(sessionId, requestId, json));
        }
        if (chunks.size() > RuleEditLimits.MAX_CHUNK_COUNT) {
            // 超过分片总数上限时无法下发（接收侧同样拒收），返回空让调用方回结构化失败回执
            return List.of();
        }
        String transferId = UUID.randomUUID().toString();
        int count = chunks.size();
        List<CustomPacketPayload> payloads = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            payloads.add(new RuleSnapshotChunkPayload(sessionId, requestId, transferId, index, count, chunks.get(index)));
        }
        return List.copyOf(payloads);
    }

}
