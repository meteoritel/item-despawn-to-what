package com.meteorite.itemdespawntowhat.client.ui.kit.internal.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

/** 渲染线程共用的模型工作区；内部深度只参与模型绘制，合成时回到调用方的平面。 */
public final class UiModelSurface {
    private static UiModelSurface shared;
    private final Map<RenderType, RenderType> layers = new IdentityHashMap<>();
    private final ByteBufferBuilder arena = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private final ModelBuffers buffers = new ModelBuffers();
    private TextureTarget target;

    private UiModelSurface() { }

    /** 在当前 pose 与裁剪范围内绘制；同一工作区逐枚复用，不保留图标颜色缓存。 */
    public static void draw(GuiGraphics graphics, UiRect box, Consumer<GuiGraphics> painter) {
        if (box.width() <= 0 || box.height() <= 0) return;
        if (shared == null) shared = new UiModelSurface();
        shared.render(graphics, box, painter);
    }

    /** 屏幕卸载时释放共享 GPU 与顶点缓冲；下一次绘制按需创建。 */
    public static void release() {
        if (shared == null) return;
        if (shared.target != null) shared.target.destroyBuffers();
        shared.buffers.release();
        shared.arena.close();
        shared = null;
    }

    private void render(GuiGraphics graphics, UiRect box, Consumer<GuiGraphics> painter) {
        graphics.flush();
        int[] viewport = new int[4];
        int[] scissor = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
        boolean clipped = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        Matrix4f pose = graphics.pose().last().pose();
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (int x : new int[]{box.x(), box.right()}) for (int y : new int[]{box.y(), box.bottom()}) {
            float screenX = pose.m00() * x + pose.m10() * y + pose.m30();
            float screenY = pose.m01() * x + pose.m11() * y + pose.m31();
            minX = Math.min(minX, screenX); maxX = Math.max(maxX, screenX);
            minY = Math.min(minY, screenY); maxY = Math.max(maxY, screenY);
        }
        int left = Math.max(viewport[0], viewport[0] + (int) Math.floor(minX * guiScale));
        int right = Math.min(viewport[0] + viewport[2], viewport[0] + (int) Math.ceil(maxX * guiScale));
        int bottom = Math.max(viewport[1], viewport[1] + viewport[3] - (int) Math.ceil(maxY * guiScale));
        int top = Math.min(viewport[1] + viewport[3], viewport[1] + viewport[3] - (int) Math.floor(minY * guiScale));
        if (clipped) {
            left = Math.max(left, scissor[0]); right = Math.min(right, scissor[0] + scissor[2]);
            bottom = Math.max(bottom, scissor[1]); top = Math.min(top, scissor[1] + scissor[3]);
        }
        int width = right - left, height = top - bottom;
        if (width <= 0 || height <= 0) return;
        int drawTarget = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readTarget = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        var shader = RenderSystem.getShader();
        int texture = RenderSystem.getShaderTexture(0);
        float[] color = RenderSystem.getShaderColor().clone();
        float[] clearColor = new float[4];
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
        GuiGraphics local = new GuiGraphics(Minecraft.getInstance(), buffers);
        local.pose().last().pose().set(pose).m32(0);
        local.pose().last().normal().set(graphics.pose().last().normal());
        try {
            RenderSystem.disableScissor();
            ensureSize(width, height);
            RenderSystem.depthMask(true);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(false);
            RenderSystem.viewport(0, 0, width, height);
            Matrix4f cropped = new Matrix4f().translation(
                    (viewport[2] - 2F * (left - viewport[0]) - width) / width,
                    (viewport[3] - 2F * (bottom - viewport[1]) - height) / height, 0)
                    .scale((float) viewport[2] / width, (float) viewport[3] / height, 1).mul(projection);
            RenderSystem.setProjectionMatrix(cropped, sorting);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            try { painter.accept(local); }
            finally { local.flush(); }
            restoreTarget(drawTarget, readTarget, viewport, clipped, scissor);
            RenderSystem.setProjectionMatrix(projection, sorting);
            composite((left - viewport[0]) / (float) guiScale,
                    (viewport[1] + viewport[3] - top) / (float) guiScale,
                    width / (float) guiScale, height / (float) guiScale, pose.m32(), width, height, color);
        } finally {
            restoreTarget(drawTarget, readTarget, viewport, clipped, scissor);
            RenderSystem.setProjectionMatrix(projection, sorting);
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.depthMask(depthWrite);
            RenderSystem.depthFunc(depthFunction);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            GlStateManager._clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            GlStateManager._clearDepth(clearDepth);
        }
    }

