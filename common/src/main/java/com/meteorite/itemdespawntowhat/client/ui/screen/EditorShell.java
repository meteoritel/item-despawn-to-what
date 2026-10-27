package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorWorkspaceView;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientState;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiHistoryDispatcher;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCodeView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiListEditor;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModalStack;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiNarration;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import com.meteorite.itemdespawntowhat.core.api.DevelopmentDiagnostics;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleIssue;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleSnapshotEntry;
import com.meteorite.itemdespawntowhat.platform.Services;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 编辑器外壳：拥有当前编辑目标、由目标派生的编辑会话、底部提示、可编辑判定与跨页命令。
 * <p>页面在构造期取得稳定协作对象（规则模型、工作区视图、列表展示缓存、字体、弹窗栈、焦点管理器），
 * 外壳本身只表达编辑语义：运行期状态查询、跨页命令与页切换。屏幕只负责窗口生命周期、排版、
 * 绘制与输入派发，并从这里读取状态。</p>
 */
final class EditorShell {


    /**
     * 底部提示的级别：只表达信息性质，形态、用色与停留由渲染层决定。
     */
    enum NoticeLevel {
        /** 操作回执：短暂展示后自动清除 */
        INFO,
        /** 成功回执：与操作回执同形，语义上表示完成 */
        SUCCESS,
        /** 需要玩家留意的异常：常驻，直到被替换或页面切换 */
        WARNING,
        /** 失败或危险：常驻，用危险色区分 */
        DANGER
    }

    private static final UiHostTheme THEME = UiHostThemes.defaultTheme();
    private static final String UI = TypeLabels.UI_PREFIX;
    private static final long NOTICE_TTL_MS = 5000L;
    private static final int CHANGES_MODAL_WIDTH = 420;
    private static final int CHANGES_MODAL_MAX_HEIGHT = 220;
    private static final int CHANGES_MODAL_SCREEN_MARGIN = 90;

    private final EditorWorkspaceView workspace;
    private final RuleEditorModel model;
    private final RuleListDisplayCache display;
    private final Font font;
    private final UiFocusManager focus = new UiFocusManager();
    private final UiModalStack modals = new UiModalStack(THEME);

    private final RuleEditorListPage listPage;
    private final RuleEditorEditPage editPage;
    private final RuleEditorDebugPage debugPage;

    // 活动页就是外壳持有的那个页实例：页身份没有第二份枚举
    private EditorPage page;
    private @Nullable String editingId;

    private @Nullable Component notice;
    private NoticeLevel noticeLevel = NoticeLevel.INFO;
    private long noticeExpiresAt;

    // 模态从无到有时清除常驻提示，避免两处同时给结论
    private boolean lastModalsEmpty = true;
    // 快照解析失败与冻结只提示一次；草稿修改后列表需刷新
    private boolean snapshotFailureNotified;
    private boolean frozenNotified;
    private boolean listNeedsRefresh;
    // 草稿冲刷只做一次：关闭路径会连续经过多个入口
    private boolean released;
    private boolean closeRequested;

    EditorShell(EditorWorkspaceView workspace, RuleEditorModel model, RuleListDisplayCache display, Font font) {
        this.workspace = workspace;
        this.model = model;
        this.display = display;
        this.font = font;
        this.listPage = new RuleEditorListPage(this, model, display, font, modals, workspace);
        this.editPage = new RuleEditorEditPage(this, model, workspace, font, modals, focus);
        this.debugPage = new RuleEditorDebugPage(this, model, workspace, font, modals);
        this.page = listPage;
        modals.setHistoryTarget(editPage::restoreHistory);
        modals.setOnScopeChanged(() -> {
            listPage.onScopeChanged();
            editPage.endInteractions();
            debugPage.onScopeChanged();
        });
        // 焦点变化时播报控件的可读名称（未开启朗读时 GameNarrator 内部会静默）
        focus.setListener((previous, next) -> {
            UiNarration.focus(next);
            focus.setSpaceActivates(!(next instanceof UiTextInput)
                    && !(next instanceof UiListEditor));
            editPage.ensureVisible(next);
        });
        model.refresh();
    }

    // ---- 查询 ----


    // 当前活动页：屏幕只按这个接口派发渲染与输入，不再按模式分支
    EditorPage activePage() {
        return page;
    }

    boolean isEditing() {
        return page == editPage;
    }

    @Nullable String editingId() {
        return editingId;
    }

    @Nullable EditSession editingSession() {
        return editingId == null ? null : model.session(editingId);
    }

    /** 当前内容是否可写：只有工作区处于 ACTIVE 时可编辑。 */
    boolean editable() {
        return workspace.active();
    }

