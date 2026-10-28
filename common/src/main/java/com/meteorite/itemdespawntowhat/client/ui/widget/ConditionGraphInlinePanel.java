package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;

import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphSidePanel.SIDE_PANEL_HEADER;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphSidePanel.SIDE_PANEL_PADDING;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTransform;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreePath;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiConditionGraphEditor.InlineEditor;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 条件叶参数面板：按路径维护锚点，负责动画、表单几何与输入捕获。
 */
final class ConditionGraphInlinePanel {
    // 参数框锚点用于校验所属叶；面板几何只依赖画布边界与滑入进度。
    private @Nullable InlineEditor form;
    private @Nullable UiTreePath anchor;
    private boolean closing;
    private final ConditionGraphSidePanel panel;
    private UiRect panelRect = new UiRect(0, 0, 0, 0);
    private Component title = Component.translatable(TypeLabels.UI_PREFIX + "diagram.leaf");

    private final ConditionGraphLayout graph;
    private UiRect bounds = new UiRect(0, 0, 0, 0);

    ConditionGraphInlinePanel(UiHostTheme theme, Font font, ConditionGraphLayout graph) {
        this.graph = graph;
        panel = new ConditionGraphSidePanel(theme, font);
    }

    void setBounds(UiRect bounds) {
        this.bounds = bounds;
    }

    void mousePressed(UiInputContext context) {
        if (form != null && !closing) form.mousePressed(context);
    }

    void mouseScrolled(UiInputContext context, double scrollX, double scrollY) {
        if (form != null && !closing) form.mouseScrolled(context, scrollX, scrollY);
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (form == null || closing) return false;
        if (form.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return false;
    }

    boolean charTyped(char codePoint, int modifiers) {
        return form != null && !closing && form.charTyped(codePoint, modifiers);
    }

    void mount(@Nullable InlineEditor editor, @Nullable UiTreePath anchorPath) {
        if (editor == null || anchorPath == null) {
            close();
            return;
        }
        if (editor == form && anchorPath.equals(anchor) && !closing) {
            return;
        }
        // 只有锚点变化才从头展开；同一锚点换实例时保留当前展开度，避免宿主每帧新建适配器导致动画永远重来
        boolean restart = !anchorPath.equals(anchor);
        form = editor;
        anchor = anchorPath;
        closing = false;
        if (restart) {
            panel.reset();
        }
        panel.start(1.0F);
    }

    void close() {
        if (form == null || closing) {
            return;
        }
        panel.tick();
        if (panel.isClosed()) {
            releaseForm();
            return;
        }
        closing = true;
        panel.start(0.0F);
    }

    boolean isOpen() {
        return form != null;
    }

    // 参数框内文本输入正在编辑：画布快捷键让位
    boolean capturesKeyboard() {
        InlineEditor editor = form;
        return editor != null && !closing && editor.capturesKeyboard();
    }

    private void tickAnimation() {
        if (form == null) {
            return;
        }
        panel.tick();
        if (closing && panel.isClosed()) {
            releaseForm();
        }
    }

    private void releaseForm() {
        form = null;
        anchor = null;
        closing = false;
        panel.reset();
        panelRect = new UiRect(0, 0, 0, 0);
    }

    void validateAnchor() {
        if (form == null || anchor == null) {
            return;
        }
        // 参数提交会重建节点对象；按路径和类型校验，避免每次输入后误关表单。
        if (graph.nodeAtPath(anchor) instanceof ConditionNode.Leaf) {
            return;
        }
        close();
    }

    private void updateBounds() {
        tickAnimation();
        InlineEditor editor = form;
        if (editor == null) {
            return;
        }
        ConditionNode anchorNode = anchor == null ? null : graph.nodeAtPath(anchor);
        if (!(anchorNode instanceof ConditionNode.Leaf)) {
            close();
        } else {
            title = graph.titleLabel(anchorNode);
        }
        if (form == null) return;
        panelRect = panel.bounds(bounds);
        int width = Math.max(0, panelRect.width() - SIDE_PANEL_PADDING * 2);
        int availableHeight = Math.max(0, panelRect.height() - SIDE_PANEL_HEADER - SIDE_PANEL_PADDING * 2);
        // 全高侧面板直接分配内容区，不做临时大视口测量，保留长表单的滚动位置。
        editor.setBounds(panelRect.x() + SIDE_PANEL_PADDING,
                panelRect.y() + SIDE_PANEL_HEADER + SIDE_PANEL_PADDING, width, availableHeight);
    }

    void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        updateBounds();
        InlineEditor editor = form;
        if (editor == null || !editor.isVisible()) {
            return;
        }
        if (panelRect.width() <= 0 || panelRect.height() <= 0) {
            return;
        }
        panel.render(graphics, bounds, title, () -> {
            UiTransform.enableScissor(graphics, editor.bounds());
            try {
                boolean interactive = !closing && bounds.contains(mouseX, mouseY)
                        && panelRect.contains(mouseX, mouseY);
                editor.render(graphics, renderFont, interactive ? mouseX : Integer.MIN_VALUE,
                        interactive ? mouseY : Integer.MIN_VALUE);
            } finally {
                UiTransform.disableScissor(graphics);
            }
        });
    }

    boolean contains(double screenX, double screenY) {
        if (form == null) {
            return false;
        }
        updateBounds();
        InlineEditor editor = form;
        if (editor == null || !editor.isVisible()) {
            return false;
        }
        return bounds.contains(screenX, screenY) && panelRect.contains(screenX, screenY);
    }

}
