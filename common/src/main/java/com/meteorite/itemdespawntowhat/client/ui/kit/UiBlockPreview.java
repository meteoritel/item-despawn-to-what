package com.meteorite.itemdespawntowhat.client.ui.kit;

import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/*** 使用真实方块 renderer 和材质，围绕模型中轴旋转；满亮、无环境遮蔽和落地阴影。 */
public final class UiBlockPreview {
    private static final float PITCH = (float) Math.toRadians(22);
    private UiBlockPreview() { }

    public static @Nullable UiModelBounds measure(BlockState state) {
        return UiModelBounds.measure((pose, buffers) -> Minecraft.getInstance().getBlockRenderer()
                .renderSingleBlock(state, pose, buffers, 15728880, OverlayTexture.NO_OVERLAY));
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
            graphics.pose().scale(scale, -scale, scale * 0.1F);
            graphics.pose().mulPose(new Quaternionf().rotationX(PITCH));
            graphics.pose().mulPose(new Quaternionf().rotationY(angle));
            graphics.pose().translate(-bounds.centerX(), -bounds.centerY(), -bounds.centerZ());
            Lighting.setupForFlatItems();
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, graphics.pose(), graphics.bufferSource(),
                    15728880, OverlayTexture.NO_OVERLAY);
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
}
