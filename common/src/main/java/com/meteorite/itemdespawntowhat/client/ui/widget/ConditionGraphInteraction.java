package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.edit.ConditionTreeNodes;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiTransform;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiRubberBand;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.tree.UiTreeEditor;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.tree.UiTreeMoveInteraction;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.tree.UiTreePath;
import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.Hotspot;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.CARD_HEIGHT;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.isContainer;
import static com.meteorite.itemdespawntowhat.client.ui.widget.UiConditionGraphEditor.termsOf;

/**
 * 画布手势与键盘导航；捕获只登记在当前 UiInteractionSession，树选择与移动基准沿用 Kit。
 */
final class ConditionGraphInteraction {
    record Actions(Consumer<Hotspot> openPicker, Runnable editLeaf, Consumer<Component> error,
                   Consumer<UiTreeEditor.Outcome> moveEnded) {
    }

    private final UiTreeEditor<ConditionNode> tree;
    private final ConditionGraphLayout graph;
    private final UiTransform transform;
    private final Actions actions;
    private final UiTreeMoveInteraction<ConditionNode> move;
    private final UiRubberBand rubberBand = new UiRubberBand();
    private static final int REVEAL_MARGIN = 24;
    private static final int DRAG_START_DISTANCE = 4;
    private static final int DROP_EDGE_DIVISOR = 4;

    // 手势状态（世界坐标，缩放与平移无关）
    private @Nullable UiTreePath armedDrag;
    private @Nullable UiTreePath clickedLeafPath;
    private boolean leafEditArmed;
    private double pressScreenX;
    private double pressScreenY;
    private boolean collapseSelectionOnRelease;
    private @Nullable String dragLabel;
    private @Nullable ConditionNode dropTarget;
    private boolean rubberActive;
    private Set<UiTreePath> rubberBase = Set.of();
    private boolean panning;
    private @Nullable UiInteractionSession interaction;
    private double panLastX;
    private double panLastY;
    // 空白区左键按下：释放时若没有产生位移，按空白点击处理（清空选择）
    private boolean panPressedBlank;
    private boolean panMoved;
    private double panTravel;
    private boolean spaceDown;
    private @Nullable UiTreePath selectionAnchor;

    ConditionGraphInteraction(UiTreeEditor<ConditionNode> tree, ConditionGraphLayout graph,
                              UiTransform transform, Actions actions) {
        this.tree = tree;
        this.graph = graph;
        this.transform = transform;
        this.actions = actions;
        move = new UiTreeMoveInteraction<>(tree, result -> {
            armedDrag = null;
            dropTarget = null;
            dragLabel = null;
            actions.moveEnded().accept(result);
        });
    }

    boolean isActive() {
        return move.isActive() || panning || rubberActive;
    }

    boolean isMoving() {
        return move.isActive();
    }

    @Nullable UiTreeEditor.MovePreview<ConditionNode> preview() {
        return move.preview();
    }

    @Nullable ConditionNode dropTarget() {
        return dropTarget;
    }

    @Nullable String dragLabel() {
        return dragLabel;
    }

    void setSelectionAnchor(@Nullable UiTreePath path) {
        selectionAnchor = path;
    }

    void pressSpace() {
        spaceDown = true;
    }

    void releaseSpace() {
        spaceDown = false;
    }

    void drawRubberBand(GuiGraphics graphics, int fill, int line) {
        if (rubberActive) rubberBand.draw(graphics, fill, line);
    }

    void end(UiInteractionSession.Target owner, UiInteractionSession.EndReason reason) {
        if (interaction != null) interaction.end(owner, reason);
        armedDrag = null;
        clickedLeafPath = null;
        leafEditArmed = false;
        collapseSelectionOnRelease = false;
        dragLabel = null;
        dropTarget = null;
        rubberActive = false;
        rubberBand.clear();
        clearPanState();
        if (reason == UiInteractionSession.EndReason.HIDDEN
                || reason == UiInteractionSession.EndReason.DISABLED
                || reason == UiInteractionSession.EndReason.SCOPE_CHANGED
                || reason == UiInteractionSession.EndReason.UNMOUNTED
                || reason == UiInteractionSession.EndReason.HOST_CLOSED) {
            spaceDown = false;
        }
        move.end(reason);
    }

    private void clearPanState() {
        panning = false;
        panPressedBlank = false;
        panMoved = false;
        panTravel = 0.0D;
    }

