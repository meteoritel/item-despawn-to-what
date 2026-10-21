package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.ChunkPresence;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * 运行时区块门面：把契约层的 {@link ChunkPresence} 提供给运行时与效果执行路径。
 * 查询无副作用，只判定区块是否已加载，世界读写前使用，禁止为执行规则生成区块。
 */
public final class LoadedChunks {
    /**
     * 区块未加载时效果执行与结算交付的重试间隔（tick）。
     * 按原版一秒节奏退避：区块加载通常在数秒内完成，逐 tick 重试只会在加载窗口内空转。
     */
    public static final int RETRY_TICKS = 20;

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
