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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 气候参数采样的服务端实现：按 quart 坐标缓存采样结果，最多保留 4096 项。
 * 仅对多噪声群系源（主世界/下界）有意义；末地等其它群系源返回 null。
 */
public final class RuntimeClimateSampler implements ClimateSampler {

    private final ServerLevel level;
    private final boolean multiNoise;
    private final Map<BlockPos, ClimateSample> cache = new LinkedHashMap<>(256, 0.75f, true);

    public RuntimeClimateSampler(ServerLevel level) {
        this.level = level;
        this.multiNoise = level.getChunkSource().getGenerator().getBiomeSource() instanceof MultiNoiseBiomeSource;
    }

    @Override
    public @Nullable ClimateSample sample(BlockPos pos) {
        if (!multiNoise) {
            return null;
        }
        BlockPos key = new BlockPos(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()));
        ClimateSample cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        ServerChunkCache chunkCache = level.getChunkSource();
        Climate.TargetPoint point = chunkCache.randomState().sampler().sample(
                key.getX(), key.getY(), key.getZ());
        ClimateSample sample = new ClimateSample(
                Climate.unquantizeCoord(point.temperature()),
                Climate.unquantizeCoord(point.humidity()),
                Climate.unquantizeCoord(point.continentalness()),
                Climate.unquantizeCoord(point.erosion()),
                Climate.unquantizeCoord(point.depth()),
                Climate.unquantizeCoord(point.weirdness()));
        cache.put(key, sample);
        if (cache.size() > 4096) {
            cache.remove(cache.keySet().iterator().next());
        }
        return sample;
    }
}
