package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.edit.ConditionTreeNodes;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.OpaqueCondition;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiDotGrid;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiHistoryShortcut;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiOrthoLink;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRubberBand;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTidyTreeLayout;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTransform;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreeEditor;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreeMoveInteraction;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreePath;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 条件树画布控件：整页画布式的条件树编辑器，直接编辑 {@link ConditionExpression} / {@link ConditionNode}。
 *
 * <p>与 {@link UiConditionTreeSummary} 的关系：结构操作与选择语义全部复用 {@link UiTreeEditor} +
 * {@link ConditionTreeNodes}，宿主注入面与操作面沿用同名方法；区别只在渲染与输入层——节点由
 * {@link UiTidyTreeLayout} 自动布局成自上而下的节点图，画布由 {@link UiTransform} 承载平移与
 * 50 / 100 / 150 / 200 四档缩放，不做折叠与展开。</p>
 *
 * <p>与 {@link UiConditionTreeSummary}（缩进摘要，只读）的关系：两者共用同一份数据与校验，
 * 渲染模式只决定展示形态。宿主注入类型与路径常量由 {@link ConditionTreeContract} 承接，两者都实现
 * {@link ConditionTreeContract.Navigation}，宿主可用同一组语义在两种渲染之间迁移选中项；
 * 限额判定、节点状态与违规项来自 {@link ConditionTreeInspection}，不在此重复实现。

 * <p>本控件是条件树唯一的编辑入口：结构操作、参数内联编辑与类型选择都在这里完成。</p>
 *
 * <ul>
 *   <li>鼠标：单击选中、Ctrl 点击增删、Shift 点击树序范围、空白左键拖拽平移、Shift+空白拖拽框选、
 *       中键或空格+左键拖拽平移、拖动节点移动、滚轮逐档缩放。</li>
 *   <li>键盘：方向键移动选择、Delete 删除、Ctrl+A 全选、Ctrl+C/V 复制粘贴、空格拖拽平移、
 *       Ctrl+0 适配视图、Ctrl+Home 回到根、Esc 取消当前手势或收起内联编辑框。</li>
 * </ul>
 *
 * <p>控件不持有正式保存入口，候选修改通过绑定回调交给宿主；参数编辑既可以交给表单层
 * （{@link #setOnEditLeaf(Consumer)}），也可以由宿主装载 {@link InlineEditor} 在锚点卡片下方内联展开，
 * 见 {@link #setInlineEditor(InlineEditor, UiTreePath)}。分组与包组的「全部满足 / 任一满足」由画布内的
 * 小菜单选择，外壳只需调用 {@link #beginAddGroupWithKind()} 与 {@link #beginWrapWithKind()}。</p>
 */
public final class UiConditionGraphEditor implements UiWidget, UiFocusTarget, ConditionTreeContract.Navigation {

    // ---- 画布 ----

    // 点阵网格在 100% 档位下的步长；其它档位由 kit 按 scale 换算
    private static final int DOT_BASE_STEP = 16;
    // 适配视图时内容四周保留的屏幕像素
    private static final int FIT_PADDING = 12;
    // 回到根时根卡片顶边距视口顶部的屏幕像素
    private static final int RESET_TOP_MARGIN = 16;
    // 键盘导航后把目标卡片带回视口时保留的屏幕像素
    private static final int REVEAL_MARGIN = 24;
    // 框选填充色（半透明强调色）

    // ---- 节点卡片 ----

    // 卡片最小 / 最大宽度（内容单位）
    private static final int CARD_MIN_WIDTH = 60;
    private static final int CARD_MAX_WIDTH = 168;
    // 卡片左右内边距与上下内边距
    private static final int CARD_PADDING_X = 4;
    private static final int CARD_PADDING_Y = 2;
    // 卡片单行高度：两行 = 第一行类型名与状态标记，第二行参数摘要
    private static final int CARD_ROW_HEIGHT = 10;
    // 卡片高度
    private static final int CARD_HEIGHT = CARD_PADDING_Y * 2 + CARD_ROW_HEIGHT * 2;
    // 扁平风格统一使用的 1 像素描边宽度
    private static final int BORDER = 1;
    // 层间距：卡片高度 + 连线垂直空间
    private static final int LEVEL_PITCH = CARD_HEIGHT + 18;
    // 同层相邻卡片的最小水平间距
    private static final int SIBLING_GAP = 12;
    // 正交连线粗细
    private static final int LINK_THICKNESS = 1;

    // ---- [+] 热区 ----

    // 非空容器子项末尾的小热区边长
    private static final int HOTSPOT_SIZE = 12;
    // 空容器与空树的大热区边长
    private static final int HOTSPOT_LARGE_SIZE = 22;

    // ---- 拖拽与框选 ----

    // 判定拖拽开始的屏幕像素距离
    private static final int DRAG_START_DISTANCE = 4;
    // 卡片上下边缘用于判定 BEFORE / AFTER 的比例分母
    private static final int DROP_EDGE_DIVISOR = 4;
    // 落点标记厚度
    private static final int DROP_MARKER_THICKNESS = 2;
    // 拖拽幽灵距指针的像素
    private static final int DRAG_GHOST_GAP = 6;
    // 双击判定间隔（毫秒）
    private static final long DOUBLE_CLICK_MS = 300L;

    // ---- 类型选择框 ----

    private static final int PICKER_MAX_WIDTH = 220;
    private static final int PICKER_MARGIN = 8;
    private static final int PICKER_HEADER = 14;
    private static final int PICKER_SEARCH_ROW = 14;
    private static final int PICKER_ROW_HEIGHT = 12;
    private static final int PICKER_MAX_ROWS = 8;
    private static final int PICKER_FOOTER = 12;
    // 筛选文本长度上限
    private static final int PICKER_FILTER_MAX = 32;

    // ---- 内联参数编辑框 ----

    // 内联框最小宽度（内容单位）
    private static final int INLINE_MIN_WIDTH = 140;
    // 内联框与锚点卡片之间的内容像素间距
    private static final int INLINE_GAP = 6;
    // 内联框展开 / 收起的动画时长（毫秒）
    private static final long INLINE_ANIM_MS = 160L;

    // ---- 空树中央大热区 ----

    // 空树提示文字与大热区之间的屏幕像素间距
    private static final int EMPTY_HINT_GAP = 4;

    // ---- 分组方式小菜单 ----

    // 菜单与锚点之间的屏幕像素间距
    private static final int KIND_MENU_GAP = 4;
    // 菜单行文本左右内边距
    private static final int KIND_MENU_PADDING = 4;
    // 菜单最小宽度
    private static final int KIND_MENU_MIN_WIDTH = 56;
    // 键盘操作提示：与类型选择框共用一套措辞
    private static final String KIND_MENU_HINT_KEY = "gui.itemdespawntowhat.edit.picker.hint";

    // 布局用包装节点：节点身份即对象身份，不依赖 ConditionNode 的值相等。
    // children 使用包内可见：外层控件负责装配子列表，而本类自身是 private 的。
    private abstract static class GraphNode {
        List<GraphNode> children = List.of();
    }

    // 真实条件节点
    private static final class NodeSlot extends GraphNode {
        private final ConditionNode node;

        NodeSlot(ConditionNode node) {
            this.node = node;
        }
    }

    // 容器末尾为 [+] 预留的槽位
    private static final class HotspotSlot extends GraphNode {
        private final ConditionNode container;

        HotspotSlot(ConditionNode container) {
            this.container = container;
        }
    }

    // 布局数据源：卡片宽度由文本测量得到，[+] 槽位按固定边长占位
    private record LayoutSource(UiConditionGraphEditor editor) implements UiTidyTreeLayout.Source<GraphNode> {
        @Override
        public List<GraphNode> children(GraphNode node) {
            return node.children;
        }

        @Override
        public int width(GraphNode node) {
            return node instanceof HotspotSlot ? HOTSPOT_SIZE : editor.cardWidth(((NodeSlot) node).node);
        }
    }

    // 单个可添加位置：parent 为 null 表示空树（作为新根写入）
    private record Hotspot(@Nullable UiTreePath parent, int index, UiRect rect) {}

    /**
     * 内联参数编辑框的内容契约：几何、绘制与输入坐标一律为**内容坐标系**（与卡片同一坐标系）。
     *
     * <p>画布负责把屏幕坐标换算成内容坐标后再转发，并按 160ms ease-out 动画只改变高度与透明度；
     * 宿主只需实现表单本体。</p>
     */
    public interface InlineEditor {
        int preferredHeight(int width);
        UiRect bounds();                       // 内容坐标系
        void setBounds(int x, int y, int width, int height);
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseY);   // 内容坐标系
        boolean mouseClicked(double x, double y, int button);
        boolean mouseDragged(double x, double y, int button, double dragX, double dragY);
        boolean mouseReleased(double x, double y, int button);
        boolean mouseScrolled(double x, double y, double scrollY);
        boolean keyPressed(int keyCode, int scanCode, int modifiers);
        boolean charTyped(char codePoint, int modifiers);
        boolean isVisible();
    }

    // 分组方式：全部满足 / 任一满足
    private enum GroupKind {
        ALL("gui.itemdespawntowhat.edit.tree.node.all", true),
        ANY("gui.itemdespawntowhat.edit.tree.node.any", false);

        private final String labelKey;
        private final boolean allOf;

        GroupKind(String labelKey, boolean allOf) {
            this.labelKey = labelKey;
            this.allOf = allOf;
        }

        Component label() {
            return Component.translatable(labelKey);
        }
    }

    // 「分组 / 包组」小菜单的确认目标
    private enum KindMenuTarget { INSERT_GROUP, WRAP_SELECTION }

    // 全树扫描得到的单节点状态，供卡片配色与状态标记使用
    private record NodeInfo(UiTreePath path, boolean incomplete, boolean overflow, boolean deep,
                            boolean leafMissing, boolean subtreeIssue) {}

    private final Font font;
    private final UiTransform transform = UiTransform.ofScales(0.5, 1.0, 1.5, 2.0);
    private final UiTidyTreeLayout<GraphNode> layout = new UiTidyTreeLayout<>(LEVEL_PITCH, SIBLING_GAP);
    private final LayoutSource layoutSource = new LayoutSource(this);
    private final UiRubberBand rubberBand = new UiRubberBand();
    private final UiTreeEditor<ConditionNode> tree;
    private final UiTreeMoveInteraction<ConditionNode> move;

    private ConditionExpression expression = ConditionExpression.EMPTY;
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private boolean visible = true;
    private boolean enabled = true;
    private boolean focused;
    private boolean addMode;
    private boolean viewInitialized;
    private boolean centerEmptyView;
    private @Nullable ConditionNode laidOutRoot;

    // 每轮重建得到的布局数据；键全部按对象身份匹配
    private final List<NodeSlot> graphNodes = new ArrayList<>();
    private final List<HotspotSlot> hotspotSlots = new ArrayList<>();
    private final List<Hotspot> hotspots = new ArrayList<>();
    private final Map<ConditionNode, GraphNode> wrappers = new IdentityHashMap<>();
    private final Map<ConditionNode, UiTreePath> paths = new IdentityHashMap<>();
    // 路径 → 节点反查：内联框锚点与菜单定位使用
    private final Map<UiTreePath, ConditionNode> nodesByPath = new HashMap<>();
    private final Map<ConditionNode, NodeInfo> infos = new IdentityHashMap<>();
    private final Map<ConditionNode, Component> leafSummaries = new IdentityHashMap<>();
    private final Map<ConditionNode, Integer> cardWidths = new IdentityHashMap<>();
    // 树序（深度优先前序），Shift 范围选择与键盘导航使用
    private final List<ConditionNode> order = new ArrayList<>();
    private final List<ConditionTreeInspection.Issue> issues = new ArrayList<>();

    private @Nullable Function<ConditionNode.Leaf, Component> leafSummary;
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
    private @Nullable UiTreePath lastClickPath;
    private long lastClickTime;
    private @Nullable UiTreePath selectionAnchor;
    private @Nullable ConditionNode hoveredNode;
    private @Nullable Hotspot hoveredHotspot;

    // 类型选择框状态
    private boolean pickerOpen;
    private @Nullable UiTreePath pickerParent;
    private int pickerIndex;
    private int pickerCursor;
    private int pickerOffset;
    private final StringBuilder pickerFilter = new StringBuilder();
    private UiRect pickerRect = new UiRect(0, 0, 0, 0);
    private UiRect pickerSearchRect = new UiRect(0, 0, 0, 0);
    private UiRect pickerListRect = new UiRect(0, 0, 0, 0);
    private int pickerVisibleRows = 1;
    private List<ConditionTreeContract.TypeOption> filteredOptions = List.of();

    // 内联参数编辑框状态；几何每次都从锚点卡片重新推导
    private @Nullable InlineEditor inlineEditor;
    private @Nullable UiTreePath inlineAnchorPath;
    private boolean inlineClosing;
    private boolean inlineCaptured;
    private float inlineOpenness;
    private float inlineAnimFrom;
    private float inlineAnimTarget;
    private long inlineAnimStart;

    // 分组 / 包组的「全部满足 / 任一满足」小菜单
    private boolean groupKindPending;
    private boolean kindMenuOpen;
    private KindMenuTarget kindMenuTarget = KindMenuTarget.INSERT_GROUP;
    private int kindMenuIndex;
    private @Nullable Hotspot kindMenuHotspot;
    private UiRect kindMenuRect = new UiRect(0, 0, 0, 0);
    private UiRect kindMenuListRect = new UiRect(0, 0, 0, 0);

    // 注入的宿主主题
    private final UiHostTheme theme;
    public UiConditionGraphEditor(UiHostTheme theme, Font font) {
        this.theme = UiHostThemes.require(theme);
        this.font = font;
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
        collapseSelectionOnRelease = false;
        dragLabel = null;
        dropTarget = null;
        rubberActive = false;
        rubberBand.clear();
        clearPanState();
        inlineCaptured = false;
        move.end(reason);
        if (reason != UiInputCapture.EndReason.RELEASE) {
            addMode = false;
            groupKindPending = false;
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

    /** 违规项明细（只读）。 */
    public List<ConditionTreeInspection.Issue> issues() {
        return List.copyOf(issues);
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
        ConditionNode target = nodeForJsonPath(path);
        if (target == null) {
            return;
        }
        UiTreePath treePath = paths.get(target);
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
        revealNode(target);
    }

    // ---- 结构操作（工具栏与键盘共用） ----

    /** 进入添加条件模式：画布显示所有 [+] 热区，点击热区弹出可搜索类型列表。 */
    public boolean beginAddCondition() {
        if (!enabled) {
            return false;
        }
        addMode = true;
        groupKindPending = false;
        closePicker();
        closeKindMenu();
        return true;
    }

    public boolean isAddMode() {
        return addMode;
    }

    /** 添加模式下是否在等用户为「分组」选 ∧ / ∨；为真时「添加条件」格不应显示为选中。 */
    public boolean isGroupKindPending() {
        return groupKindPending;
    }

    /** 是否正在等用户为「包组」选 ∧ / ∨。 */
    public boolean isWrapKindPending() {
        return kindMenuOpen && kindMenuTarget == KindMenuTarget.WRAP_SELECTION;
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
     * 进入「加分组」待选 kind 的添加模式：热区照常出现，点击某个 [+] 后由画布在该热区旁弹出
     * 「全部满足 / 任一满足」小菜单；选定后插入对应容器并保持添加模式，用户可以继续点新容器的大 [+]。
     */
    public boolean beginAddGroupWithKind() {
        if (!enabled) {
            return false;
        }
        endInteractions(UiInputCapture.EndReason.CANCEL);
        addMode = true;
        groupKindPending = true;
        closePicker();
        closeKindMenu();
        return true;
    }

    /** 对当前选中的同父连续节点弹出「全部满足 / 任一满足」包组菜单；无选中时返回 false。 */
    public boolean beginWrapWithKind() {
        if (!enabled || tree.selection().isEmpty()) {
            return false;
        }
        endInteractions(UiInputCapture.EndReason.CANCEL);
        closePicker();
        kindMenuTarget = KindMenuTarget.WRAP_SELECTION;
        kindMenuHotspot = null;
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

    /** 删除选中的最高层子树（根被删除后表达式变为空）。 */
    public boolean removeSelection() {
        if (!enabled || tree.selection().isEmpty()) {
            return false;
        }
        List<UiTreePath> selected = tree.normalizedSelection();
        UiTreePath parent = selected.isEmpty() || selected.getFirst().indices().isEmpty()
                ? null : selected.getFirst().parent();
        lastOpKey = EditSession.OP_DELETE_NODE;
        boolean changed = accept(tree.deleteMany(selected));
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
        if (order.isEmpty()) {
            return false;
        }
        List<UiTreePath> all = order.stream().map(paths::get).filter(java.util.Objects::nonNull).toList();
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
        ConditionNode root = expression.root();
        if (root == null) {
            transform.centerOn(0, 0);
            return;
        }
        GraphNode wrapper = wrappers.get(root);
        if (wrapper == null) {
            return;
        }
        UiRect rootRect = cardRect(wrapper);
        transform.centerOn(rootRect.x() + rootRect.width() / 2.0, 0);
        transform.panBy(0, RESET_TOP_MARGIN - transform.viewport().height() / 2.0);
    }

    /** 选择能容纳整棵树的档位并居中显示。 */
    public void fitToContent() {
        if (expression.root() == null) {
            resetView();
            return;
        }
        // contentHeight 是「层数 × 层高」：最后一层只用到卡片高度，按卡片底边取实际内容高度
        int contentHeight = layout.contentHeight() - LEVEL_PITCH + CARD_HEIGHT;
        transform.fit(0, 0, layout.contentWidth(), contentHeight, FIT_PADDING);
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
            addMode = false;
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
        graphNodes.clear();
        hotspotSlots.clear();
        hotspots.clear();
        wrappers.clear();
        paths.clear();
        nodesByPath.clear();
        infos.clear();
        leafSummaries.clear();
        cardWidths.clear();
        order.clear();
        issues.clear();
        ConditionNode previousRoot = laidOutRoot;
        ConditionNode root = expression.root();
        laidOutRoot = root;
        if (root == null) {
            layout.clear();
        } else {
            inspect(root, UiTreePath.ROOT, 1, new int[2]);
            NodeSlot wrapperRoot = wrap(root, UiTreePath.ROOT);
            layout.layout(wrapperRoot, layoutSource);
        }
        collectHotspots();
        tree.setSelection(tree.selection(), tree.selected());
        followPrimarySelection();
        validateInlineAnchor();
        if (root == null && (previousRoot != null || !viewInitialized)) {
            // 视口尺寸在下一帧渲染时才可用，届时再把空树大热区对准视口中心
            centerEmptyView = true;
        }
        viewInitialized = true;
    }

    // 深度优先扫描：违规项、树序、路径、参数摘要与状态标记一次算完。
    private boolean inspect(ConditionNode node, UiTreePath path, int depth, int[] counters) {
        counters[0]++;
        int nodeOrdinal = counters[0];
        int leafOrdinal = -1;
        if (node instanceof ConditionNode.Leaf) {
            counters[1]++;
            leafOrdinal = counters[1];
        }
        paths.put(node, path);
        nodesByPath.put(path, node);
        order.add(node);
        if (node instanceof ConditionNode.Leaf leaf && leaf.condition() != null && leafSummary != null) {
            leafSummaries.put(node, leafSummary.apply(leaf));
        }
        boolean incomplete = ConditionTreeInspection.isIncomplete(node);
        boolean overflow = ConditionTreeInspection.isOverflow(depth, nodeOrdinal, leafOrdinal);
        boolean deep = ConditionTreeInspection.isDeep(depth);
        boolean leafMissing = ConditionTreeInspection.isLeafMissing(node);
        // 违规项由共用校验生成：缩进摘要与画布只有一份判定与一套文案
        String jsonPath = ConditionTreeNodes.jsonPath(expression.root(), path, ConditionTreeContract.ROOT_PATH);
        issues.addAll(ConditionTreeInspection.nodeIssues(node, jsonPath, depth, nodeOrdinal, leafOrdinal));
        boolean subtreeIssue = incomplete || leafMissing || overflow;
        List<ConditionNode> children = ConditionTreeNodes.INSTANCE.children(node);
        for (int index = 0; index < children.size(); index++) {
            subtreeIssue |= inspect(children.get(index), path.child(index), depth + 1, counters);
        }
        infos.put(node, new NodeInfo(path, incomplete, overflow, deep, leafMissing, subtreeIssue));
        return subtreeIssue;
    }

    // 建立布局包装节点；容器在有子项时额外预留一个 [+] 槽位，保证热区不遮挡子树。
    private NodeSlot wrap(ConditionNode node, UiTreePath path) {
        NodeSlot wrapper = new NodeSlot(node);
        wrappers.put(node, wrapper);
        graphNodes.add(wrapper);
        List<ConditionNode> children = ConditionTreeNodes.INSTANCE.children(node);
        List<GraphNode> wrapped = new ArrayList<>(children.size() + 1);
        for (int index = 0; index < children.size(); index++) {
            wrapped.add(wrap(children.get(index), path.child(index)));
        }
        if (isContainer(node) && !children.isEmpty()
                && ConditionTreeNodes.INSTANCE.childCapacity(node) > children.size()) {
            HotspotSlot hotspot = new HotspotSlot(node);
            hotspotSlots.add(hotspot);
            wrapped.add(hotspot);
        }
        wrapper.children = List.copyOf(wrapped);
        return wrapper;
    }

    private void collectHotspots() {
        for (HotspotSlot slot : hotspotSlots) {
            ConditionNode container = slot.container;
            UiTreePath parent = paths.get(container);
            if (parent == null) {
                continue;
            }
            hotspots.add(new Hotspot(parent, ConditionTreeNodes.INSTANCE.children(container).size(), cardRect(slot)));
        }
        for (NodeSlot wrapper : graphNodes) {
            ConditionNode node = wrapper.node;
            if (!isContainer(node) || !ConditionTreeNodes.INSTANCE.children(node).isEmpty()) {
                continue;
            }
            UiTreePath parent = paths.get(node);
            if (parent == null) {
                continue;
            }
            hotspots.add(new Hotspot(parent, 0, centered(cardRect(wrapper))));
        }
        if (expression.root() == null) {
            hotspots.add(new Hotspot(null, 0, new UiRect(
                    -HOTSPOT_LARGE_SIZE / 2, -HOTSPOT_LARGE_SIZE / 2,
                    HOTSPOT_LARGE_SIZE, HOTSPOT_LARGE_SIZE)));
        }
    }

    private static UiRect centered(UiRect card) {
        return new UiRect(card.x() + (card.width() - HOTSPOT_LARGE_SIZE) / 2,
                card.y() + (card.height() - HOTSPOT_LARGE_SIZE) / 2, HOTSPOT_LARGE_SIZE, HOTSPOT_LARGE_SIZE);
    }

    private int cardWidth(ConditionNode node) {
        Integer cached = cardWidths.get(node);
        if (cached != null) {
            return cached;
        }
        int measured = Math.max(font.width(row1Label(node).getString()), font.width(row2Label(node).getString()))
                + (CARD_PADDING_X + BORDER) * 2;
        int width = Math.clamp(measured, CARD_MIN_WIDTH, CARD_MAX_WIDTH);
        cardWidths.put(node, width);
        return width;
    }

    private UiRect cardRect(GraphNode wrapper) {
        return new UiRect(layout.x(wrapper), layout.y(wrapper), layout.widthOf(wrapper), CARD_HEIGHT);
    }

    // ---- 文案 ----

    // 第一行：类型名 + 状态标记
    private Component row1Label(ConditionNode node) {
        Component label = switch (node) {
            case ConditionNode.AllOf ignored -> Component.translatable("gui.itemdespawntowhat.edit.tree.node.all");
            case ConditionNode.AnyOf ignored -> Component.translatable("gui.itemdespawntowhat.edit.tree.node.any");
            case ConditionNode.Inverted ignored -> Component.translatable("gui.itemdespawntowhat.edit.tree.node.not");
            case ConditionNode.Leaf(var condition) -> condition == null
                    ? Component.translatable("gui.itemdespawntowhat.edit.tree.leaf_missing")
                    : typeLabel(condition.type());
        };
        if (node instanceof ConditionNode.Leaf(var condition) && condition instanceof OpaqueCondition) {
            label = label.copy().append(" ").append(Component.translatable(
                    "gui.itemdespawntowhat.edit.tree.parameters_readonly"));
        }
        NodeInfo info = infos.get(node);
        if (info == null) {
            return label;
        }
        if (info.incomplete()) {
            label = label.copy().append(" ").append(Component.translatable("gui.itemdespawntowhat.edit.tree.incomplete"));
        }
        if (info.overflow()) {
            label = label.copy().append(" ").append(Component.translatable("gui.itemdespawntowhat.edit.tree.overflow_mark"));
        } else if (info.deep()) {
            // 超限时已有超限标记，避免同一行堆叠两个标记
            label = label.copy().append(" ").append(Component.translatable("gui.itemdespawntowhat.edit.tree.deep_mark"));
        }
        return label;
    }

    // 第二行：参数摘要；组合节点显示分支数量
    private Component row2Label(ConditionNode node) {
        if (node instanceof ConditionNode.Leaf(var condition)) {
            if (condition == null) {
                return Component.translatable("gui.itemdespawntowhat.edit.tree.leaf_missing");
            }
            return leafSummaries.getOrDefault(node, typeLabel(condition.type()));
        }
        List<ConditionNode> children = ConditionTreeNodes.INSTANCE.children(node);
        if (children.isEmpty()) {
            return Component.translatable("gui.itemdespawntowhat.edit.tree.empty");
        }
        return Component.translatable("gui.itemdespawntowhat.edit.tree.drag_many", children.size());
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

    // 悬停提示：完整第一行 + 第二行
    public @Nullable Component tooltipAt(double mouseX, double mouseY) {
        if (!visible || isDragging() || pickerOpen || kindMenuOpen || addMode || !bounds.contains(mouseX, mouseY)) {
            return null;
        }
        // 内联框自带提示，避免画布卡片提示压住表单
        if (inlineEditorHit(mouseX, mouseY)) {
            return null;
        }
        ConditionNode node = nodeAt(transform.toLocalX(mouseX), transform.toLocalY(mouseY));
        if (node == null) {
            return null;
        }
        return row1Label(node).copy().append(" · ").append(row2Label(node));
    }

    /** 当前手势或默认快捷键提示，供宿主底部状态栏展示。 */
    public Component hint() {
        if (move.isActive()) {
            UiTreeEditor.MovePreview<ConditionNode> preview = move.preview();
            if (preview != null && preview.outcome().error() == null && dropTarget != null) {
                return Component.translatable("gui.itemdespawntowhat.edit.tree.drop_"
                        + preview.position().name().toLowerCase(Locale.ROOT), row1Label(dropTarget));
            }
            if (operationError != null) {
                return operationError;
            }
        }
        if (pickerOpen) {
            return Component.translatable("gui.itemdespawntowhat.edit.picker.hint");
        }
        if (addMode) {
            return Component.translatable("gui.itemdespawntowhat.edit.tree.add_placeholder");
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
            transform.centerOn(0, 0);
        }
        UiRect viewport = transform.viewport();
        boolean inside = bounds.contains(mouseX, mouseY);
        boolean emptyTree = expression.root() == null;
        double localX = transform.toLocalX(mouseX);
        double localY = transform.toLocalY(mouseY);
        hoveredNode = !pickerOpen && !kindMenuOpen && inside ? nodeAt(localX, localY) : null;
        // 空树空闲态也要亮出中央大 [+]，让人一眼看出这里可以点
        hoveredHotspot = (addMode || emptyTree) && !pickerOpen && !kindMenuOpen && inside
                ? hotspotAt(localX, localY) : null;

        drawCanvasBackground(graphics);
        transform.enableScissor(graphics);
        drawDotGrid(graphics, viewport);
        transform.push(graphics);
        try {
            drawLinks(graphics);
            drawCards(graphics, renderFont);
            if (addMode || emptyTree) {
                drawHotspots(graphics, renderFont);
            }
            if (rubberActive) {
                rubberBand.draw(graphics, theme.palette().canvasRubberFill(), theme.palette().canvasAccent());
            }
            if (move.isActive()) {
                drawDropMarker(graphics);
            }
            // 内联参数编辑框画在卡片与连线之上、拖拽浮影之下
            renderInlineEditor(graphics, renderFont, mouseX, mouseY);
        } finally {
            graphics.pose().popPose();
            UiTransform.disableScissor(graphics);
        }
        if (emptyTree && !addMode) {
            drawEmptyMessage(graphics, renderFont, viewport);
        }
        if (focused && !pickerOpen && !kindMenuOpen) {
            theme.drawFocusOutline(graphics, bounds);
        }
        if (move.isActive()) {
            drawDragGhost(graphics, renderFont, mouseX, mouseY);
        }
        if (pickerOpen) {
            updatePickerRects();
            renderPicker(graphics, renderFont);
        }
        if (kindMenuOpen) {
            updateKindMenuRect();
            renderKindMenu(graphics, renderFont);
        }
    }

    /*
     * 点阵网格锚定内容原点：把内容 (0,0) 的屏幕位置按步长取模，得到视口内第一列 / 第一行点阵，
     * 因此平移画布时网格跟着内容一起走；步长仍随缩放档位变化。
     * 步长换算与 kit 的 UiDotGrid 保持一致（屏幕步长 = 基准步长 × 缩放，至少 1 像素）。
     */
    private void drawDotGrid(GuiGraphics graphics, UiRect viewport) {
        int step = Math.max(1, (int) Math.round(DOT_BASE_STEP * transform.scale()));
        int gridX = alignToGrid(transform.toScreenX(0), viewport.x(), step);
        int gridY = alignToGrid(transform.toScreenY(0), viewport.y(), step);
        UiDotGrid.draw(graphics, transform.scale(), gridX, gridY, viewport.right(), viewport.bottom(),
                DOT_BASE_STEP, theme.palette().canvasGridDot());
    }

    // 把内容原点在屏幕上的位置对齐到 [start, start + step) 内的第一个点阵坐标
    private static int alignToGrid(double originScreen, int start, int step) {
        long offset = Math.floorMod((long) Math.floor(originScreen) - start, (long) step);
        return start + (int) offset;
    }

    // 扁平画布底：纯色填充 + 1px 描边，不使用立体凹凸
    private void drawCanvasBackground(GuiGraphics graphics) {
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), theme.palette().canvasFill());
        drawBorder(graphics, bounds, theme.palette().canvasBorder());
    }

    // 空树提示：紧贴中央大 [+] 的上方，保证「这里能点」一眼可见
    private void drawEmptyMessage(GuiGraphics graphics, Font renderFont, UiRect viewport) {
        String text = TextScroll.trimToWidth(renderFont, emptyMessage.getString(), Math.max(0, viewport.width() - 8));
        int textWidth = renderFont.width(text);
        int centerX = viewport.x() + viewport.width() / 2;
        int textY = viewport.y() + viewport.height() / 2 - renderFont.lineHeight - EMPTY_HINT_GAP;
        if (!hotspots.isEmpty()) {
            UiRect hotspot = hotspots.getFirst().rect();
            centerX = (int) Math.round(transform.toScreenX(hotspot.x() + hotspot.width() / 2.0));
            textY = (int) Math.round(transform.toScreenY(hotspot.y())) - renderFont.lineHeight - EMPTY_HINT_GAP;
        }
        int textX = Math.clamp(centerX - textWidth / 2, viewport.x() + EMPTY_HINT_GAP,
                Math.max(viewport.x() + EMPTY_HINT_GAP, viewport.right() - textWidth - EMPTY_HINT_GAP));
        int clampedY = Math.clamp(textY, viewport.y() + EMPTY_HINT_GAP,
                Math.max(viewport.y() + EMPTY_HINT_GAP, viewport.bottom() - renderFont.lineHeight - EMPTY_HINT_GAP));
        graphics.drawString(renderFont, text, textX, clampedY, theme.palette().canvasHintText(), false);
    }

    private void drawLinks(GuiGraphics graphics) {
        for (NodeSlot wrapper : graphNodes) {
            ConditionNode node = wrapper.node;
            List<ConditionNode> children = ConditionTreeNodes.INSTANCE.children(node);
            if (children.isEmpty()) {
                continue;
            }
            UiRect parentRect = cardRect(wrapper);
            for (ConditionNode child : children) {
                GraphNode childWrapper = wrappers.get(child);
                if (childWrapper == null) {
                    continue;
                }
                UiRect childRect = cardRect(childWrapper);
                int color = tree.selection().contains(paths.get(node)) || tree.selection().contains(paths.get(child))
                        ? theme.palette().canvasLinkSelected() : theme.palette().canvasLink();
                UiOrthoLink.drawDown(graphics, parentRect.x() + parentRect.width() / 2, parentRect.bottom(),
                        childRect.x() + childRect.width() / 2, childRect.y(), LINK_THICKNESS, color);
            }
        }
    }

    private void drawCards(GuiGraphics graphics, Font renderFont) {
        for (NodeSlot wrapper : graphNodes) {
            ConditionNode node = wrapper.node;
            UiRect rect = cardRect(wrapper);
            if (rect.right() < 0 || rect.bottom() < 0) {
                continue;
            }
            boolean selected = tree.selection().contains(paths.get(node));
            drawCard(graphics, renderFont, node, rect, selected, node == hoveredNode);
        }
    }

    private void drawCard(GuiGraphics graphics, Font renderFont, ConditionNode node, UiRect rect,
                          boolean selected, boolean hovered) {
        int fill = selected ? theme.palette().canvasCardSelectedFill()
                : isContainer(node) ? theme.palette().canvasGroupFill() : theme.palette().canvasCardFill();
        if (hovered && !selected) {
            fill = theme.palette().canvasCardHoverFill();
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fill);
        drawBorder(graphics, rect, selected ? theme.palette().canvasCardBorderSelected() : theme.palette().canvasCardBorder());
        NodeInfo info = infos.get(node);
        int textX = rect.x() + BORDER + CARD_PADDING_X;
        int textWidth = Math.max(0, rect.width() - (BORDER + CARD_PADDING_X) * 2);
        int row1Y = rect.y() + BORDER + CARD_PADDING_Y;
        int color = info != null && (info.overflow() || info.leafMissing() || info.subtreeIssue())
                ? theme.palette().canvasTextDanger() : theme.palette().canvasText();
        String row1 = TextScroll.trimToWidth(renderFont, row1Label(node).getString(), textWidth);
        String row2 = TextScroll.trimToWidth(renderFont, row2Label(node).getString(), textWidth);
        graphics.drawString(renderFont, row1, textX, row1Y, color, false);
        graphics.drawString(renderFont, row2, textX, row1Y + CARD_ROW_HEIGHT, theme.palette().canvasTextMuted(), false);
    }

    private void drawHotspots(GuiGraphics graphics, Font renderFont) {
        for (Hotspot hotspot : hotspots) {
            UiRect rect = hotspot.rect();
            boolean hovered = hotspot == hoveredHotspot;
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(),
                    hovered ? theme.palette().canvasAccentHover() : theme.palette().canvasAccent());
            drawBorder(graphics, rect, theme.palette().canvasHotspotBorder());
            String mark = "+";
            graphics.drawString(renderFont, mark, rect.x() + (rect.width() - renderFont.width(mark)) / 2,
                    rect.y() + (rect.height() - renderFont.lineHeight) / 2 + 1, theme.palette().canvasTextOnAccent(), false);
        }
    }

    private void drawDropMarker(GuiGraphics graphics) {
        UiTreeEditor.MovePreview<ConditionNode> preview = move.preview();
        if (preview == null || dropTarget == null) {
            return;
        }
        GraphNode wrapper = wrappers.get(dropTarget);
        if (wrapper == null) {
            return;
        }
        UiRect rect = cardRect(wrapper);
        int color = preview.outcome().error() == null ? theme.palette().canvasAccent() : theme.palette().canvasTextDanger();
        if (preview.position() == UiTreeEditor.DropPosition.INSIDE) {
            drawBorder(graphics, rect, color);
            drawBorder(graphics, new UiRect(rect.x() - BORDER, rect.y() - BORDER,
                    rect.width() + BORDER * 2, rect.height() + BORDER * 2), color);
            return;
        }
        // 同层节点水平排列，BEFORE / AFTER 用目标卡片左右两侧的竖直短线表示
        int x = preview.position() == UiTreeEditor.DropPosition.BEFORE
                ? rect.x() - DROP_MARKER_THICKNESS : rect.right();
        graphics.fill(x, rect.y(), x + DROP_MARKER_THICKNESS, rect.bottom(), color);
    }

    private void drawDragGhost(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        String label = dragLabel == null ? "" : dragLabel;
        int width = Math.min(bounds.width(), renderFont.width(label) + DRAG_GHOST_GAP * 2);
        int height = renderFont.lineHeight + 2;
        int x = Math.clamp(mouseX + DRAG_GHOST_GAP, bounds.x(), Math.max(bounds.x(), bounds.right() - width));
        int y = Math.clamp(mouseY + DRAG_GHOST_GAP, bounds.y(), Math.max(bounds.y(), bounds.bottom() - height));
        graphics.fill(x, y, x + width, y + height, theme.palette().canvasDragGhostFill());
        graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont, label, Math.max(0, width - DRAG_GHOST_GAP)),
                x + DRAG_GHOST_GAP / 2, y + 1, theme.palette().canvasText(), false);
    }

    private static void drawBorder(GuiGraphics graphics, UiRect rect, int color) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.y() + BORDER, color);
        graphics.fill(rect.x(), rect.bottom() - BORDER, rect.right(), rect.bottom(), color);
        graphics.fill(rect.x(), rect.y() + BORDER, rect.x() + BORDER, rect.bottom() - BORDER, color);
        graphics.fill(rect.right() - BORDER, rect.y() + BORDER, rect.right(), rect.bottom() - BORDER, color);
    }

    // ---- 内联参数编辑框 ----

    /**
     * 装载内联参数编辑框：几何由锚点卡片推导（宽度 = max(卡片宽, 140)，紧贴卡片下沿 6 内容像素，
     * 下方越出视口时翻到卡片上方），并以 160ms ease-out 展开高度与透明度。
     * 传入 null 等价于 {@link #clearInlineEditor()}。
     */
    public void setInlineEditor(@Nullable InlineEditor editor, @Nullable UiTreePath anchorPath) {
        if (editor == null || anchorPath == null) {
            clearInlineEditor();
            return;
        }
        if (editor == inlineEditor && anchorPath.equals(inlineAnchorPath) && !inlineClosing) {
            return;
        }
        // 只有锚点变化才从头展开；同一锚点换实例时保留当前展开度，避免宿主每帧新建适配器导致动画永远重来
        boolean restart = !anchorPath.equals(inlineAnchorPath);
        inlineEditor = editor;
        inlineAnchorPath = anchorPath;
        inlineClosing = false;
        inlineCaptured = false;
        if (restart) {
            inlineOpenness = 0.0F;
        }
        startInlineAnimation(1.0F);
    }

    /** 收起内联参数编辑框：先按相同动画收缩，动画结束后再释放；未装载时不做任何事。 */
    public void clearInlineEditor() {
        if (inlineEditor == null || inlineClosing) {
            return;
        }
        if (inlineOpenness <= 0.0F) {
            releaseInlineEditor();
            return;
        }
        inlineClosing = true;
        startInlineAnimation(0.0F);
    }

    /** {@return 是否已装载内联参数编辑框（含收起动画期间）} */
    public boolean isInlineEditorOpen() {
        return inlineEditor != null;
    }

    private void startInlineAnimation(float target) {
        inlineAnimFrom = inlineOpenness;
        inlineAnimTarget = Math.clamp(target, 0.0F, 1.0F);
        inlineAnimStart = Util.getMillis();
    }

    // 按 ease-out 推进展开度：只影响高度与透明度；关闭态动画结束后释放编辑框
    private void tickInlineAnimation() {
        if (inlineEditor == null) {
            return;
        }
        if (inlineAnimTarget == inlineOpenness) {
            if (inlineClosing) {
                releaseInlineEditor();
            }
            return;
        }
        float span = Math.abs(inlineAnimTarget - inlineAnimFrom);
        long durationMs = Math.max(1L, Math.round(INLINE_ANIM_MS * span));
        float progress = Math.clamp((Util.getMillis() - inlineAnimStart) / (float) durationMs, 0.0F, 1.0F);
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
        inlineOpenness = Math.clamp(inlineAnimFrom + (inlineAnimTarget - inlineAnimFrom) * eased, 0.0F, 1.0F);
        if (progress >= 1.0F) {
            inlineOpenness = inlineAnimTarget;
            if (inlineClosing) {
                releaseInlineEditor();
            }
        }
    }

    private void releaseInlineEditor() {
        inlineEditor = null;
        inlineAnchorPath = null;
        inlineClosing = false;
        inlineCaptured = false;
        inlineOpenness = 0.0F;
        inlineAnimFrom = 0.0F;
        inlineAnimTarget = 0.0F;
    }

    /*
     * 锚点路径在树里消失、或该位置不再是叶节点（被替换成容器）时收起内联框。
     * 不按对象身份判等：参数提交会让整棵树重建出新对象，按身份判等会在每次输入后误关表单。
     */
    private void validateInlineAnchor() {
        if (inlineEditor == null || inlineAnchorPath == null) {
            return;
        }
        if (nodesByPath.get(inlineAnchorPath) instanceof ConditionNode.Leaf) {
            return;
        }
        clearInlineEditor();
    }

    // 内联框几何：内容坐标系；高度按当前展开度裁切
    private void updateInlineBounds() {
        InlineEditor editor = inlineEditor;
        if (editor == null) {
            return;
        }
        ConditionNode anchor = inlineAnchorPath == null ? null : nodesByPath.get(inlineAnchorPath);
        GraphNode wrapper = anchor == null ? null : wrappers.get(anchor);
        if (wrapper == null) {
            clearInlineEditor();
            return;
        }
        UiRect card = cardRect(wrapper);
        UiRect viewport = transform.viewport();
        int viewLeft = (int) Math.ceil(transform.toLocalX(viewport.x()));
        int viewTop = (int) Math.ceil(transform.toLocalY(viewport.y()));
        int viewRight = (int) Math.floor(transform.toLocalX(viewport.right()));
        int viewBottom = (int) Math.floor(transform.toLocalY(viewport.bottom()));
        int viewWidth = Math.max(1, viewRight - viewLeft);
        int width = Math.clamp(Math.max(card.width(), INLINE_MIN_WIDTH),
                Math.min(INLINE_MIN_WIDTH, viewWidth), viewWidth);
        int fullHeight = Math.max(0, editor.preferredHeight(width));
        int x = Math.clamp(card.x() + (card.width() - width) / 2, viewLeft, Math.max(viewLeft, viewRight - width));
        int y = card.bottom() + INLINE_GAP;
        if (y + fullHeight > viewBottom) {
            // 下方放不下就翻到卡片上方
            y = card.y() - INLINE_GAP - fullHeight;
        }
        y = Math.clamp(y, viewTop, Math.max(viewTop, viewBottom - fullHeight));
        int openHeight = Math.clamp((int) Math.ceil(fullHeight * Math.clamp(inlineOpenness, 0.0F, 1.0F)),
                0, fullHeight);
        editor.setBounds(x, y, width, openHeight);
    }

    private void renderInlineEditor(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        tickInlineAnimation();
        InlineEditor editor = inlineEditor;
        if (editor == null) {
            return;
        }
        updateInlineBounds();
        editor = inlineEditor;
        if (editor == null || !editor.isVisible()) {
            return;
        }
        UiRect rect = editor.bounds();
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        float alpha = Math.clamp(inlineOpenness, 0.0F, 1.0F);
        int localX = (int) Math.round(transform.toLocalX(mouseX));
        int localY = (int) Math.round(transform.toLocalY(mouseY));
        // 展开过程中裁切到当前高度，避免表单内容先于高度冒出来
        UiTransform.enableScissor(graphics, rect);
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        try {
            editor.render(graphics, renderFont, localX, localY);
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            UiTransform.disableScissor(graphics);
        }
    }

    // 屏幕坐标是否落在内联框内容矩形内（命中前按当前几何刷新一次）
    private boolean inlineEditorHit(double screenX, double screenY) {
        if (inlineEditor == null) {
            return false;
        }
        updateInlineBounds();
        InlineEditor editor = inlineEditor;
        if (editor == null || !editor.isVisible()) {
            return false;
        }
        return editor.bounds().contains(transform.toLocalX(screenX), transform.toLocalY(screenY));
    }

    // ---- 分组方式小菜单 ----

    private void openGroupKindMenu(Hotspot hotspot) {
        closePicker();
        kindMenuTarget = KindMenuTarget.INSERT_GROUP;
        kindMenuHotspot = hotspot;
        kindMenuIndex = 0;
        kindMenuOpen = true;
        updateKindMenuRect();
    }

    private void closeKindMenu() {
        kindMenuOpen = false;
        kindMenuHotspot = null;
        kindMenuIndex = 0;
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
        GroupKind[] kinds = GroupKind.values();
        int width = KIND_MENU_MIN_WIDTH;
        for (GroupKind kind : kinds) {
            width = Math.max(width, font.width(kind.label().getString()) + KIND_MENU_PADDING * 2 + BORDER * 2);
        }
        int maxWidth = Math.max(1, bounds.width() - KIND_MENU_PADDING);
        width = Math.min(width, maxWidth);
        int height = Math.min(kinds.length * PICKER_ROW_HEIGHT + PICKER_FOOTER, bounds.height());
        int x = anchor.right() + KIND_MENU_GAP;
        if (x + width > bounds.right()) {
            x = anchor.x() - KIND_MENU_GAP - width;
        }
        x = Math.clamp(x, bounds.x(), Math.max(bounds.x(), bounds.right() - width));
        int y = Math.clamp(anchor.y(), bounds.y(), Math.max(bounds.y(), bounds.bottom() - height));
        kindMenuRect = new UiRect(x, y, width, height);
        // 高度被小视口压缩时同步压缩列表区，避免提示行画到菜单底框之外
        int maxListHeight = Math.max(0, height - BORDER * 2 - PICKER_FOOTER);
        int listHeight = Math.min(kinds.length * PICKER_ROW_HEIGHT, maxListHeight);
        kindMenuListRect = new UiRect(x + BORDER, y + BORDER, Math.max(0, width - BORDER * 2), listHeight);
    }

    private @Nullable UiRect kindMenuAnchorRect() {
        if (kindMenuTarget == KindMenuTarget.INSERT_GROUP) {
            Hotspot hotspot = kindMenuHotspot;
            return hotspot == null ? null : toScreenRect(hotspot.rect());
        }
        ConditionNode first = firstSelectedInOrder();
        GraphNode wrapper = first == null ? null : wrappers.get(first);
        return wrapper == null ? null : toScreenRect(cardRect(wrapper));
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
        for (ConditionNode node : order) {
            UiTreePath path = paths.get(node);
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
        GroupKind[] kinds = GroupKind.values();
        graphics.fill(kindMenuRect.x(), kindMenuRect.y(), kindMenuRect.right(), kindMenuRect.bottom(),
                theme.palette().canvasPickerFill());
        drawBorder(graphics, kindMenuRect, theme.palette().canvasPickerBorder());
        for (int index = 0; index < kinds.length; index++) {
            int rowY = kindMenuRect.y() + BORDER + index * PICKER_ROW_HEIGHT;
            boolean current = index == Math.clamp(kindMenuIndex, 0, kinds.length - 1);
            if (current) {
                graphics.fill(kindMenuRect.x() + BORDER, rowY, kindMenuRect.right() - BORDER,
                        rowY + PICKER_ROW_HEIGHT, theme.palette().canvasPickerRowSelected());
            }
            graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont, kinds[index].label().getString(),
                            Math.max(0, kindMenuRect.width() - KIND_MENU_PADDING * 2)),
                    kindMenuRect.x() + KIND_MENU_PADDING, rowY + 2,
                    current ? theme.palette().canvasPickerText() : theme.palette().canvasPickerTextMuted(), false);
        }
        graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont,
                        Component.translatable(KIND_MENU_HINT_KEY).getString(),
                        Math.max(0, kindMenuRect.width() - KIND_MENU_PADDING * 2)),
                kindMenuRect.x() + KIND_MENU_PADDING, kindMenuListRect.bottom() + 2,
                theme.palette().canvasPickerTextMuted(), false);
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
        int row = (int) ((mouseY - kindMenuListRect.y()) / Math.max(1, PICKER_ROW_HEIGHT));
        return row >= 0 && row < GroupKind.values().length ? row : -1;
    }

    // 键盘：←/→ 或 ↑/↓ 切换，回车确认，Esc 取消
    private void kindMenuKeyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeKindMenu();
        } else if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_UP) {
            kindMenuIndex = Math.max(0, kindMenuIndex - 1);
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_DOWN) {
            kindMenuIndex = Math.min(GroupKind.values().length - 1, kindMenuIndex + 1);
        } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirmKindMenu();
        }
    }

    // 确认分组方式：加分组插入容器并保持添加模式；包组直接对选区执行
    private void confirmKindMenu() {
        if (!kindMenuOpen) {
            return;
        }
        GroupKind kind = GroupKind.values()[Math.clamp(kindMenuIndex, 0, GroupKind.values().length - 1)];
        KindMenuTarget target = kindMenuTarget;
        Hotspot hotspot = kindMenuHotspot;
        closeKindMenu();
        if (target == KindMenuTarget.WRAP_SELECTION) {
            wrapSelection(kind.allOf);
            return;
        }
        ConditionNode group = kind.allOf ? new ConditionNode.AllOf(List.of()) : new ConditionNode.AnyOf(List.of());
        lastOpKey = EditSession.OP_ADD_GROUP;
        if (hotspot == null || !insertNode(hotspot.parent(), hotspot.index(), group)) {
            return;
        }
        // 新容器自带大 [+]，继续保持添加模式让用户接着补子项
        addMode = true;
        groupKindPending = false;
    }

    // ---- 命中 ----

    private @Nullable ConditionNode nodeAt(double localX, double localY) {
        for (int index = graphNodes.size() - 1; index >= 0; index--) {
            NodeSlot wrapper = graphNodes.get(index);
            if (cardRect(wrapper).contains(localX, localY)) {
                return wrapper.node;
            }
        }
        return null;
    }

    private @Nullable Hotspot hotspotAt(double localX, double localY) {
        for (Hotspot hotspot : hotspots) {
            if (hotspot.rect().contains(localX, localY)) {
                return hotspot;
            }
        }
        return null;
    }

    // ---- 输入 ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !enabled || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (pickerOpen) {
            handlePickerClick(mouseX, mouseY);
            return true;
        }
        if (kindMenuOpen) {
            handleKindMenuClick(mouseX, mouseY);
            return true;
        }
        // 内联框打开时鼠标命中优先：坐标换算成内容坐标后转发
        if (inlineEditorHit(mouseX, mouseY)) {
            InlineEditor editor = inlineEditor;
            inlineCaptured = true;
            if (editor != null) {
                editor.mouseClicked(transform.toLocalX(mouseX), transform.toLocalY(mouseY), button);
            }
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
        boolean emptyTree = expression.root() == null;
        if (addMode || emptyTree) {
            Hotspot hotspot = hotspotAt(localX, localY);
            if (hotspot != null) {
                if (groupKindPending) {
                    openGroupKindMenu(hotspot);
                } else {
                    openPicker(hotspot);
                }
                return true;
            }
            if (addMode) {
                // 添加模式下热区之外同样按空白区处理：Shift+拖拽框选，否则平移 / 未位移按点击清空选择
                if (Screen.hasShiftDown()) {
                    beginRubberBand(localX, localY);
                } else {
                    beginPan(mouseX, mouseY, true);
                }
                return true;
            }
        }
        ConditionNode node = nodeAt(localX, localY);
        if (node == null) {
            // 空白区左键拖拽 = 平移；框选改到 Shift + 拖拽
            if (Screen.hasShiftDown()) {
                beginRubberBand(localX, localY);
                return true;
            }
            beginPan(mouseX, mouseY, true);
            return true;
        }
        UiTreePath clicked = paths.get(node);
        if (clicked == null) {
            return true;
        }
        long now = Util.getMillis();
        boolean doubleClick = clicked.equals(lastClickPath) && now - lastClickTime <= DOUBLE_CLICK_MS;
        lastClickPath = clicked;
        lastClickTime = now;
        boolean control = Screen.hasControlDown();
        boolean shift = Screen.hasShiftDown();
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
        if (doubleClick && !control && !shift) {
            activate();
            return true;
        }
        // 根节点没有同级位置，不能拖动
        if (!clicked.indices().isEmpty()) {
            armedDrag = clicked;
            pressScreenX = mouseX;
            pressScreenY = mouseY;
        }
        return true;
    }

    private void endGestureForNewPress() {
        armedDrag = null;
        collapseSelectionOnRelease = false;
        dragLabel = null;
        dropTarget = null;
        if (move.isActive()) {
            move.end(UiInputCapture.EndReason.CANCEL);
        }
    }

    private void selectRange(UiTreePath clicked, boolean additive) {
        ConditionNode anchorNode = selectionAnchor == null ? null : tree.query(selectionAnchor);
        int anchorIndex = anchorNode == null ? -1 : indexOfNode(anchorNode);
        ConditionNode clickedNode = tree.query(clicked);
        int clickedIndex = clickedNode == null ? -1 : indexOfNode(clickedNode);
        if (anchorIndex < 0 || clickedIndex < 0) {
            tree.select(clicked);
            selectionAnchor = clicked;
            followPrimarySelection();
            return;
        }
        Set<UiTreePath> selected = new LinkedHashSet<>(additive ? tree.selection() : Set.of());
        for (int index = Math.min(anchorIndex, clickedIndex); index <= Math.max(anchorIndex, clickedIndex); index++) {
            selected.add(paths.get(order.get(index)));
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
            for (ConditionNode node : order) {
                GraphNode wrapper = wrappers.get(node);
                if (wrapper == null) {
                    continue;
                }
                UiRect rect = cardRect(wrapper);
                if (rubberBand.intersects(rect.x(), rect.y(), rect.width(), rect.height())) {
                    selected.add(paths.get(node));
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
        if (inlineCaptured && inlineEditor != null) {
            double scale = transform.scale();
            return inlineEditor.mouseDragged(transform.toLocalX(mouseX), transform.toLocalY(mouseY), button,
                    deltaX / scale, deltaY / scale);
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
                        : node == null ? "" : row1Label(node).getString();
                lastClickPath = null;
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
        ConditionNode target = nodeAt(localX, localY);
        if (target == null) {
            dropTarget = null;
            move.clearPreview();
            operationError = null;
            return;
        }
        GraphNode wrapper = wrappers.get(target);
        UiTreePath targetPath = paths.get(target);
        if (wrapper == null || targetPath == null) {
            return;
        }
        UiRect rect = cardRect(wrapper);
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
        if (inlineCaptured) {
            inlineCaptured = false;
            InlineEditor editor = inlineEditor;
            return editor == null
                    || editor.mouseReleased(transform.toLocalX(mouseX), transform.toLocalY(mouseY), button);
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
        boolean owned = armedDrag != null || move.isActive();
        boolean left = button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
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
        dropTarget = null;
        return owned;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!visible || !enabled || !bounds.contains(mouseX, mouseY) || scrollY == 0.0D) {
            return false;
        }
        if (pickerOpen) {
            if (filteredOptions.size() > 1) {
                pickerCursor = Math.clamp(pickerCursor + (scrollY > 0.0D ? -1 : 1), 0, filteredOptions.size() - 1);
            }
            return true;
        }
        if (kindMenuOpen) {
            kindMenuIndex = Math.clamp(kindMenuIndex + (scrollY > 0.0D ? -1 : 1), 0, GroupKind.values().length - 1);
            return true;
        }
        if (inlineEditorHit(mouseX, mouseY) && inlineEditor != null
                && inlineEditor.mouseScrolled(transform.toLocalX(mouseX), transform.toLocalY(mouseY), scrollY)) {
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
        if (pickerOpen) {
            pickerKeyPressed(keyCode);
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
        if (inlineEditor != null) {
            if (inlineEditor.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                clearInlineEditor();
                return true;
            }
        }
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
                centerOnRoot();
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
        if (inlineEditor != null) {
            // 内联框打开且未匹配 Ctrl 快捷键：其余按键交给文本框，避免输入字母 / 空格 / 删除误触画布命令
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            spaceDown = true;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (addMode) {
                addMode = false;
                closePicker();
                return true;
            }
            return false;
        }
        if (order.isEmpty()) {
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
        if (pickerOpen) {
            if (codePoint < ' ') {
                return false;
            }
            if (pickerFilter.length() < PICKER_FILTER_MAX) {
                pickerFilter.append(codePoint);
            }
            refreshFilter();
            return true;
        }
        if (kindMenuOpen) {
            // 菜单只有两个固定选项，不接受文本输入
            return false;
        }
        return inlineEditor != null && inlineEditor.charTyped(codePoint, modifiers);
    }

    // ---- 键盘导航 ----

    private boolean navigate(int keyCode) {
        UiTreePath selected = tree.selected();
        if (selected == null) {
            if (order.isEmpty()) {
                return false;
            }
            return selectNode(order.getFirst());
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
                int index = indexOfNode(node);
                next = index >= 0 && index + 1 < order.size() ? paths.get(order.get(index + 1)) : null;
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

    private int indexOfNode(ConditionNode node) {
        for (int index = 0; index < order.size(); index++) {
            if (order.get(index) == node) {
                return index;
            }
        }
        return -1;
    }

    private boolean selectNode(ConditionNode node) {
        UiTreePath path = paths.get(node);
        if (path == null) {
            return false;
        }
        tree.select(path);
        followPrimarySelection();
        revealNode(node);
        return true;
    }

    private void centerOnRoot() {
        ConditionNode root = expression.root();
        if (root == null) {
            transform.centerOn(0, 0);
            return;
        }
        GraphNode wrapper = wrappers.get(root);
        if (wrapper != null) {
            UiRect rect = cardRect(wrapper);
            transform.centerOn(rect.x() + rect.width() / 2.0, rect.y() + rect.height() / 2.0);
        }
    }

    // 把卡片带进视口，保留 REVEAL_MARGIN 屏幕像素边距
    private void revealNode(ConditionNode node) {
        GraphNode wrapper = wrappers.get(node);
        if (wrapper == null) {
            return;
        }
        UiRect rect = cardRect(wrapper);
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

    // ---- 类型选择框 ----

    private void openPicker(Hotspot hotspot) {
        closeKindMenu();
        pickerOpen = true;
        pickerParent = hotspot.parent();
        pickerIndex = hotspot.index();
        pickerCursor = 0;
        pickerOffset = 0;
        pickerFilter.setLength(0);
        refreshFilter();
    }

    /** 关闭类型选择框；未打开时不做任何事。 */
    public void closePicker() {
        pickerOpen = false;
        pickerParent = null;
        pickerFilter.setLength(0);
        filteredOptions = List.of();
        pickerCursor = 0;
        pickerOffset = 0;
    }

    private void refreshFilter() {
        String needle = pickerFilter.toString().trim().toLowerCase(Locale.ROOT);
        filteredOptions = needle.isEmpty() ? List.copyOf(typeOptions) : typeOptions.stream()
                .filter(option -> option.label().getString().toLowerCase(Locale.ROOT).contains(needle)
                        || option.id().toString().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        pickerCursor = Math.clamp(pickerCursor, 0, Math.max(0, filteredOptions.size() - 1));
        pickerOffset = Math.clamp(pickerOffset, 0, Math.max(0, filteredOptions.size() - 1));
    }

    private void updatePickerRects() {
        int width = Math.clamp(bounds.width() - PICKER_MARGIN * 2, 0, PICKER_MAX_WIDTH);
        if (width <= 0 || width > bounds.width()) {
            closePicker();
            pickerRect = new UiRect(0, 0, 0, 0);
            return;
        }
        int maxRows = Math.max(1, (bounds.height() - PICKER_HEADER - PICKER_SEARCH_ROW - PICKER_FOOTER
                - PICKER_MARGIN * 2) / PICKER_ROW_HEIGHT);
        pickerVisibleRows = Math.clamp(Math.min(PICKER_MAX_ROWS, maxRows), 1, Math.max(1, filteredOptions.size()));
        int height = PICKER_HEADER + PICKER_SEARCH_ROW + pickerVisibleRows * PICKER_ROW_HEIGHT + PICKER_FOOTER;
        if (height > bounds.height()) {
            closePicker();
            pickerRect = new UiRect(0, 0, 0, 0);
            return;
        }
        int x = bounds.x() + (bounds.width() - width) / 2;
        int y = bounds.y() + (bounds.height() - height) / 2;
        pickerRect = new UiRect(x, y, width, height);
        pickerSearchRect = new UiRect(x + 2, y + PICKER_HEADER, width - 4, PICKER_SEARCH_ROW - 2);
        pickerListRect = new UiRect(x + 1, pickerSearchRect.bottom() + 1, width - 2,
                pickerVisibleRows * PICKER_ROW_HEIGHT);
    }

    private void renderPicker(GuiGraphics graphics, Font renderFont) {
        if (pickerRect.width() <= 0 || pickerRect.height() <= 0) {
            return;
        }
        graphics.fill(pickerRect.x(), pickerRect.y(), pickerRect.right(), pickerRect.bottom(), theme.palette().canvasPickerFill());
        graphics.fill(pickerRect.x() + 1, pickerRect.y() + 1, pickerRect.right() - 1,
                pickerRect.y() + PICKER_HEADER, theme.palette().canvasPickerHeaderFill());
        drawBorder(graphics, pickerRect, theme.palette().canvasPickerBorder());
        graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont,
                        Component.translatable("gui.itemdespawntowhat.edit.picker.title").getString(),
                        pickerRect.width() - 6),
                pickerRect.x() + 3, pickerRect.y() + 3, theme.palette().canvasPickerHeaderText(), false);

        graphics.fill(pickerSearchRect.x(), pickerSearchRect.y(), pickerSearchRect.right(),
                pickerSearchRect.bottom(), theme.palette().canvasPickerInputFill());
        drawBorder(graphics, pickerSearchRect, theme.palette().canvasPickerInputBorder());
        String filter = pickerFilter.toString();
        String searchText = filter.isEmpty()
                ? Component.translatable("gui.itemdespawntowhat.edit.picker.filter").getString() : filter;
        int searchColor = filter.isEmpty() ? theme.palette().canvasPickerTextDisabled() : theme.palette().canvasTextOnAccent();
        graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont, searchText, pickerSearchRect.width() - 6),
                pickerSearchRect.x() + 2, pickerSearchRect.y() + 2, searchColor, false);
        int caretX = pickerSearchRect.x() + 2 + Math.min(renderFont.width(filter), pickerSearchRect.width() - 4);
        graphics.fill(caretX, pickerSearchRect.y() + 2, caretX + 1, pickerSearchRect.bottom() - 2,
                theme.palette().canvasTextOnAccent());

        if (filteredOptions.isEmpty()) {
            graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont,
                            Component.translatable("gui.itemdespawntowhat.edit.picker.empty").getString(),
                            pickerListRect.width() - 4),
                    pickerListRect.x() + 2, pickerListRect.y() + 2, theme.palette().canvasPickerTextMuted(), false);
        } else {
            pickerOffset = Math.clamp(pickerOffset, 0, Math.max(0, filteredOptions.size() - pickerVisibleRows));
            for (int row = 0; row < pickerVisibleRows; row++) {
                int optionIndex = pickerOffset + row;
                if (optionIndex >= filteredOptions.size()) {
                    break;
                }
                int rowY = pickerListRect.y() + row * PICKER_ROW_HEIGHT;
                boolean current = optionIndex == pickerCursor;
                if (current) {
                    graphics.fill(pickerListRect.x(), rowY, pickerListRect.right(), rowY + PICKER_ROW_HEIGHT,
                            theme.palette().canvasPickerRowSelected());
                }
                graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont,
                                filteredOptions.get(optionIndex).label().getString(), pickerListRect.width() - 8),
                        pickerListRect.x() + 4, rowY + 2,
                        current ? theme.palette().canvasPickerText() : theme.palette().canvasPickerTextMuted(), false);
            }
        }
        graphics.drawString(renderFont, TextScroll.trimToWidth(renderFont,
                        Component.translatable("gui.itemdespawntowhat.edit.picker.hint").getString(),
                        pickerRect.width() - 6),
                pickerRect.x() + 3, pickerListRect.bottom() + 2, theme.palette().canvasPickerTextMuted(), false);
    }

    private void handlePickerClick(double mouseX, double mouseY) {
        int index = pickerIndexAt(mouseX, mouseY);
        if (index >= 0) {
            pickerCursor = index;
            confirmPicker();
            return;
        }
        if (!pickerRect.contains(mouseX, mouseY)) {
            closePicker();
        }
    }

    private int pickerIndexAt(double mouseX, double mouseY) {
        if (filteredOptions.isEmpty() || !pickerListRect.contains(mouseX, mouseY)) {
            return -1;
        }
        int row = (int) ((mouseY - pickerListRect.y()) / Math.max(1, PICKER_ROW_HEIGHT));
        int index = pickerOffset + row;
        return index >= 0 && index < filteredOptions.size() ? index : -1;
    }

    private void pickerKeyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closePicker();
        } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!pickerFilter.isEmpty()) {
                pickerFilter.setLength(pickerFilter.length() - 1);
                refreshFilter();
            }
        } else if (keyCode == GLFW.GLFW_KEY_UP) {
            pickerCursor = Math.max(0, pickerCursor - 1);
        } else if (keyCode == GLFW.GLFW_KEY_DOWN) {
            pickerCursor = Math.clamp(pickerCursor + 1, 0, Math.max(0, filteredOptions.size() - 1));
        } else if (keyCode == GLFW.GLFW_KEY_HOME) {
            pickerCursor = 0;
        } else if (keyCode == GLFW.GLFW_KEY_END) {
            pickerCursor = Math.max(0, filteredOptions.size() - 1);
        } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (!filteredOptions.isEmpty()) {
                confirmPicker();
            }
        }
    }

    // 确认类型：生成默认叶节点并插入热区位置，随后直接进入参数编辑
    private void confirmPicker() {
        if (pickerCursor < 0 || pickerCursor >= filteredOptions.size()) {
            closePicker();
            return;
        }
        ResourceLocation type = filteredOptions.get(pickerCursor).id();
        UiTreePath parent = pickerParent;
        int index = pickerIndex;
        closePicker();
        addMode = false;
        ConditionTreeContract.LeafFactory factory = leafFactory;
        if (factory == null) {
            return;
        }
        ConditionNode leaf = factory.create(type);
        if (leaf == null) {
            return;
        }
        lastOpKey = EditSession.OP_ADD_CONDITION;
        if (insertNode(parent, index, leaf)) {
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

    private @Nullable ConditionNode nodeForJsonPath(String path) {
        ConditionNode best = null;
        int bestLength = -1;
        for (Map.Entry<ConditionNode, UiTreePath> entry : paths.entrySet()) {
            String candidate = ConditionTreeNodes.jsonPath(expression.root(), entry.getValue(),
                    ConditionTreeContract.ROOT_PATH);
            if (!path.equals(candidate) && !path.startsWith(candidate + ".")) {
                continue;
            }
            if (candidate.length() > bestLength) {
                bestLength = candidate.length();
                best = entry.getKey();
            }
        }
        return best;
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
        int size = target == null ? 0 : ConditionTreeNodes.INSTANCE.children(target).size();
        return insertNode(parent, size, newNode);
    }

    // 当前目标容器：选中容器则用它，否则向上找最近的容器
    private @Nullable UiTreePath targetContainerPath() {
        UiTreePath selected = tree.selected();
        if (selected == null) {
            return null;
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

    private static boolean isContainer(@Nullable ConditionNode node) {
        return node instanceof ConditionNode.AllOf || node instanceof ConditionNode.AnyOf
                || node instanceof ConditionNode.Inverted;
    }

    // ---- 焦点 ----

    @Override
    public boolean canFocus() {
        return visible && enabled;
    }

    @Override
    public void setFocused(boolean newFocused) {
        if (!newFocused) {
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
            return !addMode && beginAddCondition();
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