    private void endGestureForNewPress() {
        armedDrag = null;
        clickedLeafPath = null;
        leafEditArmed = false;
        collapseSelectionOnRelease = false;
        dragLabel = null;
        dropTarget = null;
        if (move.isActive()) {
            move.end(UiInteractionSession.EndReason.CANCEL);
        }
    }

    private void selectRange(UiTreePath clicked, boolean additive) {
        ConditionNode anchorNode = selectionAnchor == null ? null : tree.query(selectionAnchor);
        int anchorIndex = anchorNode == null ? -1 : graph.indexOfNode(anchorNode);
        ConditionNode clickedNode = tree.query(clicked);
        int clickedIndex = clickedNode == null ? -1 : graph.indexOfNode(clickedNode);
        if (anchorIndex < 0 || clickedIndex < 0) {
            tree.select(clicked);
            selectionAnchor = clicked;
            return;
        }
        Set<UiTreePath> selected = new LinkedHashSet<>(additive ? tree.selection() : Set.of());
        for (int index = Math.min(anchorIndex, clickedIndex); index <= Math.max(anchorIndex, clickedIndex); index++) {
            selected.add(graph.path(graph.nodes().get(index)));
        }
        selected.remove(null);
        tree.setSelection(selected, clicked);
    }

    private void beginPan(double screenX, double screenY, boolean blankCandidate) {
        panning = true;
        panPressedBlank = blankCandidate;
        panMoved = false;
        panTravel = 0.0D;
        panLastX = screenX;
        panLastY = screenY;
    }

    private void beginRubberBand(double localX, double localY) {
        boolean additive = Screen.hasControlDown();
        rubberBase = additive ? tree.selection() : Set.of();
        rubberActive = true;
        rubberBand.begin(localX, localY);
        if (!additive) {
            tree.setSelection(List.of(), null);
        }
    }

    private void finishRubberBand() {
        if (rubberBand.active()) {
            Set<UiTreePath> selected = new LinkedHashSet<>(rubberBase);
            for (ConditionNode node : graph.nodes()) {
                UiRect rect = graph.rect(node);
                if (rect == null) {
                    continue;
                }
                if (rubberBand.intersects(rect.x(), rect.y(), rect.width(), rect.height())) {
                    selected.add(graph.path(node));
                }
            }
            selected.remove(null);
            tree.setSelection(selected, tree.selected());
        }
        rubberActive = false;
        rubberBand.clear();
    }

    private void projectDrop(double localX, double localY) {
        ConditionNode target = graph.nodeAt(localX, localY);
        if (target == null) {
            dropTarget = null;
            move.clearPreview();
            actions.error().accept(null);
            return;
        }
        UiRect rect = graph.rect(target);
        UiTreePath targetPath = graph.path(target);
        if (rect == null || targetPath == null) {
            return;
        }
        double within = localY - rect.y();
        int edge = Math.max(1, CARD_HEIGHT / DROP_EDGE_DIVISOR);
        UiTreeEditor.DropPosition position;
        if (isContainer(target) && ConditionTreeNodes.INSTANCE.children(target).isEmpty()) {
            position = UiTreeEditor.DropPosition.INSIDE;
        } else if (within < edge) {
            position = UiTreeEditor.DropPosition.BEFORE;
        } else if (within >= CARD_HEIGHT - edge) {
            position = UiTreeEditor.DropPosition.AFTER;
        } else if (ConditionTreeNodes.INSTANCE.childCapacity(target) > 0) {
            position = UiTreeEditor.DropPosition.INSIDE;
        } else {
            position = within < CARD_HEIGHT / 2.0 ? UiTreeEditor.DropPosition.BEFORE : UiTreeEditor.DropPosition.AFTER;
        }
        dropTarget = target;
        UiTreeEditor.MovePreview<ConditionNode> preview = move.preview(targetPath, position);
        actions.error().accept(preview == null ? null : preview.outcome().error());
    }

    boolean navigate(int keyCode) {
        UiTreePath selected = tree.selected();
        if (selected == null) {
            if (graph.nodes().isEmpty()) {
                return false;
            }
            return selectNode(graph.nodes().getFirst());
        }
        ConditionNode node = tree.query(selected);
        if (node == null) {
            return false;
        }
        UiTreePath next;
        if (keyCode == GLFW.GLFW_KEY_UP) {
            next = selected.indices().isEmpty() ? null : selected.parent();
        } else if (keyCode == GLFW.GLFW_KEY_LEFT) {
            next = siblingPath(selected, -1);
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            next = siblingPath(selected, 1);
        } else {
            List<ConditionNode> children = ConditionTreeNodes.INSTANCE.children(node);
            if (!children.isEmpty()) {
                next = selected.child(0);
            } else {
                int index = graph.indexOfNode(node);
                next = index >= 0 && index + 1 < graph.nodes().size() ? graph.path(graph.nodes().get(index + 1)) : null;
            }
        }
        if (next == null || tree.query(next) == null) {
            return true;
        }
        return selectNode(Objects.requireNonNull(tree.query(next)));
    }

