package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiRenderLayers;
import com.meteorite.itemdespawntowhat.client.edit.FieldPath;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiShellWidget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiWidgetShell;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;

import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphMenus.GroupKind;
import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphMenus.KindMenuAction;
import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphMenus.KindMenuEntry;
import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphMenus.KindMenuPurpose;

import java.util.Objects;

import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.BORDER;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.CARD_PADDING_X;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.isContainer;

import com.meteorite.itemdespawntowhat.client.edit.ConditionTreeNodes;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.OpaqueCondition;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiHistoryShortcut;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiTransform;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.tree.UiTreeEditor;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.tree.UiTreePath;
import com.meteorite.itemdespawntowhat.client.ui.shared.DescriptionLine;
import com.meteorite.itemdespawntowhat.client.ui.shared.DescriptionLine.Tone;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.Hotspot;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
 * {@link #setInlineEditor(InlineEditor)} 装载条件叶参数；候选修改经绑定回调交回宿主，
 * 控件不持有正式保存入口。缩进摘要 {@link UiConditionTreeSummary} 共用条件数据与校验，保持只读。</p>
 */
public final class UiConditionGraphEditor implements UiInteractionSession.Target, UiShellWidget, UiLayoutSource, ConditionTreeContract.Navigation {

    // 视图定位与手势阈值使用 GUI 屏幕像素。
    private static final int FIT_PADDING = 12;
    private static final int RESET_TOP_MARGIN = 16;
    // 条件参数说明的行数较多，比动作说明的默认宽度更宽
    private static final int CONDITION_TOOLTIP_WIDTH = 300;

    /**
     * 参数编辑框的内容契约：几何、绘制与输入坐标一律为 GUI 屏幕坐标，不随画布平移或缩放。
     *
     * <p>画布负责右侧面板的背景、标题、裁剪与 160ms ease-out 滑入动画；宿主只需实现表单本体。</p>
     */
    public interface InlineEditor extends UiInputTarget {
        int preferredHeight(int width);

        UiRect bounds();                       // GUI 屏幕坐标

        void setBounds(int x, int y, int width, int height);

        void render(GuiGraphics graphics, Font font, UiPointer pointer);   // GUI 屏幕坐标

        boolean mousePressed(UiInputContext context);

        boolean mouseDragged(UiInputContext context, double dragX, double dragY);

        boolean mouseReleased(UiInputContext context);

        boolean mouseScrolled(UiInputContext context, double scrollX, double scrollY);

        boolean keyPressed(int keyCode, int scanCode, int modifiers);

        boolean charTyped(char codePoint, int modifiers);

        boolean isVisible();

        /**
         * @return 参数框内的文本输入正在编辑：画布快捷键必须让位
         */
        boolean capturesKeyboard();
    }

    private final ConditionGraphMenus menus;
    private final Font font;
    private final UiTransform transform = UiTransform.ofScales(0.5, 1.0, 1.5, 2.0);
    private final ConditionGraphLayout graph;
    private final ConditionGraphRenderer renderer;
    private final UiTreeEditor<ConditionNode> tree;
    private final ConditionGraphInteraction gestures;

    private ConditionExpression expression = ConditionExpression.EMPTY;
    private ConditionTreeViewModel viewModel = new ConditionTreeViewModel();
    private final UiWidgetShell shell = new UiWidgetShell(this, this::invalidateInteraction);
    private boolean viewInitialized;
    private boolean centerEmptyView;
    private @Nullable ConditionNode laidOutRoot;

    private @Nullable Function<ConditionNode.Leaf, Component> leafSummary;
    private @Nullable Function<ConditionNode.Leaf, List<DescriptionLine>> leafTooltip;
    // 叶节点参数说明只在重建后失效，避免每帧重复生成
    private final Map<ConditionNode.Leaf, List<DescriptionLine>> leafTooltipCache = new HashMap<>();
    private List<ConditionTreeContract.TypeOption> typeOptions = List.of();
    private @Nullable ConditionTreeContract.LeafFactory leafFactory;
    private @Nullable Consumer<ConditionNode.Leaf> onEditLeaf;
    private @Nullable Consumer<StructureChange> listener;
    private Runnable onViewChanged = () -> {
    };
    private @Nullable ToolState toolState;
    private @Nullable Runnable copyAction;
    private @Nullable Runnable pasteAction;
    private final Component emptyMessage = Component.translatable(TypeLabels.UI_PREFIX + "tree.add_placeholder");

    private @Nullable Component operationError;
    private String lastOpKey = EditSession.OP_EDIT_CONDITIONS;
    private @Nullable FieldPath selectedPath;

    private final ConditionGraphInlinePanel inlinePanel;
    private final UiDescriptionTooltip tooltip;

    // 注入的宿主主题
    private final UiHostTheme theme;

    /**
     * {@inheritDoc}
     */
    @Override
    public UiWidgetShell shell() {
        return shell;
    }

    private void invalidateInteraction(UiInteractionSession.EndReason reason) {
        endInteractions(reason);
    }

    /**
     * 装配画布的唯一树、私有交互和呈现协作，业务会话由宿主持有。
     */
    public UiConditionGraphEditor(UiHostTheme theme, Font font) {
        this.theme = UiHostThemes.require(theme);
        this.font = font;
        graph = new ConditionGraphLayout(font);
        renderer = new ConditionGraphRenderer(this.theme, font, graph);
        menus = new ConditionGraphMenus(this.theme, font, this::confirmPicker, this::confirmKindMenu, this::viewChanged);
        inlinePanel = new ConditionGraphInlinePanel(this.theme, font, graph);
        tooltip = new UiDescriptionTooltip(this.theme, font).setMaxWidth(CONDITION_TOOLTIP_WIDTH);
        tree = new UiTreeEditor<>(ConditionTreeNodes.INSTANCE, () -> expression.root(), change -> {
            applyNode(change.after());
            if (listener != null) listener.accept(new StructureChange(lastOpKey, change));
        }, ConditionTreeInspection::validateCandidate,
                Component.translatable(TypeLabels.UI_PREFIX + "tree.invalid_path"),
                Component.translatable(TypeLabels.UI_PREFIX + "tree.invalid_parent"));
        tree.setOnViewChanged(change -> {
            followPrimarySelection();
            inlinePanel.validateAnchor();
            viewChanged();
        });
        tree.setMoveErrors(new UiTreeEditor.MoveErrors(
                Component.translatable(TypeLabels.UI_PREFIX + "tree.move_cycle"),
                Component.translatable(TypeLabels.UI_PREFIX + "tree.move_root_sibling"),
                Component.translatable(TypeLabels.UI_PREFIX + "tree.move_leaf_parent"),
                Component.translatable(TypeLabels.UI_PREFIX + "tree.move_full_parent"),
                Component.translatable(TypeLabels.UI_PREFIX + "tree.move_stale")));
        gestures = new ConditionGraphInteraction(tree, graph, transform,
                new ConditionGraphInteraction.Actions(this::openPicker, this::editSelectedLeaf, this::showOperationError, result -> {
                    accept(result);
                    if (result.changed()) {
                        followPrimarySelection();
                        rebuild();
                        if (selectedPath != null) selectPath(selectedPath);
                    }
                }));
    }

    // ---- 宿主注入面 ----

    /**
     * 使用字段级展示协调器复用摘要与画布的结构诊断版本。
     */
    public void setViewModel(ConditionTreeViewModel model) {
        viewModel = model;
        rebuild();
    }

    /**
     * 一次完整结构事实；宿主在同一历史操作中迁移输入并提交整个条件作用域。
     */
    public record StructureChange(String opKey, UiTreeEditor.Change<ConditionNode> change) {
    }

    /**
     * 工具栏只读投影；集合冻结，静止状态复用同一份快照。
     */
    public record ToolState(int selectionCount, List<UiTreePath> selectedSubtrees,
                            boolean canMoveUp, boolean canMoveDown, boolean addSelected,
                            boolean branchSelected, boolean removeSelected, boolean wrapSelected) {
        /**
         * 冻结规范化选择，外部不能改变画布的多选状态。
         */
        public ToolState {
            selectedSubtrees = List.copyOf(selectedSubtrees);
        }
    }

    /**
     * 视图通知只请求宿主准备；树操作稳定后再装配参数表单。
     */
    public void setOnViewChanged(Runnable callback) {
        onViewChanged = callback;
    }

    private void viewChanged() {
        toolState = null;
        onViewChanged.run();
    }

    /**
     * 获取动作资格与选中态，不向宿主暴露菜单模式或可变树。
     */
    public ToolState toolState() {
        if (toolState == null) {
            toolState = new ToolState(tree.selection().size(), tree.normalizedSelection(),
                    canMoveSelection(-1), canMoveSelection(1), menus.picker.isOpen(),
                    menus.kindMenuOpen && menus.kindMenuPurpose == KindMenuPurpose.INSERT_GROUP,
                    menus.kindMenuOpen && menus.kindMenuPurpose == KindMenuPurpose.DELETE_CONFIRM,
                    menus.kindMenuOpen && menus.kindMenuPurpose == KindMenuPurpose.WRAP_SELECTION);
        }
        return toolState;
    }

    /**
     * 移动、平移或框选仍在进行时延后宿主的选择装配。
     */
    public boolean isDragging() {
        return gestures.isActive();
    }

    /**
     * 注入可创建的条件类型；列表变更后重建画布与类型选择器。
     */
    public void setTypeOptions(List<ConditionTreeContract.TypeOption> options) {
        this.typeOptions = List.copyOf(options);
        menus.picker.setOptions(pickerOptions());
        rebuild();
    }

    /**
     * 注入叶节点工厂，由它在类型确认后创建条件叶。
     *
     * @param factory 创建回调，可为 {@code null} 取消注入；此时类型确认不会写入节点
     */
    public void setLeafFactory(@Nullable ConditionTreeContract.LeafFactory factory) {
        this.leafFactory = factory;
    }

    /**
     * 注入卡片摘要格式化；为空时卡片回落到条件类型标签。
     *
     * @param formatter 叶节点摘要回调，可为 {@code null} 取消注入
     */
    public void setLeafSummary(@Nullable Function<ConditionNode.Leaf, Component> formatter) {
        this.leafSummary = formatter;
        rebuild();
    }

    /**
     * 注入叶节点的参数说明行；返回空列表时回落到卡片摘要。
     *
     * @param formatter 把条件叶转换成说明行的回调，可为 {@code null} 取消注入
     */
    public void setLeafTooltip(@Nullable Function<ConditionNode.Leaf, List<DescriptionLine>> formatter) {
        this.leafTooltip = formatter;
        leafTooltipCache.clear();
    }

    /**
     * 注入完整结构事件；opKey 与 Change 来自同一次树动作，宿主同步输入映射与草稿历史。
     *
     * @param newListener 变更回调，可为 {@code null} 取消注入
     */
    public void setListener(@Nullable Consumer<StructureChange> newListener) {
        this.listener = newListener;
    }

    /**
     * 注入叶参数编辑入口；为空时叶节点只能选中，不能打开参数表单。
     *
     * @param callback 编辑回调，可为 {@code null} 取消注入
     */
    public void setOnEditLeaf(@Nullable Consumer<ConditionNode.Leaf> callback) {
        this.onEditLeaf = callback;
    }

    /**
     * 宿主在切页、历史回填、卸载或关闭时取消手势；失去输入作用域时同时释放键盘修饰态，只有正常释放可提交移动。
     */
    public void endInteractions(UiInteractionSession.EndReason reason) {
        if (reason == UiInteractionSession.EndReason.UNMOUNTED || reason == UiInteractionSession.EndReason.HOST_CLOSED) {
            renderer.releaseResources();
        }
        gestures.end(this, reason);
        menus.picker.cancelCapture();
        if (reason != UiInteractionSession.EndReason.RELEASE) {
            closePicker();
            menus.close();
        }
    }

    @Override
    public void interactionEnded(UiInteractionSession.EndReason reason) {
        endInteractions(reason);
    }

    @Override
    public void scopeExited(UiInteractionSession.EndReason reason) {
        if (reason == UiInteractionSession.EndReason.HOST_CLOSED) renderer.releaseResources();
        gestures.releaseSpace();
        closePicker();
        menus.close();
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

    // ---- 数据 ----

    public ConditionExpression expression() {
        return expression;
    }

    /**
     * 同步表达式，不产生结构提交；宿主的历史或外部回填先结束旧交互，结构操作前同步保留当前捕获。
     */
    public void setExpression(ConditionExpression newExpression) {
        this.expression = newExpression == null ? ConditionExpression.EMPTY : newExpression;
        tree.setSelection(tree.selection(), tree.selected());
        followPrimarySelection();
        rebuild();
        viewChanged();
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

    /**
     * 违规项明细（只读）。
     */
    public List<ConditionTreeInspection.Issue> issues() {
        return graph.issues();
    }

    @Override
    public @Nullable FieldPath selectedPath() {
        return selectedPath;
    }

    @Override
    public @Nullable ConditionNode selectedNode() {
        UiTreePath path = tree.selected();
        return path == null ? null : tree.query(path);
    }

    /**
     * 按 JSON 路径选中节点或其所属节点，并把卡片带回视口。
     */
    @Override
    public void selectPath(@Nullable FieldPath path) {
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
        gestures.setSelectionAnchor(treePath);
        // 外部路径定位优先于首帧整树适配，避免预览跳转后视口又被重置。
        centerEmptyView = false;
        gestures.revealNode(target);
    }

    // ---- 结构操作（工具栏与键盘共用） ----

    /**
     * 直接打开类型列表，确认后追加到当前容器或在根外包一层全部满足。
     */
    public boolean beginAddCondition() {
        if (!isEnabled()) {
            return false;
        }
        openPicker(new Hotspot(null, 0, bounds()));
        menus.pickerAppend = true;
        return true;
    }

    public boolean hasPopup() {
        return menus.picker.isOpen() || menus.kindMenuOpen;
    }

    /**
     * 宿主提供源物品与结果摘要；缺失内容用空摘要保留端点框。
     */
    public void setEndpoints(Component sourceSummary, Component resultSummary) {
        if (graph.setEndpoints(sourceSummary, resultSummary)) rebuild();
    }

    /**
     * 进入新增分组操作；编辑器不可用时拒绝。
     */
    public boolean beginAddGroup(boolean allOf) {
        if (!isEnabled()) {
            return false;
        }
        endInteractions(UiInteractionSession.EndReason.CANCEL);
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
    public void beginAddGroupWithKind(UiRect anchorScreen) {
        if (!isEnabled()) {
            return;
        }
        endInteractions(UiInteractionSession.EndReason.CANCEL);
        closePicker();
        menus.close();
        menus.open(KindMenuPurpose.INSERT_GROUP, List.of(new KindMenuEntry(GroupKind.ALL.menuLabel(), KindMenuAction.INSERT_ALL),
                new KindMenuEntry(GroupKind.ANY.menuLabel(), KindMenuAction.INSERT_ANY)), anchorScreen);
        prepareMenus();
    }

    /**
     * 对当前选中的同父连续节点弹出「全部满足 / 任一满足」包组菜单；无选中时不弹出。
     */
    public void beginWrapWithKind() {
        if (!isEnabled() || tree.selection().isEmpty()) {
            return;
        }
        endInteractions(UiInteractionSession.EndReason.CANCEL);
        closePicker();
        menus.open(KindMenuPurpose.WRAP_SELECTION, List.of(new KindMenuEntry(GroupKind.ALL.menuLabel(), KindMenuAction.WRAP_ALL),
                new KindMenuEntry(GroupKind.ANY.menuLabel(), KindMenuAction.WRAP_ANY)), null);
        prepareMenus();
    }

    /**
     * 把选中节点包一层 NOT；多选时包成「NOT（ALL 选中项）」。
     */
    public boolean invertSelection() {
        if (!isEnabled() || tree.selection().isEmpty()) {
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
                Component.translatable(TypeLabels.UI_PREFIX + "tree.wrap_selection")));
        if (changed) {
            followPrimarySelection();
            rebuild();
        }
        return changed;
    }

    /**
     * 删除选中节点；选中项下方还有分支时先弹确认菜单，由玩家选择只删本级还是整支删除。
     */
    public boolean removeSelection() {
        return removeSelection(null);
    }

    /**
     * 删除选中节点：下方还有分支时贴着触发按钮弹出确认菜单，否则直接删除。
     *
     * @param anchorScreen 触发删除的工具格屏幕矩形；为 {@code null}（键盘删除）时贴着选区卡片弹出
     */
    public boolean removeSelection(@Nullable UiRect anchorScreen) {
        if (!isEnabled() || tree.selection().isEmpty()) {
            return false;
        }
        List<UiTreePath> selected = tree.normalizedSelection();
        if (!canKeepChildren(selected)) {
            return deleteSelection(false, selected);
        }
        endInteractions(UiInteractionSession.EndReason.CANCEL);
        closePicker();
        menus.close();
        menus.open(KindMenuPurpose.DELETE_CONFIRM, List.of(
                new KindMenuEntry(Component.translatable(TypeLabels.UI_PREFIX + "tree.delete.keep_children"),
                        KindMenuAction.DELETE_KEEP_CHILDREN),
                new KindMenuEntry(Component.translatable(TypeLabels.UI_PREFIX + "tree.delete.subtree"),
                        KindMenuAction.DELETE_SUBTREE)), anchorScreen);
        prepareMenus();
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

    /**
     * 同级排序：delta 为 -1 / +1，仅单选中节点可移动。
     *
     * <p>可移动性由 {@link #canMoveSelection(int)} 按帧查询，失败原因由 {@link #operationError()} 承载；
     * 本方法不返回结果，键盘入口调用后直接视为已消费按键。</p>
     */
    public void moveSelection(int delta) {
        if (tree.normalizedSelection().size() > 1) {
            operationError = Component.translatable(TypeLabels.UI_PREFIX + "tree.move_multiple");
            return;
        }
        if (!canMoveSelection(delta)) {
            return;
        }
        UiTreePath selected = tree.selected();
        if (selected == null || selected.indices().isEmpty()) {
            return;
        }
        UiTreePath parent = selected.parent();
        int index = selected.indices().getLast();
        int target = index + delta;
        lastOpKey = EditSession.OP_MOVE_NODE;
        boolean changed = applyAt(parent, node -> swapTerms(node, index, target));
        if (changed) {
            tree.select(parent.child(target));
            followPrimarySelection();
        }
    }

    /**
     * 当前单选节点能否按 delta 方向与同级节点交换；供工具栏按帧决定上移/下移是否可用。
     *
     * <p>只做结构判断，不写入表达式，也不触发校验。</p>
     */
    private boolean canMoveSelection(int delta) {
        if (!isEnabled() || delta == 0) {
            return false;
        }
        if (tree.normalizedSelection().size() > 1) {
            return false;
        }
        UiTreePath selected = tree.selected();
        if (selected == null || selected.indices().isEmpty()) {
            return false;
        }
        List<ConditionNode> terms = termsOf(tree.query(selected.parent()));
        if (terms == null) {
            return false;
        }
        int target = selected.indices().getLast() + delta;
        return target >= 0 && target < terms.size();
    }

    // 把选中的连续同级节点包成 ALL 或 ANY 分组；写入失败时 operationError 已携带原因
    private void wrapSelection(boolean all) {
        if (!isEnabled()) {
            return;
        }
        endInteractions(UiInteractionSession.EndReason.CANCEL);
        lastOpKey = EditSession.OP_ADD_GROUP;
        boolean changed = accept(tree.wrapMany(tree.selection(),
                nodes -> all ? new ConditionNode.AllOf(nodes) : new ConditionNode.AnyOf(nodes),
                Component.translatable(TypeLabels.UI_PREFIX + "tree.wrap_selection")));
        if (changed) {
            followPrimarySelection();
            rebuild();
        }
    }

    // Ctrl+D：把选中子树复制为同级后继；根节点没有同级位置，父节点满时整体拒绝
    private boolean duplicateSelection() {
        if (!isEnabled() || tree.selection().isEmpty()) {
            return false;
        }
        List<UiTreePath> selected = tree.normalizedSelection();
        if (selected.stream().anyMatch(path -> path.indices().isEmpty())) {
            operationError = Component.translatable(TypeLabels.UI_PREFIX + "tree.duplicate_root");
            return false;
        }
        for (UiTreePath path : selected) {
            ConditionNode parent = tree.query(path.parent());
            if (parent == null || ConditionTreeNodes.INSTANCE.childCapacity(parent)
                    <= ConditionTreeNodes.INSTANCE.children(parent).size()) {
                operationError = Component.translatable(TypeLabels.UI_PREFIX + "tree.duplicate_full");
                return false;
            }
        }
        Set<UiTreePath> targets = Set.copyOf(selected);
        List<ConditionNode> copies = new ArrayList<>();
        lastOpKey = EditSession.OP_DUPLICATE_NODE;
        boolean changed = applyAt(UiTreePath.ROOT, root -> {
            copies.clear();
            return duplicateWithin(root, UiTreePath.ROOT, targets, copies);
        });
        if (!changed) {
            return false;
        }
        rebuild();
        List<UiTreePath> copyPaths = new ArrayList<>();
        for (ConditionNode copy : copies) {
            UiTreePath path = graph.path(copy);
            if (path != null) {
                copyPaths.add(path);
            }
        }
        if (!copyPaths.isEmpty()) {
            tree.setSelection(copyPaths, copyPaths.getLast());
            followPrimarySelection();
        }
        return true;
    }

    // 递归重建：每个选中节点后插入一份深拷贝，副本同时收集起来供复制后选中
    private static ConditionNode duplicateWithin(ConditionNode node, UiTreePath path,
                                                 Set<UiTreePath> targets, List<ConditionNode> copies) {
        List<ConditionNode> children = ConditionTreeNodes.INSTANCE.children(node);
        if (children.isEmpty()) {
            return node;
        }
        List<ConditionNode> next = new ArrayList<>(children.size() + targets.size());
        for (int index = 0; index < children.size(); index++) {
            ConditionNode child = children.get(index);
            UiTreePath childPath = path.child(index);
            next.add(duplicateWithin(child, childPath, targets, copies));
            if (targets.contains(childPath)) {
                ConditionNode copy = ConditionTreeNodes.copy(child);
                copies.add(copy);
                next.add(copy);
            }
        }
        return ConditionTreeNodes.INSTANCE.withChildren(node, next);
    }

    public boolean selectAll() {
        if (graph.nodes().isEmpty()) {
            return false;
        }
        List<UiTreePath> all = graph.nodes().stream().map(graph::path).filter(Objects::nonNull).toList();
        tree.setSelection(all, tree.selected());
        followPrimarySelection();
        return true;
    }

    /**
     * 把当前选择交给宿主复制入口；无选择或未装配入口时返回 false。
     */
    public boolean copySelection() {
        if (!isEnabled() || tree.selection().isEmpty() || copyAction == null) {
            return false;
        }
        copyAction.run();
        return true;
    }

    /**
     * 请求宿主粘贴当前剪贴板；编辑器不可用或未装配入口时返回 false。
     */
    public boolean pasteSelection() {
        if (!isEnabled() || pasteAction == null) {
            return false;
        }
        pasteAction.run();
        return true;
    }

    /**
     * 粘贴子树：优先作为选中容器的子项，其次作为选中节点的同级后继。
     */
    public void pasteNodes(List<ConditionNode> nodes) {
        if (!isEnabled() || nodes.isEmpty()) {
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
                operationError = Component.translatable(TypeLabels.UI_PREFIX + "tree.paste_parent");
                return;
            }
        }
        if (parent == null && nodes.size() > 1) {
            operationError = Component.translatable(TypeLabels.UI_PREFIX + "tree.paste_root");
            return;
        }
        endInteractions(UiInteractionSession.EndReason.CANCEL);
        lastOpKey = EditSession.OP_ADD_CONDITION;
        if (accept(tree.createMany(parent, index, nodes))) {
            followPrimarySelection();
            rebuild();
        }
    }

    /**
     * 编辑选中叶节点的参数。
     */
    public boolean editSelectedLeaf() {
        if (!isEnabled() || !canEditSelectedLeaf()) {
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

    /**
     * 缩放 100% 并把根卡片顶边中点放回视口顶部。
     */
    public void resetView() {
        transform.reset();
        UiRect rootRect = graph.rootRect();
        transform.centerOn(rootRect.x() + rootRect.width() / 2.0, rootRect.y());
        transform.panBy(0, RESET_TOP_MARGIN - transform.viewport().height() / 2.0);
    }

    /**
     * 选择能容纳整棵树的档位并居中显示。
     */
    public void fitToContent() {
        transform.fit(0, 0, graph.contentWidth(), graph.contentHeight(), FIT_PADDING);
    }

    /**
     * 逐档缩放，锚点为视口中心。
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBounds(int x, int y, int width, int height) {
        shell.setBounds(x, y, width, height);
        transform.setViewport(bounds().x(), bounds().y(), bounds().width(), bounds().height());
        menus.picker.setBounds(bounds());
        inlinePanel.setBounds(bounds());
    }

    /**
     * 导出画布的只读排版快照：源物品与结果两个端点、每张卡片、空树占位，以及打开时的类型选择面板。
     *
     * <p>画布内部矩形经 {@link #toScreenRect(UiRect)} 换算成 GUI 坐标，与绘制、命中共用同一条变换，
     * 节点矩形中心因此落在真正可点的位置。卡片文案取 {@link ConditionGraphLayout#cardText}，即绘制时
     * 按卡片宽裁剪过的那份标题与宽度，探针断言的字与玩家看到的一致；内容行与悬停详情属于悬停信息，
     * 不进快照。空树时只上报画布自身的加条件热点（占位提示画在它上方），点它的中心即可新增条件。
     * 参数面板由宿主条件树浮层并入快照，这里不重复上报。</p>
     */
    @Override
    public List<UiLayoutNode> layoutNodes() {
        if (!isVisible() || bounds().width() <= 0 || bounds().height() <= 0) {
            return List.of();
        }
        graph.ensureText();
        List<UiLayoutNode> nodes = new ArrayList<>();
        addEndpoint(nodes, graph.sourceRect(), graph.sourceTitleText(), graph.sourceTitleWidth());
        addEndpoint(nodes, graph.resultRect(), graph.resultTitleText(), graph.resultTitleWidth());
        for (ConditionNode node : graph.nodes()) {
            UiRect rect = graph.rect(node);
            if (rect == null) {
                continue;
            }
            ConditionGraphLayout.CardText text = graph.cardText(node);
            nodes.add(new UiLayoutNode(UiLayoutNode.Kind.CONTROL, toScreenRect(rect),
                    Component.literal(text.title()), text.titleWidth(), titleBudget(rect),
                    isEnabled(), true, List.of()));
        }
        if (graph.root() == null && !graph.hotspots().isEmpty()) {
            UiRect hotspot = graph.hotspots().getFirst().rect();
            nodes.add(new UiLayoutNode(UiLayoutNode.Kind.CONTROL, toScreenRect(hotspot), emptyMessage,
                    font.width(emptyMessage), titleBudget(hotspot), isEnabled(), true, List.of()));
        }
        if (menus.picker.isOpen()) {
            nodes.addAll(menus.picker.layoutNodes());
        }
        return nodes;
    }

    // 端点与卡片共用同一条标题留白：左右边距各一份，剩余宽度就是绘制时可用的文案宽
    private int titleBudget(UiRect content) {
        return Math.max(0, content.width() - (BORDER + CARD_PADDING_X) * 2);
    }

    private void addEndpoint(List<UiLayoutNode> nodes, UiRect content, String title, int titleWidth) {
        nodes.add(new UiLayoutNode(UiLayoutNode.Kind.CONTROL, toScreenRect(content),
                Component.literal(title), titleWidth, titleBudget(content), isEnabled(), true, List.of()));
    }

    /**
     * 禁用时结束当前交互，复合控件同步子目标资格。
     */
    public void setEnabled(boolean nextEnabled) {
        if (isEnabled() == nextEnabled) return;
        shell.setEnabled(nextEnabled);
        viewChanged();
    }

    /**
     * 隐藏时结束当前交互并退出焦点序列，恢复显示保留内容。
     */
    public UiConditionGraphEditor setVisible(boolean nextVisible) {
        shell.setVisible(nextVisible);
        return this;
    }

    // 全量重建：扫描状态、建立包装节点、运行整齐树布局。
    private void rebuild() {
        leafTooltipCache.clear();
        ConditionNode previousRoot = laidOutRoot;
        ConditionNode root = expression.root();
        laidOutRoot = root;
        graph.rebuild(viewModel.project(expression, typeOptions), typeOptions, leafSummary);
        // 结构提交后 Kit 还要按 Change.paths 迁移旧路径；几何重建不裁剪其视图状态。
        followPrimarySelection();
        inlinePanel.validateAnchor();
        if (!viewInitialized || root == null && previousRoot != null) {
            // 首次准备拿到视口后适配完整源物品到结果链路。
            centerEmptyView = true;
        }
        viewInitialized = true;
    }

    // 悬停提示：标题、条件详情与结构标记附在命中结果上，由根统一呈现。
    private List<DescriptionLine> nodeTooltip(ConditionNode node) {
        boolean issue = graph.hasIssue(node);
        List<DescriptionLine> lines = new ArrayList<>();
        lines.add(DescriptionLine.text(graph.titleLabel(node), issue ? Tone.DANGER : Tone.TITLE, 0));
        List<DescriptionLine> details = null;
        if (leafTooltip != null && node instanceof ConditionNode.Leaf leaf && leaf.condition() != null) {
            details = leafTooltipCache.computeIfAbsent(leaf, key -> List.copyOf(leafTooltip.apply(key)));
        }
        if (details == null || details.isEmpty()) {
            lines.add(DescriptionLine.note(graph.summaryLabel(node), 1));
        } else {
            lines.addAll(details);
        }
        Component status = graph.statusLabel(node);
        if (!status.getString().isEmpty()) {
            lines.add(DescriptionLine.text(status, Tone.DANGER, 0));
        }
        return lines;
    }

    /**
     * 当前手势或默认快捷键提示，供宿主底部状态栏展示。
     */
    public Component hint() {
        if (gestures.isMoving()) {
            UiTreeEditor.MovePreview<ConditionNode> preview = gestures.preview();
            if (preview != null && preview.outcome().error() == null && gestures.dropTarget() != null) {
                return Component.translatable(TypeLabels.UI_PREFIX + "tree.drop_"
                        + preview.position().name().toLowerCase(Locale.ROOT), graph.titleLabel(gestures.dropTarget()));
            }
            if (operationError != null) {
                return operationError;
            }
        }
        if (menus.picker.isOpen()) {
            return Component.translatable(TypeLabels.UI_PREFIX + "picker.hint");
        }
        return Component.translatable(TypeLabels.UI_PREFIX + "tree.selection_hint");
    }

    /**
     * 先确定视口与侧面板几何，绘制、命中和布局导出消费同一份结果。
     */
    public void prepareUi() {
        if (bounds().width() <= 0 || bounds().height() <= 0) return;
        if (centerEmptyView) {
            centerEmptyView = false;
            fitToContent();
        }
        menus.picker.advanceAnimation();
        menus.picker.setBounds(bounds());
        inlinePanel.prepareUi();
        prepareMenus();
    }

    /**
     * 按结构地址读取节点；返回只读模型，不暴露 Kit 的编辑能力。
     */
    public @Nullable ConditionNode nodeAt(UiTreePath path) {
        return graph.nodeAtPath(path);
    }

    // ---- 渲染 ----

    /**
     * {@inheritDoc}
     */
    @Override
    public void render(GuiGraphics graphics, Font renderFont, UiPointer incomingPointer) {
        UiPointer pointer = incomingPointer.resolve(this).forTarget(this);
        int mouseX = (int) pointer.x();
        int mouseY = (int) pointer.y();
        if (!isVisible() || bounds().width() <= 0 || bounds().height() <= 0) {
            return;
        }
        UiRect viewport = transform.viewport();
        boolean inside = pointer.contains(bounds());
        boolean emptyTree = expression.root() == null;
        double localX = transform.toLocalX(mouseX);
        double localY = transform.toLocalY(mouseY);
        boolean overPanel = pointer.allowed() && inlinePanel.contains(pointer.x(), pointer.y());
        ConditionNode hoveredNode = !menus.picker.isOpen() && !menus.kindMenuOpen && !overPanel && inside ? graph.nodeAt(localX, localY) : null;
        // 空树空闲态也要亮出中央大 [+]，让人一眼看出这里可以点
        Hotspot hoveredHotspot = !menus.picker.isOpen() && !menus.kindMenuOpen && !overPanel && inside
                ? graph.hotspotAt(localX, localY) : null;

        renderer.drawCanvasBackground(graphics, bounds());
        UiRenderLayers.enableScissor(graphics, transform.viewport());
        renderer.drawDotGrid(graphics, transform, viewport);
        transform.push(graphics);
        try {
            renderer.drawLinks(graphics, tree.selection());
            renderer.drawCards(graphics, renderFont, tree.selection(), hoveredNode);
            renderer.drawHotspots(graphics, hoveredHotspot);
            gestures.drawRubberBand(graphics, theme.palette().canvas().canvasRubberFill(), theme.palette().canvas().canvasAccent());
            if (gestures.isMoving()) {
                renderer.drawDropMarker(graphics, gestures.preview(), gestures.dropTarget());
            }
        } finally {
            graphics.pose().popPose();
            UiRenderLayers.disableScissor(graphics);
        }
        if (emptyTree) {
            renderer.drawEmptyMessage(graphics, renderFont, viewport, transform, emptyMessage);
        }
        if (!menus.picker.isOpen() && !menus.kindMenuOpen) {
            shell.renderFocusOutline(graphics, bounds(), theme);
        }
        if (gestures.isMoving()) {
            renderer.drawDragGhost(graphics, renderFont, bounds(), gestures.dragLabel(), mouseX, mouseY);
        }
        if (!menus.picker.isOpen()) {
            inlinePanel.render(graphics, renderFont, pointer);
        }
        if (menus.picker.isOpen()) {
            menus.picker.render(graphics, renderFont, pointer);
        }
        if (menus.kindMenuOpen) {
            menus.render(graphics, renderFont, pointer);
        }
    }

    // ---- 参数面板宿主接口 ----

    /**
     * 装载条件叶参数表单；null 表单或路径表示收起面板。
     */
    public void setInlineEditor(@Nullable InlineEditor editor) {
        inlinePanel.mount(editor, tree.selected());
        viewChanged();
    }

    /**
     * 结束参数编辑并向右收起面板；关闭动画期间只保留呈现。
     */
    public void clearInlineEditor() {
        inlinePanel.close();
        viewChanged();
    }

    /**
     * 参数面板是否仍接受编辑；关闭动画期间为 false。
     */
    public boolean isInlineEditorOpen() {
        return inlinePanel.isOpen();
    }

    // ---- 分组方式小菜单 ----

    private void prepareMenus() {
        if (!menus.kindMenuOpen) return;
        ConditionNode first = firstSelectedInOrder();
        UiRect rect = first == null ? null : graph.rect(first);
        menus.prepare(bounds(), rect == null ? null : toScreenRect(rect));
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

    // 确认弹层选项：分组方式插入分支或包住选区，删除确认决定删除范围
    private void confirmKindMenu(KindMenuAction action) {
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
        openPicker(new Hotspot(groupPath, 0, bounds()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean mousePressed(UiInputContext context) {
        double mouseX = context.pointerX();
        double mouseY = context.pointerY();
        int button = context.button();
        if (!isVisible() || !isEnabled()) {
            return false;
        }
        if (menus.picker.isOpen()) {
            menus.picker.mousePressed(context);
            return true;
        }
        if (menus.kindMenuOpen) {
            menus.mousePressed(mouseX, mouseY);
            return true;
        }
        if (!bounds().contains(mouseX, mouseY)) return false;
        // 右侧面板优先接收输入；关闭动画期间只阻止点穿，不再编辑字段。
        if (inlinePanel.contains(mouseX, mouseY)) {
            inlinePanel.mousePressed(context);
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) return false;

        gestures.mousePressed(context, this);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean mouseDragged(UiInputContext context, double deltaX, double deltaY) {
        double mouseY = context.pointerY();
        int button = context.button();
        if (!isVisible() || !isEnabled()) {
            return false;
        }
        if (menus.picker.isOpen()) {
            menus.picker.mouseDragged(mouseY, button);
            return true;
        }
        return gestures.mouseDragged(context);
    }

    @Override
    public boolean mouseReleased(UiInputContext context) {
        int button = context.button();
        if (menus.picker.isOpen()) {
            menus.picker.mouseReleased(button);
            return true;
        }
        lastOpKey = EditSession.OP_MOVE_NODE;
        return gestures.mouseReleased(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean mouseScrolled(UiInputContext context, double scrollX, double scrollY) {
        double mouseX = context.pointerX();
        double mouseY = context.pointerY();
        if (!isVisible() || !isEnabled() || !bounds().contains(mouseX, mouseY) || scrollY == 0.0D) {
            return false;
        }
        if (menus.picker.isOpen()) {
            menus.picker.mouseScrolled(mouseX, mouseY, scrollY);
            return true;
        }
        if (menus.kindMenuOpen) {
            menus.scroll(scrollY);
            return true;
        }
        if (inlinePanel.contains(mouseX, mouseY)) {
            inlinePanel.mouseScrolled(context, scrollX, scrollY);
            return true;
        }
        // 滚轮以鼠标位置为锚点逐档缩放
        transform.zoomBy(scrollY > 0.0D ? 1 : -1, mouseX, mouseY);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isVisible() || !isEnabled()) {
            return false;
        }
        if (menus.picker.isOpen()) {
            menus.picker.keyPressed(keyCode);
            return true;
        }
        if (menus.kindMenuOpen) {
            menus.keyPressed(keyCode);
            return true;
        }
        if (gestures.isMoving()) {
            if (UiHistoryShortcut.fromKey(keyCode, modifiers) != null || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                endInteractions(UiInteractionSession.EndReason.CANCEL);
                return true;
            }
            return true;
        }
        // 内联框优先拿键盘：文本框聚焦时画布快捷键必须让位
        if (inlinePanel.keyPressed(keyCode, scanCode, modifiers)) return true;
        // 文本框没消费的按键（Ctrl+D / Ctrl+0 / Ctrl+± 等）仍属于输入内容，
        // 不能落到画布命令上，否则输入参数时会复制节点或改动视图
        if (inlinePanel.capturesKeyboard()) return false;
        if (!isFocused()) return false;
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0
                && (modifiers & (GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) == 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean alt = (modifiers & GLFW.GLFW_MOD_ALT) != 0;
        if (control) {
            if (shift && keyCode == GLFW.GLFW_KEY_UP) return gestures.extendSelection(-1);
            if (shift && keyCode == GLFW.GLFW_KEY_DOWN) return gestures.extendSelection(1);
            if (keyCode == GLFW.GLFW_KEY_A) return selectAll();
            if (keyCode == GLFW.GLFW_KEY_C) return copySelection();
            if (keyCode == GLFW.GLFW_KEY_V) return pasteSelection();
            if (keyCode == GLFW.GLFW_KEY_D) return duplicateSelection();
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
        if (alt && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
            moveSelection(keyCode == GLFW.GLFW_KEY_UP ? -1 : 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            gestures.pressSpace();
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
        if (keyCode == GLFW.GLFW_KEY_HOME) return gestures.selectSiblingEdge(false);
        if (keyCode == GLFW.GLFW_KEY_END) return gestures.selectSiblingEdge(true);
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN
                || keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
            return gestures.navigate(keyCode);
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
            gestures.releaseSpace();
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!isVisible() || !isEnabled()) {
            return false;
        }
        if (menus.picker.isOpen()) {
            return menus.picker.charTyped(codePoint);
        }
        if (menus.kindMenuOpen) {
            // 弹层只接受方向键与回车，不接受文本输入
            return false;
        }
        return inlinePanel.charTyped(codePoint, modifiers);
    }

    // ---- 类型选择确认与树写入 ----

    private List<UiTypePicker.Option> pickerOptions() {
        return typeOptions.stream().map(option -> new UiTypePicker.Option(option.id(), option.label())).toList();
    }

    private void openPicker(Hotspot hotspot) {
        menus.openPicker(hotspot, pickerOptions(), bounds());
    }

    /**
     * 取消类型选择，不提交条件。
     */
    public void closePicker() {
        menus.closePicker();
    }

    private void confirmPicker(ResourceLocation type) {
        UiTreePath parent = menus.pickerParent;
        int index = menus.pickerIndex;
        boolean append = menus.pickerAppend;
        closePicker();
        ConditionTreeContract.LeafFactory factory = leafFactory;
        if (factory == null) return;
        ConditionNode leaf = factory.create(type);
        if (leaf == null) return;
        lastOpKey = EditSession.OP_ADD_CONDITION;
        boolean inserted = append ? addNode(leaf) : insertNode(parent, index, leaf);
        if (inserted) {
            gestures.revealNode(leaf);
            // 新条件尚无参数，直接打开参数框，省去再点一次叶卡片
            editSelectedLeaf();
        }
    }

    // ---- 树变换 ----

    private void applyNode(@Nullable ConditionNode newRoot) {
        this.expression = new ConditionExpression(newRoot);
        viewChanged();
        rebuild();
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
    static @Nullable List<ConditionNode> termsOf(@Nullable ConditionNode node) {
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
        return ConditionTreeNodes.INSTANCE.withChildren(node, copy);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean activate() {
        if (!isEnabled()) {
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
        return Component.translatable(TypeLabels.UI_PREFIX + "tree.accessible_name");
    }

    /**
     * 根据实际子控件解析当前命中。
     */
    @Override
    public @Nullable UiPointerHit hitTest(double x, double y) {
        if (!isVisible() || !bounds().contains(x, y)) return null;
        if (menus.picker.isOpen()) {
            UiPointerHit hit = menus.picker.hitTest(x, y);
            return hit == null ? UiPointerHit.leaf(this, x, y).preservingFocus() : hit.within(this);
        }
        if (menus.kindMenuOpen) {
            return UiPointerHit.leaf(this, x, y).preservingFocus();
        }
        UiPointerHit hit = inlinePanel.hitTest(x, y);
        if (hit != null) return hit.within(this);
        UiPointerHit leaf = UiPointerHit.leaf(this, x, y);
        if (isDragging() || inlinePanel.contains(x, y)) return leaf;
        ConditionNode node = graph.nodeAt(transform.toLocalX(x), transform.toLocalY(y));
        return node == null ? leaf : leaf.withPresentation((graphics, ignoredFont, mx, my) ->
                tooltip.render(graphics, bounds(), nodeTooltip(node), mx, my));
    }
}
