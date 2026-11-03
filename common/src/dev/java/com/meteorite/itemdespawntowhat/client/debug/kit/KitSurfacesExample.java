package com.meteorite.itemdespawntowhat.client.debug.kit;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.LightboxImage;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiControlGroup;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiFloatingPanel;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiImageView;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiLightbox;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiWidget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiTransform;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputScope;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiNavigationHistory;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiDocument;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiOrthoLink;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.text.TextMeasurer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** 文档、浮板与图片灯箱的本地装配；所有输入和诊断取自实际内容实例。 */
final class KitSurfacesExample implements UiWidget, UiLayoutSource {
    private final Font font;
    private final UiStyleSource theme;
    private final UiDocument document;
    private final UiDocument unprofiled;
    private final UiFloatingPanel panel;
    private final UiControlGroup actions = new UiControlGroup();
    private final UiNavigationHistory<Integer> history = new UiNavigationHistory<>(4);
    private final UiImageView image;
    private final UiLightbox lightbox;
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private boolean imageOpen;
    private boolean panelVisible = true;
    private int page;
    private int hits;
    private boolean previousValid = true;
    private long revision;
    private long themeRevision = -1;

    KitSurfacesExample(UiStyleSource theme, Font font) {
        this.theme = theme;
        this.font = font;
        document = new UiDocument(TextMeasurer.of(font), true);
        unprofiled = new UiDocument(TextMeasurer.of(font), false);
        unprofiled.setContent(List.of(new UiNode.Row(label("doc.disabled"), theme.textColor(true))));
        panel = new UiFloatingPanel(font, label("panel"));
        panel.setTheme(theme);
        panel.setContent(new UiFloatingPanel.Content() {
            @Override public UiRect bounds() { return unprofiled.viewport(); }
            @Override public void setBounds(int x, int y, int w, int h) { unprofiled.setViewport(x, y, w, h); }
            @Override public void render(GuiGraphics graphics, Font font, UiPointer pointer) { unprofiled.render(graphics, font, pointer); }
        });
        ResourceLocation texture = ResourceLocation.withDefaultNamespace("textures/block/stone.png");
        image = new UiImageView(LightboxImage.whole(texture, texture, 16, 16, label("image"), label("image.description")), null, true);
        lightbox = new UiLightbox(new UiLightbox.Labels() {
            @Override public Component close() { return label("image.close"); }
            @Override public Component zoomIn() { return label("image.in"); }
            @Override public Component zoomOut() { return label("image.out"); }
            @Override public Component fit() { return label("image.fit"); }
            @Override public Component previous() { return label("history.back"); }
            @Override public Component next() { return label("history.next"); }
            @Override public Component position(int index, int total) { return Component.translatable(KitShowcaseContent.UI + "showcase.position", index, total); }
        }, image, this::closeImage);
        lightbox.setTheme(theme);
        lightbox.setText(label("image"), label("image.description"));
        lightbox.inputScope().setEscape(this::closeImage);
        actions.setTheme(theme);
        actions.beginUpdate();
        action("image", () -> imageOpen = true);
        action("panel.collapse", () -> panel.setCollapsed(!panel.isCollapsed()));
        action("panel.dock", () -> panel.setDock(UiFloatingPanel.Dock.TOP_RIGHT));
        action("panel.close", () -> panelVisible = !panelVisible);
        action("history.next", () -> { history.push(page); page++; rebuildDocument(); });
        action("history.back", () -> history.back(value -> previousValid && value < page, value -> { page = value; rebuildDocument(); }));
        action("history.invalidate", () -> previousValid = false);
        actions.endUpdate();
        rebuildDocument();
    }
    private void action(String key, Runnable action) {
        actions.obtain(key).configure(font, label(key), null, List.of(), action::run);
    }
    private void rebuildDocument() {
        document.setContent(++revision, () -> List.of(new UiNode.Row(label("doc.root"), theme.textColor(true)),
                new UiNode.Row(12, 0, Component.translatable(KitShowcaseContent.UI + "showcase.doc.page", page),
                        theme.textColor(true), List.of(), List.of(label("doc.child")), page, null),
                new UiNode.Gap(8), new UiNode.Divider(theme.surfaces().panelBorder()),
                new UiNode.Frame(new UiNode.FrameSpec(150, 42, theme.surfaces().panelBorder(), new UiTransform()),
                        List.of(new UiNode.Row(label("doc.child"), theme.textColor(true))))));
    }
    void addFocusTargets(Consumer<UiFocusTarget> sink) { actions.addFocusTargets(sink); }
    @Nullable UiInputScope overlayScope() { return imageOpen ? lightbox.inputScope() : null; }
    private void closeImage() { imageOpen = false; lightbox.onClosed(); }
    void invalidateResources() { document.invalidateLayout(); unprofiled.invalidateLayout(); lightbox.invalidateResources(); }
    void close() { closeImage(); }
    @Override public UiRect bounds() { return bounds; }
    @Override public void setBounds(int x, int y, int width, int height) {
        boolean initial = bounds.width() == 0;
        bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        int cell = Math.max(1, width / 4);
        for (int i = 0; i < actions.size(); i++) actions.controls().get(i).setBounds(x + i % 4 * cell, y + i / 4 * 22, cell - 2, 20);
        document.setViewport(x, y + 52, Math.max(1, width / 2 - 8), Math.max(1, height - 90));
        panel.setWorkArea(new UiRect(x + width / 2, y + 52, Math.max(0, width / 2), Math.max(0, height - 90)));
        if (initial) panel.setBounds(x + width / 2 + 8, y + 64, Math.max(1, width / 2 - 20), 75);
        lightbox.setBounds(x + width + 12, y + height + 12);
    }
    @Override public void render(GuiGraphics graphics, Font font, UiPointer pointer) {
        if (themeRevision != theme.revision()) { rebuildDocument(); themeRevision = theme.revision(); }
        actions.render(graphics, font, pointer);
        document.render(graphics, font, pointer);
        UiOrthoLink.drawDown(graphics, bounds.x() + 16, bounds.y() + 160, bounds.x() + 120, bounds.y() + 184, 2, theme.surfaces().panelBorder());
        if (panelVisible) panel.render(graphics, pointer);
        graphics.drawString(font, state(), bounds.x(), bounds.bottom() - 12, theme.textColor(true), false);
    }
    void renderOverlay(GuiGraphics graphics, UiPointer pointer) { if (imageOpen) lightbox.render(graphics, font, pointer, 0); }
    private Component state() {
        return Component.translatable(KitShowcaseContent.UI + "showcase.surfaces.state", page, hits, panel.isCollapsed(),
                document.metrics().builds(), document.metrics().layouts(), unprofiled.metrics().renderNanos(), image.zoomPercent());
    }
    @Override public @Nullable UiPointerHit hitTest(double x, double y) {
        UiPointerHit hit = actions.hitTest(x, y);
        if (hit == null && panelVisible) hit = panel.hitTest(x, y);
        if (hit == null) hit = document.hitTest(x, y);
        return hit == null ? null : hit.within(this);
    }
    @Override public boolean mousePressed(UiInputContext context) {
        if (actions.mousePressed(context) || panelVisible && panel.mousePressed(context)) return true;
        UiTarget hit = document.hit(context.pointerX(), context.pointerY());
        if (hit == null) return false;
        hits++;
        return true;
    }
    @Override public List<UiLayoutNode> layoutNodes() {
        List<UiLayoutNode> nodes = new ArrayList<>();
        actions.controls().forEach(control -> nodes.addAll(control.layoutNodes()));
        nodes.addAll(document.layoutNodes(document.viewport().x(), document.viewport().y()));
        if (panelVisible) nodes.add(node(UiLayoutNode.Kind.FRAME, panel.bounds(), label("panel"),
                List.of(node(UiLayoutNode.Kind.CONTROL, new UiRect(panel.bounds().x(), panel.bounds().y(),
                        panel.bounds().width() - 20, font.lineHeight + 4), label("panel"), List.of()))));
        UiRect stateRect = new UiRect(bounds.x(), bounds.bottom() - 12, bounds.width(), 12);
        nodes.add(node(UiLayoutNode.Kind.FRAME, stateRect, label("surfaces.state.label"), List.of(node(UiLayoutNode.Kind.ROW, stateRect, state(), List.of()))));
        if (imageOpen) nodes.add(node(UiLayoutNode.Kind.MODAL, lightbox.viewport(), label("image"),
                List.of(node(UiLayoutNode.Kind.ROW, lightbox.viewport(), Component.translatable(KitShowcaseContent.UI + "showcase.image.zoom", image.zoomPercent()), List.of()))));
        return nodes;
    }
    private static UiLayoutNode node(UiLayoutNode.Kind kind, UiRect rect, Component text, List<UiLayoutNode> children) {
        return new UiLayoutNode(kind, rect, text, 0, 0, true, true, children);
    }
    private static Component label(String key) { return Component.translatable(KitShowcaseContent.UI + "showcase." + key); }
}
