package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** 从触发点接触的流体按 LEVEL 与 FALLING 反推来源；只查询已加载区块，搜索可按 tick 预算续接。 */
final class FluidSourceResolver {
    // 搜索限制按访问量和路径长度约束，允许水平流水以及竖直瀑布回溯。
    private static final int MAX_VISITED = 512;
    private static final int MAX_PATH = 64;
    private static final Direction[] UPSTREAM = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private final ServerLevel level;
    private final Vec3 origin;
    private final ArrayDeque<Node> queue = new ArrayDeque<>();
    private final Set<BlockPos> visited = new HashSet<>();
    private net.minecraft.world.level.material.Fluid contactType;
    private boolean initialized;
    private boolean complete;
    private @Nullable BlockPos result;
    private @Nullable Node resultNode;
    private double bestDistance = Double.POSITIVE_INFINITY;

    FluidSourceResolver(ServerLevel level, Vec3 origin) {
        this.level = level;
        this.origin = origin;
    }

    // 完成搜索返回 true；预算不足返回 false，保留全部游标。
    boolean advance(ServerTickBudget budget) {
        if (complete) return true;
        if (!initialized) {
            if (budget.exhausted()) return false;
            budget.charge(1);
            BlockPos contact = BlockPos.containing(origin);
            if (!LoadedChunks.contains(level, contact) || level.getFluidState(contact).isEmpty()) {
                complete = true;
                return true;
            }
            contactType = level.getFluidState(contact).getType();
            queue.add(new Node(contact, null, 0));
            visited.add(contact);
            initialized = true;
        }
        while (!queue.isEmpty()) {
            if (budget.exhausted()) return false;
            budget.charge(1);
            Node node = queue.removeFirst();
            if (!LoadedChunks.contains(level, node.pos())) continue;
            BlockState block = level.getBlockState(node.pos());
            FluidState fluid = block.getFluidState();
            if (fluid.isEmpty()) continue;
            if (fluid.isSource()) {
                double distance = Vec3.atCenterOf(node.pos()).distanceToSqr(origin);
                if (distance < bestDistance && validPath(node)) {
                    bestDistance = distance;
                    result = node.pos();
                    resultNode = node;
                }
                continue;
            }
            if (node.depth() >= MAX_PATH) continue;
            for (Direction direction : UPSTREAM) {
                BlockPos next = node.pos().relative(direction);
                if (visited.contains(next) || visited.size() >= MAX_VISITED || !LoadedChunks.contains(level, next)) continue;
                BlockState neighbor = level.getBlockState(next);
                if (invalidUpstream(block, neighbor, direction) || blockedFace(node.pos(), block, next, neighbor, direction)) continue;
                visited.add(next);
                queue.addLast(new Node(next, node, node.depth() + 1));
            }
        }
        complete = true;
        return true;
    }


    // 搜索跨 tick 时重读路径，避免流体变化后消耗已脱离接触水流的方块。
    private boolean validPath(Node node) {
        BlockPos contact = BlockPos.containing(origin);
        if (contactType == null || !LoadedChunks.contains(level, contact)
                || !level.getFluidState(contact).getType().isSame(contactType)) return false;
        for (Node current = node; current.parent() != null; current = current.parent()) {
            Node child = current.parent();
            if (!LoadedChunks.contains(level, child.pos()) || !LoadedChunks.contains(level, current.pos())) return false;
            BlockState lower = level.getBlockState(child.pos());
            BlockState upper = level.getBlockState(current.pos());
            Direction direction = Direction.getNearest(current.pos().getX() - child.pos().getX(),
                    current.pos().getY() - child.pos().getY(), current.pos().getZ() - child.pos().getZ());
            if (invalidUpstream(lower, upper, direction) || blockedFace(child.pos(), lower, current.pos(), upper, direction)) return false;
        }
        return true;
    }

    private static boolean invalidUpstream(BlockState current, BlockState neighbor, Direction direction) {
        FluidState from = current.getFluidState();
        FluidState to = neighbor.getFluidState();
        if (from.isEmpty() || to.isEmpty() || !from.getType().isSame(to.getType())) return true;
        boolean falling = from.hasProperty(FlowingFluid.FALLING) && from.getValue(FlowingFluid.FALLING);
        if (direction == Direction.UP) return !falling;
        if (to.isSource()) return false;
        // LiquidBlock.LEVEL 的较小值更接近源；FluidState amount 则恰好相反。
        if (current.hasProperty(LiquidBlock.LEVEL) && neighbor.hasProperty(LiquidBlock.LEVEL)) {
            // 下落流体的 LEVEL 编码含下落标志，水平方向按其实际满液位比较。
            int currentLevel = falling ? 0 : current.getValue(LiquidBlock.LEVEL);
            boolean upstreamFalling = to.hasProperty(FlowingFluid.FALLING) && to.getValue(FlowingFluid.FALLING);
            int upstreamLevel = upstreamFalling ? 0 : neighbor.getValue(LiquidBlock.LEVEL);
            return upstreamLevel >= currentLevel;
        }
        return to.getAmount() <= from.getAmount();
    }

    private boolean blockedFace(BlockPos pos, BlockState state, BlockPos next, BlockState other, Direction direction) {
        return Shapes.mergedFaceOccludes(state.getCollisionShape(level, pos), other.getCollisionShape(level, next), direction);
    }

    // 真正扣除前确认仍为源；含水方块保留本体，其余置为空气。
    boolean consume() {
        if (result == null || resultNode == null || !LoadedChunks.contains(level, result) || !validPath(resultNode)) return false;
        BlockState state = level.getBlockState(result);
        if (!state.getFluidState().isSource()) return false;
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
            return level.setBlock(result, state.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_ALL);
        }
        return level.setBlock(result, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private record Node(BlockPos pos, @Nullable Node parent, int depth) { }
}
