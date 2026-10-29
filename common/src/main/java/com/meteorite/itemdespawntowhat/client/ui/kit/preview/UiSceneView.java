package com.meteorite.itemdespawntowhat.client.ui.kit.preview;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRenderLayers;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

/** 唯一三维取景入口：整圈适框、裁剪、光照与 GUI 状态成对恢复。 */
public final class UiSceneView {
    private UiSceneView() { }

    /** 使用模型空间包围绘制已准备场景，转台只改变外部矩阵。 */
    public static void render(GuiGraphics graphics, UiRect box, PreviewScene scene, UiSceneCamera camera, UiSceneStyle style) {
        if (scene.state != PreviewState.READY || scene.bounds == null || box.width() <= 2 || box.height() <= 2) return;
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        Quaternionf previousCamera = new Quaternionf(dispatcher.cameraOrientation());
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting previousSorting = RenderSystem.getVertexSorting();
        graphics.flush();
        UiTransform.enableScissor(graphics, box);
        graphics.pose().pushPose();
        try {
            var bounds = scene.bounds;
            float scale = bounds.scale(box, camera.pitch());
            graphics.pose().translate(box.x() + box.width() / 2.0F, box.y() + box.height() / 2.0F, UiRenderLayers.ICON);
            graphics.pose().last().pose().scale(1, 1, style.depthScale());
            graphics.pose().scale(scale, -scale, scale);
            Quaternionf orientation = new Quaternionf().rotationX(camera.pitch()).rotateY(camera.yaw());
            graphics.pose().mulPose(orientation);
            graphics.pose().translate(-bounds.centerX(), -bounds.centerY(), -bounds.centerZ());
            Lighting.setupForEntityInInventory();
            dispatcher.overrideCameraOrientation(new Quaternionf(orientation).conjugate());
            RenderSystem.enableDepthTest();
            RenderSystem.setShaderFogStart(Float.MAX_VALUE);
            RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
            Matrix4f matrix = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(graphics.pose().last().pose());
            for (SceneMeshes.Layer layer : scene.layers) if (layer.type != RenderType.translucent())
                layer.draw(matrix, camera);
            RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);
            scene.instances.draw(graphics.pose(), graphics.bufferSource());
            graphics.bufferSource().endBatch();
            RenderSystem.enableDepthTest();
            for (SceneMeshes.Layer layer : scene.layers) if (layer.type == RenderType.translucent())
                layer.draw(matrix, camera);
        } finally {
            try { graphics.flush(); }
            finally {
                graphics.pose().popPose();
                dispatcher.overrideCameraOrientation(previousCamera);
                RenderSystem.setProjectionMatrix(projection, previousSorting);
                RenderSystem.setShaderFogStart(fogStart); RenderSystem.setShaderFogEnd(fogEnd);
                Lighting.setupFor3DItems();
                UiTransform.disableScissor(graphics);
                if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            }
        }
    }
}
