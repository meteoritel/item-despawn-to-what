package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiScrollView;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionLimits;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/***
 * 条件树编辑控件：直接编辑 {@link ConditionExpression} / {@link ConditionNode}，不引入平行数据模型。
 * <p>节点标记：组合节点显示 ALL / ANY / NOT，叶节点显示条件类型标签；
 * 空组合节点显示「不完整」占位，超限节点显示「超限」标记，违规明细通过 {@link #issues()} 暴露给宿主
 * （控件本身不弹窗、不提交、不写入）。
 * <ul>
 *   <li>鼠标：单击选中，点击 +/- 标记展开折叠，双击叶节点编辑参数，滚轮滚动。</li>
 *   <li>键盘：↑/↓ 移动焦点，←/→ 展开折叠，Enter/Space 激活（叶编辑参数、组合折叠），
 *       Delete/Backspace 删除，A 添加子条件，G 添加分组（Shift+G 为 ANY），N 包一层 NOT，
 *       按住 Shift 用 ↑/↓ 上移下移，PgUp/PgDn/Home/End 快速移动，Esc 关闭类型选择框。</li>
 * </ul>
 * <p>宿主注入条件类型选项与叶工厂：控件不访问注册表，也不解码参数。
 * 参数编辑通过 {@link #setOnEditLeaf(Consumer)} 回调交给表单层。
 */
public final class UiConditionTreeEditor implements UiWidget, UiFocusTarget {

    // 根节点的路径（与条件树 JSON 的字段名一致）
    public static final String ROOT_PATH = RuleFields.CONDITIONS;
    // 层级引导阈值（契约 §5.2：深度超过该值只在节点旁提示「层级较深」，不额外硬限）
    public static final int DEEP_HINT_DEPTH = 6;
    // 每层缩进宽度
    private static final int INDENT_WIDTH = 10;
    // 展开标记区宽度
    private static final int MARKER_WIDTH = 9;
    // 默认行高
    private static final int DEFAULT_ROW_HEIGHT = 12;
    // 滚动条预留宽度（与 kit 滚动条宽度保持一致）
    private static final int SCROLLBAR_RESERVE = 6;
    // 双击判定间隔（毫秒）
    private static final long DOUBLE_CLICK_MS = 300L;
    // 类型选择框最多显示的行数
    private static final int PICKER_MAX_ROWS = 8;
    // 类型选择框标题栏高度
    private static final int PICKER_HEADER = 14;
    // 类型选择框底部提示高度
    private static final int PICKER_FOOTER = 12;

    // 条件类型选项：由宿主注入，id 用于工厂与序列化，label 用于展示
    public record TypeOption(ResourceLocation id, Component label) {
    }

    // 新条件叶工厂：宿主按类型 id 生成默认参数；返回 null 表示该类型不支持可视化新增
    @FunctionalInterface
    public interface LeafFactory {
        @Nullable ConditionNode create(ResourceLocation type);
    }

    // 表达式变更监听：控件内所有结构操作完成后回调
    public interface Listener {
        void onExpressionChanged(ConditionExpression expression);
    }

    // 违规项种类
    public enum IssueKind {
        // 组合节点没有任何子项
        INCOMPLETE_GROUP,
        // 缺少条件对象
        MISSING_CONDITION,
        // 超过最大深度
        TOO_DEEP,
        // 超过最大节点数
        TOO_MANY_NODES,
        // 超过最大叶数
        TOO_MANY_LEAVES
    }

    // 违规项：种类 + 节点路径 + 可直接展示的文本
    public record Issue(IssueKind kind, String path, Component label) {
    }

    // 展平后的可见行
    private record Row(ConditionNode node, String path, int depth, int parentIndex, boolean incomplete,
                       boolean overflow, boolean deep, int leafOrdinal) {
    }

    // 字体
    private final Font font;
    // 滚动与裁剪
    private final UiScrollView scrollView = new UiScrollView();
    // 当前条件表达式
    private ConditionExpression expression = ConditionExpression.EMPTY;
    // 控件矩形
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    // 被折叠的节点路径（默认全部展开）
    private final Set<String> collapsed = new HashSet<>();
    // 展平后的可见行
    private final List<Row> rows = new ArrayList<>();
    // 当前违规项
    private final List<Issue> issues = new ArrayList<>();
    // 选中的节点路径
    private @Nullable String selectedPath;
    // 悬停行下标，-1 表示无悬停
    private int hoveredIndex = -1;
    // 行高
    private int rowHeight = DEFAULT_ROW_HEIGHT;
    // 条件类型选项
    private List<TypeOption> typeOptions = List.of();
    // 叶工厂
    private @Nullable LeafFactory leafFactory;
    // 叶参数编辑回调
    private @Nullable Consumer<ConditionNode.Leaf> onEditLeaf;
    // 表达式变更回调
    private @Nullable Listener listener;
    // 空树提示
    private Component emptyMessage = Component.translatable("gui.itemdespawntowhat.edit.tree.empty");
    // 是否持有键盘焦点
    private boolean focused;
    // 是否可见
    private boolean visible = true;
    // 上次点击时间与下标（双击判定）
    private long lastClickTime;
    private int lastClickIndex = -1;
    // 类型选择框状态
    private boolean pickerOpen;
    private @Nullable String pickerGroupPath;
    private int pickerIndex;
    private int pickerOffset;
    private UiRect pickerRect = new UiRect(0, 0, 0, 0);
    private UiRect pickerListRect = new UiRect(0, 0, 0, 0);

