package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.debug.DebugMode;
import com.meteorite.itemdespawntowhat.core.debug.DebugScenarioManager;
import com.meteorite.itemdespawntowhat.core.type.effect.PlaceBlockEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/**
 * 方块效果按游标分批计数和放置；形状偏移按参数复用，未加载区块等待后续处理。
 * 阶段 4 起每放置一个方块通过 context.reportProgress(1) 回执真实完成量。
 * 阶段 5：只替换可替换方块（canBeReplaced），不满足目标状态合法放置的位置只跳过并继续下一个候选，
 * 不为凑数突破自身 limit/radius；候选级 fill_origin 关闭时不尝试触发位置本身。
 */
public final class PlaceBlockExecutor {
    private static final Map<ShapeKey, List<BlockPos>> OFFSETS = new HashMap<>();
    /** 不含维度引用的形状缓存键；起点填充开关会改变候选集合，须参与缓存键。 */
    private record ShapeKey(PlaceBlockEffect.Shape shape, int radius, boolean fillOrigin) {}
    private PlaceBlockExecutor() {}

    public static EffectResult execute(PlaceBlockEffect effect, EffectContext context) {
        Block block = resolveBlock(effect, context);
        List<BlockPos> candidates = offsets(effect, context.fillOrigin());
        context.schedule(0, new Placement(effect, context, block, candidates));
        // 计划量为 count × rounds 的上限，再受候选位置数封顶；limit/radius/可替换性与真实放置数在游标内收敛
        int planned = Math.min(EffectTargets.saturatedMultiply(effect.count(), context.rounds()), candidates.size());
        return EffectResult.deferred(planned, "placed_by_steps");
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
    private static List<BlockPos> offsets(PlaceBlockEffect effect, boolean fillOrigin) {
        return OFFSETS.computeIfAbsent(new ShapeKey(effect.shape(), effect.radius(), fillOrigin), key -> {
            List<BlockPos> result = new ArrayList<>();
            for (int x = -key.radius; x <= key.radius; x++) {
                for (int z = -key.radius; z <= key.radius; z++) {
                    // 起点填充关闭时跳过触发位置本身，其余候选与顺序保持不变
                    if (!key.fillOrigin && x == 0 && z == 0) { continue; }
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
        private boolean reported;
        private Placement(PlaceBlockEffect effect, EffectContext context, Block block, List<BlockPos> candidates) {
            this.context = context; this.block = block;
            this.state = block.defaultBlockState();
            this.center = BlockPos.containing(context.position());
            this.offsets = candidates;
            // 计划量受候选位置数封顶：半径内位置不足时不越过 radius 强行扩张
            this.target = Math.min(EffectTargets.saturatedMultiply(effect.count(), context.rounds()), candidates.size());
            int radius = effect.radius();
            this.countPositions = BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                    center.offset(radius, radius, radius)).iterator();
            this.counting = effect.limit() != null;
            this.limit = effect.limit() == null ? Integer.MAX_VALUE : effect.limit();
        }
        @Override
        // 此处借用 Minecraft 管理的实例，生命周期由游戏负责，不能在此关闭。
        @SuppressWarnings("resource")
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
                        && context.level().setBlock(pos, state, Block.UPDATE_ALL)) {
                    placed++;
                    // 真实完成量回执：每成功放置一个方块计 1
                    context.reportProgress(1);
                }
            }
            if (counting || cursor < offsets.size() && placed < target) { context.schedule(1, this); return; }
            // 放置结束：记录真实成功数、计划上限与候选数，便于验收与排查半径不足
            if (!reported) {
                reported = true;
                if (DebugMode.ENABLED) {
                    DebugScenarioManager.observe(context.source(), "PLACE_BLOCK_RESULT",
                            "candidates", offsets.size(), "placed", placed, "target", target);
                }
            }
        }
    }
}
