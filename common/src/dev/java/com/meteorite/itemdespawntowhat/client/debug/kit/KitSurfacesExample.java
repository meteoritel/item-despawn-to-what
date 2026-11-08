package com.meteorite.itemdespawntowhat.client.debug.kit;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.LightboxImage;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiControlGroup;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiFloatingPanel;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiImageView;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiIconLightboxContent;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiLightbox;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiWidget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiTransform;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiLayer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiNavigationHistory;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiDocument;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiOrthoLink;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiSurfaceRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.text.TextMeasurer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** 文档、浮板与图片灯箱的本地装配；所有输入和诊断取自实际内容实例。 */
final class KitSurfacesExample implements UiWidget, UiLayoutSource {
    private static final int GUTTER = 12;
    private static final int STATE_LINES = 2;
    private static final int ACTION_COLUMNS = 4;
    private static final UiSurfaceRole[] SURFACE_ROLES = UiSurfaceRole.values();
    private static final UiControlStyle.State[] SURFACE_STATES = UiControlStyle.State.values();
    private static final int SURFACE_COLUMNS = 7;
    private final List<UiRect> surfaceCells = new ArrayList<>();
    private final Font font;
    private final UiStyleSource theme;
    private final UiDocument document;
    private final UiDocument unprofiled;
    private final UiFloatingPanel panel;
    private final UiControlGroup actions = new UiControlGroup();
    private final UiNavigationHistory<Integer> history = new UiNavigationHistory<>(4);
    private final UiImageView image;
    private final UiLightbox lightbox;
    private final UiIconLightboxContent iconContent;
    private UiLightbox.Content activeContent;
    private final List<UiLayer> overlayLayers;
    private Runnable overlayChanged = () -> { };
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private UiRect captionRect = new UiRect(0, 0, 0, 0);
    private UiRect documentCard = new UiRect(0, 0, 0, 0);
    private UiRect stateBlock = new UiRect(0, 0, 0, 0);
    private Component stateValue = Component.empty();
    private List<String> stateLines = List.of();
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
        iconContent = new UiIconLightboxContent(scaledIcon(texture), theme);
        activeContent = image;
        lightbox = new UiLightbox(new UiLightbox.Labels() {
            @Override public Component close() { return label("image.close"); }
            @Override public Component zoomIn() { return label("image.in"); }
            @Override public Component zoomOut() { return label("image.out"); }
            @Override public Component fit() { return label("image.fit"); }
            @Override public Component previous() { return label("history.back"); }
            @Override public Component next() { return label("history.next"); }
            @Override public Component position(int index, int total) { return Component.translatable(KitShowcaseContent.UI + "showcase.position", index, total); }
            @Override public Component zoomReadout(int percent) { return Component.translatable(KitShowcaseContent.UI + "showcase.image.zoom", percent); }
        }, image, this::closeImage);
        lightbox.setTheme(theme);
        lightbox.setText(label("image"), label("image.description"));
        overlayLayers = List.of(lightbox.layer());
        actions.setTheme(theme);
        actions.beginUpdate();
        action("image", () -> openImage(image, "image", "image.description"));
        action("image.icon", () -> openImage(iconContent, "image.icon", "image.icon.description"));
        action("panel.collapse", () -> panel.setCollapsed(!panel.isCollapsed()));
        action("panel.dock", () -> panel.setDock(UiFloatingPanel.Dock.TOP_RIGHT));
        action("panel.close", () -> panelVisible = !panelVisible);
        action("history.next", () -> { history.push(page); page++; rebuildDocument(); });
        action("history.back", () -> history.back(value -> previousValid && value < page, value -> { page = value; rebuildDocument(); }));
        action("history.invalidate", () -> previousValid = false);
        actions.endUpdate();
        rebuildDocument();
    }
    private UiIcon scaledIcon(ResourceLocation texture) {
        UiIcon sprite = new UiIcon.Sprite(texture, 0, 0, 16, 16, 16, 16);
        UiIcon item = new UiIcon.Item(new ItemStack(Items.GOLD_INGOT));
        UiIcon rendered = new UiIcon.Rendered(56, 32, (graphics, box) -> {
            theme.drawInset(graphics, box);
            int iconWidth = Math.max(0, box.width() / 2 - 4);
            int iconHeight = Math.max(0, box.height() - 4);
            sprite.renderScaled(graphics, new UiRect(box.x() + 2, box.y() + 2, iconWidth, iconHeight), theme);
            item.renderScaled(graphics, new UiRect(box.x() + box.width() / 2 + 2, box.y() + 2, iconWidth, iconHeight), theme);
        });
        return new UiIcon.Themed(56, 32, rendered::renderScaled, rendered::prepare);
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
    public void addFocusTargets(Consumer<UiFocusTarget> sink) { actions.addFocusTargets(sink); }
    List<UiLayer> overlayLayers() { return imageOpen ? overlayLayers : List.of(); }
    void setOnOverlayChanged(Runnable handler) { overlayChanged = handler; }
    private void openImage(UiLightbox.Content content, String title, String description) {
        activeContent = content;
        lightbox.setContent(content);
        lightbox.setText(label(title), label(description));
        imageOpen = true;
        overlayChanged.run();
    }
    private void closeImage() { imageOpen = false; overlayChanged.run(); lightbox.onClosed(); }
    void prepareLightbox(int width, int height) { lightbox.prepare(font, width, height); }
    void invalidateResources() { document.invalidateLayout(); unprofiled.invalidateLayout(); lightbox.invalidateResources(); }
    void close() { closeImage(); }
    @Override public UiRect bounds() { return bounds; }

    /**
     * 动作行占满宽度，文档与浮板按左右两半并排，状态行固定在底部。
     *
     * <p>浮板的工作区与文档视口同高，初始位置只在本内容首次获得尺寸时确定，之后由用户拖动。</p>
     */
    @Override public void setBounds(int x, int y, int width, int height) {
        boolean initial = bounds.width() == 0;
        bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        int capHeight = font.lineHeight + 2;
        int stateHeight = font.lineHeight + 2 + STATE_LINES * (font.lineHeight + 2);
        int stateTop = Math.max(y, y + height - stateHeight);
        captionRect = new UiRect(x, y, width, font.lineHeight);
        int actionCell = Math.max(1, (width - (ACTION_COLUMNS - 1) * 4) / ACTION_COLUMNS);
        for (int i = 0; i < actions.size(); i++) {
            actions.controls().get(i).setBounds(x + i % ACTION_COLUMNS * (actionCell + 4), y + capHeight + i / ACTION_COLUMNS * 24, actionCell, 20);
        }
        int surfaceTop = y + capHeight + 2 * 24;
        surfaceCells.clear();
        int surfaceWidth = Math.max(0, width / SURFACE_COLUMNS);
        for (int i = 0; i < SURFACE_ROLES.length; i++) surfaceCells.add(new UiRect(
                x + i % SURFACE_COLUMNS * surfaceWidth, surfaceTop + i / SURFACE_COLUMNS * 22,
                Math.max(0, surfaceWidth - 2), 20));
        int contentTop = surfaceTop + 48;
        int contentBottom = stateTop - WorkbenchLayout.GROUP_GAP;
        int contentHeight = Math.max(1, contentBottom - contentTop);
        int docWidth = Math.max(60, (width - GUTTER) / 2);
        documentCard = new UiRect(x, contentTop, docWidth, contentHeight);
        document.setViewport(x + 6, contentTop + 6, Math.max(0, docWidth - 12), Math.max(0, contentHeight - 12));
        panel.setWorkArea(new UiRect(x + docWidth + GUTTER, contentTop, Math.max(0, width - docWidth - GUTTER), contentHeight));
        if (initial) panel.setBounds(x + docWidth + GUTTER + 8, contentTop + 12, Math.max(1, docWidth - 20), 75);
        stateBlock = new UiRect(x, stateTop, width, stateHeight);
        stateValue = state();
        stateLines = WorkbenchLayout.wrapLines(font, stateValue.getString(), width, STATE_LINES);
    }

    @Override public void render(GuiGraphics graphics, Font font, UiPointer pointer) {
        if (themeRevision != theme.revision()) { rebuildDocument(); themeRevision = theme.revision(); }
        graphics.drawString(font, label("surfaces.actions"), captionRect.x(), captionRect.y(), theme.surfaces().secondaryText(), false);
        actions.render(graphics, font, pointer);
        for (int i = 0; i < SURFACE_ROLES.length; i++) {
            UiRect cell = surfaceCells.get(i);
            theme.drawSurface(graphics, cell, SURFACE_ROLES[i], SURFACE_STATES[i % SURFACE_STATES.length]);
            Component name = label("skin.role." + SURFACE_ROLES[i].name().toLowerCase(Locale.ROOT));
            graphics.drawString(font, WorkbenchLayout.ellipsize(font, name, Math.max(0, cell.width() - 4)),
                    cell.x() + 2, cell.y() + 5, theme.textColor(true), false);
        }
        theme.drawInset(graphics, documentCard);
        document.render(graphics, font, pointer);
        UiRect doc = document.viewport();
        UiOrthoLink.drawDown(graphics, doc.x() + 16, doc.bottom() - 40, doc.x() + 120, doc.bottom() - 16, 2, theme.surfaces().panelBorder());
        if (panelVisible) panel.render(graphics, pointer);
        graphics.drawString(font, label("surfaces.state.label"), stateBlock.x(), stateBlock.y(), theme.surfaces().secondaryText(), false);
        int lineY = stateBlock.y() + font.lineHeight + 2;
        for (String line : stateLines) {
            graphics.drawString(font, line, stateBlock.x(), lineY, theme.textColor(true), false);
            lineY += font.lineHeight + 2;
        }
    }
    private Component state() {
        return Component.translatable(KitShowcaseContent.UI + "showcase.surfaces.state", page, hits, panel.isCollapsed(),
                document.metrics().builds(), document.metrics().layouts(), unprofiled.metrics().renderNanos(), activeContent.zoomPercent());
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
        nodes.add(node(UiLayoutNode.Kind.FRAME, stateBlock, label("surfaces.state.label"), List.of(
                node(UiLayoutNode.Kind.ROW, stateBlock, stateValue, List.of()))));
        return nodes;
    }
    private static UiLayoutNode node(UiLayoutNode.Kind kind, UiRect rect, Component text, List<UiLayoutNode> children) {
        return new UiLayoutNode(kind, rect, text, 0, 0, true, true, children);
    }
    private static Component label(String key) { return Component.translatable(KitShowcaseContent.UI + "showcase." + key); }
}
