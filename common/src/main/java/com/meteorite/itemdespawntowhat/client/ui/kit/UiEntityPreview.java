package com.meteorite.itemdespawntowhat.client.ui.kit;

import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/*** 绘制宿主已创建的实体；不创建、不 tick、不加入世界，也不持有业务缓存。 */
public final class UiEntityPreview {
    private UiEntityPreview() { }

    // 全周使用同一包围尺寸，避免旋转时缩放呼吸；宿主可提供模型修正后的尺寸和中心。
    // 允许第三方 renderer 违反原版非空标注时安全返回失败。
    @SuppressWarnings("ConstantValue")
    public static <T extends Entity> boolean render(GuiGraphics graphics, UiRect box, T entity,
                                                   float angle, float visualWidth, float visualHeight, float centerY) {
        if (box.width() <= 2 || box.height() <= 2 || visualWidth <= 0 || visualHeight <= 0
                || !Float.isFinite(visualWidth) || !Float.isFinite(visualHeight)
                || !Float.isFinite(centerY) || !Float.isFinite(angle)) return false;
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderer<? super T> renderer;
        try { renderer = dispatcher.getRenderer(entity); }
        catch (RuntimeException exception) { return false; }
        if (renderer == null) return false;
        Quaternionf camera = new Quaternionf(dispatcher.cameraOrientation());
        graphics.flush();
        UiTransform.enableScissor(graphics, box);
        graphics.pose().pushPose();
        try {
            float scale = Math.min((box.width() - 2) / (visualWidth * 1.42F), (box.height() - 2) / visualHeight) * 0.86F;
            graphics.pose().translate(box.x() + box.width() / 2.0F, box.y() + box.height() / 2.0F, 150);
            graphics.pose().scale(scale, -scale, scale);
            graphics.pose().mulPose(new Quaternionf().rotationY(angle));
            graphics.pose().translate(0, -centerY, 0);
            Vec3 offset = renderer.getRenderOffset(entity, 0);
            graphics.pose().translate(offset.x, offset.y, offset.z);
            dispatcher.overrideCameraOrientation(new Quaternionf().rotationY(-angle));
            Lighting.setupForEntityInInventory();
            // 直接调用 renderer，避免改动 dispatcher 的阴影/命中框全局开关。
            renderer.render(entity, 0, 0, graphics.pose(), graphics.bufferSource(), 15728880);
            graphics.flush();
            return true;
        } catch (RuntimeException exception) {
            return false;
        } finally {
            try { graphics.flush(); }
            finally {
                graphics.pose().popPose();
                dispatcher.overrideCameraOrientation(camera);
                Lighting.setupFor3DItems();
                UiTransform.disableScissor(graphics);
            }
        }
    }
}
