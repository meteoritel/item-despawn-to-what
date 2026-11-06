package com.meteorite.itemdespawntowhat.client.debug.kit;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiControl;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiControlGroup;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiNumberPolicy;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiScalarSlider;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiSliderWindow;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiValueInteraction;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiDotGrid;
import com.mojang.blaze3d.systems.RenderSystem;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiSliderRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiModal;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiModalStack;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiTextInput;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputScope;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlRole;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.PreviewBudget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.PreviewScenes;

/** 展示内容持有本地模型；宿主只提供原版字体及返回动作。 */
final class KitShowcaseContent implements UiInputTarget, UiLayoutSource {
    static final String UI = "gui.itemdespawntowhat.debug.workbench.";
    private static final double INITIAL_VALUE = 0.37D;
    private static final int NAV_GAP = 4;
    private static final int NAV_TOP = WorkbenchLayout.CONTENT_TOP;
    private static final int CONTENT_TOP = NAV_TOP + WorkbenchLayout.CONTROL_HEIGHT + WorkbenchLayout.GAP;
    private final Font font;
    private final UiControlGroup controls = new UiControlGroup();
    private final List<UiControl> buttons;
    private final UiControlGroup navigation = new UiControlGroup();
    private final UiControlGroup footer = new UiControlGroup();
    private final UiStyleSource theme;
    private final KitInputsExample inputs;
    private final UiModalStack modals;
    private enum Page { CONTROLS, INPUTS, SURFACES, LAYERS }
    private Page page = Page.CONTROLS;
    private final KitSurfacesExample surfaces;
    private final KitLayersExample layers;
    private final Runnable nextTheme;
    private final UiScalarSlider slider = new UiScalarSlider(UiNumberPolicy.of(0, 1, 0.05, 0.01), UiSliderWindow.of(0, 1));
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private int width;
    private int height;
    private int clicks;
    private boolean alternateGrid;
    private int resourceEpoch;
    private int gridOwners;
    private double committed = INITIAL_VALUE;
    private UiDotGrid firstGrid;
    private UiDotGrid secondGrid;
    private final KitScrollExample scrollExample;
    private UiRect valuesRow = new UiRect(0, 0, 0, 0);
    private UiRect resourcesRow = new UiRect(0, 0, 0, 0);
    private UiRect firstGridRect = new UiRect(0, 0, 0, 0);
    private UiRect secondGridRect = new UiRect(0, 0, 0, 0);
    private int scrollCaptionY;

    KitShowcaseContent(Font font, UiStyleSource theme, Runnable back, Runnable nextTheme,
                       @Nullable Function<PreviewBudget, PreviewScenes> previewFactory) {
        this.font = font;
        this.nextTheme = nextTheme;
        surfaces = new KitSurfacesExample(theme, font);
        layers = new KitLayersExample(theme, font, previewFactory);
        this.theme = theme;
        scrollExample = new KitScrollExample(theme);
        controls.setTheme(theme);
        navigation.setTheme(theme);
        footer.setTheme(theme);
        slider.setTheme(theme, UiSliderRole.COMPACT);
        inputs = new KitInputsExample(theme, font);
        modals = new UiModalStack(theme);
        navigation.beginUpdate();
        nav("showcase.page.controls", () -> page = Page.CONTROLS);
        nav("showcase.page.inputs", () -> page = Page.INPUTS);
        nav("showcase.page.surfaces", () -> page = Page.SURFACES);
        nav("showcase.layers.title", () -> page = Page.LAYERS);
        nav("showcase.theme", nextTheme);
        nav("showcase.modal", this::openModal);
        navigation.endUpdate();
        footer.beginUpdate();
        footer.obtain("back").configure(font, text("back"), null, List.of(), back::run);
        footer.endUpdate();
        controls.beginUpdate();
        button("increment", () -> clicks++);
        button("disabled", () -> clicks++).setEnabled(false);
        button("reset", this::reset);
        KitSkinSamples.customize(controls.controls().getFirst());
        controls.endUpdate();
        buttons = controls.controls();
        slider.setLabel(text("scalar"));
        slider.setValue(INITIAL_VALUE);
        slider.setInteractionListener(new UiValueInteraction.Listener<>() {
            @Override public void committed(Double start, Double value) { committed = value; }
        });
    }

    private void nav(String key, Runnable action) {
        UiControl control = navigation.obtain(key);
        control.configure(font, text(key), null, List.of(), action::run);
    }

