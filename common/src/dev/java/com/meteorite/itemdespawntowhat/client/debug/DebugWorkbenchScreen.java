package com.meteorite.itemdespawntowhat.client.debug;

import com.meteorite.itemdespawntowhat.client.debug.kit.KitShowcaseScreen;
import com.meteorite.itemdespawntowhat.client.debug.kit.UiSliderExamples;
import com.meteorite.itemdespawntowhat.client.debug.kit.WorkbenchLayout;
import com.meteorite.itemdespawntowhat.client.ui.shared.PreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiMinecraftTheme;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputRouter;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputScope;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiNumberPolicy;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiRangeValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiScalarSlider;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiSliderRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiValueInteraction;
import com.meteorite.itemdespawntowhat.client.ui.screen.RuleEditorScreen;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiListView;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiModal;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiModalStack;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiRangeBar;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiTextInput;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
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

    /** 工具目录：每个工具一行，按钮右侧跟一句用途说明。 */
    private static final class Directory extends DebugWorkbenchScreen implements UiLayoutSource {
        private static final int TOOL_BUTTON_WIDTH = 200;
        private static final int TOOL_ROW_HEIGHT = 26;
        private final List<UiButton> buttons = new ArrayList<>();
        private final List<Tool> tools;
        private final UiInputRouter input = new UiInputRouter();
        private final UiInputScope scope;
        private final UiHostTheme theme = UiHostThemes.defaultTheme();
        Directory(Runnable back) {
            super(back);
            Runnable home = () -> Minecraft.getInstance().setScreen(new Directory(back));
            tools = List.of(
                    new Tool("kit_showcase", () -> true, () -> Minecraft.getInstance().setScreen(new KitShowcaseScreen(home, List.of(UiMinecraftTheme.INSTANCE, theme, UiHostThemes.flatTheme()),
                            Minecraft.getInstance().level == null ? null : PreviewIcons::createScenes))),
                    new Tool("gallery", () -> true, () -> Minecraft.getInstance().setScreen(new Gallery(home, false))),
                    new Tool("interactions", () -> true, () -> Minecraft.getInstance().setScreen(new Gallery(home, true))),
                    new Tool("previews", () -> Minecraft.getInstance().level != null, () -> Minecraft.getInstance().setScreen(new PreviewExamplesScreen(home))),
                    new Tool("drafts", () -> RuleEditClientWorkspace.instance().session().editable(), () -> {
                        var screen = new RuleEditorScreen(RuleEditClientWorkspace.instance());
                        Minecraft.getInstance().setScreen(screen);
                        screen.showDebugDrafts();
                    }));
            scope = new UiInputScope(new UiInputTarget() {
                @Override public void addFocusTargets(Consumer<UiFocusTarget> out) { buttons.forEach(out); }
                @Override public UiPointerHit hitTest(double x, double y) {
                    return UiPointerHit.topmost(x, y, buttons);
                }
                @Override public boolean mousePressed(UiInputContext context) {
                    for (UiButton button : buttons) { if (button.mousePressed(context)) { return true; } }
                    return false;
                }
            });
            scope.setEscape(this::onClose);
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
            WorkbenchLayout.drawPage(graphics, font, theme, width, height, text("title"));
            WorkbenchLayout.drawSubtitle(graphics, font, theme, width, text("description"));
            int contentWidth = WorkbenchLayout.contentWidth(width);
            int buttonWidth = Math.min(TOOL_BUTTON_WIDTH, Math.max(60, contentWidth - 40));
            int top = WorkbenchLayout.CONTENT_TOP;
            for (int i = 0; i < tools.size(); i++) {
                var tool = tools.get(i); var button = buttons.get(i);
                button.setEnabled(tool.available().getAsBoolean());
                button.setBounds(WorkbenchLayout.INSET, top + i * TOOL_ROW_HEIGHT, buttonWidth, WorkbenchLayout.CONTROL_HEIGHT);
            }
            var back = buttons.getLast();
            WorkbenchLayout.placeFooterBack(back, height);
            WorkbenchLayout.drawFooterRule(graphics, theme, width, height);
            var pointer = scope.pointer(x, y).resolve(scope.content());
            int textY = (WorkbenchLayout.CONTROL_HEIGHT - font.lineHeight) / 2;
            int descriptionWidth = contentWidth - buttonWidth - 12;
            for (int i = 0; i < tools.size(); i++) {
                var tool = tools.get(i); var button = buttons.get(i);
                button.render(graphics, font, pointer);
                var description = WorkbenchLayout.ellipsize(font, text(tool.id() + ".description"), descriptionWidth);
                graphics.drawString(font, description, WorkbenchLayout.INSET + buttonWidth + 12,
                        top + i * TOOL_ROW_HEIGHT + textY, theme.palette().textSecondary(), false);
            }
            back.render(graphics, font, pointer);
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
    /** 本地组件场景：控件、滚动与模态演示，说明文字随内容一起滚动。 */
    private static final class Gallery extends DebugWorkbenchScreen implements UiLayoutSource {
        private static final int VALUES_MAX_LINES = 3;
        private final UiHostTheme theme = UiHostThemes.defaultTheme();
        private final UiInputRouter input = new UiInputRouter();
        private final UiModalStack modals = new UiModalStack(theme);
        private final List<UiWidget> widgets = new ArrayList<>();
        private final List<UiButton> fixed = new ArrayList<>();
        private final List<UiLayoutNode> nodes = new ArrayList<>();
        private final List<UiLayoutNode> overlayNodes = new ArrayList<>();
        private final List<Caption> captions = new ArrayList<>();
        private UiInputScope scope;
        private UiButton increment;
        private UiButton disabledButton;
        private UiButton selectionButton;
        private UiTextInput textInput;
        private UiTextInput number;
        private UiRangeBar range;
        private UiScalarSlider scalar;
        private UiListView<Integer> list;
        private int clicks;
        private int selection;
        private int scroll;
        private int scrollMax;
        private int contentHeight;
        private int valuesTop;
        private Component values = Component.empty();
        private List<String> valuesLines = List.of();
        private double committed = 0.37;
        private boolean initialized;
        private final boolean interactions;

        private record Caption(Component text, int x, int y) {}

        Gallery(Runnable back, boolean interactions) { super(back); this.interactions = interactions; }
        @Override protected void init() {
            modals.setBounds(0, 0, width, height);
            if (initialized) { return; }
            initialized = true;
            assemble();
        }
        private UiButton button(String key, Runnable action) { return new UiButton(theme, font, text(key), UiButtonVariant.SECONDARY, action::run); }
        private void assemble() {
            input.close(); modals.clear(); widgets.clear(); fixed.clear(); captions.clear();
            clicks = 0; selection = 0; committed = 0.37; scroll = 0;
            increment = button("increment", () -> clicks++);
            disabledButton = button("disabled", () -> clicks++); disabledButton.setEnabled(false);
            textInput = new UiTextInput(theme, font, text("text")); textInput.setValue("debug");
            number = new UiTextInput(theme, font, text("number")); number.setValue("12");
            number.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
            selectionButton = button("selection", () -> selection = (selection + 1) % 3);
            range = new UiRangeBar(theme, font, UiNumberPolicy.integer(0, 100, 1), UiRangeValue.of(20, 80),
                    new UiRangeBar.Texts(text("low"), text("high"), text("absent"), text("precise")), value -> {});
            list = new UiListView<>(theme, (g, f, value, index, rect, selected, hovered, focused) ->
                    g.drawString(f, Component.translatable(UI + "list.item", value), rect.x(), rect.y() + 3, theme.palette().textPrimary(), false));
            list.setItems(List.of(1, 2, 3, 4, 5, 6, 7, 8));
            scalar = UiSliderExamples.scalarSlider(text("scalar"), new UiRect(0, 0, 100, 18), new UiValueInteraction.Listener<>() {
                @Override public void committed(Double start, Double value) { committed = value; }
            });
            var skin = theme.sliderSkin(UiSliderRole.COMPACT); scalar.setStyle(skin.style()); scalar.setPainter(skin.painter());
            widgets.addAll(List.of(increment, disabledButton, textInput, number, selectionButton, range, list));
            fixed.add(button("reset", this::assemble)); fixed.add(button("modal", this::openModal)); fixed.add(button("back", this::onClose));
            scope = new UiInputScope(new UiInputTarget() {
                @Override public void addFocusTargets(Consumer<UiFocusTarget> out) {
                    for (UiWidget widget : widgets) widget.addFocusTargets(out);
                    scalar.addFocusTargets(out);
                    fixed.forEach(out);
                }
                @Override public UiPointerHit hitTest(double x, double y) {
                    UiPointerHit hit = UiPointerHit.topmost(x, y, fixed);
                    if (hit != null) return hit;
                    if (y < WorkbenchLayout.CONTENT_TOP || y >= WorkbenchLayout.contentBottom(height)) return null;
                    hit = scalar.hitTest(x, y);
                    return hit != null ? hit : UiPointerHit.topmost(x, y, widgets);
                }
                @Override public boolean mousePressed(UiInputContext context) {
                    for (UiButton button : fixed) { if (button.mousePressed(context)) { return true; } }
                    if (context.pointerY() < WorkbenchLayout.CONTENT_TOP || context.pointerY() >= WorkbenchLayout.contentBottom(height)) { return false; }
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
        /**
         * 按当前滚动量排版一屏内容，返回内容总高度。
         *
         * <p>说明文字与控件一起参与滚动，因此总高度与滚动位置无关，可用于计算滚动上限。</p>
         */
        private int layoutContent(int contentWidth, int extraListHeight) {
            int start = WorkbenchLayout.CONTENT_TOP - scroll;
            int y = start;
            int gap = WorkbenchLayout.GAP;
            int half = Math.max(40, (contentWidth - gap) / 2);
            increment.setBounds(WorkbenchLayout.INSET, y, half, WorkbenchLayout.CONTROL_HEIGHT);
            disabledButton.setBounds(WorkbenchLayout.INSET + half + gap, y, contentWidth - half - gap, WorkbenchLayout.CONTROL_HEIGHT);
            y += WorkbenchLayout.CONTROL_HEIGHT + gap;
            captions.clear();
            y = captionRow("text", textInput, y, 20, contentWidth);
            y = captionRow("number", number, y, 20, contentWidth);
            y = captionRow("selection", selectionButton, y, 20, contentWidth);
            y = captionRow("range", range, y, 40, contentWidth);
            y = captionRow("list", list, y, 72 + extraListHeight, contentWidth);
            y += WorkbenchLayout.GROUP_GAP - gap;
            scalar.setBounds(WorkbenchLayout.INSET, y, contentWidth, WorkbenchLayout.CONTROL_HEIGHT);
            y += WorkbenchLayout.CONTROL_HEIGHT + WorkbenchLayout.GROUP_GAP;
            valuesTop = y;
            values = Component.translatable(UI + "values", clicks, textInput.value(), number.value(), selection, scalar.value(), committed, list.selectedIndex());
            valuesLines = WorkbenchLayout.wrapLines(font, values.getString(), contentWidth, VALUES_MAX_LINES);
            y += font.lineHeight + 2 + valuesLines.size() * (font.lineHeight + 2);
            contentHeight = y - start;
            return contentHeight;
        }
        private int captionRow(String key, UiWidget widget, int y, int height, int contentWidth) {
            captions.add(new Caption(text(key), WorkbenchLayout.INSET, y));
            int top = y + font.lineHeight + 2;
            widget.setBounds(WorkbenchLayout.INSET, top, contentWidth, height);
            return top + height + WorkbenchLayout.GAP;
        }
        private static boolean inViewport(UiRect rect, int top, int bottom) {
            return rect.bottom() > top && rect.y() < bottom;
        }
        @Override public void render(@NotNull GuiGraphics graphics, int mx, int my, float delta) {
            renderBackground(graphics, mx, my, delta);
            nodes.clear(); overlayNodes.clear();
            modals.setBounds(0, 0, width, height);
            WorkbenchLayout.drawPage(graphics, font, theme, width, height, text(interactions ? "interactions" : "gallery"));
            WorkbenchLayout.drawSubtitle(graphics, font, theme, width, text(interactions ? "interactions.description" : "gallery.description"));
            int contentWidth = WorkbenchLayout.contentWidth(width);
            int top = WorkbenchLayout.CONTENT_TOP;
            int bottom = WorkbenchLayout.contentBottom(height);
            int viewport = Math.max(0, bottom - top);
            layoutContent(contentWidth, 0);
            scrollMax = Math.max(0, contentHeight - viewport);
            scroll = Math.clamp(scroll, 0, scrollMax);
            // 内容不足一屏时把余量交给列表，避免页面下半部留空；有滚动余量时保持控件自然高度。
            layoutContent(contentWidth, scrollMax == 0 ? Math.max(0, viewport - contentHeight) : 0);
            int footerTop = WorkbenchLayout.footerTop(height);
            int fixedWidth = Math.max(50, (contentWidth - WorkbenchLayout.GAP * 2) / 3);
            for (int i = 0; i < fixed.size(); i++) {
                fixed.get(i).setBounds(WorkbenchLayout.INSET + i * (fixedWidth + WorkbenchLayout.GAP), footerTop, fixedWidth, WorkbenchLayout.CONTROL_HEIGHT);
            }
            WorkbenchLayout.drawFooterRule(graphics, theme, width, height);
            var pointer = scope.pointer(mx, my).resolve(scope.content());
            boolean scissored = viewport > 0;
            if (scissored) { graphics.enableScissor(WorkbenchLayout.INSET, top, width - WorkbenchLayout.INSET, bottom); }
            try {
                for (Caption caption : captions) {
                    if (inViewport(new UiRect(caption.x(), caption.y(), 1, font.lineHeight), top, bottom)) {
                        graphics.drawString(font, caption.text(), caption.x(), caption.y(), theme.palette().textSecondary(), false);
                    }
                }
                for (UiWidget widget : widgets) {
                    if (!inViewport(widget.bounds(), top, bottom)) { continue; }
                    widget.render(graphics, font, pointer);
                    emitNodes(widget);
                }
                if (inViewport(scalar.bounds(), top, bottom)) {
                    scalar.render(graphics, font, pointer);
                    addNode(UiLayoutNode.Kind.CONTROL, scalar.bounds(), text("scalar"));
                }
                graphics.drawString(font, text("values.label"), WorkbenchLayout.INSET, valuesTop, theme.palette().textSecondary(), false);
                int lineY = valuesTop + font.lineHeight + 2;
                for (String line : valuesLines) {
                    graphics.drawString(font, line, WorkbenchLayout.INSET, lineY, theme.palette().textPrimary(), false);
                    lineY += font.lineHeight + 2;
                }
                int valuesHeight = font.lineHeight + 2 + valuesLines.size() * (font.lineHeight + 2);
                UiRect valuesRect = new UiRect(WorkbenchLayout.INSET, valuesTop, contentWidth, valuesHeight);
                boolean valuesVisible = inViewport(valuesRect, top, bottom);
                var valueNode = new UiLayoutNode(UiLayoutNode.Kind.ROW, valuesRect, values, font.width(values), contentWidth, true, valuesVisible, List.of());
                nodes.add(new UiLayoutNode(UiLayoutNode.Kind.FRAME, valuesRect, text("values.label"), 0, 0, true, valuesVisible, List.of(valueNode)));
            } finally {
                if (scissored) { graphics.disableScissor(); }
            }
            for (UiButton button : fixed) { button.render(graphics, font, pointer); overlayNodes.addAll(button.layoutNodes()); }
            modals.render(graphics, font, scope.pointer(mx, my)); overlayNodes.addAll(modals.layoutNodes()); synchronize();
        }
        private void emitNodes(UiWidget widget) {
            if (widget instanceof UiLayoutSource source) {
                List<UiLayoutNode> children = source.layoutNodes();
                if (widget == textInput || widget == number) {
                    nodes.add(new UiLayoutNode(UiLayoutNode.Kind.FRAME, widget.bounds(), text(widget == textInput ? "text" : "number"), 0, 0, true, true, children));
                } else { nodes.addAll(children); }
            }
            if (widget == list) {
                for (int row = 0; row < list.items().size(); row++) {
                    UiRect bounds = list.visibleRowBounds(row);
                    if (bounds != null && bounds.height() > 0) { addNode(UiLayoutNode.Kind.ROW, bounds, Component.translatable(UI + "list.item", list.items().get(row))); }
                }
            }
        }
        private void addNode(UiLayoutNode.Kind kind, UiRect rect, Component label) {
            int top = WorkbenchLayout.CONTENT_TOP;
            int bottom = WorkbenchLayout.contentBottom(height);
            nodes.add(new UiLayoutNode(kind, rect, label, font.width(label), rect.width(), true, inViewport(rect, top, bottom), List.of()));
        }
        @Override public List<UiLayoutNode> layoutNodes() {
            List<UiLayoutNode> snapshot = new ArrayList<>();
            snapshot.add(new UiLayoutNode(UiLayoutNode.Kind.FRAME,
                    new UiRect(WorkbenchLayout.INSET, WorkbenchLayout.CONTENT_TOP, WorkbenchLayout.contentWidth(width), WorkbenchLayout.contentHeight(height)),
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
            scroll = Math.clamp(scroll - (int)(sy * 24), 0, scrollMax); return true;
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