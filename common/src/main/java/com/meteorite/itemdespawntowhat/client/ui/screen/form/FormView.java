package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.DraftPaths;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorFieldType;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.shared.FieldCatalogTypes;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 描述符驱动的表单视图：把 TypeEditorDescriptor 的字段列表渲染成可滚动的一列控件。
 * <p>所有写入都经由 EditSession（统一撤销门面）落到 RuleDraft，控件本身不直接改草稿；
 * 界面不出现任何可编辑的 JSON 文本框，未识别字段原样保留。
 */
public final class FormView implements UiWidget, UiLayoutSource {

    // 宿主默认主题：屏幕从主题入口取样式后注入本类装配的控件
    private static final UiHostTheme THEME = UiHostThemes.defaultTheme();

    // 行间距
    private static final int ROW_GAP = 2;
    // 滚动条宽度
    private static final int SCROLLBAR_WIDTH = 4;

    // 定位高亮持续帧数（约 3 秒）
    private static final int HIGHLIGHT_TICKS = 60;

    private final Font font;
    final EditSession session;
    // 草稿装载与回写集中在 FormDraftWriter
    private final FormDraftWriter draftWriter = new FormDraftWriter(this);
    // 输入分发集中在 FormInputRouter
    private final FormInputRouter inputRouter = new FormInputRouter(this);
    String basePath;
    long loadedRevision;
    final List<FormRow> rows = new ArrayList<>();
    private @Nullable SelectionCatalogOpener catalogOpener;
    // 字段名 → 目录条目可见范围：只影响该字段那一次目录浏览，不改字段值
    private final Map<String, Predicate<String>> catalogFilters = new HashMap<>();

    /** 字段目录入口由页面提供，表单传递确切字段路径与当前选择，不访问网络。 */
    @FunctionalInterface
    public interface SelectionCatalogOpener {
        /**
         * 打开目录，确认后返回完整选择快照。
         * @param field 字段描述符
         * @param path 字段在草稿中的确切路径
         * @param tags 是否只浏览标签
         * @param selected 当前字段中的引用
         * @param onPicked 确认后的完整选择回调
         */
        void open(EditorField field, String path, boolean tags, List<String> selected, Consumer<List<String>> onPicked);
    }

    /**
     * 设置返回完整选择快照的目录入口。
     * @param opener 目录入口，null 表示移除入口
     */
    public void setSelectionCatalogOpener(@Nullable SelectionCatalogOpener opener) {
        catalogOpener = opener;
        for (FormRow row : rows) { row.pickers.clear(); addPickers(row); }
    }

    /**
     * 设置某个字段的目录条目可见范围：目录弹窗只列出过滤器接受的资源引用。
     * <p>范围只影响浏览，不改变字段值与已有选择；标签条目无法判定成员，由过滤器自行决定是否保留。
     * @param fieldName 字段名（如实体字段 entity）
     * @param filter 条目过滤器，null 表示不限
     */
    public void setCatalogFilter(String fieldName, @Nullable Predicate<String> filter) {
        if (filter == null) {
            catalogFilters.remove(fieldName);
        } else {
            catalogFilters.put(fieldName, filter);
        }
    }

    /**
     * 读取某个字段的目录条目可见范围。
     * @param fieldName 字段名
     * @return 过滤器，未设置时返回 null
     */
    public @Nullable Predicate<String> catalogFilter(String fieldName) {
        return catalogFilters.get(fieldName);
    }
    private @Nullable ConditionEditingSupport conditionEditingSupport;
    private @Nullable ConditionEditContextFactory conditionContextFactory;
    private @Nullable Runnable changeListener;
    @Nullable BiConsumer<String, RuleValue> fieldWritten;

    /** 关联字段在同一次撤销操作内同步；第二个参数为写入前的值。 */
    public void setOnFieldWritten(BiConsumer<String, RuleValue> listener) {
        fieldWritten = listener;
    }
    // 输入被上限拒绝时的即时提示出口（页面显示 notice）
    private @Nullable Consumer<Component> rejectedNoticeHandler;
    // 本次按下命中的控件：释放与拖动只派发给它，避免无关控件吞掉释放或残留按下态
    @Nullable UiWidget pressedWidget;
    @Nullable FormControl pressedControl;

