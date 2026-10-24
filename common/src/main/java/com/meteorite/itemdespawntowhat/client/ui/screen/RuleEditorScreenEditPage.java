package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.ClientTypeRegistries;
import com.meteorite.itemdespawntowhat.client.edit.ConditionEditorRegistry;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorWorkspaceView;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.edit.draft.DraftConflict;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientState;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiHistoryShortcut;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.CatalogSuggestions;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ConditionEditingSupport;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.SuggestionProvider;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionTreeContract;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCodeView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTypePicker;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModalStack;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiSegmentedControl;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleIssue;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSaveStatus;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSourceView;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 规则编辑器「编辑页」（EDIT 模式）：四个页签的组合视图、底排按钮、保存，
 * 以及草稿冲突、保存回执轮询与校验问题定位。
 *
 * <p>撤销 / 重做保留 Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y 快捷键入口；
 * 条件树弹窗另有自己的撤销 / 重做图标，两者都走同一条会话历史链路。</p>
 *
 * <p>本类持有 {@link RuleEditorEditPages} 组合视图与编辑页控件；会话、工作区、焦点、弹窗与提示条
 * 通过 {@link Host} 暴露的屏幕能力访问。本地校验交给 {@link RuleEditorValidation}，
 * 模式切换与屏幕生命周期由 {@link RuleEditorScreen} 负责。</p>
 */
final class RuleEditorScreenEditPage {

    /*** 宿主能力：编辑页只通过这些入口读写屏幕状态与跨模式动作。 */
    interface Host {

        // 渲染字体
        Font font();

        // 焦点管理器
        UiFocusManager focus();

        // 弹窗栈
        UiModalStack modals();

        // 规则模型（草稿会话与脏规则）
        RuleEditorModel model();

        // 编辑工作区只读视图
        EditorWorkspaceView workspace();

        // 当前编辑会话（未打开规则时为 null）
        @Nullable EditSession session();

        // 当前编辑的规则 id
        @Nullable String editingId();

        // 是否处于编辑模式（列表页为 false）
        boolean isEditMode();

        // 草稿已变更（屏幕据此刷新列表）
        void onDraftChanged();

        // 控件重建后同步宿主的焦点目标
        void rebuildFocus();

        // 底部提示条
        void setNotice(@Nullable Component message, int color);

        // 重建管理页规则树
        void refreshList();

        // 冻结（会话失效 / 只读）时拒绝写入
        boolean rejectWhenFrozen();

        // 打开指定规则的编辑页
        void openEditor(String id);

        // 关闭屏幕
        void closeScreen();

        // 页面重建后按当前工作区状态同步一次使能
        void rebuildEditableState();

        // 草稿改动清单弹窗
        void showChanges();

        // 返回管理页
        void backToList();

        // 屏幕宽高
        int screenWidth();

        int screenHeight();
    }

    private static final UiHostTheme THEME = UiHostThemes.defaultTheme();
    // 布局常量
    private static final int TAB_H = 22;
    private static final int EDIT_PAD = 8;
    // 来源视图弹窗：只读 JSON 的宽度按「层标题 + 文件路径 + 缩进后的正文」取，高度随屏幕收缩
    private static final int SOURCE_MODAL_WIDTH = 360;
    private static final int SOURCE_MODAL_MIN_HEIGHT = 48;
    private static final int SOURCE_MODAL_MAX_HEIGHT = 200;
    private static final String UI = "gui.itemdespawntowhat.edit.";

    private final Host host;

    // ---- 页签与底排 ----
    private RuleEditorEditPages.Page tab = RuleEditorEditPages.Page.INFO;
    private @Nullable RuleEditorEditPages pages;
    private @Nullable UiSegmentedControl tabControl;
    private final List<UiButton> footerButtons = new ArrayList<>();
    private final RuleEditorScreenButtonBar footerBar = new RuleEditorScreenButtonBar(22, 4, true);
    // 编辑内容区（每帧重算；切页与问题定位复用同一份布局）
    private UiRect lastEditArea = new UiRect(0, 0, 0, 0);

