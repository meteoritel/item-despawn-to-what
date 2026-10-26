package com.meteorite.itemdespawntowhat.client.net;

import com.meteorite.itemdespawntowhat.core.network.transport.RuleChunkAssembly;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleChunkPayload;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditLimits;

/**
 * 下行文本分片的客户端重组缓冲：同一 transferId 的分片收齐后按 index 顺序拼接为完整 JSON。
 * 快照与来源视图共用本实现；同一时刻只保留最近一批分片，缓冲为空、批次换新或超过 60 秒无进展都会重开，
 * 重复下标保留首片。形状校验与批次判定复用 {@link RuleChunkAssembly}，与服务端累加器同一套口径。
 */
final class RuleChunkBuffer {

    // 当前批次；null 表示没有在途传输
    private RuleChunkAssembly.Batch batch;

    // 接收一片；收齐返回拼接后的完整 JSON，否则返回 null
    synchronized String accept(RuleChunkPayload payload, long tick) {
        RuleChunkAssembly.Chunk chunk = RuleChunkAssembly.validate(payload);
        if (chunk == null) {
            return null;
        }
        if (batch == null || isStale(tick) || !batch.matches(chunk)) {
            batch = RuleChunkAssembly.Batch.start(chunk);
        }
        batch.touch(tick);
        batch.offer(chunk);
        if (!batch.complete()) {
            return null;
        }
        String json = batch.json();
        clear();
        return json;
    }

    synchronized void clear() {
        batch = null;
    }

    // 上一批次是否已超时
    private boolean isStale(long tick) {
        return batch == null || tick - batch.lastTick() > RuleEditLimits.TRANSFER_TIMEOUT_TICKS;
    }
}