    private @Nullable UiTreePath siblingPath(UiTreePath path, int delta) {
        if (path.indices().isEmpty()) {
            return null;
        }
        int index = path.indices().getLast() + delta;
        if (index < 0) {
            return null;
        }
        UiTreePath candidate = path.parent().child(index);
        return tree.query(candidate) == null ? null : candidate;
    }

    boolean selectSiblingEdge(boolean last) {
        if (graph.nodes().isEmpty()) {
            return false;
        }
        UiTreePath selected = tree.selected();
        if (selected == null) {
            return selectNode(graph.nodes().get(last ? graph.nodes().size() - 1 : 0));
        }
        if (selected.indices().isEmpty()) {
            return false;
        }
        List<ConditionNode> terms = termsOf(tree.query(selected.parent()));
        if (terms == null || terms.isEmpty()) {
            return false;
        }
        return selectNode(terms.get(last ? terms.size() - 1 : 0));
    }

    boolean extendSelection(int step) {
        if (graph.nodes().isEmpty()) {
            return false;
        }
        if (selectionAnchor == null) {
            selectionAnchor = tree.selected();
        }
        if (selectionAnchor == null) {
            return false;
        }
        ConditionNode anchorNode = tree.query(selectionAnchor);
        if (anchorNode == null) {
            return false;
        }
        UiTreePath focusedPath = tree.selected();
        ConditionNode focusedNode = focusedPath == null ? anchorNode : tree.query(focusedPath);
        int focusIndex = graph.indexOfNode(focusedNode == null ? anchorNode : focusedNode);
        if (focusIndex < 0) {
            return false;
        }
        int target = Math.clamp(focusIndex + step, 0, graph.nodes().size() - 1);
        if (target == focusIndex) {
            return true;
        }
        UiTreePath targetPath = graph.path(graph.nodes().get(target));
        if (targetPath == null) {
            return false;
        }
        selectRange(targetPath, true);
        return true;
    }

    private boolean selectNode(ConditionNode node) {
        UiTreePath path = graph.path(node);
        if (path == null) {
            return false;
        }
        tree.select(path);
        selectionAnchor = path;
        revealNode(node);
        return true;
    }

    void revealNode(ConditionNode node) {
        UiRect rect = graph.rect(node);
        if (rect == null) {
            return;
        }
        UiRect viewport = transform.viewport();
        double left = transform.toScreenX(rect.x());
        double top = transform.toScreenY(rect.y());
        double right = transform.toScreenX(rect.right());
        double bottom = transform.toScreenY(rect.bottom());
        double panX = 0;
        double panY = 0;
        if (left < viewport.x() + REVEAL_MARGIN) {
            panX = viewport.x() + REVEAL_MARGIN - left;
        } else if (right > viewport.right() - REVEAL_MARGIN) {
            panX = viewport.right() - REVEAL_MARGIN - right;
        }
        if (top < viewport.y() + REVEAL_MARGIN) {
            panY = viewport.y() + REVEAL_MARGIN - top;
        } else if (bottom > viewport.bottom() - REVEAL_MARGIN) {
            panY = viewport.bottom() - REVEAL_MARGIN - bottom;
        }
        if (panX != 0 || panY != 0) {
            transform.panBy(panX, panY);
        }
    }

