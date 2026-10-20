package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.ConditionTreeClipboard;
import com.meteorite.itemdespawntowhat.client.edit.ConditionTreeJsonCodec;
import com.meteorite.itemdespawntowhat.client.edit.ConditionTreeNodes;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.OpaqueCondition;
import com.meteorite.itemdespawntowhat.client.edit.RuleNaming;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIconStrip;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreeCodec;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreePath;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiFlatSkin;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.shared.RuleDisplayLabels;
import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionTreeContract;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiConditionGraphEditor;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCodeView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 条件表达式画布页外壳：左上水平功能栏、左缘竖直工具列、中间条件画布与底部状态栏。
 *
 * <p>装配关系：功能栏与工具列是 kit 的 {@link UiIconStrip}，画布是 {@link UiConditionGraphEditor}，
 * 两者统一经 {@link ConditionTreeChrome} 转发输入，本类不直接接触 kit 的输入契约。
 * 条件叶的参数表单经 {@link ConditionTreeChrome.Inline} 适配成画布的内联编辑框：点击条件叶
 * 或定位参数问题时注入，容器节点没有参数不展开；表单复用 {@link FormView} 与描述符引擎。</p>
 *
 * <p>与调用方的关系保持既有形状：{@link Parameters}、{@link ParameterFactory}、构造签名，
 * 以及 {@link #historyChanged()} / {@link #discardView()} / {@link #unmount()} / {@link #reveal(String)}
 * 都由 {@code RuleEditorEditPages} 原样调用。参数编辑以共享草稿路径为准，未完成的输入留在会话。</p>
 */
public final class ConditionTreeOverlay implements UiWidget, UiFocusTarget {

    // 宿主默认主题：屏幕从主题入口取样式后注入本类装配的控件
    // 宿主默认主题：屏幕从主题入口取样式后注入本类装配的控件
    private static final UiHostTheme THEME = UiHostThemes.defaultTheme();
    // 画布页外壳（功能栏/工具列/状态栏/帮助浮层）使用扁平宿主主题（仅显式取用生效）
    private static final UiHostTheme CHROME_THEME = UiHostThemes.flatTheme();

    // 本地化前缀：条件树页
    private static final String UI = "gui.itemdespawntowhat.edit.tree.";
    // 本地化前缀：规则编辑器（复用既有撤销/重做与通用按钮文案）
    private static final String EDIT_UI = TypeLabels.UI_PREFIX;

    // ---- 布局 ----

    // 外壳统一线宽：内容区外围 1 像素外框，以及外壳各区域与画布之间的 1 像素分隔线
    private static final int SHELL_LINE = 1;
    // 图标格边长：图标最大边 16 加每侧 2 像素格子留白（与 UiIconStrip 的单格布局一致）
    private static final int ICON_CELL = 16 + 2 * 2;
    // 顶部水平功能栏高度：外框 1 + 图标格 20 + 分隔线 1；图标格被垂直居中，图标上下各余 2 像素
    private static final int FUNCTION_BAR_HEIGHT = SHELL_LINE + ICON_CELL + SHELL_LINE;
    // 左侧竖直工具列宽度
    private static final int TOOL_COLUMN_WIDTH = ICON_CELL;
    // 底部状态栏高度
    private static final int STATUS_BAR_HEIGHT = 14;
    // 状态栏文本左右留白
    private static final int STATUS_TEXT_PAD = 3;
    // 只读 JSON 视图的复制按钮
    private static final int COPY_BUTTON_WIDTH = 62;
    private static final int COPY_BUTTON_HEIGHT = 14;
    // 帮助浮层：面板宽度按正文测量后夹在最小值与该上限之间，窄视口下再退到画布可用宽度
    private static final int HELP_WIDTH = 320;
    private static final int HELP_MIN_WIDTH = 160;
    private static final int HELP_PADDING = 6;
    private static final int HELP_LINE_GAP = 2;
    // 动作标签列与说明文字之间的间距
    private static final int HELP_LABEL_GAP = 6;
    // 提示气泡宽度范围
    private static final int TOOLTIP_MIN_WIDTH = 80;
    private static final int TOOLTIP_MAX_WIDTH = 250;

    /** 叶参数表单与承载面板；title 为宿主提供的条件名，内联框当前不绘制标题。 */
    public record Parameters(FormView form, LeafPanel panel, Component title) { }

    /** 参数面板工厂：按条件路径与叶子构造表单。 */
    @FunctionalInterface
    public interface ParameterFactory {
        Parameters create(String conditionPath, ConditionNode.Leaf leaf);

        /**
         * 该叶的现役参数表单是否承载这个参数字段路径。
         *
         * <p>判定必须无副作用：只按描述符与现役绑定核对，不构建参数面板，不写草稿。字段可以有
         * 合法缺省值（如催化剂门槛省略），因此只看现役字段是否承载该路径，不看草稿里是否已有该值。
         * 默认返回 {@code false}，表示无法确认时按退化处理：只打开正确的树，不聚焦。</p>
         *
         * @param conditionPath 叶参数在草稿中的绝对路径
         * @param leaf          目标条件叶
         * @param fieldPath     待定位的参数字段绝对路径
         * @return 现役参数表单承载该字段时为 {@code true}
         */
        default boolean carriesField(String conditionPath, ConditionNode.Leaf leaf, String fieldPath) {
            return false;
        }
    }

    // 外壳内的焦点区域：Tab / Shift+Tab 在可用区域之间环绕
    private enum FocusSlot { FUNCTION_BAR, TOOL_COLUMN, CANVAS, JSON }

    private final Font font;
    private final EditSession session;
    private final String scope;
    private final ParameterFactory parameterFactory;
    private final Runnable changed;
    private final UiConditionGraphEditor editor;
    private final UiTreeCodec<ConditionNode, RuleValue> codec;
    private final ConditionTreeClipboard clipboard;
    private final UiCodeView source;
    private final ConditionTreeChrome.Strip functionBar;
    private final ConditionTreeChrome.Strip toolColumn;
    private final List<UiIconStrip.Item> toolItems = new ArrayList<>();
    // 需要选中节点才可用的工具
    private final List<UiIconStrip.Item> selectionTools = new ArrayList<>();
    // 功能栏的 4 个格子句柄：量取底板宽度用，收窄后右侧空白交还画布
    private final List<UiIconStrip.Item> functionItems;
    private final UiIconStrip.Item undoItem;
    private final UiIconStrip.Item redoItem;
    private final UiIconStrip.Item jsonItem;
    private final UiIconStrip.Item helpItem;
    private final UiIconStrip.Item addConditionItem;
    private final UiIconStrip.Item branchItem;
    private final UiIconStrip.Item wrapItem;

    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private UiRect canvasRect = bounds;
    private UiRect statusRect = bounds;
    private UiRect copyJsonRect = new UiRect(0, 0, 0, 0);
    private @Nullable Parameters parameters;
    // 当前注入画布的内联参数框；与 parameters 同生命周期
    private @Nullable ConditionTreeChrome.Inline inlineEditor;
    private @Nullable String parameterPath;
    private @Nullable String parameterType;
    private @Nullable String selectedPath;
    private @Nullable UiWidget pressed;
    private @Nullable Component decodeError;
    // 宿主注入的撤销/重做入口；为空时退回本页直接调用会话台账
    private @Nullable Runnable undoAction;
    private @Nullable Runnable redoAction;
    private FocusSlot focusSlot = FocusSlot.CANVAS;
    private boolean sourceView;
    private boolean helpOpen;
    private boolean focused;
    private long revision;

    /**
     * 创建条件画布叠加视图。
     *
     * @param font             渲染字体
     * @param session          草稿会话
     * @param scope            本视图对应的条件树绝对草稿作用域
     * @param support          共享的条件定义与按场景创建能力
     * @param parameterFactory 叶参数表单工厂
     * @param changed          草稿发生变化时的通知
     */
    public ConditionTreeOverlay(Font font, EditSession session, String scope, ConditionEditingSupport support,
                                ParameterFactory parameterFactory, Runnable changed) {
        this.font = font;
        this.session = session;
        this.scope = scope;
        this.parameterFactory = parameterFactory;
        this.changed = changed;
        codec = new ConditionTreeJsonCodec(support.registry());
        clipboard = new ConditionTreeClipboard(support.registry());
        source = new UiCodeView(THEME, font);
        source.setEmptyHint(Component.translatable(UI + "source_empty"));
        source.setAccessibleName(Component.translatable(UI + "source_title"));
        editor = new UiConditionGraphEditor(THEME, font).setTypeOptions(support.typeOptions()).setLeafFactory(support.leafFactory());
        editor.setLeafSummary(leaf -> {
            RuleValue condition = conditionJson(leaf);
            return condition != null
                    ? NaturalSummary.conditionLeaf(condition)
                    : TypeLabels.conditionLabel(leaf.condition().type());
        });
        editor.setLeafTooltip(leaf -> {
            RuleValue condition = conditionJson(leaf);
            return condition == null ? List.of() : ConditionDescription.lines(condition, THEME);
        });
        editor.setClipboardActions(this::copyNodes, this::pasteNodes);
        editor.setOnEditLeaf(leaf -> {
            syncSelection(true);
            setFocusSlot(FocusSlot.CANVAS);
        });
        editor.setBeforeEdit(() -> {
            finishParameters();
            reloadExpression();
            unmountParameters();
        });
        editor.setListener(expression -> {
            var change = editor.lastChange();
            RuleValue next = editor.tree().encode(codec);
            session.apply(editor.undoOpKey(), () -> {
                if (change != null) ConditionTreeNodes.remapInputs(session, change, scope);
                if (next == null) session.draft().removeAt(scope); else session.draft().setAt(scope, next);
            });
            revision = session.revision();
            refreshSource();
            changed.run();
        });

        functionBar = new ConditionTreeChrome.Strip(font, UiIconStrip.Orientation.HORIZONTAL);
        undoItem = functionBar.add(CHROME_THEME.icon(UiHostTheme.ICON_UNDO, false), CHROME_THEME.icon(UiHostTheme.ICON_UNDO, true),
                Component.translatable(EDIT_UI + "button.undo_shortcut"), this::undo);
        redoItem = functionBar.add(CHROME_THEME.icon(UiHostTheme.ICON_REDO, false), CHROME_THEME.icon(UiHostTheme.ICON_REDO, true),
                Component.translatable(EDIT_UI + "button.redo_shortcut"), this::redo);
        // JSON 轮询：同一个入口按当前视图切换字形与提示，切回条件树时换成条件树图标
        jsonItem = functionBar.add(CHROME_THEME.icon(UiHostTheme.ICON_VIEW_JSON, false), CHROME_THEME.icon(UiHostTheme.ICON_VIEW_JSON, true),
                Component.translatable(UI + "button.source"), this::toggleSource);
        helpItem = functionBar.add(CHROME_THEME.icon(UiHostTheme.ICON_HELP, false), CHROME_THEME.icon(UiHostTheme.ICON_HELP, true),
                Component.translatable(UI + "tool.help"), this::toggleHelp);
        functionItems = List.of(undoItem, redoItem, jsonItem, helpItem);

        toolColumn = new ConditionTreeChrome.Strip(font, UiIconStrip.Orientation.VERTICAL);
        // 7 格：新建分支与包组的 ∧ / ∨ 由点击工具格后贴着它弹出的选择菜单决定，工具列不再各占两格
        addConditionItem = addTool(UiHostTheme.ICON_ADD_CONDITION, UI + "button.add", false, editor::beginAddCondition);
        branchItem = addTool(UiHostTheme.ICON_NEW_BRANCH, UI + "button.new_branch", false, this::beginBranchMenu);
        addTool(UiHostTheme.ICON_INVERT, UI + "button.not", true, editor::invertSelection);
        addTool(UiHostTheme.ICON_REMOVE, UI + "button.remove", true, editor::removeSelection);
        addTool(UiHostTheme.ICON_MOVE_UP, UI + "button.up", true, () -> editor.moveSelection(-1));
        addTool(UiHostTheme.ICON_MOVE_DOWN, UI + "button.down", true, () -> editor.moveSelection(1));
        wrapItem = addTool(UiHostTheme.ICON_WRAP_ALL, UI + "button.wrap", true, editor::beginWrapWithKind);

        reloadExpression();
    }

    public UiConditionGraphEditor editor() { return editor; }

    public EditSession session() { return session; }

    public String scope() { return scope; }

    // 「新建分支」以工具格自身的屏幕矩形为锚弹出 ∧ / ∨ 菜单：菜单贴着按钮出现，不必先进入添加模式再点热区
    private void beginBranchMenu() {
        editor.beginAddGroupWithKind(branchItem.bounds());
    }

    // 加一个工具格：需要选中节点的进 selectionTools，由布局按选择变化统一使能
    private UiIconStrip.Item addTool(int iconRow, String tooltipKey, boolean needsSelection, Runnable action) {
        UiIconStrip.Item item = toolColumn.add(CHROME_THEME.icon(iconRow, false), CHROME_THEME.icon(iconRow, true),
                Component.translatable(tooltipKey), action::run);
        toolItems.add(item);
        if (needsSelection) selectionTools.add(item);
        return item;
    }

    // ---- 数据绑定 ----

    // 取条件叶对应的条件 JSON：卡片摘要与悬停说明共用同一段编码逻辑
    private @Nullable RuleValue conditionJson(ConditionNode.Leaf leaf) {
        RuleValue raw = codec.encode(leaf);
        return raw != null && raw.isMapping() ? raw.child(RuleFields.CONDITION) : null;
    }

    private void reloadExpression() {
        var result = editor.tree().decode(codec, session.draft().valueAt(scope));
        decodeError = result.error();
        editor.setEnabled(result.succeeded());
        if (result.succeeded()) editor.setExpression(new ConditionExpression(result.root()));
        refreshEndpoints();
        refreshSource();
        revision = session.revision();
    }

    private void refreshEndpoints() {
        RuleValue rule = session.draft().root();
        RuleValue sourceValue = rule.child(RuleFields.SOURCE);
        RuleValue items = sourceValue != null && sourceValue.isMapping()
                ? sourceValue.child(RuleFields.SOURCE_ITEMS) : null;
        Component sourceSummary = items != null && items.isList() && !items.items().isEmpty()
                ? RuleNaming.sourceTitle(rule, RuleDisplayLabels::label) : Component.empty();
        String ownerPath = scope.equals(RuleFields.CONDITIONS) ? ""
                : scope.substring(0, scope.length() - RuleFields.CONDITIONS.length() - 1);
        RuleValue owner = ownerPath.isEmpty() ? rule : session.draft().valueAt(ownerPath);
        Component resultSummary = Component.empty();
        if (owner != null && owner.isMapping()) {
            RuleValue effects = owner.child(RuleFields.EFFECTS);
            RuleValue outcomes = owner.child(RuleFields.OUTCOMES);
            if (effects != null && effects.isList() && !effects.items().isEmpty()
                    || outcomes != null && outcomes.isList() && !outcomes.items().isEmpty()) {
                resultSummary = RuleNaming.resultTitle(owner, RuleDisplayLabels::label);
            } else if (owner.child(RuleFields.TYPE) != null) {
                resultSummary = NaturalSummary.effect(owner);
            }
        }
        editor.setEndpoints(sourceSummary, resultSummary);
    }

    private void refreshSource() {
        RuleValue raw = decodeError == null ? editor.tree().encode(codec) : session.draft().valueAt(scope);
        String text = raw == null || raw.isNull() ? "" : raw.toPrettyJson();
        if (!text.equals(source.text())) {
            source.setSections(text.isEmpty() ? List.of() : List.of(new UiCodeView.Section(null, null, text)));
        }
    }

    private void copyNodes() {
        if (sourceView) {
            return;
        }
        finishParameters();
        reloadExpression();
        RuleValue.Sequence nodes = RuleValue.list();
        for (var path : editor.selectedSubtrees()) {
            RuleValue raw = session.draft().valueAt(ConditionTreeNodes.jsonPath(editor.expression().root(), path, scope));
            if (raw != null) nodes.add(raw.deepCopy());
        }
        if (nodes.size() == 0) {
            editor.showOperationError(Component.translatable(UI + "select_copy"));
            return;
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(nodes.build().toPrettyJson());
        editor.showOperationError(null);
    }

    private void pasteNodes() {
        if (sourceView) {
            return;
        }
        var result = clipboard.read(Minecraft.getInstance().keyboardHandler.getClipboard());
        if (result.error() != null) {
            editor.showOperationError(result.error());
            return;
        }
        editor.pasteNodes(result.nodes());
    }

    // ---- 视图切换 ----

    private void toggleSource() {
        editor.endInteractions(UiInputCapture.EndReason.FOCUS_SCOPE_CHANGED);
        finishParameters();
        // 只读 JSON 视图下画布不可见，内联框随之收起；切回树视图时按当前选中重建
        unmountParameters();
        editor.closePicker();
        sourceView = !sourceView;
        source.setFocused(false);
        reloadExpression();
        layout();
        setFocusSlot(sourceView ? FocusSlot.JSON : FocusSlot.CANVAS);
    }

    // 只读 JSON 的复制入口：整段复制，便于贴到外部编辑器
    private void copySource() {
        Minecraft.getInstance().keyboardHandler.setClipboard(source.text());
    }

    private void toggleHelp() {
        helpOpen = !helpOpen;
        helpItem.setSelected(helpOpen);
        if (helpOpen) {
            editor.endInteractions(UiInputCapture.EndReason.FOCUS_SCOPE_CHANGED);
        }
    }

    /**
     * 注入宿主的历史入口：功能栏按钮与 Ctrl+Z 由此走同一条链路（宿主先处理会话撤回与页面重建，
     * 再由模态的失效守卫决定本页是刷新还是关闭）。未注入时退回本页直接调用会话台账。
     */
    public void setHistoryActions(@Nullable Runnable undo, @Nullable Runnable redo) {
        this.undoAction = undo;
        this.redoAction = redo;
    }

    private void undo() {
        editor.endInteractions(UiInputCapture.EndReason.CANCEL);
        finishParameters();
        Runnable action = undoAction;
        if (action != null) {
            action.run();
            return;
        }
        if (!session.canUndo()) {
            return;
        }
        session.undo();
        historyChanged();
        changed.run();
    }

    private void redo() {
        editor.endInteractions(UiInputCapture.EndReason.CANCEL);
        finishParameters();
        Runnable action = redoAction;
        if (action != null) {
            action.run();
            return;
        }
        if (!session.canRedo()) {
            return;
        }
        session.redo();
        historyChanged();
        changed.run();
    }

    // ---- 参数表单与内联框 ----

    private void finishParameters() {
        RuleValue current = parameterPath == null ? null : session.draft().valueAt(parameterPath);
        RuleValue currentType = current != null && current.isMapping() ? current.child(RuleFields.TYPE) : null;
        if (parameters != null && currentType != null && currentType.isScalar()
                && Objects.equals(parameterType, currentType.asText())
                && parameters.form().commitPendingInputs()) {
            changed.run();
        }
    }

    private void unmountParameters() {
        if (parameters != null) {
            parameters.panel().unmount();
        }
        parameters = null;
        inlineEditor = null;
        parameterPath = null;
        parameterType = null;
        // 参数表单卸载时同步收起画布内联框，避免留下悬空内容
        editor.clearInlineEditor();
    }

    // 选择同步只收起旧表单；点击叶卡片或问题定位才显式打开参数。
    private void syncSelection() {
        syncSelection(false);
    }

    private void syncSelection(boolean openLeaf) {
        String next = editor.selectedPath();
        ConditionNode.Leaf leaf = editableLeaf(editor.selectedNode());
        boolean editable = leaf != null && next != null && (openLeaf || parameters != null
                && Objects.equals(selectedPath, next));
        // 同一选中且参数框状态一致时不重复重建表单
        if (Objects.equals(selectedPath, next) && (parameters != null) == editable) {
            if (openLeaf && inlineEditor != null) editor.setInlineEditor(inlineEditor, editor.tree().selected());
            return;
        }
        finishParameters();
        unmountParameters();
        selectedPath = next;
        if (editable) {
            parameterPath = scope + next.substring(ConditionTreeContract.ROOT_PATH.length()) + "." + RuleFields.CONDITION;
            parameterType = leaf.condition().type().toString();
            parameters = parameterFactory.create(parameterPath, leaf);
            // 参数框关联当前主选中节点；画布负责右侧吸附、滑入动画与裁剪。
            inlineEditor = new ConditionTreeChrome.Inline(parameters.panel(), parameters.form());
            editor.setInlineEditor(inlineEditor, editor.tree().selected());
        } else {
            // 容器节点没有参数、只读叶子与空选中都不展开
            editor.clearInlineEditor();
        }
        layout();
    }

    // 可编辑的条件叶：类型已知且不是只读的不透明条件
    private static @Nullable ConditionNode.Leaf editableLeaf(@Nullable ConditionNode node) {
        return node instanceof ConditionNode.Leaf leaf && leaf.condition() != null
                && !(leaf.condition() instanceof OpaqueCondition) ? leaf : null;
    }

    /** 历史回填读取当前草稿，不把旧表单值重新提交为配置。 */
    public void historyChanged() {
        editor.endInteractions(UiInputCapture.EndReason.UNMOUNTED);
        unmountParameters();
        reloadExpression();
        selectedPath = null;
        syncSelection();
    }

    /** 返回或被卸载时完成合法字段，保留非法字段并结束捕获。 */
    public void unmount() {
        finishParameters();
        discardView();
    }

    // 历史使外层作用域失效时只卸载旧视图，不再提交该路径的控件值。
    public void discardView() {
        editor.endInteractions(UiInputCapture.EndReason.UNMOUNTED);
        unmountParameters();
        pressed = null;
        editor.closePicker();
        editor.setFocused(false);
        source.setFocused(false);
    }

    /**
     * 按草稿路径定位到本树内的节点或其参数字段。
     *
     * <p>路径必须是本作用域下的完整节点前缀，可选再接一段参数字段后缀。节点前缀按结构语法
     * 严格拆解（{@code .terms[i]} / {@code .term}），并用
     * {@link ConditionTreeNodes#jsonPath(ConditionNode, UiTreePath, String)} 回渲染核对，
     * 确保前缀确实对应当前树中的结构；参数字段后缀必须由现役参数表单承载。</p>
     *
     * <p>任一步不成立时都只把焦点交给画布：不选中根、祖先或邻居，不创建参数视图，
     * 也不聚焦失效位置。纯节点定位与未知叶只读展示保持原有语义。</p>
     *
     * @param fieldPath 草稿中的绝对路径
     */
    public void reveal(String fieldPath) {
        sourceView = false;
        source.setFocused(false);
        String[] split = splitNodePath(fieldPath);
        if (split == null) {
            setFocusSlot(FocusSlot.CANVAS);
            return;
        }
        String nodePath = split[0];
        String parameterSuffix = split[1];
        UiTreePath treePath = ConditionTreeNodes.path(nodePath);
        if (treePath == null || !ConditionTreeNodes.jsonPath(editor.expression().root(), treePath,
                ConditionTreeContract.ROOT_PATH).equals(nodePath)) {
            // 结构回渲染与本路径不一致：路径语义与当前树不符，退化为只打开树
            setFocusSlot(FocusSlot.CANVAS);
            return;
        }
        ConditionNode node = editor.tree().query(treePath);
        if (node == null) {
            // 本树中已无该节点（结构已变化）：只打开正确的树，不选祖先或邻居
            setFocusSlot(FocusSlot.CANVAS);
            return;
        }
        if (parameterSuffix == null) {
            // 纯节点定位：结构已确认，直接选中该节点
            editor.selectPath(nodePath);
            syncSelection(true);
            setFocusSlot(FocusSlot.CANVAS);
            return;
        }
        ConditionNode.Leaf leaf = editableLeaf(node);
        if (leaf == null) {
            // 未知/只读叶没有参数表单：不选中节点、不聚焦参数，只打开树
            setFocusSlot(FocusSlot.CANVAS);
            return;
        }
        String parameterPath = parameterPathOf(nodePath);
        // 「.condition」本身指向参数对象：选中节点并展开参数；更深的字段必须由现役表单承载才聚焦
        boolean parameterItself = parameterSuffix.equals("." + RuleFields.CONDITION);
        if (!parameterItself && !parameterFactory.carriesField(parameterPath, leaf, fieldPath)) {
            // 失效或不存在参数字段：只打开树，不选中节点、不创建参数视图
            setFocusSlot(FocusSlot.CANVAS);
            return;
        }
        editor.selectPath(nodePath);
        syncSelection(true);
        setFocusSlot(FocusSlot.CANVAS);
        if (!parameterItself && parameters != null) {
            parameters.panel().focusField(fieldPath);
        }
    }

    // 参数表单在草稿中的绝对基路径：节点路径映射回绝对坐标后接条件对象字段
    private String parameterPathOf(String nodePath) {
        return scope + nodePath.substring(ConditionTreeContract.ROOT_PATH.length()) + "." + RuleFields.CONDITION;
    }

    // 拆出「完整节点前缀 + 可选参数字段后缀」：结构语法不合法或节点后还有尾随字符时返回 null。
    // 后缀以「.condition」开头时才是参数字段，其余未知后缀一律判不合法，避免截成祖先节点
    private @Nullable String[] splitNodePath(String fieldPath) {
        if (fieldPath == null || !(fieldPath.equals(scope) || fieldPath.startsWith(scope + "."))) {
            return null;
        }
        String relative = ConditionTreeContract.ROOT_PATH + fieldPath.substring(scope.length());
        int cursor = ConditionTreeContract.ROOT_PATH.length();
        while (cursor < relative.length() && relative.charAt(cursor) == '.') {
            // 结构段至此结束：其余后缀交给参数判定，不能在这里当成未知结构
            if (relative.startsWith("." + RuleFields.CONDITION, cursor)) {
                break;
            }
            if (relative.startsWith("." + RuleFields.TERMS + "[", cursor)) {
                int close = relative.indexOf(']', cursor);
                if (close < 0 || close == cursor + RuleFields.TERMS.length() + 1) {
                    return null;
                }
                String digits = relative.substring(cursor + RuleFields.TERMS.length() + 2, close);
                try {
                    if (Integer.parseInt(digits) < 0) return null;
                } catch (NumberFormatException invalid) {
                    return null;
                }
                cursor = close + 1;
            } else if (relative.startsWith("." + RuleFields.TERM, cursor)) {
                int after = cursor + 1 + RuleFields.TERM.length();
                if (after >= relative.length()) {
                    cursor = after;
                    break;
                }
                // 必须正好是 term 段：后面是另一个「.」；.termX 这类相似前缀不合法
                if (relative.charAt(after) != '.') {
                    return null;
                }
                cursor = after;
            } else {
                return null;
            }
        }
        if (cursor >= relative.length()) {
            return new String[]{relative, null};
        }
        String suffix = relative.substring(cursor);
        // 参数字段后缀必须以「.condition」开头；其余未知后缀（conditions.garbage）判不合法
        if (!suffix.startsWith("." + RuleFields.CONDITION)) {
            return null;
        }
        return new String[]{relative.substring(0, cursor), suffix};
    }

    // ---- 布局 ----

    @Override public UiRect bounds() { return bounds; }

    @Override public void setBounds(int x, int y, int width, int height) {
        bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        layout();
    }

    private void layout() {
        int statusHeight = Math.min(STATUS_BAR_HEIGHT, bounds.height());
        int barHeight = Math.clamp(bounds.height() - statusHeight, 0, FUNCTION_BAR_HEIGHT);
        int toolWidth = Math.min(TOOL_COLUMN_WIDTH, bounds.width());
        functionBar.setBounds(bounds.x(), bounds.y(), bounds.width(), barHeight);
        // 分区紧贴：功能栏、工具列、状态栏与画布之间不留缝隙，区域边界由 1 像素分隔线表达
        int bodyY = bounds.y() + barHeight;
        int bodyHeight = Math.max(0, bounds.height() - barHeight - statusHeight);
        toolColumn.setBounds(bounds.x(), bodyY, toolWidth, bodyHeight);
        int canvasWidth = Math.max(0, bounds.width() - toolWidth);
        canvasRect = new UiRect(bounds.x() + toolWidth, bodyY, canvasWidth, bodyHeight);
        statusRect = new UiRect(bounds.x(), bounds.y() + bounds.height() - statusHeight, bounds.width(), statusHeight);
        copyJsonRect = sourceView
                ? new UiRect(Math.max(canvasRect.x(), canvasRect.right() - COPY_BUTTON_WIDTH - 2), canvasRect.y() + 2,
                        Math.min(COPY_BUTTON_WIDTH, canvasRect.width()), COPY_BUTTON_HEIGHT)
                : new UiRect(canvasRect.x(), canvasRect.y(), 0, 0);

        editor.setBounds(canvasRect.x(), canvasRect.y(), canvasRect.width(), canvasRect.height());
        source.setBounds(canvasRect.x(), canvasRect.y(), canvasRect.width(), canvasRect.height());
        editor.setVisible(!sourceView);
        source.setVisible(sourceView);

        int jsonRow = sourceView ? UiHostTheme.ICON_VIEW_TREE : UiHostTheme.ICON_VIEW_JSON;
        jsonItem.setIcon(CHROME_THEME.icon(jsonRow, false), CHROME_THEME.icon(jsonRow, true));
        undoItem.setEnabled(session.canUndo());
        redoItem.setEnabled(session.canRedo());
        helpItem.setSelected(helpOpen);

        boolean treeView = !sourceView;
        boolean hasSelection = treeView && !editor.tree().selection().isEmpty();
        for (UiIconStrip.Item item : toolItems) {
            item.setVisible(treeView);
        }
        for (UiIconStrip.Item item : selectionTools) {
            item.setEnabled(hasSelection);
        }
        // 创建类工具的选中态取自各自弹出的浮层：类型列表 / 新建分支 ∧∨ / 包组 ∧∨
        addConditionItem.setSelected(treeView && editor.isPickerOpen());
        branchItem.setSelected(treeView && editor.isGroupKindMenuOpen());
        wrapItem.setSelected(treeView && editor.isWrapKindMenuOpen());
    }

    // ---- 渲染 ----

    @Override
    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        if (parameters != null) {
            parameters.form().tick();
        }
        if (revision != session.revision()) {
            reloadExpression();
        }
        if (parameters != null && !editor.isInlineEditorOpen()) {
            finishParameters();
            unmountParameters();
        }
        if (!sourceView && !editor.isDragging() && !Objects.equals(selectedPath, editor.selectedPath())) {
            syncSelection();
        }
        drawShell(graphics);
        functionBar.render(graphics, renderFont, mouseX, mouseY);
        if (sourceView) {
            source.render(graphics, renderFont, mouseX, mouseY);
            renderCopyButton(graphics);
        } else {
            toolColumn.render(graphics, renderFont, mouseX, mouseY);
            editor.render(graphics, renderFont, mouseX, mouseY);
        }
        drawShellLines(graphics);
        drawGroupDividers(graphics);
        renderStatus(graphics);
        if (helpOpen) {
            renderHelp(graphics);
        }
        Component tip = tooltipAt(mouseX, mouseY);
        if (tip != null) {
            graphics.renderTooltip(renderFont, renderFont.split(tip,
                    Math.clamp(bounds.width(), TOOLTIP_MIN_WIDTH, TOOLTIP_MAX_WIDTH)), mouseX, mouseY);
        }
    }

    // 外壳底板：整幅先铺画布底色，再给功能栏、工具列与状态栏铺与模态承载面同色的底板。
    // 外壳不再各自画一圈独立边框，功能栏底板收窄后，右侧空白直接露出画布底色。
    private void drawShell(GuiGraphics graphics) {
        var palette = CHROME_THEME.palette();
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), palette.canvasFill());
        int plateWidth = functionBarPlateWidth();
        if (plateWidth > 0) {
            graphics.fill(bounds.x(), bounds.y(), bounds.x() + plateWidth, functionBar.bounds().bottom(), palette.panelFill());
        }
        if (toolColumn.bounds().width() > 0 && toolColumn.bounds().height() > 0) {
            graphics.fill(toolColumn.bounds().x(), toolColumn.bounds().y(), toolColumn.bounds().right(),
                    toolColumn.bounds().bottom(), palette.panelFill());
        }
        if (statusRect.height() > 0) {
            graphics.fill(statusRect.x(), statusRect.y(), statusRect.right(), statusRect.bottom(), palette.panelFill());
        }
    }

    // 外壳线条：内容区外围一圈 canvasBorder 外框，加功能栏 / 工具列 / 状态栏与画布之间的 1 像素分隔线。
    // 功能栏与下方区域的分隔线取画布顶框所在行、状态栏上缘的分隔线取画布底框所在行，两条线与画布自身
    // 描边同色，因此各自自左缘到右缘连成一条线（左段止于画布左框，右侧由画布描边续上）。线条画在图标格
    // 之后，以免工具列的格子盖住外框左列，代价是工具列首格的顶行与顶部分隔线共行。
    // 收窄的只是底板绘制范围，格子命中仍按格子矩形判定。
    private void drawShellLines(GuiGraphics graphics) {
        int color = CHROME_THEME.palette().canvasBorder();
        UiFlatSkin.drawOutline(graphics, bounds, color);
        int plateWidth = functionBarPlateWidth();
        if (plateWidth > 0) {
            graphics.fill(bounds.x() + plateWidth - SHELL_LINE, bounds.y(), bounds.x() + plateWidth,
                    functionBar.bounds().bottom(), color);
        }
        int toolWidth = Math.min(TOOL_COLUMN_WIDTH, bounds.width());
        int bodyY = toolColumn.bounds().y();
        if (toolWidth > 0 && bodyY > bounds.y() && bodyY < bounds.bottom()) {
            graphics.fill(bounds.x(), bodyY, bounds.x() + toolWidth, bodyY + SHELL_LINE, color);
            if (statusRect.height() > 0 && statusRect.y() > bounds.y()) {
                graphics.fill(bounds.x(), statusRect.y() - SHELL_LINE, bounds.x() + toolWidth, statusRect.y(), color);
            }
        }
    }

    /**
     * 图标按语义分组：功能栏是「撤销 / 视图 / 帮助」，工具列是「新建 / 结构编辑 / 包组」。
     * 组间画 1 像素半透明分隔线，一排图标不再是一片没有结构的方格；线画在格子上，只做提示，
     * 不参与命中判定。
     */
    private void drawGroupDividers(GuiGraphics graphics) {
        int color = CHROME_THEME.palette().chromeDivider();
        drawVerticalDividers(graphics, functionItems, new int[] {2, 3}, color);
        drawHorizontalDividers(graphics, toolItems, new int[] {2, 6}, color);
    }

    // 组边界取该组首格的上/左边缘，短线两端各留 3 像素，避免与外壳描边粘连
    private static void drawVerticalDividers(GuiGraphics graphics, List<UiIconStrip.Item> items,
                                             int[] boundaryIndices, int color) {
        for (int index : boundaryIndices) {
            if (index <= 0 || index >= items.size()) {
                continue;
            }
            UiRect rect = items.get(index).bounds();
            if (rect.height() <= 0) {
                continue;
            }
            graphics.fill(rect.x(), rect.y() + 3, rect.x() + SHELL_LINE, rect.bottom() - 3, color);
        }
    }

    private static void drawHorizontalDividers(GuiGraphics graphics, List<UiIconStrip.Item> items,
                                               int[] boundaryIndices, int color) {
        for (int index : boundaryIndices) {
            if (index <= 0 || index >= items.size()) {
                continue;
            }
            UiRect rect = items.get(index).bounds();
            if (rect.width() <= 0) {
                continue;
            }
            graphics.fill(rect.x() + 3, rect.y(), rect.right() - 3, rect.y() + SHELL_LINE, color);
        }
    }

    // 功能栏底板宽度：量到最后一个图标格的右缘再加 1 像素收尾线，右侧剩余区域交还画布
    private int functionBarPlateWidth() {
        int cellsWidth = UiIconStrip.cellsWidth(functionItems);
        if (cellsWidth <= 0) {
            return 0;
        }
        return Math.min(functionBar.bounds().width(), cellsWidth + SHELL_LINE);
    }

    private void renderCopyButton(GuiGraphics graphics) {
        if (copyJsonRect.width() <= 0 || copyJsonRect.height() <= 0) {
            return;
        }
        CHROME_THEME.drawSurface(graphics, copyJsonRect, CHROME_THEME.palette().panelControlFill(), CHROME_THEME.palette().chromeBorder());
        Component label = Component.translatable(UI + "button.copy_json");
        graphics.drawString(font, TextScroll.trimToWidth(font, label.getString(), Math.max(0, copyJsonRect.width() - 4)),
                copyJsonRect.x() + 3, copyJsonRect.y() + (copyJsonRect.height() - font.lineHeight) / 2,
                CHROME_THEME.palette().panelText(), false);
    }

    private void renderStatus(GuiGraphics graphics) {
        if (statusRect.height() <= 0) {
            return;
        }
        int textY = statusRect.y() + (statusRect.height() - font.lineHeight) / 2;
        Component right = statusRight();
        int rightWidth = font.width(right.getString());
        Component status = statusText();
        graphics.drawString(font, TextScroll.trimToWidth(font, status.getString(),
                        Math.max(0, statusRect.width() - rightWidth - STATUS_TEXT_PAD * 3)),
                statusRect.x() + STATUS_TEXT_PAD, textY, statusColor(), false);
        // 状态正文与右侧缩放比例之间加一道组线，两段信息不再连成一片
        int dividerX = statusRect.right() - rightWidth - STATUS_TEXT_PAD * 2;
        graphics.fill(dividerX, statusRect.y() + 3, dividerX + SHELL_LINE, statusRect.bottom() - 3,
                CHROME_THEME.palette().chromeDivider());
        graphics.drawString(font, right.getString(), statusRect.right() - rightWidth - STATUS_TEXT_PAD, textY,
                CHROME_THEME.palette().statusTextMuted(), false);
    }

    /**
     * 帮助浮层：标题下逐条绘制「两字动作标签 + 说明」。面板宽度按正文测量后夹在最小宽度与画布
     * 可用宽度之间；说明列的可用宽度先按内宽扣掉动作标签列算出，文本按其折行，因此每行都不会
     * 超出面板内宽，也就不需要任何裁剪。
     */
    private void renderHelp(GuiGraphics graphics) {
        List<HelpEntry> entries = helpEntries();
        // 动作标签列宽取最宽标签：说明列左边缘由此对齐成一条竖线
        int labelWidth = 0;
        for (HelpEntry entry : entries) {
            labelWidth = Math.max(labelWidth, font.width(entry.label().getString()));
        }
        int available = Math.clamp(canvasRect.width() - 8, 1, HELP_WIDTH);
        int textLimit = Math.max(1, available - HELP_PADDING * 2 - labelWidth - HELP_LABEL_GAP);
        List<List<String>> bodies = new ArrayList<>(entries.size());
        int contentWidth = font.width(Component.translatable(UI + "help.title").getString());
        int lineCount = 1;
        for (HelpEntry entry : entries) {
            List<String> lines = wrapText(entry.text().getString(), textLimit);
            bodies.add(lines);
            lineCount += lines.size();
            for (String line : lines) {
                contentWidth = Math.max(contentWidth, labelWidth + HELP_LABEL_GAP + font.width(line));
            }
        }
        int width = Math.clamp(contentWidth + HELP_PADDING * 2, Math.min(HELP_MIN_WIDTH, available), available);
        int lineStep = font.lineHeight + HELP_LINE_GAP;
        int height = HELP_PADDING * 2 + lineCount * lineStep - HELP_LINE_GAP;
        int x = canvasRect.x() + Math.max(0, (canvasRect.width() - width) / 2);
        int y = canvasRect.y() + Math.max(0, (canvasRect.height() - height) / 2);
        CHROME_THEME.drawHelp(graphics, new UiRect(x, y, width, height));
        int textY = y + HELP_PADDING;
        graphics.drawString(font, Component.translatable(UI + "help.title").getString(), x + HELP_PADDING, textY,
                CHROME_THEME.palette().helpText(), false);
        textY += lineStep;
        for (int index = 0; index < entries.size(); index++) {
            List<String> lines = bodies.get(index);
            for (int row = 0; row < lines.size(); row++) {
                if (row == 0) {
                    graphics.drawString(font, entries.get(index).label().getString(), x + HELP_PADDING, textY,
                            CHROME_THEME.palette().helpText(), false);
                }
                graphics.drawString(font, lines.get(row), x + HELP_PADDING + labelWidth + HELP_LABEL_GAP, textY,
                        CHROME_THEME.palette().helpTextMuted(), false);
                textY += lineStep;
            }
        }
    }

    /**
     * 把说明文本按可用宽度折成若干行：先按空白断行，单个超长词（长 CJK 串或长标识符）
     * 再按字符断行，保证每行宽度都不超过 maxWidth，且至少产出一行。
     *
     * @param text     说明文本
     * @param maxWidth 单行允许的最大像素宽度
     * @return 自顶向下的行文本，元素个数至少为 1
     */
    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String token : text.split(" ")) {
            if (token.isEmpty()) {
                continue;
            }
            if (!current.isEmpty() && font.width(current + " " + token) > maxWidth) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (current.isEmpty() && font.width(token) > maxWidth) {
                for (int index = 0; index < token.length(); index++) {
                    current.append(token.charAt(index));
                    if (font.width(current.toString()) > maxWidth && current.length() > 1) {
                        current.setLength(current.length() - 1);
                        lines.add(current.toString());
                        current.setLength(0);
                        current.append(token.charAt(index));
                    }
                }
                continue;
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(token);
        }
        if (!current.isEmpty() || lines.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    // 帮助正文条目：按玩家上手的动作顺序排列，每条以两字动作标签开头
    private List<HelpEntry> helpEntries() {
        return List.of(
                helpEntry("add"),
                helpEntry("edit"),
                helpEntry("select"),
                helpEntry("move"),
                helpEntry("view"),
                helpEntry("pan"),
                helpEntry("close"));
    }

    /** 帮助条目：动作标签取 {@code help.action.*}，说明取 {@code help.*}，两者按动作名配对。 */
    private HelpEntry helpEntry(String action) {
        return new HelpEntry(Component.translatable(UI + "help.action." + action),
                Component.translatable(UI + "help." + action));
    }

    /** 帮助浮层的单个条目：左侧动作标签、右侧按宽度折行后的说明。 */
    private record HelpEntry(Component label, Component text) { }

    // 状态栏左侧诊断：解码错误 > 手势提示 > 问题定位 > 未完成输入 > 选择与统计
    private Component statusText() {
        Component status = decodeError != null ? decodeError : editor.operationError();
        if (status == null && sourceView && session.hasPendingInput(scope)) {
            status = Component.translatable(UI + "pending_source");
        }
        if (status == null && editor.isDragging()) {
            status = editor.hint();
        }
        if (status == null && !editor.issues().isEmpty()) {
            status = Component.translatable(UI + "issues", editor.issues().size(), editor.issues().getFirst().label());
        }
        if (status == null && session.hasPendingInput(scope)) {
            status = Component.translatable(UI + "pending_input");
        }
        if (status == null && sourceView && source.text().isEmpty()) {
            status = Component.translatable(UI + "source_empty");
        }
        if (status == null && !sourceView && editor.tree().selection().size() > 1) {
            status = Component.translatable(UI + "selection", editor.tree().selection().size());
        }
        return status == null ? Component.translatable(UI + "counts", editor.nodeCount(), editor.leafCount(), editor.depth()) : status;
    }

    // 状态栏右侧：当前视图与缩放档位
    private Component statusRight() {
        return sourceView ? Component.translatable(UI + "status.json")
                : Component.translatable(UI + "status.zoom", editor.scalePercent());
    }

    private int statusColor() {
        if (decodeError != null || editor.operationError() != null || !editor.isValid()) {
            return CHROME_THEME.palette().statusTextDanger();
        }
        return editor.issues().isEmpty() ? CHROME_THEME.palette().statusText() : CHROME_THEME.palette().statusTextWarning();
    }

    private @Nullable Component tooltipAt(int mouseX, int mouseY) {
        if (helpOpen) {
            return null;
        }
        Component tip = null;
        if (functionBar.bounds().contains(mouseX, mouseY)) {
            tip = functionBar.tooltipAt(mouseX, mouseY);
        } else if (sourceView && copyJsonRect.contains(mouseX, mouseY)) {
            tip = Component.translatable(UI + "button.copy_json");
        } else if (!sourceView && toolColumn.bounds().contains(mouseX, mouseY)) {
            tip = toolColumn.tooltipAt(mouseX, mouseY);
        }
        // 参数框的字段提示由适配器在 GUI 屏幕坐标下缓存，类型列表打开时不显示旧表单提示。
        if (tip == null && inlineEditor != null && !editor.hasPopup()) {
            tip = inlineEditor.hoverTooltip();
        }
        return tip;
    }

    // ---- 输入 ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        pressed = null;
        if (helpOpen) {
            // 帮助浮层是模态式的：任何点击先关闭它
            helpOpen = false;
            return true;
        }
        if (!bounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (!sourceView && editor.hasPopup()) {
            setFocusSlot(FocusSlot.CANVAS);
            if (editor.mouseClicked(mouseX, mouseY, button)) pressed = editor;
            return true;
        }
        if (statusRect.contains(mouseX, mouseY) && !editor.issues().isEmpty()) {
            sourceView = false;
            layout();
            setFocusSlot(FocusSlot.CANVAS);
            editor.selectPath(editor.issues().getFirst().path());
            syncSelection(true);
            return true;
        }
        if (functionBar.bounds().contains(mouseX, mouseY)) setFocusSlot(FocusSlot.FUNCTION_BAR);
        if (functionBar.mouseClicked(mouseX, mouseY, button)) {
            pressed = functionBar;
            return true;
        }
        if (sourceView) {
            if (copyJsonRect.contains(mouseX, mouseY)) {
                copySource();
                return true;
            }
            setFocusSlot(FocusSlot.JSON);
            if (!source.mouseClicked(mouseX, mouseY, button)) {
                return false;
            }
            pressed = source;
            return true;
        }
        if (toolColumn.bounds().contains(mouseX, mouseY)) setFocusSlot(FocusSlot.TOOL_COLUMN);
        if (toolColumn.mouseClicked(mouseX, mouseY, button)) {
            pressed = toolColumn;
            return true;
        }
        if (canvasRect.contains(mouseX, mouseY)) setFocusSlot(FocusSlot.CANVAS);
        if (editor.mouseClicked(mouseX, mouseY, button)) {
            pressed = editor;
            syncSelection();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        UiWidget target = pressed;
        pressed = null;
        boolean consumed = target != null && target.mouseReleased(mouseX, mouseY, button);
        if (target == toolColumn && (editor.isPickerOpen() || editor.isGroupKindMenuOpen() || editor.isWrapKindMenuOpen())) {
            setFocusSlot(FocusSlot.CANVAS);
        }
        if (target == editor) syncSelection();
        if (target != null) layout();
        return consumed;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return pressed != null && pressed.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (sourceView) {
            return source.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return editor.isVisible() && editor.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (helpOpen) {
            // 帮助浮层期间吞掉其它按键，避免误操作画布
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                helpOpen = false;
            }
            return true;
        }
        if (!sourceView && editor.hasPopup()) {
            boolean handled = editor.keyPressed(keyCode, scanCode, modifiers);
            // 键盘关闭浮层后工具格选中态要跟着复位；鼠标路径由 mouseReleased 收尾的 layout() 负责
            if (!editor.hasPopup()) {
                layout();
            }
            return handled;
        }
        // 内联框打开时外壳快捷键让位：先给内联框（经画布），再给画布，最后才是外壳
        boolean inlineOpen = !sourceView && editor.isInlineEditorOpen();
        if (inlineOpen && editor.keyPressed(keyCode, scanCode, modifiers)) {
            syncSelection();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            cycleFocus((modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
            return true;
        }
        return switch (focusSlot) {
            case FUNCTION_BAR -> activateOrNavigate(functionBar, keyCode, scanCode, modifiers);
            case TOOL_COLUMN -> activateOrNavigate(toolColumn, keyCode, scanCode, modifiers);
            case JSON -> source.keyPressed(keyCode, scanCode, modifiers);
            case CANVAS -> {
                // 内联框已经拿过本次按键，避免重复派发
                if (inlineOpen) {
                    yield false;
                }
                if (editor.isVisible() && editor.keyPressed(keyCode, scanCode, modifiers)) {
                    syncSelection();
                    yield true;
                }
                yield false;
            }
        };
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (sourceView) {
            return source.keyReleased(keyCode, scanCode, modifiers);
        }
        return editor.isVisible() && editor.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (sourceView) {
            return source.charTyped(codePoint, modifiers);
        }
        // 内联框打开时字符输入先经过画布，画布再转给内联框
        if (editor.isVisible() && (editor.isInlineEditorOpen() || editor.isPickerOpen())) {
            return editor.charTyped(codePoint, modifiers);
        }
        return editor.isVisible() && editor.isFocused() && editor.charTyped(codePoint, modifiers);
    }

    // 图标条：方向键由 kit 处理，Enter / Space 由外壳转发到 activate
    private static boolean activateOrNavigate(UiWidget widget, int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
            return ((UiFocusTarget) widget).activate();
        }
        return widget.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- 焦点 ----

    @Override public boolean canFocus() { return true; }

    @Override public boolean isFocused() { return focused; }

    @Override public void setFocused(boolean value) {
        focused = value;
        applyFocus();
    }

    @Override public boolean activate() { return false; }

    @Override public Component accessibleName() { return Component.translatable(UI + "title"); }

    private void setFocusSlot(FocusSlot slot) {
        focusSlot = slot;
        applyFocus();
    }

    private void applyFocus() {
        functionBar.setFocused(focused && focusSlot == FocusSlot.FUNCTION_BAR);
        toolColumn.setFocused(focused && focusSlot == FocusSlot.TOOL_COLUMN && !sourceView);
        source.setFocused(focused && focusSlot == FocusSlot.JSON && sourceView);
        editor.setFocused(focused && focusSlot == FocusSlot.CANVAS && editor.isVisible());
    }

    private void cycleFocus(boolean backwards) {
        List<FocusSlot> order = focusOrder();
        if (order.isEmpty()) {
            return;
        }
        int index = order.indexOf(focusSlot);
        int next = index < 0 ? (backwards ? order.size() - 1 : 0)
                : Math.floorMod(index + (backwards ? -1 : 1), order.size());
        setFocusSlot(order.get(next));
    }

    // 只列出当前可见且可聚焦的区域，隐藏的 JSON 视图不进 Tab 序列
    private List<FocusSlot> focusOrder() {
        List<FocusSlot> order = new ArrayList<>();
        if (functionBar.canFocus()) {
            order.add(FocusSlot.FUNCTION_BAR);
        }
        if (sourceView) {
            order.add(FocusSlot.JSON);
        } else {
            if (toolColumn.canFocus()) {
                order.add(FocusSlot.TOOL_COLUMN);
            }
            if (editor.canFocus()) {
                order.add(FocusSlot.CANVAS);
            }
        }
        return order;
    }

}
