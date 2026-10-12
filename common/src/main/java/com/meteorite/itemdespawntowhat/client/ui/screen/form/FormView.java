package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorFieldType;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.shared.FieldCatalogTypes;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/***
 * 描述符驱动的表单视图：把 TypeEditorDescriptor 的字段列表渲染成可滚动的一列控件。
 * <p>所有写入都经由 EditSession（统一撤销门面）落到 RuleDraft，控件本身不直接改草稿；
 * 界面不出现任何可编辑的 JSON 文本框，未识别字段原样保留。
 */
public final class FormView implements UiWidget, PickerHost, FormPicker.Host {

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
    private @Nullable SuggestionProvider suggestionProvider;
    private @Nullable CatalogOpener catalogOpener;

    /*** 字段目录入口由页面提供，表单不访问网络。 */
    @FunctionalInterface
    public interface CatalogOpener {
        void open(EditorField field, boolean tags, Consumer<List<String>> onPicked);
    }

    public FormView setCatalogOpener(@Nullable CatalogOpener opener) {
        catalogOpener = opener;
        for (FormRow row : rows) { row.pickers.clear(); addPickers(row); }
        return this;
    }
    private @Nullable ConditionSupport conditionSupport;
    private @Nullable Runnable changeListener;
    @Nullable BiConsumer<String, RuleValue> fieldWritten;

    // 关联字段在同一次撤销操作内同步；第二个参数为写入前的值。
    public void setOnFieldWritten(BiConsumer<String, RuleValue> listener) {
        fieldWritten = listener;
    }
    // 输入被上限拒绝时的即时提示出口（页面显示 notice）
    private @Nullable Consumer<Component> rejectedNoticeHandler;
    @Nullable FormPicker picker;
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
    private @Nullable String highlightPath;
    private int highlightTicks;

    public FormView(Font font, EditSession session, String basePath) {
        this.font = font;
        this.session = session;
        this.basePath = basePath == null ? "" : basePath;
    }

    // 用描述符整体替换字段列表
    public FormView setDescriptor(TypeEditorDescriptor descriptor) {
        rows.clear();
        for (EditorField field : descriptor.fields()) {
            addField(field);
        }
        return this;
    }

    // 追加一个字段
    public FormView addField(EditorField field) {
        var catalogType = FieldCatalogTypes.catalogTypeOf(field);
        boolean iconSelection = catalogSelections && catalogType != null && isCatalogField(field);
        FormRow row = new FormRow(field, pathFor(field), iconSelection
                ? new CatalogSelectionControl(font, field, this::notifyChanged)
                : FormControl.create(font, this, field, this::notifyChanged));
        row.control.setMinimumHeight(minimumControlHeight);
        row.control.setRejectedNotice(message -> {
            if (rejectedNoticeHandler != null) {
                rejectedNoticeHandler.accept(message);
            }
        });
        addPickers(row);
        rows.add(row);
        return this;
    }

    // 读取某字段控件当前的未提交值（宿主把缓冲并入自己的编辑步时使用）；字段不存在返回 null
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

    // 给某个字段行挂行尾操作按钮（例如「恢复自动命名」）；宽度按文本自适应
    public FormView setRowAction(String path, UiButton button) {
        for (FormRow row : rows) {
            if (row.path.equals(path)) {
                row.actions.clear();
                row.actions.add(button);
                row.refreshButtons();
                break;
            }
        }
        return this;
    }

    // 覆盖某字段行的控件高度（<=0 恢复默认）；例如基本页加高的优先级控件
    public FormView setRowHeight(String path, int height) {
        for (FormRow row : rows) {
            if (row.path.equals(path)) {
                row.control.setHeightOverride(height);
            }
        }
        return this;
    }

    // 卡片标题已表达字段含义时省去重复标签。
    public void hideRowLabel(String path) {
        for (FormRow row : rows) if (row.path.equals(path)) row.labelVisible = false;
    }

    // 隐藏行退出键盘导航与命中，但保存原有字段内容。
    public void setRowVisible(String path, boolean visible) {
        for (FormRow row : rows) if (row.path.equals(path)) {
            row.visible = visible;
            if (!visible) {
                List<UiFocusTarget> targets = new ArrayList<>();
                row.control.addFocusTargets(targets);
                targets.forEach(target -> target.setFocused(false));
            }
        }
    }

