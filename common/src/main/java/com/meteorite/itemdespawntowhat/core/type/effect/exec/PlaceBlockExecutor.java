package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.type.effect.PlaceBlockEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * place_block 执行器：由触发位置向外逐层放置方块，形状与数量由参数决定。
 * 未配置 block 时按 use_source_block 取源物品对应的方块。
 * rounds 语义：产出/消耗按 context.rounds() 缩放，并受既有 limit/radius 与可扣数量收敛。
 * 约定：delay_ticks / chance / 效果级 conditions 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 */
public final class PlaceBlockExecutor {

    private static final Logger LOGGER = LogManager.getLogger();

    private PlaceBlockExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void execute(PlaceBlockEffect effect, EffectContext context) {
        ServerLevel level = context.level();
        Block block = resolveBlock(effect, context);
        // 整堆一次性转化：目标数量 = count × rounds，再按 limit 与半径内可放置空间收敛
        int requested = EffectTargets.saturatedMultiply(effect.count(), context.rounds());
        int target = allowedCount(effect, context, block, requested);
        if (target <= 0) {
            return;
        }
        BlockPos center = centerOf(context);
        BlockState state = block.defaultBlockState();
        int placed = 0;
        // 由内向外逐层放置，每层内按固定遍历顺序取可用位置，避免每次触发重复全量扫描
        for (int radius = 0; radius <= effect.radius() && placed < target; radius++) {
            for (BlockPos pos : layerPositions(center, radius, effect.shape())) {
                if (placed >= target) {
                    break;
                }
                if (!level.getBlockState(pos).canBeReplaced() || !state.canSurvive(level, pos)) {
                    continue;
                }
                level.setBlock(pos, state, Block.UPDATE_ALL);
                placed++;
            }
        }
        if (placed < target) {
            LOGGER.debug("place_block 可放置位置不足：规则={} 期望={} 实际={}", context.ruleId(), target, placed);
        }
    }

    // 解析待放置方块：优先显式 block，其次按 use_source_block 从源物品推导
    private static Block resolveBlock(PlaceBlockEffect effect, EffectContext context) {
        if (effect.block() != null) {
            return EffectTargets.resolveOrThrow(effect.block(), BuiltInRegistries.BLOCK, context.random(),
                    PlaceBlockEffect.BLOCK_FIELD);
        }
        if (!effect.useSourceBlock()) {
            throw new IllegalStateException("place_block 既未配置 block 也未启用 use_source_block");
        }
        Item sourceItem = context.sourceStack().getItem();
        Block derived = Block.byItem(sourceItem);
        if (derived == Blocks.AIR) {
            throw new IllegalStateException("源物品 " + BuiltInRegistries.ITEM.getKey(sourceItem)
                    + " 没有对应的方块，无法按 use_source_block 放置");
        }
        return derived;
    }

    // 结合 limit 与放置半径计算本次实际可放置的方块数量；limit 为空表示不限制
    private static int allowedCount(PlaceBlockEffect effect, EffectContext context, Block block, int requested) {
        if (effect.limit() == null) {
            return requested;
        }
        int existing = countNearby(BlockPos.containing(context.position().x, context.position().y,
                context.position().z), effect.radius(), effect.limit(), context.level(), block);
        return Math.max(0, Math.min(requested, effect.limit() - existing));
    }

    // 统计半径内同类方块数量；达到 limit 立即返回
    private static int countNearby(BlockPos center, int radius, int limit, ServerLevel level, Block block) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (level.getBlockState(pos).getBlock() == block && ++count >= limit) {
                return count;
            }
        }
        return count;
    }

    // 某一层（切比雪夫距离 = radius 的环）上属于指定形状的位置
    private static List<BlockPos> layerPositions(BlockPos center, int radius, PlaceBlockEffect.Shape shape) {
        if (radius == 0) {
            return List.of(center);
        }
        List<BlockPos> positions = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (isOnLayer(shape, dx, dz, radius)) {
                    positions.add(center.offset(dx, 0, dz));
                }
            }
        }
        return positions;
    }

    // 形状判定：SQUARE 为方环、CIRCLE 为圆环、CROSS 为十字的四端
    private static boolean isOnLayer(PlaceBlockEffect.Shape shape, int dx, int dz, int radius) {
        int chebyshev = Math.max(Math.abs(dx), Math.abs(dz));
        return switch (shape) {
            case SQUARE -> chebyshev == radius;
            case CIRCLE -> {
                double euclidean = Math.sqrt((double) dx * dx + (double) dz * dz);
                yield euclidean >= radius && euclidean < radius + 1;
            }
            case CROSS -> chebyshev == radius && (dx == 0 || dz == 0);
        };
    }

    // 触发位置所在的方块坐标
    private static BlockPos centerOf(EffectContext context) {
        return BlockPos.containing(context.position().x, context.position().y, context.position().z);
    }
}