    void mousePressed(UiInputContext context, UiInteractionSession.Target owner) {
        double mouseX = context.pointerX();
        double mouseY = context.pointerY();
        int button = context.button();
        interaction = context.scope().interaction();
        interaction.begin(owner, UiInteractionSession.Source.POINTER, button);
        // 中键与「空格 + 左键」是附加平移手势；空白区左键本身也能平移
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE || spaceDown) {
            beginPan(mouseX, mouseY, false);
            return;
        }
        endGestureForNewPress();
        double localX = transform.toLocalX(mouseX);
        double localY = transform.toLocalY(mouseY);
        // 每个容器卡片下方的 [+] 热区都是新增条件的入口，点它就弹类型列表
        Hotspot hotspot = graph.hotspotAt(localX, localY);
        if (hotspot != null) {
            actions.openPicker().accept(hotspot);
            return;
        }
        ConditionNode node = graph.nodeAt(localX, localY);
        if (node == null) {
            // 空白区左键拖拽 = 平移；框选改到 Shift + 拖拽
            if (Screen.hasShiftDown()) {
                beginRubberBand(localX, localY);
                return;
            }
            beginPan(mouseX, mouseY, true);
            return;
        }
        UiTreePath clicked = graph.path(node);
        if (clicked == null) {
            return;
        }
        boolean control = Screen.hasControlDown();
        boolean shift = Screen.hasShiftDown();
        boolean alreadySelected = tree.selection().size() == 1 && clicked.equals(tree.selected());
        if (shift) {
            selectRange(clicked, control);
        } else if (control) {
            tree.toggleSelection(clicked);
            selectionAnchor = clicked;
        } else if (tree.selection().contains(clicked)) {
            collapseSelectionOnRelease = tree.selection().size() > 1;
            tree.setSelection(tree.selection(), clicked);
            selectionAnchor = clicked;
        } else {
            tree.select(clicked);
            selectionAnchor = clicked;
        }
        if (!control && !shift && node instanceof ConditionNode.Leaf) {
            clickedLeafPath = clicked;
            leafEditArmed = alreadySelected;
        }
        pressScreenX = mouseX;
        pressScreenY = mouseY;
        // 根节点没有同级位置，不能拖动
        if (!clicked.indices().isEmpty()) {
            armedDrag = clicked;
        }
    }

    boolean mouseDragged(UiInputContext context) {
        double mouseX = context.pointerX();
        double mouseY = context.pointerY();
        int button = context.button();
        if (panning) {
            double stepX = mouseX - panLastX;
            double stepY = mouseY - panLastY;
            transform.panBy(stepX, stepY);
            panLastX = mouseX;
            panLastY = mouseY;
            panTravel += Math.hypot(stepX, stepY);
            if (panTravel >= DRAG_START_DISTANCE) {
                panMoved = true;
            }
            return true;
        }
        double localX = transform.toLocalX(mouseX);
        double localY = transform.toLocalY(mouseY);
        if (rubberActive) {
            rubberBand.update(localX, localY);
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        UiTreePath source = armedDrag;
        if (!move.isActive() && source != null
                && Math.hypot(mouseX - pressScreenX, mouseY - pressScreenY) >= DRAG_START_DISTANCE) {
            List<UiTreePath> sources = tree.normalizedSelection();
            if (sources.isEmpty()) {
                sources = List.of(source);
            }
            if (move.begin(sources)) {
                collapseSelectionOnRelease = false;
                ConditionNode node = tree.query(source);
                dragLabel = sources.size() > 1
                        ? Component.translatable(TypeLabels.UI_PREFIX + "tree.drag_many", sources.size()).getString()
                        : node == null ? "" : graph.titleLabel(node).getString();
            }
        }
        if (!move.isActive()) {
            return source != null;
        }
        projectDrop(localX, localY);
        return true;
    }

    boolean mouseReleased(UiInputContext context) {
        double mouseX = context.pointerX();
        double mouseY = context.pointerY();
        int button = context.button();
        if (panning) {
            // 空白区按下并拖动才算平移；没有位移时按空白点击处理：清空选择
            boolean blankClick = panPressedBlank && !panMoved;
            clearPanState();
            if (blankClick) {
                tree.setSelection(List.of(), null);
            }
            return true;
        }
        if (rubberActive) {
            finishRubberBand();
            return true;
        }
        boolean owned = armedDrag != null || clickedLeafPath != null || move.isActive();
        boolean left = button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
        boolean editLeaf = left && leafEditArmed && !move.isActive() && clickedLeafPath != null
                && Math.hypot(mouseX - pressScreenX, mouseY - pressScreenY) < DRAG_START_DISTANCE
                && clickedLeafPath.equals(tree.selected())
                && tree.query(clickedLeafPath) == graph.nodeAt(transform.toLocalX(mouseX), transform.toLocalY(mouseY));
        if (!move.isActive() && collapseSelectionOnRelease && left) {
            tree.select(tree.selected());
        }
        collapseSelectionOnRelease = false;
        if (move.isActive() && left) {
            projectDrop(transform.toLocalX(mouseX), transform.toLocalY(mouseY));
            move.end(UiInteractionSession.EndReason.RELEASE);
        }
        armedDrag = null;
        clickedLeafPath = null;
        leafEditArmed = false;
        dropTarget = null;
        if (editLeaf) actions.editLeaf().run();
        return owned;
    }
}