    // 草稿冲突：已弹过冲突窗的目标 id（避免每帧重复弹；解决后移除）
    private final Set<String> promptedConflicts = new LinkedHashSet<>();
    // 已消费的回执序号
    private long seenResultSeq;
    // 注册表候选建议（惰性创建；创建失败时退化为无建议）
    private @Nullable SuggestionProvider catalogSuggestions;

    // 客户端保存前校验：只读取本页与屏幕暴露的编辑现场
    private final RuleEditorValidation validation = new RuleEditorValidation(new RuleEditorValidation.Host() {
        @Override
        public @Nullable EditSession editingSession() {
            return host.session();
        }

        @Override
        public @Nullable String editingId() {
            return host.editingId();
        }

        @Override
        public RuleEditorModel model() {
            return host.model();
        }

        @Override
        public @Nullable RuleEditorEditPages pages() {
            return pages;
        }
    });

    // 页面宿主适配：把屏幕能力（会话 / 焦点 / 弹窗 / 提示 / 冻结 / 尺寸）暴露给四页编辑流程
    private final RuleEditorEditPages.Host pagesHost = new RuleEditorEditPages.Host() {

        @Override
        public Font font() {
            return host.font();
        }

        @Override
        public @Nullable EditSession session() {
            return host.session();
        }

        @Override
        public UiFocusManager focus() {
            return host.focus();
        }

        @Override
        public UiModalStack modals() {
            return host.modals();
        }

        @Override
        public void onDraftChanged() {
            host.onDraftChanged();
        }

        @Override
        public void onControlsChanged() {
            host.rebuildFocus();
        }

        @Override
        public void notice(Component message, int color) {
            host.setNotice(message, color);
        }

        @Override
        public SuggestionProvider suggestions() {
            return suggestionProvider();
        }

        @Override
        public ConditionEditingSupport conditionEditingSupport() {
            return buildConditionEditingSupport();
        }

        @Override
        public EditorWorkspaceView workspace() {
            return host.workspace();
        }

        @Override
        public boolean rejectWhenFrozen() {
            return host.rejectWhenFrozen();
        }

        @Override
        public int screenWidth() {
            return host.screenWidth();
        }

        @Override
        public int screenHeight() {
            return host.screenHeight();
        }
    };

    RuleEditorScreenEditPage(Host host) {
        this.host = host;
        this.seenResultSeq = host.workspace().resultSeq();
    }

    // ---- 初始化 ----

    void init() {
        Font font = host.font();
        tabControl = new UiSegmentedControl(THEME, font, tabOptions());
        tabControl.setSelected(tab.value());
        tabControl.setOnChanged(this::switchTab);
        buildFooterButtons();
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
        host.rebuildFocus();
    }

    private void buildFooterButtons() {
        footerButtons.clear();
        footerButtons.add(button(UI + "button.save", UiButtonVariant.PRIMARY, this::save));
        footerButtons.add(button(UI + "button.changes", UiButtonVariant.SECONDARY, host::showChanges));
        footerButtons.add(button(UI + "button.source_view", UiButtonVariant.SECONDARY, this::showSourceView));
        footerButtons.add(button(UI + "button.back", UiButtonVariant.SECONDARY, host::backToList));
        footerBar.set(footerButtons);
    }

    // ---- 来源视图 ----

    /**
     * 打开来源视图弹窗：已取回时按层展示文件原文与来源元数据；
     * 在途只提示加载中，失败或超时时提示原文不可用并提供重试；三种状态都不影响表单编辑与保存。
     */
    private void showSourceView() {
        String id = host.editingId();
        if (id == null) {
            return;
        }
        EditorWorkspaceView workspace = host.workspace();
        RuleSourceView view = workspace.sourceView(id);
        UiModal modal = UiModal.create(THEME, host.font())
                .title(Component.translatable(UI + "source.title"));
        if (view != null) {
            modal.contentWidget(sourceView(view),
                    Math.clamp(host.screenHeight() - 100, SOURCE_MODAL_MIN_HEIGHT, SOURCE_MODAL_MAX_HEIGHT));
            modal.cancel(Component.translatable(UI + "button.close"));
        } else if (workspace.sourceViewFailed(id)) {
            modal.message(Component.translatable(UI + "source.failed"));
            modal.confirm(Component.translatable(UI + "source.retry"), () -> {
                workspace.retrySourceView(id);
                host.setNotice(Component.translatable(UI + "source.retry_sent"), THEME.palette().textSecondary());
            });
            modal.cancel(Component.translatable(UI + "button.close"));
        } else {
            modal.message(Component.translatable(
                    workspace.sourceViewLoading(id) ? UI + "source.loading" : UI + "source.none"));
            modal.cancel(Component.translatable(UI + "button.close"));
        }
        modal.preferredWidth(SOURCE_MODAL_WIDTH).layoutCentered(host.screenWidth(), host.screenHeight());
        host.modals().push(modal);
    }

