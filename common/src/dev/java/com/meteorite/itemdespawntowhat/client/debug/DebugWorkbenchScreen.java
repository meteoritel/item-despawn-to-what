package com.meteorite.itemdespawntowhat.client.debug;

import com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputRouter;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputScope;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiNumberPolicy;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRangeValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiScalarSlider;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderExamples;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiValueInteraction;
import com.meteorite.itemdespawntowhat.client.ui.screen.RuleEditorScreen;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiListView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModalStack;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiRangeBar;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** 开发客户端工具目录与本地组件场景；每个场景拥有自己的输入、模型和模态。 */
public class DebugWorkbenchScreen extends Screen {
    static final String UI = "gui.itemdespawntowhat.debug.workbench.";
    private final Runnable returnTo;

    private DebugWorkbenchScreen(Runnable returnTo) {
        super(text("title"));
        this.returnTo = returnTo;
    }

    /** 从任意开发屏幕进入目录，编辑器返回时按工作区重建屏幕。 */
    public static void open() {
        Minecraft client = Minecraft.getInstance();
        Screen previous = client.screen;
        Runnable back = previous instanceof RuleEditorScreen ? DebugWorkbenchScreen::openEditor : () -> client.setScreen(previous);
        client.setScreen(new Directory(back));
    }

    private static Component text(String key) { return Component.translatable(UI + key); }

    private static void openEditor() {
        var workspace = RuleEditClientWorkspace.instance();
        if (workspace.session().editable()) {
            Minecraft.getInstance().setScreen(new RuleEditorScreen(workspace));
        } else if (Minecraft.getInstance().getConnection() != null) {
            Minecraft.getInstance().getConnection().sendCommand("idtw config edit");
        } else { Minecraft.getInstance().setScreen(null); }
    }

    /** 本地工具保持游戏 tick 推进。 */
    @Override public boolean isPauseScreen() { return false; }
    /** 返回调用方，编辑器始终使用新的外壳实例。 */
    @Override public void onClose() { returnTo.run(); }

    private record Tool(String id, BooleanSupplier available, Runnable open) {}

    private static final class Directory extends DebugWorkbenchScreen implements UiLayoutSource {
        private final List<UiButton> buttons = new ArrayList<>();
        private final List<Tool> tools;
        private final UiInputRouter input = new UiInputRouter();
        private final UiInputScope scope;
        private final UiHostTheme theme = UiHostThemes.defaultTheme();
        Directory(Runnable back) {
            super(back);
            Runnable home = () -> Minecraft.getInstance().setScreen(new Directory(back));
            tools = List.of(
                    new Tool("gallery", () -> true, () -> Minecraft.getInstance().setScreen(new Gallery(home, false))),
                    new Tool("interactions", () -> true, () -> Minecraft.getInstance().setScreen(new Gallery(home, true))),
                    new Tool("previews", () -> Minecraft.getInstance().level != null, () -> Minecraft.getInstance().setScreen(new PreviewExamplesScreen(home))),
                    new Tool("drafts", () -> RuleEditClientWorkspace.instance().session().editable(), () -> {
                        var screen = new RuleEditorScreen(RuleEditClientWorkspace.instance());
                        Minecraft.getInstance().setScreen(screen);
                        screen.showDebugDrafts();
                    }));
            scope = new UiInputScope(new UiInputTarget() {
                @Override public boolean mousePressed(UiInputContext context) {
                    for (UiButton button : buttons) { if (button.mousePressed(context)) { return true; } }
                    return false;
                }
            });
            scope.setEscape(this::onClose);
            scope.setFocusTargets(() -> {
                scope.focus().beginUpdate(); buttons.forEach(scope.focus()::add); scope.focus().endUpdate();
            });
        }
        @Override protected void init() {
            if (buttons.isEmpty()) {
                for (Tool tool : tools) { buttons.add(new UiButton(theme, font, text(tool.id()), UiButtonVariant.SECONDARY, tool.open()::run)); }
                buttons.add(new UiButton(theme, font, text("back"), UiButtonVariant.SECONDARY, this::onClose));
            }
            scope.refreshFocus(); input.select(scope);
        }
        @Override public void render(@NotNull GuiGraphics graphics, int x, int y, float delta) {
            renderBackground(graphics, x, y, delta);
            theme.drawWindow(graphics, new UiRect(0, 0, width, height));
            graphics.drawString(font, title, 10, 10, theme.palette().textPrimary(), false);
            for (int i = 0; i < tools.size(); i++) {
                var tool = tools.get(i); var button = buttons.get(i);
                button.setEnabled(tool.available().getAsBoolean()); button.setBounds(10, 30 + i * Math.clamp((height - 64) / tools.size(), 28, 42), Math.min(190, width - 20), 18);
                button.render(graphics, font, x, y);
                graphics.drawString(font, text(tool.id() + ".description"), 12, 51 + i * Math.clamp((height - 64) / tools.size(), 28, 42), theme.palette().textSecondary(), false);
            }
            var back = buttons.getLast(); back.setBounds(10, height - 24, 90, 18); back.render(graphics, font, x, y);
        }
        @Override public List<UiLayoutNode> layoutNodes() {
            List<UiLayoutNode> nodes = new ArrayList<>(); buttons.forEach(button -> nodes.addAll(button.layoutNodes())); return nodes;
        }
        private UiInputContext pointer(double x, double y, int button) { return UiInputContext.of(scope, x, y, button, hasShiftDown(), hasControlDown(), hasAltDown()); }
        @Override public boolean mouseClicked(double x, double y, int button) { return input.mousePressed(pointer(x, y, button)); }
        @Override public boolean mouseReleased(double x, double y, int button) { return input.mouseReleased(pointer(x, y, button)); }
        @Override public boolean keyPressed(int key, int scan, int mods) { return input.keyPressed(key, scan, mods); }
        @Override public boolean keyReleased(int key, int scan, int mods) { return input.keyReleased(key, scan, mods); }
        @Override public void removed() { input.close(); }
    }

