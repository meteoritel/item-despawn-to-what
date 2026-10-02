package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 大变更集分片重组器（契约 §3.3）：按玩家 + transferId 分组，收齐后返回完整变更集文本。
 * 时钟一律用服务端活动 tick（20 tick = 1 秒），暂停时不推进；同一玩家最多 2 个在途传输。
 */
public final class RuleEditChunkAccumulator {

    // 玩家 UUID -> (transferId -> 传输会话)
    private static final Map<UUID, Map<String, ChunkSession>> SESSIONS = new HashMap<>();

    private RuleEditChunkAccumulator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /***
     * 分片重组结果：收齐时 complete=true 且 json 非空；收齐后超过变更集总量上限时 rejected=true；
     * 其余情况（未收齐、形状非法）两者皆为 false。调用方据此决定"等待"还是回 INVALID_REQUEST。
     */
    public record AcceptResult(boolean complete, boolean rejected, String json) {

        // 未收齐（含形状非法，形状校验由调用方先行判定）
        private static final AcceptResult PENDING = new AcceptResult(false, false, "");
        // 收齐但超过契约总量上限
        private static final AcceptResult REJECTED = new AcceptResult(false, true, "");
    }

    // 接收一片；收齐返回 complete=true 的结果，超量返回 rejected=true
    public static synchronized AcceptResult accept(Player player, SaveRuleChangeSetChunkPayload payload, long tick) {
        if (player == null || payload == null) {
            return AcceptResult.PENDING;
        }
        if (!isShapeValid(payload)) {
            return AcceptResult.PENDING;
        }
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
        String complete = session.offer(index, chunk);
        if (complete == null) {
            return AcceptResult.PENDING;
        }
        byTransfer.remove(transferId);
        if (byTransfer.isEmpty()) {
            SESSIONS.remove(playerId);
        }
        if (RuleEditLimits.encodedLength(complete) > RuleEditLimits.MAX_CHANGE_SET_BYTES) {
            return AcceptResult.REJECTED;
        }
        return new AcceptResult(true, false, complete);
    }

    // 分片形状校验：transferId/序号/总数/单片长度任一非法即拒绝
    public static boolean isShapeValid(SaveRuleChangeSetChunkPayload payload) {
        if (payload == null) {
            return false;
        }
        String transferId = payload.transferId();
        int count = payload.count();
        int index = payload.index();
        if (transferId == null || transferId.isEmpty()
                || transferId.length() > RuleEditLimits.MAX_TRANSFER_ID_LENGTH
                || count <= 0 || count > RuleEditLimits.MAX_CHUNK_COUNT
                || index < 0 || index >= count) {
            return false;
        }
        String chunk = payload.chunk();
        return chunk != null && RuleEditLimits.encodedLength(chunk) <= RuleEditLimits.MAX_CHUNK_BYTES;
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

    /***
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
        private String offer(int index, String chunk) {
            if (chunks[index] == null) {
                chunks[index] = chunk;
                received++;
                receivedBytes += RuleEditLimits.encodedLength(chunk);
            }
            if (received < count) {
                return null;
            }
            StringBuilder builder = new StringBuilder(Math.max(receivedBytes, 64));
            List<String> ordered = new ArrayList<>(count);
            for (String part : chunks) {
                if (part == null) {
                    return null;
                }
                ordered.add(part);
            }
            for (String part : ordered) {
                builder.append(part);
            }
            return builder.toString();
        }
    }
}
