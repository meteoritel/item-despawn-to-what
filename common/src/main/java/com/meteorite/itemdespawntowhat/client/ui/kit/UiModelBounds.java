package com.meteorite.itemdespawntowhat.client.ui.kit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/*** 以 renderer 实际提交的顶点测量模型，不使用实体碰撞框或方块形状推断可视范围。 */
public record UiModelBounds(float centerX, float centerY, float centerZ,
                            float width, float height, float depth) {
    // 绕竖轴整圈共用一个包围直径；俯仰时同时为顶部和底部留出投影空间。
    public float scale(UiRect box, float pitch) {
        float diameter = (float) Math.hypot(width, depth);
        float projectedHeight = height * Math.abs((float) Math.cos(pitch))
                + diameter * Math.abs((float) Math.sin(pitch));
        return Math.min(Math.max(0, box.width() - 2) / Math.max(0.01F, diameter),
                Math.max(0, box.height() - 2) / Math.max(0.01F, projectedHeight)) * 0.90F;
    }

    /*** 测量回调只生成顶点，不提交真实绘制；宿主负责缓存测量结果。 */
    @FunctionalInterface
    public interface Renderer {
        void render(PoseStack pose, MultiBufferSource buffers);
    }

    public static @Nullable UiModelBounds measure(Renderer renderer) {
        BoundsConsumer vertices = new BoundsConsumer();
        try {
            renderer.render(new PoseStack(), type -> vertices);
            if (!vertices.present) return null;
            return new UiModelBounds((vertices.minX + vertices.maxX) / 2,
                    (vertices.minY + vertices.maxY) / 2, (vertices.minZ + vertices.maxZ) / 2,
                    Math.max(0.01F, vertices.maxX - vertices.minX), Math.max(0.01F, vertices.maxY - vertices.minY),
                    Math.max(0.01F, vertices.maxZ - vertices.minZ));
        } catch (RuntimeException exception) { return null; }
    }

    /*** 只累计空间范围，颜色、贴图、法线和光照不影响几何测量。 */
    private static final class BoundsConsumer implements VertexConsumer {
        private float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        private boolean present;

        @Override public @NotNull VertexConsumer addVertex(float x, float y, float z) {
            if (Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)) {
                minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
                present = true;
            }
            return this;
        }
        @Override public @NotNull VertexConsumer setColor(int red, int green, int blue, int alpha) { return this; }
        @Override public @NotNull VertexConsumer setUv(float u, float v) { return this; }
        @Override public @NotNull VertexConsumer setUv1(int u, int v) { return this; }
        @Override public @NotNull VertexConsumer setUv2(int u, int v) { return this; }
        @Override public @NotNull VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