    private int labelWidth = 88;
    private int rowGap = ROW_GAP;
    private int minimumControlHeight;
    private boolean catalogSelections;
    private int viewportX;
    private int viewportY;
    private int viewportWidth;
    int viewportHeight;
    int scrollOffset;
    private int contentHeight;
    boolean visible = true;
    boolean enabled = true;
    // 子列表里的子表单：只由自己的渲染帧驱动，顶层表单由页面 tick 统一驱动
    private boolean childForm;
    private @Nullable String highlightPath;
    private int highlightTicks;
    // 行几何测量缓存：只有宽度、行结构、可见性或控件高度等真实几何输入变化后重算，
    // 静止帧与滚动帧直接复用上一次测量结果，避免把测量绑到帧循环上。
    private boolean measureDirty = true;
    private int measuredWidth = -1;
    private int measuredContentHeight;
    // 屏幕同步缓存：内容原点未变且测量未失效时跳过整表 setBounds，静止帧零逐行同步开销。
    private int syncedOriginX = Integer.MIN_VALUE;
    private int syncedOriginY = Integer.MIN_VALUE;
    // 几何变化出口：页面据此让自己的布局缓存失效，滚动帧因此不必重排页面。
    private @Nullable Runnable geometryListener;
    // 高亮框缓存：随同步一起重算，渲染帧不再分配矩形。
    private @Nullable UiRect highlightBounds;

    public FormView(Font font, EditSession session, String basePath) {
        this.font = font;
        this.session = session;
        this.basePath = basePath == null ? "" : basePath;
    }

    /** 用描述符整体替换字段列表 */
    public void setDescriptor(TypeEditorDescriptor descriptor) {
        rows.clear();
        for (EditorField field : descriptor.fields()) {
            addField(field);
        }
    }

    /** 追加一个字段 */
    public void addField(EditorField field) {
        var catalogType = FieldCatalogTypes.catalogTypeOf(field);
        boolean iconSelection = catalogSelections && catalogType != null && isCatalogField(field);
        FormRow row = new FormRow(field, pathFor(field), iconSelection
                ? new CatalogSelectionControl(font, field, this::notifyChanged)
                : FormControl.create(font, this, field, this::notifyChanged));
        row.control.setMinimumHeight(minimumControlHeight);
        // 控件自身高度变化（如条件树摘要行数）经这一出口让行测量与页面布局失效
        row.control.setOnGeometryChanged(this::invalidateMeasure);
        row.control.setRejectedNotice(message -> {
            if (rejectedNoticeHandler != null) {
                rejectedNoticeHandler.accept(message);
            }
        });
        addPickers(row);
        rows.add(row);
        invalidateMeasure();
    }

    /** 读取某字段控件当前的未提交值（宿主把缓冲并入自己的编辑步时使用）；字段不存在返回 null */
    public @Nullable RuleValue pendingValue(String path) {
        for (FormRow row : rows) {
            if (row.path.equals(path)) {
                return row.control.store();
            }
        }
        return null;
    }

    // 草稿写入通知：第二个参数为写入前的值（null 表示原先没有该字段）
    void notifyFieldWritten(String path, @Nullable RuleValue before) {
        if (fieldWritten != null) {
            fieldWritten.accept(path, before);
        }
    }

    /** 给某个字段行挂行尾操作按钮（例如「恢复自动命名」）；宽度按文本自适应 */
    public void setRowAction(String path, UiButton button) {
        for (FormRow row : rows) {
            if (row.path.equals(path)) {
                row.actions.clear();
                row.actions.add(button);
                row.refreshButtons();
                invalidateMeasure();
                break;
            }
        }
    }

    /** 追加一个行尾操作按钮：同一行可有多个动作，例如基本页的「本服开关」与「恢复默认」 */
    public void addRowAction(String path, UiButton button) {
        for (FormRow row : rows) {
            if (row.path.equals(path)) {
                row.actions.add(button);
                row.refreshButtons();
                invalidateMeasure();
                break;
            }
        }
    }

    /** 覆盖某字段行的控件高度（{@code <= 0} 恢复默认）；例如基本页加高的优先级控件 */
    public void setRowHeight(String path, int height) {
        for (FormRow row : rows) {
            if (row.path.equals(path)) {
                row.control.setHeightOverride(height);
                invalidateMeasure();
            }
        }
    }

    /** 卡片标题已表达字段含义时省去重复标签。 */
    public void hideRowLabel(String path) {
        for (FormRow row : rows) if (row.path.equals(path)) {
            row.labelVisible = false;
            invalidateMeasure();
        }
    }

    /** 隐藏行退出键盘导航与命中，但保存原有字段内容。 */
    public void setRowVisible(String path, boolean visible) {
        for (FormRow row : rows) if (row.path.equals(path)) {
            if (row.visible == visible) return;
            row.visible = visible;
            if (!visible) {
                row.control.endInteractions(UiInputCapture.EndReason.HIDDEN);
                List<UiFocusTarget> targets = new ArrayList<>();
                row.control.addFocusTargets(targets);
                targets.forEach(target -> target.setFocused(false));
            }
            invalidateMeasure();
        }
    }

