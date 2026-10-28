package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreePath;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreeProjection;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreeViewState;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreeNavigation;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreeRowMarker;
import java.util.function.Predicate;
import java.util.HashSet;
import java.util.Set;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiControlRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFlatText;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiScrollView;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * 可展开的树视图控件。
 * <p>由展平后的可见行承载渲染与命中：只有展开节点的后代才会出现在可见行中，
 * 因此键盘上下移动、Home/End、PgUp/PgDn 与鼠标命中都按可见行计算。
 * <ul>
 *   <li>鼠标：单击选中，三角单击或目录双击切换展开，叶子双击激活。</li>
 *   <li>键盘：↑/↓ 移动，← 折叠或跳到父节点，→ 展开或进入子节点，Enter 激活。</li>
 * </ul>
 * 行内容和高度由调用方决定；缩进与展开三角由本控件绘制，渲染和命中共用累计行高。
 */
public final class UiTreeView<T> implements UiWidget, UiFocusTarget, UiLayoutSource {

    /** 树行渲染回调：缩进与展开三角由控件绘制，{@code row} 已扣除展开标记与缩进，可直接绘制节点文本。 */
    @FunctionalInterface
    public interface NodeRenderer<T> {
        // 渲染一行；row 已扣除展开标记与缩进，可直接绘制节点文本
        void renderNode(GuiGraphics graphics, Font font, UiTreeNode<T> node, UiRect row, int depth, boolean selected, boolean hovered, boolean focused);
    }

    // 每层缩进宽度
    public static final int INDENT_WIDTH = 10;
    // 展开标记区宽度
    public static final int MARKER_WIDTH = 9;
    // 双击判定间隔（毫秒）
    private static final long DOUBLE_CLICK_MS = 250L;

    /** 展平后的可见行及其实际内容坐标，供渲染、命中和滚动共同使用。 */
    private static final class VisibleRow<N> {
        // 节点
        final UiTreeNode<N> node;
        // 缩进深度（根为 0）
        final int depth;
        final int top;
        final int height;
        // 渲染用矩形（内容坐标）：按值更新而不是每次绘制都新建，静止帧因此不逐行分配；
        // 重建可见行时随行对象一起重建，不会跨行复用旧几何。
        UiRect rowRect = new UiRect(0, 0, 0, 0);
        UiRect textRect = new UiRect(0, 0, 0, 0);

