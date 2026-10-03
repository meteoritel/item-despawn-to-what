package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.debug.DebugMode;
import com.meteorite.itemdespawntowhat.core.debug.DebugScenarioManager;
import com.meteorite.itemdespawntowhat.core.runtime.LoadedChunks;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.state.DropStateStore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 掉落物生成统一入口：转化产物与返还物共用同一套「创建 + 授予实体状态 + 加入世界」路径。
 * 转化产物授予新产物临时保护与转化冷却（D18），返还物授予永久保护与永久禁转；
 * 返还位置搜索按「起点附近 → 向上扫描 → 限高以上」三级推进，全部在公共预算内分步完成，
 * 单刻候选检查数受 position_search_checks_per_tick 限制，未完成则下刻从断点继续，绝不无限扫描。
 */
public final class ReturnItemSpawner {

    // 掉落物来源：决定授予哪种实体状态
    public enum Kind { CONVERSION_PRODUCT, PERMANENT_RETURN }

    // 交付结果：已加入世界 / 本刻预算用尽需下刻继续 / 区块未加载或生成被拒需重试
    public enum Outcome { ADDED, NEEDS_MORE_STEPS, CHUNK_UNLOADED }

    // 起点附近的水平搜索半径与垂直范围（不含向上扫描）
    private static final int NEARBY_RADIUS = 2;
    private static final int NEARBY_VERTICAL = 1;
    // 起点附近候选偏移：近处优先，垂直权重更高，等距用 y/x/z 稳定排序
    private static final List<BlockPos> NEARBY_OFFSETS = buildNearbyOffsets();

    private ReturnItemSpawner() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 统一的实体状态授予：产物与返还物只有这一处分支，避免两处授予语义漂移
    public static void applyGrant(ItemEntity item, Kind kind) {
        if (kind == Kind.PERMANENT_RETURN) {
            // 返还掉落物永久免转（D18：返还物不再参与转化）
            DropStateStore.grantPermanentReturn(item);
        } else {
            DropStateStore.grantNewProduct(item);
        }
    }

    // 创建并加入世界；生成被拒时返回 null，由调用方保留待返还并重试
    public static @Nullable ItemEntity spawn(ServerLevel level, Vec3 position, ItemStack stack, Kind kind) {
        ItemEntity item = new ItemEntity(level, position.x, position.y, position.z, stack);
        item.setDeltaMovement(0.0D, 0.1D, 0.0D);
        applyGrant(item, kind);
        return level.addFreshEntity(item) ? item : null;
    }

    // 交付一份返还物：位置搜索在公共预算内分步推进，区块未加载或生成被拒时交给调用方重试
    public static Outcome deliver(ItemStack stack, PositionSearch search, ServerTickBudget budget, int checksPerStep) {
        if (!LoadedChunks.contains(search.level(), BlockPos.containing(search.origin()))) {
            return Outcome.CHUNK_UNLOADED;
        }
        Vec3 position = search.step(budget, checksPerStep);
        if (position == null) {
            // 本刻候选检查预算用尽：保留待返还，下刻继续
            return Outcome.NEEDS_MORE_STEPS;
        }
        if (spawn(search.level(), position, stack, Kind.PERMANENT_RETURN) == null) {
            return Outcome.CHUNK_UNLOADED;
        }
        search.reset();
        return Outcome.ADDED;
    }

    // 候选位置是否可用：区块已加载、世界边界内、掉落物体积无碰撞、脚部与头部不是火/岩浆/仙人掌
    private static boolean isFree(ServerLevel level, BlockPos pos) {
        if (!LoadedChunks.contains(level, pos)) {
            return false;
        }
        if (!level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        AABB box = EntityType.ITEM.getSpawnAABB(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        if (!level.noCollision(box)) {
            return false;
        }
        return !isDangerous(level, pos) && !isDangerous(level, pos.above());
    }

    // 火、岩浆、仙人掌（含岩浆流体）不算可返还位置：返还物不应落进危险方块
    private static boolean isDangerous(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.CACTUS)) {
            return true;
        }
        return level.getFluidState(pos).is(FluidTags.LAVA);
    }

