package com.meteorite.itemdespawntowhat.client.ui.kit.preview;

import com.mojang.blaze3d.vertex.VertexConsumer;

import org.jetbrains.annotations.NotNull;

/** 将液体段内顶点平移回局部坐标，并统一满亮；其余属性原样转发。 */
final class SceneVertices implements VertexConsumer {
    private final VertexConsumer target;
    private final int x, y, z;
    private final Runnable beforeVertex;
    SceneVertices(VertexConsumer target, int x, int y, int z) {
        this(target, x, y, z, () -> { });
    }
    SceneVertices(VertexConsumer target, int x, int y, int z, Runnable beforeVertex) {
        this.target = target; this.x = x; this.y = y; this.z = z;
        this.beforeVertex = beforeVertex;
    }
    @Override public @NotNull VertexConsumer addVertex(float vx, float vy, float vz) { beforeVertex.run(); target.addVertex(vx + x, vy + y, vz + z); return this; }
    @Override public @NotNull VertexConsumer setColor(int r, int g, int b, int a) { target.setColor(r, g, b, a); return this; }
    @Override public @NotNull VertexConsumer setUv(float u, float v) { target.setUv(u, v); return this; }
    @Override public @NotNull VertexConsumer setUv1(int u, int v) { target.setUv1(u, v); return this; }
    @Override public @NotNull VertexConsumer setUv2(int u, int v) { target.setUv2(240, 240); return this; }
    @Override public @NotNull VertexConsumer setNormal(float nx, float ny, float nz) { target.setNormal(nx, ny, nz); return this; }
}