        VisibleRow(UiTreeNode<N> node, int depth, int top, int height) {
            this.node = node;
            this.depth = depth;
            this.top = top;
            this.height = height;
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
    private int rowHeight;
    private ToIntFunction<UiTreeNode<T>> rowHeightProvider;
    // 选中节点
    private UiTreeProjection<UiTreeNode<T>> projection = UiTreeProjection.forest(List.of(), UiTreeNode::children);
    private List<UiTreeProjection.Entry<UiTreeNode<T>>> navigationRows = List.of();
    private UiTreeViewState view = UiTreeViewState.EMPTY;
    // 空树提示
    private Component emptyMessage = Component.empty();
    // 选中变化回调
    private Consumer<UiTreeNode<T>> onSelectionChanged;
    // 激活回调
    private Consumer<UiTreeNode<T>> onActivate;
    private boolean focused;
    private boolean visible = true;
    // 按节点身份判定双击，展开后行下标变化不会误激活其它节点。
    private long lastClickTime;
    private UiTreeNode<T> lastClickNode;

    // 注入的宿主主题
    private final UiHostTheme theme;
    // 行文本测量字体：渲染时宿主传同一实例，快照导出靠它量文案宽度
    private final Font font;

    /** 使用注入主题的实例度量初始化行布局。 */
    public UiTreeView(UiHostTheme theme, Font font, NodeRenderer<T> nodeRenderer) {
        this.theme = UiHostThemes.require(theme);
        this.rowHeight = this.theme.rowHeight();
        scrollView.setStep(Math.max(1, rowHeight * 2));
        this.font = font;
        this.nodeRenderer = nodeRenderer;
    }

    // ---- 数据 ----

    /** 替换结构并清除选择；分支初始折叠，宿主随后按稳定身份恢复展开状态。 */
    public UiTreeView<T> setRoots(List<UiTreeNode<T>> newRoots) {
        roots.clear();
        roots.addAll(newRoots);
        projection = UiTreeProjection.forest(roots, UiTreeNode::children);
        view = UiTreeViewState.EMPTY;
        lastClickNode = null;
        setExpansion(node -> false);
        return this;
    }

    /** 追加根节点，保留已有状态；新分支初始折叠。 */
    public UiTreeView<T> addRoot(UiTreeNode<T> root) {
        roots.add(root);
        projection = UiTreeProjection.forest(roots, UiTreeNode::children);
        Set<UiTreePath> folded = new HashSet<>(view.collapsed());
        int rootIndex = roots.size() - 1;
        for (var entry : projection.entries()) {
            if (entry.path().indices().getFirst() == rootIndex && !entry.node().isLeaf()) folded.add(entry.path());
        }
        view = new UiTreeViewState(view.selected(), folded, view.selection());
        rebuild();
        return this;
    }

    // 根节点（只读）
    public List<UiTreeNode<T>> roots() {
        return Collections.unmodifiableList(roots);
    }

    public UiTreeView<T> setRowHeight(int rowHeight) {
        this.rowHeight = Math.max(9, rowHeight);
        scrollView.setStep(Math.max(1, this.rowHeight * 2));
        rebuild();
        return this;
    }

    // 可按节点内容计算行高；未提供时沿用统一行高。
    public void setRowHeightProvider(ToIntFunction<UiTreeNode<T>> provider) {
        rowHeightProvider = provider;
        rebuild();
    }

    // 数据刷新时由宿主保存并恢复滚动位置；尺寸变小时由视口正常钳制。
    public int scrollOffset() { return scrollView.offset(); }
    public void setScrollOffset(int offset) { scrollView.setOffset(offset); }

    public UiTreeView<T> setEmptyMessage(Component message) {
        this.emptyMessage = message;
        return this;
    }

    public UiTreeView<T> setOnSelectionChanged(Consumer<UiTreeNode<T>> callback) {
        this.onSelectionChanged = callback;
        return this;
    }

    public UiTreeView<T> setOnActivate(Consumer<UiTreeNode<T>> callback) {
        this.onActivate = callback;
        return this;
    }

    public UiTreeView<T> setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    /** 展开当前视图的全部节点，保留选择。 */
    public void expandAll() { view = view.expandAll(); rebuild(); }

    /** 折叠当前视图的全部分支，保留选择。 */
    public void collapseAll() { setExpansion(node -> false); }

    // 选中节点
    public UiTreeNode<T> selectedNode() {
        var row = view.selected() == null ? null : projection.entry(view.selected());
        return row == null ? null : row.node();
    }

    public UiTreeView<T> setSelectedNode(UiTreeNode<T> node) {
        UiTreePath path = node == null ? null : projection.pathOf(node);
        if (java.util.Objects.equals(view.selected(), path)) { ensureVisibleFor(node); return this; }
        view = view.select(path);
        ensureVisibleFor(node);
        if (onSelectionChanged != null) onSelectionChanged.accept(selectedNode());
        return this;
    }

    /** 按宿主稳定身份在当前投影上恢复展开状态，不跨结构版本复用位置路径。 */
    public void setExpansion(Predicate<UiTreeNode<T>> expanded) {
        Set<UiTreePath> folded = new HashSet<>();
        for (var entry : projection.entries()) if (!entry.node().isLeaf() && !expanded.test(entry.node())) folded.add(entry.path());
        view = new UiTreeViewState(view.selected(), folded, view.selection());
        rebuild();
    }

    /** 查询本视图的节点展开状态。 */
    public boolean isExpanded(UiTreeNode<T> node) {
        UiTreePath path = projection.pathOf(node);
        return path != null && !view.collapsed().contains(path);
    }

    /**
     * 导出树的排版快照：TREE 节点覆盖控件外框，子节点是按当前滚动偏移换算后的可见行。
     *
     * <p>只报与视口相交的行：滚动出去的节点在屏幕上不可见，报出会误导外部探针。
     * 行矩形把内容坐标加上视口原点再减去滚动偏移，得到与渲染完全一致的 GUI 坐标；
     * 文本带宽取扣除缩进与展开标记后的宽度，与 {@code textRect} 的绘制口径同源。</p>
     */
    @Override
    public List<UiLayoutNode> layoutNodes() {
        if (!visible || bounds.width() <= 0 || bounds.height() <= 0) {
            return List.of();
        }
        UiRect viewport = scrollView.viewport();
        if (viewport.width() <= 0 || viewport.height() <= 0) {
            return List.of();
        }
        int rowWidth = RowGeometry.contentWidth(scrollView);
        int offset = scrollView.offset();
        List<UiLayoutNode> rows = new ArrayList<>();
        for (VisibleRow<T> row : visibleRows) {
            if (row.top + row.height <= offset || row.top >= offset + viewport.height()) {
                continue;
            }
            UiRect text = textRect(row, rowWidth, row.depth * INDENT_WIDTH);
            Component label = row.node.label();
            rows.add(new UiLayoutNode(UiLayoutNode.Kind.ROW,
                    new UiRect(viewport.x() + text.x(), viewport.y() + row.top - offset, text.width(), text.height()),
                    label, font.width(label), text.width(), true, true, List.of()));
        }
        return List.of(new UiLayoutNode(UiLayoutNode.Kind.TREE, bounds, Component.empty(), 0, 0,
                true, true, List.copyOf(rows)));
    }

    // ---- 布局 ----

    @Override
    public UiRect bounds() {
        return bounds;
    }

    // 几何不变时不再重设视口：宿主布局每帧对齐控件矩形，重复设置会逐帧分配矩形。
    // 行数或行高变化由 rebuild() 同步滚动范围，因此早退不会漏掉内容变化。
    @Override
    public void setBounds(int x, int y, int width, int height) {
        int nextWidth = Math.max(0, width);
        int nextHeight = Math.max(0, height);
        if (bounds.x() == x && bounds.y() == y && bounds.width() == nextWidth && bounds.height() == nextHeight) {
            return;
        }
        this.bounds = new UiRect(x, y, nextWidth, nextHeight);
        scrollView.setViewport(x + 1, y + 1, Math.max(0, nextWidth - 2), Math.max(0, nextHeight - 2));
        scrollView.setStep(Math.max(1, rowHeight * 2));
        syncScrollRange();
    }

    // 重新展平可见行并同步滚动范围
    private void rebuild() {
        visibleRows.clear();
        navigationRows = projection.visible(view);
        int top = 0;
        for (var entry : navigationRows) {
            int height = rowHeightProvider == null ? rowHeight : Math.max(9, rowHeightProvider.applyAsInt(entry.node()));
            visibleRows.add(new VisibleRow<>(entry.node(), entry.depth(), top, height));
            top += height;
        }
        syncScrollRange();
    }

    // 几何变化只更新视口，不重建节点或选择。
    private void syncScrollRange() {
        int contentHeight = visibleRows.isEmpty() ? 0 : visibleRows.getLast().top + visibleRows.getLast().height;
        scrollView.setContentHeight(contentHeight);
        int viewportHeight = scrollView.viewport().height();
        scrollView.setScrollbarVisible(contentHeight > viewportHeight);
    }

    // 行矩形按值复用：滚动或控件宽度变化时才重建，静止帧不逐行分配
    private static <N> UiRect rowRect(VisibleRow<N> row, int rowWidth) {
        if (row.rowRect.y() != row.top || row.rowRect.width() != rowWidth || row.rowRect.height() != row.height) {
            row.rowRect = new UiRect(0, row.top, rowWidth, row.height);
        }
        return row.rowRect;
    }

    // 文本区矩形按值复用：扣除缩进与展开标记后的剩余区域
    private static <N> UiRect textRect(VisibleRow<N> row, int rowWidth, int markerX) {
        int textX = markerX + MARKER_WIDTH;
        int textWidth = Math.max(0, rowWidth - markerX - MARKER_WIDTH);
        if (row.textRect.x() != textX || row.textRect.y() != row.top
                || row.textRect.width() != textWidth || row.textRect.height() != row.height) {
            row.textRect = new UiRect(textX, row.top, textWidth, row.height);
        }
        return row.textRect;
    }

    // 递归展平


    // 屏幕坐标换算为内容坐标下的可见行下标；行高是否等高由 indexAtContentY 的二分决定
    private int indexAt(double mouseX, double mouseY) {
        if (!scrollView.canHoverContent(mouseX, mouseY)) {
            return -1;
        }
        return indexAtContentY(RowGeometry.contentY(scrollView, mouseY));
    }

    // 非等高行按累计位置二分查找，鼠标与翻页不再按固定行高猜测。
    private int indexAtContentY(int contentY) {
        int low = 0, high = visibleRows.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            VisibleRow<T> row = visibleRows.get(middle);
            if (contentY < row.top) high = middle - 1;
            else if (contentY >= row.top + row.height) low = middle + 1;
            else return middle;
        }
        return -1;
    }

