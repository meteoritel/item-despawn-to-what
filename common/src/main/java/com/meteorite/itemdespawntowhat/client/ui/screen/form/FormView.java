package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorFieldType;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 描述符驱动的表单视图：把 TypeEditorDescriptor 的字段列表渲染成可滚动的一列控件。
 * <p>所有写入都经由 EditSession（统一撤销门面）落到 RuleDraft，控件本身不直接改草稿；
 * 界面不出现任何可编辑的 JSON 文本框，未识别字段原样保留。
 */
public final class FormView implements com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget, PickerHost {

    // 行间距
    private static final int ROW_GAP = 2;
    // 窄于此宽度时标签换到控件上方（320x240 小尺寸用单列）
    private static final int NARROW_WIDTH = 180;
    // 标签列与控件之间的间隙
    private static final int LABEL_GAP = 4;
    // 标签行高（标签在上方时使用）
    private static final int LABEL_LINE_HEIGHT = 10;
    // 滚动步长
    private static final int SCROLL_STEP = 24;
    // 滚动条宽度
    private static final int SCROLLBAR_WIDTH = 4;

    // 定位高亮持续帧数（约 3 秒）
    private static final int HIGHLIGHT_TICKS = 60;
    // 选择框最多显示的行数
    private static final int PICKER_ROWS = 8;
    // 选择框头部/底部/输入框高度
    private static final int PICKER_HEADER = 14;
    private static final int PICKER_FOOTER = 12;
    private static final int PICKER_INPUT = 12;

    private final Font font;
    private final EditSession session;
    private final String basePath;
    private final List<Row> rows = new ArrayList<>();
    private @Nullable SuggestionProvider suggestionProvider;
    private @Nullable ConditionSupport conditionSupport;
    private @Nullable Runnable changeListener;
    private @Nullable Picker picker;

