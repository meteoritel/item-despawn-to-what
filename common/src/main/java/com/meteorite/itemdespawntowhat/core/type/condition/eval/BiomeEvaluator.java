package com.meteorite.itemdespawntowhat.core.type.condition.eval;

import com.meteorite.itemdespawntowhat.core.api.ClimateSample;
import com.meteorite.itemdespawntowhat.core.api.ConditionContext;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.type.condition.BiomeCondition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import java.util.Optional;

/**
 * biome 条件的求值器：exact 模式按群系注册名/标签匹配，climate 模式按 6 个气候参数区间匹配。
 * 纯谓词：只读取上下文，不修改世界；不处理取反（取反由条件树的 inverted 节点承担）。
 * 标签判定走 TagLookup（带缓存），气候采样走 ClimateSampler（按位置缓存）。
 */
public final class BiomeEvaluator {

    private BiomeEvaluator() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 按 mode 分派；mode 为空属非法配置（加载期已拒载），这里 fail-closed
    public static boolean test(BiomeCondition condition, ConditionContext context) {
        if (condition.mode() == null) {
            return false;
        }
        return switch (condition.mode()) {
            case EXACT -> testExact(condition, context);
            case CLIMATE -> testClimate(condition, context);
        };
    }

    // exact：取当前位置的群系 id，逐个引用比对，命中任意一项即成立
    private static boolean testExact(BiomeCondition condition, ConditionContext context) {
        Optional<ResourceKey<Biome>> key = context.level().getBiome(context.pos()).unwrapKey();
        if (key.isEmpty()) {
            return false;
        }
        ResourceLocation biomeId = key.get().location();
        for (TaggedId reference : condition.biomes()) {
            boolean matched = reference.tag()
                    ? context.tags().biomeInTag(reference.id(), biomeId)
                    : reference.id().equals(biomeId);
            if (matched) {
                return true;
            }
        }
        return false;
    }

    // climate：采样 6 参数后逐区间比对，已填写的区间必须全部满足；
    // 采样为 null（末地等非多噪声群系源）时视为条件不成立
    private static boolean testClimate(BiomeCondition condition, ConditionContext context) {
        ClimateSample sample = context.climate().sample(context.pos());
        if (sample == null) {
            return false;
        }
        boolean constrained = false;
        if (condition.temperature() != null) {
            constrained = true;
            if (!within(sample.temperature(), condition.temperature())) {
                return false;
            }
        }
        if (condition.humidity() != null) {
            constrained = true;
            if (!within(sample.humidity(), condition.humidity())) {
                return false;
            }
        }
        if (condition.continentalness() != null) {
            constrained = true;
            if (!within(sample.continentalness(), condition.continentalness())) {
                return false;
            }
        }
        if (condition.erosion() != null) {
            constrained = true;
            if (!within(sample.erosion(), condition.erosion())) {
                return false;
            }
        }
        if (condition.depth() != null) {
            constrained = true;
            if (!within(sample.depth(), condition.depth())) {
                return false;
            }
        }
        if (condition.weirdness() != null) {
            constrained = true;
            if (!within(sample.weirdness(), condition.weirdness())) {
                return false;
            }
        }
        // 一个区间都没填属非法配置（加载期已拒载），fail-closed 而不是恒真
        return constrained;
    }

    // 单个气候参数区间判定：两端可空表示该端不限制
    private static boolean within(double value, BiomeCondition.ClimateRange range) {
        if (range.min() != null && value < range.min()) {
            return false;
        }
        return range.max() == null || value <= range.max();
    }
}
