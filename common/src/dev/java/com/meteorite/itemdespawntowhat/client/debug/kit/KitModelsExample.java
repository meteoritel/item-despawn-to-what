package com.meteorite.itemdespawntowhat.client.debug.kit;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiControlGroup;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiListView;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiWidget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiChildren;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContainer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import org.jetbrains.annotations.Nullable;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLinearLayout;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.tree.UiTreeEditor;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.tree.UiTreeNavigation;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.tree.UiTreePath;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.tree.UiTreeProjection;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.tree.UiTreeViewState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** 本地不可变树和自定义文本模型的真实消费者，无宿主业务类型。 */
final class KitModelsExample implements UiWidget, UiInputContainer, UiLayoutSource {
    private record Node(String key, List<Node> children) {
        private Node { children = List.copyOf(children); }
    }
    private Node root = new Node("root", List.of(new Node("first", List.of()), new Node("second", List.of())));
    private final UiChildren<UiInputTarget> children = new UiChildren<>();
    private final UiControlGroup actions = new UiControlGroup();
    private final UiLinearLayout toolbar = new UiLinearLayout(UiLinearLayout.Axis.HORIZONTAL, 4, 0);
    private final KitTextModelExample text;
    private final UiListView<UiTreeProjection.Entry<Node>> tree;
    private final UiTreeEditor<Node> editor;
    private UiTreeViewState view = UiTreeViewState.EMPTY.select(UiTreePath.ROOT);
    private UiTreeProjection<Node> projection;
    private UiRect bounds = new UiRect(0, 0, 0, 0);

    KitModelsExample(Font font, UiStyleSource theme) {
        text = new KitTextModelExample(font);
        tree = new UiListView<>(theme, (graphics, renderFont, entry, index, row, selected, hovered, focused) ->
                graphics.drawString(renderFont, label(entry.node().key()), row.x() + entry.depth() * 10,
                        row.y() + 4, theme.textColor(true), false));
        editor = new UiTreeEditor<>(new UiTreeEditor.Adapter<>() {
            @Override public List<Node> children(Node node) { return node.children(); }
            @Override public Node withChildren(Node node, List<Node> nodes) { return new Node(node.key(), nodes); }
            @Override public int childCapacity(Node node) { return node.key().equals("root") ? UNLIMITED_CHILDREN : 0; }
        }, () -> root, change -> { root = change.after(); rebuild(); }, node -> null, label("invalid"), label("invalid"));
        editor.setOnViewChanged(change -> {
            view = new UiTreeViewState(change.selected(), change.collapsed(), change.selection());
            refreshRows();
        });
        tree.setOnSelectionChanged(index -> {
            var entry = tree.selectedItem();
            if (entry != null) editor.select(entry.path());
        });
        actions.setTheme(theme);
        actions.beginUpdate();
        actions.obtain("move").configure(font, label("move"), null, List.of(), this::moveFirst);
        actions.obtain("fold").configure(font, label("fold"), null, List.of(),
                () -> editor.setCollapsed(UiTreePath.ROOT, !view.collapsed().contains(UiTreePath.ROOT)));
        actions.endUpdate();
        toolbar.setChildren(List.of(UiLinearLayout.Child.remain(), UiLinearLayout.Child.remain()));
        children.add(actions); children.add(text); children.add(tree);
        rebuild();
        editor.select(UiTreePath.ROOT);
    }

    private void rebuild() { projection = UiTreeProjection.single(root, Node::children); refreshRows(); }
    private void refreshRows() {
        var rows = projection.visible(view);
        tree.setItems(rows);
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).path().equals(view.selected())) { tree.setSelectedIndex(i); break; }
    }
    private void moveFirst() {
        UiTreePath source = UiTreePath.ROOT.child(0);
        if (editor.beginMove(source)) {
            var preview = editor.previewMove(root, source, UiTreePath.ROOT.child(1), UiTreeEditor.DropPosition.AFTER);
            editor.commitMove(preview);
        }
    }
    /** 树导航派生新视图，编辑器继续持有结构操作与路径映射。 */
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        var intent = UiTreeNavigation.fromKey(key);
        if (!tree.isFocused() || intent == null) return UiInputContainer.super.keyPressed(key, scan, modifiers);
        UiTreeViewState next = UiTreeNavigation.navigate(projection.visible(view), view, intent, 5);
        for (var entry : projection.entries()) editor.setCollapsed(entry.path(), next.collapsed().contains(entry.path()));
        editor.select(next.selected());
        return true;
    }
    /** 同一成员树同时提供命中与焦点版本。 */
    @Override public @Nullable UiPointerHit hitTest(double x, double y) { return UiInputContainer.super.hitTest(x, y); }
    /** {@inheritDoc} */
    @Override public long focusRevision() { return UiInputContainer.super.focusRevision(); }
    /** {@inheritDoc} */
    @Override public UiChildren<UiInputTarget> inputChildren() { return children; }
    /** {@inheritDoc} */
    @Override public UiRect bounds() { return bounds; }
    /** {@inheritDoc} */
    @Override public void setBounds(int x, int y, int width, int height) {
        bounds = new UiRect(x, y, width, height);
        toolbar.setBounds(x, y, width, 20);
        for (int i = 0; i < actions.size(); i++) {
            UiRect cell = toolbar.bounds(i);
            actions.controls().get(i).setBounds(cell.x(), cell.y(), cell.width(), cell.height());
        }
        text.setBounds(x, y + 24, width, 20);
        tree.setBounds(x, y + 48, width, Math.max(0, height - 48));
    }
    /** {@inheritDoc} */
    @Override public void render(GuiGraphics graphics, Font font, UiPointer pointer) {
        actions.render(graphics, font, pointer); text.render(graphics, font, pointer); tree.render(graphics, font, pointer);
    }
    /** {@inheritDoc} */
    @Override public List<UiLayoutNode> layoutNodes() {
        var nodes = new ArrayList<UiLayoutNode>();
        for (var control : actions.controls()) nodes.addAll(control.layoutNodes());
        nodes.add(new UiLayoutNode(UiLayoutNode.Kind.TEXT_INPUT, text.bounds(), text.accessibleName(), 0,
                text.bounds().width(), true, true, List.of()));
        var rows = projection.visible(view);
        var rowNodes = new ArrayList<UiLayoutNode>();
        for (int i = 0; i < rows.size(); i++) {
            UiRect row = tree.visibleRowBounds(i);
            if (row != null) rowNodes.add(new UiLayoutNode(UiLayoutNode.Kind.ROW, row, label(rows.get(i).node().key()),
                    0, row.width(), true, true, List.of()));
        }
        nodes.add(new UiLayoutNode(UiLayoutNode.Kind.TREE, tree.bounds(), label("root"), 0, 0, true, true, rowNodes));
        return List.copyOf(nodes);
    }
    private static Component label(String key) { return Component.translatable(KitShowcaseContent.UI + "showcase.models." + key); }
}
