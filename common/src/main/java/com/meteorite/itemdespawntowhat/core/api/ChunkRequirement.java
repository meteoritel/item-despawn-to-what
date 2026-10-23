package com.meteorite.itemdespawntowhat.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * 一次推进需要的水平区块范围；不可变，可跨 tick 保留，查询只读且不申请区块加载。
 * 坐标单位是区块，位置与方块半径通过工厂方法转换，避免调用方混淆单位。
 * @param minX 最小区块 X
 * @param minZ 最小区块 Z
 * @param maxX 最大区块 X（含）
 * @param maxZ 最大区块 Z（含）
 */
public record ChunkRequirement(int minX, int minZ, int maxX, int maxZ) {
    /** 校验区块范围，拒绝倒置的范围。 */
    public ChunkRequirement {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("区块范围不得倒置");
        }
    }

    /**
     * 声明一个位置所属的区块。
     * @param position 目标方块位置
     * @return 单区块需求
     */
    public static ChunkRequirement at(BlockPos position) {
        return around(position, 0);
    }

    /**
     * 声明水平正方形覆盖的区块。
     * @param center 中心方块位置
     * @param radiusBlocks 非负水平半径，单位为方块
     * @return 覆盖整个范围的区块需求
     */
    public static ChunkRequirement around(BlockPos center, int radiusBlocks) {
        if (radiusBlocks < 0) {
            throw new IllegalArgumentException("区块需求的方块半径不得为负");
        }
        return new ChunkRequirement((center.getX() - radiusBlocks) >> 4,
                (center.getZ() - radiusBlocks) >> 4,
                (center.getX() + radiusBlocks) >> 4, (center.getZ() + radiusBlocks) >> 4);
    }

    /**
     * 将已固定目标纳入本次推进范围；目标已被覆盖时复用本值。
     * @param position 本次还需访问的方块位置
     * @return 同时覆盖原范围与目标位置的区块需求
     */
    public ChunkRequirement including(BlockPos position) {
        int x = position.getX() >> 4;
        int z = position.getZ() >> 4;
        if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
            return this;
        }
        return new ChunkRequirement(Math.min(minX, x), Math.min(minZ, z),
                Math.max(maxX, x), Math.max(maxZ, z));
    }

    /**
     * 只读检查整个范围，调用方负责等待策略和后续世界操作。
     * @param level 目标维度
     * @return 所需区块全部已加载时为 true
     */
    public boolean isReady(ServerLevel level) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!level.getChunkSource().hasChunk(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }
}
