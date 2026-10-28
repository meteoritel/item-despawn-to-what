package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.ClientTypeRegistries;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorRegistries;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.edit.draft.DraftConflict;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientState;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiHistoryShortcut;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.screen.RuleEditorEditSections.EditSection;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ConditionEditingSupport;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
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
import com.meteorite.itemdespawntowhat.core.service.edit.RuleIssue;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleSaveStatus;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleSourceView;
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
 * <p>本类持有 {@link RuleEditorEditSections} 组合视图与编辑页控件；会话、工作区、焦点、弹窗与提示条
 * 通过 {@link EditorShell} 提供的命令与协作者访问。本地校验交给 {@link RuleEditorValidation}，
 * 页切换由 {@link EditorShell} 负责；屏幕只驱动页面生命周期与输入派发。</p>
 */
final class RuleEditorEditPage implements EditorPage {

    private final UiHostTheme THEME;
    // 布局常量
    private static final int TAB_H = 22;
    private static final int HEADER_H = 24;
    private static final int EDIT_PAD = 8;
    // 来源视图弹窗：只读 JSON 的宽度按「层标题 + 文件路径 + 缩进后的正文」取，高度随屏幕收缩
    private static final int SOURCE_MODAL_WIDTH = 360;
    private static final int SOURCE_MODAL_MIN_HEIGHT = 48;
    private static final int SOURCE_MODAL_MAX_HEIGHT = 200;
    private static final String UI = TypeLabels.UI_PREFIX;

    private final EditorShell shell;
    private final RuleEditorModel model;
    private final RuleEditClientWorkspace workspace;
    private final Font font;
    private final UiModalStack modals;
    private final UiFocusManager focus;
    private final RuleEditorValidation validation;

    // ---- 页签与底排 ----
    private EditSection tab = EditSection.INFO;
    private @Nullable RuleEditorEditSections pages;
    private @Nullable EditSession boundSession;

    /** 页面收尾只区分保留、完成编辑和两种真实释放方式。 */
    enum Finish { RETAIN, EDIT, UNMOUNT, CLOSE }
    private @Nullable UiSegmentedControl tabControl;
    private final List<UiButton> footerButtons = new ArrayList<>();
    private final RuleEditorScreenButtonBar footerBar = new RuleEditorScreenButtonBar(22, 4, true);
    // 编辑内容区（每帧重算；切页与问题定位复用同一份布局）
    private UiRect lastEditArea = new UiRect(0, 0, 0, 0);

    // 草稿冲突：已弹过冲突窗的目标 id（避免每帧重复弹；解决后移除）
    private final Set<String> promptedConflicts = new LinkedHashSet<>();
    // 已消费的回执序号
    private long seenResultSeq;
    // 客户端保存前校验：只读取本页与屏幕暴露的编辑现场

    // 页面宿主适配：把屏幕能力（会话 / 焦点 / 弹窗 / 提示 / 冻结 / 尺寸）暴露给四页编辑流程

    RuleEditorEditPage(EditorShell shell, RuleEditorModel model, RuleEditClientWorkspace workspace, Font font,
            UiModalStack modals, UiFocusManager focus) {
        this.THEME = shell.theme();
        this.shell = shell;
        this.model = model;
        this.workspace = workspace;
        this.font = font;
        this.modals = modals;
        this.focus = focus;
        this.validation = new RuleEditorValidation(shell, model, () -> pages);
        this.seenResultSeq = workspace.saving().outcome().sequence();
    }

    // ---- 页面度量与标题：屏幕按这些声明统一排版，页不自己算窗口几何 ----

    public int headerHeight() {
        return HEADER_H;
    }

    public int pagePadding() {
        return EDIT_PAD;
    }

    public @Nullable String title() {
        // 页头只显示当前实际生效名称；规则 ID 由基本页第一行的只读行单独承担
        return shell.editingId() == null ? null
                : Component.translatable(UI + "header.editing", shell.labelText(shell.editingId())).getString();
    }

