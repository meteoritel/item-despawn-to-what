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

/** 展示内容持有本地模型；宿主只提供原版字体及返回动作。 */
final class KitShowcaseContent implements UiInputTarget, UiLayoutSource {
    static final String UI = "gui.itemdespawntowhat.debug.workbench.";
    private static final double INITIAL_VALUE = 0.37D;
    private final Font font;
    private final UiControlGroup controls = new UiControlGroup();
    private final List<UiControl> buttons;
    private final UiControlGroup navigation = new UiControlGroup();
    private final UiStyleSource theme;
    private final KitInputsExample inputs;
    private final UiModalStack modals;
    private enum Page { CONTROLS, INPUTS, SURFACES }
    private Page page = Page.CONTROLS;
    private final KitSurfacesExample surfaces;
    private final Runnable nextTheme;
    private final UiScalarSlider slider = new UiScalarSlider(UiNumberPolicy.of(0, 1, 0.05, 0.01), UiSliderWindow.of(0, 1));
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private int clicks;
    private boolean alternateGrid;
    private int resourceEpoch;
    private int gridOwners;
    private double committed = INITIAL_VALUE;
    private UiDotGrid firstGrid;
    private UiDotGrid secondGrid;
    private final KitScrollExample scrollExample;

    KitShowcaseContent(Font font, UiStyleSource theme, Runnable back, Runnable nextTheme) {
        this.font = font;
        this.nextTheme = nextTheme;
        surfaces = new KitSurfacesExample(theme, font);
        this.theme = theme;
        scrollExample = new KitScrollExample(theme);
        controls.setTheme(theme);
        navigation.setTheme(theme);
        slider.setTheme(theme, UiSliderRole.COMPACT);
        inputs = new KitInputsExample(theme, font);
        modals = new UiModalStack(theme);
        navigation.beginUpdate();
        nav("showcase.page.controls", () -> page = Page.CONTROLS);
        nav("showcase.page.inputs", () -> page = Page.INPUTS);
        nav("showcase.page.surfaces", () -> page = Page.SURFACES);
        nav("showcase.theme", nextTheme);
        nav("showcase.modal", this::openModal);
        navigation.endUpdate();
        controls.beginUpdate();
        button("increment", () -> clicks++);
        button("disabled", () -> clicks++).setEnabled(false);
        button("reset", this::reset);
        button("back", back);
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
        if (page == Page.INPUTS) inputs.addFocusTargets(sink);
        else if (page == Page.SURFACES) surfaces.addFocusTargets(sink);
        else {
            controls.addFocusTargets(sink);
            sink.accept(slider);
        }
    }

    @Override public long focusRevision() { return page.ordinal() + navigation.focusRevision() + controls.focusRevision() + inputs.focusRevision(); }