    // 最近一次结构改动对应的撤销操作 key（宿主据此显示真实的撤销操作名）
    private String lastOpKey = EditSession.OP_EDIT_CONDITIONS;

    public UiConditionTreeEditor(Font font) {
        this.font = font;
    }

    // 最近一次改动的撤销操作 key
    public String undoOpKey() {
        return lastOpKey;
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
        return expression.isStructurallyValid();
    }

    // 违规项明细（只读）
    public List<Issue> issues() {
        return List.copyOf(issues);
    }

    // 键盘提示文本，供宿主在状态栏展示
    public Component hint() {
        return Component.translatable("gui.itemdespawntowhat.edit.tree.hint");
    }

    // 是否正在显示类型选择框
    public boolean isPickerOpen() {
        return pickerOpen;
    }

    // 关闭类型选择框
    public void closePicker() {
        pickerOpen = false;
        pickerGroupPath = null;
    }

    // 设置条件类型选项
    public UiConditionTreeEditor setTypeOptions(List<TypeOption> options) {
        this.typeOptions = List.copyOf(options);
        return this;
    }

    // 设置叶工厂
    public UiConditionTreeEditor setLeafFactory(LeafFactory factory) {
        this.leafFactory = factory;
        return this;
    }

    // 设置叶参数编辑回调
    public UiConditionTreeEditor setOnEditLeaf(Consumer<ConditionNode.Leaf> callback) {
        this.onEditLeaf = callback;
        return this;
    }

    // 设置变更回调
    public UiConditionTreeEditor setListener(Listener newListener) {
        this.listener = newListener;
        return this;
    }

    // 设置空表达式提示
    public UiConditionTreeEditor setEmptyMessage(Component message) {
        this.emptyMessage = message;
        return this;
    }

    // 设置行高
    public UiConditionTreeEditor setRowHeight(int newRowHeight) {
        this.rowHeight = Math.max(9, newRowHeight);
        rebuild();
        return this;
    }

    // 设置是否可见
    public UiConditionTreeEditor setVisible(boolean newVisible) {
        this.visible = newVisible;
        return this;
    }

    // 展开全部
    public void expandAll() {
        collapsed.clear();
        rebuild();
    }

    // 折叠全部（根节点保留展开，否则只剩一行）
    public void collapseAll() {
        collapsed.clear();
        if (expression.root() != null) {
            collapsed.add(ROOT_PATH);
        }
        rebuild();
    }

    // 选中的节点路径
    public @Nullable String selectedPath() {
        return selectedPath;
    }

    // 选中的节点
    public @Nullable ConditionNode selectedNode() {
        Row row = selectedRow();
        return row == null ? null : row.node();
    }

    // 可见行数
    public int rowCount() {
        return rows.size();
    }

    // 选中指定路径的节点（路径不存在时忽略）
    public void selectPath(@Nullable String path) {
        if (path == null) {
            selectedPath = null;
            return;
        }
        for (Row row : rows) {
            if (row.path().equals(path)) {
                selectedPath = path;
                ensureVisible(indexOfPath(path));
                return;
            }
        }
    }

    // ---- 结构操作（工具栏与键盘共用） ----

    // 打开条件类型选择框，向当前目标分组追加一个条件
    public boolean beginAddCondition() {
        String groupPath = targetGroupPath();
        if (expression.root() == null) {
            groupPath = null;
        }
        pickerOpen = true;
        pickerGroupPath = groupPath;
        pickerIndex = 0;
        pickerOffset = 0;
        return true;
    }

    // 追加一个分组（allOf 为 true 表示 ALL，否则 ANY）
    public boolean addGroup(boolean allOf) {
        ConditionNode group = allOf ? new ConditionNode.AllOf(List.of()) : new ConditionNode.AnyOf(List.of());
        boolean added = addNode(group);
        if (added) {
            lastOpKey = EditSession.OP_ADD_GROUP;
        }
        return added;
    }

