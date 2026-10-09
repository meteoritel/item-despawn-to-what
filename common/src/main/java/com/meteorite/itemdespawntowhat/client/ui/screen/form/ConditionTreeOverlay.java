package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.google.gson.JsonElement;
import com.meteorite.itemdespawntowhat.client.edit.ConditionTreeNodes;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.OpaqueCondition;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.screen.RuleEditorP4Panels;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiConditionTreeEditor;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** 单个条件表达式的大屏编辑作用域，已接受值写共享草稿，未完成文本留在会话。 */
public final class ConditionTreeOverlay implements UiWidget, UiFocusTarget {
    private static final String UI = "gui.itemdespawntowhat.edit.tree.";
    private static final int GAP = 6;
    private static final int MIN_PANE_WIDTH = 180;
    private static final int TOOL_HEIGHT = 18;
    private static final int STATUS_HEIGHT = 22;
    private static final int TOOLTIP_MIN_WIDTH = 80;
    private static final int TOOLTIP_MAX_WIDTH = 250;

    public record Parameters(FormView form, RuleEditorP4Panels.LeafPanel panel, Component title) {}
    @FunctionalInterface
    public interface ParameterFactory {
        Parameters create(String conditionPath, ConditionNode.Leaf leaf);
    }

    private final Font font;
    private final EditSession session;
    private final String scope;
    private final ConditionSupport support;
    private final ParameterFactory parameterFactory;
    private final Runnable changed;
    private final UiConditionTreeEditor editor;
    private final List<UiButton> buttons = new ArrayList<>();
    private final UiButton parametersButton;
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private UiRect parameterRect = bounds;
    private @Nullable Parameters parameters;
    private @Nullable String parameterPath;
    private @Nullable String parameterType;
    private @Nullable String selectedPath;
    private @Nullable UiWidget pressed;
    private int toolbarHeight;
    private boolean wide;
    private boolean showParameters;
    private boolean focused;
    private boolean viewChanged;
    private long revision;

    public ConditionTreeOverlay(Font font, EditSession session, String scope, ConditionSupport support,
                                ParameterFactory parameterFactory, Runnable changed) {
        this.font = font;
        this.session = session;
        this.scope = scope;
        this.support = support;
        this.parameterFactory = parameterFactory;
        this.changed = changed;
        editor = new UiConditionTreeEditor(font).setTypeOptions(support.typeOptions()).setLeafFactory(support.leafFactory());
        editor.setLeafSummary(leaf -> {
            JsonElement raw = RuleDraft.encodeConditions(new ConditionExpression(leaf), support.registry());
            return raw != null && raw.isJsonObject() && raw.getAsJsonObject().get(RuleFields.CONDITION) != null
                    ? NaturalSummary.conditionLeaf(raw.getAsJsonObject().getAsJsonObject(RuleFields.CONDITION))
                    : com.meteorite.itemdespawntowhat.client.edit.TypeLabels.conditionLabel(leaf.condition().type());
        });
        editor.setOnEditLeaf(leaf -> {
            syncSelection();
            showParameters = true;
            layout();
            if (parameters != null) parameters.panel().setFocused(true);
        });
        editor.setBeforeEdit(() -> {
            finishParameters();
            reloadExpression();
            unmountParameters();
        });
        editor.setListener(expression -> {
            var change = editor.lastChange();
            JsonElement next = RuleDraft.encodeConditions(expression, support.registry());
            session.apply(editor.undoOpKey(), () -> {
                if (change != null) ConditionTreeNodes.remapInputs(session, change.before(), change.after(), scope);
                if (next == null) session.draft().removeAt(scope); else session.draft().setAt(scope, next);
            });
            revision = session.revision();
            changed.run();
        });
        addButton("add", editor::beginAddCondition);
        addButton("all", () -> editor.addGroup(true));
        addButton("any", () -> editor.addGroup(false));
        addButton("not", editor::wrapSelectedInNot);
        addButton("remove", editor::deleteSelected);
        addButton("up", () -> editor.moveSelected(-1));
        addButton("down", () -> editor.moveSelected(1));
        addButton("expand", editor::expandAll);
        addButton("collapse", editor::collapseAll);
        parametersButton = addButton("parameters", () -> {
            finishParameters();
            showParameters = !showParameters;
            if (!showParameters && parameters != null) parameters.panel().setFocused(false);
            layout();
        });
        editor.tree().setOnViewChanged(view -> viewChanged = true);
        reloadExpression();
    }

    public UiConditionTreeEditor editor() { return editor; }
    public EditSession session() { return session; }
    public String scope() { return scope; }

    private UiButton addButton(String name, Runnable action) {
        UiButton button = new UiButton(font, Component.translatable(UI + "button." + name), UiButtonVariant.SECONDARY, () -> {
            action.run();
            syncSelection();
            layout();
        });
        buttons.add(button);
        return button;
    }