    // 启用开关等含义直观的行不弹出提示。
    public void suppressRowTooltip(String path) {
        for (FormRow row : rows) if (row.path.equals(path)) row.tooltipEnabled = false;
    }

    // 展示有效默认文本，不修改草稿的字段存在性。
    public void setRowDefaultText(String path, Component value) {
        for (FormRow row : rows) if (row.path.equals(path) && row.control instanceof TextControl text) {
            row.defaultText = value;
            text.displayDefault(value);
        }
    }

    // 动态默认值只用于回填，未编辑时仍保留原字段的省略状态。
    public void setRowDefaultValue(String path, Supplier<RuleValue> value) {
        for (FormRow row : rows) if (row.path.equals(path)) row.defaultValue = value;
        reload();
    }

    // 数量对象按引用身份展示；对象键无需进入草稿的点分路径语法。
    public void setItemCountReferences(String path, List<String> references) {
        for (FormRow row : rows) if (row.path.equals(path) && row.control instanceof ItemCountsControl counts) {
            counts.setReferences(references);
        }
    }

    // 只由卡片编辑页启用，弹窗等其它宿主保留原有密度。
    public void useCardSpacing() {
        minimumControlHeight = 20;
        rowGap = 6;
        for (FormRow row : rows) row.control.setMinimumHeight(minimumControlHeight);
    }

    // 在装配字段之前启用；输入页统一使用目录添加物品和标签。
    public void useCatalogSelections() { catalogSelections = true; }

    // 基本页以紧凑密度显示，不重复给可选值添加默认前缀。
    public void useCompactSpacing() {
        minimumControlHeight = 18;
        rowGap = 4;
        labelWidth = 64;
        for (FormRow row : rows) row.control.setMinimumHeight(minimumControlHeight);
    }

    // 输入被上限拒绝时的即时提示出口
    public FormView setOnRejectedNotice(@Nullable Consumer<Component> handler) {
        this.rejectedNoticeHandler = handler;
        return this;
    }


    // 支持叶子面板在描述符载入后挂接目录入口。
    private void addPickers(FormRow row) {
        if (catalogOpener != null && FieldCatalogTypes.catalogTypeOf(row.field) != null
                && isCatalogField(row.field)) {
            row.pickers.add(new UiButton(THEME, font,
                    Component.translatable("gui.itemdespawntowhat.edit.pick." + (catalogSelections ? "add" : "open")),
                    UiButtonVariant.SECONDARY, () -> pick(row, false)));
            if (!catalogSelections && (row.field.type() == EditorFieldType.TAG || row.field.type() == EditorFieldType.TAG_LIST)) row.pickers.add(
                    new UiButton(THEME, font,
                            Component.translatable("gui.itemdespawntowhat.edit.pick.tags"),
                            UiButtonVariant.SECONDARY, () -> pick(row, true)));
        }
        row.refreshButtons();
    }