    /**
     * 将两个物品数量字段按物品逐项交错排布，上行门槛、下行实际消耗；字段写入与撤销仍各自独立。
     * @param upperPath 上行数量字段路径
     * @param lowerPath 下行数量字段路径
     */
    public void pairItemCountRows(String upperPath, String lowerPath) {
        FormRow upper = rows.stream().filter(row -> row.path.equals(upperPath)).findFirst().orElse(null);
        FormRow lower = rows.stream().filter(row -> row.path.equals(lowerPath)).findFirst().orElse(null);
        if (upper != null && lower != null && upper.control instanceof ItemCountsControl
                && lower.control instanceof ItemCountsControl) {
            upper.pairedBelow = lower;
            lower.pairedAbove = upper;
            invalidateMeasure();
        }
    }

    /** 模式相关字段随当前输入和历史回填显示；隐藏行保留原有值与文本缓冲。 */
    public void setRowVisibility(String path, BooleanSupplier visibility) {
        for (FormRow row : rows) if (row.path.equals(path)) row.visibility = visibility;
        refreshRowVisibility();
    }

    void refreshRowVisibility() {
        boolean changed = false;
        for (FormRow row : rows) if (row.visibility != null) {
            boolean next = row.visibility.getAsBoolean();
            if (row.visible != next) {
                setRowVisible(row.path, next);
                row.wasEditing = false;
                changed = true;
            }
        }
        if (changed) {
            invalidateMeasure();
            layout(viewportX, viewportY, viewportWidth, viewportHeight);
        }
    }

    /** 启用开关等含义直观的行不弹出提示。 */
    public void suppressRowTooltip(String path) {
        for (FormRow row : rows) if (row.path.equals(path)) row.tooltipEnabled = false;
    }

    /**
     * 关闭该行的 hint 兜底提示，保留校验问题、控件提示与文本/标签被裁剪时的提示。
     *
     * <p>用于提示文案已经作为占位符写在文本框内的字段，避免悬停时重复弹出同一段文字。</p>
     */
    public void suppressRowHintTooltip(String path) {
        for (FormRow row : rows) if (row.path.equals(path)) row.hintTooltipEnabled = false;
    }

    /** 动态默认值只用于回填，未编辑时仍保留原字段的省略状态。 */
    public void setRowDefaultValue(String path, Supplier<RuleValue> value) {
        for (FormRow row : rows) if (row.path.equals(path)) row.defaultValue = value;
        reload();
    }

    /**
     * 设置数值字段的常用滑条窗口，不修改合法域、草稿值或正在输入的缓冲。
     * @param path 绝对字段路径
     * @param min 窗口下限
     * @param max 窗口上限
     */
    public void setRowSliderWindow(String path, double min, double max) {
        for (FormRow row : rows) if (row.path.equals(path) && row.control instanceof NumberControl number) {
            number.setWindow(min, max);
        }
    }

    /** 数量对象按引用身份展示；对象键无需进入草稿的点分路径语法。 */
    public void setItemCountReferences(String path, List<String> references) {
        for (FormRow row : rows) if (row.path.equals(path) && row.control instanceof ItemCountsControl counts) {
            counts.setReferences(references);
            invalidateMeasure();
        }
    }

    /** 卡片编辑页与条件树参数框启用，弹窗等其它宿主保留原有密度。 */
    public void useCardSpacing() {
        minimumControlHeight = FormControl.CARD_CONTROL_HEIGHT;
        rowGap = 6;
        for (FormRow row : rows) row.control.setMinimumHeight(minimumControlHeight);
        invalidateMeasure();
    }

    /** 在装配字段之前启用；输入页统一使用目录添加物品和标签。 */
    public void useCatalogSelections() { catalogSelections = true; }

    /** 基本页以紧凑密度显示，不重复给可选值添加默认前缀。 */
    public void useCompactSpacing() {
        minimumControlHeight = 18;
        rowGap = 4;
        labelWidth = 64;
        for (FormRow row : rows) row.control.setMinimumHeight(minimumControlHeight);
        invalidateMeasure();
    }

    /** 输入被上限拒绝时的即时提示出口 */
    public void setOnRejectedNotice(@Nullable Consumer<Component> handler) {
        this.rejectedNoticeHandler = handler;
    }

    // 支持叶子面板在描述符载入后挂接目录入口。
    private void addPickers(FormRow row) {
        if (catalogOpener != null && FieldCatalogTypes.catalogTypeOf(row.field) != null
                && isCatalogField(row.field)) {
            row.pickers.add(new UiButton(THEME, font,
                    Component.translatable(TypeLabels.UI_PREFIX + "pick." + (catalogSelections ? "add" : "open")),
                    UiButtonVariant.SECONDARY, () -> pick(row, false)).setChipStyle(true));
            if (!catalogSelections && (row.field.type() == EditorFieldType.TAG || row.field.type() == EditorFieldType.TAG_LIST)) row.pickers.add(
                    new UiButton(THEME, font,
                            Component.translatable(TypeLabels.UI_PREFIX + "pick.tags"),
                            UiButtonVariant.SECONDARY, () -> pick(row, true)).setChipStyle(true));
        }
        row.refreshButtons();
    }

