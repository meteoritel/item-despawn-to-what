package com.meteorite.itemdespawntowhat.client.debug.kit;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiLightbox;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiTransform;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiDocument;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiDocumentHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.text.TextMeasurer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** 自定义 Content 展示文档行、图标动作和嵌套 Frame，输入提示仍来自一次 hitTest。 */
final class KitDocumentLightboxContent implements UiLightbox.Content {
    private final UiDocument document;
    private final UiStyleSource theme;
    private @Nullable UiInteractionSession session;
    private long themeRevision = -1;
    private int activations;

    KitDocumentLightboxContent(Font font, UiStyleSource theme) {
        this.theme = theme;
        document = new UiDocument(TextMeasurer.of(font), false);
        rebuild();
    }
    private void rebuild() {
        UiNode.InlineIcon icon = new UiNode.InlineIcon(new UiIcon.Item(new ItemStack(Items.GOLD_INGOT)),
                List.of(label("icon")), "icon", () -> activations++);
        document.setContent(List.of(new UiNode.Row(0, UiNode.NO_PARENT, icon,
                        Component.translatable(KitShowcaseContent.UI + "showcase.canvas.count", activations),
                        theme.textColor(true), List.of(), List.of(label("row")), "row", () -> activations++),
                new UiNode.Gap(8), new UiNode.Frame(new UiNode.FrameSpec(180, 48, theme.surfaces().panelBorder(), new UiTransform()),
                        List.of(new UiNode.Row(label("child"), theme.textColor(true))))));
    }
    /** {@inheritDoc} */
    @Override public int contentWidth() { return document.contentWidth(); }
    /** {@inheritDoc} */
    @Override public int contentHeight() { return document.contentHeight(); }
    /** {@inheritDoc} */
    @Override public void setViewport(UiRect viewport) {
        document.setViewport(viewport.x(), viewport.y(), viewport.width(), viewport.height());
        document.transform().setViewport(viewport.x(), viewport.y(), viewport.width(), viewport.height());
    }
    /** {@inheritDoc} */
    @Override public void render(GuiGraphics graphics, Font font, UiPointer pointer) {
        if (themeRevision != theme.revision()) { rebuild(); themeRevision = theme.revision(); }
        document.render(graphics, font, pointer);
    }
    /** 行与图标提示按文档同一缓存解析，空白仍保留 Content 所有权。 */
    @Override public UiPointerHit hitTest(double x, double y) {
        UiPointerHit hit = document.hitTest(x, y);
        return hit == null ? UiPointerHit.leaf(this, x, y) : hit.within(this);
    }
    /** {@inheritDoc} */
    @Override public boolean zoom(double amount, double x, double y) {
        if (amount == 0) return false;
        document.transform().zoomBy((int) Math.signum(amount), x, y);
        return true;
    }
    /** {@inheritDoc} */
    @Override public boolean zoomBy(int direction) {
        UiRect viewport = document.viewport();
        return zoom(direction, viewport.x() + viewport.width() / 2.0, viewport.y() + viewport.height() / 2.0);
    }
    /** {@inheritDoc} */
    @Override public void fit() { document.transform().fit(0, 0, contentWidth(), contentHeight(), 8); }
    /** {@inheritDoc} */
    @Override public boolean panBy(double dx, double dy) {
        if (session == null || !session.owns(this)) return false;
        document.transform().panBy(dx, dy);
        return true;
    }
    /** 文档动作由宿主解释；按下后仍允许同一手势平移。 */
    @Override public boolean mousePressed(UiInputContext context) {
        if (context.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || !document.viewport().contains(context.pointerX(), context.pointerY())) return false;
        UiDocumentHit hit = document.hit(context.pointerX(), context.pointerY());
        if (hit != null && hit.action() != null) { hit.action().run(); rebuild(); }
        session = context.scope().interaction();
        session.begin(this, UiInteractionSession.Source.POINTER, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        return true;
    }
    /** {@inheritDoc} */
    @Override public boolean mouseDragged(UiInputContext context, double dx, double dy) { return panBy(dx, dy); }
    /** {@inheritDoc} */
    @Override public boolean mouseReleased(UiInputContext context) { return session != null && session.owns(this); }
    /** {@inheritDoc} */
    @Override public void interactionEnded(UiInteractionSession.EndReason reason) { session = null; }
    /** {@inheritDoc} */
    @Override public int zoomPercent() { return document.transform().scalePercent(); }
    /** {@inheritDoc} */
    @Override public @Nullable Component placeholder() { return null; }
    /** {@inheritDoc} */
    @Override public void invalidateResources() { document.invalidateLayout(); rebuild(); }
    private static Component label(String key) { return Component.translatable(KitShowcaseContent.UI + "showcase.canvas." + key); }
}
