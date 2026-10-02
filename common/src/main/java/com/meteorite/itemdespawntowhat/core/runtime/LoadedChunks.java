package com.meteorite.itemdespawntowhat.core.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** 无副作用的区块存在性查询，世界读写前使用，禁止为执行规则生成区块。 */
public final class LoadedChunks {
    private LoadedChunks() {}

    public static boolean contains(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static boolean containsArea(ServerLevel level, BlockPos pos, int radius) {
        for (int x = (pos.getX() - radius) >> 4; x <= (pos.getX() + radius) >> 4; x++) {
            for (int z = (pos.getZ() - radius) >> 4; z <= (pos.getZ() + radius) >> 4; z++) {
                if (!level.getChunkSource().hasChunk(x, z)) { return false; }
            }
        }
        return true;
    }
}