    boolean saving() {
        return workspace.state() == RuleEditClientState.APPLYING;
    }

    @Nullable Component notice() {
        return notice;
    }

    NoticeLevel noticeLevel() {
        return noticeLevel;
    }

    RuleEditorListPage listPage() {
        return listPage;
    }

    RuleEditorEditPage editPage() {
        return editPage;
    }

    RuleEditorDebugPage debugPage() {
        return debugPage;
    }

    UiFocusManager focus() {
        return focus;
    }

    UiModalStack modals() {
        return modals;
    }

    EditorWorkspaceView workspace() {
        return workspace;
    }

    // 条目显示名（空名或与 id 相同时回退到本地化占位）；与列表共用同一份展示快照
    String labelText(String id) {
        return display.snapshot(id).title();
    }

    // 当前编辑目标的内容是否只读：编辑页据此显示沙盒标识；控件仍可写
    boolean isEditReadOnly() {
        return page == editPage && editingId != null && model.isReadOnly(editingId);
    }

    /**
     * 取出并清除「请求关闭界面」标志；屏幕在事件结束与每帧更新时消费。
     */
    boolean takeCloseRequest() {
        if (!closeRequested) {
            return false;
        }
        closeRequested = false;
        return true;
    }

    /** 请求屏幕关闭自己：外壳不回调屏幕类型，只留下标志。 */
    void requestClose() {
        closeRequested = true;
    }

    // ---- 命令 ----

    // Esc 逐级取消：编辑页 / 调试页返回列表，列表页关闭界面
    void escape() {
        if (page == listPage) {
            closeByUser();
        } else {
            backToList();
        }
    }

    // 保存遮罩接管输入前：结束页面交互并释放焦点
    void leaveInteractions() {
        listPage.onScopeChanged();
        editPage.endInteractions();
        focus.clearFocus();
    }

    void openEditor(String id) {
        if (!editable()) {
            setNotice(Component.translatable(UI + "notice.frozen"), NoticeLevel.WARNING);
            return;
        }
        if (id == null) {
            return;
        }
        RuleSnapshotEntry entry = model.entry(id);
        // 只读数据包条目也进入编辑页（内容走沙盒会话）；只有损坏或没有可打开内容的条目才走详情弹窗
        if (entry != null && (RuleSnapshotEntry.STATUS_INVALID.equals(entry.status())
                || entry.issues().stream().anyMatch(issue -> RuleIssue.SEVERITY_ERROR.equals(issue.severity())))) {
            showRuleDetails(entry);
            return;
        }
        if (model.openSession(id) == null) {
            if (entry != null) {
                showRuleDetails(entry);
                return;
            }
            setNotice(Component.translatable(UI + "notice.rule_unavailable"), NoticeLevel.WARNING);
            return;
        }
        editPage.flushPendingEdits();
        // 切换编辑目标即丢弃上一个只读条目的沙盒内容；当前目标的会话保留（启停意图不受影响）
        model.dropSandboxSessions(id);
        // 编辑页打开规则时按需取回来源视图；失败或超时只影响来源对照
        model.requestSourceView(id);
        editingId = id;
        page = editPage;
        // 页面切换：提示只对切换前的页面成立，连操作回执一起清除
        setNotice(null, NoticeLevel.INFO);
        // 换规则时重建页面状态（候选与效果选择归零）
        editPage.openNew();
        editPage.rebuildEdit();
        rebuildFocus();
    }

    void backToList() {
        if (saving()) {
            return;
        }
        editPage.unmount();
        // 离开编辑页：只读条目的沙盒内容全部丢弃，未应用的启停意图保留
        model.dropSandboxSessions(null);
        page = listPage;
        editingId = null;
        setNotice(null, NoticeLevel.INFO);
        listPage.refreshList();
        rebuildFocus();
    }

    // 调试页入口条件：debug 模式开启、停在管理页、未在保存且没有弹层
    boolean canEnterDebug() {
        return Services.PLATFORM.isDevelopmentEnvironment() && page == listPage && !saving() && modals.isEmpty();
    }

    void enterDebug() {
        page = debugPage;
        setNotice(null, NoticeLevel.INFO);
        debugPage.updateButtons();
        rebuildFocus();
    }

    void openInputTab() {
        editPage.openInputTab();
    }

    void save() {
        editPage.save();
    }

    boolean dispatchHistory(int keyCode, int scanCode, int modifiers, UiHistoryDispatcher.KeyHandler activeScope) {
        return UiHistoryDispatcher.dispatch(keyCode, scanCode, modifiers, activeScope, editPage::restoreHistory);
    }

