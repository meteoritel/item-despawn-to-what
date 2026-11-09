package com.meteorite.itemdespawntowhat.client.ui.kit.api.control;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiRenderLayers;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 图片内容视图：{@link UiLightbox.Content} 的图片实现——contain 适配、有限步进缩放、指针锚点缩放、
 * 平移钳制、严格裁剪绘制与缺图占位。
 *
 * <p>本类**只做一张图**：尺寸与缩放/平移状态；图集、标题、控制按钮与关闭语义都在灯箱外壳里。
 * 因此同一张图既能被图集逐个展示，也能被任意宿主单独放进灯箱。</p>
 *
 * <p>坐标与口径：视口由外壳写入（宿主 GUI 逻辑坐标），{@code offsetX/offsetY} 是**图片中心相对视口中心
 * 的屏幕像素偏移**，{@code scale} 是绘制缩放。任一轴的目标尺寸不超过视口时该轴偏移被锁死为 0（居中），
 * 超过时偏移钳制在 ±(目标尺寸 − 视口尺寸)/2 内，因此图片永远不会被拖出视口留下空白。</p>
 *
 * <p>本类不逐帧解码、不创建纹理：贴图与区域尺寸都由 {@link LightboxImage} 给出，资源可用性只在构造、
 * 内容变更与 {@link #invalidateResources()} 时各判定一次。</p>
 */
public final class UiImageView implements UiLightbox.Content {
    /**
     * 缺图重查间隔（帧）：20 帧，60 FPS 下约 0.33 秒。
     */
    private static final int RECHECK_INTERVAL = 20;

    private final LightboxImage image;
    @Nullable
    private final Component missingText;
    private final LightboxViewport view;
    /**
     * 小图默认不主动放大；true 时 fit 允许放大到视口。
     */
    private final boolean allowUpscale;
    private boolean available;
    private @Nullable UiInteractionSession session;
    /**
     * 缺图时的重查计时：约每 20 帧重查一次，避免逐帧 IO，又能在资源补齐后自愈。
     */
    private int recheckTicks;

    public UiImageView(LightboxImage image, @Nullable Component missingText) {
        this(image, missingText, false);
    }

    /**
     * 便捷重载：不显示占位文案。
     */
    public UiImageView(LightboxImage image) {
        this(image, null, false);
    }

    /**
     * @param allowUpscale 是否允许 fit 放大到视口（默认 false：小图保持原始像素大小）
     */
    public UiImageView(LightboxImage image, @Nullable Component missingText, boolean allowUpscale) {
        this.image = Objects.requireNonNull(image);
        view = new LightboxViewport(image.width(), image.height());
        this.missingText = missingText;
        this.allowUpscale = allowUpscale;
        this.available = resolveAvailability();
    }

    // ---------- 内容契约 ----------

    @Override
    public int contentWidth() {
        return image.width();
    }

    @Override
    public int contentHeight() {
        return image.height();
    }

    @Override
    public void setViewport(UiRect viewport) {
        view.setViewport(Objects.requireNonNull(viewport));
    }

    /**
     * contain 适配：按视口与图片尺寸取较小比例，小图默认不放大（scale 上限 1.0），最后钳制在
     * 既有缩放范围；偏移归零表示居中。
     */
    @Override
    public void fit() {
        double maximum = allowUpscale ? LightboxViewport.MAX_SCALE : 1.0;
        view.fit(Math.min(maximum, view.containScale()), LightboxViewport.MIN_SCALE, LightboxViewport.MAX_SCALE);
    }

    /**
     * 指针锚点缩放：缩放前后指针下的同一内容点保持不动；到顶/到底或结果不变时返回 false。
     */
    @Override
    public boolean zoom(double amount, double anchorX, double anchorY) {
        return view.zoom(amount, anchorX, anchorY);
    }

    @Override
    public boolean zoomBy(int direction) { return view.zoomBy(direction); }

    @Override
    public boolean panBy(double screenDx, double screenDy) {
        return session != null && session.owns(this) && view.panBy(screenDx, screenDy);
    }

    @Override
    public boolean mousePressed(UiInputContext context) {
        int button = context.button();
        if (button != 0 || !view.viewport().contains(context.pointerX(), context.pointerY())) return false;

        session = context.scope().interaction();
        session.begin(this, UiInteractionSession.Source.POINTER, button);
        return true;
    }

    @Override
    public boolean mouseReleased(UiInputContext context) {
        int button = context.button();
        if (button != 0) return false;
        return session != null && session.owns(this);
    }

    @Override
    public void interactionEnded(UiInteractionSession.EndReason reason) { session = null; }

    @Override
    public boolean mouseDragged(UiInputContext context, double dragX, double dragY) {
        return panBy(dragX, dragY);
    }

    @Override
    public int zoomPercent() { return view.zoomPercent(); }


    @Override
    public @Nullable Component placeholder() {
        recheckIfMissing();
        return available ? null : missingText;
    }

    @Override
    public void invalidateResources() {
        boolean previous = available;
        available = resolveAvailability();
        // 「不可用 → 可用」时图片尺寸才真正可用，重新求一次 fit；其余情况保留用户缩放。
        if (!previous && available) fit();
    }

    // ---------- 绘制 ----------

    /** {@inheritDoc} */
    @Override
    public void render(GuiGraphics graphics, Font font, UiPointer incomingPointer) {
        UiRect viewport = view.viewport();
        if (!available || viewport.width() == 0 || viewport.height() == 0) return;
        UiRect target = view.contentBounds();
        UiRenderLayers.enableScissor(graphics, viewport);
        try {
            graphics.blit(image.texture(), target.x(), target.y(), target.width(), target.height(),
                    image.u(), image.v(), image.width(), image.height(),
                    image.textureWidth(), image.textureHeight());
        } finally {
            UiRenderLayers.disableScissor(graphics);
        }
    }

    // ---------- 内部 ----------

    // 缺图状态下由外壳每帧调用一次占位查询，这里做限频重查：
    // 贴图由资源包补齐时无需宿主接线也能恢复显示；可用状态不重查，绘制路径仍然零 IO。
    private void recheckIfMissing() {
        if (available) return;
        if (++recheckTicks < RECHECK_INTERVAL) return;
        recheckTicks = 0;
        invalidateResources();
    }

    // 资源可用性在构造、重载通知与限频重查时判定；正常情况下绘制路径不做 IO。
    private boolean resolveAvailability() {
        return Minecraft.getInstance().getResourceManager().getResource(image.texture()).isPresent();
    }
}
