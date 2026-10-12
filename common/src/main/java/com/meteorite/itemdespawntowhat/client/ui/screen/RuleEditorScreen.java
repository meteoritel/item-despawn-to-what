package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDescriptors;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorWorkspaceView;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleNaming;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiHistoryDispatcher;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ConsumptionSummary;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.NaturalSummary;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ReadOnlyTextView;
import com.meteorite.itemdespawntowhat.client.ui.shared.BlockPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.EntityPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.RuleDisplayLabels;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModalStack;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiNarration;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleIssue;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshotEntry;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/***
 * 规则编辑器主屏（契约 §5、docs/plan/plan-frontend-rewrite-forms.md）。
 * <p>两种模式：规则管理页（列表 / 搜索 / 筛选 / 新建 / 复制 / 停用 / 删除 / 恢复原始 / 模板）与规则编辑页
 * （基本信息 / 源物品 / 触发条件 / 效果 四个页签）。所有字段编辑走描述符驱动的
 * {@link com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView}，条件树走
 * {@link com.meteorite.itemdespawntowhat.client.ui.widget.UiConditionTreeSummary}，界面任何位置都不出现可编辑 JSON 文本框。
 * <p>本类只保留模式切换、屏幕生命周期、布局编排与事件派发：管理页在 {@link RuleEditorScreenListPage}
 * （条目动作在 {@link RuleEditorScreenListActions}），编辑页与保存 / 校验入口在 {@link RuleEditorScreenEditPage}
 * （本地校验在 {@link RuleEditorValidation}），两者的公共按钮排为 {@link RuleEditorScreenButtonBar}。
 * <p>只有工作区处于 ACTIVE 时可编辑；APPLYING 冻结；工作区进入 FREE 时自行关屏。
 */
public final class RuleEditorScreen extends Screen {

    // 宿主默认主题：屏幕从主题入口取样式后注入本类装配的控件
    private static final UiHostTheme THEME = UiHostThemes.defaultTheme();

    // 布局常量
    private static final int PAD = 4;
    private static final int HEADER_H = 14;
    private static final int EDIT_HEADER_H = 24;
    private static final int EDIT_PAD = 8;
    private static final int NOTICE_H = 10;
    // i18n 前缀
    private static final String UI = "gui.itemdespawntowhat.edit.";

    // 模式
    private enum Mode { LIST, EDIT }

    private final EditorWorkspaceView workspace;
    private final RuleEditorModel model;
    private final UiFocusManager focus = new UiFocusManager();
    private final UiModalStack modals = new UiModalStack(THEME);

    // 管理页（LIST）与编辑页（EDIT）：各自持有控件与视图状态
    private final RuleEditorScreenListPage listPage;
    private final RuleEditorScreenEditPage editPage;

    // 当前模式与编辑对象
    private Mode mode = Mode.LIST;
    private @Nullable String editingId;
    // 状态提示（保存回执 / 校验失败等）
    private @Nullable Component notice;
    private int noticeColor = THEME.palette().textSecondary();

    // 上一帧的可编辑状态（冻结切换时刷新表单使能）
    private boolean lastEditable = true;
    // 冻结提示只提示一次
    private boolean frozenNotified;
    // 草稿被修改后需要在下一帧刷新列表
    private boolean listNeedsRefresh;
    // 内容区（每帧重算，供命中测试复用）
    private UiRect contentArea = new UiRect(0, 0, 0, 0);