    // 来源视图正文：每层一个代码块，标题给出层与数据包，副标题给出文件路径
    private UiCodeView sourceView(RuleSourceView view) {
        List<UiCodeView.Section> sections = new ArrayList<>();
        appendSourceSection(sections, view.base());
        appendSourceSection(sections, view.overlay());
        UiCodeView code = new UiCodeView(THEME, host.font());
        if (sections.isEmpty()) {
            code.setEmptyHint(Component.translatable(UI + "source.empty"));
        }
        return code.setSections(sections);
    }

    // 追加单层代码块；该层不存在时跳过
    private void appendSourceSection(List<UiCodeView.Section> sections, @Nullable RuleSourceView.View view) {
        if (view == null) {
            return;
        }
        Component layer = Component.translatableWithFallback(UI + "source.layer." + view.layer(), view.layer());
        Component title = view.packId() == null ? layer : layer.copy().append(" [" + view.packId() + "]");
        sections.add(new UiCodeView.Section(title, Component.literal(view.path()),
                RuleDraft.of(view.body()).root().toPrettyJson()));
    }

    private UiButton button(String labelKey, UiButtonVariant variant, Runnable action) {
        return new UiButton(THEME, host.font(), Component.translatable(labelKey), variant, action::run);
    }

    // ---- 渲染与布局 ----

    // 页签行布局并渲染；返回内容区起点（未创建页签控件时原样返回）
    int layoutTabs(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY, int pagePad, int y) {
        if (tabControl == null) {
            return y;
        }
        tabControl.setBounds(pagePad, y, Math.max(0, host.screenWidth() - pagePad * 2), TAB_H);
        tabControl.render(graphics, renderFont, mouseX, mouseY);
        return y + TAB_H + EDIT_PAD;
    }

