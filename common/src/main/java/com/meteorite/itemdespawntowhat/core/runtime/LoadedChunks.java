package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.ChunkPresence;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * 运行时区块门面：把契约层的 {@link ChunkPresence} 提供给运行时与效果执行路径。
 * 查询无副作用，只判定区块是否已加载，世界读写前使用，禁止为执行规则生成区块。
 */
public final class LoadedChunks {
    private LoadedChunks() {}

    // 单个方块的所属区块是否已加载（只读查询，不触发区块生成）
    public static boolean contains(ServerLevel level, BlockPos pos) {
        return ChunkPresence.contains(level, pos);
    }

    // 以 pos 为中心、水平半径 radius 覆盖到的所有区块是否都已加载
    public static boolean containsArea(ServerLevel level, BlockPos pos, int radius) {
        return ChunkPresence.containsArea(level, pos, radius);
    }
}
