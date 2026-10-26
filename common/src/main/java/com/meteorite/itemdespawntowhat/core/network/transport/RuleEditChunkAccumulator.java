package com.meteorite.itemdespawntowhat.core.network.transport;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 大变更集分片重组器：按玩家 + transferId 分组，收齐后返回完整文本供保存流程使用。
 * 批次归位与收齐判定复用 {@link RuleChunkAssembly.Batch}，与客户端缓冲同一实现。
 * 时钟一律用服务端活动 tick（20 tick = 1 秒），暂停时不推进；同一玩家最多 2 个在途传输。
 */
public final class RuleEditChunkAccumulator {

    // 玩家 UUID -> (transferId -> 在途批次)；插入序即淘汰序
    private static final Map<UUID, Map<String, RuleChunkAssembly.Batch>> SESSIONS = new LinkedHashMap<>();

    private RuleEditChunkAccumulator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 分片重组结果：收齐时 complete=true 且 json 为完整文本；收齐后超过变更集总量上限时 rejected=true；
     * 其余情况（未收齐、形状非法）两者皆为 false。调用方据此决定等待、回执失败还是继续保存。
     * @param complete 是否收齐
     * @param rejected 是否因总量超限被拒绝
     * @param json 收齐后的完整文本，未收齐时为空串
     */
    public record AcceptResult(boolean complete, boolean rejected, String json) {
        // 未收齐（含形状非法，形状校验由调用方先行判定）
        private static final AcceptResult PENDING = new AcceptResult(false, false, "");
        // 收齐但超过契约总量上限
        private static final AcceptResult REJECTED = new AcceptResult(false, true, "");
    }

    /**
     * 接收已校验分片，复用单片字节数累计总量；重复下标只保留并计入首片。
     * @param player 发送玩家
     * @param validated 由 {@link RuleChunkAssembly#validate} 生成的分片；null 表示形状非法
     * @param tick 服务端活动刻
     * @return 重组结果
     */
    public static synchronized AcceptResult acceptValidated(Player player, @Nullable RuleChunkAssembly.Chunk validated, long tick) {
        if (player == null || validated == null) {
            return AcceptResult.PENDING;
        }
        String transferId = validated.payload().transferId();
        UUID playerId = player.getUUID();
        Map<String, RuleChunkAssembly.Batch> byTransfer = SESSIONS.computeIfAbsent(playerId, key -> new LinkedHashMap<>());
        RuleChunkAssembly.Batch batch = byTransfer.get(transferId);
        if (batch == null) {
            evictOldest(byTransfer);
            batch = RuleChunkAssembly.Batch.start(validated);
            byTransfer.put(transferId, batch);
        } else if (!batch.matches(validated)) {
            // 同一 transferId 换了会话或操作：视为新传输，重置以避免串包
            batch = RuleChunkAssembly.Batch.start(validated);
            byTransfer.put(transferId, batch);
        }
        batch.touch(tick);
        batch.offer(validated);
        if (!batch.complete()) {
            return AcceptResult.PENDING;
        }
        byTransfer.remove(transferId);
        if (byTransfer.isEmpty()) {
            SESSIONS.remove(playerId);
        }
        if (batch.bytes() > RuleEditLimits.MAX_CHANGE_SET_BYTES) {
            return AcceptResult.REJECTED;
        }
        return new AcceptResult(true, false, batch.json());
    }

    // 在途传输过多：按插入顺序丢弃最早的一个，避免单玩家占用过多内存
    private static void evictOldest(Map<String, RuleChunkAssembly.Batch> byTransfer) {
        if (byTransfer.size() < RuleEditLimits.MAX_TRANSFERS_PER_PLAYER) {
            return;
        }
        Iterator<String> iterator = byTransfer.keySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    /**
     * 清理超过空闲阈值的分片传输（按活动 tick 计）。
     * @param tick 服务端活动刻
     */
    public static synchronized void expireIdle(long tick) {
        Iterator<Map.Entry<UUID, Map<String, RuleChunkAssembly.Batch>>> players = SESSIONS.entrySet().iterator();
        while (players.hasNext()) {
            Map<String, RuleChunkAssembly.Batch> byTransfer = players.next().getValue();
            byTransfer.entrySet().removeIf(entry -> tick - entry.getValue().lastTick() > RuleEditLimits.TRANSFER_TIMEOUT_TICKS);
            if (byTransfer.isEmpty()) {
                players.remove();
            }
        }
    }

    /**
     * 清理某玩家的全部在途传输。
     * @param playerId 玩家 UUID
     */
    public static synchronized void clear(@Nullable UUID playerId) {
        if (playerId != null) {
            SESSIONS.remove(playerId);
        }
    }

    // 清空全部在途传输（停服时调用）
    public static synchronized void clearAll() {
        SESSIONS.clear();
    }
}