    private void reloadExpression() {
        ConditionExpression expression = RuleDraft.decodeConditions(session.draft().getAt(scope), support.registry());
        if (expression != null) editor.setExpression(expression);
        revision = session.revision();
    }

    private void finishParameters() {
        JsonElement current = parameterPath == null ? null : session.draft().getAt(parameterPath);
        if (parameters != null && current != null && current.isJsonObject()
                && current.getAsJsonObject().get(RuleFields.TYPE) != null
                && Objects.equals(parameterType, current.getAsJsonObject().get(RuleFields.TYPE).getAsString())
                && parameters.form().commitPendingInputs()) changed.run();
    }

    private void unmountParameters() {
        if (parameters != null) parameters.panel().unmount();
        parameters = null;
        parameterPath = null;
        parameterType = null;
    }

    private void syncSelection() {
        viewChanged = false;
        String next = editor.selectedPath();
        if (Objects.equals(selectedPath, next) && (parameters != null || !(editor.selectedNode() instanceof ConditionNode.Leaf))) return;
        finishParameters();
        unmountParameters();
        selectedPath = next;
        ConditionNode node = editor.selectedNode();
        if (node instanceof ConditionNode.Leaf leaf && leaf.condition() != null && !(leaf.condition() instanceof OpaqueCondition)) {
            parameterPath = scope + next.substring(UiConditionTreeEditor.ROOT_PATH.length()) + "." + RuleFields.CONDITION;
            parameterType = leaf.condition().type().toString();
            parameters = parameterFactory.create(parameterPath, leaf);
        }
        layout();
    }

    /** 历史回填读取当前草稿，不把旧面板值重新提交为配置。 */
    public void historyChanged() {
        unmountParameters();
        reloadExpression();
        selectedPath = null;
        syncSelection();
    }

    /** 返回或被卸载时完成合法字段，保留非法字段并结束捕获。 */
    public void unmount() {
        finishParameters();
        discardView();
    }

    // 历史使外层作用域失效时只卸载旧视图，不再提交该路径的控件值。
    public void discardView() {
        unmountParameters();
        pressed = null;
        editor.closePicker();
        editor.setFocused(false);
    }

    public void reveal(String fieldPath) {
        editor.selectPath(UiConditionTreeEditor.ROOT_PATH + fieldPath.substring(scope.length()));
        syncSelection();
        if (parameters != null) {
            showParameters = true;
            layout();
            parameters.panel().focusField(fieldPath);
        }
    }

    @Override public UiRect bounds() { return bounds; }
    @Override public void setBounds(int x, int y, int width, int height) {
        bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        layout();
    }

    private void layout() {
        wide = bounds.width() >= MIN_PANE_WIDTH * 2 + GAP;
        parametersButton.setVisible(!wide);
        int x = bounds.x();
        int y = bounds.y();
        for (UiButton button : buttons) {
            if (!button.isVisible()) continue;
            int width = Math.min(bounds.width(), button.preferredWidth(4));
            if (x > bounds.x() && x + width > bounds.right()) { x = bounds.x(); y += TOOL_HEIGHT + GAP; }
            button.setBounds(x, y, width, TOOL_HEIGHT);
            x += width + GAP;
        }
        toolbarHeight = y - bounds.y() + TOOL_HEIGHT + GAP;
        int bodyY = bounds.y() + toolbarHeight;
        int height = Math.max(0, bounds.height() - toolbarHeight - STATUS_HEIGHT);
        int treeWidth = wide ? (bounds.width() - GAP) / 2 : bounds.width();
        editor.setVisible(wide || !showParameters);
        editor.setBounds(bounds.x(), bodyY, treeWidth, height);
        parameterRect = new UiRect(wide ? bounds.x() + treeWidth + GAP : bounds.x(), bodyY,
                wide ? bounds.width() - treeWidth - GAP : bounds.width(), height);
        if (parameters != null) parameters.panel().setBounds(parameterRect.x(), parameterRect.y() + TOOL_HEIGHT,
                parameterRect.width(), Math.max(0, parameterRect.height() - TOOL_HEIGHT));
    }