    // 宿主的行提示使用与控件点击相同的命中结果。
    public UiTreeNode<T> nodeAt(double mouseX, double mouseY) {
        int index = indexAt(mouseX, mouseY);
        return index < 0 ? null : visibleRows.get(index).node;
    }

    // 宿主行内控件使用与 renderer 相同的缩进和滚动坐标，不自行猜测行号。
    public UiRect nodeContentBounds(UiTreeNode<T> node) {
        for (VisibleRow<T> row : visibleRows) if (row.node == node) {
            int inset = row.depth * INDENT_WIDTH + MARKER_WIDTH;
            return new UiRect(scrollView.viewport().x() + inset,
                    scrollView.viewport().y() + row.top - scrollView.offset(),
                    Math.max(0, RowGeometry.contentWidth(scrollView) - inset), row.height);
        }
        return new UiRect(0, 0, 0, 0);
    }

    // 选中节点在可见行中的下标，未选中时为 -1
    private int selectedIndex() {
        for (int i = 0; i < visibleRows.size(); i++) if (visibleRows.get(i).node == selectedNode()) return i;
        return -1;
    }

    // 滚动到指定可见行
    private void ensureVisible(int index) {
        if (index < 0 || index >= visibleRows.size()) {
            return;
        }
        VisibleRow<T> row = visibleRows.get(index);
        scrollView.ensureVisible(new UiRect(0, row.top, Math.max(0, scrollView.viewport().width()), row.height));
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
        theme.drawInset(graphics, bounds);
        UiRect viewport = scrollView.viewport();
        if (visibleRows.isEmpty()) {
            UiFlatText.drawCentered(graphics, font, emptyMessage, viewport.x() + viewport.width() / 2,
                    viewport.y() + Math.max(0, viewport.height() / 2 - 4), theme.palette().textSecondary());
            return;
        }
        int hoveredIndex = indexAt(mouseX, mouseY);
        int rowWidth = RowGeometry.contentWidth(scrollView);
        scrollView.push(graphics);
        int firstIndex = Math.max(0, indexAtContentY(scrollView.offset()));
        for (int i = firstIndex; i < visibleRows.size(); i++) {
            VisibleRow<T> visibleRow = visibleRows.get(i);
            if (visibleRow.top >= scrollView.offset() + viewport.height()) break;
            UiRect row = rowRect(visibleRow, rowWidth);
            boolean selected = visibleRow.node == selectedNode();
            if (selected) {
                theme.drawSelection(graphics, row);
            } else if (i == hoveredIndex) {
                graphics.fill(row.x(), row.y(), row.right(), row.bottom(), theme.palette().controlHover());
            }
            int markerX = visibleRow.depth * INDENT_WIDTH;
            if (!visibleRow.node.isLeaf()) {
                drawTriangle(graphics, markerX + 1, row.y() + row.height() / 2,
                        isExpanded(visibleRow.node));
            }
            UiRect textRow = textRect(visibleRow, rowWidth, markerX);
            nodeRenderer.renderNode(graphics, font, visibleRow.node, textRow, visibleRow.depth, selected, i == hoveredIndex, focused);
        }
        scrollView.pop(graphics);
        if (focused) {
            theme.drawFocusOutline(graphics, bounds);
        }
        scrollView.renderScrollbar(graphics, theme.controlStyle(UiControlRole.SECONDARY));
    }

