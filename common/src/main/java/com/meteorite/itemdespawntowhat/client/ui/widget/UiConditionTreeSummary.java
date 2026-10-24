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
import com.meteorite.itemdespawntowhat.core.model.Condition;
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
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 条件树缩进摘要：只读展示树干、叶条件名与参数摘要，并作为进入画布编辑器的入口。
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

    private static final int INDENT_WIDTH = 10;
    private static final int MARKER_WIDTH = 9;
    private static final int MARKER_INSET = 1;
    // 扫描线层数、最大宽度与半高，与 UiTreeView 的像素三角同形同尺寸
    private static final int MARK_SCANLINES = 4;
    private static final int MARK_SPAN = 7;
    private static final int MARK_HALF_HEIGHT = 3;
    // 对齐上一层标记区中心，使同一分支的引导线连成一条竖线
    private static final int GUIDE_INSET = 6;
    private static final int DEFAULT_ROW_HEIGHT = 12;

    // 展平后的可见行；node 为空表示空组合的占位行
    private record Row(@Nullable ConditionNode node, String path, int depth, int parentIndex,
                       boolean incomplete, boolean overflow, boolean deep) {
    }

    private final UiHostTheme theme;
    private final UiScrollView scrollView = new UiScrollView();
    private ConditionExpression expression = ConditionExpression.EMPTY;
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private final List<Row> rows = new ArrayList<>();
    // 与可见行分开维护：折叠只影响 rows，不改变节点计数与违规标记
    private final Map<String, Row> allNodes = new HashMap<>();
    private final Set<String> collapsed = new HashSet<>();
    private final List<ConditionTreeInspection.Issue> issues = new ArrayList<>();
    // 按表达式扫描一次算出，绘制时按路径查表，避免逐行遍历 issues
    private final Set<String> flaggedPaths = new HashSet<>();
    // 键为节点路径，值已按行宽裁剪；表达式、注入选项、语言、字体或行宽变化时整批失效
    private final Map<String, String> rowLabels = new HashMap<>();
    private int rowLabelsEpoch = -1;
    private @Nullable Font rowLabelsFont;
    private @Nullable Object rowLabelsLanguage;
    private int rowLabelsWidth = -1;
    // 表达式重新校验或注入的类型/参数摘要变化时自增，使行文案缓存失效
    private int contentEpoch;
    // 与字体、语言、可用宽度一起缓存
    private String emptyText = "";
    private int emptyTextWidth = -1;
    private @Nullable Font emptyTextFont;
    private @Nullable Object emptyTextLanguage;
    // 避免内容未变时重复校验
    private @Nullable ConditionExpression inspectedExpression;
    private @Nullable String selectedPath;
    // -1 表示无悬停
    private int hoveredIndex = -1;
    // 选中行每帧要画两次（底色与标记），几何不变时复用同一矩形
    private UiRect selectedRowRect = new UiRect(0, 0, 0, 0);
    private int rowHeight = DEFAULT_ROW_HEIGHT;
    private List<ConditionTreeContract.TypeOption> typeOptions = List.of();
    private Component emptyMessage = Component.translatable(TypeLabels.UI_PREFIX + "tree.add_placeholder");
    // 宿主注入；为空时叶行只显示条件名
    private @Nullable Function<ConditionNode.Leaf, Component> leafDetail;
    // 参数为节点的树路径
    private @Nullable Consumer<String> onOpenCanvas;
    private boolean focused;
    private boolean visible = true;
    // 禁用时拒绝导航与进入画布
    private boolean enabled = true;

    /**
     * 创建条件树摘要控件。
     *
     * @param theme 宿主主题，不可为 null
     * @param font  保留以兼容组件构造签名；渲染字体由 {@link #render} 传入
     */
    public UiConditionTreeSummary(UiHostTheme theme, @SuppressWarnings("unused") Font font) {
        this.theme = UiHostThemes.require(theme);
    }

    /**
     * 设置进入画布的入口回调。控件不直接持有编辑器，点击节点时只回调节点路径。
     *
     * @param handler 接收节点树路径的回调
     */
    public UiConditionTreeSummary setOnOpenCanvas(Consumer<String> handler) {
        this.onOpenCanvas = handler;
        return this;
    }

    // ---- 数据 ----

    /**
     * 当前表达式。
     *
     * @return 当前表达式；未设置时为 {@link ConditionExpression#EMPTY}
     */
    public ConditionExpression expression() {
        return expression;
    }

    /**
     * 设置表达式并重建可见行。不触发变更回调，避免与宿主形成回环。
     *
     * @param newExpression 新表达式；null 按空表达式处理
     */
    public void setExpression(ConditionExpression newExpression) {
        ConditionExpression next = newExpression == null ? ConditionExpression.EMPTY : newExpression;
        // ConditionExpression 是不可变 record：内容相等时可见行不会变化，跳过整树展平
        if (next.equals(expression)) {
            return;
        }
        this.expression = next;
        rebuild();
    }

    /**
     * 结构是否完整。空表达式视为合法。
     *
     * @return 结构合法且没有违规项时为 true
     */
    public boolean isValid() {
        return expression.isStructurallyValid() && issues.isEmpty();
    }

    /**
     * 表达式深度。
     *
     * @return 根节点深度为 1，空表达式为 0
     */
    public int depth() {
        return expression.depth();
    }

    /**
     * 违规项明细。
     *
     * @return 不可变的违规项快照
     */
    public List<ConditionTreeInspection.Issue> issues() {
        return List.copyOf(issues);
    }

    // ---- 配置 ----

    /**
     * 注入条件类型选项。选项标签参与行文案，重新注入会使文案缓存失效。
     *
     * @param options 可选条件类型，不可为 null
     */
    public void setTypeOptions(List<ConditionTreeContract.TypeOption> options) {
        this.typeOptions = List.copyOf(options);
        contentEpoch++;
    }

    /**
     * 设置叶条件的参数摘要。回调返回 null 或空文本时，叶行只显示条件名。
     *
     * @param detail 参数摘要回调；null 表示不显示参数
     */
    public void setLeafDetail(@Nullable Function<ConditionNode.Leaf, Component> detail) {
        this.leafDetail = detail;
        // 换回调后行文案必须重算
        contentEpoch++;
    }

    /**
     * 设置空树提示文案。
     *
     * @param message 提示文案
     */
    public void setEmptyMessage(Component message) {
        this.emptyMessage = message;
        // 文案参与裁剪缓存：下次绘制必须重新裁剪
        emptyTextWidth = -1;
    }

    /**
     * 设置行高。低于 9 的值按 9 处理。
     *
     * @param newRowHeight 新行高，单位为像素
     */
    public UiConditionTreeSummary setRowHeight(int newRowHeight) {
        int next = Math.max(9, newRowHeight);
        // 行高没变时行几何不变，无需重建可见行
        if (next == rowHeight) {
            return this;
        }
        this.rowHeight = next;
        rebuild();
        return this;
    }

    /**
     * 设置是否可交互。禁用时立即释放焦点。
     *
     * @param next false 表示只读且不接收键盘操作
     */
    public void setEnabled(boolean next) {
        enabled = next;
        if (!next) {
            focused = false;
        }
    }

    /**
     * 设置是否可见。
     *
     * @param newVisible false 时不渲染、不接收输入
     */
    public UiConditionTreeSummary setVisible(boolean newVisible) {
        this.visible = newVisible;
        return this;
    }

    // ---- 视图状态 ----

    /**
     * 选中的节点路径。
     *
     * @return 节点路径；无选中时为 null
     */
    @Override
    public @Nullable String selectedPath() {
        return selectedPath;
    }

    /**
     * 选中的节点。
     *
     * @return 选中节点；无选中或选中的是占位行时为 null
     */
    @Override
    public @Nullable ConditionNode selectedNode() {
        Row row = selectedPath == null ? null : allNodes.get(selectedPath);
        return row == null ? null : row.node();
    }

    /**
     * 当前可见行数，受折叠影响。
     *
     * @return 可见行数
     */
    public int rowCount() {
        return rows.size();
    }

    /**
     * 按路径选中节点。参数为节点路径或其下参数字段路径：命中字段路径时选中最近的祖先节点，
     * 并展开该节点的所有祖先、滚动到可见位置。
     *
     * @param path 条件树路径；null 清除选中
     */
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
        boolean collapsedChanged = collapsed.removeIf(ancestor -> nodePath.startsWith(ancestor + "."));
        boolean selectionChanged = !nodePath.equals(selectedPath);
        selectedPath = nodePath;
        // 只有展开态或选中项真的变化才需要重排可见行；滚动定位始终执行，命中路径语义不变
        if (collapsedChanged || selectionChanged) {
            rebuild();
        }
        ensureVisible(indexOfPath(nodePath));
    }

    @Override
    public UiRect bounds() {
        return bounds;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        int nextWidth = Math.max(0, width);
        int nextHeight = Math.max(0, height);
        // 几何是「视图」而不是内容：不变时仅保留现有可见行，不整树展平；
        // 内容变化统一走 rebuild()，所以早退不会漏掉行数或表达式变更。
        if (bounds.x() == x && bounds.y() == y && bounds.width() == nextWidth && bounds.height() == nextHeight) {
            return;
        }
        this.bounds = new UiRect(x, y, nextWidth, nextHeight);
        scrollView.setViewport(x + 1, y + 1, Math.max(0, nextWidth - 2), Math.max(0, nextHeight - 2));
        scrollView.setStep(Math.max(1, rowHeight * 2));
        updateScrollRange();
    }

    // 重新展平可见行并同步滚动范围；只在表达式、折叠态、选中路径等内容真的变化时调用
    private void rebuild() {
        // 行高可能已变（setRowHeight），滚动步长按行高派生，必须与可见行一起刷新
        scrollView.setStep(Math.max(1, rowHeight * 2));
        rows.clear();
        ConditionNode root = expression.root();
        if (inspectedExpression != expression) {
            issues.clear();
            allNodes.clear();
            if (root != null) {
                inspect(root, ConditionTreeContract.ROOT_PATH, 0, new int[2]);
            }
            inspectedExpression = expression;
            contentEpoch++;
            rebuildFlaggedPaths();
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
        updateScrollRange();
    }

    // 内容高度决定树高与滚动条；可见行或行高变化后统一在这里同步，调用方不必各自计算
    private void updateScrollRange() {
        int contentHeight = rows.size() * rowHeight;
        scrollView.setContentHeight(contentHeight);
        scrollView.setScrollbarVisible(contentHeight > scrollView.viewport().height());
    }

    // 违规标记按节点路径预计算：drawRow 只做一次集合查询，不再对每个可见行扫全部违规项
    private void rebuildFlaggedPaths() {
        flaggedPaths.clear();
        if (issues.isEmpty()) {
            return;
        }
        for (String candidate : allNodes.keySet()) {
            for (ConditionTreeInspection.Issue issue : issues) {
                if (issue.path().equals(candidate) || issue.path().startsWith(candidate + ".")) {
                    flaggedPaths.add(candidate);
                    break;
                }
            }
        }
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
        switch (node) {
            case ConditionNode.AllOf(var terms) -> inspectTerms(terms, path, depthOffset, counters);
            case ConditionNode.AnyOf(var terms) -> inspectTerms(terms, path, depthOffset, counters);
            case ConditionNode.Inverted(var term) -> {
                if (term != null) {
                    inspect(term, path + "." + RuleFields.TERM, depthOffset + 1, counters);
                }
            }
            case ConditionNode.Leaf ignored -> {
            }
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
        switch (node) {
            case ConditionNode.AllOf(var terms) -> appendTerms(terms, path, depth, index);
            case ConditionNode.AnyOf(var terms) -> appendTerms(terms, path, depth, index);
            case ConditionNode.Inverted(var term) -> append(term, path + "." + RuleFields.TERM, depth + 1, index);
            case ConditionNode.Leaf ignored -> {
            }
        }
    }

    // 空组合补一个占位行，其余子项按 terms[i] 路径展开
    private void appendTerms(List<ConditionNode> terms, String path, int depth, int parentIndex) {
        if (terms.isEmpty()) {
            append(null, path + "." + RuleFields.TERMS + "[0]", depth + 1, parentIndex);
        }
        for (int i = 0; i < terms.size(); i++) {
            append(terms.get(i), path + "." + RuleFields.TERMS + "[" + i + "]", depth + 1, parentIndex);
        }
    }

    private boolean isCollapsed(String path) {
        return collapsed.contains(path);
    }

    // 返回该节点此前是否处于折叠态
    private boolean unfold(String path) {
        return collapsed.remove(path);
    }

    private void fold(String path) {
        collapsed.add(path);
    }

    // 屏幕坐标换算为可见行下标；行内容与行距同为 rowHeight，固定行高直接整除
    private int indexAt(double mouseX, double mouseY) {
        if (!scrollView.canHoverContent(mouseX, mouseY)) {
            return -1;
        }
        return RowGeometry.fixedRowIndex(RowGeometry.contentY(scrollView, mouseY),
                rowHeight, rowHeight, rows.size());
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

    private void ensureVisible(int index) {
        if (index < 0 || index >= rows.size()) {
            return;
        }
        scrollView.ensureVisible(new UiRect(0, index * rowHeight, Math.max(0, scrollView.viewport().width()), rowHeight));
    }

    private @Nullable Row selectedRow() {
        if (selectedPath == null) {
            return null;
        }
        int index = indexOfPath(selectedPath);
        return index < 0 ? null : rows.get(index);
    }

    private int visibleRowCount() {
        return Math.max(1, scrollView.viewport().height() / Math.max(1, rowHeight));
    }

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
            String empty = emptyText(renderFont, Math.max(0, viewport.width() - 8));
            graphics.drawString(renderFont, empty, viewport.x() + 4,
                    viewport.y() + Math.max(0, viewport.height() / 2 - 4), theme.palette().textSecondary(), false);
        } else {
            int rowWidth = RowGeometry.contentWidth(scrollView);
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

    // 几何不变时复用同一矩形，避免每帧分配
    private UiRect selectionBounds(int rowY, int rowWidth) {
        if (selectedRowRect.y() != rowY || selectedRowRect.width() != rowWidth || selectedRowRect.height() != rowHeight) {
            selectedRowRect = new UiRect(0, rowY, rowWidth, rowHeight);
        }
        return selectedRowRect;
    }

    private void drawRow(GuiGraphics graphics, Font renderFont, Row row, int index, int rowY, int rowWidth) {
        boolean selected = row.path().equals(selectedPath);
        if (selected) {
            UiRect selection = selectionBounds(rowY, rowWidth);
            theme.drawSelection(graphics, selection);
            theme.drawSelectMarker(graphics, selection);
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
        int color = flaggedPaths.contains(row.path())
                ? theme.palette().danger() : theme.palette().textPrimary();
        String label = rowLabelText(row, renderFont, rowWidth, textX);
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

    /**
     * 悬停行的完整文案，供宿主显示提示；窄树中被截断的文本可由此读取。
     *
     * @param mouseX 鼠标横坐标
     * @param mouseY 鼠标纵坐标
     * @return 完整行文案；指针不在内容区或无命中行时为 null
     */
    public @Nullable Component tooltipAt(double mouseX, double mouseY) {
        if (!scrollView.canHoverContent(mouseX, mouseY)) {
            return null;
        }
        int index = indexAt(mouseX, mouseY);
        return index < 0 ? null : rowLabel(rows.get(index));
    }

    // 组节点用分组名（与「包成组」菜单共用 tree.node.*）与子项数，取反节点用与画布标题共用的运算符名
    // （tree.operator.not），叶节点显示条件名与参数摘要，并附不完整 / 超限标记
    private Component rowLabel(Row row) {
        ConditionNode node = row.node();
        if (node == null) {
            return Component.translatable(TypeLabels.UI_PREFIX + "tree.add_placeholder");
        }
        Component label = switch (node) {
            case ConditionNode.AllOf(var terms) -> Component.translatable(TypeLabels.UI_PREFIX + "tree.group_label",
                    Component.translatable(TypeLabels.UI_PREFIX + "tree.node.all"), terms.size());
            case ConditionNode.AnyOf(var terms) -> Component.translatable(TypeLabels.UI_PREFIX + "tree.group_label",
                    Component.translatable(TypeLabels.UI_PREFIX + "tree.node.any"), terms.size());
            case ConditionNode.Inverted ignored -> Component.translatable(TypeLabels.UI_PREFIX + "tree.operator.not");
            case ConditionNode.Leaf leaf -> leafLabel(leaf);
        };
        if (row.incomplete()) {
            label = label.copy().append(" ").append(Component.translatable(TypeLabels.UI_PREFIX + "tree.incomplete"));
        }
        if (row.overflow()) {
            label = label.copy().append(" ").append(Component.translatable(TypeLabels.UI_PREFIX + "tree.overflow_mark"));
        } else if (row.deep()) {
            // 超限时已有超限标记，避免同一行堆叠两个标记
            label = label.copy().append(" ").append(Component.translatable(TypeLabels.UI_PREFIX + "tree.deep_mark"));
        }
        return label;
    }

    // 行文案按路径缓存：同一行在同一语言、字体与行宽下只构造一次 Component 并裁剪
    private String rowLabelText(Row row, Font renderFont, int rowWidth, int textX) {
        ensureRowLabels(renderFont, rowWidth);
        String cached = rowLabels.get(row.path());
        if (cached != null) {
            return cached;
        }
        String label = TextScroll.trimToWidth(renderFont, rowLabel(row).getString(),
                Math.max(0, rowWidth - textX - 2));
        rowLabels.put(row.path(), label);
        return label;
    }

    // 行宽参与裁剪结果，语言参与本地化文本，字体参与测量：任一变化都重建整批行文案
    private void ensureRowLabels(Font renderFont, int rowWidth) {
        Object language = Language.getInstance();
        if (rowLabelsEpoch == contentEpoch && rowLabelsFont == renderFont
                && rowLabelsLanguage == language && rowLabelsWidth == rowWidth) {
            return;
        }
        rowLabels.clear();
        rowLabelsEpoch = contentEpoch;
        rowLabelsFont = renderFont;
        rowLabelsLanguage = language;
        rowLabelsWidth = rowWidth;
    }

    // 空树提示同样只在依赖变化时裁剪：绘制每帧都会走这里
    private String emptyText(Font renderFont, int width) {
        Object language = Language.getInstance();
        if (emptyTextWidth == width && emptyTextFont == renderFont && emptyTextLanguage == language) {
            return emptyText;
        }
        emptyText = TextScroll.trimToWidth(renderFont, emptyMessage.getString(), width);
        emptyTextWidth = width;
        emptyTextFont = renderFont;
        emptyTextLanguage = language;
        return emptyText;
    }

    // 叶标签：条件名（参数摘要）；缺条件或参数摘要为空时只留条件名
    private Component leafLabel(ConditionNode.Leaf leaf) {
        Condition condition = leaf.condition();
        if (condition == null) {
            return Component.translatable(TypeLabels.UI_PREFIX + "tree.leaf_missing");
        }
        Component name = typeLabel(condition.type());
        Function<ConditionNode.Leaf, Component> detail = leafDetail;
        Component params = detail == null ? null : detail.apply(leaf);
        return params == null || params.getString().isEmpty()
                ? name : Component.translatable(TypeLabels.UI_PREFIX + "summary.leaf", name, params);
    }

    // 条件类型标签：优先使用宿主注入的选项标签，其次走本地化 key，最后回退原始 id
    private Component typeLabel(@Nullable ResourceLocation type) {
        if (type == null) {
            return Component.translatable(TypeLabels.UI_PREFIX + "tree.leaf_missing");
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

    // ←：已展开则折叠，否则跳到父节点
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

    // →：已折叠则展开，否则进入第一个子节点
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
        return Component.translatable(TypeLabels.UI_PREFIX + "tree.accessible_name");
    }
}