    private void pick(FormRow row, boolean tags) {
        if (!enabled || catalogOpener == null) return;
        commitPendingInputs();
        RuleValue existing = session.draft().valueAt(row.path);
        List<String> selected = existing == null ? List.of()
                : existing.isList() ? existing.textItems() : existing.isText() ? List.of(existing.asText()) : List.of();
        catalogOpener.open(row.field, row.path, tags, selected, ids -> {
            if (!enabled) return;
            RuleValue value;
            if (row.field.type() == EditorFieldType.TAG_LIST || row.field.type() == EditorFieldType.RL_LIST) {
                // 文本引用由完整选择替换；未知元素保留，避免目录编辑破坏扩展数据。
                RuleValue.Sequence array = RuleValue.list();
                LinkedHashSet<String> remaining = new LinkedHashSet<>(ids);
                if (existing != null && existing.isList()) for (RuleValue element : existing.items()) {
                    if (!element.isText() || remaining.remove(element.asText())) array.add(element.deepCopy());
                }
                remaining.forEach(id -> array.add(RuleValue.text(id)));
                value = array.build();
            } else {
                if (ids.isEmpty()) return;
                value = RuleValue.text(ids.getFirst());
            }
            writeField(row, value);
            reload(); notifyChanged();
        });
    }

    private static boolean isCatalogField(EditorField field) {
        return switch (field.type()) {
            case TAG, TAG_LIST, RESOURCE_LOCATION, REGISTRY_ID, RL_LIST -> true;
            default -> false;
        };
    }

    // 字段在草稿中的路径：原始 JSON 行与说明行指向所在对象本身
    private String pathFor(EditorField field) {
        if (field.type() == EditorFieldType.RAW_JSON || field.type() == EditorFieldType.NOTE) {
            return basePath;
        }
        return childPath(field.name());
    }

    void changeChildStructure(String relative, Runnable change, Supplier<RuleValue> value) {
        commitPendingInputs();
        session.apply(EditSession.OP_SET_FIELD, () -> {
            change.run();
            session.draft().setAt(childPath(relative), value.get());
        });
        loadedRevision = session.revision();
        for (FormRow row : rows) if (row.field.name().equals(relative)) acceptInput(row);
        invalidateMeasure();
    }

    void clearChildInputs(String relative) { session.clearPendingInputs(childPath(relative)); }

    // 重排嵌套表单时同步输入路径，不把下标变化绑定到另一个子项。
    void rebase(String nextBase) {
        retainPendingInputs();
        session.remapPendingInputs(basePath, nextBase);
        basePath = nextBase;
        for (FormRow row : rows) row.path = pathFor(row.field);
    }

    // 拼接子路径
    String childPath(String relative) {
        return DraftPaths.child(basePath, relative);
    }

    /** 条件树返回相对路径，宿主编辑叶参数时需定位到本表单所在的规则或效果。 */
    public String draftPath(String relative) {
        return childPath(relative);
    }

    /**
     * 装配条件编辑的共享定义能力（类型清单、按场景创建、解码注册表）。
     *
     * @param support 共享定义能力，null 时条件树控件只读回退
     */
    public void setConditionEditingSupport(@Nullable ConditionEditingSupport support) {
        this.conditionEditingSupport = support;
    }

    /**
     * 装配条件编辑上下文的工厂：按实际字段路径与所属对象路径创建局部目标上下文。
     *
     * @param factory 上下文工厂，null 时不提供条件画布入口
     */
    public void setConditionContextFactory(@Nullable ConditionEditContextFactory factory) {
        this.conditionContextFactory = factory;
    }

    // 条件树控件所用的共享定义能力（可为空，为空时条件字段只读回退）
    @Nullable ConditionEditingSupport conditionEditingSupport() {
        return conditionEditingSupport;
    }

    // 为实际条件字段创建局部上下文：字段路径取自与本行相同的 pathFor，所属对象由本表单基路径显式给出
    @Nullable ConditionEditContext conditionContext(EditorField field) {
        if (conditionContextFactory == null) return null;
        return conditionContextFactory.create(pathFor(field), basePath);
    }

    public void setLabelWidth(int width) {
        this.labelWidth = Math.max(0, width);
        invalidateMeasure();
    }

    public void setOnChanged(Runnable listener) {
        this.changeListener = listener;
    }