    private void openModal() {
        UiTextInput edit = new UiTextInput(theme, font, text("showcase.text"));
        edit.setValue(text("showcase.text").getString());
        modals.push(UiModal.create(theme, font).title(text("showcase.modal")).contentWidget(edit, 40)
                .closeWhen(() -> !edit.value().isBlank())
                .addButton(modalButton("showcase.theme", this::themeAction))
                .addButton(modalButton("showcase.nested", this::openModal)).cancel(text("back")));
    }

    private void themeAction() { nextTheme.run(); }

    private UiControl modalButton(String key, Runnable action) {
        UiControl control = new UiControl();
        control.setTheme(theme, UiControlRole.SECONDARY);
        control.setFocusable(true);
        control.configure(font, text(key), null, List.of(), action::run);
        return control;
    }

    UiInputScope activeScope(UiInputScope page) {
        UiInputScope overlay = surfaces.overlayScope();
        if (overlay != null) return overlay;
        UiModal top = modals.top();
        return top == null ? page : top.inputScope();
    }

    private UiControl button(String key, Runnable action) {
        UiControl control = controls.obtain(key);
        control.configure(font, text(key), null, List.of(), action::run);
        return control;
    }

    private void reset() {
        releaseResources();
        scrollExample.reset();
        clicks = 0;
        committed = INITIAL_VALUE;
        slider.setValue(INITIAL_VALUE);
    }

    public void addFocusTargets(Consumer<UiFocusTarget> sink) {
        navigation.addFocusTargets(sink);
        footer.addFocusTargets(sink);
        if (page == Page.INPUTS) inputs.addFocusTargets(sink);
        else if (page == Page.SURFACES) surfaces.addFocusTargets(sink);
        else if (page == Page.LAYERS) layers.addFocusTargets(sink);
        else {
            controls.addFocusTargets(sink);
            sink.accept(slider);
        }
    }

    @Override public long focusRevision() { return page.ordinal() + navigation.focusRevision() + controls.focusRevision() + inputs.focusRevision(); }

