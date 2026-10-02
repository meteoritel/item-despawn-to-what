package com.meteorite.itemdespawntowhat.core.network.transport;

import java.nio.charset.StandardCharsets;

/**
 * 新链路配置编辑协议（阶段④）的通道命名空间与资源上限。
 * 与旧链路 {@code network.ConfigEditLimits} 完全独立，不共享常量也不互相引用；
 * 数值口径对齐旧链路的既有结论：原版 serverbound 自定义载荷上限 32767 字节、clientbound 上限 1 MiB。
 */
public final class RuleEditLimits {

    // 新链路 payload 的 ResourceLocation 命名空间，与旧链路通道区分
    public static final String NAMESPACE = "idtw";

    // 单包直发上限（内容字节）：原版 serverbound 自定义载荷整包上限 32767 字节，
    // 扣掉载荷 id、长度前缀等包头开销后取 32000，保证整包不越界
    public static final int MAX_DIRECT_PACKET_BYTES = 32_000;

    // 直发字符串编解码器的字符数上限（与字节阈值同量级，ASCII 场景一一对应）
    public static final int MAX_DIRECT_CHARS = MAX_DIRECT_PACKET_BYTES;

    // 分片载荷的单片上限（UTF-8 字节）
    public static final int MAX_CHUNK_BYTES = 30_000;

    // 一次变更集的字节上限
    public static final int MAX_CHANGE_SET_BYTES = 4 * 1024 * 1024;

    // 单片数量上限
    public static final int MAX_CHUNK_COUNT = (MAX_CHANGE_SET_BYTES + MAX_CHUNK_BYTES - 1) / MAX_CHUNK_BYTES;

    // transferId 的长度上限
    public static final int MAX_TRANSFER_ID_LENGTH = 64;

    // 每个玩家同时存活的分片传输上限
    public static final int MAX_TRANSFERS_PER_PLAYER = 2;

    // 分片传输的空闲超时（沿用旧链路 60s 口径）
    public static final long TRANSFER_TIMEOUT_MILLIS = 60_000L;

    // S2C 快照单包上限：原版 clientbound 自定义载荷上限 1 MiB，取整留余量
    public static final int MAX_SNAPSHOT_BYTES = 1_000_000;

    // 字符串编解码器的字符数上限：按字节上限折算（UTF-8 单字符最多 4 字节，取字节上限即可覆盖）
    public static final int MAX_SNAPSHOT_CHARS = MAX_SNAPSHOT_BYTES;

    // 分片字符串编解码器的字符数上限
    public static final int MAX_CHUNK_CHARS = MAX_CHUNK_BYTES;

    private RuleEditLimits() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 按 UTF-8 字节数计算长度，避免多字节字符绕过长度检查
    public static int encodedLength(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }
}
