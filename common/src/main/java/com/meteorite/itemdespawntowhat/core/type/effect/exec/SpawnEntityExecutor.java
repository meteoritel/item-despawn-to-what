package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.debug.DebugMode;
import com.meteorite.itemdespawntowhat.core.debug.DebugScenarioManager;
import com.meteorite.itemdespawntowhat.core.type.effect.SpawnEntityEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.EntityProduct;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/*** 统一实体生成分发与通用实体策略，保留有界安全位置搜索。 */
public final class SpawnEntityExecutor {



    // 单个运行批次最多生成的实体数（沿用阶段 4 的批次大小）
    private static final int SPAWNS_PER_RUN = 8;
    // 安全搜索范围：水平 ±2（5×5），上下各 2 格，共 125 个候选
    private static final int SEARCH_HORIZONTAL = 2;
    private static final int SEARCH_VERTICAL = 2;
    // 候选偏移按「水平距离平方 + 4×垂直距离平方」升序，等距用 y/x/z 稳定排序
    private static final List<BlockPos> SEARCH_OFFSETS = buildSearchOffsets();

    private SpawnEntityExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 顶层只注册一个实体生成效果，专用执行策略按产出子类分发。
    public static EffectResult execute(SpawnEntityEffect effect, EffectContext context) {
        return switch (effect.product()) {
            case EntityProduct.Item item -> SpawnItemExecutor.execute(item, context);
            case EntityProduct.Experience experience -> SpawnXpExecutor.execute(experience, context);
            case EntityProduct.Generic entity -> executeEntity(entity, context);
        };
    }

    private static EffectResult executeEntity(EntityProduct.Generic effect, EffectContext context) {
        EntityType<?> type = EffectTargets.resolveOrThrow(effect.entity(), BuiltInRegistries.ENTITY_TYPE,
                context.random(), "entity");
        if (EntityProduct.Generic.isSpecial(type)) {
            throw new IllegalArgumentException("掉落物和经验球必须使用专用产出子类");
        }
        int count = EffectTargets.saturatedMultiply(effect.count(), context.rounds());
        context.schedule(0, new SpawnBatch(effect, context, type, count));
        return EffectResult.deferred(count, "stepped");
    }

    // 无实体位置判据：世界边界 + 类型自身的地面/水域要求 + 完整生成碰撞箱 + 火/岩浆/仙人掌危险
    private static boolean isSafeSpawnPosition(EntityType<?> type, ServerLevel level, BlockPos pos) {
        if (!level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        // 未登记放置类型的实体回退为「不限制」，仍由碰撞箱与危险方块判据把关
        if (!SpawnPlacements.isSpawnPositionOk(type, level, pos)) {
            return false;
        }
        // 用实体类型的生成碰撞箱检查完整体积，不创建实体（避免触发入世界副作用）
        AABB box = type.getSpawnAABB(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        if (!level.noCollision(box)) {
            return false;
        }
        return isHarmless(level, pos) && isHarmless(level, pos.above());
    }

    // 脚部与头部位置的火、岩浆、仙人掌（含岩浆流体）都视为危险
    private static boolean isHarmless(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.CACTUS)) {
            return false;
        }
        return !level.getFluidState(pos).is(FluidTags.LAVA);
    }

