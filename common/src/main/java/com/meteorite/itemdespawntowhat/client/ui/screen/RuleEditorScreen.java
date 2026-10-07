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
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTreeView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTreeNode;
import com.meteorite.itemdespawntowhat.client.edit.RuleCategories;
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
    private @Nullable UiTreeView<String> ruleList;
    private final List<String> visibleIds = new ArrayList<>();
    private final List<UiButton> listButtons = new ArrayList<>();
    private final ButtonBar listBar = new ButtonBar();
    private final ButtonBar globalBar = new ButtonBar();
    private final ButtonBar selectedBar = new ButtonBar();
    private final Set<String> expandedCategories = new LinkedHashSet<>();
    private final java.util.Map<String, RuleRecipeView> recipeViews = new java.util.LinkedHashMap<>();
    private boolean narrowDetails;
    private @Nullable UiButton detailsButton;
    private UiRect previewArea = new UiRect(0, 0, 0, 0);
    private final com.meteorite.itemdespawntowhat.client.ui.kit.UiScrollView previewScroll = new com.meteorite.itemdespawntowhat.client.ui.kit.UiScrollView();
    private String lastQuery = "";
    private String lastTreeQuery = "";
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
    private @Nullable UiButton requiredButton;
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
        modals.setOnScopeChanged(() -> {
            if (ruleList != null) ruleList.mouseReleased(-1, -1, 0);
            previewScroll.mouseReleased();
            if (pages != null) pages.endInteractions();
        });
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
        super.init();
        // resize 会再次调用 init；保留控件、输入缓冲、选择与滚动，仅在渲染时更新几何。
        if (ruleList != null) {
            modals.setBounds(0, 0, width, height);
            return;
        }
        searchField = new UiTextInput(font, Component.translatable(UI + "list.search"));
        searchField.setMaxLength(128);
        searchField.setValue(lastQuery);
        filterControl = new UiSegmentedControl(font, filterOptions());
        filterControl.setSelected(filterOptions().get(filterIndex).value());
        filterControl.setOnChanged(value -> {
            filterIndex = indexOfFilter(value);
            refreshList();
        });
        ruleList = new UiTreeView<>(font, (graphics, rowFont, node, row, depth, selected, hovered, focused) -> {
            if (node.value().startsWith("@")) {
                graphics.drawString(rowFont, TextScroll.trimToWidth(rowFont, node.label().getString(), row.width() - 4),
                        row.x() + 2, row.y() + (row.height() - rowFont.lineHeight) / 2, UiPalette.TEXT_SECONDARY, false);
            } else renderRuleRow(graphics, rowFont, node.value(), row, hovered);
        });
        ruleList.setRowHeightProvider(node -> font.lineHeight + 4
                + (node.value().startsWith("@") ? 0 : RuleRecipeView.CONTENT_HEIGHT + 1));
        ruleList.setEmptyMessage(Component.translatable(UI + "list.empty"));
        ruleList.setOnActivate(node -> { if (!node.value().startsWith("@")) openEditor(node.value()); });
        tabControl = new UiSegmentedControl(font, tabOptions());
        tabControl.setSelected(tab.value());
        tabControl.setOnChanged(this::switchTab);
        buildListButtons();
        buildFooterButtons();
        requiredButton = button(UI + "button.required", UiButtonVariant.SECONDARY, this::showMissing);
        refreshList();
        rebuildEdit();
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
        UiButton create = button(UI + "button.new", UiButtonVariant.PRIMARY, this::promptNewRule);
        UiButton templates = button(UI + "button.template", UiButtonVariant.SECONDARY, this::openTemplatePicker);
        UiButton edit = button(UI + "button.edit", UiButtonVariant.PRIMARY, () -> {
            String id = selectedRuleId(); if (id != null) openEditor(id);
        });
        UiButton toggle = button(UI + "button.toggle", UiButtonVariant.SECONDARY, this::toggleSelectedEnabled);
        UiButton copy = button(UI + "button.duplicate", UiButtonVariant.SECONDARY, this::promptDuplicate);
        UiButton more = button(UI + "button.more", UiButtonVariant.SECONDARY, this::openMore);
        UiButton details = button(UI + "button.details", UiButtonVariant.SECONDARY, () -> { narrowDetails = !narrowDetails; rebuildFocus(); });
        detailsButton = details;
        applyButton = button(UI + "button.apply", UiButtonVariant.PRIMARY, this::save);
        UiButton changes = button(UI + "button.changes", UiButtonVariant.SECONDARY, this::showChanges);
        globalBar.set(List.of(create, templates, details));
        selectedBar.set(List.of(edit, toggle, copy, more));
        listBar.set(List.of(applyButton, changes));
        listButtons.addAll(List.of(create, templates, details, edit, toggle, copy, more, applyButton, changes));
    }

    /*** 纵向菜单项，避免窄屏和英文长按钮挤出弹窗。 */
    private record MenuAction(String key, Runnable action) { }

    private void openMore() {
        if (selectedRuleId() == null) { noSelection(); return; }
        UiModal modal = UiModal.create(font).title(Component.translatable(UI + "button.more"));
        UiListView<MenuAction> list = new UiListView<>(font,
                (graphics, rowFont, item, index, row, selected, hovered, focused) -> graphics.drawString(rowFont,
                        Component.translatable(UI + item.key()), row.x() + 4, row.y() + 4, UiPalette.TEXT_PRIMARY, false));
        list.setItems(List.of(new MenuAction("button.mask", this::maskSelected),
                new MenuAction("button.restore", this::restoreSelected), new MenuAction("button.delete", this::deleteSelected)));
        list.setRowHeight(20);
        list.setActivateOnSingleClick(true);
        list.setOnActivate(item -> { modals.close(modal); item.action().run(); });
        modal.contentWidget(list, 64).cancel(Component.translatable(UI + "button.cancel"));
        modal.preferredWidth(300).layoutCentered(width, height);
        modals.push(modal);
    }

    private void showMissing() {
        EditSession session = editingSession();
        if (session == null) return;
        var missing = com.meteorite.itemdespawntowhat.client.edit.RuleRequirements.find(session.draft().view());
        UiListView<com.meteorite.itemdespawntowhat.client.edit.RuleRequirements.Missing> list = new UiListView<>(font,
                (graphics, rowFont, item, index, row, selected, hovered, focused) -> graphics.drawString(rowFont,
                        Component.translatable(item.labelKey()), row.x() + 2, row.y() + 2, UiPalette.TEXT_PRIMARY, false));
        list.setItems(missing);
        list.setActivateOnSingleClick(true);
        list.setEmptyMessage(Component.translatable(UI + "required.ready"));
        UiModal modal = UiModal.create(font).title(Component.translatable(UI + "button.required"));
        list.setOnActivate(item -> {
            revealIssue(FormIssue.error(item.path(), Component.translatable(item.labelKey()), Component.translatable(UI + "issue.required")));
            modals.close(modal);
        });
        modal.contentWidget(list, Math.clamp(missing.size() * 12L, 24, 100)).cancel(Component.translatable(UI + "button.cancel"));
        modals.push(modal.layoutCentered(width, height));
    }

    private void showChanges() {
        Component body = Component.translatable(UI + "changes.note");
        for (String id : model.dirtyRuleIds()) {
            EditSession session = model.session(id);
            String kind = model.isRestoring(id) ? "restore" : model.isCreated(id) ? "create"
                    : session != null && session.draft().isDeleted() ? "remove" : "modify";
            body = body.copy().append("\n").append(Component.translatable(UI + "changes." + kind))
                    .append(": ").append(labelText(id));
        }
        modals.push(UiModal.create(font).title(Component.translatable(UI + "button.changes"))
                .message(body).preferredWidth(300).cancel(Component.translatable(UI + "button.cancel"))
                .layoutCentered(width, height));
    }

    private void buildFooterButtons() {
        footerButtons.clear();
        footerButtons.add(button(UI + "button.save", UiButtonVariant.PRIMARY, this::save));
        this.undoButton = button(UI + "button.undo", UiButtonVariant.SECONDARY, this::undo);
        this.redoButton = button(UI + "button.redo", UiButtonVariant.SECONDARY, this::redo);
        footerButtons.add(this.undoButton);
        footerButtons.add(this.redoButton);
        footerButtons.add(button(UI + "button.changes", UiButtonVariant.SECONDARY, this::showChanges));
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
        int actualMouseX = mouseX;
        int actualMouseY = mouseY;
        var pointer = com.meteorite.itemdespawntowhat.client.ui.kit.UiPointer.gated(modals.isEmpty(), mouseX, mouseY);
        mouseX = pointer.x();
        mouseY = pointer.y();
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
        if (mode == Mode.EDIT && requiredButton != null) {
            requiredButton.setBounds(PAD, y, w - PAD * 2, 14);
            requiredButton.render(graphics, font, mouseX, mouseY);
            y += 16;
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
        mouseX = actualMouseX;
        mouseY = actualMouseY;
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
                UiTreeNode<String> node = ruleList.nodeAt(mouseX, mouseY);
                if (node != null && !node.value().startsWith("@")) {
                    String id = node.value();
                    RuleRecipeView recipe = recipeViews.get(id);
                    var tag = tagAt(mouseX, mouseY);
                    tip = tag == null ? Component.literal(labelText(id)).append("\n").append(recipe == null ? Component.empty() : recipe.tooltip())
                            : tag.tooltip();
                    Component state = ruleState(id);
                    if (!state.getString().isBlank()) tip = tip.copy().append("\n").append(state);
                    if (RulePreviewIcons.unavailable(model.displayBody(id))) tip = tip.copy()
                            .append("\n").append(Component.translatable(UI + "preview.unavailable"));
                }
            }
            if (tip != null) {
                renderTooltip(graphics, tip, mouseX, mouseY);
            }
        }
        // 字段说明提示：没有弹窗时才显示，避免盖住上层内容
        if (modals.isEmpty() && mode == Mode.EDIT && pages != null) {
            Component tip = pages.tooltipAt(mouseX, mouseY);
            if (tip != null) {
                renderTooltip(graphics, tip, mouseX, mouseY);
            }
        }
    }

    // Component 的单行 tooltip 重载不处理换行；先按窗口宽度拆行再交给原版定位。
    private void renderTooltip(GuiGraphics graphics, Component tip, int mouseX, int mouseY) {
        graphics.renderTooltip(font, font.split(tip, Math.clamp(width - 16, 1, 240)), mouseX, mouseY);
    }

    // 顶部标题与状态
    private void drawHeaderText(GuiGraphics graphics) {
        String title = this.getTitle().getString();
        if (mode == Mode.EDIT && editingId != null) {
            title = Component.translatable(UI + "recipe.editor_title", title, labelText(editingId)).getString();
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
        boolean wide = area.width() >= 430;
        if (detailsButton != null) detailsButton.setVisible(!wide);
        int headerHeight = globalBar.preferredHeight(area.width());
        globalBar.layout(area.y(), area.width());
        globalBar.render(graphics, font, mouseX, mouseY);
        int searchY = area.y() + headerHeight + PAD;
        int searchWidth = Math.min(160, area.width() / 2);
        if (searchField != null) {
            searchField.setBounds(area.x(), searchY, searchWidth, ROW_H);
            searchField.render(graphics, font, mouseX, mouseY);
        }
        if (filterControl != null) {
            filterControl.setBounds(area.x() + searchWidth + 2, searchY, area.width() - searchWidth - 2, ROW_H);
            filterControl.render(graphics, font, mouseX, mouseY);
        }
        int listY = searchY + ROW_H + PAD;
        int available = Math.max(0, area.bottom() - listY);
        int treeWidth = wide ? area.width() * 45 / 100 : area.width();
        int detailHeight = !wide && narrowDetails ? available : 0;
        if (ruleList != null) {
            ruleList.setVisible(wide || !narrowDetails);
            ruleList.setBounds(area.x(), listY, treeWidth, available - detailHeight);
            ruleList.render(graphics, font, mouseX, mouseY);
        }
        previewArea = wide ? new UiRect(area.x() + treeWidth + PAD, listY, area.width() - treeWidth - PAD, available)
                : new UiRect(area.x(), area.bottom() - detailHeight, area.width(), detailHeight);
        selectedBar.buttons.forEach(button -> button.setVisible(wide || detailHeight > 0));
        if (wide || detailHeight > 0) renderSelectedPreview(graphics, mouseX, mouseY);
    }

    private void renderSelectedPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        UiTheme.drawInset(graphics, previewArea);
        int barHeight = selectedBar.preferredHeight(previewArea.width());
        selectedBar.layout(previewArea.x(), previewArea.y(), previewArea.width());
        selectedBar.render(graphics, font, mouseX, mouseY);
        String id = selectedRuleId();
        Component text = Component.translatable(UI + "preview.select");
        if (id != null) {
            JsonObject rule = model.displayBody(id);
            text = Component.literal(labelText(id)).append("\n").append(recipeViews.containsKey(id)
                            ? recipeViews.get(id).summary() : Component.empty()).append("\n\n")
                    .append(Component.translatable(UI + "preview.group", Component.translatable(UI + "category." + RuleCategories.category(rule))))
                    .append("\n").append(rule == null ? Component.empty() : NaturalSummary.rule(rule));
        }
        if (id != null && RulePreviewIcons.unavailable(model.displayBody(id)))
            text = text.copy().append("\n").append(Component.translatable(UI + "preview.unavailable"));
        var lines = font.split(text, Math.max(1, previewArea.width() - 12));
        previewScroll.setViewport(previewArea.x() + 4, previewArea.y() + barHeight + PAD,
                Math.max(0, previewArea.width() - 8), Math.max(0, previewArea.height() - barHeight - 8));
        var icon = id == null ? null : RulePreviewIcons.rule(model.displayBody(id));
        int previewHeight = icon == null ? 0 : 64;
        previewScroll.setContentHeight(lines.size() * 11 + previewHeight);
        previewScroll.push(graphics);
        if (icon != null && previewScroll.offset() < previewHeight) {
            if (icon instanceof com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon.Rendered rendered)
                rendered.painter().render(graphics, new UiRect(0, 0, Math.min(72, previewScroll.viewport().width()), 60));
            else icon.render(graphics, 24, 20);
        }
        int y = previewHeight;
        for (var line : lines) { graphics.drawString(font, line, 0, y, UiPalette.TEXT_PRIMARY, false); y += 11; }
        previewScroll.pop(graphics);
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

    // 名称和状态放第一行；原注册名位置改为图标配方，不显示内部规则 ID。
    private void renderRuleRow(GuiGraphics graphics, Font rowFont, String id, UiRect row, boolean hovered) {
        RuleSnapshotEntry entry = model.entry(id);
        int color = isRuleActive(id, entry) ? UiPalette.TEXT_PRIMARY : UiPalette.TEXT_DISABLED;
        String state = ruleState(id).getString();
        int stateWidth = rowFont.width(state);
        graphics.drawString(rowFont, TextScroll.trimToWidth(rowFont, labelText(id),
                Math.max(0, row.width() - stateWidth - 6)), row.x() + 2, row.y() + 2, color, false);
        if (!state.isBlank()) graphics.drawString(rowFont, state, row.right() - stateWidth - 2, row.y() + 2,
                model.hasError(id) ? UiPalette.DANGER : UiPalette.TEXT_SECONDARY, false);
        RuleRecipeView recipe = recipeViews.get(id);
        if (recipe != null) recipe.render(graphics, rowFont,
                new UiRect(row.x() + 2, row.y() + rowFont.lineHeight + 3,
                        Math.max(0, row.width() - 4), RuleRecipeView.CONTENT_HEIGHT), color, hovered);
    }

    // 状态用明确文字，正常启用不占用配方行；草稿标志与问题提示不再使用符号。
    private Component ruleState(String id) {
        if (model.hasError(id)) return Component.translatable(UI + "recipe.status.error");
        EditSession session = model.session(id);
        if (session != null && (session.isDirty() || model.isRestoring(id)))
            return Component.translatable(UI + "recipe.status.pending");
        RuleSnapshotEntry entry = model.entry(id);
        if (entry != null && RuleSnapshotEntry.STATUS_MASKED.equals(entry.status()))
            return Component.translatable(UI + "recipe.status.masked");
        return isRuleActive(id, entry) ? Component.empty() : Component.translatable(UI + "recipe.status.disabled");
    }

    // 效果列表行
    // ---- 列表维护 ----

    private void refreshList() {
        if (ruleList == null) {
            return;
        }
        String selectedValue = ruleList.selectedNode() == null ? null : ruleList.selectedNode().value();
        int scrollOffset = ruleList.scrollOffset();
        visibleIds.clear();
        recipeViews.clear();
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
            recipeViews.put(id, new RuleRecipeView(model.displayBody(id)));
        }
        if (lastTreeQuery.isBlank()) rememberCategoryState(ruleList.roots());
        lastTreeQuery = lastQuery;
        List<UiTreeNode<String>> roots = new ArrayList<>();
        UiTreeNode<String> entity = categoryNode("entity");
        for (String category : List.of("entity.item", "entity.entity", "entity.experience", "entity.mixed")) entity.addChild(categoryNode(category));
        roots.add(entity);
        for (String category : List.of("block", "loot", "world", "mixed", "extension")) roots.add(categoryNode(category));
        for (String id : visibleIds) {
            String category = RuleCategories.category(model.displayBody(id));
            UiTreeNode<String> group = roots.stream().flatMap(root -> root == entity ? root.children().stream() : java.util.stream.Stream.of(root))
                    .filter(node -> node.value().equals("@" + category)).findFirst().orElseThrow();
            group.addChild(id, Component.literal(labelText(id)));
        }
        List<UiTreeNode<String>> nonempty = new ArrayList<>();
        for (UiTreeNode<String> root : roots) {
            if (root == entity) {
                UiTreeNode<String> parent = categoryNode("entity");
                root.children().stream().filter(node -> !node.children().isEmpty()).forEach(node -> parent.addChild(counted(node)));
                if (!parent.children().isEmpty()) nonempty.add(parent);
            } else if (!root.children().isEmpty()) nonempty.add(counted(root));
        }
        ruleList.setRoots(nonempty);
        ruleList.setSelectedNode(findNode(nonempty, selectedValue));
        ruleList.setScrollOffset(scrollOffset);
    }

    // 刷新时按稳定值恢复目录或规则选择，不依赖重建前的节点实例。
    private @Nullable UiTreeNode<String> findNode(List<UiTreeNode<String>> nodes, @Nullable String value) {
        if (value == null) return null;
        for (UiTreeNode<String> node : nodes) {
            if (value.equals(node.value())) return node;
            UiTreeNode<String> child = findNode(node.children(), value);
            if (child != null) return child;
        }
        return null;
    }

    private UiTreeNode<String> counted(UiTreeNode<String> node) {
        UiTreeNode<String> copy = new UiTreeNode<>(node.value(), node.label().copy().append(" (" + node.children().size() + ")"))
                .setExpanded(node.isExpanded());
        node.children().forEach(copy::addChild);
        return copy;
    }

    private UiTreeNode<String> categoryNode(String category) {
        return new UiTreeNode<>("@" + category, Component.translatable(UI + "category." + category))
                .setExpanded(!lastQuery.isBlank() || expandedCategories.contains(category));
    }

    private void rememberCategoryState(List<UiTreeNode<String>> nodes) {
        for (UiTreeNode<String> node : nodes) {
            if (!node.value().startsWith("@")) continue;
            String category = node.value().substring(1);
            if (node.isExpanded()) expandedCategories.add(category); else expandedCategories.remove(category);
            rememberCategoryState(node.children());
        }
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
        String label = body == null ? "" : RuleNaming.ruleTitle(body, RuleDisplayLabels::label).getString();
        return label.isBlank() || label.equals(id) ? Component.translatable(UI + "recipe.unnamed").getString() : label;
    }

    private @Nullable String selectedRuleId() {
        UiTreeNode<String> node = ruleList == null ? null : ruleList.selectedNode();
        return node == null || node.value().startsWith("@") ? null : node.value();
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
                // 目录建议是辅助入口；依赖尚不可用时保留空候选，不阻止规则编辑。
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
        if (templateId == null) { switchTab("input"); if (tabControl != null) tabControl.setSelected(tab.value()); }
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
        for (var missing : com.meteorite.itemdespawntowhat.client.edit.RuleRequirements.find(draft.view()))
            issues.add(FormIssue.error(missing.path(), Component.translatable(missing.labelKey()), Component.translatable(UI + "issue.required")));
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
            if (requiredButton != null) focus.add(requiredButton);
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
        if (button == 0 && mode == Mode.LIST) {
            var tag = tagAt(mouseX, mouseY);
            if (tag != null) {
                modals.push(UiModal.create(font).title(tag.label())
                        .contentWidget(new TagCarouselView(font, tag), Math.clamp(height - 70, 110, 220))
                        .preferredWidth(280).cancel(Component.translatable(UI + "button.close"))
                        .layoutCentered(width, height));
                return true;
            }
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
            return globalBar.mouseClicked(mouseX, mouseY, button) || selectedBar.mouseClicked(mouseX, mouseY, button)
                    || listBar.mouseClicked(mouseX, mouseY, button);
        }
        if (requiredButton != null && requiredButton.mouseClicked(mouseX, mouseY, button)) return true;
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

    // 指针必须先通过树的内容门禁，再命中配方标签，滚动条拖动期间不会打开或提示。
    private @Nullable TagPreviewIcons.Tag tagAt(double mouseX, double mouseY) {
        if (ruleList == null) return null;
        UiTreeNode<String> node = ruleList.nodeAt(mouseX, mouseY);
        if (node == null) return null;
        RuleRecipeView recipe = recipeViews.get(node.value());
        if (recipe == null) return null;
        UiRect row = ruleList.nodeContentBounds(node);
        return recipe.tagAt(font, new UiRect(row.x() + 2, row.y() + font.lineHeight + 3,
                Math.max(0, row.width() - 4), RuleRecipeView.CONTENT_HEIGHT), mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (modals.isVisible()) {
            return modals.mouseReleased(mouseX, mouseY, button);
        }
        boolean consumed = pages != null && pages.mouseReleased(mouseX, mouseY, button);
        if (ruleList != null) consumed |= ruleList.mouseReleased(mouseX, mouseY, button);
        consumed |= globalBar.mouseReleased(mouseX, mouseY, button);
        consumed |= selectedBar.mouseReleased(mouseX, mouseY, button);
        consumed |= listBar.mouseReleased(mouseX, mouseY, button);
        if (requiredButton != null) consumed |= requiredButton.mouseReleased(mouseX, mouseY, button);
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
            if (previewArea.contains(mouseX, mouseY) && previewScroll.scrollBy(scrollY * 20)) return true;
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
        if (requiredButton != null && session != null) requiredButton.setLabel(Component.translatable(UI + "required.count",
                com.meteorite.itemdespawntowhat.client.edit.RuleRequirements.find(session.draft().view()).size()));
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

    // 外部切屏或断线也会调用 removed，及时释放预览实体持有的客户端世界。
    @Override public void removed() {
        EntityPreviewIcons.clear(); RulePreviewIcons.clear(); BlockPreviewIcons.clear();
        super.removed();
    }

    // 只关屏，不动工作区会话（工作区由服务端租约与协议驱动）
    @Override
    public void onClose() {
        EntityPreviewIcons.clear();
        BlockPreviewIcons.clear();
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
            if (buttons.stream().noneMatch(UiButton::isVisible)) {
                return 0;
            }
            if (width <= 0) {
                return 1;
            }
            int rows = 1;
            int used = 0;
            for (UiButton button : buttons) {
                if (!button.isVisible()) continue;
                int buttonWidth = Math.clamp(button.preferredWidth(6), Math.min(24, width), width);
                if (used > 0 && used + buttonWidth > width) {
                    rows++;
                    used = 0;
                }
                used += buttonWidth + GAP;
            }
            return rows;
        }

        private void layout(int y, int width) { layout(PAD, y, width); }

        private void layout(int x, int y, int width) {
            int limit = Math.max(1, width);
            int cursorX = x;
            int cursorY = y;
            for (UiButton button : buttons) {
                if (!button.isVisible()) continue;
                int buttonWidth = Math.clamp(button.preferredWidth(6), Math.min(24, limit), limit);
                if (cursorX > x && cursorX + buttonWidth > x + limit) {
                    cursorX = x;
                    cursorY += BUTTON_H + GAP;
                }
                button.setBounds(cursorX, cursorY, buttonWidth, BUTTON_H);
                cursorX += buttonWidth + GAP;
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
