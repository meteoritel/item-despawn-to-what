package com.meteorite.itemdespawntowhat.core.network.transport;

import com.meteorite.itemdespawntowhat.Constants;
import java.nio.charset.StandardCharsets;

/**
 * 配置编辑协议的通道命名空间与资源上限。
 * 数值口径：serverbound 自定义载荷整包 32767 字节，clientbound 侧预留 4 MiB。
 */
public final class RuleEditLimits {

    // payload 的 ResourceLocation 命名空间
    public static final String NAMESPACE = Constants.ID_NAMESPACE;

    // 单包直发上限（内容字节）：serverbound 整包上限 32767，扣掉包头开销后取 32000
    public static final int MAX_DIRECT_PACKET_BYTES = 32_000;

    // 直发字符串编解码器的字符数上限（与字节阈值同量级，ASCII 场景一一对应）
    public static final int MAX_DIRECT_CHARS = MAX_DIRECT_PACKET_BYTES;

    // 分片载荷的单片上限（UTF-8 字节）
    public static final int MAX_CHUNK_BYTES = 30_000;

    // 一次变更集的字节上限
    public static final int MAX_CHANGE_SET_BYTES = 4 * 1024 * 1024;

    // 单次变更集最多 2048 条；协议解析与文件写入层均复用该上限。
    public static final int MAX_EDITS_PER_CHANGE_SET = 2048;

    // 单次保存步骤最多处理 8 条；同时受公共 tick 预算与本步骤 1ms 软预算约束。
    public static final int SAVE_ENTRIES_PER_STEP = 8;
    public static final long SAVE_STEP_BUDGET_NANOS = 1_000_000L;

    // 单次快照步骤最多处理 32 条；只按条数与公共 tick 预算节流，不设单步时间预算。
    public static final int SNAPSHOT_ENTRIES_PER_STEP = 32;

    // 快照分片投递节拍：每 tick 最多下发 4 片，缩短大快照的投递 tick 数。
    public static final int SNAPSHOT_CHUNKS_PER_TICK = 4;

    // 单片数量上限
    public static final int MAX_CHUNK_COUNT = (MAX_CHANGE_SET_BYTES + MAX_CHUNK_BYTES - 1) / MAX_CHUNK_BYTES;

    // transferId 的长度上限
    public static final int MAX_TRANSFER_ID_LENGTH = 64;

    // 每个玩家同时存活的分片传输上限
    public static final int MAX_TRANSFERS_PER_PLAYER = 2;

    // 分片传输的空闲超时（60s，与既有链路口径一致）
    public static final long TRANSFER_TIMEOUT_MILLIS = 60_000L;

    // 分片传输空闲超时的 tick 口径（20 tick = 1 秒）：时钟一律用服务端活动 tick，暂停时不流逝
    public static final long TRANSFER_TIMEOUT_TICKS = TRANSFER_TIMEOUT_MILLIS / 50L;

    // S2C 快照单包上限
    public static final int MAX_SNAPSHOT_BYTES = 4 * 1024 * 1024;

    // 单条规则的来源视图（基底 + 覆盖原始 JSON 与元数据）字节上限：与快照总量同量级，超限回失败回执
    public static final int MAX_SOURCE_VIEW_BYTES = 4 * 1024 * 1024;

    // 字符串编解码器的字符数上限：按字节上限折算（UTF-8 单字符最多 4 字节，取字节上限即可覆盖）
    public static final int MAX_SNAPSHOT_CHARS = MAX_SNAPSHOT_BYTES;

    // 来源视图字符串编解码器的字符数上限
    public static final int MAX_SOURCE_VIEW_CHARS = MAX_SOURCE_VIEW_BYTES;

    // 分片字符串编解码器的字符数上限
    public static final int MAX_CHUNK_CHARS = MAX_CHUNK_BYTES;

    // 标识字段（sessionId/operationId/requestId）的字符上限：UUID 文本 36 位，取 64 留余量
    public static final int MAX_ID_CHARS = 64;

    // 短码字段（statusCode/messageCode/catalogType/reasonCode/targetId）的字符上限
    public static final int MAX_CODE_CHARS = 128;

    // 回执兜底文案的字符上限
    public static final int MAX_FALLBACK_CHARS = 512;

    // 单次回执携带的消息参数条数上限
    public static final int MAX_MESSAGE_ARGS = 8;

    // 单条消息参数的字符上限
    public static final int MAX_MESSAGE_ARG_CHARS = 256;

    // 单次回执携带的结构化问题条数上限
    public static final int MAX_ISSUES_PER_RESULT = 64;

    // 编解码器允许的超限余量：超限请求必须先抵达服务端再按 INVALID_REQUEST 回绝，不得直接断连
    public static final int PACKET_SLACK_BYTES = 4_096;

    private RuleEditLimits() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 按 UTF-8 字节数计算长度，避免多字节字符绕过长度检查
    public static int encodedLength(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }
}