    // ---- 输入 ----

    // 像素三角不依赖字体字形；叶子只保留缩进，不绘制虚假的展开按钮。
    private void drawTriangle(GuiGraphics graphics, int x, int centerY, boolean expanded) {
        UiTreeRowMarker.draw(graphics, x, centerY, expanded, theme.palette().textPrimary());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !bounds.contains(mouseX, mouseY) || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
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
        long now = Util.getMillis();
        boolean doubleClick = visibleRow.node == lastClickNode && now - lastClickTime <= DOUBLE_CLICK_MS;
        lastClickNode = visibleRow.node;
        lastClickTime = now;
        setSelectedNode(visibleRow.node);
        if (UiTreeRowMarker.hit(contentX, visibleRow.depth, INDENT_WIDTH, MARKER_WIDTH) && !visibleRow.node.isLeaf()) {
            lastClickNode = null;
            toggleExpanded(visibleRow.node);
            return true;
        }
        if (doubleClick) {
            lastClickNode = null;
            activate();
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
        if (!visible || visibleRows.isEmpty()) return false;
        UiTreeNavigation.Intent intent = UiTreeNavigation.fromKey(keyCode);
        if (intent != null) {
            int page = Math.max(1, scrollView.viewport().height() / Math.max(1, rowHeight));
            int index = selectedIndex();
            if (index >= 0 && (intent == UiTreeNavigation.Intent.PAGE_UP || intent == UiTreeNavigation.Intent.PAGE_DOWN)) {
                int direction = intent == UiTreeNavigation.Intent.PAGE_UP ? -1 : 1;
                int y = visibleRows.get(index).top + direction * Math.max(1, scrollView.viewport().height());
                page = Math.max(1, Math.abs(indexAtContentY(Math.clamp(y, 0, visibleRows.getLast().top)) - index));
            }
            UiTreeNode<T> previous = selectedNode();
            view = UiTreeNavigation.navigate(navigationRows, view, intent, page);
            rebuild();
            ensureVisibleFor(selectedNode());
            if (previous != selectedNode() && onSelectionChanged != null) onSelectionChanged.accept(selectedNode());
            return true;
        }
        return (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && activate();
    }

    private void toggleExpanded(UiTreeNode<T> node) {
        UiTreePath path = projection.pathOf(node);
        if (path == null) return;
        view = view.fold(path, isExpanded(node));
        rebuild();
        ensureVisibleFor(node);
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
        UiTreeNode<T> selected = selectedNode();
        if (!visible || selected == null) {
            return false;
        }
        if (!selected.isLeaf()) {
            toggleExpanded(selected);
            return true;
        }
        if (onActivate == null) return false;
        onActivate.accept(selected);
        return true;
    }

    @Override
    public Component accessibleName() {
        return emptyMessage;
    }
}
