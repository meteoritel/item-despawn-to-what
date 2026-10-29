package com.meteorite.itemdespawntowhat.client.ui.kit.preview;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiModelBounds;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/** 静态几何的编译、交接与 GPU 生命周期；分段只决定工作顺序，缓冲按 RenderType 合并。 */
@SuppressWarnings("resource") // 构建缓冲由 Prepared 持有，跨线程交接后统一 close。
final class SceneMeshes {
    private SceneMeshes() { }

    static final class TooLarge extends RuntimeException {
        TooLarge() { super("单场景几何超过 8 MiB"); }
    }

    static final class Building implements AutoCloseable {
        final ByteBufferBuilder arena = new ByteBufferBuilder(4096);
        final BufferBuilder vertices = new BufferBuilder(arena, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        final ByteBufferBuilder indices = new ByteBufferBuilder(1024);
        @Nullable MeshData mesh;
        @Nullable MeshData.SortState sorting;
        int vertexCount;
        long bytes;
        @Override public void close() {
            if (mesh != null) { mesh.close(); mesh = null; }
            indices.close(); arena.close();
        }
    }

    static final class Prepared implements AutoCloseable {
        final Map<RenderType, Building> layers = new LinkedHashMap<>();
        @Nullable UiModelBounds bounds;
        long bytes;
        boolean exceeded;
        @Override public void close() { layers.values().forEach(Building::close); layers.clear(); }
    }

    static Prepared compile(SceneBlocks blocks, PreviewScenes.Geometry geometry, boolean cull) {
        Prepared result = new Prepared();
        Function<RenderType, VertexConsumer> buffers = budgetedBuffers(result);
        ModelBlockRenderer.enableCaching();
        try {
            compileBlocks(blocks, geometry, cull, buffers);
            finish(result);
            return result;
        } catch (RuntimeException exception) {
            result.close();
            // 原版 dispatcher 会包装顶点提交异常，预算终态仍按原始触发原因分类。
            if (result.exceeded) throw new TooLarge();
            throw exception;
        }
        finally { ModelBlockRenderer.clearCache(); }
    }

    private static Function<RenderType, VertexConsumer> budgetedBuffers(Prepared result) {
        long[] allocated = {0};
        return type -> {
            Building layer = result.layers.computeIfAbsent(type, ignored -> new Building());
            return new SceneVertices(layer.vertices, 0, 0, 0, () -> {
                int before = layer.vertexCount++;
                long oldIndices = (long) (before / 4) * 6 * VertexFormat.IndexType.least(before).bytes;
                long newIndices = (long) (layer.vertexCount / 4) * 6 * VertexFormat.IndexType.least(layer.vertexCount).bytes;
                allocated[0] += DefaultVertexFormat.BLOCK.getVertexSize() + newIndices - oldIndices;
                if (allocated[0] > PreviewScenes.SCENE_LIMIT) { result.exceeded = true; throw new TooLarge(); }
            });
        };
    }

