package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 大变更集分片重组器：按玩家 + transferId 分组，收齐后返回有序分片，文本拼接由保存后台队列执行。
 * 时钟一律用服务端活动 tick（20 tick = 1 秒），暂停时不推进；同一玩家最多 2 个在途传输。
 */
public final class RuleEditChunkAccumulator {

    // 玩家 UUID -> (transferId -> 传输会话)
    private static final Map<UUID, Map<String, ChunkSession>> SESSIONS = new HashMap<>();

    private RuleEditChunkAccumulator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 已通过形状校验的不可变分片；字节数只能由校验入口计算，避免调用方绕过限额。 */
    public static final class ValidatedChunk {
        private final SaveRuleChangeSetChunkPayload payload;
        private final int encodedBytes;

        private ValidatedChunk(SaveRuleChangeSetChunkPayload payload, int encodedBytes) {
            this.payload = payload;
            this.encodedBytes = encodedBytes;
        }
    }

    /**
     * 分片重组结果：收齐时 complete=true 且 chunks 非空；收齐后超过变更集总量上限时 rejected=true；
     * 其余情况（未收齐、形状非法）两者皆为 false。调用方据此决定"等待"还是回 INVALID_REQUEST。
     */
    public record AcceptResult(boolean complete, boolean rejected, List<String> chunks) {
        /**
         * 固定收齐分片，避免调用方修改后台待拼接的数据。
         * @param complete 是否收齐
         * @param rejected 是否拒绝
         * @param chunks 下标顺序的分片
         */
        public AcceptResult { chunks = List.copyOf(chunks); }
        /**
         * 兼容完整文本调用方的构造入口。
         * @param complete 是否收齐
         * @param rejected 是否拒绝
         * @param json 完整文本
         */
        public AcceptResult(boolean complete, boolean rejected, String json) {
            this(complete, rejected, json.isEmpty() ? List.of() : List.of(json));
        }

        /**
         * 按下标拼接文本；生产保存入口在后台执行拼接。
         * @return 完整变更集文本
         */
        public String json() { return String.join("", chunks); }

        // 未收齐（含形状非法，形状校验由调用方先行判定）
        private static final AcceptResult PENDING = new AcceptResult(false, false, "");
        // 收齐但超过契约总量上限
        private static final AcceptResult REJECTED = new AcceptResult(false, true, "");
    }

    // 接收一片；收齐返回 complete=true 的结果，超量返回 rejected=true
    public static synchronized AcceptResult accept(Player player, SaveRuleChangeSetChunkPayload payload, long tick) {
        return acceptValidated(player, validate(payload), tick);
    }