    void layout(int width, int height) {
        this.width = width;
        this.height = height;
        int contentWidth = WorkbenchLayout.contentWidth(width);
        int navWidth = Math.max(24, (contentWidth - (navigation.size() - 1) * NAV_GAP) / navigation.size());
        for (int i = 0; i < navigation.size(); i++) {
            navigation.controls().get(i).setBounds(WorkbenchLayout.INSET + i * (navWidth + NAV_GAP), NAV_TOP, navWidth, WorkbenchLayout.CONTROL_HEIGHT);
        }
        WorkbenchLayout.placeFooterBack(footer.controls().getFirst(), height);
        bounds = new UiRect(WorkbenchLayout.INSET, CONTENT_TOP, contentWidth,
                Math.max(0, WorkbenchLayout.contentBottom(height) - CONTENT_TOP));
        inputs.setBounds(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        surfaces.setBounds(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        layers.setBounds(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        modals.setBounds(0, 0, width, height);
        modals.refreshLayout();
        int actionWidth = Math.max(32, (bounds.width() - 2 * WorkbenchLayout.GAP) / 3);
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).setBounds(bounds.x() + i * (actionWidth + WorkbenchLayout.GAP), bounds.y(), actionWidth, WorkbenchLayout.CONTROL_HEIGHT);
        }
        int y = bounds.y() + WorkbenchLayout.CONTROL_HEIGHT + WorkbenchLayout.GAP;
        slider.setBounds(bounds.x(), y, bounds.width(), WorkbenchLayout.CONTROL_HEIGHT);
        y += WorkbenchLayout.CONTROL_HEIGHT + WorkbenchLayout.GAP;
        valuesRow = new UiRect(bounds.x(), y, bounds.width(), font.lineHeight);
        y += font.lineHeight + WorkbenchLayout.GROUP_GAP;
        resourcesRow = new UiRect(bounds.x(), y, bounds.width(), font.lineHeight);
        y += font.lineHeight + 4;
        int remaining = Math.max(0, bounds.bottom() - y);
        int captionHeight = font.lineHeight + 4;
        // 点阵面板按剩余高度取四成，滚动示例吃掉其余空间，两块演示都不留空。
        int gridHeight = Math.min(320, (int) (remaining * 0.4F));
        int scrollHeight = Math.max(0, remaining - gridHeight - captionHeight - WorkbenchLayout.GAP);
        if (scrollHeight < 48) {
            // 高度不足时改为二等分，保证两块演示都留在内容区内。
            gridHeight = Math.max(0, (remaining - captionHeight - WorkbenchLayout.GAP) / 2);
            scrollHeight = Math.max(0, remaining - gridHeight - captionHeight - WorkbenchLayout.GAP);
        }
        int middle = bounds.x() + bounds.width() / 2;
        firstGridRect = new UiRect(bounds.x(), y, Math.max(0, middle - 4 - bounds.x()), gridHeight);
        secondGridRect = new UiRect(middle + 4, y, Math.max(0, bounds.right() - middle - 4), gridHeight);
        scrollCaptionY = y + gridHeight + WorkbenchLayout.GAP;
        scrollExample.layout(new UiRect(bounds.x(), scrollCaptionY + captionHeight, bounds.width(), scrollHeight));
    }

    void render(GuiGraphics graphics, UiPointer pointer) {
        WorkbenchLayout.drawPageHeading(graphics, font, theme, width, text("kit_showcase"));
        WorkbenchLayout.drawSubtitle(graphics, font, theme, width, text("kit_showcase.description"));
        navigation.render(graphics, font, pointer);
        if (page == Page.INPUTS) inputs.render(graphics, font, pointer);
        else if (page == Page.SURFACES) surfaces.render(graphics, font, pointer);
        else if (page == Page.LAYERS) layers.render(graphics, font, pointer);
        else renderControls(graphics, pointer);
        WorkbenchLayout.drawFooterRule(graphics, theme, width, height);
        footer.render(graphics, font, pointer);
        modals.tick();
        modals.render(graphics, font, pointer);
        surfaces.renderOverlay(graphics, pointer);
        // 提示画在页脚与弹层之后；弹层开启时由弹层自己出提示
        if (page == Page.CONTROLS && modals.isEmpty() && pointer.allowsTooltip() && pointer.hit() != null) {
            pointer.hit().renderTooltip(graphics, font, theme.tooltipSkin(), (int) pointer.x(), (int) pointer.y());
        }
    }

    private void renderControls(GuiGraphics graphics, UiPointer pointer) {
        controls.render(graphics, font, pointer);
        slider.render(graphics, font, pointer);
        graphics.drawString(font, values(), valuesRow.x(), valuesRow.y(), theme.surfaces().text(), false);
        Component label = text("showcase.resources");
        int labelColor = theme.surfaces().secondaryText();
        graphics.drawString(font, label, resourcesRow.x(), resourcesRow.y(), labelColor, false);
        Component resources = Component.translatable(UI + "showcase.resources.values", alternateGrid, gridOwners, resourceEpoch);
        Component trimmed = WorkbenchLayout.ellipsize(font, resources, Math.max(0, resourcesRow.width() - font.width(label) - 12));
        graphics.drawString(font, trimmed, resourcesRow.right() - font.width(trimmed), resourcesRow.y(), labelColor, false);
        if (firstGrid == null) { firstGrid = new UiDotGrid(); gridOwners++; }
        if (secondGrid == null) { secondGrid = new UiDotGrid(); gridOwners++; }
        theme.drawInset(graphics, firstGridRect);
        theme.drawInset(graphics, secondGridRect);
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            int firstColor = alternateGrid ? theme.surfaces().text() : theme.surfaces().secondaryText();
            firstGrid.draw(graphics, 1, firstGridRect.x(), firstGridRect.y(), firstGridRect.right(), firstGridRect.bottom(),
                    alternateGrid ? 10 : 8, firstColor);
            secondGrid.draw(graphics, 1, secondGridRect.x(), secondGridRect.y(), secondGridRect.right(), secondGridRect.bottom(),
                    12, theme.surfaces().secondaryText());
        } finally {
            graphics.flush();
            RenderSystem.disableBlend();
        }
        graphics.drawString(font, text("showcase.scroll"), bounds.x(), scrollCaptionY, theme.surfaces().secondaryText(), false);
        scrollExample.render(graphics, font);
    }

    boolean controlsVisible() { return page == Page.CONTROLS; }

    void changeGrid() { alternateGrid = !alternateGrid; }

    void invalidateResources() {
        resourceEpoch++;
        surfaces.invalidateResources();
        layers.invalidateResources();
        if (firstGrid != null) firstGrid.invalidate();
        if (secondGrid != null) secondGrid.invalidate();
    }

