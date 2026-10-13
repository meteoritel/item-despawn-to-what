package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiControlRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiScrollView;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/***
 * 条件树缩进摘要：只读展示树干与叶条件名，并作为进入画布编辑器的入口。
 * <p>本控件不提供结构编辑、拖拽与参数输入：点击节点只选中该节点并通过
 * {@link #setOnOpenCanvas(Consumer)} 通知宿主打开画布；展开标记用于折叠展示，不改变结构。
 * 条件树的结构与参数编辑全部在 {@link UiConditionGraphEditor}（画布与其内联编辑框）完成。</p>
 * <p>结构校验与违规项来自 {@link ConditionTreeInspection}，与画布共用同一份判定。
 * 宿主注入类型与路径常量见 {@link ConditionTreeContract}；本类只实现其中的
 * {@link ConditionTreeContract.Navigation}，向宿主暴露选中项语义。
 * <ul>
 *   <li>鼠标：单击节点进入画布，单击展开 / 收起标记（▼ 已展开、▶ 已收起）折叠展示，滚轮滚动。</li>
 *   <li>键盘：↑/↓ 移动焦点，←/→ 折叠或进入子节点，Enter/Space 进入画布，
 *       PgUp/PgDn/Home/End 快速移动。</li>
 * </ul>
 */
public final class UiConditionTreeSummary implements UiWidget, UiFocusTarget, ConditionTreeContract.Navigation {

    // 每层缩进宽度
    private static final int INDENT_WIDTH = 10;
    // 展开标记区宽度
    private static final int MARKER_WIDTH = 9;
    // 标记水平起点（相对标记区左缘）
    private static final int MARKER_INSET = 1;
    // 展开 / 收起标记的扫描线层数、最大宽度与半高，与 UiTreeView 的像素三角同形同尺寸
    private static final int MARK_SCANLINES = 4;
    private static final int MARK_SPAN = 7;
    private static final int MARK_HALF_HEIGHT = 3;
    // 层级引导线相对标记区左缘的偏移：对齐上一层标记区中心，使同一分支的引导线连成一条竖线
    private static final int GUIDE_INSET = 6;
    // 默认行高
    private static final int DEFAULT_ROW_HEIGHT = 12;
    // 滚动条预留宽度（与 kit 滚动条宽度保持一致）
    private static final int SCROLLBAR_RESERVE = 6;

    // 展平后的可见行
    private record Row(@Nullable ConditionNode node, String path, int depth, int parentIndex,
                       boolean incomplete, boolean overflow, boolean deep) {
    }

    // 注入的宿主主题
    private final UiHostTheme theme;
    // 滚动与裁剪
    private final UiScrollView scrollView = new UiScrollView();
    // 当前条件表达式
    private ConditionExpression expression = ConditionExpression.EMPTY;
    // 控件矩形
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    // 展平后的可见行
    private final List<Row> rows = new ArrayList<>();
    // 全树节点信息独立于可见行，折叠不会改变计数和违规标记。
    private final Map<String, Row> allNodes = new HashMap<>();
    // 折叠的节点路径
    private final Set<String> collapsed = new HashSet<>();
    // 当前违规项
    private final List<ConditionTreeInspection.Issue> issues = new ArrayList<>();
    // 已扫描的表达式，避免重复校验
    private @Nullable ConditionExpression inspectedExpression;
    // 选中的节点路径
    private @Nullable String selectedPath;
    // 悬停行下标，-1 表示无悬停
    private int hoveredIndex = -1;
    // 行高
    private int rowHeight = DEFAULT_ROW_HEIGHT;
    // 条件类型选项
    private List<ConditionTreeContract.TypeOption> typeOptions = List.of();
    // 空树提示
    private Component emptyMessage = Component.translatable("gui.itemdespawntowhat.edit.tree.add_placeholder");
    // 进入画布的入口回调，参数为节点的树路径
    private @Nullable Consumer<String> onOpenCanvas;
    // 是否持有键盘焦点
    private boolean focused;
    // 是否可见
    private boolean visible = true;
    // 是否可交互（只读摘要中表示是否允许导航与进入画布）
    private boolean enabled = true;