    // 草稿改动清单（管理页底排与编辑页底排共用）
    void showChanges() {
        RuleChangesView changes = new RuleChangesView(font, model, this::labelText);
        modals.push(UiModal.create(THEME, font).title(Component.translatable(UI + "button.changes"))
                .contentWidget(changes, changes.preferredHeight(Math.clamp(screenHeight() - CHANGES_MODAL_SCREEN_MARGIN,
                        RuleRecipeView.CONTENT_HEIGHT, CHANGES_MODAL_MAX_HEIGHT)))
                .preferredWidth(CHANGES_MODAL_WIDTH).cancel(Component.translatable(UI + "button.cancel"))
                .layoutCentered(screenWidth(), screenHeight()));
    }

    // 确认弹窗：页面只表达「要确认什么」，按钮与排版由这里装配
    void confirm(Component title, Component message, Runnable onConfirm) {
        UiModal modal = UiModal.create(THEME, font);
        modal.title(title);
        modal.message(message);
        modal.confirm(Component.translatable(UI + "button.confirm"), onConfirm::run);
        modal.cancel(Component.translatable(UI + "button.cancel"));
        modal.layoutCentered(screenWidth(), screenHeight());
        modals.push(modal);
    }

    void refreshList() {
        listPage.refreshList();
    }

    void draftChanged() {
        listNeedsRefresh = true;
    }

    void rebuildFocus() {
        focus.clear();
        focus.beginUpdate();
        activePage().addFocusTargets(focus);
        focus.endUpdate();
        if (focus.focused() == null) {
            focus.focusFirst();
        }
    }

    // ---- 提示 ----

    void setNotice(@Nullable Component message, NoticeLevel level) {
        this.notice = message;
        this.noticeLevel = level;
        // 操作回执短暂显示后自动消失；其余级别需要玩家处理，常驻到被替换或页面切换
        this.noticeExpiresAt = message != null && level == NoticeLevel.INFO ? Util.getMillis() + NOTICE_TTL_MS : 0L;
    }

    // 只清除常驻提示：操作回执自身会超时；模态打开与页面切换各自触发一次清除
    private void clearStickyNotice() {
        if (notice != null && noticeLevel != NoticeLevel.INFO) {
            setNotice(null, NoticeLevel.INFO);
        }
    }

    // ---- 每帧推进 ----

    void tick() {
        modals.tick();
        boolean modalsEmpty = modals.isEmpty();
        if (lastModalsEmpty && !modalsEmpty) {
            clearStickyNotice();
        }
        lastModalsEmpty = modalsEmpty;
        // 快照是打开屏幕之后才到的（打开时只发出了请求），每帧消费一次；
        // refresh() 内部按快照实例去重，重复调用无副作用
        long applyStarted = DevelopmentDiagnostics.start();
        boolean applied = model.refresh();
        if (applied) {
            logSnapshotApplied(DevelopmentDiagnostics.micros(applyStarted));
            listPage.refreshList();
            editPage.rebuildEdit();
            rebuildFocus();
            // 快照修订变化会清空来源视图缓存：仍在编辑的规则按需重新取回，失败只影响来源对照
            if (page == editPage && editingId != null) {
                model.requestSourceView(editingId);
            }
        }
        if (listNeedsRefresh) {
            listNeedsRefresh = false;
            listPage.refreshList();
        }
        editPage.tick();
        editPage.pollWorkspace();
        // 节流落盘、恢复提示、按钮状态、冲突弹窗与冻结
        model.tickPersistence();
        if (model.consumeDraftFailures() > 0) {
            setNotice(Component.translatable(UI + "notice.draft_save_failed"), NoticeLevel.WARNING);
        }
        // 快照解析失败时首载会一直停在「加载中」：用常驻警告让玩家知道当前内容不可用
        if (workspace.snapshotFailure() == null) {
            snapshotFailureNotified = false;
        } else if (!snapshotFailureNotified) {
            snapshotFailureNotified = true;
            setNotice(Component.translatable(UI + "notice.snapshot_failed"), NoticeLevel.DANGER);
        }
        int restored = model.consumeRestoredCount();
        if (restored > 0) {
            setNotice(Component.translatable(UI + "notice.restored_draft", restored), NoticeLevel.WARNING);
        }
        updateActionButtons();
        editPage.promptConflicts();
        if (!editable() && !frozenNotified) {
            frozenNotified = true;
            setNotice(Component.translatable(UI + "notice.frozen"), NoticeLevel.WARNING);
        } else if (editable()) {
            frozenNotified = false;
        }
        // 操作回执是操作反馈：达到存活时长后自动清除，其余级别由替换或页面切换清除
        if (notice != null && noticeExpiresAt > 0L && Util.getMillis() >= noticeExpiresAt) {
            setNotice(null, NoticeLevel.INFO);
        }
    }