    public String stateLabel() {
        // 冻结文案优先，且与只读彻底分开（冻结说的是会话不可写，只读说的是内容不保存）
        if (!shell.editable()) {
            return Component.translatable(UI + "state.freezing").getString();
        }
        if (shell.isEditReadOnly()) {
            return Component.translatable(UI + "state.readonly").getString();
        }
        return Component.translatable(UI + (model.dirtyRuleIds().isEmpty() ? "state.applied" : "state.unapplied"))
                .getString();
    }

    public boolean statePanel() {
        return true;
    }

    public boolean statePanelHighlighted() {
        return shell.editable() && shell.isEditReadOnly();
    }

    public @Nullable String titleDetail() {
        return shell.editingId() == null ? null : shell.labelText(shell.editingId());
    }

    public int layoutHeader(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY, int pagePad, int top) {
        return layoutTabs(graphics, renderFont, mouseX, mouseY, pagePad, top);
    }

    // ---- 初始化 ----

    public void init() {
        Font font = this.font;
        tabControl = new UiSegmentedControl(THEME, font, tabOptions());
        tabControl.setSelected(tab.value());
        tabControl.setOnChanged(this::switchTab);
        buildFooterButtons();
    }

    private List<UiSegmentedControl.Option> tabOptions() {
        List<UiSegmentedControl.Option> options = new ArrayList<>();
        for (EditSection value : EditSection.values()) {
            options.add(new UiSegmentedControl.Option(value.value(),
                    Component.translatable(UI + "tab." + value.value())));
        }
        return options;
    }

    // 切页：先结束进行中的预览并提交有效编辑，再重建目标页
    private void switchTab(String value) {
        EditSection target = EditSection.of(value);
        if (target == tab) {
            return;
        }
        if (pages != null) {
            finish(Finish.EDIT);
            pages.setPage(target);
        }
        tab = target;
        shell.rebuildFocus();
    }

    private void buildFooterButtons() {
        footerButtons.clear();
        footerButtons.add(button(UI + "button.save", UiButtonVariant.PRIMARY, this::save));
        footerButtons.add(button(UI + "button.changes", UiButtonVariant.SECONDARY, shell::showChanges));
        footerButtons.add(button(UI + "button.source_view", UiButtonVariant.SECONDARY, this::showSourceView));
        footerButtons.add(button(UI + "button.back", UiButtonVariant.SECONDARY, shell::backToList));
        footerBar.set(footerButtons);
    }

    // ---- 来源视图 ----

    /**
     * 打开来源视图弹窗：已取回时按层展示文件原文与来源元数据；
     * 在途只提示加载中，失败或超时时提示原文不可用并提供重试；三种状态都不影响表单编辑与保存。
     */
    private void showSourceView() {
        String id = shell.editingId();
        if (id == null) {
            return;
        }
        RuleEditClientWorkspace workspace = this.workspace;
        RuleSourceView view = workspace.sourceView(id).value();
        UiModal modal = UiModal.create(THEME, font)
    .title(Component.translatable(UI + "source.title"));
        if (view != null) {
            modal.contentWidget(sourceView(view),
                    Math.clamp(shell.screenHeight() - 100, SOURCE_MODAL_MIN_HEIGHT, SOURCE_MODAL_MAX_HEIGHT));
            modal.cancel(Component.translatable(UI + "button.close"));
        } else if (workspace.sourceView(id).failed()) {
            modal.message(Component.translatable(UI + "source.failed"));
            modal.confirm(Component.translatable(UI + "source.retry"), () -> {
                workspace.retrySourceView(id);
                shell.setNotice(Component.translatable(UI + "source.retry_sent"),EditorShell.NoticeLevel.INFO);
            });
            modal.cancel(Component.translatable(UI + "button.close"));
        } else {
            modal.message(Component.translatable(
                    workspace.sourceView(id).loading() ? UI + "source.loading" : UI + "source.none"));
            modal.cancel(Component.translatable(UI + "button.close"));
        }
        modal.preferredWidth(SOURCE_MODAL_WIDTH).layoutCentered(shell.screenWidth(), shell.screenHeight());
        modals.push(modal);
    }