    private int labelWidth = 88;
    private int viewportX;
    private int viewportY;
    private int viewportWidth;
    private int viewportHeight;
    private int scrollOffset;
    private int contentHeight;
    private boolean visible = true;
    private boolean enabled = true;
    private boolean wasEditing;
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
        rows.add(new Row(field, pathFor(field), FormControl.create(font, this, field, this::notifyChanged)));
        return this;
    }

    // 字段在草稿中的路径：原始 JSON 行与说明行指向所在对象本身
    private String pathFor(EditorField field) {
        if (field.type() == EditorFieldType.RAW_JSON || field.type() == EditorFieldType.NOTE) {
            return basePath;
        }
        return childPath(field.name());
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

    // 控件值变化时的统一回调
    void notifyChanged() {
        if (changeListener != null) {
            changeListener.run();
        }
    }

    // 从草稿重新装载全部控件
    public void reload() {
        RuleDraft draft = session.draft();
        for (Row row : rows) {
            row.control.load(draft.getAt(row.path));
        }
    }

    // 用给定对象装载控件（子列表新建子项时使用，不读草稿）
    public void reloadWith(JsonObject object) {
        for (Row row : rows) {
            JsonElement value = object.has(row.field.name()) ? object.get(row.field.name()) : null;
            row.control.load(value);
        }
    }

    // 把控件值写回草稿；逐字段比较，只有真正变化才记入撤销历史
    public boolean applyToDraft() {
        RuleDraft draft = session.draft();
        boolean written = false;
        for (Row row : rows) {
            if (row.field.type() == EditorFieldType.NOTE || row.path.isEmpty()) {
                continue;
            }
            JsonElement next = row.control.store();
            JsonElement current = draft.getAt(row.path);
            if (Objects.equals(current, next)) {
                continue;
            }
            String path = row.path;
            String opKey = opKeyFor(row);
            if (next == null) {
                session.apply(opKey, () -> draft.removeAt(path));
            } else {
                JsonElement value = next.deepCopy();
                session.apply(opKey, () -> draft.setAt(path, value));
            }
            written = true;
        }
        return written;
    }

    // 撤销操作名：来源列表单独成项（契约 §5.2 的来源与排除标签），其余交给控件自报
    private static String opKeyFor(Row row) {
        if (row.path.equals(RuleFields.SOURCE) || row.path.startsWith(RuleFields.SOURCE + ".")) {
            return EditSession.OP_SET_SOURCE;
        }
        return row.control.undoOpKey();
    }

    // 组装当前控件的对象形态（子列表用）
    JsonObject storeObject() {
        JsonObject object = new JsonObject();
        for (Row row : rows) {
            if (row.field.type() == EditorFieldType.NOTE || row.field.type() == EditorFieldType.RAW_JSON) {
                continue;
            }
            JsonElement value = row.control.store();
            if (value != null) {
                object.add(row.field.name(), value.deepCopy());
            }
        }
        return object;
    }

    // 新建子表单（子列表的每一项）
    FormView newChildForm(String relativePath, List<EditorField> fields) {
        FormView child = new FormView(font, session, childPath(relativePath));
        child.setSuggestionProvider(suggestionProvider);
        child.setConditionSupport(conditionSupport);
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
        for (Row row : rows) {
            if (row.field.type() == EditorFieldType.NOTE) {
                continue;
            }
            list.addAll(row.control.issues(row.path));
        }
        return list;
    }

    // 定位到指定草稿路径的字段：滚动使其整行可见、短暂高亮，返回其首个可聚焦目标（没有则返回 null）
    public @Nullable UiFocusTarget revealPath(String path) {
        Row row = rowFor(path);
        if (row == null) {
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
    private @Nullable Row rowFor(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        for (Row row : rows) {
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
        for (Row row : rows) {
            row.control.addFocusTargets(out);
        }
    }

    // 每帧调用：控件失去焦点时统一落盘
    public void tick() {
        if (highlightTicks > 0) {
            highlightTicks--;
        }
        boolean editing = isEditing();
        if (wasEditing && !editing) {
            applyToDraft();
        }
        wasEditing = editing;
    }

    public boolean isEditing() {
        for (Row row : rows) {
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

    // 只有用户修改过的非法输入才阻止离开，不阻止未完成草稿切换页签。
    public @Nullable FormIssue pendingInputIssue() {
        for (Row row : rows) {
            row.control.finishInput();
            if (row.control.hasPendingInput()) {
                for (FormIssue issue : row.control.issues(row.path)) {
                    if (issue.blocking()) {
                        return issue;
                    }
                }
            }
        }
        return null;
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

    // 按可用宽度排布行（内容坐标系，y 从 0 起）
    private int layoutRows(int availableWidth) {
        boolean narrow = availableWidth < NARROW_WIDTH;
        int effectiveLabelWidth = narrow ? 0 : Math.min(labelWidth, Math.max(32, availableWidth / 3));
        int controlWidth = narrow ? availableWidth : Math.max(24, availableWidth - effectiveLabelWidth - LABEL_GAP);
        int cursor = 0;
        for (Row row : rows) {
            row.narrow = narrow;
            row.labelWidth = effectiveLabelWidth;
            row.controlWidth = controlWidth;
            row.controlRelX = narrow ? 0 : effectiveLabelWidth + LABEL_GAP;
            if (row.field.type() == EditorFieldType.NOTE) {
                row.controlRelY = 0;
                row.contentHeight = row.control.height();
            } else {
                row.control.measure(controlWidth);
                int controlHeight = row.control.height();
                row.controlRelY = narrow ? LABEL_LINE_HEIGHT : Math.max(0, (Math.max(controlHeight, LABEL_LINE_HEIGHT) - controlHeight) / 2);
                row.contentHeight = Math.max(controlHeight + row.controlRelY, LABEL_LINE_HEIGHT);
            }
            row.contentY = cursor;
            cursor += row.contentHeight + ROW_GAP;
        }
        return Math.max(0, cursor - (rows.isEmpty() ? 0 : ROW_GAP));
    }

    private void clampScroll() {
        this.scrollOffset = Math.clamp(scrollOffset, 0, maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - viewportHeight);
    }

    // 把控件矩形同步到当前滚动位置
    private void syncBounds() {
        for (Row row : rows) {
            int screenY = rowScreenY(row);
            row.control.setBounds(viewportX + row.controlRelX, screenY + row.controlRelY, row.controlWidth);
        }
    }

    private int rowScreenY(Row row) {
        return viewportY + row.contentY - scrollOffset;
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
        for (Row row : rows) {
            row.control.setEnabled(newEnabled);
        }
    }

    // 焦点作用域切换（打开模态、切页）：先结束进行中的捕获再换焦点
    public void onFocusScopeChanged() {
        endInteractions(UiInputCapture.EndReason.FOCUS_SCOPE_CHANGED);
    }

    // 表单从界面卸载
    public void unmount() {
        endInteractions(UiInputCapture.EndReason.UNMOUNTED);
    }

    // 宿主屏幕关闭
    public void onHostClosed() {
        endInteractions(UiInputCapture.EndReason.HOST_CLOSED);
    }

    // 统一结束入口：把「未提交的预览」按原因回退或提交，交由各控件实现
    void endInteractions(UiInputCapture.EndReason reason) {
        for (Row row : rows) {
            row.control.endInteractions(reason);
        }
    }

    @Override
    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        syncBounds();
        graphics.enableScissor(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight);
        try {
            for (Row row : rows) {
                int screenY = rowScreenY(row);
                if (screenY + row.contentHeight < viewportY || screenY > viewportY + viewportHeight) {
                    continue;
                }
                if (row.field.type() == EditorFieldType.NOTE) {
                    drawNote(graphics, renderFont, row, screenY);
                    continue;
                }
                if (!row.narrow) {
                    drawLabel(graphics, renderFont, row, screenY);
                } else {
                    drawLabel(graphics, renderFont, row, screenY);
                }
                if (row.control instanceof FormControl.RawJsonControl raw) {
                    String summary = TextScroll.trimToWidth(renderFont, raw.summary(row.controlWidth), row.controlWidth);
                    graphics.drawString(renderFont, summary, viewportX + row.controlRelX, screenY + row.controlRelY,
                            UiPalette.TEXT_PRIMARY, false);
                    continue;
                }
                row.control.render(graphics, renderFont, mouseX, mouseY);
            }
            if (highlightTicks > 0 && highlightPath != null) {
                for (Row row : rows) {
                    if (row.path.equals(highlightPath)) {
                        int screenY = rowScreenY(row);
                        UiTheme.drawFocusOutline(graphics, new UiRect(viewportX + row.controlRelX - 1,
                                screenY + row.controlRelY - 1, row.controlWidth + 2, row.control.height() + 2));
                        break;
                    }
                }
            }
        } finally {
            graphics.disableScissor();
        }
        if (contentHeight > viewportHeight) {
            renderScrollbar(graphics);
        }
        if (picker != null) {
            picker.render(graphics, renderFont, mouseX, mouseY);
        }
    }

    // 字段标签；必填字段加 * 提示
    private void drawLabel(GuiGraphics graphics, Font renderFont, Row row, int screenY) {
        int width = row.narrow ? row.controlWidth : row.labelWidth;
        String text = row.control.label().getString();
        if (row.field.required()) {
            text = text + " *";
        }
        String trimmed = TextScroll.trimToWidth(renderFont, text, Math.max(4, width));
        graphics.drawString(renderFont, trimmed, viewportX, screenY + 1, UiPalette.TEXT_PRIMARY, false);
    }

    // 只读说明行
    private void drawNote(GuiGraphics graphics, Font renderFont, Row row, int screenY) {
        String text = ((FormControl.NoteControl) row.control).note().getString();
        String trimmed = TextScroll.trimToWidth(renderFont, text, Math.max(4, row.controlWidth));
        graphics.drawString(renderFont, trimmed, viewportX, screenY + 1, UiPalette.TEXT_SECONDARY, false);
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int x = viewportX + viewportWidth - SCROLLBAR_WIDTH;
        UiTheme.drawScrollTrack(graphics, new UiRect(x, viewportY, SCROLLBAR_WIDTH, viewportHeight));
        int maxScroll = maxScroll();
        if (maxScroll <= 0) {
            return;
        }
        int thumbHeight = Math.max(8, viewportHeight * viewportHeight / Math.max(1, contentHeight));
        int thumbY = viewportY + (viewportHeight - thumbHeight) * scrollOffset / maxScroll;
        graphics.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbHeight, UiPalette.SCROLL_THUMB);
    }

    // 鼠标位置下的提示：优先显示该字段的校验问题，其次显示字段提示
    public @Nullable Component tooltipAt(double mouseX, double mouseY) {
        if (!visible || !bounds().contains(mouseX, mouseY)) {
            return null;
        }
        for (Row row : rows) {
            if (row.field.type() == EditorFieldType.NOTE) {
                continue;
            }
            int screenY = rowScreenY(row);
            UiRect rect = new UiRect(viewportX, screenY, row.controlWidth + row.controlRelX, row.contentHeight);
            if (!rect.contains(mouseX, mouseY)) {
                continue;
            }
            List<FormIssue> list = row.control.issues(row.path);
            if (!list.isEmpty()) {
                return list.getFirst().message();
            }
            Component full = row.control.fullText();
            if (full != null && font.width(full) > row.controlWidth - 6) {
                return full;
            }
            if (font.width(row.control.label()) > (row.narrow ? row.controlWidth : row.labelWidth)) {
                return row.control.label();
            }
            return row.control.hint();
        }
        return null;
    }

    // ---- PickerHost ----

    @Override
    public void openPicker(EditorField field, Consumer<String> onPicked) {
        List<Suggestion> suggestions = suggestionProvider == null ? List.of() : suggestionProvider.suggestions(field);
        if (suggestions == null || suggestions.isEmpty()) {
            return;
        }
        this.picker = new Picker(suggestions, onPicked);
    }

    public boolean isPickerOpen() {
        return picker != null;
    }

    public void closePicker() {
        this.picker = null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) {
            return false;
        }
        if (picker != null) {
            return picker.mouseClicked(mouseX, mouseY, button);
        }
        if (!enabled || !bounds().contains(mouseX, mouseY)) {
            return false;
        }
        syncBounds();
        for (Row row : rows) {
            if (row.control.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (picker != null) {
            return picker.mouseReleased(mouseX, mouseY, button);
        }
        boolean consumed = false;
        for (Row row : rows) {
            consumed |= row.control.mouseReleased(mouseX, mouseY, button);
        }
        return consumed;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (picker != null) {
            return picker.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        boolean consumed = false;
        for (Row row : rows) {
            consumed |= row.control.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return consumed;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (picker != null) {
            return picker.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (!visible || !bounds().contains(mouseX, mouseY)) {
            return false;
        }
        for (Row row : rows) {
            if (row.control.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        if (maxScroll() <= 0) {
            return false;
        }
        int delta = (int) Math.round(scrollY * SCROLL_STEP);
        this.scrollOffset = Math.max(0, Math.min(scrollOffset - delta, maxScroll()));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (picker != null) {
            return picker.keyPressed(keyCode, scanCode, modifiers);
        }
        // 顺序：聚焦控件优先；方向键与 PageUp/PageDown 都不允许同时改值和滚动
        for (Row row : rows) {
            if (handlesInput(row.control) && row.control.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            this.scrollOffset = Math.max(0, scrollOffset - viewportHeight);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            this.scrollOffset = Math.min(maxScroll(), scrollOffset + viewportHeight);
            return true;
        }
        return false;
    }

    // 按键抬起：交给聚焦控件收尾（方向键重复在抬起时合并为一次提交）
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        for (Row row : rows) {
            if (handlesInput(row.control) && row.control.keyReleased(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (picker != null) {
            return picker.charTyped(codePoint, modifiers);
        }
        for (Row row : rows) {
            if (handlesInput(row.control) && row.control.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return false;
    }

    // 该控件当前是否持有焦点（键盘输入只发给持有焦点的控件）
    private boolean handlesInput(FormControl control) {
        List<UiFocusTarget> targets = new ArrayList<>();
        control.addFocusTargets(targets);
        for (UiFocusTarget target : targets) {
            if (target.isFocused()) {
                return true;
            }
        }
        return false;
    }

    // 选中框内的一行
    private static final class Row {

        final EditorField field;
        final String path;
        final FormControl control;
        int contentY;
        int contentHeight;
        int controlRelX;
        int controlRelY;
        int controlWidth;
        int labelWidth;
        boolean narrow;

        Row(EditorField field, String path, FormControl control) {
            this.field = field;
            this.path = path;
            this.control = control;
        }
    }

    // 候选值选择框：过滤输入框 + 可选列表
    private final class Picker {

        private final List<Suggestion> all;
        private final List<Suggestion> filtered = new ArrayList<>();
        private final UiTextInput filterInput;
        private final Consumer<String> onPicked;
        private int selectedIndex;
        private int offset;
        private UiRect rect = new UiRect(0, 0, 0, 0);
        private UiRect listRect = new UiRect(0, 0, 0, 0);

        Picker(List<Suggestion> suggestions, Consumer<String> onPicked) {
            this.all = suggestions;
            this.onPicked = onPicked;
            this.filterInput = new UiTextInput(font,
                    Component.translatable("gui.itemdespawntowhat.edit.picker.filter"));
            this.filterInput.setOnCommit(text -> confirm());
            applyFilter("");
        }

        private void applyFilter(String text) {
            filtered.clear();
            String lower = text == null ? "" : text.toLowerCase(java.util.Locale.ROOT);
            for (Suggestion suggestion : all) {
                if (lower.isEmpty() || suggestion.value().toLowerCase(java.util.Locale.ROOT).contains(lower)) {
                    filtered.add(suggestion);
                }
            }
            this.selectedIndex = 0;
            this.offset = 0;
        }

        private void confirm() {
            if (selectedIndex >= 0 && selectedIndex < filtered.size()) {
                onPicked.accept(filtered.get(selectedIndex).value());
            }
            closePicker();
        }

        private int width() {
            return Math.clamp(viewportWidth - 8, 120, 240);
        }

        private int height() {
            int rows = Math.clamp(filtered.size(), 1, PICKER_ROWS);
            return PICKER_HEADER + PICKER_INPUT + rows * UiTheme.ROW_HEIGHT + PICKER_FOOTER;
        }

        void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
            int w = width();
            int h = height();
            int x = viewportX + Math.max(0, (viewportWidth - w) / 2);
            int y = viewportY + Math.max(0, (viewportHeight - h) / 2);
            this.rect = new UiRect(x, y, w, h);
            this.listRect = new UiRect(x + 1, y + PICKER_HEADER + PICKER_INPUT, w - 2,
                    Math.max(UiTheme.ROW_HEIGHT, h - PICKER_HEADER - PICKER_INPUT - PICKER_FOOTER));
            graphics.fill(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight,
                    UiPalette.MODAL_DIM);
            UiTheme.drawWindow(graphics, rect);
            UiTheme.drawHeader(graphics, new UiRect(rect.x(), rect.y(), rect.width(), PICKER_HEADER));
            String title = TextScroll.trimToWidth(renderFont,
                    Component.translatable("gui.itemdespawntowhat.edit.picker.title").getString(),
                    Math.max(4, rect.width() - 4));
            graphics.drawString(renderFont, title, rect.x() + 2, rect.y() + 4, UiPalette.HEADER_TEXT, false);
            filterInput.setBounds(x + 2, y + PICKER_HEADER, w - 4, PICKER_INPUT - 2);
            filterInput.render(graphics, renderFont, mouseX, mouseY);
            int visibleRows = Math.max(1, listRect.height() / UiTheme.ROW_HEIGHT);
            for (int i = 0; i < visibleRows; i++) {
                int index = offset + i;
                if (index >= filtered.size()) {
                    break;
                }
                int rowY = listRect.y() + i * UiTheme.ROW_HEIGHT;
                UiRect rowRect = new UiRect(listRect.x(), rowY, listRect.width(), UiTheme.ROW_HEIGHT);
                if (index == selectedIndex) {
                    UiTheme.drawSelection(graphics, rowRect);
                } else if (rowRect.contains(mouseX, mouseY)) {
                    graphics.fill(rowRect.x(), rowRect.y(), rowRect.right(), rowRect.bottom(), UiPalette.CONTROL_HOVER);
                }
                Component label = filtered.get(index).label();
                String text = TextScroll.trimToWidth(renderFont, label.getString(), rowRect.width() - 2);
                graphics.drawString(renderFont, text, rowRect.x() + 1, rowRect.y() + 2, UiPalette.TEXT_PRIMARY, false);
            }
            if (filtered.isEmpty()) {
                String text = TextScroll.trimToWidth(renderFont,
                        Component.translatable("gui.itemdespawntowhat.edit.picker.empty").getString(),
                        Math.max(4, listRect.width() - 2));
                graphics.drawString(renderFont, text, listRect.x() + 1, listRect.y() + 2,
                        UiPalette.TEXT_DISABLED, false);
            }
            String footer = TextScroll.trimToWidth(renderFont,
                    Component.translatable("gui.itemdespawntowhat.edit.picker.hint").getString(),
                    Math.max(4, w - 4));
            graphics.drawString(renderFont, footer, x + 2, rect.bottom() - PICKER_FOOTER + 2,
                    UiPalette.TEXT_SECONDARY, false);
        }

        boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (filterInput.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (!listRect.contains(mouseX, mouseY)) {
                if (!rect.contains(mouseX, mouseY)) {
                    closePicker();
                }
                return true;
            }
            int index = offset + (int) ((mouseY - listRect.y()) / UiTheme.ROW_HEIGHT);
            if (index >= 0 && index < filtered.size()) {
                this.selectedIndex = index;
                confirm();
            }
            return true;
        }

        boolean mouseReleased(double mouseX, double mouseY, int button) {
            return filterInput.mouseReleased(mouseX, mouseY, button);
        }

        boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            return false;
        }

        boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            int visibleRows = Math.max(1, listRect.height() / UiTheme.ROW_HEIGHT);
            int maxOffset = Math.max(0, filtered.size() - visibleRows);
            this.offset = Math.max(0, Math.min(offset - (int) Math.signum(scrollY), maxOffset));
            return true;
        }

        boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closePicker();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                this.selectedIndex = Math.max(0, selectedIndex - 1);
                ensureVisible();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                this.selectedIndex = Math.min(Math.max(0, filtered.size() - 1), selectedIndex + 1);
                ensureVisible();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_HOME) {
                this.selectedIndex = 0;
                ensureVisible();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_END) {
                this.selectedIndex = Math.max(0, filtered.size() - 1);
                ensureVisible();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirm();
                return true;
            }
            if (filterInput.keyPressed(keyCode, scanCode, modifiers)) {
                applyFilter(filterInput.value());
                return true;
            }
            return true;
        }

        boolean charTyped(char codePoint, int modifiers) {
            if (filterInput.charTyped(codePoint, modifiers)) {
                applyFilter(filterInput.value());
                return true;
            }
            return false;
        }

        private void ensureVisible() {
            int visibleRows = Math.max(1, listRect.height() / UiTheme.ROW_HEIGHT);
            if (selectedIndex < offset) {
                this.offset = selectedIndex;
            } else if (selectedIndex >= offset + visibleRows) {
                this.offset = selectedIndex - visibleRows + 1;
            }
        }
    }
}