    private void ensureSize(int width, int height) {
        if (target == null) {
            target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            target.setClearColor(0, 0, 0, 0);
        } else if (target.width < width || target.height < height) {
            target.resize(Math.max(target.width, width), Math.max(target.height, height), Minecraft.ON_OSX);
        }
    }

    private static void restoreTarget(int draw, int read, int[] viewport, boolean clipped, int[] scissor) {
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        if (clipped) RenderSystem.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
        else RenderSystem.disableScissor();
    }

    private void composite(float x, float y, float width, float height, float z, int pixelsX, int pixelsY, float[] color) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        // 透明工作区已累计 premultiplied RGB，合成时保留透明材质和 glint 的实际颜色。
        RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        RenderSystem.setShaderColor(color[0] * color[3], color[1] * color[3], color[2] * color[3], color[3]);
        float u = (float) pixelsX / target.width, v = (float) pixelsY / target.height;
        BufferBuilder quad = RenderSystem.renderThreadTesselator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        quad.addVertex(x, y + height, z).setUv(0, 0);
        quad.addVertex(x + width, y + height, z).setUv(u, 0);
        quad.addVertex(x + width, y, z).setUv(u, v);
        quad.addVertex(x, y, z).setUv(0, v);
        BufferUploader.drawWithShader(quad.buildOrThrow());
    }

    private RenderType layer(RenderType type) {
        return layers.computeIfAbsent(type, source -> new RenderType(
                "ui_model", source.format(), source.mode(), source.bufferSize(), source.affectsCrumbling(), source.sortOnUpload(),
                () -> { source.setupRenderState(); target.bindWrite(false); },
                () -> { source.clearRenderState(); target.bindWrite(false); }) {
            @Override public void draw(@NotNull MeshData mesh) {
                setupRenderState();
                try { BufferUploader.drawWithShader(mesh); }
                finally { clearRenderState(); }
            }
        });
    }

    private final class ModelBuffers extends MultiBufferSource.BufferSource {
        ModelBuffers() {
            super(arena, new LinkedHashMap<>());
            // 与原版固定缓冲顺序一致，尤其让 glint 在基底材质写入深度后提交。
            for (RenderType type : new RenderType[]{Sheets.solidBlockSheet(), Sheets.cutoutBlockSheet(), Sheets.bannerSheet(),
                    Sheets.translucentCullBlockSheet(), Sheets.shieldSheet(), Sheets.bedSheet(), Sheets.shulkerBoxSheet(),
                    Sheets.signSheet(), Sheets.hangingSignSheet(), Sheets.chestSheet(), RenderType.armorEntityGlint(),
                    RenderType.glint(), RenderType.glintTranslucent(), RenderType.entityGlint(), RenderType.entityGlintDirect(),
                    RenderType.waterMask()}) {
                fixedBuffers.put(layer(type), new ByteBufferBuilder(RenderType.TRANSIENT_BUFFER_SIZE));
            }
        }
        @Override public @NotNull VertexConsumer getBuffer(@NotNull RenderType type) { return super.getBuffer(layer(type)); }
        void release() { fixedBuffers.values().forEach(ByteBufferBuilder::close); }
    }
}
