package com.meteorite.itemdespawntowhat.client.ui.kit;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/*** 使用真实方块模型和材质，围绕模型中轴旋转；满亮、无环境遮蔽、无方向光。 */
public final class UiBlockPreview {
    private static final float PITCH = (float) Math.toRadians(22);
    // 预览统一按满亮取样，与方块在 GUI 中的展示一致
    private static final int FULL_BRIGHT = 15728880;
    // 烘焙模型取面时的固定种子，保证同一 state 每次取到同一组面
    private static final long MODEL_SEED = 42L;
    private UiBlockPreview() { }

    public static @Nullable UiModelBounds measure(BlockState state) {
        return UiModelBounds.measure((pose, buffers) -> Minecraft.getInstance().getBlockRenderer()
                .renderSingleBlock(state, pose, buffers, FULL_BRIGHT, OverlayTexture.NO_OVERLAY));
    }

    // 实际可视顶点中心和整圈投影参与适框，裁剪只作最终边界保护。
    public static boolean render(GuiGraphics graphics, UiRect box, BlockState state, float angle, UiModelBounds bounds) {
        if (box.width() <= 2 || box.height() <= 2 || !Float.isFinite(angle)) return false;
        graphics.flush();
        UiTransform.enableScissor(graphics, box);
        graphics.pose().pushPose();
        try {
            float scale = bounds.scale(box, PITCH);
            graphics.pose().translate(box.x() + box.width() / 2.0F, box.y() + box.height() / 2.0F, UiRenderLayers.ICON);
            // GUI 层级只压缩顶点深度，法线保留真实模型朝向，避免光照被非等比缩放扭曲。
            graphics.pose().last().pose().scale(1, 1, 0.1F);
            graphics.pose().scale(scale, -scale, scale);
            graphics.pose().mulPose(new Quaternionf().rotationX(PITCH));
            graphics.pose().mulPose(new Quaternionf().rotationY(angle));
            graphics.pose().translate(-bounds.centerX(), -bounds.centerY(), -bounds.centerZ());
            Lighting.setupFor3DItems();
            renderModel(graphics, state);
            graphics.flush();
            return true;
        } catch (RuntimeException exception) { return false; }
        finally {
            try { graphics.flush(); }
            finally {
                graphics.pose().popPose();
                Lighting.setupFor3DItems();
                UiTransform.disableScissor(graphics);
            }
        }
    }

    // 方块表 RenderType 在 1.21.1 是 entity 系 shader，顶点法线会参与 minecraft_mix_light：
    // 交叉平面这类两平面法线互相垂直的模型必然一面亮一面暗。这里改用不带方向光的 solid/cutout 系
    // RenderType，让所有面拿到同一份满亮光强；带方块实体的模型（箱、告示牌等）仍走原版渲染路径。
    private static void renderModel(GuiGraphics graphics, BlockState state) {
        if (state.getRenderShape() != RenderShape.MODEL) {
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, graphics.pose(), graphics.bufferSource(),
                    FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
        int tint = minecraft.getBlockColors().getColor(state, null, null, 0);
        float red = (tint >> 16 & 255) / 255.0F;
        float green = (tint >> 8 & 255) / 255.0F;
        float blue = (tint & 255) / 255.0F;
        VertexConsumer consumer = graphics.bufferSource().getBuffer(flatRenderType(state));
        PoseStack.Pose pose = graphics.pose().last();
        RandomSource random = RandomSource.create(MODEL_SEED);
        for (Direction direction : Direction.values()) {
            random.setSeed(MODEL_SEED);
            putQuads(consumer, pose, model.getQuads(state, direction, random), red, green, blue);
        }
        random.setSeed(MODEL_SEED);
        putQuads(consumer, pose, model.getQuads(state, null, random), red, green, blue);
    }

    private static void putQuads(VertexConsumer consumer, PoseStack.Pose pose, List<BakedQuad> quads,
            float red, float green, float blue) {
        for (BakedQuad quad : quads) {
            if (quad.isTinted()) consumer.putBulkData(pose, quad, red, green, blue, 1.0F, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            else consumer.putBulkData(pose, quad, 1.0F, 1.0F, 1.0F, 1.0F, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
    }

    // 映射到不带方向光的方块渲染类型，保留原版的分层语义（实体、半透明、裁剪）。
    private static RenderType flatRenderType(BlockState state) {
        RenderType chunkType = ItemBlockRenderTypes.getChunkRenderType(state);
        if (chunkType == RenderType.translucent()) return RenderType.translucent();
        if (chunkType == RenderType.cutout()) return RenderType.cutout();
        if (chunkType == RenderType.cutoutMipped()) return RenderType.cutoutMipped();
        return RenderType.solid();
    }
}