    // 给选中节点包一层 NOT
    public boolean wrapSelectedInNot() {
        Row row = selectedRow();
        if (row == null) {
            return false;
        }
        if (row.node() instanceof ConditionNode.Inverted) {
            return false;
        }
        lastOpKey = EditSession.OP_WRAP_NOT;
        applyAt(row.path(), node -> new ConditionNode.Inverted(node));
        return true;
    }

    // 删除选中节点（根节点被删除后表达式变为空）
    public boolean deleteSelected() {
        Row row = selectedRow();
        if (row == null) {
            return false;
        }
        if (row.parentIndex() < 0) {
            selectedPath = null;
            lastOpKey = EditSession.OP_DELETE_NODE;
            applyNode(null);
            return true;
        }
        Row parent = rows.get(row.parentIndex());
        String nextSelection = parent.path();
        lastOpKey = EditSession.OP_DELETE_NODE;
        applyAt(row.path(), node -> null);
        selectPath(nextSelection);
        return true;
    }

    // 在父分组内上移/下移选中节点
    public boolean moveSelected(int delta) {
        if (delta == 0) {
            return false;
        }
        Row row = selectedRow();
        if (row == null || row.parentIndex() < 0) {
            return false;
        }
        Row parent = rows.get(row.parentIndex());
        if (!(parent.node() instanceof ConditionNode.AllOf) && !(parent.node() instanceof ConditionNode.AnyOf)) {
            return false;
        }
        int index = termIndexOf(row);
        if (index < 0) {
            return false;
        }
        int target = index + delta;
        String parentPath = parent.path();
        lastOpKey = EditSession.OP_MOVE_NODE;
        applyAt(parentPath, node -> swapTerms(node, index, target));
        selectPath(parentPath + ".terms[" + target + "]");
        return true;
    }

    // 切换选中分组的 ALL / ANY
    public boolean toggleSelectedGroupKind() {
        Row row = selectedRow();
        if (row == null) {
            return false;
        }
        if (!(row.node() instanceof ConditionNode.AllOf) && !(row.node() instanceof ConditionNode.AnyOf)) {
            return false;
        }
        lastOpKey = EditSession.OP_TOGGLE_KIND;
        applyAt(row.path(), UiConditionTreeEditor::toggleKind);
        return true;
    }

    // 编辑选中叶节点的参数
    public boolean editSelectedLeaf() {
        Row row = selectedRow();
        if (row == null || !(row.node() instanceof ConditionNode.Leaf leaf)) {
            return false;
        }
        if (leaf.condition() == null || onEditLeaf == null) {
            return false;
        }
        // 叶参数由表单层写入，这里只记录操作类别
        lastOpKey = EditSession.OP_SET_FIELD;
        onEditLeaf.accept(leaf);
        return true;
    }

    // 折叠/展开选中节点
    public boolean toggleSelectedFold() {
        Row row = selectedRow();
        if (row == null || !hasChildren(row.node())) {
            return false;
        }
        if (!collapsed.remove(row.path())) {
            collapsed.add(row.path());
        }
        rebuild();
        return true;
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
        rows.clear();
        issues.clear();
        ConditionNode root = expression.root();
        if (root != null) {
            int[] counters = new int[2];
            append(root, ROOT_PATH, 0, -1, counters);
        }
        if (selectedPath != null && indexOfPath(selectedPath) < 0) {
            selectedPath = rows.isEmpty() ? null : rows.get(Math.min(rows.size() - 1, Math.max(0, hoveredIndex))).path();
        }
        if (hoveredIndex >= rows.size()) {
            hoveredIndex = -1;
        }
        scrollView.setContentHeight(rows.size() * rowHeight);
        int viewportHeight = scrollView.viewport().height();
        scrollView.setScrollbarVisible(rows.size() * rowHeight > viewportHeight);
    }