    // 完成的滑条、开关和选择操作立即写入；聚焦文本仍只保留缓冲。
    void notifyChanged() {
        invalidateMeasure();
        refreshRowVisibility();
        retainPendingInputs();
        applyToDraft();
        if (changeListener != null) {
            changeListener.run();
        }
    }

    // ---- 草稿写入薄封装：实现集中在 FormDraftWriter ----

    public void reload() {
        draftWriter.reload();
        invalidateMeasure();
        refreshRowVisibility();
        retainPendingInputs();
    }

    public void reloadWith(RuleValue object) {
        draftWriter.reloadWith(object);
        invalidateMeasure();
        refreshRowVisibility();
        retainPendingInputs();
    }

    /** 保存会话内输入，不改变规则 JSON 或配置历史。 */
    public void retainPendingInputs() {
        draftWriter.retainPendingInputs();
    }

    /** 完成当前字段输入；只写合法值，非法文本留在会话缓冲。 */
    public boolean commitPendingInputs() {
        return draftWriter.applyToDraft(true);
    }

    /** 把控件值写回草稿；逐字段比较，只有真正变化才记入撤销历史 */
    public boolean applyToDraft() {
        return draftWriter.applyToDraft(false);
    }

    // 输入分发桥接：字段提交与最近输入接受
    void acceptInput(FormRow row) {
        draftWriter.acceptInput(row);
    }

    void writeField(FormRow row, @Nullable RuleValue next) {
        draftWriter.writeField(row, next);
    }

    // 布局/滚动薄封装：状态在本类，几何计算集中在 FormRowLayout

    private int layoutRows(int availableWidth) {
        return FormRowLayout.layoutRows(rows, availableWidth, labelWidth, rowGap, minimumControlHeight, font, THEME);
    }

    /**
     * 登记几何变化回调：行高、可见性、按钮等变化后通知页面让布局缓存失效。
     *
     * @param listener 回调；null 表示取消登记
     */
    public void setOnGeometryChanged(@Nullable Runnable listener) {
        this.geometryListener = listener;
    }

    /** 行几何是否有待重测：页面据此决定是否需要重新布局。 */
    public boolean needsMeasure() {
        return measureDirty;
    }

    /**
     * 标记行几何失效：下一次测量前不复用缓存，并通知页面重新布局。
     *
     * <p>所有可能改变行高度或按钮排布的入口都必须经过这里，页面布局因此只需要在真实几何
     * 变化时执行一次，而不是每帧重排。</p>
     */
    void invalidateMeasure() {
        measureDirty = true;
        syncedOriginX = Integer.MIN_VALUE;
        syncedOriginY = Integer.MIN_VALUE;
        if (geometryListener != null) {
            geometryListener.run();
        }
    }

    // 按宽度测量行几何：几何输入与宽度都没变时复用缓存
    private int measureRows(int availableWidth) {
        int width = Math.max(0, availableWidth);
        if (!measureDirty && measuredWidth == width) {
            return measuredContentHeight;
        }
        measuredWidth = width;
        measuredContentHeight = layoutRows(width);
        measureDirty = false;
        // 测量重写了行几何，强制下一次同步重新落屏
        syncedOriginX = Integer.MIN_VALUE;
        syncedOriginY = Integer.MIN_VALUE;
        return measuredContentHeight;
    }

    /**
     * 视口竖直平移：页面滚动时只改视口原点，行几何与按钮内容坐标保持不变。
     *
     * <p>同步阶段只把行原点平移到位，不重新测量文本宽度；页面滚动因此不再触发整页重排。</p>
     *
     * @param deltaY 屏幕坐标增量（正数向下）
     */
    public void shiftViewportY(int deltaY) {
        if (deltaY == 0) return;
        viewportY += deltaY;
    }

    /**
     * 把行几何同步到屏幕：只叠加内容原点与滚动偏移。
     *
     * <p>原点与测量都没变时直接返回，因此静止帧不做任何逐行 setBounds。</p>
     */
    void syncBounds() {
        int originX = viewportX;
        int originY = viewportY - scrollOffset;
        if (!measureDirty && originX == syncedOriginX && originY == syncedOriginY) {
            return;
        }
        FormRowLayout.syncBounds(rows, originX, originY);
        syncedOriginX = originX;
        syncedOriginY = originY;
        refreshHighlightBounds();
    }

    // 高亮框只在同步后重算一次，避免渲染帧每帧分配
    private void refreshHighlightBounds() {
        if (highlightTicks <= 0 || highlightPath == null) {
            highlightBounds = null;
            return;
        }
        for (FormRow row : rows) {
            if (!row.visible || !row.path.equals(highlightPath)) continue;
            highlightBounds = new UiRect(viewportX + row.controlRelX - 1,
                    rowScreenY(row) + row.controlRelY - 1,
                    row.controlWidth + 2, row.control.layoutHeight() + 2);
            return;
        }
        highlightBounds = null;
    }