    // 字体由 render 接收；保留构造参数以兼容现有组件 API。
    public UiConditionTreeSummary(UiHostTheme theme, @SuppressWarnings("unused") Font font) {
        this.theme = UiHostThemes.require(theme);
    }

    // 设置进入画布的入口回调；控件不直接持有编辑器
    public UiConditionTreeSummary setOnOpenCanvas(Consumer<String> handler) {
        this.onOpenCanvas = handler;
        return this;
    }

    // ---- 数据 ----

    // 当前表达式
    public ConditionExpression expression() {
        return expression;
    }

    // 外部设置表达式：不触发变更回调，避免与宿主形成回环
    public void setExpression(ConditionExpression newExpression) {
        this.expression = newExpression == null ? ConditionExpression.EMPTY : newExpression;
        rebuild();
    }

    // 结构是否完整（空表达式视为合法）
    public boolean isValid() {
        return expression.isStructurallyValid() && issues.isEmpty();
    }

    public int nodeCount() {
        return expression.nodeCount();
    }

    public int leafCount() {
        return expression.leafCount();
    }

    public int depth() {
        return expression.depth();
    }

    // 违规项明细（只读）
    public List<ConditionTreeInspection.Issue> issues() {
        return List.copyOf(issues);
    }

    // ---- 配置 ----

    // 设置条件类型选项
    public UiConditionTreeSummary setTypeOptions(List<ConditionTreeContract.TypeOption> options) {
        this.typeOptions = List.copyOf(options);
        return this;
    }

    // 设置空表达式提示
    public UiConditionTreeSummary setEmptyMessage(Component message) {
        this.emptyMessage = message;
        return this;
    }

    // 设置行高
    public UiConditionTreeSummary setRowHeight(int newRowHeight) {
        this.rowHeight = Math.max(9, newRowHeight);
        rebuild();
        return this;
    }

    // 设置是否可交互；禁用时立即释放焦点
    public void setEnabled(boolean next) {
        enabled = next;
        if (!next) {
            focused = false;
        }
    }

    public UiConditionTreeSummary setVisible(boolean newVisible) {
        this.visible = newVisible;
        return this;
    }

    // ---- 视图状态 ----

    // 展开全部
    public void expandAll() {
        collapsed.clear();
        rebuild();
    }

    // 将整棵树折叠为根节点
    public void collapseAll() {
        collapsed.clear();
        if (expression.root() != null) {
            collapsed.add(ConditionTreeContract.ROOT_PATH);
        }
        rebuild();
    }

    // 选中的节点路径
    @Override
    public @Nullable String selectedPath() {
        return selectedPath;
    }

    // 选中的节点
    @Override
    public @Nullable ConditionNode selectedNode() {
        Row row = selectedPath == null ? null : allNodes.get(selectedPath);
        return row == null ? null : row.node();
    }

    // 可见行数
    public int rowCount() {
        return rows.size();
    }

