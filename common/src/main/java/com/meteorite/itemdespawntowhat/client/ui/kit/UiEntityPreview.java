package com.meteorite.itemdespawntowhat.client.ui.kit;

import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jetbrains.annotations.Nullable;

/*** 绘制宿主已创建的实体；不创建、不 tick、不加入世界，也不持有业务缓存。 */
public final class UiEntityPreview {
    private UiEntityPreview() { }

    // 全周使用同一包围尺寸，避免旋转时缩放呼吸；宿主可提供模型修正后的尺寸和中心。
    // 允许第三方 renderer 违反原版非空标注时安全返回失败。
    public static <T extends Entity> boolean render(GuiGraphics graphics, UiRect box, T entity,
                                                   float angle, float visualWidth, float visualHeight, float centerY) {
        if (box.width() <= 2 || box.height() <= 2 || visualWidth <= 0 || visualHeight <= 0
                || !Float.isFinite(visualWidth) || !Float.isFinite(visualHeight)
                || !Float.isFinite(centerY) || !Float.isFinite(angle)) return false;
        return render(graphics, box, entity, angle, new UiModelBounds(0, centerY, 0, visualWidth, visualHeight, visualWidth));
    }

    // 每个样例只测量一次；包含头部、翅膀、装饰层和 renderer 的位置修正。
    @SuppressWarnings("ConstantValue")
    public static <T extends Entity> @Nullable UiModelBounds measure(T entity) {
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        try {
            EntityRenderer<? super T> renderer = dispatcher.getRenderer(entity);
            if (renderer == null) return null;
            return UiModelBounds.measure((pose, buffers) -> {
                Vec3 offset = renderer.getRenderOffset(entity, 0);
                pose.translate(offset.x, offset.y, offset.z);
                renderer.render(entity, 0, 0, pose, buffers, 15728880);
            });
        } catch (RuntimeException exception) { return null; }
    }

    @SuppressWarnings("ConstantValue")
    public static <T extends Entity> boolean render(GuiGraphics graphics, UiRect box, T entity,
                                                   float angle, UiModelBounds bounds) {
        if (box.width() <= 2 || box.height() <= 2 || !Float.isFinite(angle)) return false;
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
            float scale = bounds.scale(box, 0);
            graphics.pose().translate(box.x() + box.width() / 2.0F, box.y() + box.height() / 2.0F, UiRenderLayers.ICON);
            graphics.pose().scale(scale, -scale, scale * 0.1F);
            graphics.pose().mulPose(new Quaternionf().rotationY(angle));
            graphics.pose().translate(-bounds.centerX(), -bounds.centerY(), -bounds.centerZ());
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