    // 候选偏移的稳定顺序：近处优先，垂直方向权重更高（4×dy²）
    private static List<BlockPos> buildSearchOffsets() {
        List<BlockPos> offsets = new ArrayList<>();
        for (int dx = -SEARCH_HORIZONTAL; dx <= SEARCH_HORIZONTAL; dx++) {
            for (int dz = -SEARCH_HORIZONTAL; dz <= SEARCH_HORIZONTAL; dz++) {
                for (int dy = -SEARCH_VERTICAL; dy <= SEARCH_VERTICAL; dy++) {
                    offsets.add(new BlockPos(dx, dy, dz));
                }
            }
        }
        offsets.sort(Comparator.comparingInt((BlockPos pos) -> pos.getX() * pos.getX() + pos.getZ() * pos.getZ()
                        + 4 * pos.getY() * pos.getY())
                .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        return List.copyOf(offsets);
    }

    /** 分批生成的持续游标：每批最多 8 个实体、16 次安全候选检查，未完成则下刻继续。 */
    private static final class SpawnBatch implements Runnable {
        private final EntityProduct.Generic effect;
        private final EffectContext context;
        private final EntityType<?> type;
        private final ServerLevel level;
        private final Vec3 origin;
        private final BlockPos center;
        private final int count;
        private final boolean safeSpawn;
        private int spawned;
        private int searchCursor;
        private @Nullable BlockPos target;
        private boolean targetReady;
        private SpawnBatch(EntityProduct.Generic effect, EffectContext context, EntityType<?> type, int count) {
            this.effect = effect; this.context = context; this.type = type;
            this.level = context.level();
            this.origin = context.position();
            this.center = BlockPos.containing(origin);
            this.count = count;
            this.safeSpawn = context.safeSpawn();
        }
        @Override
        public void run() {
            // 安全搜索可能读取相邻方块，候选范围比阶段 4 的生成点更宽，需多留一圈区块
            int area = safeSpawn ? SEARCH_HORIZONTAL : 1;
            if (!com.meteorite.itemdespawntowhat.core.runtime.LoadedChunks.containsArea(level, center, area)) {
                context.schedule(20, this); return;
            }
            // 阈值只检查开组准入，本批继续完成已开始组。
            int end = (int) Math.min(count, (long) spawned + SPAWNS_PER_RUN);
            int checks = 0;
            // 每刻候选检查上限来自配置（position_search_checks_per_tick），未完成则下刻从断点继续
            int checkLimit = Math.max(1, context.positionSearchChecksPerTick());
            while (spawned < end) {
                if (safeSpawn && !targetReady) {
                    while (searchCursor < SEARCH_OFFSETS.size() && checks < checkLimit) {
                        BlockPos candidate = center.offset(SEARCH_OFFSETS.get(searchCursor++));
                        checks++;
                        if (isSafeSpawnPosition(type, level, candidate)) { target = candidate; break; }
                    }
                    if (target == null && searchCursor < SEARCH_OFFSETS.size()) {
                        // 搜索未完成：从断点下刻继续，保持「最近优先」语义
                        context.schedule(1, this); return;
                    }
                    targetReady = true;
                    if (DebugMode.ENABLED) {
                        DebugScenarioManager.observe(context.source(), "SPAWN_POS_SEARCH",
                                "entity", BuiltInRegistries.ENTITY_TYPE.getKey(type),
                                "candidates_checked", searchCursor,
                                "hit", target != null,
                                "fallback_origin", target == null,
                                "x", target != null ? target.getX() : origin.x,
                                "y", target != null ? target.getY() : origin.y,
                                "z", target != null ? target.getZ() : origin.z);
                    }
                }
                if (checks >= checkLimit && spawned < end) {
                    context.schedule(1, this); return;
                }
                spawnOne(target);
                spawned++;
                if (safeSpawn) { searchCursor = 0; target = null; targetReady = false; }
            }
            if (spawned < count) { context.schedule(1, this); }
        }

        // 生成一个实体：安全搜索命中时用命中方块中心，未开启搜索或回退时沿用原点附近的随机偏移
        private void spawnOne(@Nullable BlockPos hit) {
            Entity spawned = type.create(level);
            if (spawned == null) {
                throw new IllegalStateException(effect.entity().serialized() + " 在该维度无法创建实体");
            }
            if (hit != null) {
                spawned.moveTo(hit.getX() + 0.5D, hit.getY(), hit.getZ() + 0.5D, 0.0F, 0.0F);
            } else {
                spawned.moveTo(origin.x + (level.random.nextDouble() - 0.5) * 0.5,
                        origin.y,
                        origin.z + (level.random.nextDouble() - 0.5) * 0.5,
                        0.0F, 0.0F);
            }
            if (spawned instanceof AgeableMob ageableMob) {
                ageableMob.setAge(effect.age());
            }
            EffectTargets.addEntity(context, spawned);
            // 真实完成量回执：每个成功写入世界的实体计 1
            context.reportProgress(1);
        }
    }
}