    void releaseResources() {
        layers.close();
        surfaces.close();
        modals.clear();
        if (firstGrid != null) firstGrid.close();
        if (secondGrid != null) secondGrid.close();
        firstGrid = null;
        secondGrid = null;
    }

    private Component values() {
        return Component.translatable(UI + "showcase.values", clicks, slider.value(), committed);
    }

    @Override
    public @Nullable UiPointerHit hitTest(double x, double y) {
        UiPointerHit hit = navigation.hitTest(x, y);
        if (hit == null) hit = footer.hitTest(x, y);
        if (hit == null && page == Page.INPUTS) hit = inputs.hitTest(x, y);
        if (hit == null && page == Page.SURFACES) hit = surfaces.hitTest(x, y);
        if (hit == null && page == Page.LAYERS) hit = layers.hitTest(x, y);
        if (page != Page.CONTROLS) return hit == null ? null : hit.within(this);
        if (hit == null) hit = scrollExample.hitTest(x, y);
        if (hit == null) hit = slider.hitTest(x, y);
        if (hit == null) hit = controls.hitTest(x, y);
        return hit == null ? null : hit.within(this);
    }

    @Override
    public boolean mousePressed(UiInputContext context) {
        if (context.hits(navigation)) return navigation.mousePressed(context);
        if (context.hits(footer)) return footer.mousePressed(context);
        if (page == Page.INPUTS) return inputs.mousePressed(context);
        if (page == Page.SURFACES) return surfaces.mousePressed(context);
        if (page == Page.LAYERS) return layers.mousePressed(context);
        if (context.hits(scrollExample)) return scrollExample.mousePressed(context);
        if (context.hits(slider)) return slider.mousePressed(context);
        return context.hits(controls) && controls.mousePressed(context);
    }

    @Override
    public boolean mouseScrolled(UiInputContext context, double x, double y) {
        return page == Page.INPUTS ? inputs.mouseScrolled(context, x, y) : page == Page.CONTROLS && scrollExample.mouseScrolled(context, x, y);
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        return page == Page.INPUTS ? inputs.keyPressed(key, scan, mods) : page == Page.CONTROLS && slider.isFocused() && slider.keyPressed(key, scan, mods);
    }

    @Override public boolean charTyped(char value, int mods) {
        return page == Page.INPUTS && inputs.charTyped(value, mods);
    }

    @Override
    public List<UiLayoutNode> layoutNodes() {
        List<UiLayoutNode> nodes = new ArrayList<>();
        if (page == Page.INPUTS) nodes.addAll(inputs.layoutNodes());
        else if (page == Page.SURFACES) nodes.addAll(surfaces.layoutNodes());
        else if (page == Page.LAYERS) nodes.addAll(layers.layoutNodes());
        else {
            for (UiControl control : buttons) {
                nodes.add(node(UiLayoutNode.Kind.CONTROL, control.bounds(), control.accessibleName(), control.isEnabled(), List.of()));
            }
            nodes.add(node(UiLayoutNode.Kind.CONTROL, slider.bounds(), text("scalar"), true, List.of()));
            nodes.addAll(scrollExample.nodes());
            Component resources = Component.translatable(UI + "showcase.resources.values", alternateGrid, gridOwners, resourceEpoch);
            nodes.add(node(UiLayoutNode.Kind.FRAME, resourcesRow, text("showcase.resources"), true,
                    List.of(node(UiLayoutNode.Kind.ROW, resourcesRow, resources, true, List.of()))));
            nodes.add(node(UiLayoutNode.Kind.FRAME, valuesRow, text("values.label"), true,
                    List.of(node(UiLayoutNode.Kind.ROW, valuesRow, values(), true, List.of()))));
        }
        // 页签与页脚在内容区之外，作为独立根节点导出，避免被内容 Frame 的裁剪范围丢弃。
        List<UiLayoutNode> result = new ArrayList<>();
        for (UiControl control : navigation.controls()) result.addAll(control.layoutNodes());
        for (UiControl control : footer.controls()) result.addAll(control.layoutNodes());
        result.add(node(UiLayoutNode.Kind.FRAME, bounds, text("kit_showcase"), true, nodes));
        result.addAll(modals.layoutNodes());
        return result;
    }

    private UiLayoutNode node(UiLayoutNode.Kind kind, UiRect rect, Component label, boolean enabled, List<UiLayoutNode> children) {
        return new UiLayoutNode(kind, rect, label, font.width(label), rect.width(), enabled, true, children);
    }

    private static Component text(String key) { return Component.translatable(UI + key); }
}
