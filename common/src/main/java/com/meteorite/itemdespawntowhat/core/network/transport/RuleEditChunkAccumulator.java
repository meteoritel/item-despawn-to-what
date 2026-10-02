package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端变更集分片重组器（新链路独立实现，不引用旧 network 包）。
 * 限额与超时口径见 {@link RuleEditLimits}：单片 ≤ 30 KB、总量 ≤ 4 MiB、
 * 单次传输空闲 60s 过期、每玩家最多 2 个并发传输；过期与去重都在接收时顺带清理。
 */
public final class RuleEditChunkAccumulator {

    private static final Logger LOGGER = LogManager.getLogger();
    // 玩家 → (transferId → 分片会话)
    private static final Map<UUID, Map<String, ChunkSession>> SESSIONS = new HashMap<>();

    private RuleEditChunkAccumulator() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 接收一个分片；收齐时返回完整 JSON 文本，未收齐或非法时返回 null
    public static synchronized @Nullable String accept(Player player, SaveRuleChangeSetChunkPayload payload) {
        if (player == null || payload == null) {
            return null;
        }
        if (payload.chunkCount() <= 0 || payload.chunkCount() > RuleEditLimits.MAX_CHUNK_COUNT) {
            LOGGER.warn("拒绝分片：片数 {} 非法（玩家 {}）", payload.chunkCount(), player.getUUID());
            return null;
        }
        String transferId = payload.transferId();
        if (transferId == null || transferId.isBlank()
                || transferId.length() > RuleEditLimits.MAX_TRANSFER_ID_LENGTH) {
            LOGGER.warn("拒绝分片：transferId 非法（玩家 {}）", player.getUUID());
            return null;
        }
        String chunkData = payload.chunkData();
        if (chunkData == null) {
            return null;
        }
        int chunkBytes = RuleEditLimits.encodedLength(chunkData);
        if (chunkBytes > RuleEditLimits.MAX_CHUNK_BYTES) {
            LOGGER.warn("拒绝分片：单片 {} 字节超过上限（玩家 {}）", chunkBytes, player.getUUID());
            return null;
        }
        if (payload.chunkIndex() < 0 || payload.chunkIndex() >= payload.chunkCount()) {
            LOGGER.warn("拒绝分片：下标 {}/{} 非法（玩家 {}）",
                    payload.chunkIndex(), payload.chunkCount(), player.getUUID());
            return null;
        }

        long now = System.currentTimeMillis();
        UUID playerId = player.getUUID();
        Map<String, ChunkSession> sessions = SESSIONS.get(playerId);
        if (sessions != null) {
            // 过期会话在接收路径上顺带清理，避免依赖额外的定时任务
            sessions.values().removeIf(session -> session.isExpired(now));
        }

        ChunkSession session = sessions == null ? null : sessions.get(transferId);
        if (session == null) {
            if (sessions == null) {
                sessions = new HashMap<>();
                SESSIONS.put(playerId, sessions);
            }
            if (sessions.size() >= RuleEditLimits.MAX_TRANSFERS_PER_PLAYER) {
                LOGGER.warn("拒绝分片：玩家 {} 并发传输已达上限", playerId);
                return null;
            }
            session = new ChunkSession(payload.chunkCount());
            sessions.put(transferId, session);
        } else if (session.chunkCount != payload.chunkCount()) {
            LOGGER.warn("拒绝分片：transferId {} 的片数不一致（玩家 {}）", transferId, playerId);
            sessions.remove(transferId);
            cleanup(playerId, sessions);
            return null;
        }

        if (!session.addChunk(payload.chunkIndex(), chunkData, chunkBytes)) {
            LOGGER.warn("拒绝分片：transferId {} 累计超过大小上限（玩家 {}）", transferId, playerId);
            sessions.remove(transferId);
            cleanup(playerId, sessions);
            return null;
        }
        if (!session.isComplete()) {
            return null;
        }

        String joined = session.join();
        sessions.remove(transferId);
        cleanup(playerId, sessions);
        if (RuleEditLimits.encodedLength(joined) > RuleEditLimits.MAX_CHANGE_SET_BYTES) {
            LOGGER.warn("拒绝分片：重组后超过变更集大小上限（玩家 {}）", playerId);
            return null;
        }
        return joined;
    }

    // 定期释放空闲分片，避免只发送部分数据的在线玩家长期持有缓存。
    public static synchronized void expireIdle(long now) {
        SESSIONS.values().forEach(sessions -> sessions.values().removeIf(session -> session.isExpired(now)));
        SESSIONS.values().removeIf(Map::isEmpty);
    }

    // 清理某个玩家的全部未完成传输（玩家断开时调用）
    public static synchronized void clear(UUID playerId) {
        if (playerId != null) {
            SESSIONS.remove(playerId);
        }
    }

    // 清理全部未完成传输（服务端停止时调用）
    public static synchronized void clearAll() {
        SESSIONS.clear();
    }

    // 会话清空后移除玩家条目，避免空 Map 常驻
    private static void cleanup(UUID playerId, Map<String, ChunkSession> sessions) {
        if (sessions.isEmpty()) {
            SESSIONS.remove(playerId);
        }
    }

    // 单次分片传输的接收状态
    private static final class ChunkSession {

        private final int chunkCount;
        private final String[] chunks;
        private int receivedCount;
        private int receivedBytes;
        private long lastActivityTime;

        private ChunkSession(int chunkCount) {
            this.chunkCount = chunkCount;
            this.chunks = new String[chunkCount];
            this.lastActivityTime = System.currentTimeMillis();
        }

        // 记录一片；重复下标按首片为准，累计超限返回 false
        private boolean addChunk(int index, String data, int bytes) {
            lastActivityTime = System.currentTimeMillis();
            if (chunks[index] == null) {
                if (receivedBytes + bytes > RuleEditLimits.MAX_CHANGE_SET_BYTES) {
                    return false;
                }
                chunks[index] = data;
                receivedCount++;
                receivedBytes += bytes;
            }
            return true;
        }

        private boolean isComplete() {
            return receivedCount >= chunkCount;
        }

        private boolean isExpired(long now) {
            return now - lastActivityTime > RuleEditLimits.TRANSFER_TIMEOUT_MILLIS;
        }

        private String join() {
            StringBuilder builder = new StringBuilder(receivedBytes);
            for (String chunk : chunks) {
                if (chunk != null) {
                    builder.append(chunk);
                }
            }
            return builder.toString();
        }
    }
}
