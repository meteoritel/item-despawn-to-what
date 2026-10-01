package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.ClimateSample;
import com.meteorite.itemdespawntowhat.core.api.ClimateSampler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 气候参数采样的服务端实现：按方块位置缓存采样结果。
 * 仅对多噪声群系源（主世界/下界）有意义；末地等其它群系源返回 null。
 */
public final class RuntimeClimateSampler implements ClimateSampler {

    private final ServerLevel level;
    private final boolean multiNoise;
    private final Map<BlockPos, ClimateSample> cache = new HashMap<>();

    public RuntimeClimateSampler(ServerLevel level) {
        this.level = level;
        this.multiNoise = level.getChunkSource().getGenerator().getBiomeSource() instanceof MultiNoiseBiomeSource;
    }

    @Override
    public @Nullable ClimateSample sample(BlockPos pos) {
        if (!multiNoise) {
            return null;
        }
        BlockPos key = pos.immutable();
        ClimateSample cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        ServerChunkCache chunkCache = level.getChunkSource();
        Climate.TargetPoint point = chunkCache.randomState().sampler().sample(
                QuartPos.fromBlock(key.getX()), QuartPos.fromBlock(key.getY()), QuartPos.fromBlock(key.getZ()));
        ClimateSample sample = new ClimateSample(
                Climate.unquantizeCoord(point.temperature()),
                Climate.unquantizeCoord(point.humidity()),
                Climate.unquantizeCoord(point.continentalness()),
                Climate.unquantizeCoord(point.erosion()),
                Climate.unquantizeCoord(point.depth()),
                Climate.unquantizeCoord(point.weirdness()));
        cache.put(key, sample);
        return sample;
    }
}