    int maxScroll() {
        return FormRowLayout.maxScroll(contentHeight, viewportHeight);
    }

    private int rowScreenY(FormRow row) {
        return FormRowLayout.rowScreenY(viewportY, row, scrollOffset);
    }

    // 组装当前控件的对象形态（子列表用）
    RuleValue storeObject() {
        RuleValue.Mapping object = RuleValue.mapping();
        for (FormRow row : rows) {
            if (row.field.type() == EditorFieldType.NOTE || row.field.type() == EditorFieldType.RAW_JSON) {
                continue;
            }
            RuleValue value = row.control.issues(row.path).stream().anyMatch(FormIssue::blocking)
                    ? row.control.originalValue() : row.control.store();
            if (value != null) {
                object.put(row.field.name(), value.deepCopy());
            }
        }
        return object.build();
    }

    // 新建子表单（子列表的每一项）
    FormView newChildForm(String relativePath, List<EditorField> fields) {
        FormView child = new FormView(font, session, childPath(relativePath));
        child.childForm = true;
        if (catalogSelections) child.useCatalogSelections();
        child.setConditionEditingSupport(conditionEditingSupport);
        child.setConditionContextFactory(conditionContextFactory);
        child.setSelectionCatalogOpener(catalogOpener);
        child.setLabelWidth(labelWidth);
        child.setOnChanged(this::notifyChanged);
        for (EditorField field : fields) {
            child.addField(field);
        }
        return child;
    }

    /** 本地校验问题清单 */
    public List<FormIssue> issues() {
        List<FormIssue> list = new ArrayList<>();
        for (FormRow row : rows) {
            if (!row.visible || row.field.type() == EditorFieldType.NOTE) {
                continue;
            }
            list.addAll(row.control.issues(row.path));
        }
        return list;
    }

    /** 定位到指定草稿路径的字段：滚动使其整行可见、短暂高亮，返回其首个可聚焦目标（没有则返回 null） */
    public @Nullable UiFocusTarget revealPath(String path) {
        FormRow row = rowFor(path);
        if (row == null || !row.visible) {
            return null;
        }
        // 条件树行是条件问题的唯一入口：先问控件能否精确落点。无法落点时直接结束，
        // 不滚动、不高亮，也不退回到本行的打开按钮等通用焦点。
        // 普通控件保持原有顺序：先滚动高亮，再让控件处理嵌套定位
        boolean conditionEntry = row.control instanceof ConditionTreeControl;
        UiFocusTarget nestedTarget = null;
        if (conditionEntry) {
            nestedTarget = row.control.revealPath(path, row.path);
            if (nestedTarget == null) {
                return null;
            }
        }
        if (row.contentY < scrollOffset) {
            scrollOffset = Math.max(0, row.contentY - 2);
        } else if (row.contentY + row.contentHeight > scrollOffset + viewportHeight) {
            scrollOffset = Math.min(maxScroll(), row.contentY + row.contentHeight - viewportHeight + 2);
        }
        clampScroll();
        this.highlightPath = row.path;
        this.highlightTicks = HIGHLIGHT_TICKS;
        syncBounds();
        refreshHighlightBounds();
        if (!conditionEntry) {
            nestedTarget = row.control.revealPath(path, row.path);
        }
        if (nestedTarget != null && nestedTarget.canFocus()) return nestedTarget;
        List<UiFocusTarget> targets = new ArrayList<>();
        row.control.addFocusTargets(targets);
        for (UiFocusTarget target : targets) {
            if (target.canFocus()) {
                return target;
            }
        }
        return null;
    }

    // 按问题路径找行：路径可能正好是字段，也可能指向字段下的子项
    // 归属必须唯一：多行的字段段边界同时匹配同一路径说明描述符不唯一，此时拒绝定位，
    // 调用方在滚动、高亮与进入控件之前就会收到 null，不会先改动视图状态
    private @Nullable FormRow rowFor(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        FormRow matched = null;
        for (FormRow row : rows) {
            if (row.field.type() == EditorFieldType.NOTE) {
                continue;
            }
            if (path.equals(row.path) || path.startsWith(row.path + ".")) {
                if (matched != null) {
                    return null;
                }
                matched = row;
            }
        }
        return matched;
    }

    /** Tab 顺序（U 界面把本表登记进 UiFocusManager） */
    public List<UiFocusTarget> focusTargets() {
        List<UiFocusTarget> list = new ArrayList<>();
        addFocusTargets(list);
        return list;
    }

    void addFocusTargets(List<UiFocusTarget> out) {
        for (FormRow row : rows) {
            if (!row.visible) continue;
            row.control.addFocusTargets(out);
            out.addAll(row.pickers);
            out.addAll(row.actions);
        }
    }

