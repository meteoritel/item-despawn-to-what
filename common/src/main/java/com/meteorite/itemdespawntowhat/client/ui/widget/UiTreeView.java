package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiScrollView;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/***
 * 可展开的树视图控件。
 * <p>由展平后的可见行承载渲染与命中：只有展开节点的后代才会出现在可见行中，
 * 因此键盘上下移动、Home/End、PgUp/PgDn 与鼠标命中都按可见行计算。
 * <ul>
 *   <li>鼠标：单击选中，点击展开标记（节点左侧的 +/- 区）切换展开状态，双击激活。</li>
 *   <li>键盘：↑/↓ 移动，← 折叠或跳到父节点，→ 展开或进入子节点，Enter/Space 激活。</li>
 * </ul>
 * 行内容由调用方通过 {@link NodeRenderer} 决定；节点文本缩进与连线由本控件绘制。
 */
public final class UiTreeView<T> implements UiWidget, UiFocusTarget {

    // 行渲染回调
    @FunctionalInterface
    public interface NodeRenderer<T> {
        // 渲染一行；row 已扣除选中标记与缩进，可直接绘制节点文本
        void renderNode(GuiGraphics graphics, Font font, UiTreeNode<T> node, UiRect row, int depth, boolean selected, boolean hovered, boolean focused);
    }

    // 每层缩进宽度
    public static final int INDENT_WIDTH = 10;
    // 展开标记区宽度
    public static final int MARKER_WIDTH = 9;
    // 默认行高
    public static final int DEFAULT_ROW_HEIGHT = UiTheme.ROW_HEIGHT;
    // 滚动条预留宽度（与 kit 的滚动条宽度保持一致）
    private static final int SCROLLBAR_RESERVE = 4;
    // 双击判定间隔（毫秒）
    private static final long DOUBLE_CLICK_MS = 250L;

    // 展平后的可见行
    private static final class VisibleRow<N> {
        // 节点
        final UiTreeNode<N> node;
        // 缩进深度（根为 0）
        final int depth;

        VisibleRow(UiTreeNode<N> node, int depth) {
            this.node = node;
            this.depth = depth;
        }
    }

    // 行渲染器
    private final NodeRenderer<T> nodeRenderer;
    // 滚动与裁剪
    private final UiScrollView scrollView = new UiScrollView();
    // 根节点
    private final List<UiTreeNode<T>> roots = new ArrayList<>();
    // 展平后的可见行
    private final List<VisibleRow<T>> visibleRows = new ArrayList<>();
    // 控件矩形
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    // 行高
    private int rowHeight = DEFAULT_ROW_HEIGHT;
    // 选中节点
    private UiTreeNode<T> selectedNode;
    // 一屏可见行数
    private int visibleRowCount = 1;
    // 空树提示
    private Component emptyMessage = Component.empty();
    // 选中变化回调
    private Consumer<UiTreeNode<T>> onSelectionChanged;
    // 激活回调
    private Consumer<UiTreeNode<T>> onActivate;
    // 是否持有键盘焦点
    private boolean focused;
    // 是否可见
    private boolean visible = true;
    // 上次点击时间与下标（双击判定）
    private long lastClickTime;
    private int lastClickIndex = -1;

    // 字体由 render 接收；保留构造参数以兼容现有组件 API。
    public UiTreeView(@SuppressWarnings("unused") Font font, NodeRenderer<T> nodeRenderer) {
        this.nodeRenderer = nodeRenderer;
    }

    // ---- 数据 ----

    // 替换全部根节点；已有展开状态全部丢失
    public UiTreeView<T> setRoots(List<UiTreeNode<T>> newRoots) {
        roots.clear();
        roots.addAll(newRoots);
        selectedNode = null;
        rebuild();
        return this;
    }

    // 追加根节点
    public UiTreeView<T> addRoot(UiTreeNode<T> root) {
        roots.add(root);
        rebuild();
        return this;
    }

    // 根节点（只读）
    public List<UiTreeNode<T>> roots() {
        return Collections.unmodifiableList(roots);
    }

    // 设置行高
    public UiTreeView<T> setRowHeight(int rowHeight) {
        this.rowHeight = Math.max(9, rowHeight);
        scrollView.setStep(Math.max(1, this.rowHeight * 2));
        rebuild();
        return this;
    }

    // 设置空树提示
    public UiTreeView<T> setEmptyMessage(Component message) {
        this.emptyMessage = message;
        return this;
    }

    // 设置选中变化回调
    public UiTreeView<T> setOnSelectionChanged(Consumer<UiTreeNode<T>> callback) {
        this.onSelectionChanged = callback;
        return this;
    }

    // 设置激活回调
    public UiTreeView<T> setOnActivate(Consumer<UiTreeNode<T>> callback) {
        this.onActivate = callback;
        return this;
    }