    private static void compileBlocks(SceneBlocks blocks, PreviewScenes.Geometry geometry, boolean cull,
        Function<RenderType, VertexConsumer> buffers) {
        BitSet mask = blocks.visible(cull);
        List<PreviewStructure.Cell> cells = new ArrayList<>(blocks.structure.blocks());
        cells.sort(Comparator.comparingInt((PreviewStructure.Cell cell) -> cell.pos().getY() >> 4)
                .thenComparingInt(cell -> cell.pos().getZ() >> 4).thenComparingInt(cell -> cell.pos().getX() >> 4));
        PoseStack pose = new PoseStack();
        RandomSource random = RandomSource.create(42);
        BlockPos section = null;
        for (PreviewStructure.Cell cell : cells) {
            BlockPos pos = cell.pos();
            BlockPos nextSection = new BlockPos(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
            if (!nextSection.equals(section)) {
                if (Thread.currentThread().isInterrupted()) throw new CancellationException();
                section = nextSection;
            }
            if (!blocks.visible(mask, pos)) continue;
            var state = blocks.getBlockState(pos);
            if (state.getRenderShape() == RenderShape.MODEL) {
                pose.pushPose();
                try {
                    pose.translate(pos.getX(), pos.getY(), pos.getZ());
                    geometry.block(state, pos, blocks, pose, buffers, random);
                } finally { pose.popPose(); }
            }
            var fluid = state.getFluidState();
            if (!fluid.isEmpty()) geometry.fluid(pos, blocks, type -> new SceneVertices(buffers.apply(type),
                    pos.getX() & ~15, pos.getY() & ~15, pos.getZ() & ~15), state, fluid);
        }
    }

    private static void finish(Prepared result) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (var entry : result.layers.entrySet()) {
            Building layer = entry.getValue();
            layer.mesh = layer.vertices.build();
            if (layer.mesh == null) continue;
            MeshData.DrawState draw = layer.mesh.drawState();
            if (entry.getKey() == RenderType.translucent())
                layer.sorting = layer.mesh.sortQuads(layer.indices, VertexSorting.DISTANCE_TO_ORIGIN);
            layer.bytes = (long) draw.vertexCount() * draw.format().getVertexSize()
                    + (long) draw.indexCount() * draw.indexType().bytes;
            result.bytes += layer.bytes;
            if (result.bytes > PreviewScenes.SCENE_LIMIT) throw new TooLarge();
            ByteBuffer data = layer.mesh.vertexBuffer().order(ByteOrder.nativeOrder());
            for (int i = 0; i < draw.vertexCount(); i++) {
                int offset = i * draw.format().getVertexSize();
                float x = data.getFloat(offset), y = data.getFloat(offset + 4), z = data.getFloat(offset + 8);
                minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
            }
        }
        if (Float.isFinite(minX)) result.bounds = new UiModelBounds((minX + maxX) / 2, (minY + maxY) / 2,
                (minZ + maxZ) / 2, maxX - minX, maxY - minY, maxZ - minZ);
    }

    static final class Layer implements AutoCloseable {
        final RenderType type;
        final VertexBuffer gpu = new VertexBuffer(VertexBuffer.Usage.STATIC);
        final @Nullable MeshData.SortState sorting;
        private int sortKey = Integer.MIN_VALUE;

        Layer(RenderType type, Building source) {
            this.type = type; sorting = source.sorting;
            gpu.bind();
            try {
                MeshData mesh = source.mesh;
                source.mesh = null;
                if (mesh != null) gpu.upload(mesh);
            } catch (RuntimeException exception) { gpu.close(); throw exception; }
            finally { VertexBuffer.unbind(); }
        }

        void draw(Matrix4f matrix, UiSceneCamera camera) {
            type.setupRenderState();
            try {
                Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
                gpu.bind();
                int key = Math.floorMod((int) Math.floor(Math.toDegrees(camera.yaw()) / 15), 24)
                        + 24 * Math.round((float) Math.toDegrees(camera.pitch()));
                if (sorting != null && key != sortKey) {
                    try (ByteBufferBuilder indices = new ByteBufferBuilder(Math.max(1024, sorting.centroids().length * 24))) {
                        var result = sorting.buildSortedIndexBuffer(indices, viewSorting(camera));
                        if (result != null) gpu.uploadIndexBuffer(result);
                    }
                    sortKey = key;
                }
                var shader = RenderSystem.getShader();
                if (shader == null) throw new IllegalStateException("渲染层未绑定 shader: " + type);
                var offset = shader.getUniform("ChunkOffset");
                if (offset != null) offset.set(0F, 0F, 0F);
                gpu.drawWithShader(matrix, RenderSystem.getProjectionMatrix(), shader);
            } finally { VertexBuffer.unbind(); type.clearRenderState(); }
        }
        @Override public void close() { gpu.close(); }
    }

    static VertexSorting viewSorting(UiSceneCamera camera) {
        float sy = (float) Math.sin(camera.yaw()), cy = (float) Math.cos(camera.yaw());
        float sp = (float) Math.sin(camera.pitch()), cp = (float) Math.cos(camera.pitch());
        // GUI 中更小的 z 更远，按预览变换后的深度从远到近提交。
        return VertexSorting.byDistance(v -> sy * cp * v.x - sp * v.y - cy * cp * v.z);
    }
}