    // 来源视图正文：每层一个代码块，标题给出层与数据包，副标题给出文件路径
    private UiCodeView sourceView(RuleSourceView view) {
        List<UiCodeView.Section> sections = new ArrayList<>();
        appendSourceSection(sections, view.base());
        appendSourceSection(sections, view.overlay());
        UiCodeView code = new UiCodeView(THEME, font);
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
        return new UiButton(THEME, font, Component.translatable(labelKey), variant, action::run);
    }

    // ---- 渲染与布局 ----

    // 页签行布局并渲染；返回内容区起点（未创建页签控件时原样返回）
    int layoutTabs(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY, int pagePad, int y) {
        if (tabControl == null) {
            return y;
        }
        tabControl.setBounds(pagePad, y, Math.max(0, shell.screenWidth() - pagePad * 2), TAB_H);
    tabControl.render(graphics, renderFont, mouseX, mouseY);
        return y + TAB_H + EDIT_PAD;
    }

    // 编辑页布局：四个页签各自在内容区里排布控件（含窄屏分层）
    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY, UiRect contentArea) {
        // 内容区整块铺面板底：页签条与各分区在这块底上排布
        graphics.fill(contentArea.x() - 2, contentArea.y() - 2,
                contentArea.x() + contentArea.width() + 2, contentArea.y() + contentArea.height(),
                THEME.palette().panelFill());
        // 内容区只在矩形真的变化时替换记录：静止帧不分配新矩形，也不触发重排
        if (!contentArea.equals(lastEditArea)) {
            lastEditArea = new UiRect(contentArea.x(), contentArea.y(), contentArea.width(), contentArea.height());
        }
        if (pages == null) {
            return;
        }
        syncEditableState();
        // 布局按需执行：几何输入未变时这里只命中缓存，渲染帧不再做任何测量
        pages.ensureLayout(lastEditArea);
        pages.render(graphics, renderFont, mouseX, mouseY);
    }

    /**
     * 导出编辑页当前可见控件的快照：页签、页内控件与页脚按钮条。
     *
     * <p>页签排在最前，因为它是同屏唯一能在四个页面之间切换的控件；页内控件由
     * {@link RuleEditorEditSections} 汇总（表单沿焦点目标展开到输入框与行尾按钮），底排来自页脚按钮条。
     * 页签矩形来自上一次布局，未初始化的页签不报节点。</p>
     */
    public List<UiLayoutNode> layoutNodes() {
        List<UiLayoutNode> nodes = new ArrayList<>();
        if (tabControl != null) {
            nodes.addAll(tabControl.layoutNodes());
        }
        if (pages != null) {
            nodes.addAll(pages.layoutNodes());
        }
        nodes.addAll(footerBar.layoutNodes());
        return nodes;
    }

    public int footerHeight(int width) {
        return footerBar.preferredHeight(width);
    }

    public void renderFooter(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY, UiRect footer) {
        THEME.drawDivider(graphics, footer.x(), footer.y() - RuleEditorPageSupport.PAD, footer.width());
        footerBar.layout(footer.x(), footer.y(), footer.width());
    footerBar.render(graphics, renderFont, mouseX, mouseY);
    }

    public boolean renderDescriptionTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        return pages != null && pages.renderDescriptionTooltip(graphics, mouseX, mouseY);
    }

    public void onScopeChanged() {
        endInteractions();
    }

    public @Nullable UiTypePicker overlay() {
        return activeTypePicker();
    }

    public @Nullable Component tooltipAt(double mouseX, double mouseY) {
        return pages == null ? null : pages.tooltipAt(mouseX, mouseY);
    }

    @Nullable UiTypePicker activeTypePicker() {
        if (pages == null) return null;
        UiTypePicker picker = pages.resultsSection.typePicker;
        return picker.isOpen() ? picker : null;
    }

    // ---- 页面装配与生命周期 ----

    void rebuildEdit() {
        finish(Finish.EDIT);
        EditSession session = shell.editingSession();
        if (!shell.isEditing() || session == null) {
            finish(Finish.UNMOUNT);
            return;
        }
        if (pages == null) {
            pages = new RuleEditorEditSections(shell, model, font, focus, buildConditionEditingSupport(), () -> finish(Finish.EDIT));
        }
        boundSession = session;
        pages.setPage(tab);
        pages.rebuild();
        if (lastEditArea.width() > 0) {
            // 重建后页面已按当前几何排好；这里只补一次缓存命中判断
            pages.ensureLayout(lastEditArea);
        }
        // 页面控件是刚重建的，按当前工作区状态同步一次使能（APPLYING 期间必须冻结）
        syncEditableState();
    }

    /** 按当前会话身份收尾；旧页面只释放，不向重绑后的会话回写。 */
    void finish(Finish reason) {
        if (pages == null) return;
        if (reason == Finish.RETAIN) {
            pages.retainPendingInputs();
            return;
        }
        if (boundSession == shell.editingSession() && shell.editable()) {
            pages.finishEditing();
        } else {
            pages.endInteractions();
        }
        if (reason == Finish.UNMOUNT || reason == Finish.CLOSE) {
            if (reason == Finish.CLOSE) pages.onHostClosed(); else pages.unmount();
            pages = null;
            boundSession = null;
        }
    }

    // 进入新规则的编辑页：页签回到基本信息并丢弃上一份页面状态（候选与效果选择归零）
    void openNew() {
        finish(Finish.UNMOUNT);
        tab = EditSection.INFO;
        if (tabControl != null) {
            tabControl.setSelected(tab.value());
        }
    }

    // 新建规则后直接切到「输入」页签
    void openInputTab() {
        switchTab(EditSection.INPUT.value());
        if (tabControl != null) {
            tabControl.setSelected(tab.value());
        }
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

    public void addFocusTargets(UiFocusManager focus) {
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
    public boolean ownsFocusTarget(UiFocusTarget target) {
        return pages != null && pages.focusTargets().contains(target);
    }

    private void syncEditableState() {
        if (pages != null) {
            pages.syncEditableState();
        }
        updateButtons();
    }

    public void tick() {
        if (shell.isEditing() && pages != null) {
    pages.tick();
            // 内容里的「默认启用」可能被改：本服开关显示的是生效状态，需要跟着刷新
            pages.refreshServerState();
        }
    }

    // 底排「保存」按钮的可用状态与标签：只有存在待应用改动时才可点，无改动时显示「无改动」。
    // 沙盒内容本身不产生脏，因此只读条目在没改启停时自然保持禁用。
    public void updateButtons() {
        if (footerButtons.isEmpty()) {
            return;
        }
        UiButton save = footerButtons.getFirst();
        boolean dirty = model.hasDirty();
        save.setEnabled(shell.editable() && dirty);
        save.setLabel(dirty
                ? Component.translatable(UI + "button.apply_changes", model.dirtyRuleIds().size())
                : Component.translatable(UI + "button.no_changes"));
    }

    // ---- 输入委托 ----

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean consumed = pages != null && pages.mouseReleased(mouseX, mouseY, button);
        consumed |= footerMouseReleased(mouseX, mouseY, button);
        return consumed;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return pages != null && pages.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return pages != null && pages.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return pages != null && pages.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return pages != null && pages.keyReleased(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return pages != null && pages.charTyped(codePoint, modifiers);
    }

    private boolean footerMouseReleased(double mouseX, double mouseY, int button) {
    return footerBar.mouseReleased(mouseX, mouseY, button);
    }

    // ---- 保存 / 撤销 ----

    void save() {
        // F-I：先把当前草稿落盘（写盘本身有 2 秒节流），保证崩溃窗口内的最近编辑不丢
        finish(Finish.EDIT);
        model.persistNow();
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
            shell.setNotice(notice,EditorShell.NoticeLevel.DANGER);
            String issueRuleId = validation.issueRuleId();
            if (issueRuleId != null && !issueRuleId.equals(shell.editingId())) {
                shell.openEditor(issueRuleId);
            }
            revealIssue(blocker);
            return;
        }
        String json = model.buildChangeSetJson();
        if (json == null) {
            shell.setNotice(Component.translatable(UI + "notice.no_changes"),EditorShell.NoticeLevel.INFO);
            return;
        }
        if (!shell.editable()) {
            shell.setNotice(Component.translatable(UI + "notice.frozen"),EditorShell.NoticeLevel.WARNING);
            return;
        }
        if (workspace.save(RuleEditClientWorkspace.newOperationId(), json)) {
            shell.setNotice(Component.translatable(UI + "notice.applying"),EditorShell.NoticeLevel.INFO);
            if (!issues.isEmpty()) {
                // 提醒项不阻塞保存，但把玩家带到第一条上（例如未注册的第三方条件类型）
                revealIssue(issues.getFirst());
            }
        } else {
            shell.setNotice(Component.translatable(UI + "notice.save_rejected"),EditorShell.NoticeLevel.DANGER);
        }
    }

    boolean restoreHistory(UiHistoryShortcut shortcut) {
        if (!shell.isEditing()) return false;
        EditSession session = shell.editingSession();
        if (session == null) return false;
        long revision = session.revision();
        if (shortcut == UiHistoryShortcut.UNDO) undo(); else redo();
        return session.revision() != revision;
    }

    private void undo() {
        if (!shell.editable()) {
            return;
        }
        finish(Finish.EDIT);
        EditSession session = shell.editingSession();
        if (session != null && session.canUndo()) {
            session.undo();
            rebuildEdit();
            shell.rebuildFocus();
        }
    }

    private void redo() {
        if (!shell.editable()) {
            return;
        }
        finish(Finish.EDIT);
        EditSession session = shell.editingSession();
        if (session != null && session.canRedo()) {
            session.redo();
            rebuildEdit();
            shell.rebuildFocus();
        }
    }

    // 校验失败定位：先结束交互并提交当前页待写字段，再按路径切页并聚焦到具体字段
    // （规则 → 页签 → 候选 → 效果 → 条件 → 字段；路径到当前下标的映射由页面负责）
    void revealIssue(FormIssue issue) {
        if (pages == null || issue == null) {
            return;
        }
        finish(Finish.EDIT);
        UiFocusTarget target = pages.reveal(issue.path());
        tab = pages.page();
        if (tabControl != null) {
            tabControl.setSelected(tab.value());
        }
        shell.rebuildFocus();
        if (target != null) {
            focus.focusOn(target);
            pages.ensureVisible(target);
        }
    }

    // 服务端 VALIDATION_FAILED 定位：规则 → 页签 → 候选 → 效果 → 条件 → 字段
    void locateValidationIssues() {
        for (RuleIssue issue : workspace.saving().outcome().issues()) {
            if (issue == null || !RuleIssue.SEVERITY_ERROR.equals(issue.severity())) {
                continue;
            }
            String path = issue.fieldPath();
            if (path == null || path.isBlank()) {
                continue;
            }
            if (issue.ruleId() != null && (!issue.ruleId().equals(shell.editingId()) || !shell.isEditing())) {
                shell.openEditor(issue.ruleId());
                if (!issue.ruleId().equals(shell.editingId()) || !shell.isEditing()) continue;
            }
            if (pages == null || !shell.isEditing()) continue;
            Component message = issue.messageCode().isBlank()
                    ? Component.literal(issue.fallbackMessage())
                    : Component.translatable(issue.messageCode(), issue.messageArgs().toArray());
            revealIssue(FormIssue.error(path, message, message));
            shell.setNotice(Component.translatable(UI + "notice.validation_located", message),EditorShell.NoticeLevel.DANGER);
            return;
        }
    }

    // ---- 草稿冲突与保存回执 ----

    // 有冲突就弹窗（一次弹一个，已提示过的不重复弹）
    void promptConflicts() {
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
        UiModal modal = UiModal.create(THEME, font);
    modal.title(Component.translatable(UI + "conflict.title"));
        modal.message(Component.translatable(UI + "conflict.message." + conflict.messageSuffix(), id));
        modal.confirm(Component.translatable(UI + "conflict.keep"), () -> {
            model.resolveConflict(id, true);
            promptedConflicts.remove(id);
            shell.refreshList();
            rebuildEdit();
            shell.rebuildFocus();
            shell.setNotice(Component.translatable(UI + "notice.conflict_kept"),EditorShell.NoticeLevel.INFO);
        });
        modal.addButton(new UiButton(THEME, font, Component.translatable(UI + "conflict.use_server"),
                UiButtonVariant.DANGER, () -> {
                    model.resolveConflict(id, false);
                    promptedConflicts.remove(id);
                    shell.refreshList();
                    rebuildEdit();
                    shell.rebuildFocus();
                    shell.setNotice(Component.translatable(UI + "notice.conflict_discarded"),EditorShell.NoticeLevel.WARNING);
                }));
        modal.cancel(Component.translatable(UI + "conflict.later"));
        modal.layoutCentered(shell.screenWidth(), shell.screenHeight());
        modals.push(modal);
    }

    // 保存回执与工作区状态轮询
    void pollWorkspace() {
        long sequence = workspace.saving().outcome().sequence();
        if (sequence != seenResultSeq) {
            seenResultSeq = sequence;
            RuleSaveStatus status = workspace.saving().outcome().status();
            shell.refreshSnapshot();
            if (status == RuleSaveStatus.SUCCESS || status == RuleSaveStatus.NO_CHANGES) {
                model.acceptSaved();
                shell.setNotice(Component.translatable(com.meteorite.itemdespawntowhat.core.network.transport.RuleEditMessages.key(status)),EditorShell.NoticeLevel.SUCCESS);
            } else if (status == RuleSaveStatus.CANCELLED) {
                shell.setNotice(Component.translatable(com.meteorite.itemdespawntowhat.core.network.transport.RuleEditMessages.key(status)),EditorShell.NoticeLevel.INFO);
            } else if (status == RuleSaveStatus.SAVED_NOT_RELOADED) {
                model.acceptSaved();
                shell.setNotice(Component.translatable(com.meteorite.itemdespawntowhat.core.network.transport.RuleEditMessages.key(status)),EditorShell.NoticeLevel.WARNING);
            } else if (isSessionLost(status)) {
                // 失权/断线：取消未完成预览、停用写入、保留草稿；不从 GUI 重开会话，也不写盘
                cancelPreviews();
                Component reason = status == null ? Component.empty() : Component.translatable(com.meteorite.itemdespawntowhat.core.network.transport.RuleEditMessages.key(status));
                shell.setNotice(reason.copy().append(" ").append(Component.translatable(UI + "notice.session_reopen")),
                        EditorShell.NoticeLevel.DANGER);
            } else if (status == RuleSaveStatus.VALIDATION_FAILED) {
                shell.setNotice(Component.translatable(com.meteorite.itemdespawntowhat.core.network.transport.RuleEditMessages.key(status)),EditorShell.NoticeLevel.DANGER);
                locateValidationIssues();
            } else if (status != null) {
                shell.setNotice(Component.translatable(com.meteorite.itemdespawntowhat.core.network.transport.RuleEditMessages.key(status), workspace.saving().outcome().messageArgs().toArray()),
                        EditorShell.NoticeLevel.DANGER);
            }
            shell.refreshList();
            rebuildEdit();
            shell.rebuildFocus();
        }
        if (workspace.session().state() == RuleEditClientState.FREE && workspace.session().openRequest() == null) {
            shell.requestClose();
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
        endInteractions();
    }

    // ---- 条件树支持 ----

    // 条件树的共享定义能力（类型清单、按场景创建、解码注册表）：触发页使用，不捕获表单或导航状态
    private ConditionEditingSupport buildConditionEditingSupport() {
        return new ConditionEditingSupport() {
            @Override
            public List<ConditionTreeContract.TypeOption> typeOptions() {
                List<ConditionTreeContract.TypeOption> options = new ArrayList<>();
                for (TypeEditorDescriptor descriptor : EditorRegistries.CONDITIONS.all()) {
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
