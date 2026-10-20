package com.meteorite.itemdespawntowhat.client.ui.widget;

import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.BORDER;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.CARD_HEIGHT;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.isContainer;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphRenderer.drawBorder;
import com.meteorite.itemdespawntowhat.client.edit.ConditionTreeNodes;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.OpaqueCondition;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiHistoryShortcut;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRubberBand;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTransform;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreeEditor;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreeMoveInteraction;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreePath;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.Hotspot;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 条件树画布编辑入口：协调结构操作、选择、拖拽、键盘导航与宿主回调。
 *
 * <p>结构与选择由 {@link UiTreeEditor} 和 {@link ConditionTreeNodes} 处理；展示数据由
 * {@link ConditionGraphLayout} 重建，{@link ConditionGraphRenderer} 绘制画布。
 * 类型选择与参数表单分别交给 {@link UiTypePicker} 和 {@link ConditionGraphInlinePanel}，
 * 共用 {@link ConditionGraphSidePanel} 的屏幕坐标、外观和动画。</p>
 *
 * <p>宿主通过 {@link ConditionTreeContract.Navigation} 同步路径，通过
 * {@link #setInlineEditor(InlineEditor, UiTreePath)} 装载条件叶参数；候选修改经绑定回调交回宿主，
 * 控件不持有正式保存入口。缩进摘要 {@link UiConditionTreeSummary} 共用条件数据与校验，保持只读。</p>
 */
public final class UiConditionGraphEditor implements UiWidget, UiFocusTarget, ConditionTreeContract.Navigation {

    // 视图定位与手势阈值使用 GUI 屏幕像素。
    private static final int FIT_PADDING = 12;
    private static final int RESET_TOP_MARGIN = 16;
    private static final int REVEAL_MARGIN = 24;
    private static final int DRAG_START_DISTANCE = 4;
    private static final int DROP_EDGE_DIVISOR = 4;
    // 条件参数说明的行数较多，比动作说明的默认宽度更宽
    private static final int CONDITION_TOOLTIP_WIDTH = 300;

    // ---- 分组方式小菜单 ----

    // 菜单与锚点之间的屏幕像素间距
    private static final int KIND_MENU_GAP = 4;
    private static final int KIND_MENU_ROW_HEIGHT = 18;
    // 菜单行文本左右内边距
    private static final int KIND_MENU_PADDING = 4;
    // 菜单最小宽度
    private static final int KIND_MENU_MIN_WIDTH = 56;

    /**
     * 参数编辑框的内容契约：几何、绘制与输入坐标一律为 GUI 屏幕坐标，不随画布平移或缩放。
     *
     * <p>画布负责右侧面板的背景、标题、裁剪与 160ms ease-out 滑入动画；宿主只需实现表单本体。</p>
     */
    public interface InlineEditor {
        int preferredHeight(int width);
        UiRect bounds();                       // GUI 屏幕坐标
        void setBounds(int x, int y, int width, int height);
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseY);   // GUI 屏幕坐标
        boolean mouseClicked(double x, double y, int button);
        boolean mouseDragged(double x, double y, int button, double dragX, double dragY);
        boolean mouseReleased(double x, double y, int button);
        boolean mouseScrolled(double x, double y, double scrollY);
        boolean keyPressed(int keyCode, int scanCode, int modifiers);
        boolean charTyped(char codePoint, int modifiers);
        boolean isVisible();
    }

    // 分组方式：∧ 全部满足 / ∨ 任一满足
    private enum GroupKind {
        ALL("gui.itemdespawntowhat.edit.tree.operator.all", "gui.itemdespawntowhat.edit.tree.node.all"),
        ANY("gui.itemdespawntowhat.edit.tree.operator.any", "gui.itemdespawntowhat.edit.tree.node.any");

        private final String operatorKey;
        private final String labelKey;

        GroupKind(String operatorKey, String labelKey) {
            this.operatorKey = operatorKey;
            this.labelKey = labelKey;
        }

        // 菜单行同时给出运算符与它展开后的说法，只看到单字「且 / 或」时也能判断含义
        Component menuLabel() {
            return Component.translatable(operatorKey).append(" ").append(Component.translatable(labelKey));
        }
    }

    // 弹层确认后的动作：分组方式与删除范围共用同一套弹出菜单
    private enum KindMenuAction { INSERT_ALL, INSERT_ANY, WRAP_ALL, WRAP_ANY, DELETE_KEEP_CHILDREN, DELETE_SUBTREE }

    // 弹层的一行：显示文本与确认动作
    private record KindMenuEntry(Component label, KindMenuAction action) {}

    // 弹层用途：决定锚点位置与工具栏选中态
    private enum KindMenuPurpose { INSERT_GROUP, WRAP_SELECTION, DELETE_CONFIRM }

    private final Font font;
    private final UiTransform transform = UiTransform.ofScales(0.5, 1.0, 1.5, 2.0);
    private final ConditionGraphLayout graph;
    private final ConditionGraphRenderer renderer;
    private final UiRubberBand rubberBand = new UiRubberBand();
    private final UiTreeEditor<ConditionNode> tree;
    private final UiTreeMoveInteraction<ConditionNode> move;

    private ConditionExpression expression = ConditionExpression.EMPTY;
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private boolean visible = true;
    private boolean enabled = true;
    private boolean focused;
    private boolean viewInitialized;
    private boolean centerEmptyView;
    private @Nullable ConditionNode laidOutRoot;

    private @Nullable Function<ConditionNode.Leaf, Component> leafSummary;
    private @Nullable Function<ConditionNode.Leaf, List<UiDescriptionTooltip.Line>> leafTooltip;
    // 叶节点参数说明只在重建后失效，避免每帧重复生成
    private final Map<ConditionNode.Leaf, List<UiDescriptionTooltip.Line>> leafTooltipCache = new HashMap<>();
    private List<ConditionTreeContract.TypeOption> typeOptions = List.of();
    private @Nullable ConditionTreeContract.LeafFactory leafFactory;
    private @Nullable Consumer<ConditionNode.Leaf> onEditLeaf;
    private @Nullable ConditionTreeContract.Listener listener;
    private @Nullable Runnable copyAction;
    private @Nullable Runnable pasteAction;
    private Component emptyMessage = Component.translatable("gui.itemdespawntowhat.edit.tree.add_placeholder");

    private @Nullable UiTreeEditor.Change<ConditionNode> lastChange;
    private @Nullable Component operationError;
    private String lastOpKey = EditSession.OP_EDIT_CONDITIONS;
    private @Nullable String selectedPath;

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
    private double panLastX;
    private double panLastY;
    // 空白区左键按下：释放时若没有产生位移，按空白点击处理（清空选择）
    private boolean panPressedBlank;
    private boolean panMoved;
    private double panTravel;
    private boolean spaceDown;
    private @Nullable UiTreePath selectionAnchor;

    private final UiTypePicker picker;
    private boolean pickerAppend;
    private @Nullable UiTreePath pickerParent;
    private int pickerIndex;
    private final ConditionGraphInlinePanel inlinePanel;
    private final UiDescriptionTooltip tooltip;

    private boolean kindMenuOpen;
    private KindMenuPurpose kindMenuPurpose = KindMenuPurpose.INSERT_GROUP;
    private List<KindMenuEntry> kindMenuEntries = List.of();
    private @Nullable UiRect kindMenuAnchor;
    private int kindMenuIndex;
    // 指针位置：指针在列表内时高亮跟随鼠标，指针静止则不覆盖键盘选择
    private boolean kindMenuPointerKnown;
    private int kindMenuLastMouseX;
    private int kindMenuLastMouseY;
    private UiRect kindMenuRect = new UiRect(0, 0, 0, 0);
    private UiRect kindMenuListRect = new UiRect(0, 0, 0, 0);

    // 注入的宿主主题
    private final UiHostTheme theme;
    public UiConditionGraphEditor(UiHostTheme theme, Font font) {
        this.theme = UiHostThemes.require(theme);
        this.font = font;
        graph = new ConditionGraphLayout(font);
        renderer = new ConditionGraphRenderer(this.theme, font, graph);
        picker = new UiTypePicker(this.theme, font, Component.translatable("gui.itemdespawntowhat.edit.picker.title"), this::confirmPicker);
        inlinePanel = new ConditionGraphInlinePanel(this.theme, font, graph);
        tooltip = new UiDescriptionTooltip(this.theme, font).setMaxWidth(CONDITION_TOOLTIP_WIDTH);
        tree = new UiTreeEditor<>(ConditionTreeNodes.INSTANCE, () -> expression.root(), change -> {
            lastChange = change;
            applyNode(change.after());
        }, ConditionTreeInspection::validateCandidate,
                Component.translatable("gui.itemdespawntowhat.edit.tree.invalid_path"),
                Component.translatable("gui.itemdespawntowhat.edit.tree.invalid_parent"));
        tree.setMoveErrors(new UiTreeEditor.MoveErrors(
                Component.translatable("gui.itemdespawntowhat.edit.tree.move_cycle"),
                Component.translatable("gui.itemdespawntowhat.edit.tree.move_root_sibling"),
                Component.translatable("gui.itemdespawntowhat.edit.tree.move_leaf_parent"),
                Component.translatable("gui.itemdespawntowhat.edit.tree.move_full_parent"),
                Component.translatable("gui.itemdespawntowhat.edit.tree.move_stale")));
        move = new UiTreeMoveInteraction<>(tree, result -> {
            armedDrag = null;
            dropTarget = null;
            dragLabel = null;
            accept(result);
            if (result.changed() && tree.selected() != null) {
                followPrimarySelection();
                rebuild();
                if (selectedPath != null) selectPath(selectedPath);
            }
        });
    }

    // ---- 宿主注入面 ----

    public UiTreeEditor<ConditionNode> tree() {
        return tree;
    }

    public boolean isDragging() {
        return move.isActive() || panning || rubberActive;
    }

    public UiConditionGraphEditor setTypeOptions(List<ConditionTreeContract.TypeOption> options) {
        this.typeOptions = List.copyOf(options);
        picker.setOptions(pickerOptions());
        rebuild();
        return this;
    }

    public UiConditionGraphEditor setLeafFactory(ConditionTreeContract.LeafFactory factory) {
        this.leafFactory = factory;
        return this;
    }

    public UiConditionGraphEditor setLeafSummary(Function<ConditionNode.Leaf, Component> formatter) {
        this.leafSummary = formatter;
        rebuild();
        return this;
    }

    /**
     * 注入叶节点的参数说明行；返回空列表时回落到卡片摘要。
     *
     * @param formatter 把条件叶转换成说明行的回调，可为 {@code null} 取消注入
     */
    public void setLeafTooltip(@Nullable Function<ConditionNode.Leaf, List<UiDescriptionTooltip.Line>> formatter) {
        this.leafTooltip = formatter;
        leafTooltipCache.clear();
    }

    public UiConditionGraphEditor setListener(ConditionTreeContract.Listener newListener) {
        this.listener = newListener;
        return this;
    }

    public UiConditionGraphEditor setOnEditLeaf(Consumer<ConditionNode.Leaf> callback) {
        this.onEditLeaf = callback;
        return this;
    }

    /** 宿主在切页、历史回填、卸载或关闭时取消手势；只有释放可提交移动。 */
    public void endInteractions(UiInputCapture.EndReason reason) {
        armedDrag = null;
        clickedLeafPath = null;
        collapseSelectionOnRelease = false;
        dragLabel = null;
        dropTarget = null;
        rubberActive = false;
        rubberBand.clear();
        clearPanState();
        inlinePanel.cancelCapture();
        picker.cancelCapture();
        move.end(reason);
        if (reason != UiInputCapture.EndReason.RELEASE) {
            closePicker();
            closeKindMenu();
        }
    }

    // 结束平移手势并清掉「空白点击」的待判状态
    private void clearPanState() {
        panning = false;
        panPressedBlank = false;
        panMoved = false;
        panTravel = 0.0D;
    }

    public @Nullable UiTreeEditor.Change<ConditionNode> lastChange() {
        return lastChange;
    }

    public @Nullable Component operationError() {
        return operationError;
    }

    public void showOperationError(@Nullable Component error) {
        operationError = error;
    }

    public void setBeforeEdit(Runnable handler) {
        tree.setBeforeEdit(handler);
    }

    public void setClipboardActions(Runnable copy, Runnable paste) {
        copyAction = copy;
        pasteAction = paste;
    }

    public UiConditionGraphEditor setEmptyMessage(Component message) {
        this.emptyMessage = message;
        return this;
    }

    public List<UiTreePath> selectedSubtrees() {
        return tree.normalizedSelection();
    }

    // ---- 数据 ----

    public ConditionExpression expression() {
        return expression;
    }

    /** 外部设置表达式：不触发变更回调，避免与宿主形成回环。 */
    public void setExpression(ConditionExpression newExpression) {
        endInteractions(UiInputCapture.EndReason.UNMOUNTED);
        this.expression = newExpression == null ? ConditionExpression.EMPTY : newExpression;
        tree.setSelection(tree.selection(), tree.selected());
        followPrimarySelection();
        rebuild();
    }

    public boolean isValid() {
        return expression.isStructurallyValid() && !graph.hasIssues();
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

    /** 违规项明细（只读）。 */
    public List<ConditionTreeInspection.Issue> issues() {
        return graph.issues();
    }

    /** 最近一次改动的撤销操作 key。 */
    public String undoOpKey() {
        return lastOpKey;
    }

    @Override
    public @Nullable String selectedPath() {
        return selectedPath;
    }

    @Override
    public @Nullable ConditionNode selectedNode() {
        UiTreePath path = tree.selected();
        return path == null ? null : tree.query(path);
    }

    /** 按 JSON 路径选中节点或其所属节点，并把卡片带回视口。 */
    @Override
    public void selectPath(@Nullable String path) {
        if (path == null) {
            selectedPath = null;
            tree.select(null);
            return;
        }
        ConditionNode target = graph.nodeForJsonPath(path);
        if (target == null) {
            return;
        }
        UiTreePath treePath = graph.path(target);
        if (treePath == null) {
            return;
        }
        selectedPath = ConditionTreeNodes.jsonPath(expression.root(), treePath,
                ConditionTreeContract.ROOT_PATH);
        if (tree.selection().contains(treePath)) {
            tree.setSelection(tree.selection(), treePath);
        } else {
            tree.select(treePath);
        }
        selectionAnchor = treePath;
        // 外部路径定位优先于首帧整树适配，避免预览跳转后视口又被重置。
        centerEmptyView = false;
        revealNode(target);
    }

    // ---- 结构操作（工具栏与键盘共用） ----

    /** 直接打开类型列表，确认后追加到当前容器或在根外包一层全部满足。 */
    public boolean beginAddCondition() {
        if (!enabled) {
            return false;
        }
        openPicker(new Hotspot(null, 0, bounds));
        pickerAppend = true;
        return true;
    }

    public boolean isPickerOpen() {
        return picker.isOpen();
    }

    public boolean hasPopup() {
        return picker.isOpen() || kindMenuOpen;
    }

    /** 宿主提供源物品与结果摘要；缺失内容用空摘要保留端点框。 */
    public void setEndpoints(Component sourceSummary, Component resultSummary) {
        if (graph.setEndpoints(sourceSummary, resultSummary)) rebuild();
    }

    /** 「新建分支」的 ∧ / ∨ 菜单是否打开；为真时该工具格显示为选中。 */
    public boolean isGroupKindMenuOpen() {
        return kindMenuOpen && kindMenuPurpose == KindMenuPurpose.INSERT_GROUP;
    }

    /** 「包组」的 ∧ / ∨ 菜单是否打开。 */
    public boolean isWrapKindMenuOpen() {
        return kindMenuOpen && kindMenuPurpose == KindMenuPurpose.WRAP_SELECTION;
    }

    /** 删除确认菜单是否打开；为真时删除工具格显示为选中。 */
    public boolean isDeleteConfirmOpen() {
        return kindMenuOpen && kindMenuPurpose == KindMenuPurpose.DELETE_CONFIRM;
    }

    /** 在当前可添加位置追加一个分组；无参形式默认 ALL。 */
    public boolean beginAddGroup() {
        return beginAddGroup(true);
    }

    public boolean beginAddGroup(boolean allOf) {
        if (!enabled) {
            return false;
        }
        endInteractions(UiInputCapture.EndReason.CANCEL);
        ConditionNode group = allOf ? new ConditionNode.AllOf(List.of()) : new ConditionNode.AnyOf(List.of());
        lastOpKey = EditSession.OP_ADD_GROUP;
        return addNode(group);
    }

    /**
     * 在工具栏「新建分支」按钮旁弹出「且 / 或」菜单：选定后插入一个空分支，并把新建条件窗口直接弹到
     * 新分支的子项热区上，省去「先插入空分组、再点热区、再选类型」的多余步骤。
     *
     * @param anchorScreen 按钮在 GUI 屏幕坐标下的矩形，菜单贴着它弹出
     */
    public boolean beginAddGroupWithKind(UiRect anchorScreen) {
        if (!enabled) {
            return false;
        }
        endInteractions(UiInputCapture.EndReason.CANCEL);
        closePicker();
        closeKindMenu();
        kindMenuPurpose = KindMenuPurpose.INSERT_GROUP;
        kindMenuEntries = List.of(new KindMenuEntry(GroupKind.ALL.menuLabel(), KindMenuAction.INSERT_ALL),
                new KindMenuEntry(GroupKind.ANY.menuLabel(), KindMenuAction.INSERT_ANY));
        kindMenuAnchor = anchorScreen;
        kindMenuIndex = 0;
        kindMenuOpen = true;
        updateKindMenuRect();
        return true;
    }

    /** 对当前选中的同父连续节点弹出「全部满足 / 任一满足」包组菜单；无选中时返回 false。 */
    public boolean beginWrapWithKind() {
        if (!enabled || tree.selection().isEmpty()) {
            return false;
        }
        endInteractions(UiInputCapture.EndReason.CANCEL);
        closePicker();
        kindMenuPurpose = KindMenuPurpose.WRAP_SELECTION;
        kindMenuEntries = List.of(new KindMenuEntry(GroupKind.ALL.menuLabel(), KindMenuAction.WRAP_ALL),
                new KindMenuEntry(GroupKind.ANY.menuLabel(), KindMenuAction.WRAP_ANY));
        kindMenuAnchor = null;
        kindMenuIndex = 0;
        kindMenuOpen = true;
        updateKindMenuRect();
        return true;
    }

    /** 把选中节点包一层 NOT；多选时包成「NOT（ALL 选中项）」。 */
    public boolean invertSelection() {
        if (!enabled || tree.selection().isEmpty()) {
            return false;
        }
        List<UiTreePath> selected = tree.normalizedSelection();
        if (selected.size() == 1 && tree.query(selected.getFirst()) instanceof ConditionNode.Inverted) {
            return false;
        }
        lastOpKey = EditSession.OP_WRAP_NOT;
        boolean changed = accept(tree.wrapMany(tree.selection(),
                nodes -> new ConditionNode.Inverted(nodes.size() == 1 ? nodes.getFirst()
                        : new ConditionNode.AllOf(nodes)),
                Component.translatable("gui.itemdespawntowhat.edit.tree.wrap_selection")));
        if (changed) {
            followPrimarySelection();
            rebuild();
        }
        return changed;
    }

    /** 删除选中节点；选中项下方还有分支时先弹确认菜单，由玩家选择只删本级还是整支删除。 */
    public boolean removeSelection() {
        return removeSelection(null);
    }

    /**
     * 删除选中节点：下方还有分支时贴着触发按钮弹出确认菜单，否则直接删除。
     *
     * @param anchorScreen 触发删除的工具格屏幕矩形；为 {@code null}（键盘删除）时贴着选区卡片弹出
     */
    public boolean removeSelection(@Nullable UiRect anchorScreen) {
        if (!enabled || tree.selection().isEmpty()) {
            return false;
        }
        List<UiTreePath> selected = tree.normalizedSelection();
        if (!canKeepChildren(selected)) {
            return deleteSelection(false, selected);
        }
        endInteractions(UiInputCapture.EndReason.CANCEL);
        closePicker();
        closeKindMenu();
        kindMenuPurpose = KindMenuPurpose.DELETE_CONFIRM;
        kindMenuEntries = List.of(
                new KindMenuEntry(Component.translatable("gui.itemdespawntowhat.edit.tree.delete.keep_children"),
                        KindMenuAction.DELETE_KEEP_CHILDREN),
                new KindMenuEntry(Component.translatable("gui.itemdespawntowhat.edit.tree.delete.subtree"),
                        KindMenuAction.DELETE_SUBTREE));
        kindMenuAnchor = anchorScreen;
        kindMenuIndex = 0;
        kindMenuOpen = true;
        updateKindMenuRect();
        return true;
    }

    // 下方分支能否交给父节点：根节点没有父节点，容量不足的父节点（例如取反只能容纳一个子项）也放不下
    private boolean canKeepChildren(List<UiTreePath> selected) {
        boolean hasChildren = false;
        for (UiTreePath path : selected) {
            ConditionNode node = tree.query(path);
            if (node == null) continue;
            List<ConditionNode> lifted = ConditionTreeNodes.INSTANCE.children(node);
            if (lifted.isEmpty()) continue;
            hasChildren = true;
            if (path.indices().isEmpty()) return false;
            ConditionNode parent = tree.query(path.parent());
            if (parent == null) return false;
            if (!ConditionTreeNodes.INSTANCE.acceptsChildren(parent,
                    ConditionTreeNodes.INSTANCE.children(parent).size() - 1 + lifted.size())) {
                return false;
            }
        }
        return hasChildren;
    }

    // keepChildren 为真时保留下方分支并上移，否则删除整个子树
    private boolean deleteSelection(boolean keepChildren, List<UiTreePath> selected) {
        if (selected.isEmpty()) {
            return false;
        }
        UiTreePath parent = selected.getFirst().indices().isEmpty() ? null : selected.getFirst().parent();
        lastOpKey = EditSession.OP_DELETE_NODE;
        boolean changed = accept(keepChildren ? tree.deleteKeepingChildren(selected) : tree.deleteMany(selected));
        if (changed) {
            selectedPath = null;
            if (parent != null && tree.query(parent) != null) {
                tree.select(parent);
                followPrimarySelection();
            }
            rebuild();
        }
        return changed;
    }

    /** 同级排序：delta 为 -1 / +1，仅单选中节点可移动。 */
    public boolean moveSelection(int delta) {
        if (!enabled || delta == 0) {
            return false;
        }
        if (tree.normalizedSelection().size() > 1) {
            operationError = Component.translatable("gui.itemdespawntowhat.edit.tree.move_multiple");
            return false;
        }
        UiTreePath selected = tree.selected();
        if (selected == null || selected.indices().isEmpty()) {
            return false;
        }
        UiTreePath parent = selected.parent();
        ConditionNode parentNode = tree.query(parent);
        List<ConditionNode> terms = termsOf(parentNode);
        if (terms == null) {
            return false;
        }
        int index = selected.indices().getLast();
        int target = index + delta;
        if (target < 0 || target >= terms.size()) {
            return false;
        }
        lastOpKey = EditSession.OP_MOVE_NODE;
        boolean changed = applyAt(parent, node -> swapTerms(node, index, target));
        if (changed) {
            tree.select(parent.child(target));
            followPrimarySelection();
        }
        return changed;
    }

    /** 把选中的连续同级节点包成 ALL 或 ANY 分组。 */
    public boolean wrapSelection(boolean all) {
        if (!enabled) {
            return false;
        }
        endInteractions(UiInputCapture.EndReason.CANCEL);
        lastOpKey = EditSession.OP_ADD_GROUP;
        boolean changed = accept(tree.wrapMany(tree.selection(),
                nodes -> all ? new ConditionNode.AllOf(nodes) : new ConditionNode.AnyOf(nodes),
                Component.translatable("gui.itemdespawntowhat.edit.tree.wrap_selection")));
        if (changed) {
            followPrimarySelection();
            rebuild();
        }
        return changed;
    }

    public boolean selectAll() {
        if (graph.nodes().isEmpty()) {
            return false;
        }
        List<UiTreePath> all = graph.nodes().stream().map(graph::path).filter(java.util.Objects::nonNull).toList();
        tree.setSelection(all, tree.selected());
        followPrimarySelection();
        return true;
    }

    public boolean copySelection() {
        if (!enabled || tree.selection().isEmpty() || copyAction == null) {
            return false;
        }
        copyAction.run();
        return true;
    }

    public boolean pasteSelection() {
        if (!enabled || pasteAction == null) {
            return false;
        }
        pasteAction.run();
        return true;
    }

    /** 粘贴子树：优先作为选中容器的子项，其次作为选中节点的同级后继。 */
    public void pasteNodes(List<ConditionNode> nodes) {
        if (!enabled || nodes.isEmpty()) {
            return;
        }
        UiTreePath parent;
        int index;
        if (expression.isEmpty()) {
            parent = null;
            index = 0;
        } else {
            UiTreePath selected = tree.selected();
            ConditionNode target = selected == null ? null : tree.query(selected);
            if (target != null && ConditionTreeNodes.INSTANCE.childCapacity(target) > 0) {
                parent = selected;
                index = ConditionTreeNodes.INSTANCE.children(target).size();
            } else if (selected != null && !selected.indices().isEmpty()) {
                parent = selected.parent();
                index = selected.indices().getLast() + 1;
            } else {
                operationError = Component.translatable("gui.itemdespawntowhat.edit.tree.paste_parent");
                return;
            }
        }
        if (parent == null && nodes.size() > 1) {
            operationError = Component.translatable("gui.itemdespawntowhat.edit.tree.paste_root");
            return;
        }
        endInteractions(UiInputCapture.EndReason.CANCEL);
        lastOpKey = EditSession.OP_ADD_CONDITION;
        if (accept(tree.createMany(parent, index, nodes))) {
            followPrimarySelection();
            rebuild();
        }
    }

    /** 编辑选中叶节点的参数。 */
    public boolean editSelectedLeaf() {
        if (!enabled || !canEditSelectedLeaf()) {
            return false;
        }
        ConditionNode node = selectedNode();
        if (!(node instanceof ConditionNode.Leaf leaf) || onEditLeaf == null) {
            return false;
        }
        // 叶参数由表单层写入，这里只记录操作类别
        lastOpKey = EditSession.OP_SET_FIELD;
        onEditLeaf.accept(leaf);
        return true;
    }

    public boolean canEditSelectedLeaf() {
        return selectedNode() instanceof ConditionNode.Leaf(var condition)
                && condition != null && !(condition instanceof OpaqueCondition);
    }

    // ---- 视图 ----

    /** 缩放 100% 并把根卡片顶边中点放回视口顶部。 */
    public void resetView() {
        transform.reset();
        UiRect rootRect = graph.rootRect();
        transform.centerOn(rootRect.x() + rootRect.width() / 2.0, rootRect.y());
        transform.panBy(0, RESET_TOP_MARGIN - transform.viewport().height() / 2.0);
    }

    /** 选择能容纳整棵树的档位并居中显示。 */
    public void fitToContent() {
        transform.fit(0, 0, graph.contentWidth(), graph.contentHeight(), FIT_PADDING);
    }

    /** 逐档缩放，锚点为视口中心。 */
    public void zoomStep(int direction) {
        if (direction == 0) {
            return;
        }
        UiRect viewport = transform.viewport();
        transform.zoomBy(direction, viewport.x() + viewport.width() / 2.0, viewport.y() + viewport.height() / 2.0);
    }

    public int scalePercent() {
        return transform.scalePercent();
    }

    // ---- 布局 ----

    @Override
    public UiRect bounds() {
        return bounds;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        this.bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        transform.setViewport(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        picker.setBounds(bounds);
        inlinePanel.setBounds(bounds);
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    public void setEnabled(boolean next) {
        enabled = next;
        if (!next) {
            endInteractions(UiInputCapture.EndReason.DISABLED);
            focused = false;
            closePicker();
        }
    }

    public UiConditionGraphEditor setVisible(boolean next) {
        if (!next) {
            endInteractions(UiInputCapture.EndReason.HIDDEN);
        }
        this.visible = next;
        return this;
    }

    // 全量重建：扫描状态、建立包装节点、运行整齐树布局。
    private void rebuild() {
        leafTooltipCache.clear();
        ConditionNode previousRoot = laidOutRoot;
        ConditionNode root = expression.root();
        laidOutRoot = root;
        graph.rebuild(expression, typeOptions, leafSummary);
        tree.setSelection(tree.selection(), tree.selected());
        followPrimarySelection();
        inlinePanel.validateAnchor();
        if (!viewInitialized || root == null && previousRoot != null) {
            // 视口尺寸在下一帧渲染时可用，届时适配完整源物品到结果链路。
            centerEmptyView = true;
        }
        viewInitialized = true;
    }

    // 悬停提示：标题行 + 摘要行 + 结构标记行，由画布自绘面板呈现（原版 tooltip 不参与）
    private @Nullable List<UiDescriptionTooltip.Line> tooltipAt(double mouseX, double mouseY) {
        if (!visible || isDragging() || picker.isOpen() || kindMenuOpen || !bounds.contains(mouseX, mouseY)) {
            return null;
        }
        // 内联框自带提示，避免画布卡片提示压住表单
        if (inlinePanel.contains(mouseX, mouseY)) {
            return null;
        }
        ConditionNode node = graph.nodeAt(transform.toLocalX(mouseX), transform.toLocalY(mouseY));
        if (node == null) {
            return null;
        }
        boolean issue = graph.hasIssue(node);
        List<UiDescriptionTooltip.Line> lines = new ArrayList<>();
        lines.add(new UiDescriptionTooltip.Line(graph.titleLabel(node),
                issue ? theme.palette().canvasTextDanger() : theme.palette().canvasAccent()));
        List<UiDescriptionTooltip.Line> details = null;
        if (leafTooltip != null && node instanceof ConditionNode.Leaf leaf && leaf.condition() != null) {
            details = leafTooltipCache.computeIfAbsent(leaf, key -> List.copyOf(leafTooltip.apply(key)));
        }
        if (details == null || details.isEmpty()) {
            lines.add(new UiDescriptionTooltip.Line(graph.summaryLabel(node), theme.palette().canvasTextMuted()));
        } else {
            lines.addAll(details);
        }
        Component status = graph.statusLabel(node);
        if (!status.getString().isEmpty()) {
            lines.add(new UiDescriptionTooltip.Line(status, theme.palette().canvasTextDanger()));
        }
        return lines;
    }

    /** 当前手势或默认快捷键提示，供宿主底部状态栏展示。 */
    public Component hint() {
        if (move.isActive()) {
            UiTreeEditor.MovePreview<ConditionNode> preview = move.preview();
            if (preview != null && preview.outcome().error() == null && dropTarget != null) {
                return Component.translatable("gui.itemdespawntowhat.edit.tree.drop_"
                        + preview.position().name().toLowerCase(Locale.ROOT), graph.titleLabel(dropTarget));
            }
            if (operationError != null) {
                return operationError;
            }
        }
        if (picker.isOpen()) {
            return Component.translatable("gui.itemdespawntowhat.edit.picker.hint");
        }
        return Component.translatable("gui.itemdespawntowhat.edit.tree.selection_hint");
    }

    // ---- 渲染 ----

    @Override
    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        if (!visible || bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        transform.setViewport(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        if (centerEmptyView) {
            centerEmptyView = false;
            fitToContent();
        }
        UiRect viewport = transform.viewport();
        boolean inside = bounds.contains(mouseX, mouseY);
        boolean emptyTree = expression.root() == null;
        double localX = transform.toLocalX(mouseX);
        double localY = transform.toLocalY(mouseY);
        boolean overPanel = inlinePanel.contains(mouseX, mouseY);
        ConditionNode hoveredNode = !picker.isOpen() && !kindMenuOpen && !overPanel && inside ? graph.nodeAt(localX, localY) : null;
        // 空树空闲态也要亮出中央大 [+]，让人一眼看出这里可以点
        Hotspot hoveredHotspot = !picker.isOpen() && !kindMenuOpen && !overPanel && inside
                ? graph.hotspotAt(localX, localY) : null;

        renderer.drawCanvasBackground(graphics, bounds);
        transform.enableScissor(graphics);
        renderer.drawDotGrid(graphics, transform, viewport);
        transform.push(graphics);
        try {
            renderer.drawLinks(graphics, tree.selection());
            renderer.drawCards(graphics, renderFont, tree.selection(), hoveredNode);
            renderer.drawHotspots(graphics, hoveredHotspot);
            if (rubberActive) {
                rubberBand.draw(graphics, theme.palette().canvasRubberFill(), theme.palette().canvasAccent());
            }
            if (move.isActive()) {
                renderer.drawDropMarker(graphics, move.preview(), dropTarget);
            }
        } finally {
            graphics.pose().popPose();
            UiTransform.disableScissor(graphics);
        }
        if (emptyTree) {
            renderer.drawEmptyMessage(graphics, renderFont, viewport, transform, emptyMessage);
        }
        if (focused && !picker.isOpen() && !kindMenuOpen) {
            theme.drawFocusOutline(graphics, bounds);
        }
        if (move.isActive()) {
            renderer.drawDragGhost(graphics, renderFont, bounds, dragLabel, mouseX, mouseY);
        }
        if (!picker.isOpen()) {
            inlinePanel.render(graphics, renderFont, mouseX, mouseY);
        }
        if (picker.isOpen()) {
            picker.render(graphics, renderFont, mouseX, mouseY);
        }
        if (kindMenuOpen) {
            updateKindMenuRect();
            updateKindMenuHover(mouseX, mouseY);
            renderKindMenu(graphics, renderFont);
        }
        List<UiDescriptionTooltip.Line> tip = tooltipAt(mouseX, mouseY);
        if (tip != null) {
            tooltip.render(graphics, bounds, tip, mouseX, mouseY);
        }
    }

    // ---- 参数面板宿主接口 ----

    /** 装载条件叶参数表单；null 表单或路径表示收起面板。 */
    public void setInlineEditor(@Nullable InlineEditor editor, @Nullable UiTreePath anchorPath) {
        inlinePanel.mount(editor, anchorPath);
    }

    /** 向右收起参数面板，动画结束后释放表单。 */
    public void clearInlineEditor() {
        inlinePanel.close();
    }

    /** 是否已装载参数表单，含关闭动画期间。 */
    public boolean isInlineEditorOpen() {
        return inlinePanel.isOpen();
    }

    // ---- 分组方式小菜单 ----

    private void closeKindMenu() {
        kindMenuOpen = false;
        kindMenuAnchor = null;
        kindMenuEntries = List.of();
        kindMenuIndex = 0;
        kindMenuPointerKnown = false;
    }

    // 菜单几何为屏幕坐标：优先放在锚点右侧，放不下改为左侧，并整体夹在画布内
    private void updateKindMenuRect() {
        if (!kindMenuOpen || bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        UiRect anchor = kindMenuAnchorRect();
        if (anchor == null) {
            closeKindMenu();
            kindMenuRect = new UiRect(0, 0, 0, 0);
            return;
        }
        if (kindMenuEntries.isEmpty()) {
            closeKindMenu();
            kindMenuRect = new UiRect(0, 0, 0, 0);
            return;
        }
        int rowCount = kindMenuEntries.size();
        int width = KIND_MENU_MIN_WIDTH;
        for (KindMenuEntry entry : kindMenuEntries) {
            width = Math.max(width, font.width(entry.label().getString()) + KIND_MENU_PADDING * 2 + BORDER * 2);
        }
        int maxWidth = Math.max(1, bounds.width() - KIND_MENU_PADDING);
        width = Math.min(width, maxWidth);
        int height = Math.min(rowCount * KIND_MENU_ROW_HEIGHT + BORDER * 2, bounds.height());
        int x = anchor.right() + KIND_MENU_GAP;
        if (x + width > bounds.right()) {
            x = anchor.x() - KIND_MENU_GAP - width;
        }
        x = Math.clamp(x, bounds.x(), Math.max(bounds.x(), bounds.right() - width));
        int y = Math.clamp(anchor.y(), bounds.y(), Math.max(bounds.y(), bounds.bottom() - height));
        kindMenuRect = new UiRect(x, y, width, height);
        // 视口过矮时按可用高度裁掉列表区：行高不变，只显示画得下的行
        int availableListHeight = height - BORDER * 2;
        int listHeight = availableListHeight <= 0 ? 0
                : Math.min(rowCount * KIND_MENU_ROW_HEIGHT, availableListHeight);
        kindMenuListRect = new UiRect(x + BORDER, y + BORDER, Math.max(0, width - BORDER * 2), listHeight);
    }

    private @Nullable UiRect kindMenuAnchorRect() {
        // 工具栏触发（新建分支、删除确认）贴着工具格弹出；包组与键盘删除贴着选区里树序最靠前的卡片弹出
        if (kindMenuAnchor != null) {
            return kindMenuAnchor;
        }
        ConditionNode first = firstSelectedInOrder();
        UiRect rect = first == null ? null : graph.rect(first);
        return rect == null ? null : toScreenRect(rect);
    }

    private UiRect toScreenRect(UiRect content) {
        double scale = transform.scale();
        return new UiRect((int) Math.round(transform.toScreenX(content.x())),
                (int) Math.round(transform.toScreenY(content.y())),
                Math.max(1, (int) Math.round(content.width() * scale)),
                Math.max(1, (int) Math.round(content.height() * scale)));
    }

    // 选区里树序最靠前的节点：包组菜单定位在它附近
    private @Nullable ConditionNode firstSelectedInOrder() {
        for (ConditionNode node : graph.nodes()) {
            UiTreePath path = graph.path(node);
            if (path != null && tree.selection().contains(path)) {
                return node;
            }
        }
        return null;
    }

    private void renderKindMenu(GuiGraphics graphics, Font renderFont) {
        if (!kindMenuOpen || kindMenuRect.width() <= 0 || kindMenuRect.height() <= 0) {
            return;
        }
        graphics.fill(kindMenuRect.x(), kindMenuRect.y(), kindMenuRect.right(), kindMenuRect.bottom(),
                theme.palette().canvasPickerFill());
        drawBorder(graphics, kindMenuRect, theme.palette().canvasPickerBorder());
        for (int index = 0; index < kindMenuEntries.size(); index++) {
            int rowY = kindMenuRect.y() + BORDER + index * KIND_MENU_ROW_HEIGHT;
            boolean current = index == Math.clamp(kindMenuIndex, 0, kindMenuEntries.size() - 1);
            if (current) {
                graphics.fill(kindMenuRect.x() + BORDER, rowY, kindMenuRect.right() - BORDER,
                        rowY + KIND_MENU_ROW_HEIGHT, theme.palette().canvasPickerRowSelected());
            }
            graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont,
                            kindMenuEntries.get(index).label().getString(),
                            Math.max(0, kindMenuRect.width() - KIND_MENU_PADDING * 2)),
                    kindMenuRect.x() + KIND_MENU_PADDING,
                    rowY + (KIND_MENU_ROW_HEIGHT - renderFont.lineHeight) / 2,
                    current ? theme.palette().canvasPickerText() : theme.palette().canvasPickerTextMuted(), false);
        }
    }

    private void handleKindMenuClick(double mouseX, double mouseY) {
        int index = kindMenuIndexAt(mouseX, mouseY);
        if (index >= 0) {
            kindMenuIndex = index;
            confirmKindMenu();
            return;
        }
        if (!kindMenuRect.contains(mouseX, mouseY)) {
            closeKindMenu();
        }
    }

    private int kindMenuIndexAt(double mouseX, double mouseY) {
        if (!kindMenuListRect.contains(mouseX, mouseY)) {
            return -1;
        }
        int row = (int) ((mouseY - kindMenuListRect.y()) / Math.max(1, KIND_MENU_ROW_HEIGHT));
        return row >= 0 && row < kindMenuEntries.size() ? row : -1;
    }

    // 弹层高亮跟随指针：指针停在列表里时悬停行即时高亮，移出列表则保留键盘选中的行
    private void updateKindMenuHover(int mouseX, int mouseY) {
        boolean moved = !kindMenuPointerKnown || mouseX != kindMenuLastMouseX || mouseY != kindMenuLastMouseY;
        kindMenuPointerKnown = true;
        kindMenuLastMouseX = mouseX;
        kindMenuLastMouseY = mouseY;
        if (!moved) {
            return;
        }
        int index = kindMenuIndexAt(mouseX, mouseY);
        if (index >= 0) {
            kindMenuIndex = index;
        }
    }

    // 键盘：←/→ 或 ↑/↓ 切换，回车确认，Esc 取消
    private void kindMenuKeyPressed(int keyCode) {
        if (kindMenuEntries.isEmpty()) {
            closeKindMenu();
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeKindMenu();
        } else if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_UP) {
            kindMenuIndex = Math.max(0, kindMenuIndex - 1);
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_DOWN) {
            kindMenuIndex = Math.min(kindMenuEntries.size() - 1, kindMenuIndex + 1);
        } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirmKindMenu();
        }
    }

    // 确认弹层选项：分组方式插入分支或包住选区，删除确认决定删除范围
    private void confirmKindMenu() {
        if (!kindMenuOpen || kindMenuEntries.isEmpty()) {
            return;
        }
        KindMenuAction action = kindMenuEntries.get(Math.clamp(kindMenuIndex, 0, kindMenuEntries.size() - 1)).action();
        closeKindMenu();
        switch (action) {
            case WRAP_ALL -> wrapSelection(true);
            case WRAP_ANY -> wrapSelection(false);
            case DELETE_KEEP_CHILDREN -> deleteSelection(true, tree.normalizedSelection());
            case DELETE_SUBTREE -> deleteSelection(false, tree.normalizedSelection());
            case INSERT_ALL -> insertBranch(true);
            case INSERT_ANY -> insertBranch(false);
        }
    }

    // 新建分支插入后直接把条件类型列表弹到它的子项热区上，省掉玩家再点一次热区的那一步
    private void insertBranch(boolean allOf) {
        ConditionNode group = allOf ? new ConditionNode.AllOf(List.of()) : new ConditionNode.AnyOf(List.of());
        lastOpKey = EditSession.OP_ADD_GROUP;
        if (!addNode(group)) {
            return;
        }
        UiTreePath groupPath = graph.path(group);
        if (groupPath == null) {
            return;
        }
        openPicker(new Hotspot(groupPath, 0, bounds));
    }

    // ---- 输入 ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !enabled) {
            return false;
        }
        if (picker.isOpen()) {
            picker.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (kindMenuOpen) {
            handleKindMenuClick(mouseX, mouseY);
            return true;
        }
        if (!bounds.contains(mouseX, mouseY)) return false;
        // 右侧面板优先接收输入；关闭动画期间只阻止点穿，不再编辑字段。
        if (inlinePanel.contains(mouseX, mouseY)) {
            inlinePanel.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        boolean left = button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
        // 中键与「空格 + 左键」是附加平移手势；空白区左键本身也能平移
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE || (left && spaceDown)) {
            beginPan(mouseX, mouseY, false);
            return true;
        }
        if (!left) {
            return false;
        }
        endGestureForNewPress();
        double localX = transform.toLocalX(mouseX);
        double localY = transform.toLocalY(mouseY);
        // 每个容器卡片下方的 [+] 热区都是新增条件的入口，点它就弹类型列表
        Hotspot hotspot = graph.hotspotAt(localX, localY);
        if (hotspot != null) {
            openPicker(hotspot);
            return true;
        }
        ConditionNode node = graph.nodeAt(localX, localY);
        if (node == null) {
            // 空白区左键拖拽 = 平移；框选改到 Shift + 拖拽
            if (Screen.hasShiftDown()) {
                beginRubberBand(localX, localY);
                return true;
            }
            beginPan(mouseX, mouseY, true);
            return true;
        }
        UiTreePath clicked = graph.path(node);
        if (clicked == null) {
            return true;
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
        followPrimarySelection();
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
        return true;
    }

    private void endGestureForNewPress() {
        armedDrag = null;
        clickedLeafPath = null;
        leafEditArmed = false;
        collapseSelectionOnRelease = false;
        dragLabel = null;
        dropTarget = null;
        if (move.isActive()) {
            move.end(UiInputCapture.EndReason.CANCEL);
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
            followPrimarySelection();
            return;
        }
        Set<UiTreePath> selected = new LinkedHashSet<>(additive ? tree.selection() : Set.of());
        for (int index = Math.min(anchorIndex, clickedIndex); index <= Math.max(anchorIndex, clickedIndex); index++) {
            selected.add(graph.path(graph.nodes().get(index)));
        }
        selected.remove(null);
        tree.setSelection(selected, clicked);
    }

    // 开始平移；blankCandidate 表示这次按下落在空白区，释放时没有位移就按点击处理
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
            followPrimarySelection();
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
            followPrimarySelection();
        }
        rubberActive = false;
        rubberBand.clear();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!visible || !enabled) {
            return false;
        }
        if (picker.isOpen()) {
            picker.mouseDragged(mouseY, button);
            return true;
        }
        if (inlinePanel.hasCapture()) {
            inlinePanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
            return true;
        }
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
                        ? Component.translatable("gui.itemdespawntowhat.edit.tree.drag_many", sources.size()).getString()
                        : node == null ? "" : graph.titleLabel(node).getString();
            }
        }
        if (!move.isActive()) {
            return source != null;
        }
        projectDrop(localX, localY);
        return true;
    }

    // 落点判定：卡片上四分之一为 BEFORE、下四分之一为 AFTER，中间为 INSIDE；叶节点按上下半区择近。
    private void projectDrop(double localX, double localY) {
        ConditionNode target = graph.nodeAt(localX, localY);
        if (target == null) {
            dropTarget = null;
            move.clearPreview();
            operationError = null;
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
        operationError = preview == null ? null : preview.outcome().error();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (picker.isOpen()) {
            picker.mouseReleased(button);
            return true;
        }
        if (inlinePanel.hasCapture()) {
            return inlinePanel.mouseReleased(mouseX, mouseY, button);
        }
        if (panning) {
            // 空白区按下并拖动才算平移；没有位移时按空白点击处理：清空选择
            boolean blankClick = panPressedBlank && !panMoved;
            clearPanState();
            if (blankClick) {
                tree.setSelection(List.of(), null);
                followPrimarySelection();
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
            followPrimarySelection();
        }
        collapseSelectionOnRelease = false;
        if (move.isActive() && left) {
            projectDrop(transform.toLocalX(mouseX), transform.toLocalY(mouseY));
            lastOpKey = EditSession.OP_MOVE_NODE;
            move.end(UiInputCapture.EndReason.RELEASE);
        }
        armedDrag = null;
        clickedLeafPath = null;
        leafEditArmed = false;
        dropTarget = null;
        if (editLeaf) editSelectedLeaf();
        return owned;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!visible || !enabled || !bounds.contains(mouseX, mouseY) || scrollY == 0.0D) {
            return false;
        }
        if (picker.isOpen()) {
            picker.mouseScrolled(mouseX, mouseY, scrollY);
            return true;
        }
        if (kindMenuOpen) {
            if (!kindMenuEntries.isEmpty()) {
                kindMenuIndex = Math.clamp(kindMenuIndex + (scrollY > 0.0D ? -1 : 1), 0, kindMenuEntries.size() - 1);
            }
            return true;
        }
        if (inlinePanel.contains(mouseX, mouseY)) {
            inlinePanel.mouseScrolled(mouseX, mouseY, scrollY);
            return true;
        }
        // 滚轮以鼠标位置为锚点逐档缩放
        transform.zoomBy(scrollY > 0.0D ? 1 : -1, mouseX, mouseY);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || !enabled) {
            return false;
        }
        if (picker.isOpen()) {
            picker.keyPressed(keyCode);
            return true;
        }
        if (kindMenuOpen) {
            kindMenuKeyPressed(keyCode);
            return true;
        }
        if (move.isActive()) {
            if (UiHistoryShortcut.fromKey(keyCode, modifiers) != null || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                endInteractions(UiInputCapture.EndReason.CANCEL);
                return true;
            }
            return true;
        }
        // 内联框优先拿键盘：文本框聚焦时画布快捷键必须让位
        if (inlinePanel.keyPressed(keyCode, scanCode, modifiers)) return true;
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0
                && (modifiers & (GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) == 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (control) {
            if (keyCode == GLFW.GLFW_KEY_A) return selectAll();
            if (keyCode == GLFW.GLFW_KEY_C) return copySelection();
            if (keyCode == GLFW.GLFW_KEY_V) return pasteSelection();
            if (keyCode == GLFW.GLFW_KEY_0) {
                fitToContent();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_HOME) {
                resetView();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) {
                zoomStep(1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
                zoomStep(-1);
                return true;
            }
        }
        if (inlinePanel.isOpen()) {
            // 内联框打开且未匹配 Ctrl 快捷键：其余按键交给文本框，避免输入字母 / 空格 / 删除误触画布命令
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            spaceDown = true;
            return true;
        }
        if (graph.nodes().isEmpty()) {
            if (keyCode == GLFW.GLFW_KEY_A || keyCode == GLFW.GLFW_KEY_ENTER
                    || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                return beginAddCondition();
            }
            if (keyCode == GLFW.GLFW_KEY_G) {
                return beginAddGroup(!shift);
            }
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN
                || keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
            return navigate(keyCode);
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            return activate();
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            return removeSelection();
        }
        if (keyCode == GLFW.GLFW_KEY_A) {
            return beginAddCondition();
        }
        if (keyCode == GLFW.GLFW_KEY_G) {
            return beginAddGroup(!shift);
        }
        if (keyCode == GLFW.GLFW_KEY_N) {
            return invertSelection();
        }
        return false;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            spaceDown = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible || !enabled) {
            return false;
        }
        if (picker.isOpen()) {
            return picker.charTyped(codePoint);
        }
        if (kindMenuOpen) {
            // 弹层只接受方向键与回车，不接受文本输入
            return false;
        }
        return inlinePanel.charTyped(codePoint, modifiers);
    }

    // ---- 键盘导航 ----

    private boolean navigate(int keyCode) {
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
        return selectNode(java.util.Objects.requireNonNull(tree.query(next)));
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

    private boolean selectNode(ConditionNode node) {
        UiTreePath path = graph.path(node);
        if (path == null) {
            return false;
        }
        tree.select(path);
        followPrimarySelection();
        revealNode(node);
        return true;
    }

    // 把卡片带进视口，保留 REVEAL_MARGIN 屏幕像素边距
    private void revealNode(ConditionNode node) {
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

    // ---- 类型选择确认与树写入 ----

    private List<UiTypePicker.Option> pickerOptions() {
        return typeOptions.stream().map(option -> new UiTypePicker.Option(option.id(), option.label())).toList();
    }

    private void openPicker(Hotspot hotspot) {
        closeKindMenu();
        pickerAppend = false;
        pickerParent = hotspot.parent();
        pickerIndex = hotspot.index();
        picker.open(pickerOptions(), bounds);
    }

    public void closePicker() {
        picker.close();
        pickerParent = null;
    }

    private void confirmPicker(ResourceLocation type) {
        UiTreePath parent = pickerParent;
        int index = pickerIndex;
        boolean append = pickerAppend;
        closePicker();
        ConditionTreeContract.LeafFactory factory = leafFactory;
        if (factory == null) return;
        ConditionNode leaf = factory.create(type);
        if (leaf == null) return;
        lastOpKey = EditSession.OP_ADD_CONDITION;
        boolean inserted = append ? addNode(leaf) : insertNode(parent, index, leaf);
        if (inserted) {
            revealNode(leaf);
            // 新条件尚无参数，直接打开参数框，省去再点一次叶卡片
            editSelectedLeaf();
        }
    }

    // ---- 树变换 ----

    private void applyNode(@Nullable ConditionNode newRoot) {
        this.expression = new ConditionExpression(newRoot);
        rebuild();
        if (listener != null) {
            listener.onExpressionChanged(this.expression);
        }
    }

    private void followPrimarySelection() {
        UiTreePath primary = tree.selected();
        selectedPath = primary == null ? null
                : ConditionTreeNodes.jsonPath(expression.root(), primary, ConditionTreeContract.ROOT_PATH);
    }

    private boolean accept(UiTreeEditor.Outcome result) {
        operationError = result.error();
        return result.changed();
    }

    // 指定节点更新由 Kit 校验和提交，不在控件中复制结构变换算法
    private boolean applyAt(UiTreePath path, UnaryOperator<ConditionNode> transform) {
        return accept(tree.update(path, transform));
    }

    // 插入节点：parent 为 null 只允许空树写入根
    private boolean insertNode(@Nullable UiTreePath parent, int index, ConditionNode node) {
        boolean changed = parent == null ? accept(tree.create(null, 0, node)) : accept(tree.create(parent, index, node));
        if (changed) {
            followPrimarySelection();
            rebuild();
        }
        return changed;
    }

    // 追加节点：有目标容器就追加，没有容器时包一层 ALL 以保持语义
    private boolean addNode(ConditionNode newNode) {
        if (expression.root() == null) {
            return insertNode(null, 0, newNode);
        }
        UiTreePath parent = targetContainerPath();
        if (parent == null) {
            boolean changed = applyAt(UiTreePath.ROOT, root -> new ConditionNode.AllOf(List.of(root, newNode)));
            if (changed) {
                tree.select(UiTreePath.ROOT.child(1));
                followPrimarySelection();
            }
            return changed;
        }
        ConditionNode target = tree.query(parent);
        if (target instanceof ConditionNode.Inverted) {
            // NOT 只能容纳一个子节点；在其子节点处包组，保留取反范围与现有条件。
            UiTreePath child = parent.child(0);
            boolean changed = applyAt(child, node -> new ConditionNode.AllOf(List.of(node, newNode)));
            if (changed) {
                tree.select(child.child(1));
                followPrimarySelection();
            }
            return changed;
        }
        int size = target == null ? 0 : ConditionTreeNodes.INSTANCE.children(target).size();
        return insertNode(parent, size, newNode);
    }

    // 明确选中位置优先；无选择时以最深叶为锚点，同深度取树序最后一叶。
    private @Nullable UiTreePath targetContainerPath() {
        UiTreePath selected = tree.selected();
        if (selected == null) {
            for (ConditionNode node : graph.nodes()) {
                if (!(node instanceof ConditionNode.Leaf)) continue;
                UiTreePath path = graph.path(node);
                if (path != null && (selected == null || path.indices().size() >= selected.indices().size())) {
                    selected = path;
                }
            }
            if (selected == null) return null;
        }
        UiTreePath current = selected;
        while (true) {
            ConditionNode node = tree.query(current);
            if (isContainer(node)) {
                return current;
            }
            if (current.indices().isEmpty()) {
                return null;
            }
            current = current.parent();
        }
    }

    // 取分组子项，非分组返回 null
    private static @Nullable List<ConditionNode> termsOf(@Nullable ConditionNode node) {
        if (node instanceof ConditionNode.AllOf(var allTerms)) {
            return allTerms;
        }
        if (node instanceof ConditionNode.AnyOf(var anyTerms)) {
            return anyTerms;
        }
        return null;
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

    // ---- 焦点 ----

    @Override
    public boolean canFocus() {
        return visible && enabled;
    }

    @Override
    public void setFocused(boolean newFocused) {
        if (focused && !newFocused) {
            endInteractions(UiInputCapture.EndReason.FOCUS_SCOPE_CHANGED);
        }
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
        ConditionNode node = selectedNode();
        if (node == null) {
            return beginAddCondition();
        }
        if (node instanceof ConditionNode.Leaf) {
            return editSelectedLeaf();
        }
        return false;
    }

    @Override
    public Component accessibleName() {
        return Component.translatable("gui.itemdespawntowhat.edit.tree.accessible_name");
    }
}