    /**
     * 导出表单的排版快照：按行顺序收集当前可见的字段控件与行尾按钮。
     *
     * <p>表单布局已经把控件矩形写成屏幕坐标，滚动时由 {@link #shiftViewportY} 平移视口，
     * 因此直接沿行的焦点目标收集，不再做坐标换算。复合控件（条件树、目录选择等）只暴露
     * 自身的焦点目标，其内部行不在焦点顺序里，快照不展开这些内部行。</p>
     */
    @Override
    public List<UiLayoutNode> layoutNodes() {
        if (!visible) {
            return List.of();
        }
        List<UiLayoutNode> nodes = new ArrayList<>();
        List<UiFocusTarget> targets = new ArrayList<>();
        IdentityHashMap<UiFocusTarget, Boolean> seen = new IdentityHashMap<>();
        for (FormRow row : rows) {
            if (!row.visible) {
                continue;
            }
            targets.clear();
            row.control.addFocusTargets(targets);
            targets.addAll(row.pickers);
            targets.addAll(row.actions);
            for (UiFocusTarget target : targets) {
                if (seen.put(target, Boolean.TRUE) != null || !(target instanceof UiLayoutSource source)) {
                    continue;
                }
                nodes.addAll(source.layoutNodes());
            }
        }
        return nodes;
    }

    /** 每个字段独立完成编辑，切换到下一字段也提交一次，不按字符记录。 */
    public void tick() {
        if (loadedRevision != session.revision()) reload();
        refreshRowVisibility();
        if (highlightTicks > 0) highlightTicks--;
        // 整表只保留一轮输入：本轮收尾的行由下方定点处理，不再重复扫描全表比较文本
        retainPendingInputs();
        for (FormRow row : rows) {
            if (!row.visible) continue;
            boolean editing = row.control.isEditing();
            if (row.wasEditing && !editing) {
                row.control.finishInput();
                if (row.control.issues(row.path).stream().noneMatch(FormIssue::blocking)) {
                    RuleValue next = row.control.store();
                    writeField(row, next);
                    acceptInput(row);
                } else {
                    // 收尾可能规范化文本缓冲：只对本次确实结束编辑的行定点重存，避免再扫全表
                    draftWriter.retainRowInput(row);
                }
                // 收尾可能改变控件高度（列表/条件树的折叠展开等），让下一次布局重新测量
                invalidateMeasure();
            }
            row.wasEditing = editing;
        }
    }

    public boolean isEditing() {
        for (FormRow row : rows) {
            if (row.control.isEditing()) {
                return true;
            }
        }
        return false;
    }

    // 子表单布局：高度不限，返回内容高度（子列表用）
    int layoutUnbounded(int x, int y, int width) {
        return layout(x, y, width, Integer.MAX_VALUE / 4);
    }

    /** 页面按实际内容分配空间，避免固定空白与表单互相挤占。 */
    public int preferredHeight(int width) {
        return measureRows(width);
    }

    /** 布局：返回内容总高度 */
    public int layout(int x, int y, int width, int height) {
        this.viewportX = x;
        this.viewportY = y;
        this.viewportWidth = Math.max(0, width);
        this.viewportHeight = Math.max(0, height);
        int used = measureRows(this.viewportWidth);
        if (used > this.viewportHeight && this.viewportWidth > SCROLLBAR_WIDTH * 2) {
            used = measureRows(this.viewportWidth - SCROLLBAR_WIDTH);
        }
        this.contentHeight = used;
        clampScroll();
        syncBounds();
        return contentHeight;
    }

    /**
     * 流式排布：按可用宽度测量一次后把视口高度贴合内容高度，供页面表单直接使用。
     *
     * <p>替代「先 preferredHeight 再 layout」的两次测量：页面一次调用即可拿全行几何与内容高度，
     * 且这类表单内不出现滚动条，滚动偏移归零。</p>
     *
     * @param x     内容原点屏幕 X
     * @param y     内容原点屏幕 Y
     * @param width 可用宽度
     * @return 内容高度
     */
    public int layoutNatural(int x, int y, int width) {
        this.viewportX = x;
        this.viewportY = y;
        this.viewportWidth = Math.max(0, width);
        int used = measureRows(this.viewportWidth);
        this.viewportHeight = used;
        this.contentHeight = used;
        this.scrollOffset = 0;
        syncBounds();
        return used;
    }

    private void clampScroll() {
        this.scrollOffset = Math.clamp(scrollOffset, 0, maxScroll());
    }

    public int contentHeight() {
        return contentHeight;
    }

    @Override
    public UiRect bounds() {
        return new UiRect(viewportX, viewportY, viewportWidth, viewportHeight);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        layout(x, y, width, height);
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean newVisible) {
        this.visible = newVisible;
        if (!newVisible) {
            endInteractions(UiInputCapture.EndReason.HIDDEN);
        }
    }

