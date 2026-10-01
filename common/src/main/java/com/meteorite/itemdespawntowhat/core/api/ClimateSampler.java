package com.meteorite.itemdespawntowhat.core.api;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * 原版气候参数采样入口。
 * 采样源为 ServerChunkCache.randomState().sampler()，实现方需按位置缓存结果。
 */
public interface ClimateSampler {

    // 采样指定位置的 6 个气候参数；该位置不属于多噪声群系源（如末地）时返回 null
    @Nullable
    ClimateSample sample(BlockPos pos);
}