    void layout(int width, int height) {
        bounds = new UiRect(8, 28, Math.max(0, width - 16), Math.max(0, height - 36));
        int navWidth = Math.max(0, (width - 30) / navigation.size());
        for (int i = 0; i < navigation.size(); i++) navigation.controls().get(i).setBounds(12 + i * (navWidth + 2), 28, navWidth, 20);
        inputs.setBounds(12, 60, Math.max(0, width - 24), Math.max(0, height - 72));
        surfaces.setBounds(12, 60, Math.max(0, width - 24), Math.max(0, height - 72));
        modals.setBounds(0, 0, width, height);
        modals.refreshLayout();
        int buttonWidth = Math.clamp((bounds.width() - 8) / 2, 0, 140);
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).setBounds(12 + i % 2 * (buttonWidth + 8), 56 + i / 2 * 24, buttonWidth, 20);
        }
        slider.setBounds(12, 108, Math.max(0, width - 24), 20);
        scrollExample.layout(new UiRect(12, 212, Math.max(0, width - 24), Math.max(0, height - 224)));
    }

    void render(GuiGraphics graphics, UiPointer pointer) {
        graphics.drawString(font, text("kit_showcase"), 10, 10, 0xFFFFFFFF, false);
        navigation.render(graphics, font, pointer);
        if (page == Page.INPUTS) inputs.render(graphics, font, pointer);
        else if (page == Page.SURFACES) surfaces.render(graphics, font, pointer);
        else renderControls(graphics, pointer);
        modals.tick();
        modals.render(graphics, font, pointer);
        surfaces.renderOverlay(graphics, pointer);
    }

    private void renderControls(GuiGraphics graphics, UiPointer pointer) {
        controls.render(graphics, font, pointer);
        slider.render(graphics, font, pointer);
        graphics.drawString(font, values(), 12, 132, 0xFFFFFFFF, false);
        if (firstGrid == null) { firstGrid = new UiDotGrid(); gridOwners++; }
        if (secondGrid == null) { secondGrid = new UiDotGrid(); gridOwners++; }
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            int middle = bounds.x() + bounds.width() / 2;
            firstGrid.draw(graphics, 1, 12, 156, middle - 4, 200, alternateGrid ? 10 : 8, alternateGrid ? 0xFF66CC99 : 0xFF6699CC);
            secondGrid.draw(graphics, 1, middle + 4, 156, bounds.right() - 4, 200, 12, 0xFFCC9966);
        } finally {
            graphics.flush();
            RenderSystem.disableBlend();
        }
        scrollExample.render(graphics, font);
        controls.renderTooltip(graphics, font, pointer);
    }

    boolean controlsVisible() { return page == Page.CONTROLS; }

    void changeGrid() { alternateGrid = !alternateGrid; }

    void invalidateResources() {
        resourceEpoch++;
        surfaces.invalidateResources();
        if (firstGrid != null) firstGrid.invalidate();
        if (secondGrid != null) secondGrid.invalidate();
    }

    void releaseResources() {
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
        if (hit == null && page == Page.INPUTS) hit = inputs.hitTest(x, y);
        if (hit == null && page == Page.SURFACES) hit = surfaces.hitTest(x, y);
        if (page != Page.CONTROLS) return hit == null ? null : hit.within(this);
        if (hit == null) hit = scrollExample.hitTest(x, y);
        if (hit == null) hit = slider.hitTest(x, y);
        if (hit == null) hit = controls.hitTest(x, y);
        return hit == null ? null : hit.within(this);
    }

    @Override
    public boolean mousePressed(UiInputContext context) {
        if (context.hits(navigation)) return navigation.mousePressed(context);
        if (page == Page.INPUTS) return inputs.mousePressed(context);
        if (page == Page.SURFACES) return surfaces.mousePressed(context);
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
        for (UiControl control : navigation.controls()) nodes.addAll(control.layoutNodes());
        if (page == Page.INPUTS) nodes.addAll(inputs.layoutNodes());
        else if (page == Page.SURFACES) nodes.addAll(surfaces.layoutNodes());
        else {
        for (UiControl control : buttons) {
            nodes.add(node(UiLayoutNode.Kind.CONTROL, control.bounds(), control.accessibleName(), control.isEnabled(), List.of()));
        }
        nodes.add(node(UiLayoutNode.Kind.CONTROL, slider.bounds(), text("scalar"), true, List.of()));
        nodes.addAll(scrollExample.nodes());
        UiRect resourceRect = new UiRect(12, 144, bounds.width(), 10);
        Component resources = Component.translatable(UI + "showcase.resources.values", alternateGrid, gridOwners, resourceEpoch);
        nodes.add(node(UiLayoutNode.Kind.FRAME, resourceRect, text("showcase.resources"), true,
                List.of(node(UiLayoutNode.Kind.ROW, resourceRect, resources, true, List.of()))));
        UiRect valuesRect = new UiRect(12, 132, bounds.width(), font.lineHeight);
        nodes.add(node(UiLayoutNode.Kind.FRAME, valuesRect, text("values.label"), true,
                List.of(node(UiLayoutNode.Kind.ROW, valuesRect, values(), true, List.of()))));
        }
        List<UiLayoutNode> result = new ArrayList<>();
        result.add(node(UiLayoutNode.Kind.FRAME, bounds, text("kit_showcase"), true, nodes));
        result.addAll(modals.layoutNodes());
        return result;
    }

    private UiLayoutNode node(UiLayoutNode.Kind kind, UiRect rect, Component label, boolean enabled, List<UiLayoutNode> children) {
        return new UiLayoutNode(kind, rect, label, font.width(label), rect.width(), enabled, true, children);
    }

    private static Component text(String key) { return Component.translatable(UI + key); }
}
