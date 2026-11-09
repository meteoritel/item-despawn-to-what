package com.meteorite.itemdespawntowhat.client.ui.kit.api.control;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiRenderLayers;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 按目标尺寸重绘 UiIcon 的灯箱内容；名义逻辑尺寸用于缩放读数，资源由图标宿主管理。
 * 所有调用在客户端线程执行，准备回调只登记内容，实际准备仍服从宿主帧预算。
 */
public final class UiIconLightboxContent implements UiLightbox.Content {
    private final UiIcon icon;
    private final UiStyleSource theme;
    private final LightboxViewport view;
    private @Nullable UiInteractionSession session;

    /** 图标保持原实例；主题在每次绘制时读取，内容不接管图标资源的释放。 */
    public UiIconLightboxContent(UiIcon icon, UiStyleSource theme) {
        this.icon = Objects.requireNonNull(icon);
        this.theme = Objects.requireNonNull(theme);
        view = new LightboxViewport(icon.width(), icon.height());
    }

    /** {@inheritDoc} */
    @Override public int contentWidth() { return icon.width(); }
    /** {@inheritDoc} */
    @Override public int contentHeight() { return icon.height(); }
    /** resize 保留用户缩放，只重新钳制平移。 */
    @Override public void setViewport(UiRect viewport) { view.setViewport(Objects.requireNonNull(viewport)); }

    /** 小图标也适应整个视口，手动缩放范围覆盖 fit 倍率并允许继续放大。 */
    @Override public void fit() {
        double scale = view.containScale();
        if (scale <= 0) return;
        view.fit(scale, Math.min(LightboxViewport.MIN_SCALE, scale),
                Math.max(LightboxViewport.MAX_SCALE, scale * LightboxViewport.MAX_SCALE));
    }

    /** {@inheritDoc} */
    @Override public boolean zoom(double amount, double x, double y) { return view.zoom(amount, x, y); }
    /** {@inheritDoc} */
    @Override public boolean zoomBy(int direction) { return view.zoomBy(direction); }
    /** 只有本内容持有指针会话时接收平移。 */
    @Override public boolean panBy(double x, double y) { return session != null && session.owns(this) && view.panBy(x, y); }

    /** {@inheritDoc} */
    @Override public boolean mousePressed(UiInputContext context) {
        int button = context.button();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT
                || !view.viewport().contains(context.pointerX(), context.pointerY())) return false;
        session = context.scope().interaction();
        session.begin(this, UiInteractionSession.Source.POINTER, button);
        return true;
    }

    /** 释放由 router 统一结束会话，内容只报告当前捕获是否属于自己。 */
    @Override public boolean mouseReleased(UiInputContext context) {
        return context.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && session != null && session.owns(this);
    }
    /** {@inheritDoc} */
    @Override public boolean mouseDragged(UiInputContext context, double x, double y) { return panBy(x, y); }
    /** 结束后清除借用的会话引用，捕获所有权始终由作用域维护。 */
    @Override public void interactionEnded(UiInteractionSession.EndReason reason) { session = null; }
    /** {@inheritDoc} */
    @Override public int zoomPercent() { return view.zoomPercent(); }
    /** 准备和失败占位由图标自身按统一目标框绘制。 */
    @Override public @Nullable Component placeholder() { return null; }

    /** 在固定视口裁剪内重绘目标框；图标的资源准备由宿主帧包络推进。 */
    @Override public void render(GuiGraphics graphics, Font font, UiPointer pointer) {
        UiRect viewport = view.viewport();
        if (viewport.width() == 0 || viewport.height() == 0) return;
        icon.prepare();
        UiRenderLayers.enableScissor(graphics, viewport);
        try { icon.renderScaled(graphics, view.contentBounds(), theme); }
        finally { UiRenderLayers.disableScissor(graphics); }
    }
}