    // 编辑页布局：四个页签各自在内容区里排布控件（含窄屏分层）
    void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY, UiRect contentArea) {
        lastEditArea = new UiRect(contentArea.x(), contentArea.y(), contentArea.width(), contentArea.height());
        if (pages == null) {
            return;
        }
        pages.layout(lastEditArea);
        pages.render(graphics, renderFont, mouseX, mouseY);
    }

    int footerHeight(int width) {
        return footerBar.preferredHeight(width);
    }

    void renderFooter(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY, int pagePad, int y, int width) {
        THEME.drawDivider(graphics, pagePad, y - RuleEditorPageSupport.PAD, width);
        footerBar.layout(pagePad, y, width);
        footerBar.render(graphics, renderFont, mouseX, mouseY);
    }

    boolean renderDescriptionTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        return pages != null && pages.renderDescriptionTooltip(graphics, mouseX, mouseY);
    }

    @Nullable Component tooltipAt(double mouseX, double mouseY) {
        return pages == null ? null : pages.tooltipAt(mouseX, mouseY);
    }

    @Nullable UiTypePicker activeTypePicker() {
        if (pages == null) return null;
        UiTypePicker picker = pages.ruleEditorResultsPage.typePicker;
        return picker.isOpen() ? picker : null;
    }

    // ---- 页面装配与生命周期 ----

    void rebuildEdit() {
        EditSession session = host.session();
        if (!host.isEditMode() || session == null) {
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
        host.rebuildEditableState();
    }

    // 提交当前页所有可见表单的待写字段（切页 / 返回 / 应用 / 关屏前调用）
    void applyAllForms() {
        if (pages != null) {
            pages.applyToDraft();
        }
    }

    boolean blockNavigation() {
        return pages != null && pages.blockNavigation();
    }

    // 进入新规则的编辑页：页签回到基本信息并丢弃上一份页面状态（候选与效果选择归零）
    void openNew() {
        tab = RuleEditorEditPages.Page.INFO;
        pages = null;
        if (tabControl != null) {
            tabControl.setSelected(tab.value());
        }
    }

    // 新建规则后直接切到「输入」页签
    void openInputTab() {
        switchTab(RuleEditorEditPages.Page.INPUT.value());
        if (tabControl != null) {
            tabControl.setSelected(tab.value());
        }
    }

    // 返回管理页前卸载；页面正在阻塞导航（如未确认的输入）时返回 false
    boolean unmount() {
        if (pages != null) {
            if (pages.blockNavigation()) {
                return false;
            }
            pages.unmount();
        }
        pages = null;
        return true;
    }

    // 关屏 / 会话失效：结束页面交互并让表单释放捕获与输入状态
    void close() {
        if (pages == null) {
            return;
        }
        pages.endInteractions();
        pages.onHostClosed();
        pages = null;
    }

    void endInteractions() {
        if (pages != null) {
            pages.endInteractions();
        }
    }

    void ensureVisible(@Nullable UiFocusTarget target) {
        if (target != null && pages != null) {
            pages.ensureVisible(target);
        }
    }

    void addFocusTargets(UiFocusManager focus) {
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

    // 编辑页控件是否属于页面焦点目标（鼠标命中时用于裁剪区域判断）
    boolean isPageFocusTarget(UiFocusTarget target) {
        return pages != null && pages.focusTargets().contains(target);
    }

    void setPagesEnabled(boolean editable) {
        if (pages != null) {
            pages.setEnabled(editable);
        }
    }

    void tick() {
        if (host.isEditMode() && pages != null) {
            pages.tick();
        }
    }

    // 底排「保存」按钮的可用状态与标签（撤销 / 重做已移出底排，仅保留快捷键入口）
    void updateHistoryButtons() {
        if (!footerButtons.isEmpty()) {
            footerButtons.getFirst().setLabel(Component.translatable(UI + "button.apply_changes", host.model().dirtyRuleIds().size()));
        }
    }

    // ---- 输入委托 ----

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tabControl != null && tabControl.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (pages != null && pages.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return footerMouseClicked(mouseX, mouseY, button);
    }

    // 底排按钮命中（页签 / 页面控件未消费时）
    boolean footerMouseClicked(double mouseX, double mouseY, int button) {
        return footerBar.mouseClicked(mouseX, mouseY, button);
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        return pages != null && pages.mouseReleased(mouseX, mouseY, button);
    }

    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return pages != null && pages.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return pages != null && pages.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return pages != null && pages.keyPressed(keyCode, scanCode, modifiers);
    }

    boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return pages != null && pages.keyReleased(keyCode, scanCode, modifiers);
    }

    boolean charTyped(char codePoint, int modifiers) {
        return pages != null && pages.charTyped(codePoint, modifiers);
    }

    boolean footerMouseReleased(double mouseX, double mouseY, int button) {
        return footerBar.mouseReleased(mouseX, mouseY, button);
    }

    // ---- 保存 / 撤销 ----

    void save() {
        // F-I：先把当前草稿落盘（写盘本身有 2 秒节流），保证崩溃窗口内的最近编辑不丢
        if (blockNavigation()) {
            return;
        }
        applyAllForms();
        host.model().persistNow();
        List<FormIssue> issues = validation.collectIssues();
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
            host.setNotice(notice, THEME.palette().danger());
            String issueRuleId = validation.issueRuleId();
            if (issueRuleId != null && !issueRuleId.equals(host.editingId())) {
                host.openEditor(issueRuleId);
            }
            revealIssue(blocker);
            return;
        }
        String json = host.model().buildChangeSetJson();
        if (json == null) {
            host.setNotice(Component.translatable(UI + "notice.no_changes"), THEME.palette().textSecondary());
            return;
        }
        if (!host.workspace().active()) {
            host.setNotice(Component.translatable(UI + "notice.frozen"), THEME.palette().warning());
            return;
        }
        if (host.workspace().save(host.workspace().newOperationId(), json)) {
            host.setNotice(Component.translatable(UI + "notice.applying"), THEME.palette().textSecondary());
            if (!issues.isEmpty()) {
                // 提醒项不阻塞保存，但把玩家带到第一条上（例如未注册的第三方条件类型）
                revealIssue(issues.getFirst());
            }
        } else {
            host.setNotice(Component.translatable(UI + "notice.save_rejected"), THEME.palette().danger());
        }
    }

    boolean restoreHistory(UiHistoryShortcut shortcut) {
        if (!host.isEditMode()) return false;
        EditSession session = host.session();
        if (session == null) return false;
        long revision = session.revision();
        if (shortcut == UiHistoryShortcut.UNDO) undo(); else redo();
        return session.revision() != revision;
    }

    private void undo() {
        if (host.rejectWhenFrozen()) {
            return;
        }
        if (blockNavigation()) {
            return;
        }
        EditSession session = host.session();
        if (session != null && session.canUndo()) {
            session.undo();
            rebuildEdit();
            host.rebuildFocus();
        }
    }

    private void redo() {
        if (host.rejectWhenFrozen()) {
            return;
        }
        if (blockNavigation()) {
            return;
        }
        EditSession session = host.session();
        if (session != null && session.canRedo()) {
            session.redo();
            rebuildEdit();
            host.rebuildFocus();
        }
    }

    // 校验失败定位：先结束交互并提交当前页待写字段，再按路径切页并聚焦到具体字段
    // （规则 → 页签 → 候选 → 效果 → 条件 → 字段；路径到当前下标的映射由页面负责）
    void revealIssue(FormIssue issue) {
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
        host.rebuildFocus();
        if (target != null) {
            host.focus().focusOn(target);
            pages.ensureVisible(target);
        }
    }

    // 服务端 VALIDATION_FAILED 定位：规则 → 页签 → 候选 → 效果 → 条件 → 字段
    void locateValidationIssues() {
        for (RuleIssue issue : host.workspace().lastIssues()) {
            if (issue == null || !RuleIssue.SEVERITY_ERROR.equals(issue.severity())) {
                continue;
            }
            String path = issue.fieldPath();
            if (path == null || path.isBlank()) {
                continue;
            }
            if (issue.ruleId() != null && (!issue.ruleId().equals(host.editingId()) || !host.isEditMode())) {
                host.openEditor(issue.ruleId());
                if (!issue.ruleId().equals(host.editingId()) || !host.isEditMode()) continue;
            }
            if (pages == null || !host.isEditMode()) continue;
            Component message = issue.messageCode().isBlank()
                    ? Component.literal(issue.fallbackMessage())
                    : Component.translatable(issue.messageCode(), issue.messageArgs().toArray());
            revealIssue(FormIssue.error(path, message, message));
            host.setNotice(Component.translatable(UI + "notice.validation_located", message), THEME.palette().danger());
            return;
        }
    }

    // ---- 草稿冲突与保存回执 ----

    // 有冲突就弹窗（一次弹一个，已提示过的不重复弹）
    void promptConflicts() {
        for (DraftConflict conflict : host.model().conflicts()) {
            if (promptedConflicts.add(conflict.targetId())) {
                openConflictModal(conflict);
                return;
            }
        }
    }

    // 冲突弹窗：保留草稿 / 丢弃草稿用服务端版本 / 稍后决定
    private void openConflictModal(DraftConflict conflict) {
        String id = conflict.targetId();
        UiModal modal = UiModal.create(THEME, host.font());
        modal.title(Component.translatable(UI + "conflict.title"));
        modal.message(Component.translatable(UI + "conflict.message." + conflict.messageSuffix(), id));
        modal.confirm(Component.translatable(UI + "conflict.keep"), () -> {
            host.model().resolveConflict(id, true);
            promptedConflicts.remove(id);
            host.refreshList();
            rebuildEdit();
            host.rebuildFocus();
            host.setNotice(Component.translatable(UI + "notice.conflict_kept"), THEME.palette().textSecondary());
        });
        modal.addButton(new UiButton(THEME, host.font(), Component.translatable(UI + "conflict.use_server"),
                UiButtonVariant.DANGER, () -> {
                    host.model().resolveConflict(id, false);
                    promptedConflicts.remove(id);
                    host.refreshList();
                    rebuildEdit();
                    host.rebuildFocus();
                    host.setNotice(Component.translatable(UI + "notice.conflict_discarded"), THEME.palette().warning());
                }));
        modal.cancel(Component.translatable(UI + "conflict.later"));
        modal.layoutCentered(host.screenWidth(), host.screenHeight());
        host.modals().push(modal);
    }

    // 保存回执与工作区状态轮询
    void pollWorkspace() {
        long sequence = host.workspace().resultSeq();
        if (sequence != seenResultSeq) {
            seenResultSeq = sequence;
            RuleSaveStatus status = host.workspace().lastStatus();
            host.model().refresh();
            if (status == RuleSaveStatus.SUCCESS || status == RuleSaveStatus.NO_CHANGES) {
                host.model().acceptSaved();
                host.setNotice(Component.translatable(status.messageKey()), THEME.palette().success());
            } else if (status == RuleSaveStatus.CANCELLED) {
                host.setNotice(Component.translatable(status.messageKey()), THEME.palette().textSecondary());
            } else if (status == RuleSaveStatus.SAVED_NOT_RELOADED) {
                host.model().acceptSaved();
                host.setNotice(Component.translatable(status.messageKey()), THEME.palette().warning());
            } else if (isSessionLost(status)) {
                // 失权/断线：取消未完成预览、停用写入、保留草稿；不从 GUI 重开会话，也不写盘
                cancelPreviews();
                Component reason = status == null ? Component.empty() : Component.translatable(status.messageKey());
                host.setNotice(reason.copy().append(" ").append(Component.translatable(UI + "notice.session_reopen")),
                        THEME.palette().danger());
            } else if (status == RuleSaveStatus.VALIDATION_FAILED) {
                host.setNotice(Component.translatable(status.messageKey()), THEME.palette().danger());
                locateValidationIssues();
            } else if (status != null) {
                host.setNotice(Component.translatable(status.messageKey(), host.workspace().lastMessageArgs().toArray()),
                        THEME.palette().danger());
            }
            host.refreshList();
            rebuildEdit();
            host.rebuildFocus();
        }
        if (host.workspace().state() == RuleEditClientState.FREE && host.workspace().openRequest() == null) {
            host.closeScreen();
        }
    }

    // 会话失效状态码：取消预览、停用写入、保留草稿（失权或断线不写盘）
    private static boolean isSessionLost(@Nullable RuleSaveStatus status) {
        return status == RuleSaveStatus.NO_PERMISSION || status == RuleSaveStatus.LOCK_NOT_OWNED
                || status == RuleSaveStatus.LOCK_BUSY || status == RuleSaveStatus.SESSION_EXPIRED;
    }

    // 取消未完成交互：关闭弹窗并结束页面的活动捕获（旧草稿保留）
    private void cancelPreviews() {
        while (!host.modals().isEmpty()) {
            host.modals().closeTop();
        }
        endInteractions();
    }

    // ---- 建议与条件树支持 ----

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

    // 条件树的共享定义能力（类型清单、按场景创建、解码注册表）：触发页使用，不捕获表单或导航状态
    private ConditionEditingSupport buildConditionEditingSupport() {
        return new ConditionEditingSupport() {
            @Override
            public List<ConditionTreeContract.TypeOption> typeOptions() {
                List<ConditionTreeContract.TypeOption> options = new ArrayList<>();
                for (TypeEditorDescriptor descriptor : ConditionEditorRegistry.all()) {
                    options.add(new ConditionTreeContract.TypeOption(descriptor.id(), descriptor.label()));
                }
                return options;
            }

            @Override
            public ConditionTreeContract.LeafFactory leafFactory() {
                return ClientTypeRegistries::leafNode;
            }

            @Override
            public TypeRegistry<ConditionType<?>> registry() {
                return ClientTypeRegistries.conditions();
            }
        };
    }
}
