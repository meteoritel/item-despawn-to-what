package com.meteorite.itemdespawntowhat.client.net;

import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditLimits;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSnapshotChunkPayload;

/***
 * 快照分片的客户端重组缓冲：同一 transferId 的分片收齐后按 index 顺序拼接为完整 JSON。
 * 同一时刻只保留最近一批分片；会话切换、重复下标、超过 60 秒无进展都会重置缓冲。
 */
final class RuleSnapshotChunkBuffer {

    // 当前批次的会话 id
    private String sessionId = "";
    // 当前批次的传输 id
    private String transferId = "";
    // 本批次分片总数
    private int count;
    // 已收到的分片内容，下标即 index
    private String[] chunks = new String[0];
    // 已收到的分片数
    private int received;
    // 最近一次收到分片的客户端 tick
    private long lastTick;

    // 接收一片；收齐返回拼接后的完整 JSON，否则返回 null
    synchronized String accept(RuleSnapshotChunkPayload payload, long tick) {
        if (payload == null || payload.transferId().isEmpty() || payload.count() <= 0
                || payload.count() > RuleEditLimits.MAX_CHUNK_COUNT
                || payload.index() < 0 || payload.index() >= payload.count()) {
            return null;
        }
        if (isStale(tick) || !payload.transferId().equals(transferId)
                || !payload.sessionId().equals(sessionId) || payload.count() != count) {
            start(payload, tick);
        }
        lastTick = tick;
        if (chunks[payload.index()] == null) {
            chunks[payload.index()] = payload.chunk();
            received++;
        }
        if (received < count) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (String chunk : chunks) {
            builder.append(chunk == null ? "" : chunk);
        }
        String json = builder.toString();
        clear();
        return json;
    }

    // 清空缓冲
    synchronized void clear() {
        sessionId = "";
        transferId = "";
        count = 0;
        chunks = new String[0];
        received = 0;
        lastTick = 0L;
    }

    // 开启新批次
    private void start(RuleSnapshotChunkPayload payload, long tick) {
        sessionId = payload.sessionId();
        transferId = payload.transferId();
        count = payload.count();
        chunks = new String[count];
        received = 0;
        lastTick = tick;
    }

    // 上一批次是否已超时
    private boolean isStale(long tick) {
        return transferId.isEmpty() || tick - lastTick > RuleEditLimits.TRANSFER_TIMEOUT_TICKS;
    }
}
