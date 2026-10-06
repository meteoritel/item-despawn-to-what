package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDefaults;
import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDescriptors;
import com.meteorite.itemdespawntowhat.client.edit.ClientTypeRegistries;
import com.meteorite.itemdespawntowhat.client.edit.ConditionEditorRegistry;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorWorkspaceView;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.edit.draft.DraftConflict;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientState;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.CatalogSuggestions;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ConditionSupport;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ConsumptionSummary;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormIssue;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.RuleTemplateHooks;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.NaturalSummary;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ReadOnlyTextView;
import com.meteorite.itemdespawntowhat.client.edit.RuleNaming;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.Suggestion;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.SuggestionProvider;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiConditionTreeEditor;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiListView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModalStack;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiNarration;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiSegmentedControl;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.model.CatalystCost;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionLimits;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleIssue;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSaveStatus;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshotEntry;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/***
 * 规则编辑器主屏（契约 §5、docs/plan/plan-frontend-rewrite-forms.md）。
 * <p>两种模式：规则管理页（列表/搜索/筛选/新建/复制/停用/删除/恢复原始/模板）与规则编辑页
 * （基本信息 / 源物品 / 触发条件 / 效果 四个页签）。所有字段编辑走描述符驱动的
 * {@link FormView}，条件树走 {@link UiConditionTreeEditor}，界面任何位置都不出现可编辑 JSON 文本框。
 * <p>只有工作区处于 ACTIVE 时可编辑；APPLYING 冻结；工作区进入 FREE 时自行关屏。
 */
public final class RuleEditorScreen extends Screen {

    // 布局常量
    private static final int PAD = 4;
    private static final int HEADER_H = 14;
    private static final int TAB_H = 14;
    private static final int ROW_H = 12;
    private static final int NOTICE_H = 10;
    // 自然语言摘要 / 消耗语义提示行高
    private static final int SUMMARY_H = 10;
    // i18n 前缀
    private static final String UI = "gui.itemdespawntowhat.edit.";
    private static final String ISSUE = UI + "issue.";

    // 模式
    private enum Mode { LIST, EDIT }

    // 编辑页签（取值与 RuleEditorEditPages.Page 对应，标签键为 tab.<value>）

    private final EditorWorkspaceView workspace;
    private final RuleEditorModel model;
    private final UiFocusManager focus = new UiFocusManager();
    private final UiModalStack modals = new UiModalStack();

    // 当前模式与页签
    private Mode mode = Mode.LIST;
    private RuleEditorEditPages.Page tab = RuleEditorEditPages.Page.INFO;
    // 状态提示（保存回执/校验失败等）
    private @Nullable Component notice;
    private int noticeColor = UiPalette.TEXT_SECONDARY;
    // 已消费的回执序号
    private long seenResultSeq;

    // ---- 管理页 ----
    private @Nullable UiTextInput searchField;
    private @Nullable UiSegmentedControl filterControl;
    private @Nullable UiListView<String> ruleList;
    private final List<String> visibleIds = new ArrayList<>();
    private final List<UiButton> listButtons = new ArrayList<>();
    private final ButtonBar listBar = new ButtonBar();
    private String lastQuery = "";
    private int filterIndex;

    // ---- 编辑页 ----
    private @Nullable String editingId;
    // 四页编辑流程（基本信息 / 输入与成本 / 触发与条件 / 结果）
    private @Nullable RuleEditorEditPages pages;
    private @Nullable UiSegmentedControl tabControl;
    private final List<UiButton> footerButtons = new ArrayList<>();
    private final ButtonBar footerBar = new ButtonBar();
    // 编辑内容区（每帧重算；切页与问题定位复用同一份布局）
    private UiRect lastEditArea = new UiRect(0, 0, 0, 0);

    // 页面宿主适配：把屏幕能力（会话 / 焦点 / 弹窗 / 提示 / 冻结 / 尺寸）暴露给四页编辑流程
    private final RuleEditorEditPages.Host pagesHost = new RuleEditorEditPages.Host() {

        @Override
        public Font font() {
            return RuleEditorScreen.this.font;
        }

        @Override
        public @Nullable EditSession session() {
            return editingSession();
        }

        @Override
        public UiFocusManager focus() {
            return focus;
        }

        @Override
        public UiModalStack modals() {
            return modals;
        }

        @Override
        public void onDraftChanged() {
            RuleEditorScreen.this.onDraftChanged();
        }

        @Override
        public void onControlsChanged() {
            rebuildFocus();
        }

        @Override
        public void notice(Component message, int color) {
            setNotice(message, color);
        }

        @Override
        public SuggestionProvider suggestions() {
            return suggestionProvider();
        }

        @Override
        public ConditionSupport conditionSupport() {
            return pageConditionSupport();
        }

        @Override
        public EditorWorkspaceView workspace() {
            return RuleEditorScreen.this.workspace;
        }

        @Override
        public boolean rejectWhenFrozen() {
            return RuleEditorScreen.this.rejectWhenFrozen();
        }

        @Override
        public int screenWidth() {
            return RuleEditorScreen.this.width;
        }

        @Override
        public int screenHeight() {
            return RuleEditorScreen.this.height;
        }
    };

    // ---- P6：草稿落盘与冲突 ----
    // 「应用全部」按钮（无改动或会话冻结时禁用）
    private @Nullable UiButton applyButton;
    // 撤销 / 重做按钮（显示下一次操作名，无历史时禁用）
    private @Nullable UiButton undoButton;
    private @Nullable UiButton redoButton;
    // 已弹过冲突窗的目标 id（避免每帧重复弹；解决后移除）
    private final Set<String> promptedConflicts = new LinkedHashSet<>();
    // 上一帧的可编辑状态（冻结切换时刷新表单使能）
    private boolean lastEditable = true;
    // 冻结提示只提示一次
    private boolean frozenNotified;

    // 草稿被修改后需要在下一帧刷新列表
    private boolean listNeedsRefresh;
    // 注册表候选建议（惰性创建；创建失败时退化为无建议）
    private @Nullable SuggestionProvider catalogSuggestions;

    // 内容区（每帧重算，供命中测试复用）
    private UiRect contentArea = new UiRect(0, 0, 0, 0);

    // 构造：由 {@link RuleEditorOpener} 在服务端授权成功后调用
    public RuleEditorScreen(EditorWorkspaceView workspace) {
        super(Component.translatable(UI + "title"));
        BuiltinEditorDescriptors.bootstrap();
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.model = new RuleEditorModel(workspace);
        // 焦点变化时播报控件的可读名称（未开启朗读时 GameNarrator 内部会静默）
        focus.setListener((previous, next) -> {
            UiNarration.focus(next);
            focus.setSpaceActivates(!(next instanceof UiTextInput)
                    && !(next instanceof com.meteorite.itemdespawntowhat.client.ui.widget.UiListEditor));
            if (next != null && pages != null) {
                pages.ensureVisible(next);
            }
        });
        this.seenResultSeq = workspace.resultSeq();
        this.model.refresh();
    }

    // 编辑器不做游戏内暂停
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- 初始化 ----

    @Override
    protected void init() {
        boolean preserveInputs = pages != null && pages.blockNavigation();
        super.init();
        searchField = new UiTextInput(font, Component.translatable(UI + "list.search"));
        searchField.setMaxLength(128);
        searchField.setValue(lastQuery);
        filterControl = new UiSegmentedControl(font, filterOptions());
        filterControl.setSelected(filterOptions().get(filterIndex).value());
        filterControl.setOnChanged(value -> {
            filterIndex = indexOfFilter(value);
            refreshList();
        });
        ruleList = new UiListView<>(font, this::renderRuleRow);
        ruleList.setRowHeight(24);
        ruleList.setEmptyMessage(Component.translatable(UI + "list.empty"));
        ruleList.setOnActivate(this::openEditor);
        tabControl = new UiSegmentedControl(font, tabOptions());
        tabControl.setSelected(tab.value());
        tabControl.setOnChanged(this::switchTab);
        buildListButtons();
        buildFooterButtons();
        refreshList();
        if (!preserveInputs) {
            rebuildEdit();
        }
        rebuildFocus();
    }

    private List<UiSegmentedControl.Option> filterOptions() {
        return List.of(
                new UiSegmentedControl.Option("all", Component.translatable(UI + "filter.all")),
                new UiSegmentedControl.Option("enabled", Component.translatable(UI + "filter.enabled")),
                new UiSegmentedControl.Option("disabled", Component.translatable(UI + "filter.disabled")),
                new UiSegmentedControl.Option("issue", Component.translatable(UI + "filter.issue")));
    }