    // 节点或其参数字段路径均可定位；先展开祖先再滚动到节点。
    @Override
    public void selectPath(@Nullable String path) {
        if (path == null) {
            selectedPath = null;
            return;
        }
        String nodePath = allNodes.keySet().stream()
                .filter(candidate -> path.equals(candidate) || path.startsWith(candidate + "."))
                .max(Comparator.comparingInt(String::length)).orElse(null);
        if (nodePath == null) {
            return;
        }
        collapsed.removeIf(ancestor -> nodePath.startsWith(ancestor + "."));
        selectedPath = nodePath;
        rebuild();
        ensureVisible(indexOfPath(nodePath));
    }

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
        rows.clear();
        ConditionNode root = expression.root();
        if (inspectedExpression != expression) {
            issues.clear();
            allNodes.clear();
            if (root != null) {
                inspect(root, ConditionTreeContract.ROOT_PATH, 0, new int[2]);
            }
            inspectedExpression = expression;
        }
        if (root != null) {
            append(root, ConditionTreeContract.ROOT_PATH, 0, -1);
        }
        if (selectedPath != null && !allNodes.containsKey(selectedPath) && indexOfPath(selectedPath) < 0) {
            selectedPath = rows.isEmpty() ? null : rows.get(Math.clamp(hoveredIndex, 0, rows.size() - 1)).path();
        }
        if (hoveredIndex >= rows.size()) {
            hoveredIndex = -1;
        }
        scrollView.setContentHeight(rows.size() * rowHeight);
        scrollView.setScrollbarVisible(rows.size() * rowHeight > scrollView.viewport().height());
    }

    // 深度优先扫描完整表达式；占位行不计为真实节点。
    private void inspect(ConditionNode node, String path, int depthOffset, int[] counters) {
        counters[0]++;
        int nodeOrdinal = counters[0];
        int leafOrdinal = -1;
        if (node instanceof ConditionNode.Leaf) {
            counters[1]++;
            leafOrdinal = counters[1];
        }
        int depth = depthOffset + 1;
        allNodes.put(path, new Row(node, path, depthOffset, -1,
                ConditionTreeInspection.isIncomplete(node),
                ConditionTreeInspection.isOverflow(depth, nodeOrdinal, leafOrdinal),
                ConditionTreeInspection.isDeep(depth)));
        issues.addAll(ConditionTreeInspection.nodeIssues(node, path, depth, nodeOrdinal, leafOrdinal));
        if (node instanceof ConditionNode.AllOf all) {
            inspectTerms(all.terms(), path, depthOffset, counters);
        } else if (node instanceof ConditionNode.AnyOf any) {
            inspectTerms(any.terms(), path, depthOffset, counters);
        } else if (node instanceof ConditionNode.Inverted inverted && inverted.term() != null) {
            inspect(inverted.term(), path + "." + RuleFields.TERM, depthOffset + 1, counters);
        }
    }

    private void inspectTerms(List<ConditionNode> terms, String path, int depthOffset, int[] counters) {
        for (int i = 0; i < terms.size(); i++) {
            inspect(terms.get(i), path + "." + RuleFields.TERMS + "[" + i + "]", depthOffset + 1, counters);
        }
    }

    // 只投影展开节点；标记和序号来自全树扫描，不受折叠影响。
    private void append(@Nullable ConditionNode node, String path, int depth, int parentIndex) {
        if (node == null) {
            rows.add(new Row(null, path, depth, parentIndex, false, false, false));
            return;
        }
        Row info = allNodes.get(path);
        int index = rows.size();
        rows.add(new Row(node, path, depth, parentIndex, info.incomplete(), info.overflow(), info.deep()));
        if (isCollapsed(path)) {
            return;
        }
        if (node instanceof ConditionNode.AllOf(var allTerms)) {
            if (allTerms.isEmpty()) {
                append(null, path + "." + RuleFields.TERMS + "[0]", depth + 1, index);
            }
            for (int i = 0; i < allTerms.size(); i++) {
                append(allTerms.get(i), path + "." + RuleFields.TERMS + "[" + i + "]", depth + 1, index);
            }
        } else if (node instanceof ConditionNode.AnyOf(var anyTerms)) {
            if (anyTerms.isEmpty()) {
                append(null, path + "." + RuleFields.TERMS + "[0]", depth + 1, index);
            }
            for (int i = 0; i < anyTerms.size(); i++) {
                append(anyTerms.get(i), path + "." + RuleFields.TERMS + "[" + i + "]", depth + 1, index);
            }
        } else if (node instanceof ConditionNode.Inverted(var invertedTerm)) {
            append(invertedTerm, path + "." + RuleFields.TERM, depth + 1, index);
        }
    }

    private boolean isCollapsed(String path) {
        return collapsed.contains(path);
    }

    private boolean unfold(String path) {
        return collapsed.remove(path);
    }

    private void fold(String path) {
        collapsed.add(path);
    }

    // 内容区可用宽度（扣除滚动条）
    private int contentWidth() {
        int width = scrollView.viewport().width();
        if (scrollView.isScrollbarVisible()) {
            width -= SCROLLBAR_RESERVE;
        }
        return Math.max(0, width);
    }

    // 屏幕坐标换算为可见行下标
    private int indexAt(double mouseX, double mouseY) {
        UiRect viewport = scrollView.viewport();
        if (!viewport.contains(mouseX, mouseY)) {
            return -1;
        }
        int contentY = (int) (mouseY - viewport.y()) + scrollView.offset();
        int index = contentY / Math.max(1, rowHeight);
        return index >= 0 && index < rows.size() ? index : -1;
    }

    // 路径对应的可见行下标，未找到返回 -1
    private int indexOfPath(String path) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).path().equals(path)) {
                return i;
            }
        }
        return -1;
    }

    // 滚动到指定可见行
    private void ensureVisible(int index) {
        if (index < 0 || index >= rows.size()) {
            return;
        }
        scrollView.ensureVisible(new UiRect(0, index * rowHeight, Math.max(0, scrollView.viewport().width()), rowHeight));
    }

    // 选中行
    private @Nullable Row selectedRow() {
        if (selectedPath == null) {
            return null;
        }
        int index = indexOfPath(selectedPath);
        return index < 0 ? null : rows.get(index);
    }

    // 一屏可见行数
    private int visibleRowCount() {
        return Math.max(1, scrollView.viewport().height() / Math.max(1, rowHeight));
    }

    // 是否有子节点
    private static boolean hasChildren(@Nullable ConditionNode node) {
        return node instanceof ConditionNode.Inverted || node instanceof ConditionNode.AllOf
                || node instanceof ConditionNode.AnyOf;
    }

    // ---- 渲染 ----

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        if (!visible || bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        theme.drawInset(graphics, bounds);
        UiRect viewport = scrollView.viewport();
        hoveredIndex = indexAt(mouseX, mouseY);
        if (rows.isEmpty()) {
            String empty = TextScroll.trimToWidth(renderFont, emptyMessage.getString(), Math.max(0, viewport.width() - 8));
            graphics.drawString(renderFont, empty, viewport.x() + 4,
                    viewport.y() + Math.max(0, viewport.height() / 2 - 4), theme.palette().textSecondary(), false);
        } else {
            int rowWidth = contentWidth();
            scrollView.push(graphics);
            for (int i = 0; i < rows.size(); i++) {
                int rowY = i * rowHeight;
                if (rowY + rowHeight < scrollView.offset() || rowY > scrollView.offset() + viewport.height()) {
                    continue;
                }
                drawRow(graphics, renderFont, rows.get(i), i, rowY, rowWidth);
            }
            scrollView.pop(graphics);
        }
        if (focused) {
            theme.drawFocusOutline(graphics, bounds);
        }
        scrollView.renderScrollbar(graphics, theme.controlStyle(UiControlRole.SECONDARY));
    }

    // 绘制一行
    private void drawRow(GuiGraphics graphics, Font renderFont, Row row, int index, int rowY, int rowWidth) {
        boolean selected = row.path().equals(selectedPath);
        if (selected) {
            theme.drawSelection(graphics, new UiRect(0, rowY, rowWidth, rowHeight));
            theme.drawSelectMarker(graphics, new UiRect(0, rowY, rowWidth, rowHeight));
        } else if (index == hoveredIndex) {
            graphics.fill(0, rowY, rowWidth, rowY + rowHeight, theme.palette().controlHover());
        }
        int markerX = theme.selectMarkerWidth() + row.depth() * INDENT_WIDTH;
        // 层级引导：上一层标记列上的 1 像素竖线，贯穿本行；用更浅的正文色与标记拉开层次
        if (row.depth() > 0) {
            int guideX = markerX - GUIDE_INSET;
            graphics.fill(guideX, rowY, guideX + 1, rowY + rowHeight, theme.palette().textDisabled());
        }
        if (hasChildren(row.node())) {
            drawMark(graphics, markerX + MARKER_INSET, rowY + rowHeight / 2, !isCollapsed(row.path()));
        }
        int textX = markerX + MARKER_WIDTH;
        int color = issues.stream().anyMatch(issue -> issue.path().equals(row.path())
                || issue.path().startsWith(row.path() + ".")) ? theme.palette().danger() : theme.palette().textPrimary();
        String label = TextScroll.trimToWidth(renderFont, rowLabel(row).getString(),
                Math.max(0, rowWidth - textX - 2));
        graphics.drawString(renderFont, label, textX, rowY + theme.textOffset(), color, false);
    }

    /**
     * 绘制展开 / 收起标记：{@code MARK_SCANLINES} 层 1 像素扫描线拼成的像素三角，不依赖字体字形。
     *
     * <p>几何与 {@link UiTreeView} 的树标记一致——已展开画向下的实心三角、已收起画向右的实心三角，
     * 让弹窗树与缩进摘要共用同一种标记；三角最大宽度落在标记区内，命中范围仍是
     * {@code markerX} 到 {@code markerX + MARKER_WIDTH}。</p>
     *
     * @param graphics 绘制目标
     * @param x        三角左缘横坐标（标记区左缘加 {@code MARKER_INSET}）
     * @param centerY  行的垂直中线
     * @param expanded 已展开时画向下三角，收起时画向右三角
     */
    private void drawMark(GuiGraphics graphics, int x, int centerY, boolean expanded) {
        for (int step = 0; step < MARK_SCANLINES; step++) {
            if (expanded) {
                graphics.fill(x + step, centerY - MARK_HALF_HEIGHT + 1 + step, x + MARK_SPAN - step,
                        centerY - MARK_HALF_HEIGHT + 2 + step, theme.palette().textPrimary());
            } else {
                graphics.fill(x + step + 1, centerY - MARK_HALF_HEIGHT + step, x + step + 2,
                        centerY + MARK_HALF_HEIGHT + 1 - step, theme.palette().textPrimary());
            }
        }
    }

    // 完整行文案供宿主提示，窄树中截断的文本仍可读取。
    public @Nullable Component tooltipAt(double mouseX, double mouseY) {
        if (!scrollView.canHoverContent(mouseX, mouseY)) {
            return null;
        }
        int index = indexAt(mouseX, mouseY);
        return index < 0 ? null : rowLabel(rows.get(index));
    }

    // 行文本：组节点用分组名（与「包成组」菜单共用 tree.node.*）与子项数，取反节点用与画布标题共用的运算符名
    // （tree.operator.not），叶节点只显示条件名，并附不完整/超限标记
    private Component rowLabel(Row row) {
        ConditionNode node = row.node();
        Component label;
        if (node == null) {
            return Component.translatable("gui.itemdespawntowhat.edit.tree.add_placeholder");
        } else if (node instanceof ConditionNode.AllOf all) {
            label = Component.translatable("gui.itemdespawntowhat.edit.tree.group_label",
                    Component.translatable("gui.itemdespawntowhat.edit.tree.node.all"), all.terms().size());
        } else if (node instanceof ConditionNode.AnyOf any) {
            label = Component.translatable("gui.itemdespawntowhat.edit.tree.group_label",
                    Component.translatable("gui.itemdespawntowhat.edit.tree.node.any"), any.terms().size());
        } else if (node instanceof ConditionNode.Inverted) {
            label = Component.translatable("gui.itemdespawntowhat.edit.tree.operator.not");
        } else if (node instanceof ConditionNode.Leaf(var leafCondition)) {
            label = leafCondition == null
                    ? Component.translatable("gui.itemdespawntowhat.edit.tree.leaf_missing")
                    : typeLabel(leafCondition.type());
        } else {
            label = Component.empty();
        }
        if (row.incomplete()) {
            label = label.copy().append(" ").append(Component.translatable("gui.itemdespawntowhat.edit.tree.incomplete"));
        }
        if (row.overflow()) {
            label = label.copy().append(" ").append(Component.translatable("gui.itemdespawntowhat.edit.tree.overflow_mark"));
        } else if (row.deep()) {
            // 超限时已有超限标记，避免同一行堆叠两个标记
            label = label.copy().append(" ").append(Component.translatable("gui.itemdespawntowhat.edit.tree.deep_mark"));
        }
        return label;
    }

    // 条件类型标签：优先使用宿主注入的选项标签，其次走本地化 key，最后回退原始 id
    private Component typeLabel(@Nullable ResourceLocation type) {
        if (type == null) {
            return Component.translatable("gui.itemdespawntowhat.edit.tree.leaf_missing");
        }
        for (ConditionTreeContract.TypeOption option : typeOptions) {
            if (option.id().equals(type)) {
                return option.label();
            }
        }
        return TypeLabels.conditionLabel(type);
    }

    // ---- 输入：只做导航与进入画布 ----

    // 选中并进入画布：只回调宿主，不在摘要内改变结构
    private void openCanvas(String path) {
        selectedPath = path;
        Consumer<String> handler = onOpenCanvas;
        if (handler != null) {
            handler.accept(path);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !enabled || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (scrollView.mousePressed(mouseX, mouseY, button)) {
            return true;
        }
        int index = indexAt(mouseX, mouseY);
        if (index < 0) {
            // 空白处：进入画布并定位根节点
            openCanvas(ConditionTreeContract.ROOT_PATH);
            return true;
        }
        Row row = rows.get(index);
        UiRect viewport = scrollView.viewport();
        int contentX = (int) (mouseX - viewport.x());
        int markerX = theme.selectMarkerWidth() + row.depth() * INDENT_WIDTH;
        selectedPath = row.path();
        if (row.node() != null && hasChildren(row.node()) && contentX >= markerX && contentX <= markerX + MARKER_WIDTH) {
            // 展开标记只切换展示，不进入画布
            if (!unfold(row.path())) {
                fold(row.path());
            }
            rebuild();
            return true;
        }
        openCanvas(row.path());
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (scrollView.isDragging()) {
            scrollView.mouseDragged(mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!visible) {
            return false;
        }
        scrollView.mouseReleased();
        return bounds.contains(mouseX, mouseY);
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
        if (!visible || !enabled) {
            return false;
        }
        if (rows.isEmpty()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                    || keyCode == GLFW.GLFW_KEY_SPACE) {
                openCanvas(ConditionTreeContract.ROOT_PATH);
                return true;
            }
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            return moveSelection(-1);
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            return moveSelection(1);
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            return moveSelection(-visibleRowCount());
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            return moveSelection(visibleRowCount());
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            return selectIndex(0);
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            return selectIndex(rows.size() - 1);
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            return collapseOrJumpToParent();
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            return expandOrEnterChild();
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_SPACE) {
            return activate();
        }
        return false;
    }

    // 上下移动选中行
    private boolean moveSelection(int delta) {
        if (rows.isEmpty()) {
            return false;
        }
        int index = selectedPath == null ? -1 : indexOfPath(selectedPath);
        int next = Math.clamp(index < 0 ? 0 : index + delta, 0, rows.size() - 1);
        return selectIndex(next);
    }

    private boolean selectIndex(int index) {
        if (index < 0 || index >= rows.size()) {
            return false;
        }
        selectedPath = rows.get(index).path();
        rebuild();
        ensureVisible(index);
        return true;
    }

    // 左键：已展开则折叠，否则跳到父节点
    private boolean collapseOrJumpToParent() {
        Row row = selectedRow();
        if (row == null) {
            return false;
        }
        if (hasChildren(row.node()) && !isCollapsed(row.path())) {
            fold(row.path());
            rebuild();
            return true;
        }
        if (row.parentIndex() >= 0 && row.parentIndex() < rows.size()) {
            selectedPath = rows.get(row.parentIndex()).path();
            rebuild();
            ensureVisible(row.parentIndex());
            return true;
        }
        return false;
    }

    // 右键：已折叠则展开，否则进入第一个子节点
    private boolean expandOrEnterChild() {
        Row row = selectedRow();
        if (row == null) {
            return false;
        }
        if (hasChildren(row.node()) && isCollapsed(row.path())) {
            unfold(row.path());
            rebuild();
            return true;
        }
        int index = indexOfPath(row.path());
        if (index >= 0 && index + 1 < rows.size() && rows.get(index + 1).parentIndex() == index) {
            selectedPath = rows.get(index + 1).path();
            rebuild();
            ensureVisible(index + 1);
            return true;
        }
        return false;
    }

    // ---- 焦点 ----

    @Override
    public boolean canFocus() {
        return visible && enabled;
    }

    @Override
    public void setFocused(boolean newFocused) {
        this.focused = newFocused;
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public boolean activate() {
        if (!enabled) {
            return false;
        }
        openCanvas(selectedPath == null ? ConditionTreeContract.ROOT_PATH : selectedPath);
        return true;
    }

    @Override
    public Component accessibleName() {
        return Component.translatable("gui.itemdespawntowhat.edit.tree.accessible_name");
    }
}