    // 构造：由 {@link RuleEditorOpener} 在服务端授权成功后调用
    public RuleEditorScreen(EditorWorkspaceView workspace) {
        super(Component.translatable(UI + "title"));
        BuiltinEditorDescriptors.bootstrap();
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.model = new RuleEditorModel(workspace);
        // 管理页宿主适配：把屏幕能力暴露给列表页与条目动作
        RuleEditorScreenListPage.Host listHost = new RuleEditorScreenListPage.Host() {
            @Override public Font font() { return RuleEditorScreen.this.font; }
            @Override public UiModalStack modals() { return modals; }
            @Override public RuleEditorModel model() { return model; }
            @Override public EditorWorkspaceView workspace() { return RuleEditorScreen.this.workspace; }
            @Override public int screenWidth() { return RuleEditorScreen.this.width; }
            @Override public int screenHeight() { return RuleEditorScreen.this.height; }
            @Override public boolean rejectWhenFrozen() { return RuleEditorScreen.this.rejectWhenFrozen(); }
            @Override public void setNotice(@Nullable Component message, int color) { RuleEditorScreen.this.setNotice(message, color); }
            @Override public String labelText(String id) { return RuleEditorScreen.this.labelText(id); }
            @Override public void openEditor(String id) { RuleEditorScreen.this.openEditor(id); }
            @Override public void selectInputTab() { editPage.openInputTab(); }
            @Override public void showChanges() { RuleEditorScreen.this.showChanges(); }
            @Override public void save() { editPage.save(); }
            @Override public void confirm(String titleKey, String messageKey, Runnable onConfirm) { RuleEditorScreen.this.confirm(titleKey, messageKey, onConfirm); }
            @Override public void refreshList() { listPage.refreshList(); }
        };
        // 编辑页宿主适配：把屏幕能力暴露给页签组合视图与保存流程
        RuleEditorScreenEditPage.Host editHost = new RuleEditorScreenEditPage.Host() {
            @Override public Font font() { return RuleEditorScreen.this.font; }
            @Override public UiFocusManager focus() { return focus; }
            @Override public UiModalStack modals() { return modals; }
            @Override public RuleEditorModel model() { return model; }
            @Override public EditorWorkspaceView workspace() { return RuleEditorScreen.this.workspace; }
            @Override public @Nullable EditSession session() { return editingSession(); }
            @Override public @Nullable String editingId() { return editingId; }
            @Override public boolean isEditMode() { return mode == Mode.EDIT; }
            @Override public void onDraftChanged() { RuleEditorScreen.this.onDraftChanged(); }
            @Override public void rebuildFocus() { RuleEditorScreen.this.rebuildFocus(); }
            @Override public void setNotice(@Nullable Component message, int color) { RuleEditorScreen.this.setNotice(message, color); }
            @Override public void refreshList() { listPage.refreshList(); }
            @Override public boolean rejectWhenFrozen() { return RuleEditorScreen.this.rejectWhenFrozen(); }
            @Override public void openEditor(String id) { RuleEditorScreen.this.openEditor(id); }
            @Override public void closeScreen() { RuleEditorScreen.this.onClose(); }
            @Override public void rebuildEditableState() { RuleEditorScreen.this.rebuildEditableState(); }
            @Override public void showChanges() { RuleEditorScreen.this.showChanges(); }
            @Override public void backToList() { RuleEditorScreen.this.backToList(); }
            @Override public int screenWidth() { return RuleEditorScreen.this.width; }
            @Override public int screenHeight() { return RuleEditorScreen.this.height; }
        };
        this.listPage = new RuleEditorScreenListPage(listHost);
        this.editPage = new RuleEditorScreenEditPage(editHost);
        modals.setHistoryTarget(editPage::restoreHistory);
        modals.setOnScopeChanged(() -> {
            listPage.onScopeChanged();
            editPage.endInteractions();
        });
        // 焦点变化时播报控件的可读名称（未开启朗读时 GameNarrator 内部会静默）
        focus.setListener((previous, next) -> {
            UiNarration.focus(next);
            focus.setSpaceActivates(!(next instanceof UiTextInput)
                    && !(next instanceof com.meteorite.itemdespawntowhat.client.ui.widget.UiListEditor));
            editPage.ensureVisible(next);
        });
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
        if (listPage.initialized()) {
            modals.setBounds(0, 0, width, height);
            return;
        }
        listPage.init();
        editPage.init();
        listPage.refreshList();
        editPage.rebuildEdit();
        rebuildFocus();
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
        THEME.drawWindow(graphics, new UiRect(0, 0, w, h));
        int headerHeight = mode == Mode.EDIT ? EDIT_HEADER_H : HEADER_H;
        int pagePad = mode == Mode.EDIT ? EDIT_PAD : PAD;
        THEME.drawDivider(graphics, 0, headerHeight, w);
        drawHeaderText(graphics);
        int y = headerHeight + PAD;
        if (mode == Mode.EDIT) {
            y = editPage.layoutTabs(graphics, font, mouseX, mouseY, pagePad, y);
        }
        int availableWidth = Math.max(0, w - pagePad * 2);
        int footerHeight = mode == Mode.LIST ? listPage.footerHeight(availableWidth) : editPage.footerHeight(availableWidth);
        int footerY = h - pagePad - footerHeight;
        int noticeY = footerY - PAD - (notice == null ? 0 : NOTICE_H);
        contentArea = new UiRect(pagePad, y, availableWidth, Math.max(0, noticeY - y - EDIT_PAD));
        if (mode == Mode.LIST) {
            listPage.render(graphics, font, mouseX, mouseY, contentArea);
        } else {
            graphics.fill(pagePad - 2, y - 2, w - pagePad + 2, Math.max(y - 2, noticeY - PAD), THEME.palette().panelFill());
            editPage.render(graphics, font, mouseX, mouseY, contentArea);
        }
        drawNotice(graphics, noticeY, w);
        if (mode == Mode.LIST) {
            listPage.renderFooter(graphics, font, mouseX, mouseY, footerY, Math.max(0, w - PAD * 2));
        } else {
            editPage.renderFooter(graphics, font, mouseX, mouseY, pagePad, footerY, availableWidth);
        }
        mouseX = actualMouseX;
        mouseY = actualMouseY;
        modals.render(graphics, font, mouseX, mouseY);
        if (modals.isEmpty()) {
            Component tip = null;
            if (notice != null && new UiRect(PAD, noticeY, availableWidth, NOTICE_H).contains(mouseX, mouseY)) {
                tip = notice;
            } else if (mode == Mode.EDIT && mouseY < EDIT_HEADER_H) {
                EditSession session = editingSession();
                if (session != null) {
                    tip = NaturalSummary.rule(session.draft().root()).copy().append("\n")
                            .append(ConsumptionSummary.hintRule(session.draft().root()));
                }
            } else if (mode == Mode.LIST) {
                tip = listPage.tooltipAt(mouseX, mouseY);
            }
            if (tip != null) {
                renderTooltip(graphics, tip, mouseX, mouseY);
            }
        }
        // 字段说明提示：没有弹窗时才显示，避免盖住上层内容
        if (modals.isEmpty() && mode == Mode.EDIT) {
            Component tip = editPage.tooltipAt(mouseX, mouseY);
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
            // 页头只显示当前实际生效名称；规则 ID 由基本页第一行的只读行单独承担
            title = Component.translatable(UI + "header.editing", labelText(editingId)).getString();
        }
        String state = Component.translatable(!workspace.active() ? UI + "state.freezing"
                : mode == Mode.EDIT ? UI + (model.dirtyRuleIds().isEmpty() ? "state.applied" : "state.unapplied") : UI + "state.active")
                .getString();
        int padding = mode == Mode.EDIT ? EDIT_PAD : PAD;
        int textY = mode == Mode.EDIT ? (EDIT_HEADER_H - font.lineHeight) / 2 : 3;
        int stateWidth = font.width(state) + 12;
        title = TextScroll.trimToWidth(font, title, Math.max(0, this.width - padding * 2 - stateWidth - PAD));
        graphics.drawString(font, title, padding, textY, THEME.palette().headerText(), false);
        if (mode == Mode.EDIT) {
            THEME.drawPanel(graphics, new UiRect(this.width - padding - stateWidth, 4, stateWidth, EDIT_HEADER_H - 8));
        }
        graphics.drawString(font, state, this.width - padding - font.width(state) - (mode == Mode.EDIT ? 6 : 0), textY, THEME.palette().headerText(), false);
    }