    /**
     * 接收已校验分片，复用单片字节数累计总量；重复下标只保留并计入首片。
     * @param player 发送玩家
     * @param validated 由 validate 生成的分片；null 表示形状非法
     * @param tick 服务端活动刻
     * @return 重组结果
     */
    public static synchronized AcceptResult acceptValidated(Player player, @Nullable ValidatedChunk validated, long tick) {
        if (player == null || validated == null) {
            return AcceptResult.PENDING;
        }
        SaveRuleChangeSetChunkPayload payload = validated.payload;
        String transferId = payload.transferId();
        int count = payload.count();
        int index = payload.index();
        String chunk = payload.chunk();
        UUID playerId = player.getUUID();
        Map<String, ChunkSession> byTransfer = SESSIONS.computeIfAbsent(playerId, key -> new HashMap<>());
        ChunkSession session = byTransfer.get(transferId);
        if (session == null) {
            if (byTransfer.size() >= RuleEditLimits.MAX_TRANSFERS_PER_PLAYER) {
                // 在途传输过多：丢弃最早的一个，避免单玩家占用过多内存
                Iterator<String> iterator = byTransfer.keySet().iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
            session = new ChunkSession(payload.sessionId(), payload.operationId(), count, tick);
            byTransfer.put(transferId, session);
        } else if (!session.matches(payload.sessionId(), payload.operationId(), count)) {
            // 同一 transferId 换了会话或操作：视为新传输，重置以避免串包
            session = new ChunkSession(payload.sessionId(), payload.operationId(), count, tick);
            byTransfer.put(transferId, session);
        }
        session.touch(tick);
        List<String> complete = session.offer(index, chunk, validated.encodedBytes);
        if (complete == null) {
            return AcceptResult.PENDING;
        }
        byTransfer.remove(transferId);
        if (byTransfer.isEmpty()) {
            SESSIONS.remove(playerId);
        }
        if (session.receivedBytes > RuleEditLimits.MAX_CHANGE_SET_BYTES) {
            return AcceptResult.REJECTED;
        }
        return new AcceptResult(true, false, complete);
    }

    // 分片形状校验：transferId/序号/总数/单片长度任一非法即拒绝
    public static boolean isShapeValid(SaveRuleChangeSetChunkPayload payload) {
        return validate(payload) != null;
    }

    /**
     * 校验分片形状并且只计量一次 UTF-8 字节数，供后续接收直接复用。
     * @param payload 原始分片
     * @return 校验通过的分片，非法时返回 null
     */
    public static @Nullable ValidatedChunk validate(SaveRuleChangeSetChunkPayload payload) {
        if (payload == null) {
            return null;
        }
        String transferId = payload.transferId();
        int count = payload.count();
        int index = payload.index();
        if (transferId == null || transferId.isEmpty()
                || transferId.length() > RuleEditLimits.MAX_TRANSFER_ID_LENGTH
                || count > RuleEditLimits.MAX_CHUNK_COUNT
                || index < 0 || index >= count) {
            return null;
        }
        String chunk = payload.chunk();
        if (chunk == null) { return null; }
        int encodedBytes = RuleEditLimits.encodedLength(chunk);
        return encodedBytes <= RuleEditLimits.MAX_CHUNK_BYTES ? new ValidatedChunk(payload, encodedBytes) : null;
    }

    // 清理超过空闲阈值的分片传输（按活动 tick 计）
    public static synchronized void expireIdle(long tick) {
        Iterator<Map.Entry<UUID, Map<String, ChunkSession>>> players = SESSIONS.entrySet().iterator();
        while (players.hasNext()) {
            Map<String, ChunkSession> byTransfer = players.next().getValue();
            byTransfer.entrySet().removeIf(entry -> tick - entry.getValue().lastTick > RuleEditLimits.TRANSFER_TIMEOUT_TICKS);
            if (byTransfer.isEmpty()) {
                players.remove();
            }
        }
    }

    // 清理某玩家的全部在途传输
    public static synchronized void clear(UUID playerId) {
        if (playerId != null) {
            SESSIONS.remove(playerId);
        }
    }

    // 清空全部在途传输（停服时调用）
    public static synchronized void clearAll() {
        SESSIONS.clear();
    }

    /**
     * 单个分片传输的接收状态。
     */
    private static final class ChunkSession {

        private final String sessionId;
        private final String operationId;
        private final int count;
        private final String[] chunks;
        private int received;
        private int receivedBytes;
        private long lastTick;

        private ChunkSession(String sessionId, String operationId, int count, long tick) {
            this.sessionId = sessionId == null ? "" : sessionId;
            this.operationId = operationId == null ? "" : operationId;
            this.count = count;
            this.chunks = new String[count];
            this.received = 0;
            this.receivedBytes = 0;
            this.lastTick = tick;
        }

        // 判断分片是否属于同一逻辑传输
        private boolean matches(String sessionId, String operationId, int count) {
            return this.count == count
                    && this.sessionId.equals(sessionId == null ? "" : sessionId)
                    && this.operationId.equals(operationId == null ? "" : operationId);
        }

        // 刷新活动时间
        private void touch(long tick) {
            this.lastTick = tick;
        }

        // 写入一片；已存在的下标按首片为准，收齐返回拼接结果，否则返回 null
        private List<String> offer(int index, String chunk, int encodedBytes) {
            if (chunks[index] == null) {
                chunks[index] = chunk;
                received++;
                receivedBytes += encodedBytes;
            }
            if (received < count) {
                return null;
            }
            List<String> ordered = new ArrayList<>(count);
            for (String part : chunks) {
                if (part == null) {
                    return null;
                }
                ordered.add(part);
            }
            return List.copyOf(ordered);
        }
    }
}
