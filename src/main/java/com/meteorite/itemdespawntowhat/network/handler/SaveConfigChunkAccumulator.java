package com.meteorite.itemdespawntowhat.network.handler;

import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.network.payload.c2s.SaveConfigChunkPayload;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// 服务端分包重组器：按玩家和 transferId 暂存配置分片，齐全后拼回完整 JSON。
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

        if (payload.chunkCount() <= 0) {
            LOGGER.warn("[SaveConfigChunkAccumulator] Invalid chunk count {} from player {}",
                    payload.chunkCount(), player.getUUID());
            return null;
        }

        if (payload.chunkIndex() < 0 || payload.chunkIndex() >= payload.chunkCount()) {
            LOGGER.warn("[SaveConfigChunkAccumulator] Invalid chunk index {}/{} for transfer {} from player {}",
                    payload.chunkIndex(), payload.chunkCount(), payload.transferId(), player.getUUID());
            return null;
        }

        UUID playerId = player.getUUID();
        Map<String, ChunkSession> playerSessions = SESSIONS.get(playerId);
        ChunkSession session = playerSessions != null ? playerSessions.get(payload.transferId()) : null;

        if (session == null) {
            if (playerSessions == null) {
                playerSessions = new HashMap<>();
                SESSIONS.put(playerId, playerSessions);
            }
            session = new ChunkSession(payload.configType(), payload.chunkCount());
            playerSessions.put(payload.transferId(), session);
        } else if (!session.matches(payload.configType(), payload.chunkCount())) {
            LOGGER.warn("[SaveConfigChunkAccumulator] Transfer metadata mismatch for player {}, transferId={}",
                    playerId, payload.transferId());
            playerSessions.remove(payload.transferId());
            cleanupPlayerSessions(playerId, playerSessions);
            return null;
        }

        session.addChunk(payload.chunkIndex(), payload.chunkData());
        if (!session.isComplete()) {
            return null;
        }

        String jsonData = session.join();
        playerSessions.remove(payload.transferId());
        cleanupPlayerSessions(playerId, playerSessions);
        return jsonData;
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

    private static final class ChunkSession {
        private final ConfigType configType;
        private final int chunkCount;
        private final String[] chunks;
        private int receivedCount;

        private ChunkSession(ConfigType configType, int chunkCount) {
            this.configType = configType;
            this.chunkCount = chunkCount;
            this.chunks = new String[chunkCount];
        }

        private boolean matches(ConfigType configType, int chunkCount) {
            return this.configType == configType && this.chunkCount == chunkCount;
        }

        private void addChunk(int chunkIndex, String chunkData) {
            if (chunks[chunkIndex] == null) {
                chunks[chunkIndex] = chunkData;
                receivedCount++;
            }
        }

        private boolean isComplete() {
            return receivedCount >= chunkCount;
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