    // 首载分段观测：快照进入模型（绑定草稿、重建会话）的耗时
    private void logSnapshotApplied(long micros) {
        if (!DevelopmentDiagnostics.enabled()) {
            return;
        }
        JsonObject data = new JsonObject();
        data.addProperty("entries", model.ruleIds().size());
        data.addProperty("dirty", model.dirtyRuleIds().size());
        data.addProperty("apply_ms", micros / 1000.0);
        DevelopmentDiagnostics.event(DevelopmentDiagnostics.SCENE_FIRST_LOAD, "CLIENT_APPLY", data);
    }

    // 应用全部按钮与撤销/重做按钮的可用状态与标签
    private void updateActionButtons() {
        listPage.updateButtons();
        editPage.updateButtons();
        debugPage.updateButtons();
    }

    // ---- 关闭与落盘 ----

    void refreshFromWorkspace() {
        model.refresh();
        listPage.refreshList();
        editPage.rebuildEdit();
        rebuildFocus();
    }

    // 用户主动关闭：有未保存改动时提供「暂存到本地」「放弃」「取消」三个选择
    void closeByUser() {
        if (saving()) {
            return;
        }
        editPage.flushPendingEdits();
        if (!model.hasDirty()) {
            closeEditor();
            return;
        }
        UiModal modal = UiModal.create(THEME, font);
        modal.title(Component.translatable(UI + "confirm.discard_title"));
        modal.message(Component.translatable(UI + "confirm.discard_message"));
        modal.addButton(new UiButton(THEME, font, Component.translatable(UI + "button.save_local"),
                UiButtonVariant.PRIMARY, () -> {
            modals.close(modal);
            // 先把当前表单输入并入草稿，再立即写盘；关闭流程本身也会再冲刷一次
            editPage.applyAllForms();
            model.persistNow();
            closeEditor();
        }));
        modal.addButton(new UiButton(THEME, font, Component.translatable(UI + "button.discard"),
                UiButtonVariant.DANGER, () -> {
            modals.close(modal);
            // 玩家确认放弃：连落盘草稿一起删掉，避免下次打开又「恢复」回来
            model.discardAllDrafts();
            closeEditor();
        }));
        modal.cancel(Component.translatable(UI + "button.cancel"));
        modal.layoutCentered(screenWidth(), screenHeight());
        modals.push(modal);
    }

    // 关闭编辑界面：先冲刷草稿再结束工作区会话——会话结束会立刻摘屏并触发 removed
    private void closeEditor() {
        flushDrafts();
        workspace.close("screen_closed");
        requestClose();
    }

    // 关屏前把未应用的表单输入并入草稿并立即写盘（断线 / 关游戏 / 关界面都能恢复）。
    // 保存中由服务端回执收尾，这里不碰页面状态；重复调用只生效一次。
    void flushDrafts() {
        if (released || saving()) {
            return;
        }
        released = true;
        editPage.applyAllForms();
        editPage.close();
        // 关屏前丢弃沙盒内容：它们不进变更集，也没有必要留在内存里
        model.dropSandboxSessions(null);
        model.persistNow();
    }

    // 损坏规则显示原始视图与服务端问题，不创建会被表单改写的草稿
    private void showRuleDetails(RuleSnapshotEntry entry) {
        UiModal modal = UiModal.create(THEME, font);
        modal.title(Component.translatable(UI + "detail.rule", entry.id().toString()));
        // 说明与原始内容同处一个只读代码块，避免弹窗正文与代码区分成两套排版
        Component note = Component.translatable(UI + (RuleSnapshotEntry.STATUS_INVALID.equals(entry.status())
                ? "detail.invalid" : "detail.unavailable"));
        // 核心条目视图仍是 JSON 对象：经草稿入口转为值视图后再美化输出
        String raw = RuleDraft.of(entry.toJson()).root().toPrettyJson();
        modal.contentWidget(new UiCodeView(THEME, font).setSections(List.of(new UiCodeView.Section(note, null, raw))),
                Math.clamp(screenHeight() - 90, 36, 180));
        modal.cancel(Component.translatable(UI + "button.close"));
        modal.layoutCentered(screenWidth(), screenHeight());
        modals.push(modal);
    }

    // ---- 实时尺寸 ----

    /** 当前窗口宽度：由屏幕在每帧与事件里刷新，页与外壳都按它排版。 */
    private int screenWidth = 320;
    private int screenHeight = 240;

    void setScreenSize(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }

    int screenWidth() {
        return screenWidth;
    }

    int screenHeight() {
        return screenHeight;
    }
}