    // 底部状态提示
    private void drawNotice(GuiGraphics graphics, int y, int w) {
        if (notice == null) {
            return;
        }
        String text = TextScroll.trimToWidth(font, notice.getString(), Math.max(0, w - PAD * 2));
        graphics.drawString(font, text, PAD, y + 1, noticeColor, false);
    }

    // ---- 模式切换 ----

    private @Nullable EditSession editingSession() {
        return editingId == null ? null : model.session(editingId);
    }

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
            setNotice(Component.translatable(UI + "notice.rule_unavailable"), THEME.palette().warning());
            return;
        }
        if (editPage.blockNavigation()) {
            return;
        }
        editingId = id;
        mode = Mode.EDIT;
        // 换规则时重建页面状态（候选与效果选择归零）
        editPage.openNew();
        editPage.rebuildEdit();
        rebuildFocus();
    }

    // 损坏规则显示原始视图与服务端问题，不创建会被表单改写的草稿。
    private void showRuleDetails(RuleSnapshotEntry entry) {
        UiModal modal = UiModal.create(THEME, font);
        modal.title(Component.translatable(UI + "detail.rule", entry.id().toString()));
        modal.message(Component.translatable(UI + (RuleSnapshotEntry.STATUS_INVALID.equals(entry.status())
                ? "detail.invalid" : "detail.unavailable")));
        // 核心条目视图仍是 JSON 对象：经草稿入口转为值视图后再美化输出
        String raw = RuleDraft.of(entry.toJson()).root().toPrettyJson();
        modal.contentWidget(new ReadOnlyTextView(font, Component.literal(raw)), Math.clamp(height - 90, 36, 180));
        modal.cancel(Component.translatable(UI + "button.close"));
        modal.layoutCentered(width, height);
        modals.push(modal);
    }

    // 返回管理页：先结束交互并卸载页面，再刷新列表
    private void backToList() {
        if (!editPage.unmount()) {
            return;
        }
        mode = Mode.LIST;
        editingId = null;
        listPage.refreshList();
        rebuildFocus();
    }

    // 草稿改动清单（管理页底排与编辑页底排共用）
    private void showChanges() {
        Component body = Component.translatable(UI + "changes.note");
        for (String id : model.dirtyRuleIds()) {
            EditSession session = model.session(id);
            String kind = model.isRestoring(id) ? "restore" : model.isCreated(id) ? "create"
                    : session != null && session.draft().isDeleted() ? "remove" : "modify";
            body = body.copy().append("\n").append(Component.translatable(UI + "changes." + kind))
                    .append(": ").append(labelText(id));
        }
        modals.push(UiModal.create(THEME, font).title(Component.translatable(UI + "button.changes"))
                .message(body).preferredWidth(300).cancel(Component.translatable(UI + "button.cancel"))
                .layoutCentered(width, height));
    }

    private void onDraftChanged() {
        listNeedsRefresh = true;
    }

    // ---- 焦点 / 事件 / 生命周期 ----

    private void rebuildFocus() {
        focus.clear();
        focus.beginUpdate();
        if (mode == Mode.LIST) {
            listPage.addFocusTargets(focus);
        } else {
            editPage.addFocusTargets(focus);
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
        if (button == 0 && mode == Mode.LIST && listPage.openTagDetails(mouseX, mouseY)) {
            return true;
        }
        if (button == 0) {
            focusClicked(mouseX, mouseY);
        }
        if (mode == Mode.LIST) {
            return listPage.mouseClicked(mouseX, mouseY, button);
        }
        if (editPage.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (modals.isVisible()) {
            return modals.mouseReleased(mouseX, mouseY, button);
        }
        boolean consumed = editPage.mouseReleased(mouseX, mouseY, button);
        consumed |= listPage.mouseReleased(mouseX, mouseY, button);
        consumed |= editPage.footerMouseReleased(mouseX, mouseY, button);
        return consumed || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (modals.isVisible()) {
            return modals.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (editPage.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (mode == Mode.LIST && listPage.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
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
            if (listPage.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        } else if (editPage.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // F1–F12 属于原版全局热键（F2 截图、F3 调试、F11 全屏）。原版 KeyboardHandler 在
        // screen.keyPressed 返回 true 时会直接 return、不再把该键喂给 KeyMapping，因此本模组的
        // 任何界面都不得消费功能键；这里在最外层统一放行，弹层与页面都覆盖到。
        if (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F12) {
            return false;
        }
        if (modals.isVisible()) {
            return modals.keyPressed(keyCode, scanCode, modifiers);
        }
        if (UiHistoryDispatcher.dispatch(keyCode, scanCode, modifiers, (key, scan, mods) -> {
            if (mode == Mode.LIST && listPage.searchKeyPressed(key, scan, mods)) return true;
            if (mode == Mode.EDIT && editPage.keyPressed(key, scan, mods)) return true;
            return focus.keyPressed(key, scan, mods);
        }, editPage::restoreHistory)) return true;
        // Esc 先交给正在捕获输入的控件：行内精确输入回退缓冲、拖动中的数值取消预览；
        // 均未消费时才逐级取消（编辑页返回列表，列表页关闭屏幕）
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (focus.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (mode == Mode.EDIT && editPage.keyPressed(keyCode, scanCode, modifiers)) {
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
            if (listPage.listKeyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        } else if (editPage.keyPressed(keyCode, scanCode, modifiers)) {
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
            if (editPage.isPageFocusTarget(target) && !contentArea.contains(mouseX, mouseY)) {
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
        if (mode == Mode.EDIT && editPage.keyReleased(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (modals.isVisible()) {
            return modals.charTyped(codePoint, modifiers);
        }
        if (mode == Mode.LIST && listPage.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (mode == Mode.EDIT && editPage.charTyped(codePoint, modifiers)) {
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
            listPage.refreshList();
            editPage.rebuildEdit();
            rebuildFocus();
        }
        if (listNeedsRefresh) {
            listNeedsRefresh = false;
            listPage.refreshList();
        }
        editPage.tick();
        editPage.pollWorkspace();
        // P6：节流落盘、恢复提示、按钮状态、冲突弹窗与冻结
        model.tickPersistence();
        if (model.consumeDraftFailures() > 0) {
            setNotice(Component.translatable(UI + "notice.draft_save_failed"), THEME.palette().warning());
        }
        int restored = model.consumeRestoredCount();
        if (restored > 0) {
            setNotice(Component.translatable(UI + "notice.restored_draft", restored), THEME.palette().warning());
        }
        updateActionButtons();
        editPage.promptConflicts();
        boolean editable = workspace.active();
        if (!editable && !frozenNotified) {
            frozenNotified = true;
            setNotice(Component.translatable(UI + "notice.frozen"), THEME.palette().warning());
        } else if (editable) {
            frozenNotified = false;
        }
        applyEditableState(editable);
    }

    // 应用全部按钮与撤销/重做按钮的可用状态与标签
    private void updateActionButtons() {
        listPage.updateButtons();
        listPage.updateApplyButton();
        editPage.updateHistoryButtons();
    }

    // 冻结（APPLYING / 非 ACTIVE）时禁用表单控件；状态切换时才真正遍历
    private void applyEditableState(boolean editable) {
        if (editable == lastEditable) {
            return;
        }
        lastEditable = editable;
        applyEditableStateNow(editable);
    }

    // 页面重建后立即同步使能状态（页面控件是新建出来的，默认可用，必须显式同步一次）
    private void rebuildEditableState() {
        lastEditable = workspace.active();
        applyEditableStateNow(lastEditable);
    }

    // 立即同步页面的使能状态
    private void applyEditableStateNow(boolean editable) {
        editPage.setPagesEnabled(editable);
        // 列表页动作按钮同属可编辑面：冻结时一并禁用，避免「点了没反应」
        // （返回按钮不禁用，保证冻结期间玩家仍能退出屏幕）
        listPage.setButtonsEnabled(editable);
        // 应用/撤销/重做再按脏标记与历史精确修正
        updateActionButtons();
    }

    // 冻结（APPLYING / 非 ACTIVE）时禁止一切会改动草稿的入口，并给出明确提示而不是静默丢弃；返回 true 表示已被冻结阻断
    private boolean rejectWhenFrozen() {
        if (workspace.active()) {
            return false;
        }
        setNotice(Component.translatable(UI + "notice.frozen"), THEME.palette().warning());
        return true;
    }

    // 外部重新打开时刷新界面（此时工作区已进入 ACTIVE 并发出快照请求）
    public void refreshFromWorkspace() {
        model.refresh();
        listPage.refreshList();
        editPage.rebuildEdit();
        rebuildFocus();
    }

    // 用户主动关闭：有未保存改动先确认
    private void closeByUser() {
        if (editPage.blockNavigation()) {
            return;
        }
        if (model.hasDirty()) {
            confirm(UI + "confirm.discard_title", UI + "confirm.discard_message", () -> {
                // 玩家确认放弃：连落盘草稿一起删掉，避免下次打开又「恢复」回来
                model.discardAllDrafts();
                editPage.close();
                workspace.close("screen_closed");
                this.onClose();
            });
            return;
        }
        editPage.close();
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
        editPage.applyAllForms();
        editPage.close();
        // P6：关屏前把未应用草稿写盘（断线 / 关游戏 / 关界面都能恢复）
        model.persistNow();
        super.onClose();
    }

    // ---- 小工具 ----

    private void setNotice(@Nullable Component message, int color) {
        this.notice = message;
        this.noticeColor = color;
    }

    // 条目显示名（空名或与 id 相同时回退到本地化占位）
    private String labelText(String id) {
        RuleValue body = model.displayBody(id);
        String label = body == null ? "" : RuleNaming.ruleTitle(body, RuleDisplayLabels::label).getString();
        return label.isBlank() || label.equals(id) ? Component.translatable(UI + "recipe.unnamed").getString() : label;
    }

    // 确认弹窗
    private void confirm(String titleKey, String messageKey, Runnable onConfirm) {
        UiModal modal = UiModal.create(THEME, font);
        modal.title(Component.translatable(titleKey));
        modal.message(Component.translatable(messageKey));
        modal.confirm(Component.translatable(UI + "button.confirm"), onConfirm::run);
        modal.cancel(Component.translatable(UI + "button.cancel"));
        modal.layoutCentered(this.width, this.height);
        modals.push(modal);
    }
}