    // 设置是否可见
    public UiTreeView<T> setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    // 展开全部节点
    public void expandAll() {
        for (UiTreeNode<T> root : roots) {
            root.expandAll();
        }
        rebuild();
    }

    // 折叠全部节点
    public void collapseAll() {
        for (UiTreeNode<T> root : roots) {
            root.collapseAll();
        }
        rebuild();
    }

    // 选中节点
    public UiTreeNode<T> selectedNode() {
        return selectedNode;
    }

    // 设置选中节点
    public UiTreeView<T> setSelectedNode(UiTreeNode<T> node) {
        if (selectedNode == node) {
            ensureVisibleFor(node);
            return this;
        }
        selectedNode = node;
        ensureVisibleFor(node);
        if (onSelectionChanged != null) {
            onSelectionChanged.accept(selectedNode);
        }
        return this;
    }

    // ---- 布局 ----

    @Override
    public UiRect bounds() {
        return bounds;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        this.bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        scrollView.setViewport(x + 1, y + 1, Math.max(0, width - 2), Math.max(0, height - 2));
        scrollView.setStep(Math.max(1, rowHeight * 2));
        rebuild();
    }

    // 重新展平可见行并同步滚动范围
    private void rebuild() {
        visibleRows.clear();
        for (UiTreeNode<T> root : roots) {
            append(root, 0);
        }
        scrollView.setContentHeight(visibleRows.size() * rowHeight);
        int viewportHeight = scrollView.viewport().height();
        visibleRowCount = Math.max(1, viewportHeight / Math.max(1, rowHeight));
        scrollView.setScrollbarVisible(visibleRows.size() * rowHeight > viewportHeight);
    }

    // 递归展平
    private void append(UiTreeNode<T> node, int depth) {
        visibleRows.add(new VisibleRow<>(node, depth));
        if (!node.isLeaf() && node.isExpanded()) {
            for (UiTreeNode<T> child : node.children()) {
                append(child, depth + 1);
            }
        }
    }

    // 内容区可用宽度（扣除滚动条）
    private int contentWidth() {
        int width = scrollView.viewport().width();
        if (scrollView.isScrollbarVisible()) {
            width -= SCROLLBAR_RESERVE;
        }
        return Math.max(0, width);
    }

    // 屏幕坐标换算为内容坐标下的可见行下标
    private int indexAt(double mouseX, double mouseY) {
        UiRect viewport = scrollView.viewport();
        if (!viewport.contains(mouseX, mouseY)) {
            return -1;
        }
        int contentY = (int) (mouseY - viewport.y()) + scrollView.offset();
        int index = contentY / Math.max(1, rowHeight);
        return index >= 0 && index < visibleRows.size() ? index : -1;
    }

    // 选中节点在可见行中的下标，未选中时为 -1
    private int selectedIndex() {
        return selectedNode == null ? -1 : visibleRows.indexOf(new VisibleRow<>(selectedNode, 0));
    }

    // 滚动到指定可见行
    private void ensureVisible(int index) {
        if (index < 0 || index >= visibleRows.size()) {
            return;
        }
        scrollView.ensureVisible(new UiRect(0, index * rowHeight, Math.max(0, scrollView.viewport().width()), rowHeight));
    }

    // 滚动到指定节点
    private void ensureVisibleFor(UiTreeNode<T> node) {
        if (node == null) {
            return;
        }
        for (int i = 0; i < visibleRows.size(); i++) {
            if (visibleRows.get(i).node == node) {
                ensureVisible(i);
                return;
            }
        }
    }