    @Override public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        if (parameters != null) parameters.form().tick();
        if (revision != session.revision()) reloadExpression();
        if (viewChanged || !Objects.equals(selectedPath, editor.selectedPath())
                || parameters == null && editor.selectedNode() instanceof ConditionNode.Leaf) syncSelection();
        for (UiButton button : buttons) if (button.isVisible()) button.render(graphics, renderFont, mouseX, mouseY);
        if (editor.isVisible()) editor.render(graphics, renderFont, mouseX, mouseY);
        if (wide || showParameters) {
            UiTheme.drawInset(graphics, parameterRect);
            Component title = parameters == null ? Component.translatable(UI + (editor.selectedNode() instanceof ConditionNode.Leaf leaf
                    && leaf.condition() instanceof OpaqueCondition ? "parameters_readonly" : "select_node")) : parameters.title();
            graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont, title.getString(), Math.max(0, parameterRect.width() - 4)),
                    parameterRect.x() + 2, parameterRect.y() + 3, UiPalette.TEXT_PRIMARY, false);
            if (parameters != null) parameters.panel().render(graphics, renderFont, mouseX, mouseY);
        }
        Component status = editor.operationError();
        if (status == null && !editor.issues().isEmpty()) status = Component.translatable(UI + "issues",
                editor.issues().size(), editor.issues().getFirst().label());
        if (status == null && session.hasPendingInput(scope)) status = Component.translatable(UI + "pending_input");
        if (status == null) status = Component.translatable(UI + "counts", editor.nodeCount(), editor.leafCount(), editor.depth());
        graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont, status.getString(), Math.max(0, bounds.width() - 4)),
                bounds.x() + 2, bounds.bottom() - STATUS_HEIGHT + 4,
                editor.isValid() && editor.operationError() == null ? UiPalette.TEXT_SECONDARY : UiPalette.DANGER, false);
        Component tip = parameters != null && (wide || showParameters) ? parameters.form().tooltipAt(mouseX, mouseY) : null;
        if (tip == null && editor.isVisible()) tip = editor.tooltipAt(mouseX, mouseY);
        if (tip != null) graphics.renderTooltip(renderFont, renderFont.split(tip,
                Math.max(TOOLTIP_MIN_WIDTH, Math.min(TOOLTIP_MAX_WIDTH, bounds.width()))), mouseX, mouseY);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        pressed = null;
        if (y >= bounds.bottom() - STATUS_HEIGHT && bounds.contains(x, y) && !editor.issues().isEmpty()) {
            editor.selectPath(editor.issues().getFirst().path());
            syncSelection();
            return true;
        }
        for (UiButton target : buttons) if (target.isVisible() && target.mouseClicked(x, y, button)) {
            if (parameters != null) parameters.panel().setFocused(false);
            pressed = target;
            return true;
        }
        if (parameters != null && (wide || showParameters) && parameterRect.contains(x, y)) {
            parameters.panel().setFocused(true);
            if (parameters.panel().mouseClicked(x, y, button)) {
                editor.setFocused(false);
                pressed = parameters.panel();
                return true;
            }
        }
        if (editor.isVisible() && editor.mouseClicked(x, y, button)) {
            editor.setFocused(true);
            if (parameters != null) parameters.panel().setFocused(false);
            pressed = editor;
            syncSelection();
            return true;
        }
        return false;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        UiWidget target = pressed;
        pressed = null;
        return target != null && target.mouseReleased(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return pressed != null && pressed.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (parameters != null && (wide || showParameters) && parameterRect.contains(x, y)) return parameters.panel().mouseScrolled(x, y, dx, dy);
        return editor.isVisible() && editor.mouseScrolled(x, y, dx, dy);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (parameters != null && (wide || showParameters) && parameters.panel().isFocused()
                && parameters.panel().keyPressed(key, scan, modifiers)) return true;
        if (key == GLFW.GLFW_KEY_TAB) {
            if (parameters != null && (wide || showParameters) && editor.isFocused()) {
                editor.setFocused(false); parameters.panel().setFocused(true);
            } else { if (parameters != null) parameters.panel().setFocused(false); editor.setFocused(true); }
            return true;
        }
        boolean consumed = editor.isVisible() && editor.isFocused() && editor.keyPressed(key, scan, modifiers);
        if (consumed) syncSelection();
        return consumed;
    }
    @Override public boolean keyReleased(int key, int scan, int modifiers) {
        if (parameters != null && (wide || showParameters) && parameters.panel().isFocused()) return parameters.panel().keyReleased(key, scan, modifiers);
        return editor.isVisible() && editor.keyReleased(key, scan, modifiers);
    }
    @Override public boolean charTyped(char codePoint, int modifiers) {
        return parameters != null && (wide || showParameters) && parameters.panel().isFocused()
                && parameters.panel().charTyped(codePoint, modifiers);
    }
    @Override public boolean canFocus() { return true; }
    @Override public boolean isFocused() { return focused; }
    @Override public void setFocused(boolean value) {
        focused = value;
        editor.setFocused(value && editor.isVisible());
        if (parameters != null) parameters.panel().setFocused(false);
    }
    @Override public boolean activate() { return false; }
    @Override public Component accessibleName() { return Component.translatable(UI + "title"); }
}