    public void setEnabled(boolean newEnabled) {
        this.enabled = newEnabled;
        if (!newEnabled) {
            endInteractions(UiInputCapture.EndReason.DISABLED);
        }
        for (FormRow row : rows) {
            row.control.setEnabled(newEnabled);
            row.pickers.forEach(button -> button.setEnabled(newEnabled));
            row.actions.forEach(button -> button.setEnabled(newEnabled));
        }
    }

    /** 焦点作用域切换（打开模态、切页）：先结束进行中的捕获再换焦点 */
    public void onFocusScopeChanged() {
        retainPendingInputs();
        endInteractions(UiInputCapture.EndReason.FOCUS_SCOPE_CHANGED);
    }

    /** 表单从界面卸载 */
    public void unmount() {
        retainPendingInputs();
        endInteractions(UiInputCapture.EndReason.UNMOUNTED);
    }

    /** 宿主屏幕关闭 */
    public void onHostClosed() {
        retainPendingInputs();
        endInteractions(UiInputCapture.EndReason.HOST_CLOSED);
    }

    // 统一结束入口：把「未提交的预览」按原因回退或提交，交由各控件实现
    void endInteractions(UiInputCapture.EndReason reason) {
        for (FormRow row : rows) {
            row.control.endInteractions(reason);
        }
    }

    @Override
    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        // 顶层表单已由页面 tick 驱动，这里只补子列表里不在页面表单清单内的子表单；
        // 两处都调会让同一表单每帧 tick 两次（高亮衰减加倍、输入整理重复）
        if (childForm) tick();
        syncBounds();
        graphics.enableScissor(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight);
        try {
            for (FormRow row : rows) {
                if (!row.visible) continue;
                int screenY = rowScreenY(row);
                if (screenY + row.contentHeight < viewportY || screenY > viewportY + viewportHeight) {
                    continue;
                }
                if (row.field.type() == EditorFieldType.NOTE) {
                    FormRowRenderer.drawNote(graphics, renderFont, row, screenY, viewportX, THEME);
                    continue;
                }
                if (row.labelShown) {
                    FormRowRenderer.drawLabel(graphics, renderFont, row, screenY, viewportX, minimumControlHeight, THEME);
                }
                if (row.control instanceof RawJsonControl raw) {
                    String summary = TextScroll.trimToWidth(renderFont, raw.summary(row.controlWidth), row.controlWidth);
                    graphics.drawString(renderFont, summary, viewportX + row.controlRelX, screenY + row.controlRelY,
                            THEME.palette().textPrimary(), false);
                    continue;
                }
                row.control.render(graphics, renderFont, mouseX, mouseY);
                if (row.control.issues(row.path).stream().anyMatch(FormIssue::blocking)) {
                    int left = viewportX + row.controlRelX;
                    int top = screenY + row.controlRelY;
                    graphics.fill(left, top + row.control.layoutHeight() - 1,
                            left + row.controlWidth, top + row.control.layoutHeight(), THEME.palette().danger());
                }
                for (var pickerButton : row.pickers) pickerButton.render(graphics, renderFont, mouseX, mouseY);
                for (UiButton action : row.actions) action.render(graphics, renderFont, mouseX, mouseY);
            }
            if (highlightTicks > 0 && highlightBounds != null) {
                THEME.drawFocusOutline(graphics, highlightBounds);
            }
        } finally {
            graphics.disableScissor();
        }
        if (contentHeight > viewportHeight) {
            FormRowRenderer.renderScrollbar(graphics, viewportX, viewportY, viewportWidth, viewportHeight,
                    contentHeight, scrollOffset, maxScroll(), SCROLLBAR_WIDTH, THEME);
        }
    }

    /** 鼠标位置下的提示：优先显示该字段的校验问题，其次显示字段提示 */
    public @Nullable Component tooltipAt(double mouseX, double mouseY) {
        return FormRowRenderer.tooltipAt(mouseX, mouseY, font, rows, viewportX, viewportY, viewportWidth,
                viewportHeight, scrollOffset);
    }

    // ---- 输入分发薄封装：实现集中在 FormInputRouter ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = inputRouter.mouseClicked(mouseX, mouseY, button);
        if (handled) invalidateMeasure();
        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = inputRouter.mouseReleased(mouseX, mouseY, button);
        if (handled) invalidateMeasure();
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return inputRouter.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return inputRouter.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = inputRouter.keyPressed(keyCode, scanCode, modifiers);
        if (handled) invalidateMeasure();
        return handled;
    }

    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return inputRouter.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return inputRouter.charTyped(codePoint, modifiers);
    }

}