    // ---- 渲染 ----

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        UiTheme.drawInset(graphics, bounds);
        UiRect viewport = scrollView.viewport();
        if (visibleRows.isEmpty()) {
            graphics.drawCenteredString(font, emptyMessage, viewport.x() + viewport.width() / 2,
                    viewport.y() + Math.max(0, viewport.height() / 2 - 4), UiPalette.TEXT_SECONDARY);
            return;
        }
        int hoveredIndex = indexAt(mouseX, mouseY);
        int rowWidth = contentWidth();
        int baseX = UiTheme.SELECT_MARKER_WIDTH;
        scrollView.push(graphics);
        for (int i = 0; i < visibleRows.size(); i++) {
            int rowY = i * rowHeight;
            if (rowY + rowHeight < scrollView.offset() || rowY > scrollView.offset() + viewport.height()) {
                continue;
            }
            VisibleRow<T> visibleRow = visibleRows.get(i);
            UiRect row = new UiRect(0, rowY, rowWidth, rowHeight);
            boolean selected = visibleRow.node == selectedNode;
            if (selected) {
                UiTheme.drawSelection(graphics, row);
                UiTheme.drawSelectMarker(graphics, row);
            } else if (i == hoveredIndex) {
                graphics.fill(row.x(), row.y(), row.right(), row.bottom(), UiPalette.CONTROL_HOVER);
            }
            int markerX = baseX + visibleRow.depth * INDENT_WIDTH;
            // 非根节点画一小段横向连线，帮助区分层级
            if (visibleRow.depth > 0) {
                graphics.fill(markerX - 5, row.y() + row.height() / 2, markerX - 2, row.y() + row.height() / 2 + 1, UiPalette.TEXT_SECONDARY);
            }
            // TODO 美术资源：展开标记暂用 ASCII +/-，后续可替换为像素贴图
            if (!visibleRow.node.isLeaf()) {
                String mark = visibleRow.node.isExpanded() ? "-" : "+";
                graphics.drawString(font, mark, markerX + 2, row.y() + UiTheme.TEXT_OFFSET, UiPalette.TEXT_PRIMARY, false);
            }
            UiRect textRow = new UiRect(markerX + MARKER_WIDTH, row.y(),
                    Math.max(0, row.width() - markerX - MARKER_WIDTH), row.height());
            nodeRenderer.renderNode(graphics, font, visibleRow.node, textRow, visibleRow.depth, selected, i == hoveredIndex, focused);
        }
        scrollView.pop(graphics);
        if (focused) {
            UiTheme.drawFocusOutline(graphics, bounds);
        }
        scrollView.renderScrollbar(graphics, UiTheme.secondaryStyle());
    }

    // ---- 输入 ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (scrollView.mousePressed(mouseX, mouseY, button)) {
            return true;
        }
        int index = indexAt(mouseX, mouseY);
        if (index < 0) {
            return true;
        }
        VisibleRow<T> visibleRow = visibleRows.get(index);
        UiRect viewport = scrollView.viewport();
        int contentX = (int) (mouseX - viewport.x());
        int markerX = UiTheme.SELECT_MARKER_WIDTH + visibleRow.depth * INDENT_WIDTH;
        long now = net.minecraft.Util.getMillis();
        boolean doubleClick = index == lastClickIndex && now - lastClickTime <= DOUBLE_CLICK_MS;
        lastClickIndex = index;
        lastClickTime = now;
        setSelectedNode(visibleRow.node);
        if (contentX >= markerX && contentX <= markerX + MARKER_WIDTH && !visibleRow.node.isLeaf()) {
            visibleRow.node.toggleExpanded();
            rebuild();
            ensureVisibleFor(visibleRow.node);
            return true;
        }
        if (doubleClick && onActivate != null) {
            onActivate.accept(visibleRow.node);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollView.isDragging()) {
            scrollView.mouseDragged(mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrollView.mouseReleased();
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!visible || !bounds.contains(mouseX, mouseY) || scrollY == 0.0D) {
            return false;
        }
        return scrollView.scrollBy(scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || visibleRows.isEmpty()) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            moveSelection(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            moveSelection(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            moveSelection(-visibleRowCount);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            moveSelection(visibleRowCount);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            setSelectedNode(visibleRows.getFirst().node);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            setSelectedNode(visibleRows.getLast().node);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            return collapseOrJumpToParent();
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            return expandOrEnterChild();
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            return activate();
        }
        return false;
    }

    // 相对移动选中行
    private void moveSelection(int delta) {
        int index = selectedIndex();
        int base = index < 0 ? (delta > 0 ? -1 : visibleRows.size()) : index;
        int target = Math.max(0, Math.min(visibleRows.size() - 1, base + delta));
        setSelectedNode(visibleRows.get(target).node);
    }

    // 左方向键：优先折叠，已折叠则跳到父节点
    private boolean collapseOrJumpToParent() {
        int index = selectedIndex();
        if (index < 0) {
            return false;
        }
        VisibleRow<T> current = visibleRows.get(index);
        if (!current.node.isLeaf() && current.node.isExpanded()) {
            current.node.setExpanded(false);
            rebuild();
            ensureVisibleFor(current.node);
            return true;
        }
        for (int i = index - 1; i >= 0; i--) {
            if (visibleRows.get(i).depth < current.depth) {
                setSelectedNode(visibleRows.get(i).node);
                return true;
            }
        }
        return true;
    }

    // 右方向键：优先展开，已展开则进入第一个子节点
    private boolean expandOrEnterChild() {
        int index = selectedIndex();
        if (index < 0) {
            return false;
        }
        VisibleRow<T> current = visibleRows.get(index);
        if (current.node.isLeaf()) {
            return true;
        }
        if (!current.node.isExpanded()) {
            current.node.setExpanded(true);
            rebuild();
            ensureVisibleFor(current.node);
            return true;
        }
        if (index + 1 < visibleRows.size() && visibleRows.get(index + 1).depth > current.depth) {
            setSelectedNode(visibleRows.get(index + 1).node);
        }
        return true;
    }

    // ---- 焦点 ----

    @Override
    public boolean canFocus() {
        return visible;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public boolean activate() {
        if (!visible || selectedNode == null || onActivate == null) {
            return false;
        }
        onActivate.accept(selectedNode);
        return true;
    }

    @Override
    public Component accessibleName() {
        return emptyMessage;
    }
}
