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

    /**
     * 按宿主给出的可视尺寸绘制实体：全周使用同一包围尺寸，避免旋转时缩放呼吸。
     *
     * @param box 绘制区域，宽或高不足 2 像素时放弃绘制
     * @param entity 宿主已创建的实体
     * @param angle 绕 Y 轴旋转角度
     * @param visualWidth 模型修正后的可视宽度
     * @param visualHeight 模型修正后的可视高度
     * @param centerY 模型修正后的中心 Y 偏移
     * @return 是否完成绘制；尺寸非法或 renderer 抛出运行时异常时返回 false
     */
    public static <T extends Entity> boolean render(GuiGraphics graphics, UiRect box, T entity,
                                                   float angle, float visualWidth, float visualHeight, float centerY) {
        if (box.width() <= 2 || box.height() <= 2 || visualWidth <= 0 || visualHeight <= 0
                || !Float.isFinite(visualWidth) || !Float.isFinite(visualHeight)
                || !Float.isFinite(centerY) || !Float.isFinite(angle)) return false;
        return render(graphics, box, entity, angle, new UiModelBounds(0, centerY, 0, visualWidth, visualHeight, visualWidth));
    }

    /**
     * 测量实体模型的顶点范围：包含头部、翅膀、装饰层和 renderer 的位置修正，宿主可缓存结果。
     *
     * @param entity 目标实体
     * @return 模型包围信息；实体没有 renderer 或测量抛出运行时异常时返回 null
     */
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

    /**
     * 用已测量的包围信息绘制实体，供宿主复用测量结果。
     *
     * @param box 绘制区域，宽或高不足 2 像素时放弃绘制
     * @param entity 宿主已创建的实体
     * @param angle 绕 Y 轴旋转角度
     * @param bounds 由 {@link #measure} 得到的模型包围信息
     * @return 是否完成绘制；实体没有 renderer 或绘制抛出运行时异常时返回 false
     */
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