    // 起点附近候选偏移的稳定顺序：dx²+dz²+4·dy² 升序，再按 y/x/z
    private static List<BlockPos> buildNearbyOffsets() {
        List<BlockPos> offsets = new ArrayList<>();
        for (int dx = -NEARBY_RADIUS; dx <= NEARBY_RADIUS; dx++) {
            for (int dz = -NEARBY_RADIUS; dz <= NEARBY_RADIUS; dz++) {
                for (int dy = -NEARBY_VERTICAL; dy <= NEARBY_VERTICAL; dy++) {
                    offsets.add(new BlockPos(dx, dy, dz));
                }
            }
        }
        offsets.sort(Comparator.comparingInt((BlockPos pos) -> pos.getX() * pos.getX() + pos.getZ() * pos.getZ()
                        + 4 * pos.getY() * pos.getY())
                .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        return List.copyOf(offsets);
    }

    /** 返还位置搜索游标：跨刻保留进度（stage 0 起点附近 → 1 向上扫描 → 2 限高以上）。 */
    public static final class PositionSearch {
        private final ServerLevel level;
        private final ItemEntity source;
        private final Vec3 origin;
        private final BlockPos originBlock;
        private final int maxBuildHeight;
        private int stage;
        private int cursor;
        private int scanY;
        private int checked;
        private @Nullable Vec3 decided;

        public PositionSearch(ServerLevel level, ItemEntity source) {
            this.level = level;
            this.source = source;
            this.origin = source.position();
            this.originBlock = BlockPos.containing(origin);
            this.maxBuildHeight = level.getMaxBuildHeight();
        }

        // 搜索一步；返回已确定的位置，null 表示本刻预算用尽、下刻从断点继续
        public @Nullable Vec3 step(ServerTickBudget budget, int checksPerStep) {
            if (decided != null) {
                return decided;
            }
            int limit = Math.max(1, checksPerStep);
            int checks = 0;
            while (checks < limit) {
                if (budget.exhausted()) {
                    return null;
                }
                if (stage == 0) {
                    if (cursor < NEARBY_OFFSETS.size()) {
                        BlockPos pos = originBlock.offset(NEARBY_OFFSETS.get(cursor++));
                        checks++;
                        checked++;
                        budget.charge(1);
                        if (isFree(level, pos)) {
                            return decide(Vec3.atBottomCenterOf(pos));
                        }
                        continue;
                    }
                    stage = 1;
                    scanY = originBlock.getY() + 1;
                } else if (stage == 1) {
                    if (scanY < maxBuildHeight) {
                        BlockPos pos = new BlockPos(originBlock.getX(), scanY++, originBlock.getZ());
                        checks++;
                        checked++;
                        budget.charge(1);
                        if (isFree(level, pos)) {
                            return decide(Vec3.atBottomCenterOf(pos));
                        }
                        continue;
                    }
                    stage = 2;
                } else {
                    // 满高柱：在限高以上生成，掉落物随后自然落回，绝不破坏方块或无限等候
                    return decide(new Vec3(origin.x, maxBuildHeight, origin.z));
                }
            }
            return null;
        }

        // 交付成功后重置，供下一份返还物从起点附近重新搜索
        public void reset() {
            stage = 0;
            cursor = 0;
            scanY = 0;
            checked = 0;
            decided = null;
        }

        ServerLevel level() {
            return level;
        }

        Vec3 origin() {
            return origin;
        }

        // 决定位置并记录观测：stage 1 表示回退向上扫描，stage 2 表示限高以上
        private Vec3 decide(Vec3 position) {
            decided = position;
            if (DebugMode.ENABLED) {
                DebugScenarioManager.observe(source, "REBATE_POS_SEARCH",
                        "stage", stage,
                        "candidates_checked", checked,
                        "above_limit", stage >= 2,
                        "fallback_upward", stage >= 1,
                        "x", position.x, "y", position.y, "z", position.z);
            }
            return position;
        }
    }
}
