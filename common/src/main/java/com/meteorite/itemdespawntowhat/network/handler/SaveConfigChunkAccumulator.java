package com.meteorite.itemdespawntowhat.network.handler;

import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.network.ConfigEditLimits;
import com.meteorite.itemdespawntowhat.network.payload.c2s.SaveConfigChunkPayload;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端配置分片的限额校验与重组器。
 */
public final class SaveConfigChunkAccumulator {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<UUID, Map<String, ChunkSession>> SESSIONS = new HashMap<>();

    private SaveConfigChunkAccumulator() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 接收一个配置分片；如果当前 transfer 已收齐，则返回完整 JSON，否则返回 null。
    public static synchronized String acceptChunk(ServerPlayer player, SaveConfigChunkPayload payload) {
        if (player == null || payload == null) {
            return null;
        }

        if (payload.configType() == null
                || payload.chunkCount() <= 0
                || payload.chunkCount() > ConfigEditLimits.MAX_CHUNK_COUNT) {
            LOGGER.warn("[SaveConfigChunkAccumulator] Invalid chunk count {} from player {}",
                    payload.chunkCount(), player.getUUID());
            return null;
        }

        String transferId = payload.transferId();
        if (transferId == null || transferId.isBlank()
                || transferId.length() > ConfigEditLimits.MAX_TRANSFER_ID_LENGTH) {
            LOGGER.warn("[SaveConfigChunkAccumulator] Invalid transfer id from player {}", player.getUUID());
            return null;
        }

        String chunkData = payload.chunkData();
        int chunkBytes = ConfigEditLimits.encodedLength(chunkData);
        if (chunkData == null || chunkBytes > ConfigEditLimits.MAX_CHUNK_BYTES) {
            LOGGER.warn("[SaveConfigChunkAccumulator] Invalid chunk size {} from player {}",
                    chunkBytes, player.getUUID());
            return null;
        }

        if (payload.chunkIndex() < 0 || payload.chunkIndex() >= payload.chunkCount()) {
            LOGGER.warn("[SaveConfigChunkAccumulator] Invalid chunk index {}/{} for transfer {} from player {}",
                    payload.chunkIndex(), payload.chunkCount(), payload.transferId(), player.getUUID());
            return null;
        }

        UUID playerId = player.getUUID();
        Map<String, ChunkSession> playerSessions = SESSIONS.get(playerId);
        if (playerSessions != null) {
            removeExpiredSessions(playerSessions);
            cleanupPlayerSessions(playerId, playerSessions);
            playerSessions = SESSIONS.get(playerId);
        }
        ChunkSession session = playerSessions != null ? playerSessions.get(transferId) : null;

        if (session == null) {
            if (playerSessions == null) {
                playerSessions = new HashMap<>();
                SESSIONS.put(playerId, playerSessions);
            }
            if (playerSessions.size() >= ConfigEditLimits.MAX_SESSIONS_PER_PLAYER) {
                LOGGER.warn("[SaveConfigChunkAccumulator] Too many active transfers from player {}", playerId);
                return null;
            }
            session = new ChunkSession(payload.configType(), payload.chunkCount());
            playerSessions.put(transferId, session);
        } else if (!session.matches(payload.configType(), payload.chunkCount())) {
            LOGGER.warn("[SaveConfigChunkAccumulator] Transfer metadata mismatch for player {}, transferId={}",
                    playerId, transferId);
            playerSessions.remove(transferId);
            cleanupPlayerSessions(playerId, playerSessions);
            return null;
        }

        if (!session.addChunk(payload.chunkIndex(), chunkData, chunkBytes)) {
            LOGGER.warn("[SaveConfigChunkAccumulator] Transfer exceeded size limit for player {}, transferId={}",
                    playerId, transferId);
            playerSessions.remove(transferId);
            cleanupPlayerSessions(playerId, playerSessions);
            return null;
        }
        if (!session.isComplete()) {
            return null;
        }

        String jsonData = session.join();
        playerSessions.remove(transferId);
        cleanupPlayerSessions(playerId, playerSessions);
        return ConfigEditLimits.isConfigSizeValid(jsonData) ? jsonData : null;
    }

    public static synchronized void clear(ServerPlayer player) {
        if (player != null) {
            clear(player.getUUID());
        }
    }

    public static synchronized void clear(UUID playerId) {
        if (playerId == null) {
            return;
        }

        Map<String, ChunkSession> removed = SESSIONS.remove(playerId);
        if (removed != null && !removed.isEmpty()) {
            LOGGER.debug("Cleared pending save chunk sessions for player {}", playerId);
        }
    }

    public static synchronized void clearAll() {
        if (!SESSIONS.isEmpty()) {
            SESSIONS.clear();
        }
    }

    private static void cleanupPlayerSessions(UUID playerId, Map<String, ChunkSession> playerSessions) {
        if (playerSessions != null && playerSessions.isEmpty()) {
            SESSIONS.remove(playerId);
        }
    }

    private static void removeExpiredSessions(Map<String, ChunkSession> playerSessions) {
        long now = System.currentTimeMillis();
        playerSessions.values().removeIf(session -> session.isExpired(now));
    }

    private static final class ChunkSession {
        private final ConfigType configType;
        private final int chunkCount;
        private final String[] chunks;
        private int receivedCount;
        private int receivedBytes;
        private long lastActivityTime;

        private ChunkSession(ConfigType configType, int chunkCount) {
            this.configType = configType;
            this.chunkCount = chunkCount;
            this.chunks = new String[chunkCount];
            this.lastActivityTime = System.currentTimeMillis();
        }

        private boolean matches(ConfigType configType, int chunkCount) {
            return this.configType == configType && this.chunkCount == chunkCount;
        }

        private boolean addChunk(int chunkIndex, String chunkData, int chunkBytes) {
            lastActivityTime = System.currentTimeMillis();
            if (chunks[chunkIndex] == null) {
                if (receivedBytes + chunkBytes > ConfigEditLimits.MAX_CONFIG_BYTES) {
                    return false;
                }
                chunks[chunkIndex] = chunkData;
                receivedCount++;
                receivedBytes += chunkBytes;
            }
            return true;
        }

        private boolean isComplete() {
            return receivedCount >= chunkCount;
        }

        private boolean isExpired(long now) {
            return now - lastActivityTime > ConfigEditLimits.SESSION_TIMEOUT_MS;
        }

        private String join() {
            StringBuilder builder = new StringBuilder();
            for (String chunk : chunks) {
                if (chunk != null) {
                    builder.append(chunk);
                }
            }
            return builder.toString();
        }
    }
}
