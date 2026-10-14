package com.meteorite.itemdespawntowhat.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * 区块存在性查询契约：只读判定目标区块是否已加载，供世界读写前的门禁使用。
 * 只查不生成，禁止为执行规则加载区块；条件层依赖本类即可，不必反向依赖运行时层。
 */
public final class ChunkPresence {

    private ChunkPresence() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 单个方块所在区块是否已加载。
     *
     * @param level 目标维度
     * @param pos 目标位置
     * @return 所在区块已加载时为 true；不触发区块生成
     */
    public static boolean contains(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    /**
     * 以 pos 为中心、水平半径 radius 覆盖到的所有区块是否都已加载。
     *
     * @param level 目标维度
     * @param pos 中心位置
     * @param radius 水平半径（方块数）
     * @return 覆盖到的区块全部已加载时为 true；不触发区块生成
     */
    public static boolean containsArea(ServerLevel level, BlockPos pos, int radius) {
        for (int x = (pos.getX() - radius) >> 4; x <= (pos.getX() + radius) >> 4; x++) {
            for (int z = (pos.getZ() - radius) >> 4; z <= (pos.getZ() + radius) >> 4; z++) {
                if (!level.getChunkSource().hasChunk(x, z)) { return false; }
            }
        }
        return true;
    }
}