    private static final class Gallery extends DebugWorkbenchScreen implements UiLayoutSource {
        private final UiHostTheme theme = UiHostThemes.defaultTheme();
        private final UiInputRouter input = new UiInputRouter();
        private final UiModalStack modals = new UiModalStack(theme);
        private final List<UiWidget> widgets = new ArrayList<>();
        private final List<UiButton> fixed = new ArrayList<>();
        private final List<UiLayoutNode> nodes = new ArrayList<>();
        private final List<UiLayoutNode> overlayNodes = new ArrayList<>();
        private UiInputScope scope;
        private UiTextInput textInput;
        private UiTextInput number;
        private UiRangeBar range;
        private UiScalarSlider scalar;
        private UiListView<Integer> list;
        private int clicks;
        private int selection;
        private int scroll;
        private double committed = 0.37;
        private boolean initialized;
        private final boolean interactions;

        Gallery(Runnable back, boolean interactions) { super(back); this.interactions = interactions; }
        @Override protected void init() {
            modals.setBounds(0, 0, width, height);
            if (initialized) { return; }
            initialized = true;
            assemble();
        }
        private UiButton button(String key, Runnable action) { return new UiButton(theme, font, text(key), UiButtonVariant.SECONDARY, action::run); }
        private void assemble() {
            input.close(); modals.clear(); widgets.clear(); fixed.clear();
            clicks = 0; selection = 0; committed = 0.37; scroll = 0;
            textInput = new UiTextInput(theme, font, text("text")); textInput.setValue("debug");
            number = new UiTextInput(theme, font, text("number")); number.setValue("12");
            number.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
            widgets.add(button("increment", () -> clicks++));
            widgets.add(button("disabled", () -> clicks++).setEnabled(false));
            widgets.add(textInput); widgets.add(number);
            widgets.add(button("selection", () -> selection = (selection + 1) % 3));
            range = new UiRangeBar(theme, font, UiNumberPolicy.integer(0, 100, 1), UiRangeValue.of(20, 80),
                    new UiRangeBar.Texts(text("low"), text("high"), text("absent"), text("precise")), value -> {});
            widgets.add(range);
            list = new UiListView<>(theme, font, (g, f, value, index, rect, selected, hovered, focused) ->
                    g.drawString(f, Component.translatable(UI + "list.item", value), rect.x(), rect.y() + 3, theme.palette().textPrimary(), false));
            list.setItems(List.of(1, 2, 3, 4, 5, 6, 7, 8)); widgets.add(list);
            scalar = UiSliderExamples.scalarSlider(text("scalar"), new UiRect(0, 0, 100, 18), new UiValueInteraction.Listener() {
                @Override public void committed(double start, double value) { committed = value; }
            });
            var skin = theme.sliderSkin(UiSliderRole.COMPACT); scalar.setStyle(skin.style()); scalar.setPainter(skin.painter());
            fixed.add(button("reset", this::assemble)); fixed.add(button("modal", this::openModal)); fixed.add(button("back", this::onClose));
            scope = new UiInputScope(new UiInputTarget() {
                @Override public boolean mousePressed(UiInputContext context) {
                    for (UiButton button : fixed) { if (button.mousePressed(context)) { return true; } }
                    if (context.pointerY() < 30 || context.pointerY() >= height - 30) { return false; }
                    if (scalar.mousePressed(context)) { return true; }
                    for (UiWidget widget : widgets) { if (widget.mousePressed(context)) { return true; } }
                    return false;
                }
                @Override public boolean keyPressed(int key, int scan, int mods) {
                    if (textInput.isFocused() && textInput.keyPressed(key, scan, mods)) { return true; }
                    if (number.isFocused() && number.keyPressed(key, scan, mods)) { return true; }
                    return range.keyPressed(key, scan, mods) || scalar.keyPressed(key, scan, mods) || list.keyPressed(key, scan, mods);
                }
                @Override public boolean charTyped(char value, int mods) {
                    return textInput.isFocused() && textInput.charTyped(value, mods) || number.isFocused() && number.charTyped(value, mods) || range.charTyped(value, mods);
                }
            });
            scope.setEscape(this::onClose);
            scope.setFocusTargets(() -> {
                scope.focus().beginUpdate();
                for (UiWidget widget : widgets) { if (widget instanceof UiFocusTarget target) { scope.focus().add(target); } }
                scope.focus().add(scalar); fixed.forEach(scope.focus()::add); scope.focus().endUpdate();
            });
            modals.setOnScopeChanged(this::synchronize); synchronize();
        }
        private void synchronize() {
            UiInputScope active = modals.isEmpty() ? scope : Objects.requireNonNull(modals.top()).inputScope();
            if (active != null) { active.refreshFocus(); input.select(active); }
        }
        private void openModal() {
            modals.push(UiModal.create(theme, font).title(text("modal.title")).message(text("modal.message"))
                    .cancel(text("close")).preferredWidth(Math.min(280, width - 20)).layoutCentered(width, height));
        }
        @Override public void render(@NotNull GuiGraphics graphics, int mx, int my, float delta) {
            renderBackground(graphics, mx, my, delta); theme.drawWindow(graphics, new UiRect(0, 0, width, height));
            modals.setBounds(0, 0, width, height); nodes.clear(); overlayNodes.clear();
            graphics.drawString(font, text(interactions ? "interactions" : "gallery"), 10, 10, theme.palette().textPrimary(), false);
            int w = Math.max(60, width - 24); int y = 34 - scroll;
            graphics.enableScissor(8, 30, width - 8, height - 30);
            try {
                for (UiWidget widget : widgets) { int h = widget == range ? 40 : widget == list ? 72 : 20;
                    widget.setBounds(12, y, w, h);
                    if (y + h > 30 && y < height - 30) {
                        widget.render(graphics, font, modals.isEmpty() ? mx : -1, modals.isEmpty() ? my : -1);
                        if (widget instanceof UiLayoutSource source) {
                            List<UiLayoutNode> children = source.layoutNodes();
                            if (widget == textInput || widget == number) {
                                nodes.add(new UiLayoutNode(UiLayoutNode.Kind.FRAME, widget.bounds(),
                                        text(widget == textInput ? "text" : "number"), 0, 0, true, true, children));
                            } else { nodes.addAll(children); }
                        }
                        if (widget == list) {
                            for (int row = 0; row < list.items().size(); row++) {
                                UiRect bounds = list.visibleRowBounds(row);
                                if (bounds != null && bounds.height() > 0) { addNode(UiLayoutNode.Kind.ROW, bounds, Component.translatable(UI + "list.item", list.items().get(row))); }
                            }
                        }
                    }
                    y += h + 8;
                }
                scalar.setBounds(12, y, w, 20); scalar.render(graphics, font, UiInputContext.pointer(scope, modals.isEmpty() ? mx : -1, modals.isEmpty() ? my : -1, -1, 0));
                addNode(UiLayoutNode.Kind.CONTROL, scalar.bounds(), text("scalar"));
                y += 32;
                Component values = Component.translatable(UI + "values", clicks, textInput.value(), number.value(), selection, scalar.value(), committed, list.selectedIndex());
                graphics.drawString(font, values, 12, y, theme.palette().textPrimary(), false);
                UiRect valuesRect = new UiRect(12, y, w, 12);
                var valueNode = new UiLayoutNode(UiLayoutNode.Kind.ROW, valuesRect, values, font.width(values), w,
                        true, y + 12 > 30 && y < height - 30, List.of());
                nodes.add(new UiLayoutNode(UiLayoutNode.Kind.FRAME, valuesRect, text("values.label"), 0, 0,
                        true, valueNode.visible(), List.of(valueNode)));
            } finally { graphics.disableScissor(); }
            for (int i = 0; i < fixed.size(); i++) {
                var button = fixed.get(i); button.setBounds(8 + i * (width - 16) / 3, height - 24, (width - 24) / 3, 18);
                button.render(graphics, font, mx, my); overlayNodes.addAll(button.layoutNodes());
            }
            modals.render(graphics, font, mx, my); overlayNodes.addAll(modals.layoutNodes()); synchronize();
        }
        private void addNode(UiLayoutNode.Kind kind, UiRect rect, Component label) {
            boolean visible = rect.bottom() > 30 && rect.y() < height - 30;
            nodes.add(new UiLayoutNode(kind, rect, label, font.width(label), rect.width(), true, visible, List.of()));
        }
        @Override public List<UiLayoutNode> layoutNodes() {
            List<UiLayoutNode> snapshot = new ArrayList<>();
            snapshot.add(new UiLayoutNode(UiLayoutNode.Kind.FRAME, new UiRect(8, 30, width - 16, height - 60),
                    text(interactions ? "interactions" : "gallery"), 0, 0, true, true, List.copyOf(nodes)));
            snapshot.addAll(overlayNodes);
            return List.copyOf(snapshot);
        }
        private UiInputContext pointer(double x, double y, int button) { synchronize(); return UiInputContext.of(input.active(), x, y, button, hasShiftDown(), hasControlDown(), hasAltDown()); }
        @Override public boolean mouseClicked(double x, double y, int button) { return input.mousePressed(pointer(x, y, button)); }
        @Override public boolean mouseReleased(double x, double y, int button) { return input.mouseReleased(pointer(x, y, button)); }
        @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return input.mouseDragged(pointer(x, y, button), dx, dy); }
        @Override public boolean mouseScrolled(double x, double y, double sx, double sy) {
            if (!modals.isEmpty()) { return input.mouseScrolled(pointer(x, y, -1), sx, sy); }
            if (list.bounds().contains(x, y) && list.mouseScrolled(pointer(x, y, -1), sx, sy)) { return true; }
            scroll = Math.clamp(scroll - (int)(sy * 24), 0, Math.max(0, 438 - height)); return true;
        }
        @Override public boolean keyPressed(int key, int scan, int mods) {
            if (interactions && key == GLFW.GLFW_KEY_M && (mods & GLFW.GLFW_MOD_CONTROL) != 0) { openModal(); return true; }
            synchronize(); return input.keyPressed(key, scan, mods);
        }
        @Override public boolean keyReleased(int key, int scan, int mods) { return input.keyReleased(key, scan, mods); }
        @Override public boolean charTyped(char value, int mods) { return input.charTyped(value, mods); }
        @Override public void tick() { modals.tick(); }
        @Override public void removed() { input.close(); modals.clear(); }
    }
}
