package com.meteorite.itemdespawntowhat.client.ui.kit.preview;

import java.util.BitSet;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import org.jetbrains.annotations.NotNull;

/** 完整占位与静态提交掩码分开持有，剔除后邻面、AO 和 tint 仍读取原始场景。 */
final class SceneBlocks implements BlockAndTintGetter {
    final PreviewStructure structure;
    private final BlockState[] states;
    private final int sx, sy, sz;
    private final @Nullable Biome fallbackBiome;

    SceneBlocks(PreviewStructure structure, @Nullable Biome fallbackBiome) {
        this.structure = structure;
        sx = structure.size().getX(); sy = structure.size().getY(); sz = structure.size().getZ();
        states = new BlockState[sx * sy * sz];
        this.fallbackBiome = fallbackBiome;
        for (PreviewStructure.Cell cell : structure.blocks())
            states[index(cell.pos().getX(), cell.pos().getY(), cell.pos().getZ())] = structure.palette().get(cell.state());
    }

    private int index(int x, int y, int z) { return (y * sz + z) * sx + x; }

    @Override public @NotNull BlockState getBlockState(@NotNull BlockPos pos) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return Blocks.AIR.defaultBlockState();
        BlockState state = states[index(x, y, z)];
        return state == null ? Blocks.AIR.defaultBlockState() : state;
    }
    @Override public @NotNull FluidState getFluidState(@NotNull BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public @Nullable BlockEntity getBlockEntity(@NotNull BlockPos pos) { return null; }
    @Override public float getShade(@NotNull Direction direction, boolean shade) { return 1; }
    @Override public int getBrightness(@NotNull LightLayer layer, @NotNull BlockPos pos) { return 15; }
    @Override public int getRawBrightness(@NotNull BlockPos pos, int amount) { return 15; }
    @Override public @NotNull LevelLightEngine getLightEngine() {
        throw new UnsupportedOperationException("预览占位直接提供满亮查询");
    }
    @Override public int getHeight() { return sy; }
    @Override public int getMinBuildHeight() { return 0; }
    @Override public int getBlockTint(@NotNull BlockPos pos, @NotNull ColorResolver resolver) {
        if (resolver == BiomeColors.GRASS_COLOR_RESOLVER) return 0x91BD59;
        if (resolver == BiomeColors.FOLIAGE_COLOR_RESOLVER) return 0x77AB2F;
        if (resolver == BiomeColors.WATER_COLOR_RESOLVER) return 0x3F76E4;
        if (fallbackBiome == null) throw new IllegalStateException("自定义颜色解析器需要客户端群系注册表");
        return resolver.getColor(fallbackBiome, pos.getX(), pos.getZ());
    }

    BitSet visible(boolean cull) {
        BitSet visible = new BitSet(states.length);
        if (!cull) { visible.set(0, states.length); return visible; }
        int wx = sx + 2, wy = sy + 2, wz = sz + 2;
        int count = Math.multiplyExact(Math.multiplyExact(wx, wy), wz);
        BitSet visited = new BitSet(count);
        int[] queue = new int[count];
        int read = 0, write = 1;
        visited.set(0);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (read < write) {
            if ((read & 4095) == 0 && Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException();
            int v = queue[read++], x = v % wx, z = v / wx % wz, y = v / wx / wz;
            for (Direction direction : Direction.values()) {
                int nx = x + direction.getStepX(), ny = y + direction.getStepY(), nz = z + direction.getStepZ();
                if (nx < 0 || ny < 0 || nz < 0 || nx >= wx || ny >= wy || nz >= wz) continue;
                pos.set(nx - 1, ny - 1, nz - 1);
                if (nx > 0 && ny > 0 && nz > 0 && nx <= sx && ny <= sy && nz <= sz)
                    visible.set(index(nx - 1, ny - 1, nz - 1));
                int next = (ny * wz + nz) * wx + nx;
                if (!visited.get(next)) {
                    visited.set(next);
                    if (!getBlockState(pos).isSolidRender(this, pos)) queue[write++] = next;
                }
            }
        }
        return visible;
    }

    boolean visible(BitSet mask, BlockPos pos) { return mask.get(index(pos.getX(), pos.getY(), pos.getZ())); }
}
