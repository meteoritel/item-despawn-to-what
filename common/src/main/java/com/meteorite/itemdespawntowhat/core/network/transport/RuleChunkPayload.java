package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 下行文本分片的统一视图：快照与来源视图共享同一套客户端重组逻辑。
 * 同一次传输由 sessionId + requestId 定位请求、transferId 区分批次，index 从 0 到 count-1。
 */
public interface RuleChunkPayload extends CustomPacketPayload {

    // 当前编辑会话 id
    String sessionId();

    // 请求标识：来源视图按请求配对，快照沿用既有 requestId
    String requestId();

    // 本次传输的批次标识
    String transferId();

    // 分片下标
    int index();

    // 分片总数
    int count();

    // 分片文本
    String chunk();
}