    private void pick(FormRow row, boolean tags) {
        if (!enabled || catalogOpener == null) return;
        commitPendingInputs();
        catalogOpener.open(row.field, tags, ids -> {
            if (ids.isEmpty() || !enabled) return;
            RuleValue value;
            if (row.field.type() == EditorFieldType.TAG_LIST || row.field.type() == EditorFieldType.RL_LIST) {
                java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
                RuleValue existing = session.draft().valueAt(row.path);
                if (existing != null && existing.isList()) for (RuleValue element : existing.items())
                    if (element.isText()) merged.add(element.asText());
                merged.addAll(ids);
                RuleValue.Sequence array = RuleValue.list(); merged.forEach(id -> array.add(RuleValue.text(id))); value = array.build();
            } else value = RuleValue.text(ids.getFirst());
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
        return basePath.isEmpty() ? relative : basePath + "." + relative;
    }

    // 条件树返回相对路径，宿主编辑叶参数时需定位到本表单所在的规则或效果。
    public String draftPath(String relative) {
        return childPath(relative);
    }

    public FormView setSuggestionProvider(@Nullable SuggestionProvider provider) {
        this.suggestionProvider = provider;
        return this;
    }

    public FormView setConditionSupport(@Nullable ConditionSupport support) {
        this.conditionSupport = support;
        return this;
    }

    // 条件树控件所用的宿主能力（可为空，为空时条件字段只读回退）
    @Nullable ConditionSupport conditionSupport() {
        return conditionSupport;
    }

    public FormView setLabelWidth(int width) {
        this.labelWidth = Math.max(0, width);
        return this;
    }

    public FormView setOnChanged(Runnable listener) {
        this.changeListener = listener;
        return this;
    }

    // 完成的滑条、开关和选择操作立即写入；聚焦文本仍只保留缓冲。
    void notifyChanged() {
        retainPendingInputs();
        applyToDraft();
        if (changeListener != null) {
            changeListener.run();
        }
    }

    // ---- 草稿写入薄封装：实现集中在 FormDraftWriter ----

    public void reload() {
        draftWriter.reload();
    }

    public void reloadWith(RuleValue object) {
        draftWriter.reloadWith(object);
    }

    /** 保存会话内输入，不改变规则 JSON 或配置历史。 */
    public void retainPendingInputs() {
        draftWriter.retainPendingInputs();
    }

    /** 完成当前字段输入；只写合法值，非法文本留在会话缓冲。 */
    public boolean commitPendingInputs() {
        return draftWriter.applyToDraft(true);
    }

    // 把控件值写回草稿；逐字段比较，只有真正变化才记入撤销历史
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

    void syncBounds() {
        FormRowLayout.syncBounds(rows, viewportX, viewportY, scrollOffset, font);
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
        if (catalogSelections) child.useCatalogSelections();
        child.setSuggestionProvider(suggestionProvider);
        child.setConditionSupport(conditionSupport);
        child.setCatalogOpener(catalogOpener);
        child.setLabelWidth(labelWidth);
        child.setOnChanged(this::notifyChanged);
        for (EditorField field : fields) {
            child.addField(field);
        }
        return child;
    }

    // 本地校验问题清单
    public List<FormIssue> issues() {
        List<FormIssue> list = new ArrayList<>();
        for (FormRow row : rows) {
            if (row.field.type() == EditorFieldType.NOTE) {
                continue;
            }
            list.addAll(row.control.issues(row.path));
        }
        return list;
    }

    // 定位到指定草稿路径的字段：滚动使其整行可见、短暂高亮，返回其首个可聚焦目标（没有则返回 null）
    public @Nullable UiFocusTarget revealPath(String path) {
        FormRow row = rowFor(path);
        if (row == null || !row.visible) {
            return null;
        }
        if (row.contentY < scrollOffset) {
            scrollOffset = Math.max(0, row.contentY - 2);
        } else if (row.contentY + row.contentHeight > scrollOffset + viewportHeight) {
            scrollOffset = Math.min(maxScroll(), row.contentY + row.contentHeight - viewportHeight + 2);
        }
        clampScroll();
        syncBounds();
        this.highlightPath = row.path;
        this.highlightTicks = HIGHLIGHT_TICKS;
        UiFocusTarget nestedTarget = row.control.revealPath(path, row.path);
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
    private @Nullable FormRow rowFor(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        for (FormRow row : rows) {
            if (row.field.type() == EditorFieldType.NOTE) {
                continue;
            }
            if (path.equals(row.path) || path.startsWith(row.path + ".")) {
                return row;
            }
        }
        return null;
    }

    // Tab 顺序（U 界面把本表登记进 UiFocusManager）
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

    // 每个字段独立完成编辑，切换到下一字段也提交一次，不按字符记录。
    public void tick() {
        if (loadedRevision != session.revision()) reload();
        if (highlightTicks > 0) highlightTicks--;
        retainPendingInputs();
        for (FormRow row : rows) {
            boolean editing = row.control.isEditing();
            if (row.wasEditing && !editing) {
                row.control.finishInput();
                if (row.control.issues(row.path).stream().noneMatch(FormIssue::blocking)) {
                    RuleValue next = row.control.store();
                    writeField(row, next);
                    acceptInput(row);
                }
            }
            row.wasEditing = editing;
        }
        retainPendingInputs();
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

    // 页面按实际内容分配空间，避免固定空白与表单互相挤占。
    public int preferredHeight(int width) {
        return layoutRows(Math.max(0, width));
    }

    // 保存前检查当前输入；切换页面允许保留非法文本。
    public @Nullable FormIssue pendingInputIssue() {
        return draftWriter.pendingInputIssue();
    }

    // 布局：返回内容总高度
    public int layout(int x, int y, int width, int height) {
        this.viewportX = x;
        this.viewportY = y;
        this.viewportWidth = Math.max(0, width);
        this.viewportHeight = Math.max(0, height);
        int used = layoutRows(this.viewportWidth);
        if (used > this.viewportHeight && this.viewportWidth > SCROLLBAR_WIDTH * 2) {
            used = layoutRows(this.viewportWidth - SCROLLBAR_WIDTH);
        }
        this.contentHeight = used;
        clampScroll();
        syncBounds();
        return contentHeight;
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

    public FormView setVisible(boolean newVisible) {
        this.visible = newVisible;
        if (!newVisible) {
            endInteractions(UiInputCapture.EndReason.HIDDEN);
        }
        return this;
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

    // 焦点作用域切换（打开模态、切页）：先结束进行中的捕获再换焦点
    public void onFocusScopeChanged() {
        retainPendingInputs();
        endInteractions(UiInputCapture.EndReason.FOCUS_SCOPE_CHANGED);
    }

    // 表单从界面卸载
    public void unmount() {
        retainPendingInputs();
        endInteractions(UiInputCapture.EndReason.UNMOUNTED);
    }

    // 宿主屏幕关闭
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
        tick();
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
                if (row.labelVisible && (minimumControlHeight == 0 || row.control.usesLabelColumn())) {
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
                for (com.meteorite.itemdespawntowhat.client.ui.widget.UiButton action : row.actions) action.render(graphics, renderFont, mouseX, mouseY);
            }
            if (highlightTicks > 0 && highlightPath != null) {
                for (FormRow row : rows) {
                    if (row.visible && row.path.equals(highlightPath)) {
                        int screenY = rowScreenY(row);
                        THEME.drawFocusOutline(graphics, new UiRect(viewportX + row.controlRelX - 1,
                                screenY + row.controlRelY - 1, row.controlWidth + 2, row.control.layoutHeight() + 2));
                        break;
                    }
                }
            }
        } finally {
            graphics.disableScissor();
        }
        if (contentHeight > viewportHeight) {
            FormRowRenderer.renderScrollbar(graphics, viewportX, viewportY, viewportWidth, viewportHeight,
                    contentHeight, scrollOffset, maxScroll(), SCROLLBAR_WIDTH, THEME);
        }
        if (picker != null) {
            picker.render(graphics, renderFont, mouseX, mouseY);
        }
    }




    // 鼠标位置下的提示：优先显示该字段的校验问题，其次显示字段提示
    public @Nullable Component tooltipAt(double mouseX, double mouseY) {
        return FormRowRenderer.tooltipAt(mouseX, mouseY, font, rows, viewportX, viewportY, viewportWidth,
                viewportHeight, scrollOffset);
    }

    // ---- PickerHost ----

    @Override
    public void openPicker(EditorField field, Consumer<String> onPicked) {
        List<Suggestion> suggestions = suggestionProvider == null ? List.of() : suggestionProvider.suggestions(field);
        if (suggestions == null || suggestions.isEmpty()) {
            return;
        }
        this.picker = new FormPicker(font, THEME, suggestions, onPicked, this);
    }

    public boolean isPickerOpen() {
        return picker != null;
    }

    public void closePicker() {
        this.picker = null;
    }

    // ---- 输入分发薄封装：实现集中在 FormInputRouter ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return inputRouter.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return inputRouter.mouseReleased(mouseX, mouseY, button);
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
        return inputRouter.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return inputRouter.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return inputRouter.charTyped(codePoint, modifiers);
    }

    // ---- FormPicker.Host：选择框只读视口几何，关闭动作走 closePicker ----

    @Override
    public int viewportX() {
        return viewportX;
    }

    @Override
    public int viewportY() {
        return viewportY;
    }

    @Override
    public int viewportWidth() {
        return viewportWidth;
    }

    @Override
    public int viewportHeight() {
        return viewportHeight;
    }
}