    // 递归展平：只有展开的节点才展平其子项
    private void append(ConditionNode node, String path, int depth, int parentIndex, int[] counters) {
        counters[0]++;
        int nodeOrdinal = counters[0];
        int leafOrdinal = -1;
        if (node instanceof ConditionNode.Leaf) {
            counters[1]++;
            leafOrdinal = counters[1];
        }
        boolean incomplete = isIncomplete(node);
        boolean overflow = depth + 1 > ConditionLimits.MAX_DEPTH
                || nodeOrdinal > ConditionLimits.MAX_NODES
                || leafOrdinal > ConditionLimits.MAX_LEAVES;
        // 契约 §5.2：深度超过 DEEP_HINT_DEPTH 只做层级引导提示，不计入违规项
        boolean deep = depth + 1 > DEEP_HINT_DEPTH;
        int index = rows.size();
        rows.add(new Row(node, path, depth, parentIndex, incomplete, overflow, deep, leafOrdinal));
        if (incomplete) {
            issues.add(new Issue(IssueKind.INCOMPLETE_GROUP, path,
                    Component.translatable("gui.itemdespawntowhat.edit.issue.incomplete_group")));
        }
        if (node instanceof ConditionNode.Leaf leaf && leaf.condition() == null) {
            issues.add(new Issue(IssueKind.MISSING_CONDITION, path,
                    Component.translatable("gui.itemdespawntowhat.edit.issue.missing_condition")));
        }
        if (depth + 1 > ConditionLimits.MAX_DEPTH) {
            issues.add(new Issue(IssueKind.TOO_DEEP, path, Component.translatable("gui.itemdespawntowhat.edit.issue.too_deep",
                    depth + 1, ConditionLimits.MAX_DEPTH)));
        }
        if (nodeOrdinal > ConditionLimits.MAX_NODES) {
            issues.add(new Issue(IssueKind.TOO_MANY_NODES, path, Component.translatable("gui.itemdespawntowhat.edit.issue.too_many_nodes",
                    nodeOrdinal, ConditionLimits.MAX_NODES)));
        }
        if (leafOrdinal > ConditionLimits.MAX_LEAVES) {
            issues.add(new Issue(IssueKind.TOO_MANY_LEAVES, path, Component.translatable("gui.itemdespawntowhat.edit.issue.too_many_leaves",
                    leafOrdinal, ConditionLimits.MAX_LEAVES)));
        }
        if (collapsed.contains(path)) {
            return;
        }
        if (node instanceof ConditionNode.AllOf all) {
            for (int i = 0; i < all.terms().size(); i++) {
                append(all.terms().get(i), path + ".terms[" + i + "]", depth + 1, index, counters);
            }
        } else if (node instanceof ConditionNode.AnyOf any) {
            for (int i = 0; i < any.terms().size(); i++) {
                append(any.terms().get(i), path + ".terms[" + i + "]", depth + 1, index, counters);
            }
        } else if (node instanceof ConditionNode.Inverted inverted) {
            append(inverted.term(), path + "." + RuleFields.TERM, depth + 1, index, counters);
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

    // 当前选中行在父分组 terms 中的下标，非分组子项返回 -1
    private int termIndexOf(Row row) {
        String prefix = ".terms[";
        int start = row.path().lastIndexOf(prefix);
        if (start < 0 || !row.path().endsWith("]")) {
            return -1;
        }
        try {
            return Integer.parseInt(row.path().substring(start + prefix.length(), row.path().length() - 1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    // 添加子项的目标分组路径：选中分组则用它，选中叶则向上找最近的分组，找不到返回 null
    private @Nullable String targetGroupPath() {
        Row row = selectedRow();
        int index = row == null ? -1 : indexOfPath(row.path());
        while (index >= 0) {
            Row current = rows.get(index);
            if (current.node() instanceof ConditionNode.AllOf || current.node() instanceof ConditionNode.AnyOf) {
                return current.path();
            }
            index = current.parentIndex();
        }
        return null;
    }

    // ---- 树变换（只依赖官方 record） ----

    // 应用新根节点并通知监听
    private void applyNode(@Nullable ConditionNode newRoot) {
        this.expression = new ConditionExpression(newRoot);
        rebuild();
        if (listener != null) {
            listener.onExpressionChanged(this.expression);
        }
    }

    // 在指定路径上应用变换（返回 null 表示删除该节点）
    private void applyAt(String path, UnaryOperator<ConditionNode> transform) {
        ConditionNode root = expression.root();
        if (root == null) {
            return;
        }
        if (ROOT_PATH.equals(path)) {
            applyNode(transform.apply(root));
            return;
        }
        ConditionNode updated = transformByPath(root, ROOT_PATH, path, transform);
        if (updated != null) {
            applyNode(updated);
        }
    }

    // 递归变换：命中目标路径时调用 transform，子项返回 null 表示从 terms 中移除
    private static @Nullable ConditionNode transformByPath(ConditionNode node, String nodePath, String targetPath,
                                                           UnaryOperator<ConditionNode> transform) {
        if (nodePath.equals(targetPath)) {
            return transform.apply(node);
        }
        if (node instanceof ConditionNode.AllOf all) {
            return new ConditionNode.AllOf(transformChildren(all.terms(), nodePath, targetPath, transform));
        }
        if (node instanceof ConditionNode.AnyOf any) {
            return new ConditionNode.AnyOf(transformChildren(any.terms(), nodePath, targetPath, transform));
        }
        if (node instanceof ConditionNode.Inverted inverted) {
            ConditionNode term = transformByPath(inverted.term(), nodePath + "." + RuleFields.TERM, targetPath, transform);
            return term == null ? null : new ConditionNode.Inverted(term);
        }
        return node;
    }

    // 逐个子项递归变换
    private static List<ConditionNode> transformChildren(List<ConditionNode> terms, String nodePath, String targetPath,
                                                         UnaryOperator<ConditionNode> transform) {
        List<ConditionNode> out = new ArrayList<>(terms.size());
        for (int i = 0; i < terms.size(); i++) {
            ConditionNode child = transformByPath(terms.get(i), nodePath + ".terms[" + i + "]", targetPath, transform);
            if (child != null) {
                out.add(child);
            }
        }
        return out;
    }

    // 向分组追加子项；不是分组时原样返回
    private static ConditionNode appendTerm(ConditionNode node, ConditionNode child) {
        if (node instanceof ConditionNode.AllOf all) {
            List<ConditionNode> terms = new ArrayList<>(all.terms());
            terms.add(child);
            return new ConditionNode.AllOf(terms);
        }
        if (node instanceof ConditionNode.AnyOf any) {
            List<ConditionNode> terms = new ArrayList<>(any.terms());
            terms.add(child);
            return new ConditionNode.AnyOf(terms);
        }
        return node;
    }

    // 交换分组内两个子项
    private static ConditionNode swapTerms(ConditionNode node, int from, int to) {
        List<ConditionNode> terms = termsOf(node);
        if (terms == null || from < 0 || to < 0 || from >= terms.size() || to >= terms.size()) {
            return node;
        }
        List<ConditionNode> copy = new ArrayList<>(terms);
        ConditionNode element = copy.remove(from);
        copy.add(to, element);
        return node instanceof ConditionNode.AllOf ? new ConditionNode.AllOf(copy) : new ConditionNode.AnyOf(copy);
    }

    // 取分组子项，非分组返回 null
    private static @Nullable List<ConditionNode> termsOf(ConditionNode node) {
        if (node instanceof ConditionNode.AllOf all) {
            return all.terms();
        }
        if (node instanceof ConditionNode.AnyOf any) {
            return any.terms();
        }
        return null;
    }

    // ALL 与 ANY 互转
    private static ConditionNode toggleKind(ConditionNode node) {
        if (node instanceof ConditionNode.AllOf all) {
            return new ConditionNode.AnyOf(all.terms());
        }
        if (node instanceof ConditionNode.AnyOf any) {
            return new ConditionNode.AllOf(any.terms());
        }
        return node;
    }

    // 追加节点：有目标分组就追加，没有分组时包一层 ALL 以保持语义
    private boolean addNode(ConditionNode newNode) {
        ConditionNode root = expression.root();
        if (root == null) {
            applyNode(newNode);
            selectPath(ROOT_PATH);
            return true;
        }
        String groupPath = pickerGroupPath != null ? pickerGroupPath : targetGroupPath();
        if (groupPath == null) {
            applyNode(new ConditionNode.AllOf(List.of(root, newNode)));
            selectPath(ROOT_PATH + ".terms[1]");
            return true;
        }
        int size = termsOf(findNode(groupPath)) == null ? 0 : termsOf(findNode(groupPath)).size();
        applyAt(groupPath, node -> appendTerm(node, newNode));
        selectPath(groupPath + ".terms[" + size + "]");
        return true;
    }

    // 按路径取节点
    private @Nullable ConditionNode findNode(String path) {
        ConditionNode current = expression.root();
        if (current == null) {
            return null;
        }
        if (ROOT_PATH.equals(path)) {
            return current;
        }
        for (String part : path.substring(ROOT_PATH.length() + 1).split("\\.")) {
            if (current == null) {
                return null;
            }
            if (part.equals(RuleFields.TERM)) {
                current = current instanceof ConditionNode.Inverted inverted ? inverted.term() : null;
                continue;
            }
            int open = part.indexOf('[');
            if (open < 0 || !part.startsWith(RuleFields.TERMS)) {
                return null;
            }
            int close = part.indexOf(']', open);
            int index;
            try {
                index = Integer.parseInt(part.substring(open + 1, close));
            } catch (NumberFormatException | IndexOutOfBoundsException ignored) {
                return null;
            }
            List<ConditionNode> terms = termsOf(current);
            if (terms == null || index < 0 || index >= terms.size()) {
                return null;
            }
            current = terms.get(index);
        }
        return current;
    }

    // 分组是否为空
    private static boolean isIncomplete(ConditionNode node) {
        List<ConditionNode> terms = termsOf(node);
        return terms != null && terms.isEmpty();
    }

    // 是否有子节点
    private static boolean hasChildren(ConditionNode node) {
        if (node instanceof ConditionNode.Inverted) {
            return true;
        }
        List<ConditionNode> terms = termsOf(node);
        return terms != null && !terms.isEmpty();
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
        UiTheme.drawInset(graphics, bounds);
        UiRect viewport = scrollView.viewport();
        hoveredIndex = pickerOpen ? -1 : indexAt(mouseX, mouseY);
        if (rows.isEmpty()) {
            String empty = TextScroll.trimToWidth(renderFont, emptyMessage.getString(), Math.max(0, viewport.width() - 8));
            graphics.drawString(renderFont, empty, viewport.x() + 4,
                    viewport.y() + Math.max(0, viewport.height() / 2 - 4), UiPalette.TEXT_SECONDARY, false);
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
        if (focused && !pickerOpen) {
            UiTheme.drawFocusOutline(graphics, bounds);
        }
        scrollView.renderScrollbar(graphics, UiTheme.secondaryStyle());
        if (pickerOpen) {
            renderPicker(graphics, renderFont);
        }
    }

    // 绘制一行
    private void drawRow(GuiGraphics graphics, Font renderFont, Row row, int index, int rowY, int rowWidth) {
        boolean selected = selectedPath != null && selectedPath.equals(row.path());
        if (selected) {
            UiTheme.drawSelection(graphics, new UiRect(0, rowY, rowWidth, rowHeight));
            UiTheme.drawSelectMarker(graphics, new UiRect(0, rowY, rowWidth, rowHeight));
        } else if (index == hoveredIndex) {
            graphics.fill(0, rowY, rowWidth, rowY + rowHeight, UiPalette.CONTROL_HOVER);
        }
        int markerX = UiTheme.SELECT_MARKER_WIDTH + row.depth() * INDENT_WIDTH;
        // 非根节点画一小段横向连线，帮助区分层级
        if (row.depth() > 0) {
            graphics.fill(markerX - 5, rowY + rowHeight / 2, markerX - 2, rowY + rowHeight / 2 + 1, UiPalette.TEXT_SECONDARY);
        }
        // TODO 美术资源：展开标记暂用 ASCII +/-，后续可替换为像素贴图
        if (hasChildren(row.node())) {
            String mark = collapsed.contains(row.path()) ? "+" : "-";
            graphics.drawString(renderFont, mark, markerX + 2, rowY + UiTheme.TEXT_OFFSET, UiPalette.TEXT_PRIMARY, false);
        }
        int textX = markerX + MARKER_WIDTH;
        int color = row.overflow() ? UiPalette.DANGER : UiPalette.TEXT_PRIMARY;
        String label = TextScroll.trimToWidth(renderFont, rowLabel(row).getString(),
                Math.max(0, rowWidth - textX - 2));
        graphics.drawString(renderFont, label, textX, rowY + UiTheme.TEXT_OFFSET, color, false);
    }

    // 行文本：组合节点显示 ALL/ANY/NOT 与子项数，叶节点显示条件类型标签，并附不完整/超限标记
    private Component rowLabel(Row row) {
        ConditionNode node = row.node();
        Component label;
        if (node instanceof ConditionNode.AllOf all) {
            label = Component.translatable("gui.itemdespawntowhat.edit.tree.group_label",
                    Component.translatable("gui.itemdespawntowhat.edit.tree.node.all"), all.terms().size());
        } else if (node instanceof ConditionNode.AnyOf any) {
            label = Component.translatable("gui.itemdespawntowhat.edit.tree.group_label",
                    Component.translatable("gui.itemdespawntowhat.edit.tree.node.any"), any.terms().size());
        } else if (node instanceof ConditionNode.Inverted) {
            label = Component.translatable("gui.itemdespawntowhat.edit.tree.node.not");
        } else if (node instanceof ConditionNode.Leaf leaf) {
            label = leaf.condition() == null
                    ? Component.translatable("gui.itemdespawntowhat.edit.tree.leaf_missing")
                    : typeLabel(leaf.condition().type());
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
        for (TypeOption option : typeOptions) {
            if (option.id().equals(type)) {
                return option.label();
            }
        }
        return TypeLabels.conditionLabel(type);
    }

    // 绘制类型选择框
    private void renderPicker(GuiGraphics graphics, Font renderFont) {
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), UiPalette.MODAL_DIM);
        int width = Math.min(Math.max(96, bounds.width() - 8), 220);
        int maxRows = Math.max(1, (bounds.height() - PICKER_HEADER - PICKER_FOOTER - 4) / Math.max(1, rowHeight));
        int visibleRows = Math.min(Math.min(PICKER_MAX_ROWS, maxRows), Math.max(1, typeOptions.size()));
        int height = PICKER_HEADER + visibleRows * rowHeight + PICKER_FOOTER;
        int x = bounds.x() + Math.max(0, (bounds.width() - width) / 2);
        int y = bounds.y() + Math.max(0, (bounds.height() - height) / 2);
        if (width <= 4 || height <= 4 || y + height > bounds.bottom() || x + width > bounds.right()) {
            closePicker();
            return;
        }
        pickerRect = new UiRect(x, y, width, height);
        UiTheme.drawWindow(graphics, pickerRect);
        UiTheme.drawHeader(graphics, new UiRect(x + 1, y + 1, width - 2, PICKER_HEADER));
        graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont,
                        Component.translatable("gui.itemdespawntowhat.edit.picker.title").getString(), width - 6),
                x + 3, y + UiTheme.TEXT_OFFSET + 1, UiPalette.HEADER_TEXT, false);
        pickerListRect = new UiRect(x + 1, y + 1 + PICKER_HEADER, width - 2, visibleRows * rowHeight);
        if (typeOptions.isEmpty()) {
            graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont,
                            Component.translatable("gui.itemdespawntowhat.edit.picker.empty").getString(), width - 6),
                    x + 3, pickerListRect.y() + UiTheme.TEXT_OFFSET, UiPalette.TEXT_SECONDARY, false);
        } else {
            pickerOffset = Math.max(0, Math.min(pickerOffset, Math.max(0, typeOptions.size() - visibleRows)));
            for (int i = 0; i < visibleRows; i++) {
                int optionIndex = pickerOffset + i;
                if (optionIndex >= typeOptions.size()) {
                    break;
                }
                int rowY = pickerListRect.y() + i * rowHeight;
                UiRect rowRect = new UiRect(pickerListRect.x(), rowY, pickerListRect.width(), rowHeight);
                if (optionIndex == pickerIndex) {
                    UiTheme.drawSelection(graphics, rowRect);
                    UiTheme.drawSelectMarker(graphics, rowRect);
                }
                graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont,
                                typeOptions.get(optionIndex).label().getString(), pickerListRect.width() - UiTheme.SELECT_MARKER_WIDTH - 4),
                        rowRect.x() + UiTheme.SELECT_MARKER_WIDTH, rowY + UiTheme.TEXT_OFFSET,
                        UiPalette.TEXT_PRIMARY, false);
            }
        }
        Component hint = Component.translatable("gui.itemdespawntowhat.edit.picker.hint");
        graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont, hint.getString(), width - 6),
                x + 3, pickerListRect.bottom() + 2, UiPalette.TEXT_SECONDARY, false);
    }

    // ---- 输入 ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (pickerOpen) {
            return handlePickerClick(mouseX, mouseY);
        }
        if (scrollView.mousePressed(mouseX, mouseY, button)) {
            return true;
        }
        int index = indexAt(mouseX, mouseY);
        if (index < 0) {
            return true;
        }
        Row row = rows.get(index);
        UiRect viewport = scrollView.viewport();
        int contentX = (int) (mouseX - viewport.x());
        int markerX = UiTheme.SELECT_MARKER_WIDTH + row.depth() * INDENT_WIDTH;
        long now = net.minecraft.Util.getMillis();
        boolean doubleClick = index == lastClickIndex && now - lastClickTime <= DOUBLE_CLICK_MS;
        lastClickIndex = index;
        lastClickTime = now;
        selectedPath = row.path();
        if (contentX >= markerX && contentX <= markerX + MARKER_WIDTH && hasChildren(row.node())) {
            if (!collapsed.remove(row.path())) {
                collapsed.add(row.path());
            }
            rebuild();
            return true;
        }
        if (doubleClick) {
            activate();
        }
        return true;
    }

    // 类型选择框内的鼠标处理
    private boolean handlePickerClick(double mouseX, double mouseY) {
        int index = pickerIndexAt(mouseX, mouseY);
        if (index >= 0) {
            pickerIndex = index;
            confirmPicker();
            return true;
        }
        if (!pickerRect.contains(mouseX, mouseY)) {
            closePicker();
        }
        return true;
    }

    // 类型选择框内的选项下标
    private int pickerIndexAt(double mouseX, double mouseY) {
        if (typeOptions.isEmpty() || !pickerListRect.contains(mouseX, mouseY)) {
            return -1;
        }
        int row = (int) ((mouseY - pickerListRect.y()) / Math.max(1, rowHeight));
        int index = pickerOffset + row;
        return index >= 0 && index < typeOptions.size() ? index : -1;
    }

    // 确认选择：用叶工厂生成默认叶节点并追加
    private void confirmPicker() {
        if (pickerIndex < 0 || pickerIndex >= typeOptions.size()) {
            closePicker();
            return;
        }
        ResourceLocation type = typeOptions.get(pickerIndex).id();
        LeafFactory factory = leafFactory;
        closePicker();
        if (factory == null) {
            return;
        }
        ConditionNode leaf = factory.create(type);
        if (leaf == null) {
            return;
        }
        if (addNode(leaf)) {
            lastOpKey = EditSession.OP_ADD_CONDITION;
        }
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
        if (pickerOpen) {
            if (typeOptions.size() > 1) {
                pickerIndex = Math.max(0, Math.min(typeOptions.size() - 1,
                        pickerIndex + (scrollY > 0.0D ? -1 : 1)));
                pickerOffset = Math.max(0, Math.min(pickerOffset, Math.max(0, typeOptions.size() - PICKER_MAX_ROWS)));
                return true;
            }
            return true;
        }
        return scrollView.scrollBy(scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) {
            return false;
        }
        if (pickerOpen) {
            return pickerKeyPressed(keyCode);
        }
        if (rows.isEmpty()) {
            if (keyCode == GLFW.GLFW_KEY_A || keyCode == GLFW.GLFW_KEY_G) {
                return beginAddCondition();
            }
            return false;
        }
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (keyCode == GLFW.GLFW_KEY_UP) {
            return shift ? moveSelected(-1) : moveSelection(-1);
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            return shift ? moveSelected(1) : moveSelection(1);
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
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
            return activate();
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            return deleteSelected();
        }
        if (keyCode == GLFW.GLFW_KEY_A) {
            return beginAddCondition();
        }
        if (keyCode == GLFW.GLFW_KEY_G) {
            return addGroup(!shift);
        }
        if (keyCode == GLFW.GLFW_KEY_N) {
            return wrapSelectedInNot();
        }
        if (keyCode == GLFW.GLFW_KEY_T) {
            return toggleSelectedGroupKind();
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return false;
        }
        return false;
    }

    // 类型选择框键盘处理
    private boolean pickerKeyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closePicker();
            return true;
        }
        if (typeOptions.isEmpty()) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            pickerIndex = Math.max(0, pickerIndex - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            pickerIndex = Math.min(typeOptions.size() - 1, pickerIndex + 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            pickerIndex = 0;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            pickerIndex = typeOptions.size() - 1;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirmPicker();
            return true;
        }
        return true;
    }

    // 一屏可见行数
    private int visibleRowCount() {
        return Math.max(1, scrollView.viewport().height() / Math.max(1, rowHeight));
    }

    // 相对移动选中行
    private boolean moveSelection(int delta) {
        if (rows.isEmpty()) {
            return false;
        }
        int index = selectedPath == null ? -1 : indexOfPath(selectedPath);
        int base = index < 0 ? (delta > 0 ? -1 : rows.size()) : index;
        int next = Math.max(0, Math.min(rows.size() - 1, base + delta));
        return selectIndex(next);
    }

    // 选中指定下标
    private boolean selectIndex(int index) {
        if (index < 0 || index >= rows.size()) {
            return false;
        }
        selectedPath = rows.get(index).path();
        ensureVisible(index);
        return true;
    }

    // 左方向键：优先折叠，已折叠则跳到父节点
    private boolean collapseOrJumpToParent() {
        Row row = selectedRow();
        if (row == null) {
            return false;
        }
        if (hasChildren(row.node()) && !collapsed.contains(row.path())) {
            collapsed.add(row.path());
            rebuild();
            return true;
        }
        if (row.parentIndex() >= 0) {
            return selectIndex(row.parentIndex());
        }
        return true;
    }

    // 右方向键：优先展开，已展开则进入第一个子节点
    private boolean expandOrEnterChild() {
        Row row = selectedRow();
        if (row == null) {
            return false;
        }
        if (hasChildren(row.node()) && collapsed.contains(row.path())) {
            collapsed.remove(row.path());
            rebuild();
            return true;
        }
        int index = selectedPath == null ? -1 : indexOfPath(selectedPath);
        if (hasChildren(row.node()) && index + 1 < rows.size() && rows.get(index + 1).parentIndex() == index) {
            return selectIndex(index + 1);
        }
        return true;
    }

    // ---- 焦点 ----

    @Override
    public boolean canFocus() {
        return visible;
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
        Row row = selectedRow();
        if (row == null) {
            return beginAddCondition();
        }
        if (row.node() instanceof ConditionNode.Leaf) {
            return editSelectedLeaf();
        }
        return toggleSelectedFold();
    }

    @Override
    public Component accessibleName() {
        return Component.translatable("gui.itemdespawntowhat.edit.tree.accessible_name");
    }
}
