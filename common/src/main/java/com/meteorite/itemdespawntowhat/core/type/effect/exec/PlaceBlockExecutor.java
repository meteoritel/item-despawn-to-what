package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.type.effect.PlaceBlockEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** 方块效果按游标分批计数和放置；形状偏移按参数复用，未加载区块等待后续处理。 */
public final class PlaceBlockExecutor {
    private static final Map<ShapeKey, List<BlockPos>> OFFSETS = new HashMap<>();
    /** 不含维度引用的形状缓存键。 */
    private record ShapeKey(PlaceBlockEffect.Shape shape, int radius) {}
    private PlaceBlockExecutor() {}

    public static void execute(PlaceBlockEffect effect, EffectContext context) {
        Block block = resolveBlock(effect, context);
        context.schedule(0, new Placement(effect, context, block));
    }

    private static Block resolveBlock(PlaceBlockEffect effect, EffectContext context) {
        if (effect.block() != null) {
            return EffectTargets.resolveOrThrow(effect.block(), BuiltInRegistries.BLOCK, context.random(), PlaceBlockEffect.BLOCK_FIELD);
        }
        Item item = context.sourceStack().getItem();
        Block block = Block.byItem(item);
        if (!effect.useSourceBlock() || block == Blocks.AIR) {
            throw new IllegalStateException("源物品没有可放置的方块: " + BuiltInRegistries.ITEM.getKey(item));
        }
        return block;
    }

    // 每个坐标只生成一次，按由内向外的层序排序；避免每次转化创建各层列表。
    private static List<BlockPos> offsets(PlaceBlockEffect effect) {
        return OFFSETS.computeIfAbsent(new ShapeKey(effect.shape(), effect.radius()), key -> {
            List<BlockPos> result = new ArrayList<>();
            for (int x = -key.radius; x <= key.radius; x++) {
                for (int z = -key.radius; z <= key.radius; z++) {
                    if (key.shape == PlaceBlockEffect.Shape.CROSS && x != 0 && z != 0) { continue; }
                    if (key.shape == PlaceBlockEffect.Shape.CIRCLE && Math.hypot(x, z) >= key.radius + 1) { continue; }
                    result.add(new BlockPos(x, 0, z));
                }
            }
            result.sort(Comparator.comparingInt((BlockPos pos) -> key.shape == PlaceBlockEffect.Shape.CIRCLE
                    ? (int) Math.hypot(pos.getX(), pos.getZ()) : Math.max(Math.abs(pos.getX()), Math.abs(pos.getZ())))
                    .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
            return List.copyOf(result);
        });
    }

    /** 计数与放置的持续游标，最多处理 128 个方块位置后让出本刻。 */
    private static final class Placement implements Runnable {
        private final EffectContext context;
        private final Block block;
        private final BlockState state;
        private final BlockPos center;
        private final List<BlockPos> offsets;
        private final Iterator<BlockPos> countPositions;
        private BlockPos pendingCount;
        private int existing;
        private int cursor;
        private int placed;
        private int target;
        private boolean counting;
        private final int limit;
        private Placement(PlaceBlockEffect effect, EffectContext context, Block block) {
            this.context = context; this.block = block;
            this.state = block.defaultBlockState();
            this.center = BlockPos.containing(context.position());
            this.offsets = offsets(effect);
            this.target = EffectTargets.saturatedMultiply(effect.count(), context.rounds());
            int radius = effect.radius();
            this.countPositions = BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                    center.offset(radius, radius, radius)).iterator();
            this.counting = effect.limit() != null;
            this.limit = effect.limit() == null ? Integer.MAX_VALUE : effect.limit();
        }
        @Override
        public void run() {
            int visits = 0;
            while (counting && visits++ < 128) {
                if (pendingCount == null && countPositions.hasNext()) { pendingCount = countPositions.next().immutable(); }
                if (pendingCount == null || existing >= limit) {
                    target = Math.max(0, Math.min(target, limit - existing));
                    counting = false;
                    break;
                }
                if (!com.meteorite.itemdespawntowhat.core.runtime.LoadedChunks.contains(context.level(), pendingCount)) { context.schedule(20, this); return; }
                if (context.level().getBlockState(pendingCount).is(block)) { existing++; }
                pendingCount = null;
            }
            while (!counting && cursor < offsets.size() && placed < target && visits++ < 128) {
                BlockPos pos = center.offset(offsets.get(cursor));
                // canSurvive 可能读取相邻方块，边界两侧都须已加载。
                if (!com.meteorite.itemdespawntowhat.core.runtime.LoadedChunks.contains(context.level(), pos.offset(-1, 0, -1)) || !com.meteorite.itemdespawntowhat.core.runtime.LoadedChunks.contains(context.level(), pos.offset(1, 0, 1))) {
                    context.schedule(20, this); return;
                }
                cursor++;
                if (context.level().getBlockState(pos).canBeReplaced() && state.canSurvive(context.level(), pos)
                        && context.level().setBlock(pos, state, Block.UPDATE_ALL)) { placed++; }
            }
            if (counting || cursor < offsets.size() && placed < target) { context.schedule(1, this); }
        }
    }
}