    private int indexOfFilter(String value) {
        for (int i = 0; i < 4; i++) {
            if (filterOptions().get(i).value().equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private List<UiSegmentedControl.Option> tabOptions() {
        List<UiSegmentedControl.Option> options = new ArrayList<>();
        for (RuleEditorEditPages.Page value : RuleEditorEditPages.Page.values()) {
            options.add(new UiSegmentedControl.Option(value.value(),
                    Component.translatable(UI + "tab." + value.value())));
        }
        return options;
    }

    // 切页：先结束进行中的预览并提交有效编辑，再重建目标页
    private void switchTab(String value) {
        RuleEditorEditPages.Page target = RuleEditorEditPages.Page.of(value);
        if (target == tab) {
            return;
        }
        if (pages != null) {
            if (pages.blockNavigation()) {
                if (tabControl != null) {
                    tabControl.setSelected(tab.value());
                }
                return;
            }
            pages.setPage(target);
        }
        tab = target;
        rebuildFocus();
    }

    // ---- 按钮 ----

    private void buildListButtons() {
        listButtons.clear();
        listButtons.add(button(UI + "button.new", UiButtonVariant.PRIMARY, this::promptNewRule));
        listButtons.add(button(UI + "button.template", UiButtonVariant.SECONDARY, this::openTemplatePicker));
        listButtons.add(button(UI + "button.duplicate", UiButtonVariant.SECONDARY, this::promptDuplicate));
        listButtons.add(button(UI + "button.edit", UiButtonVariant.PRIMARY, () -> {
            String id = selectedRuleId();
            if (id != null) {
                openEditor(id);
            }
        }));
        listButtons.add(button(UI + "button.toggle", UiButtonVariant.SECONDARY, this::toggleSelectedEnabled));
        listButtons.add(button(UI + "button.mask", UiButtonVariant.SECONDARY, this::maskSelected));
        listButtons.add(button(UI + "button.restore", UiButtonVariant.SECONDARY, this::restoreSelected));
        listButtons.add(button(UI + "button.delete", UiButtonVariant.DANGER, this::deleteSelected));
        this.applyButton = button(UI + "button.apply", UiButtonVariant.PRIMARY, this::save);
        listButtons.add(this.applyButton);
        listBar.set(listButtons);
    }

    private void buildFooterButtons() {
        footerButtons.clear();
        footerButtons.add(button(UI + "button.save", UiButtonVariant.PRIMARY, this::save));
        this.undoButton = button(UI + "button.undo", UiButtonVariant.SECONDARY, this::undo);
        this.redoButton = button(UI + "button.redo", UiButtonVariant.SECONDARY, this::redo);
        footerButtons.add(this.undoButton);
        footerButtons.add(this.redoButton);
        footerButtons.add(button(UI + "button.back", UiButtonVariant.SECONDARY, this::backToList));
        footerBar.set(footerButtons);
    }

    private UiButton button(String labelKey, UiButtonVariant variant, Runnable action) {
        return new UiButton(font, Component.translatable(labelKey), variant, action::run);
    }

    // ---- 渲染 ----

    // 每帧重算布局并绘制
    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        int w = this.width;
        int h = this.height;
        modals.setBounds(0, 0, w, h);
        UiTheme.drawWindow(graphics, new UiRect(0, 0, w, h));
        UiTheme.drawDivider(graphics, 0, HEADER_H, w);
        drawHeaderText(graphics);
        int y = HEADER_H + 2;
        if (mode == Mode.EDIT && tabControl != null) {
            tabControl.setBounds(PAD, y, Math.clamp(w - PAD * 2, 0, 240), TAB_H);
            tabControl.render(graphics, font, mouseX, mouseY);
            y += TAB_H + 2;
        }
        int availableWidth = Math.max(0, w - PAD * 2);
        int footerHeight = (mode == Mode.LIST ? listBar : footerBar).preferredHeight(availableWidth);
        int footerY = h - PAD - footerHeight;
        int noticeY = footerY - 1 - NOTICE_H;
        int summaryY = noticeY - (mode == Mode.EDIT ? SUMMARY_H : 0);
        contentArea = new UiRect(PAD, y, availableWidth, Math.max(0, summaryY - y - 1));
        if (mode == Mode.LIST) {
            renderListMode(graphics, mouseX, mouseY);
        } else {
            renderEditMode(graphics, mouseX, mouseY);
            drawSummary(graphics, summaryY, w);
        }
        drawNotice(graphics, noticeY, w);
        if (mode == Mode.LIST) {
            listBar.layout(footerY, Math.max(0, w - PAD * 2));
            listBar.render(graphics, font, mouseX, mouseY);
        } else {
            if (!footerButtons.isEmpty()) {
                footerButtons.getFirst().setEnabled(workspace.active());
            }
            footerBar.layout(footerY, Math.max(0, w - PAD * 2));
            footerBar.render(graphics, font, mouseX, mouseY);
        }
        modals.render(graphics, font, mouseX, mouseY);
        if (modals.isEmpty()) {
            Component tip = null;
            if (notice != null && new UiRect(PAD, noticeY, availableWidth, NOTICE_H).contains(mouseX, mouseY)) {
                tip = notice;
            } else if (mode == Mode.EDIT && new UiRect(PAD, summaryY, availableWidth, SUMMARY_H).contains(mouseX, mouseY)) {
                EditSession session = editingSession();
                if (session != null) {
                    tip = tab == RuleEditorEditPages.Page.RESULTS ? ConsumptionSummary.hintRule(session.draft().view())
                            : NaturalSummary.rule(session.draft().view());
                }
            } else if (mode == Mode.LIST && ruleList != null && ruleList.bounds().contains(mouseX, mouseY)) {
                int index = ruleList.itemIndexAt(mouseX, mouseY);
                if (index >= 0 && index < visibleIds.size()) {
                    String id = visibleIds.get(index);
                    tip = Component.literal(labelText(id) + "\n" + id);
                }
            }
            if (tip != null) {
                graphics.renderTooltip(font, tip, mouseX, mouseY);
            }
        }
        // 字段说明提示：没有弹窗时才显示，避免盖住上层内容
        if (modals.isEmpty() && mode == Mode.EDIT && pages != null) {
            Component tip = pages.tooltipAt(mouseX, mouseY);
            if (tip != null) {
                graphics.renderTooltip(font, tip, mouseX, mouseY);
            }
        }
    }

    // 顶部标题与状态
    private void drawHeaderText(GuiGraphics graphics) {
        String title = this.getTitle().getString();
        if (mode == Mode.EDIT && editingId != null) {
            title = title + " - " + editingId;
        }
        title = TextScroll.trimToWidth(font, title, Math.max(0, this.width - 96));
        graphics.drawString(font, title, PAD, 3, UiPalette.HEADER_TEXT, false);
        String state = Component.translatable(workspace.active() ? UI + "state.active" : UI + "state.freezing")
                .getString();
        graphics.drawString(font, state, this.width - PAD - font.width(state), 3, UiPalette.HEADER_TEXT, false);
    }

    // 底部状态提示
    private void drawNotice(GuiGraphics graphics, int y, int w) {
        if (notice == null) {
            return;
        }
        String text = TextScroll.trimToWidth(font, notice.getString(), Math.max(0, w - PAD * 2));
        graphics.drawString(font, text, PAD, y + 1, noticeColor, false);
    }

    // 摘要行：结果页显示消耗语义提示（顶层 effects 或候选效果合并），其余页显示自然语言摘要
    private void drawSummary(GuiGraphics graphics, int y, int w) {
        EditSession session = editingSession();
        if (session == null) {
            return;
        }
        Component text;
        if (tab == RuleEditorEditPages.Page.RESULTS) {
            text = ConsumptionSummary.hintRule(session.draft().view());
        } else {
            text = NaturalSummary.rule(session.draft().view());
        }
        if (text.getString().isEmpty()) {
            return;
        }
        String trimmed = TextScroll.trimToWidth(font, text.getString(), Math.max(0, w - PAD * 2));
        graphics.drawString(font, trimmed, PAD, y + 1, UiPalette.TEXT_SECONDARY, false);
    }

    // 管理页布局
    private void renderListMode(GuiGraphics graphics, int mouseX, int mouseY) {
        UiRect area = contentArea;
        int searchWidth = Math.min(160, area.width());
        if (searchField != null) {
            searchField.setBounds(area.x(), area.y(), searchWidth, ROW_H);
            searchField.render(graphics, font, mouseX, mouseY);
        }
        if (filterControl != null) {
            filterControl.setBounds(area.x() + searchWidth + 2, area.y(),
                    Math.max(0, area.width() - searchWidth - 2), ROW_H);
            filterControl.render(graphics, font, mouseX, mouseY);
        }
        int listY = area.y() + ROW_H + 2;
        int listHeight = Math.max(0, area.height() - ROW_H - 2);
        if (ruleList != null) {
            ruleList.setBounds(area.x(), listY, area.width(), listHeight);
            ruleList.render(graphics, font, mouseX, mouseY);
        }
    }

    // 编辑页布局：四个页签各自在内容区里排布控件（含窄屏分层）
    private void renderEditMode(GuiGraphics graphics, int mouseX, int mouseY) {
        lastEditArea = new UiRect(contentArea.x(), contentArea.y(), contentArea.width(), contentArea.height());
        if (pages == null) {
            return;
        }
        pages.layout(lastEditArea);
        pages.render(graphics, font, mouseX, mouseY);
    }

    // 规则列表行
    private void renderRuleRow(GuiGraphics graphics, Font rowFont, String id, int index, UiRect row,
            boolean selected, boolean hovered, boolean focused) {
        RuleSnapshotEntry entry = model.entry(id);
        EditSession session = model.session(id);
        String marker = statusMark(entry);
        if (session != null && (session.isDirty() || model.isRestoring(id))) {
            marker = isRuleActive(id, entry) ? "[+] " : "[-] ";
        }
        int color = UiPalette.TEXT_PRIMARY;
        if (entry != null) {
            if (RuleSnapshotEntry.STATUS_DISABLED.equals(entry.status())
                    || RuleSnapshotEntry.STATUS_MASKED.equals(entry.status())) {
                color = UiPalette.TEXT_DISABLED;
            } else if (!entry.editable()) {
                color = UiPalette.TEXT_SECONDARY;
            }
        }
        if (session != null && session.isDirty()) {
            marker = marker + Component.translatable(UI + "list.dirty").getString();
        }
        String tag = model.hasError(id) ? Component.translatable(UI + "list.error").getString() : "";
        int width = Math.max(0, row.width() - 4 - (tag.isEmpty() ? 0 : rowFont.width(tag) + 4));
        String label = labelText(id);
        String text = TextScroll.trimToWidth(rowFont, marker + (label.isBlank() ? id : label), width);
        graphics.drawString(rowFont, text, row.x() + 2, row.y() + 2, color, false);
        graphics.drawString(rowFont, TextScroll.trimToWidth(rowFont, id, Math.max(0, row.width() - 4)),
                row.x() + 2, row.y() + 13, UiPalette.TEXT_SECONDARY, false);
        if (!tag.isEmpty()) {
            graphics.drawString(rowFont, tag, row.right() - 2 - rowFont.width(tag), row.y() + 2,
                    UiPalette.DANGER, false);
        }
    }

    // 状态文字标记（颜色必须配文字）
    private String statusMark(@Nullable RuleSnapshotEntry entry) {
        if (entry == null) {
            return "[*] ";
        }
        return switch (entry.status()) {
            case RuleSnapshotEntry.STATUS_ACTIVE -> "[+] ";
            case RuleSnapshotEntry.STATUS_DISABLED -> "[-] ";
            case RuleSnapshotEntry.STATUS_MASKED -> "[x] ";
            case RuleSnapshotEntry.STATUS_INVALID -> "[!] ";
            default -> "[?] ";
        };
    }

    // 效果列表行
    // ---- 列表维护 ----

    private void refreshList() {
        if (ruleList == null) {
            return;
        }
        String selectedId = selectedRuleId();
        visibleIds.clear();
        String query = lastQuery == null ? "" : lastQuery.toLowerCase(Locale.ROOT).trim();
        for (String id : model.ruleIds()) {
            RuleSnapshotEntry entry = model.entry(id);
            if (!matchesFilter(id, entry)) {
                continue;
            }
            if (!query.isEmpty()
                    && !id.toLowerCase(Locale.ROOT).contains(query)
                    && !labelText(id).toLowerCase(Locale.ROOT).contains(query)
                    && !matchesBodyQuery(id, query)) {
                continue;
            }
            visibleIds.add(id);
        }
        ruleList.setItems(new ArrayList<>(visibleIds));
        ruleList.setSelectedIndex(visibleIds.isEmpty() ? -1 : Math.max(0, visibleIds.indexOf(selectedId)));
    }

    // 当前草稿中的资源 ID、备注与参数也参与搜索；不触发目录查询。
    private boolean matchesBodyQuery(String id, String query) {
        JsonObject body = model.displayBody(id);
        return body != null && body.toString().toLowerCase(Locale.ROOT).contains(query);
    }

    private boolean matchesFilter(String id, @Nullable RuleSnapshotEntry entry) {
        boolean active = isRuleActive(id, entry);
        return switch (filterIndex) {
            case 1 -> active;
            case 2 -> !active;
            case 3 -> entry != null && !entry.issues().isEmpty() || model.hasError(id);
            default -> true;
        };
    }

    // 尚未修改的条目以快照状态为准；改动、恢复与新建条目以当前草稿为准。
    private boolean isRuleActive(String id, @Nullable RuleSnapshotEntry entry) {
        EditSession session = model.session(id);
        if (session != null && (session.isDirty() || model.isCreated(id) || model.isRestoring(id))) {
            return !session.draft().isDeleted() && session.draft().getBoolean(RuleFields.ENABLED, true);
        }
        return entry == null || RuleSnapshotEntry.STATUS_ACTIVE.equals(entry.status());
    }

    private String labelText(String id) {
        JsonObject body = model.displayBody(id);
        return body == null ? "" : RuleNaming.ruleTitle(body, RuleDisplayLabels::label).getString();
    }

    private @Nullable String selectedRuleId() {
        return ruleList == null ? null : ruleList.selectedItem();
    }

    // 搜索框内容变化后重新过滤
    private void syncSearch() {
        if (searchField == null) {
            return;
        }
        String value = searchField.value();
        if (!value.equals(lastQuery)) {
            lastQuery = value;
            refreshList();
        }
    }

    // ---- 草稿与表单 ----

    private @Nullable EditSession editingSession() {
        return editingId == null ? null : model.session(editingId);
    }

    private void rebuildEdit() {
        EditSession session = editingSession();
        if (mode != Mode.EDIT || session == null) {
            pages = null;
            return;
        }
        if (pages == null) {
            pages = new RuleEditorEditPages(pagesHost);
        }
        pages.setPage(tab);
        pages.rebuild();
        if (lastEditArea.width() > 0) {
            pages.layout(lastEditArea);
        }
        // 页面控件是刚重建的，按当前工作区状态同步一次使能（APPLYING 期间必须冻结）
        lastEditable = workspace.active();
        applyEditableStateNow(lastEditable);
    }

    // 提交当前页所有可见表单的待写字段（切页 / 返回 / 应用 / 关屏前调用）
    private void applyAllForms() {
        if (pages != null) {
            pages.applyToDraft();
        }
    }

    private void onDraftChanged() {
        listNeedsRefresh = true;
    }

    // 建议提供者（注册表候选），构造失败时降级为无建议
    private SuggestionProvider suggestionProvider() {
        if (catalogSuggestions == null) {
            try {
                catalogSuggestions = new CatalogSuggestions();
            } catch (RuntimeException | LinkageError error) {
                catalogSuggestions = null;
            }
        }
        return catalogSuggestions == null ? field -> List.of() : catalogSuggestions;
    }

    // 条件树宿主能力（页签与效果级条件共用）
    private ConditionSupport pageConditionSupport() {
        return new ConditionSupport() {
            @Override
            public List<UiConditionTreeEditor.TypeOption> typeOptions() {
                List<UiConditionTreeEditor.TypeOption> options = new ArrayList<>();
                for (TypeEditorDescriptor descriptor : ConditionEditorRegistry.all()) {
                    options.add(new UiConditionTreeEditor.TypeOption(descriptor.id(), descriptor.label()));
                }
                return options;
            }

            @Override
            public UiConditionTreeEditor.LeafFactory leafFactory() {
                return ClientTypeRegistries::leafNode;
            }

            @Override
            public Consumer<ConditionNode.Leaf> onEditLeaf() {
                // 叶参数编辑由四页编辑流程自己实现（它知道发起编辑的那个表单实例）
                return leaf -> {
                    if (pages != null) {
                        pages.editConditionLeaf(leaf);
                    }
                };
            }

            @Override
            public TypeRegistry<ConditionType<?>> registry() {
                return ClientTypeRegistries.conditions();
            }
        };
    }

    // ---- 模式切换 ----

    private void openEditor(String id) {
        if (rejectWhenFrozen()) {
            return;
        }
        if (id == null) {
            return;
        }
        RuleSnapshotEntry entry = model.entry(id);
        if (entry != null && (RuleSnapshotEntry.STATUS_INVALID.equals(entry.status()) || !entry.editable()
                || entry.issues().stream().anyMatch(issue -> RuleIssue.SEVERITY_ERROR.equals(issue.severity())))) {
            showRuleDetails(entry);
            return;
        }
        if (model.openSession(id) == null) {
            if (entry != null) {
                showRuleDetails(entry);
                return;
            }
            setNotice(Component.translatable(UI + "notice.rule_unavailable"), UiPalette.WARNING);
            return;
        }
        if (pages != null && pages.blockNavigation()) {
            return;
        }
        editingId = id;
        mode = Mode.EDIT;
        tab = RuleEditorEditPages.Page.INFO;
        // 换规则时重建页面状态（候选与效果选择归零）
        pages = null;
        if (tabControl != null) {
            tabControl.setSelected(tab.value());
        }
        rebuildEdit();
        rebuildFocus();
    }

    // 损坏规则显示原始视图与服务端问题，不创建会被表单改写的草稿。
    private void showRuleDetails(RuleSnapshotEntry entry) {
        UiModal modal = UiModal.create(font);
        modal.title(Component.translatable(UI + "detail.rule", entry.id().toString()));
        modal.message(Component.translatable(UI + (RuleSnapshotEntry.STATUS_INVALID.equals(entry.status())
                ? "detail.invalid" : "detail.unavailable")));
        String raw = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(entry.toJson());
        modal.contentWidget(new ReadOnlyTextView(font, Component.literal(raw)), Math.clamp(height - 90, 36, 180));
        modal.cancel(Component.translatable(UI + "button.close"));
        modal.layoutCentered(width, height);
        modals.push(modal);
    }

    // 返回管理页：先结束交互并卸载页面，再刷新列表
    private void backToList() {
        if (pages != null) {
            if (pages.blockNavigation()) {
                return;
            }
            pages.unmount();
        }
        pages = null;
        mode = Mode.LIST;
        editingId = null;
        refreshList();
        rebuildFocus();
    }

    // ---- 管理页动作 ----

    private void promptNewRule() {
        promptNewRule(null);
    }

    private void promptNewRule(@Nullable String templateId) {
        if (rejectWhenFrozen()) {
            return;
        }
        promptText(UI + "prompt.new_title", "itemdespawntowhat:new_rule",
                value -> createRule(value, templateId));
    }

    private void createRule(String value, @Nullable String templateId) {
        if (rejectWhenFrozen()) {
            return;
        }
        ResourceLocation id = normalizeId(value);
        if (id == null) {
            setNotice(Component.translatable(UI + "notice.invalid_id"), UiPalette.DANGER);
            return;
        }
        String key = id.toString();
        if (model.entry(key) != null || model.isCreated(key)) {
            setNotice(Component.translatable(UI + "notice.duplicate_id"), UiPalette.DANGER);
            return;
        }
        JsonObject body;
        if (templateId == null) {
            body = BuiltinEditorDefaults.ruleBody(key);
        } else {
            JsonObject loaded = RuleTemplateHooks.load(ResourceLocation.tryParse(templateId));
            if (loaded == null) {
                setNotice(Component.translatable(UI + "notice.template_failed"), UiPalette.DANGER);
                return;
            }
            body = BuiltinEditorDefaults.copyOf(key, loaded);
            // 模板里的 enabled=false 只用于样本演示，从模板新建的规则一律启用
            body.addProperty(RuleFields.ENABLED, true);
        }
        model.createSession(key, body);
        refreshList();
        openEditor(key);
        setNotice(Component.translatable(UI + "notice.created"), UiPalette.SUCCESS);
    }

    private void promptDuplicate() {
        if (rejectWhenFrozen()) {
            return;
        }
        String sourceId = selectedRuleId();
        if (sourceId == null) {
            noSelection();
            return;
        }
        EditSession session = model.openSession(sourceId);
        if (session == null) {
            noSelection();
            return;
        }
        JsonObject body = session.draft().toJson();
        String candidate = sourceId + "_copy";
        int suffix = 2;
        while (model.entry(candidate) != null || model.isCreated(candidate)) {
            candidate = sourceId + "_copy" + suffix;
            suffix++;
        }
        promptText(UI + "prompt.duplicate_title", candidate, value -> {
            // 弹窗期间可能已被冻结（应用在途 / 会话结束），提交前再确认一次
            if (rejectWhenFrozen()) {
                return;
            }
            ResourceLocation id = normalizeId(value);
            if (id == null) {
                setNotice(Component.translatable(UI + "notice.invalid_id"), UiPalette.DANGER);
                return;
            }
            String key = id.toString();
            if (model.entry(key) != null || model.isCreated(key)) {
                setNotice(Component.translatable(UI + "notice.duplicate_id"), UiPalette.DANGER);
                return;
            }
            model.createSession(key, BuiltinEditorDefaults.copyOf(key, body));
            refreshList();
            openEditor(key);
            setNotice(Component.translatable(UI + "notice.created"), UiPalette.SUCCESS);
        });
    }

    // 从内置模板新建
    private void openTemplatePicker() {
        if (rejectWhenFrozen()) {
            return;
        }
        List<Suggestion> templates = RuleTemplateHooks.list();
        if (templates.isEmpty()) {
            setNotice(Component.translatable(UI + "notice.template_empty"), UiPalette.WARNING);
            return;
        }
        UiModal modal = UiModal.create(font);
        modal.title(Component.translatable(UI + "prompt.template_title"));
        UiListView<Suggestion> view = new UiListView<>(font, this::renderTemplateRow);
        view.setItems(templates);
        view.setRowHeight(ROW_H);
        view.setEmptyMessage(Component.translatable(UI + "list.empty"));
        view.setOnActivate(suggestion -> {
            modals.closeTop();
            promptNewRule(suggestion.value());
        });
        int height = Math.clamp((long) templates.size() * ROW_H, ROW_H, 140);
        modal.contentWidget(view, height);
        modal.cancel(Component.translatable(UI + "button.cancel"));
        modal.layoutCentered(this.width, this.height);
        modals.push(modal);
    }

    private void renderTemplateRow(GuiGraphics graphics, Font rowFont, Suggestion item, int index, UiRect row,
            boolean selected, boolean hovered, boolean focused) {
        String label = TextScroll.trimToWidth(rowFont, item.label().getString(), Math.max(0, row.width() - 4));
        graphics.drawString(rowFont, label, row.x() + 2, row.y() + 2, UiPalette.TEXT_PRIMARY, false);
    }

    private void toggleSelectedEnabled() {
        if (rejectWhenFrozen()) {
            return;
        }
        String id = selectedRuleId();
        if (id == null) {
            noSelection();
            return;
        }
        EditSession session = model.openSession(id);
        if (session == null) {
            noSelection();
            return;
        }
        RuleDraft draft = session.draft();
        boolean enabled = !draft.getBoolean(RuleFields.ENABLED, true);
        session.apply(EditSession.OP_SET_FIELD, () -> draft.setBoolean(RuleFields.ENABLED, enabled));
        refreshList();
        setNotice(Component.translatable(enabled ? UI + "notice.enabled" : UI + "notice.disabled"), UiPalette.SUCCESS);
    }

    private void maskSelected() {
        if (rejectWhenFrozen()) {
            return;
        }
        String id = selectedRuleId();
        if (id == null) {
            noSelection();
            return;
        }
        EditSession session = model.openSession(id);
        if (session == null) {
            noSelection();
            return;
        }
        RuleDraft draft = session.draft();
        boolean masked = !draft.getBoolean(RuleFields.DELETE, false);
        session.apply(EditSession.OP_SET_FIELD, () -> draft.setBoolean(RuleFields.DELETE, masked));
        refreshList();
        setNotice(Component.translatable(masked ? UI + "notice.masked" : UI + "notice.unmasked"), UiPalette.SUCCESS);
    }

    private void restoreSelected() {
        if (rejectWhenFrozen()) {
            return;
        }
        String id = selectedRuleId();
        if (id == null) {
            noSelection();
            return;
        }
        EditSession session = model.openSession(id);
        if (session == null) {
            noSelection();
            return;
        }
        // 走统一门面：「恢复原始版本」也要能撤销
        session.apply(EditSession.OP_RESTORE_ORIGINAL, () -> model.markRestore(id));
        refreshList();
        setNotice(Component.translatable(UI + "notice.restored"), UiPalette.SUCCESS);
    }

    private void deleteSelected() {
        if (rejectWhenFrozen()) {
            return;
        }
        String id = selectedRuleId();
        if (id == null) {
            noSelection();
            return;
        }
        if (model.isCreated(id)) {
            confirm(UI + "confirm.delete_title", UI + "confirm.delete_draft_message", () -> {
                if (rejectWhenFrozen()) {
                    return;
                }
                model.dropSession(id);
                refreshList();
                setNotice(Component.translatable(UI + "notice.deleted"), UiPalette.SUCCESS);
            });
            return;
        }
        confirm(UI + "confirm.delete_title", UI + "confirm.delete_message", () -> {
            if (rejectWhenFrozen()) {
                return;
            }
            model.openSession(id);
            model.markDeleted(id, true);
            refreshList();
            setNotice(Component.translatable(UI + "notice.deleted"), UiPalette.SUCCESS);
        });
    }

    // ---- 效果动作 ----

    // ---- 保存 / 撤销 ----

    private void save() {
        // F-I：先把当前草稿落盘（写盘本身有 2 秒节流），保证崩溃窗口内的最近编辑不丢
        if (pages != null && pages.blockNavigation()) {
            return;
        }
        applyAllForms();
        model.persistNow();
        List<FormIssue> issues = collectIssues();
        // 只有「本地可证非法」才阻塞保存；未注册第三方类型等提醒项不阻塞（服务端才是权威校验方）
        FormIssue blocker = null;
        for (FormIssue issue : issues) {
            if (issue.blocking()) {
                blocker = issue;
                break;
            }
        }
        if (blocker != null) {
            // 字段级问题带上字段名，玩家才知道该改哪里（label 与 message 同一实例时说明它本身就是说明文案）
            Component notice = blocker.label() == blocker.message()
                    ? blocker.message()
                    : Component.translatable(UI + "notice.issue", blocker.label(), blocker.message());
            setNotice(notice, UiPalette.DANGER);
            if (issueRuleId != null && !issueRuleId.equals(editingId)) {
                openEditor(issueRuleId);
            }
            revealIssue(blocker);
            return;
        }
        String json = model.buildChangeSetJson();
        if (json == null) {
            setNotice(Component.translatable(UI + "notice.no_changes"), UiPalette.TEXT_SECONDARY);
            return;
        }
        if (!workspace.active()) {
            setNotice(Component.translatable(UI + "notice.frozen"), UiPalette.WARNING);
            return;
        }
        if (workspace.save(workspace.newOperationId(), json)) {
            setNotice(Component.translatable(UI + "notice.applying"), UiPalette.TEXT_SECONDARY);
            if (!issues.isEmpty()) {
                // 提醒项不阻塞保存，但把玩家带到第一条上（例如未注册的第三方条件类型）
                revealIssue(issues.getFirst());
            }
        } else {
            setNotice(Component.translatable(UI + "notice.save_rejected"), UiPalette.DANGER);
        }
    }

    private void undo() {
        if (rejectWhenFrozen()) {
            return;
        }
        if (pages != null && pages.blockNavigation()) {
            return;
        }
        EditSession session = editingSession();
        if (session != null && session.canUndo()) {
            session.undo();
            rebuildEdit();
            rebuildFocus();
        }
    }

    private void redo() {
        if (rejectWhenFrozen()) {
            return;
        }
        if (pages != null && pages.blockNavigation()) {
            return;
        }
        EditSession session = editingSession();
        if (session != null && session.canRedo()) {
            session.redo();
            rebuildEdit();
            rebuildFocus();
        }
    }

    private @Nullable String issueRuleId;

    private List<FormIssue> collectIssues() {
        issueRuleId = editingId;
        List<FormIssue> issues = new ArrayList<>();
        EditSession session = editingSession();
        if (session != null && !session.draft().isDeleted() && !model.isRestoring(editingId)) {
            issues.addAll(localIssues(session.draft()));
        }
        for (String id : model.dirtyRuleIds()) {
            if (id.equals(editingId)) {
                continue;
            }
            EditSession other = model.session(id);
            if (other != null && !other.draft().isDeleted() && !model.isRestoring(id)) {
                List<FormIssue> otherIssues = localIssues(other.draft());
                if (issues.stream().noneMatch(FormIssue::blocking) && otherIssues.stream().anyMatch(FormIssue::blocking)) {
                    issueRuleId = id;
                }
                issues.addAll(otherIssues);
            }
        }
        if (pages != null) {
            issues.addAll(pages.issues());
        }
        return issues;
    }

    // 本地保存前拦截（forms.md §9 的可本地判定项；服务端仍是权威校验方）
    private List<FormIssue> localIssues(RuleDraft draft) {
        List<FormIssue> issues = new ArrayList<>();
        JsonArray effects = jsonArray(draft.view().get(RuleFields.EFFECTS));
        JsonArray outcomes = jsonArray(draft.view().get(RuleFields.OUTCOMES));
        if (effects.isEmpty() && outcomes.isEmpty()) {
            issues.add(issue(RuleFields.EFFECTS, UI + "issue.effects_empty"));
        } else if (effects.size() > 32) {
            issues.add(issue(RuleFields.EFFECTS, UI + "issue.effects_limit"));
        }
        // effects 与 outcomes 二选一：同时声明会被服务端拒绝，本地先拦
        if (!effects.isEmpty() && !outcomes.isEmpty()) {
            issues.add(issue(RuleFields.OUTCOMES, UI + "issue.outcomes_conflict"));
        }
        // 规则级条件树
        validateConditionTree(draft, "", RuleFields.CONDITIONS, issues);
        // 效果级条件树：切到别的效果页签时也要能拦住，否则只在服务端报 VALIDATION_FAILED
        for (int i = 0; i < effects.size(); i++) {
            JsonElement element = effects.get(i);
            if (!element.isJsonObject()) {
                continue;
            }
            // rawConditionsAt 需要指向 conditions 元素本身
            String path = RuleFields.EFFECTS + "[" + i + "]." + RuleFields.CONDITIONS;
            validateConditionTree(draft, path, path, issues);
        }
        // 候选结果：id 非空且唯一、effects 非空；候选内效果级条件树同样本地校验
        Set<String> candidateIds = new LinkedHashSet<>();
        for (int i = 0; i < outcomes.size(); i++) {
            JsonElement element = outcomes.get(i);
            String candidatePath = RuleFields.OUTCOMES + "[" + i + "]";
            if (!element.isJsonObject()) {
                issues.add(issue(candidatePath, UI + "issue.candidate_id_invalid"));
                continue;
            }
            JsonObject candidate = element.getAsJsonObject();
            JsonElement idElement = candidate.get(RuleFields.CANDIDATE_ID);
            if (idElement == null || !idElement.isJsonPrimitive() || idElement.getAsString().isBlank()
                    || !candidateIds.add(idElement.getAsString())) {
                issues.add(issue(candidatePath + "." + RuleFields.CANDIDATE_ID, UI + "issue.candidate_id_invalid"));
            }
            JsonArray candidateEffects = jsonArray(candidate.get(RuleFields.CANDIDATE_EFFECTS));
            if (candidateEffects.isEmpty()) {
                issues.add(issue(candidatePath + "." + RuleFields.CANDIDATE_EFFECTS, UI + "issue.candidate_effects_empty"));
                continue;
            }
            for (int j = 0; j < candidateEffects.size(); j++) {
                if (!candidateEffects.get(j).isJsonObject()) {
                    continue;
                }
                String conditionsPath = candidatePath + "." + RuleFields.CANDIDATE_EFFECTS + "[" + j + "]."
                        + RuleFields.CONDITIONS;
                validateConditionTree(draft, conditionsPath, conditionsPath, issues);
            }
        }
        JsonElement name = draft.view().get(RuleFields.DISPLAY_NAME);
        if (name != null && name.isJsonPrimitive()
                && name.getAsString().codePointCount(0, name.getAsString().length()) > 128) {
            issues.add(issue(RuleFields.DISPLAY_NAME, UI + "issue.display_name_too_long"));
        }
        JsonElement seconds = draft.view().get(RuleFields.TRIGGER_AFTER_SECONDS);
        if (seconds != null && seconds.isJsonPrimitive() && seconds.getAsJsonPrimitive().isNumber()
                && seconds.getAsInt() < 0) {
            issues.add(issue(RuleFields.TRIGGER_AFTER_SECONDS, UI + "issue.trigger_negative"));
        }
        // 固定成本契约（与服务端 RuleValidation 对齐）：source_cost 必须为正数、catalyst_cost 不得为负数
        JsonElement sourceCost = draft.view().get(RuleFields.SOURCE_COST);
        if (isInteger(sourceCost) && sourceCost.getAsInt() <= 0) {
            issues.add(issue(RuleFields.SOURCE_COST, UI + "issue.source_cost_not_positive"));
        }
        // catalyst_cost 契约（与服务端 RuleValidation / RuleCodecs 对齐）：必须是对象 {items, count?, radius?}，
        // 整数写法已废弃；items 不能为空、count / radius 只在显式写出时做区间拦截
        JsonElement catalystCost = draft.view().get(RuleFields.CATALYST_COST);
        boolean hasCatalystCost = catalystCost != null && !catalystCost.isJsonNull();
        if (hasCatalystCost) {
            if (!catalystCost.isJsonObject()) {
                issues.add(issue(RuleFields.CATALYST_COST, UI + "issue.catalyst_cost_not_object"));
            } else {
                JsonObject cost = catalystCost.getAsJsonObject();
                if (jsonArray(cost.get(RuleFields.CATALYST_ITEMS)).isEmpty()) {
                    issues.add(issue(RuleFields.CATALYST_COST + "." + RuleFields.CATALYST_ITEMS,
                            UI + "issue.catalyst_cost_items_empty"));
                }
                JsonElement countElement = cost.get(RuleFields.CATALYST_COUNT);
                if (isInteger(countElement) && (countElement.getAsInt() < CatalystCost.MIN_COUNT
                        || countElement.getAsInt() > CatalystCost.MAX_COUNT)) {
                    issues.add(issue(RuleFields.CATALYST_COST + "." + RuleFields.CATALYST_COUNT,
                            UI + "issue.catalyst_cost_count_out_of_range"));
                }
                JsonElement radiusElement = cost.get(RuleFields.CATALYST_RADIUS);
                if (isInteger(radiusElement) && (radiusElement.getAsInt() < CatalystCost.MIN_RADIUS
                        || radiusElement.getAsInt() > CatalystCost.MAX_RADIUS)) {
                    issues.add(issue(RuleFields.CATALYST_COST + "." + RuleFields.CATALYST_RADIUS,
                            UI + "issue.catalyst_cost_radius_out_of_range"));
                }
            }
        }
        // 结构版本：当前只支持 RuleCodecs.DEFAULT_SCHEMA_VERSION
        JsonElement schemaVersion = draft.view().get(RuleFields.SCHEMA_VERSION);
        if (isInteger(schemaVersion) && schemaVersion.getAsInt() != RuleCodecs.DEFAULT_SCHEMA_VERSION) {
            issues.add(issue(RuleFields.SCHEMA_VERSION, UI + "issue.schema_version_unsupported"));
        }
        // 消耗效果统计覆盖顶层 effects 与候选结果内 effects
        Map<String, Integer> consumption = new LinkedHashMap<>();
        countConsumption(effects, consumption);
        for (JsonElement element : outcomes) {
            if (element.isJsonObject()) {
                countConsumption(jsonArray(element.getAsJsonObject().get(RuleFields.CANDIDATE_EFFECTS)), consumption);
            }
        }
        for (Integer count : consumption.values()) {
            if (count != null && count > 1) {
                issues.add(issue(RuleFields.EFFECTS, UI + "issue.duplicate_consumption"));
            }
        }
        // 固定成本与同名消耗效果互斥（重复记账）
        if (isInteger(sourceCost) && consumption.containsKey(TypeLabels.OWN_NAMESPACE + ":consume_source")) {
            issues.add(issue(RuleFields.SOURCE_COST, UI + "issue.double_bookkeeping"));
        }
        if (hasCatalystCost && consumption.containsKey(TypeLabels.OWN_NAMESPACE + ":consume_catalyst")) {
            issues.add(issue(RuleFields.CATALYST_COST, UI + "issue.double_bookkeeping"));
        }
        return issues;
    }

    // 统计一组效果里的消耗类型（同一消耗效果在规则内只允许出现一次）
    private static void countConsumption(JsonArray effects, Map<String, Integer> consumption) {
        for (JsonElement element : effects) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonElement typeElement = element.getAsJsonObject().get(RuleFields.TYPE);
            if (typeElement == null || !typeElement.isJsonPrimitive()) {
                continue;
            }
            String type = typeElement.getAsString();
            if (type.startsWith(TypeLabels.OWN_NAMESPACE + ":consume_")) {
                consumption.merge(type, 1, Integer::sum);
            }
        }
    }

    // 读取草稿中的数组字段：非数组统一按空数组处理
    private static JsonArray jsonArray(@Nullable JsonElement raw) {
        return raw != null && raw.isJsonArray() ? raw.getAsJsonArray() : new JsonArray();
    }

    // 是否为数值 JSON：成本与结构版本的本地拦截只处理显式写出的数值
    private static boolean isInteger(@Nullable JsonElement raw) {
        return raw != null && raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isNumber();
    }

    // 校验某条路径下的条件树（规则级传 ""，效果级传 "effects[i]"），问题路径指向 conditions 字段
    private void validateConditionTree(RuleDraft draft, String draftPath, String issuePath, List<FormIssue> issues) {
        if (draft.rawConditionsAt(draftPath) == null) {
            return;
        }
        ConditionExpression expression = draft.conditionsAt(draftPath, ClientTypeRegistries.conditions());
        if (expression == null) {
            // 只有本地可证结构损坏（不是对象 / 缺 op / op 不是字符串）才阻塞；
            // 未注册或第三方类型只提醒：原数据在草稿里原样保留，服务端才是权威校验方
            if (isStructurallyDamaged(draft.rawConditionsAt(draftPath))) {
                issues.add(issue(issuePath, UI + "issue.conditions_decode"));
            } else {
                issues.add(warning(issuePath, UI + "issue.conditions_unregistered"));
            }
            return;
        }
        if (!expression.isStructurallyValid()) {
            issues.add(issue(issuePath, ISSUE + "incomplete_group"));
        }
        if (expression.leafCount() > ConditionLimits.MAX_LEAVES) {
            issues.add(issue(issuePath, ISSUE + "too_many_leaves"));
        }
        if (expression.nodeCount() > ConditionLimits.MAX_NODES) {
            issues.add(issue(issuePath, ISSUE + "too_many_nodes"));
        }
        if (expression.depth() > ConditionLimits.MAX_DEPTH) {
            issues.add(issue(issuePath, ISSUE + "too_deep", expression.depth(), ConditionLimits.MAX_DEPTH));
        }
    }

    private FormIssue issue(String path, String messageKey) {
        return issue(path, messageKey, new Object[0]);
    }

    // 带占位符参数的校验项（阻塞保存）
    private FormIssue issue(String path, String messageKey, Object... args) {
        Component message = Component.translatable(messageKey, args);
        return FormIssue.error(path, message, message);
    }

    // 带占位符参数的提醒项（不阻塞保存）
    private FormIssue warning(String path, String messageKey, Object... args) {
        Component message = Component.translatable(messageKey, args);
        return FormIssue.warning(path, message, message);
    }

    // 条件树 JSON 是否本地可证损坏：不是对象、缺 op 或 op 不是原始值
    private static boolean isStructurallyDamaged(@Nullable JsonElement raw) {
        if (raw == null || raw.isJsonNull() || !raw.isJsonObject()) {
            return true;
        }
        JsonElement op = raw.getAsJsonObject().get(RuleFields.OP);
        return op == null || !op.isJsonPrimitive();
    }

    // 校验失败定位：先结束交互并提交当前页待写字段，再按路径切页并聚焦到具体字段
    // （规则 → 页签 → 候选 → 效果 → 条件 → 字段；路径到当前下标的映射由页面负责）
    private void revealIssue(FormIssue issue) {
        if (pages == null || issue == null) {
            return;
        }
        pages.endInteractions();
        applyAllForms();
        UiFocusTarget target = pages.reveal(issue.path());
        tab = pages.page();
        if (tabControl != null) {
            tabControl.setSelected(tab.value());
        }
        rebuildFocus();
        if (target != null) {
            focus.focusOn(target);
            pages.ensureVisible(target);
        }
    }

    // ---- 焦点 / 事件 / 生命周期 ----

    private void rebuildFocus() {
        focus.clear();
        focus.beginUpdate();
        if (mode == Mode.LIST) {
            if (searchField != null) {
                focus.add(searchField);
            }
            if (filterControl != null) {
                focus.add(filterControl);
            }
            if (ruleList != null) {
                focus.add(ruleList);
            }
            for (UiButton button : listButtons) {
                focus.add(button);
            }
        } else {
            if (tabControl != null) {
                focus.add(tabControl);
            }
            if (pages != null) {
                for (UiFocusTarget target : pages.focusTargets()) {
                    focus.add(target);
                }
            }
            for (UiButton button : footerButtons) {
                focus.add(button);
            }
        }
        focus.endUpdate();
        if (focus.focused() == null) {
            focus.focusFirst();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (modals.isVisible()) {
            return modals.mouseClicked(mouseX, mouseY, button);
        }
        if (button == 0) {
            focusClicked(mouseX, mouseY);
        }
        if (mode == Mode.LIST) {
            if (searchField != null && searchField.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (filterControl != null && filterControl.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (ruleList != null && ruleList.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return listBar.mouseClicked(mouseX, mouseY, button);
        }
        if (tabControl != null && tabControl.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (pages != null && pages.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (footerBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (modals.isVisible()) {
            return modals.mouseReleased(mouseX, mouseY, button);
        }
        boolean consumed = pages != null && pages.mouseReleased(mouseX, mouseY, button);
        consumed |= listBar.mouseReleased(mouseX, mouseY, button);
        consumed |= footerBar.mouseReleased(mouseX, mouseY, button);
        return consumed || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (modals.isVisible()) {
            return modals.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (pages != null && pages.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (mode == Mode.LIST && ruleList != null
                && ruleList.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (modals.isVisible()) {
            return modals.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (mode == Mode.LIST) {
            if (ruleList != null && ruleList.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        } else if (pages != null && pages.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (modals.isVisible()) {
            return modals.keyPressed(keyCode, scanCode, modifiers);
        }
        // Esc 先交给正在捕获输入的控件：行内精确输入回退缓冲、拖动中的数值取消预览；
        // 均未消费时才逐级取消（编辑页返回列表，列表页关闭屏幕）
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (focus.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (mode == Mode.EDIT && pages != null && pages.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (mode == Mode.EDIT) {
                backToList();
            } else {
                closeByUser();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB && focus.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (mode == Mode.LIST) {
            if (searchField != null && searchField.isFocused()
                    && searchField.keyPressed(keyCode, scanCode, modifiers)) {
                syncSearch();
                return true;
            }
            if (ruleList != null && ruleList.isFocused() && ruleList.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        } else if (pages != null && pages.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return focus.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    // 鼠标和键盘共用唯一焦点，裁剪区域外的控件不能夺取输入。
    private void focusClicked(double mouseX, double mouseY) {
        for (UiFocusTarget target : focus.targets()) {
            if (!target.canFocus() || !target.bounds().contains(mouseX, mouseY)) {
                continue;
            }
            if (pages != null && pages.focusTargets().contains(target) && !contentArea.contains(mouseX, mouseY)) {
                continue;
            }
            focus.focusOn(target);
            return;
        }
        focus.clearFocus();
    }

    // 键盘抬起：把滑杆方向键的按住过程合并为一次提交
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (mode == Mode.EDIT && pages != null && pages.keyReleased(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (modals.isVisible()) {
            return modals.charTyped(codePoint, modifiers);
        }
        if (mode == Mode.LIST && searchField != null && searchField.isFocused() && searchField.charTyped(codePoint, modifiers)) {
            syncSearch();
            return true;
        }
        if (mode == Mode.EDIT && pages != null && pages.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void tick() {
        modals.tick();
        // F-A：快照是打开屏幕之后才到的（打开时只发出了请求），每帧消费一次；
        // refresh() 内部按快照实例去重，重复调用无副作用
        if (model.refresh()) {
            refreshList();
            rebuildEdit();
            rebuildFocus();
        }
        if (listNeedsRefresh) {
            listNeedsRefresh = false;
            refreshList();
        }
        if (mode == Mode.EDIT && pages != null) {
            pages.tick();
        }
        pollWorkspace();
        // P6：节流落盘、恢复提示、按钮状态、冲突弹窗与冻结
        model.tickPersistence();
        if (model.consumeDraftFailures() > 0) {
            setNotice(Component.translatable(UI + "notice.draft_save_failed"), UiPalette.WARNING);
        }
        int restored = model.consumeRestoredCount();
        if (restored > 0) {
            setNotice(Component.translatable(UI + "notice.restored_draft", restored), UiPalette.WARNING);
        }
        updateActionButtons();
        promptConflicts();
        boolean editable = workspace.active();
        if (!editable && !frozenNotified) {
            frozenNotified = true;
            setNotice(Component.translatable(UI + "notice.frozen"), UiPalette.WARNING);
        } else if (editable) {
            frozenNotified = false;
        }
        applyEditableState(editable);
    }

    // 应用全部按钮与撤销/重做按钮的可用状态与标签
    private void updateActionButtons() {
        boolean editable = workspace.active();
        if (applyButton != null) {
            applyButton.setEnabled(editable && model.hasDirty());
        }
        if (!footerButtons.isEmpty()) {
            footerButtons.getFirst().setLabel(Component.translatable(UI + "button.apply_changes", model.dirtyRuleIds().size()));
        }
        if (applyButton != null) {
            applyButton.setLabel(Component.translatable(UI + "button.apply_changes", model.dirtyRuleIds().size()));
        }
        EditSession session = editingSession();
        boolean canUndo = editable && session != null && session.canUndo();
        boolean canRedo = editable && session != null && session.canRedo();
        if (undoButton != null) {
            undoButton.setEnabled(canUndo);
            String opKey = canUndo ? session.undoOpKey() : null;
            undoButton.setLabel(opKey == null
                    ? Component.translatable(UI + "button.undo")
                    : Component.translatable(UI + "button.undo_named", Component.translatable(opKey)));
        }
        if (redoButton != null) {
            redoButton.setEnabled(canRedo);
            String opKey = canRedo ? session.redoOpKey() : null;
            redoButton.setLabel(opKey == null
                    ? Component.translatable(UI + "button.redo")
                    : Component.translatable(UI + "button.redo_named", Component.translatable(opKey)));
        }
    }

    // 冻结（APPLYING / 非 ACTIVE）时禁用表单控件；状态切换时才真正遍历
    private void applyEditableState(boolean editable) {
        if (editable == lastEditable) {
            return;
        }
        lastEditable = editable;
        applyEditableStateNow(editable);
    }

    // 立即同步页面的使能状态（页面控件是新建出来的，默认可用，必须显式同步一次）
    private void applyEditableStateNow(boolean editable) {
        if (pages != null) {
            pages.setEnabled(editable);
        }
        // 列表页动作按钮同属可编辑面：冻结时一并禁用，避免「点了没反应」
        // （返回按钮不禁用，保证冻结期间玩家仍能退出屏幕）
        for (UiButton button : this.listButtons) {
            button.setEnabled(editable);
        }
        // 应用/撤销/重做再按脏标记与历史精确修正
        updateActionButtons();
    }

    // 冻结（APPLYING / 非 ACTIVE）时禁止一切会改动草稿的入口，并给出明确提示而不是静默丢弃；返回 true 表示已被冻结阻断
    private boolean rejectWhenFrozen() {
        if (workspace.active()) {
            return false;
        }
        setNotice(Component.translatable(UI + "notice.frozen"), UiPalette.WARNING);
        return true;
    }

    // 有冲突就弹窗（一次弹一个，已提示过的不重复弹）
    private void promptConflicts() {
        for (DraftConflict conflict : model.conflicts()) {
            if (promptedConflicts.add(conflict.targetId())) {
                openConflictModal(conflict);
                return;
            }
        }
    }

    // 冲突弹窗：保留草稿 / 丢弃草稿用服务端版本 / 稍后决定
    private void openConflictModal(DraftConflict conflict) {
        String id = conflict.targetId();
        UiModal modal = UiModal.create(font);
        modal.title(Component.translatable(UI + "conflict.title"));
        modal.message(Component.translatable(UI + "conflict.message." + conflict.messageSuffix(), id));
        modal.confirm(Component.translatable(UI + "conflict.keep"), () -> {
            model.resolveConflict(id, true);
            promptedConflicts.remove(id);
            refreshList();
            rebuildEdit();
            rebuildFocus();
            setNotice(Component.translatable(UI + "notice.conflict_kept"), UiPalette.TEXT_SECONDARY);
        });
        modal.addButton(new UiButton(font, Component.translatable(UI + "conflict.use_server"),
                UiButtonVariant.DANGER, () -> {
                    model.resolveConflict(id, false);
                    promptedConflicts.remove(id);
                    refreshList();
                    rebuildEdit();
                    rebuildFocus();
                    setNotice(Component.translatable(UI + "notice.conflict_discarded"), UiPalette.WARNING);
                }));
        modal.cancel(Component.translatable(UI + "conflict.later"));
        modal.layoutCentered(this.width, this.height);
        modals.push(modal);
    }

    // 外部重新打开时刷新界面（此时工作区已进入 ACTIVE 并发出快照请求）
    public void refreshFromWorkspace() {
        model.refresh();
        refreshList();
        rebuildEdit();
        rebuildFocus();
    }

    // 保存回执与工作区状态轮询
    private void pollWorkspace() {
        long sequence = workspace.resultSeq();
        if (sequence != seenResultSeq) {
            seenResultSeq = sequence;
            RuleSaveStatus status = workspace.lastStatus();
            model.refresh();
            if (status == RuleSaveStatus.SUCCESS || status == RuleSaveStatus.NO_CHANGES) {
                model.acceptSaved();
                setNotice(Component.translatable(status.messageKey()), UiPalette.SUCCESS);
            } else if (status == RuleSaveStatus.SAVED_NOT_RELOADED) {
                model.acceptSaved();
                setNotice(Component.translatable(status.messageKey()), UiPalette.WARNING);
            } else if (isSessionLost(status)) {
                // 失权/断线：取消未完成预览、停用写入、保留草稿；不从 GUI 重开会话，也不写盘
                cancelPreviews();
                Component reason = status == null ? Component.empty() : Component.translatable(status.messageKey());
                setNotice(reason.copy().append(" ").append(Component.translatable(UI + "notice.session_reopen")),
                        UiPalette.DANGER);
            } else if (status == RuleSaveStatus.VALIDATION_FAILED) {
                setNotice(Component.translatable(status.messageKey()), UiPalette.DANGER);
                locateValidationIssues();
            } else if (status != null) {
                setNotice(Component.translatable(status.messageKey(), workspace.lastMessageArgs().toArray()),
                        UiPalette.DANGER);
            }
            refreshList();
            rebuildEdit();
            rebuildFocus();
        }
        if (workspace.state() == RuleEditClientState.FREE && workspace.openRequest() == null) {
            this.onClose();
        }
    }

    // 会话失效状态码：取消预览、停用写入、保留草稿（失权或断线不写盘）
    private static boolean isSessionLost(@Nullable RuleSaveStatus status) {
        return status == RuleSaveStatus.NO_PERMISSION || status == RuleSaveStatus.LOCK_NOT_OWNED
                || status == RuleSaveStatus.LOCK_BUSY || status == RuleSaveStatus.SESSION_EXPIRED;
    }

    // 取消未完成交互：关闭弹窗并结束页面的活动捕获（旧草稿保留）
    private void cancelPreviews() {
        while (!modals.isEmpty()) {
            modals.closeTop();
        }
        if (pages != null) {
            pages.endInteractions();
        }
    }

    // 服务端 VALIDATION_FAILED 定位：规则 → 页签 → 候选 → 效果 → 条件 → 字段
    private void locateValidationIssues() {
        if (pages == null) {
            return;
        }
        for (RuleIssue issue : workspace.lastIssues()) {
            if (issue == null || !RuleIssue.SEVERITY_ERROR.equals(issue.severity())) {
                continue;
            }
            if (issue.ruleId() != null && editingId != null && !issue.ruleId().equals(editingId)) {
                continue;
            }
            String path = issue.fieldPath();
            if (path == null || path.isBlank()) {
                continue;
            }
            Component message = issue.messageCode().isBlank()
                    ? Component.literal(issue.fallbackMessage())
                    : Component.translatable(issue.messageCode(), issue.messageArgs().toArray());
            revealIssue(FormIssue.error(path, message, message));
            setNotice(Component.translatable(UI + "notice.validation_located", message), UiPalette.DANGER);
            return;
        }
    }

    // 用户主动关闭：有未保存改动先确认
    private void closeByUser() {
        if (pages != null && pages.blockNavigation()) {
            return;
        }
        if (model.hasDirty()) {
            confirm(UI + "confirm.discard_title", UI + "confirm.discard_message", () -> {
                // 玩家确认放弃：连落盘草稿一起删掉，避免下次打开又「恢复」回来
                model.discardAllDrafts();
                closePages();
                workspace.close("screen_closed");
                this.onClose();
            });
            return;
        }
        closePages();
        workspace.close("screen_closed");
        this.onClose();
    }

    // 只关屏，不动工作区会话（工作区由服务端租约与协议驱动）
    @Override
    public void onClose() {
        applyAllForms();
        closePages();
        // P6：关屏前把未应用草稿写盘（断线 / 关游戏 / 关界面都能恢复）
        model.persistNow();
        super.onClose();
    }

    // 关屏 / 会话失效：结束页面交互并让表单释放捕获与输入状态
    private void closePages() {
        if (pages == null) {
            return;
        }
        pages.endInteractions();
        pages.onHostClosed();
        pages = null;
    }

    // ---- 小工具 ----

    private void setNotice(@Nullable Component message, int color) {
        this.notice = message;
        this.noticeColor = color;
    }

    private void noSelection() {
        setNotice(Component.translatable(UI + "notice.no_selection"), UiPalette.WARNING);
    }

    private @Nullable ResourceLocation normalizeId(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        ResourceLocation parsed = trimmed.indexOf(':') >= 0
                ? ResourceLocation.tryParse(trimmed)
                : ResourceLocation.tryParse(TypeLabels.OWN_NAMESPACE + ":" + trimmed);
        if (parsed == null || !TypeLabels.OWN_NAMESPACE.equals(parsed.getNamespace())) {
            return null;
        }
        return parsed;
    }

    // 文本输入弹窗
    private void promptText(String titleKey, String initial, Consumer<String> onValue) {
        UiTextInput input = new UiTextInput(font, Component.translatable(UI + "prompt.id_hint"));
        input.setMaxLength(128);
        input.setValue(initial);
        UiModal modal = UiModal.create(font);
        modal.title(Component.translatable(titleKey));
        modal.contentWidget(input, ROW_H + 4);
        modal.confirm(Component.translatable(UI + "button.confirm"), () -> onValue.accept(input.value()));
        modal.cancel(Component.translatable(UI + "button.cancel"));
        modal.layoutCentered(this.width, this.height);
        modals.push(modal);
        input.setFocused(true);
    }

    // 确认弹窗
    private void confirm(String titleKey, String messageKey, Runnable onConfirm) {
        UiModal modal = UiModal.create(font);
        modal.title(Component.translatable(titleKey));
        modal.message(Component.translatable(messageKey));
        modal.confirm(Component.translatable(UI + "button.confirm"), onConfirm::run);
        modal.cancel(Component.translatable(UI + "button.cancel"));
        modal.layoutCentered(this.width, this.height);
        modals.push(modal);
    }

    // 底排按钮：自动换行、左对齐
    private static final class ButtonBar {
        private static final int BUTTON_H = 16;
        private static final int GAP = 2;

        private final List<UiButton> buttons = new ArrayList<>();

        private void set(List<UiButton> next) {
            buttons.clear();
            buttons.addAll(next);
        }

        private int preferredHeight(int width) {
            int rows = rows(width);
            return rows == 0 ? 0 : rows * BUTTON_H + (rows - 1) * GAP;
        }

        private int rows(int width) {
            if (buttons.isEmpty()) {
                return 0;
            }
            if (width <= 0) {
                return 1;
            }
            int rows = 1;
            int used = 0;
            for (UiButton button : buttons) {
                int buttonWidth = Math.clamp(button.preferredWidth(6), Math.min(24, width), width);
                if (used > 0 && used + buttonWidth > width) {
                    rows++;
                    used = 0;
                }
                used += buttonWidth + GAP;
            }
            return rows;
        }

        private void layout(int y, int width) {
            int x = PAD;
            if (buttons.isEmpty()) {
                return;
            }
            int rows = Math.max(1, rows(width));
            // 按钮宽度下限 24，但不得超过可用宽度（窄屏退化到列宽），保证 clamp 上下界合法
            int limit = Math.max(1, width);
            int minWidth = Math.min(24, limit);
            int cursor = 0;
            for (int rowIndex = 0; rowIndex < rows && cursor < buttons.size(); rowIndex++) {
                int rowY = y + rowIndex * (BUTTON_H + GAP);
                int cursorX = x;
                while (cursor < buttons.size()) {
                    UiButton button = buttons.get(cursor);
                    int buttonWidth = Math.clamp(button.preferredWidth(6), minWidth, limit);
                    if (cursorX > x && cursorX + buttonWidth > x + width) {
                        break;
                    }
                    button.setBounds(cursorX, rowY, buttonWidth, BUTTON_H);
                    button.setVisible(true);
                    cursorX += buttonWidth + GAP;
                    cursor++;
                }
            }
        }

        private void render(@NotNull GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
            for (UiButton button : buttons) {
                if (button.isVisible()) {
                    button.render(graphics, renderFont, mouseX, mouseY);
                }
            }
        }

        private boolean mouseClicked(double mouseX, double mouseY, int button) {
            for (UiButton candidate : buttons) {
                if (candidate.isVisible() && candidate.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            return false;
        }

        private boolean mouseReleased(double mouseX, double mouseY, int button) {
            boolean consumed = false;
            for (UiButton candidate : buttons) {
                if (candidate.isVisible()) {
                    consumed |= candidate.mouseReleased(mouseX, mouseY, button);
                }
            }
            return consumed;
        }
    }
}
