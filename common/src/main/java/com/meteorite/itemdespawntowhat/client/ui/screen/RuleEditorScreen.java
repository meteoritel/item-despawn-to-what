package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDescriptors;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorWorkspaceView;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientState;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiListEditor;
import com.meteorite.itemdespawntowhat.platform.Services;
import com.meteorite.itemdespawntowhat.core.api.DevelopmentDiagnostics;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRenderLayers;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFlatText;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiHistoryDispatcher;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.shared.BlockPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.DescriptionLine;
import com.meteorite.itemdespawntowhat.client.ui.shared.EntityPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCodeView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiDescriptionTooltip;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiListView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTypePicker;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModalStack;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiNarration;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleIssue;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshotEntry;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 规则编辑器主屏。
 * <p>两种模式：规则管理页（列表 / 搜索 / 筛选 / 新建 / 复制 / 停用 / 删除）与规则编辑页
 * （基本信息 / 源物品 / 触发条件 / 效果 四个页签）。所有字段编辑走描述符驱动的
 * {@link com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView}，条件树走
 * {@link com.meteorite.itemdespawntowhat.client.ui.widget.UiConditionTreeSummary}，界面任何位置都不出现可编辑 JSON 文本框。
 * <p>本类只保留模式切换、屏幕生命周期、布局编排与事件派发：管理页在 {@link RuleEditorListPage}
 * （条目动作在 {@link RuleEditorListActions}），编辑页与保存 / 校验入口在 {@link RuleEditorEditPage}
 * （本地校验在 {@link RuleEditorValidation}），两者的公共按钮排为 {@link RuleEditorScreenButtonBar}。
 * <p>只有工作区处于 ACTIVE 时可编辑；APPLYING 冻结；工作区进入 FREE 时自行关屏。
 */
public final class RuleEditorScreen extends Screen {

    // 宿主默认主题：屏幕从主题入口取样式后注入本类装配的控件
    private static final UiHostTheme THEME = UiHostThemes.defaultTheme();

    // 布局常量
    private static final int PAD = 4;
    private static final int EDIT_HEADER_H = 24;
    private static final int EDIT_PAD = 8;
    private static final int NOTICE_H = 10;
    // INFO 提示的存活时长：操作回执不需要常驻，超时后自动清除
    private static final long NOTICE_INFO_TTL_MS = 5000L;
    // 实心提示色块的左右内边距
    private static final int NOTICE_CHIP_PAD = 3;
    private static final int CHANGES_MODAL_WIDTH = 420;
    private static final int CHANGES_MODAL_MAX_HEIGHT = 220;
    private static final int CHANGES_MODAL_SCREEN_MARGIN = 90;
    // i18n 前缀
    private static final String UI = TypeLabels.UI_PREFIX;

    // 模式：DEBUG 为开发环境专用页，只在 debug 模式下可从管理页进入
    private enum Mode {LIST, EDIT, DEBUG}

    /**
     * 底部状态提示的级别：决定提示的绘制形态与存活时长。
     * <p>调用点只表达语义，具体用色由屏幕按级别从主题调色板取，各页面不再自带色值。</p>
     */
    public enum NoticeLevel {
        /**
         * 操作回执：纯文本绘制，短暂显示后自动清除
         */
        INFO,
        /**
         * 需要玩家留意的异常：实心色块常驻，直到被新提示替换或页面 / 模态切换
         */
        WARNING,
        /**
         * 危险或失败：形态与 WARNING 一致，改用危险色区分
         */
        DANGER
    }

    // 宿主工作区与屏幕级共享状态
    private final EditorWorkspaceView workspace;
    private final RuleEditorModel model;
    // 列表展示快照缓存：列表页、页头标题与提示共用，避免按帧重复取体
    private final RuleListDisplayCache display;
    private final UiFocusManager focus = new UiFocusManager();
    private final UiModalStack modals = new UiModalStack(THEME);

    // 管理页（LIST）、编辑页（EDIT）与调试页（DEBUG）：各自持有控件与视图状态
    private final RuleEditorListPage listPage;
    private final RuleEditorEditPage editPage;
    private final RuleEditorDebugPage debugPage;

    // 保存遮罩按需创建，null 表示尚未打开
    private @Nullable RuleSaveOverlay saveOverlay;

    private Mode mode = Mode.LIST;
    private @Nullable String editingId;

    // 状态提示（保存回执 / 校验失败等）
    private @Nullable Component notice;
    private NoticeLevel noticeLevel = NoticeLevel.INFO;
    // INFO 提示自动清除的时间戳（{@link Util#getMillis()} 口径）；0 表示常驻
    private long noticeExpiresAt;
    // 上一帧的提示是否被截断（决定悬停提示是否出现）
    private boolean noticeTruncated;
    // 上一帧绘制的提示区域（悬停命中测试用），未绘制时为 null
    private @Nullable UiRect noticeRect;
    private @Nullable UiRect truncatedTitleRect;
    // 上一帧是否存在模态（用于识别「模态打开」这一清除时机）
    private boolean lastModalsEmpty = true;

    // 状态同步标志：快照解析失败与冻结只提示一次，草稿修改后列表需刷新
    private boolean snapshotFailureNotified;
    // 上一帧的可编辑状态（冻结切换时刷新表单使能）
    private boolean lastEditable = true;
    private boolean frozenNotified;
    private boolean listNeedsRefresh;

    // 内容区（每帧重算，供命中测试复用）
    private UiRect contentArea = new UiRect(0, 0, 0, 0);

    // 草稿冲刷只做一次：关闭路径会连续经过 closeEditor / onClose / removed
    private boolean released;

    // 构造：由 {@link RuleEditorOpener} 在服务端授权成功后调用
    public RuleEditorScreen(EditorWorkspaceView workspace) {
        super(Component.translatable(UI + "title"));
        BuiltinEditorDescriptors.bootstrap();
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.model = new RuleEditorModel(workspace);
        this.display = new RuleListDisplayCache(this.model);
        // 管理页宿主适配：把屏幕能力暴露给列表页与条目动作
        RuleEditorListPage.Host listHost = new RuleEditorListPage.Host() {
            @Override
            public Font font() {
                return RuleEditorScreen.this.font;
            }

            @Override
            public UiModalStack modals() {
                return modals;
            }

            @Override
            public RuleEditorModel model() {
                return model;
            }

            @Override
            public EditorWorkspaceView workspace() {
                return RuleEditorScreen.this.workspace;
            }

            @Override
            public int screenWidth() {
                return RuleEditorScreen.this.width;
            }

            @Override
            public int screenHeight() {
                return RuleEditorScreen.this.height;
            }

            @Override
            public boolean rejectWhenFrozen() {
                return RuleEditorScreen.this.rejectWhenFrozen();
            }

            @Override
            public void setNotice(@Nullable Component message, int color) {
                RuleEditorScreen.this.setNotice(message, color);
            }

            @Override
            public RuleListDisplayCache display() {
                return display;
            }

            @Override
            public void openEditor(String id) {
                RuleEditorScreen.this.openEditor(id);
            }

            @Override
            public void selectInputTab() {
                editPage.openInputTab();
            }

            @Override
            public void showChanges() {
                RuleEditorScreen.this.showChanges();
            }

            @Override
            public void save() {
                editPage.save();
            }

            @Override
            public void confirm(String titleKey, String messageKey, Runnable onConfirm) {
                RuleEditorScreen.this.confirm(titleKey, messageKey, onConfirm);
            }

            @Override
            public void refreshList() {
                listPage.refreshList();
            }
        };
        // 编辑页宿主适配：把屏幕能力暴露给页签组合视图与保存流程
        RuleEditorEditPage.Host editHost = new RuleEditorEditPage.Host() {
            @Override
            public Font font() {
                return RuleEditorScreen.this.font;
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
            public RuleEditorModel model() {
                return model;
            }

            @Override
            public EditorWorkspaceView workspace() {
                return RuleEditorScreen.this.workspace;
            }

            @Override
            public @Nullable EditSession session() {
                return editingSession();
            }

            @Override
            public @Nullable String editingId() {
                return editingId;
            }

            @Override
            public boolean isEditMode() {
                return mode == Mode.EDIT;
            }

            @Override
            public void onDraftChanged() {
                RuleEditorScreen.this.onDraftChanged();
            }

            @Override
            public void rebuildFocus() {
                RuleEditorScreen.this.rebuildFocus();
            }

            @Override
            public void setNotice(@Nullable Component message, int color) {
                RuleEditorScreen.this.setNotice(message, color);
            }

            @Override
            public void refreshList() {
                listPage.refreshList();
            }

            @Override
            public boolean rejectWhenFrozen() {
                return RuleEditorScreen.this.rejectWhenFrozen();
            }

            @Override
            public void openEditor(String id) {
                RuleEditorScreen.this.openEditor(id);
            }

            @Override
            public void closeScreen() {
                RuleEditorScreen.this.onClose();
            }

            @Override
            public void rebuildEditableState() {
                RuleEditorScreen.this.rebuildEditableState();
            }

            @Override
            public void showChanges() {
                RuleEditorScreen.this.showChanges();
            }

            @Override
            public void backToList() {
                RuleEditorScreen.this.backToList();
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
        // 调试页宿主适配：只暴露调试页需要的卡片计数、刷新与返回能力
        RuleEditorDebugPage.Host debugHost = new RuleEditorDebugPage.Host() {
            @Override
            public Font font() {
                return RuleEditorScreen.this.font;
            }

            @Override
            public UiModalStack modals() {
                return modals;
            }

            @Override
            public RuleEditorModel model() {
                return model;
            }

            @Override
            public EditorWorkspaceView workspace() {
                return RuleEditorScreen.this.workspace;
            }

            @Override
            public int screenWidth() {
                return RuleEditorScreen.this.width;
            }

            @Override
            public int screenHeight() {
                return RuleEditorScreen.this.height;
            }

            @Override
            public void setNotice(@Nullable Component message, int color) {
                RuleEditorScreen.this.setNotice(message, color);
            }

            @Override
            public void refreshList() {
                listPage.refreshList();
            }

            @Override
            public void backToList() {
                RuleEditorScreen.this.backToList();
            }
        };
        this.listPage = new RuleEditorListPage(listHost);
        this.editPage = new RuleEditorEditPage(editHost);
        this.debugPage = new RuleEditorDebugPage(debugHost);
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
        this.model.refresh();
    }

    // 编辑器不做游戏内暂停
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * @return 保存期间禁止 Esc 关闭；断线收尾由工作区控制
     */
    @Override
    public boolean shouldCloseOnEsc() {
        return !saving();
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
        debugPage.init();
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
        UiTypePicker picker = mode == Mode.EDIT ? editPage.activeTypePicker() : null;
        boolean saving = saving();
        if (saving) {
            saveOverlay();
        } else {
            saveOverlay = null;
        }
        var pointer = UiPointer.gated(!saving && modals.isEmpty() && picker == null, mouseX, mouseY);
        mouseX = pointer.x();
        mouseY = pointer.y();
        int w = this.width;
        int h = this.height;
        modals.setBounds(0, 0, w, h);
        THEME.drawWindow(graphics, new UiRect(0, 0, w, h));
        int headerHeight = mode == Mode.EDIT ? EDIT_HEADER_H : UiTheme.HEADER_HEIGHT;
        int pagePad = mode == Mode.EDIT ? EDIT_PAD : PAD;
        THEME.drawDivider(graphics, 0, headerHeight, w);
        drawHeaderText(graphics);
        int availableWidth = Math.max(0, w - pagePad * 2);
        int y = headerHeight + PAD;
        if (mode == Mode.EDIT) {
            y = editPage.layoutTabs(graphics, font, mouseX, mouseY, pagePad, y);
        }
        int footerHeight = switch (mode) {
            case LIST -> listPage.footerHeight(availableWidth);
            case EDIT -> editPage.footerHeight(availableWidth);
            case DEBUG -> debugPage.footerHeight(availableWidth);
        };
        int footerY = h - pagePad - footerHeight;
        int noticeY = footerY - PAD - (notice == null ? 0 : NOTICE_H);
        contentArea = new UiRect(pagePad, y, availableWidth, Math.max(0, noticeY - y - pagePad));
        switch (mode) {
            case LIST -> listPage.render(graphics, font, mouseX, mouseY, contentArea);
            case EDIT -> {
                graphics.fill(pagePad - 2, y - 2, w - pagePad + 2, Math.max(y - 2, noticeY - PAD), THEME.palette().panelFill());
                editPage.render(graphics, font, mouseX, mouseY, contentArea);
            }
            case DEBUG -> debugPage.render(graphics, font, mouseX, mouseY, contentArea);
        }
        drawNotice(graphics, noticeY, w);
        switch (mode) {
            case LIST -> listPage.renderFooter(graphics, font, mouseX, mouseY, footerY, Math.max(0, w - PAD * 2));
            case EDIT -> editPage.renderFooter(graphics, font, mouseX, mouseY, pagePad, footerY, availableWidth);
            case DEBUG -> debugPage.renderFooter(graphics, font, mouseX, mouseY, pagePad, footerY, availableWidth);
        }
        mouseX = actualMouseX;
        mouseY = actualMouseY;
        if (saving) {
            int overlayMouseX = mouseX;
            int overlayMouseY = mouseY;
            UiRenderLayers.draw(graphics, UiRenderLayers.MODAL_STEP,
                    () -> saveOverlay().render(graphics, width, height, overlayMouseX, overlayMouseY, Util.getMillis()));
            return;
        }
        modals.render(graphics, font, mouseX, mouseY);
        if (modals.isEmpty() && picker != null) {
            picker.setBounds(contentArea.x(), contentArea.y(), contentArea.width(), contentArea.height());
            picker.render(graphics, font, mouseX, mouseY);
        }
        if (modals.isEmpty() && picker == null) {
            Component tip = null;
            if (notice != null && noticeTruncated && noticeRect != null && noticeRect.contains(mouseX, mouseY)) {
                tip = notice;
            } else if (mode == Mode.EDIT && truncatedTitleRect != null
                    && truncatedTitleRect.contains(mouseX, mouseY) && editingId != null) {
                new UiDescriptionTooltip(THEME, font).render(graphics, new UiRect(0, 0, width, height),
                        List.of(DescriptionLine.text(Component.literal(labelText(editingId)),
                                DescriptionLine.Tone.VALUE, 0)), mouseX, mouseY);
            } else if (mode == Mode.LIST) {
                listPage.renderDescriptionTooltip(graphics, mouseX, mouseY);
            }
            if (tip != null) {
                renderTooltip(graphics, tip, mouseX, mouseY);
            }
        }
        // 字段说明提示：没有弹窗时才显示，避免盖住上层内容
        if (modals.isEmpty() && picker == null && mode == Mode.EDIT && !editPage.renderDescriptionTooltip(graphics, mouseX, mouseY)) {
            Component tip = editPage.tooltipAt(mouseX, mouseY);
            if (tip != null) {
                renderTooltip(graphics, tip, mouseX, mouseY);
            }
        }
    }

    // Component 的单行 tooltip 重载不处理换行；按窗口宽度折行后交给无阴影提示面板定位。
    private void renderTooltip(GuiGraphics graphics, Component tip, int mouseX, int mouseY) {
        UiFlatText.tooltip(graphics, font, tip, Math.clamp(width - 16, 1, 240), mouseX, mouseY);
    }

    // 顶部标题与状态
    private void drawHeaderText(GuiGraphics graphics) {
        truncatedTitleRect = null;
        String title = this.getTitle().getString();
        if (mode == Mode.DEBUG) {
            title = Component.translatable(UI + "debug.title").getString();
        } else if (mode == Mode.EDIT && editingId != null) {
            // 页头只显示当前实际生效名称；规则 ID 由基本页第一行的只读行单独承担
            title = Component.translatable(UI + "header.editing", labelText(editingId)).getString();
        }
        String state = Component.translatable(!workspace.active() ? UI + "state.freezing"
                        : mode == Mode.EDIT ? UI + (model.dirtyRuleIds().isEmpty() ? "state.applied" : "state.unapplied") : UI + "state.active")
                .getString();
        int padding = mode == Mode.EDIT ? EDIT_PAD : PAD;
        int textY = mode == Mode.EDIT ? (EDIT_HEADER_H - font.lineHeight) / 2 : 3;
        int stateWidth = font.width(state) + 12;
        String fullTitle = title;
        title = TextScroll.trimToWidth(font, title, Math.max(0, this.width - padding * 2 - stateWidth - PAD));
        if (mode == Mode.EDIT && !title.equals(fullTitle)) {
            truncatedTitleRect = new UiRect(padding, textY, font.width(title), font.lineHeight);
        }
        graphics.drawString(font, title, padding, textY, THEME.palette().headerText(), false);
        if (mode == Mode.EDIT) {
            THEME.drawPanel(graphics, new UiRect(this.width - padding - stateWidth, 4, stateWidth, EDIT_HEADER_H - 8));
        }
        graphics.drawString(font, state, this.width - padding - font.width(state) - (mode == Mode.EDIT ? 6 : 0), textY, THEME.palette().headerText(), false);
    }

    // 底部状态提示：INFO 为纯文本，WARNING / DANGER 为实心色块（颜色与形状双线索）
    private void drawNotice(GuiGraphics graphics, int y, int w) {
        noticeRect = null;
        noticeTruncated = false;
        if (notice == null) {
            return;
        }
        int maxWidth = Math.max(0, w - PAD * 2);
        String full = notice.getString();
        String text = TextScroll.trimToWidth(font, full, maxWidth);
        noticeTruncated = !text.equals(full);
        UiHostPalette palette = THEME.palette();
        if (noticeLevel == NoticeLevel.INFO) {
            graphics.drawString(font, text, PAD, y + 1, palette.textSecondary(), false);
            noticeRect = new UiRect(PAD, y, font.width(text), NOTICE_H);
            return;
        }
        int fill = noticeLevel == NoticeLevel.DANGER ? palette.danger() : palette.warning();
        UiRect chip = new UiRect(PAD, y, Math.min(maxWidth, font.width(text) + NOTICE_CHIP_PAD * 2), NOTICE_H);
        THEME.drawSurface(graphics, chip, fill, palette.controlBevelDark());
        graphics.drawString(font, text, chip.x() + NOTICE_CHIP_PAD, y + 1, palette.textPrimary(), false);
        noticeRect = chip;
    }

    private boolean saving() {
        return workspace.state() == RuleEditClientState.APPLYING;
    }

    private RuleSaveOverlay saveOverlay() {
        if (saveOverlay == null) {
            listPage.onScopeChanged();
            editPage.endInteractions();
            focus.clearFocus();
            saveOverlay = new RuleSaveOverlay(font, THEME, workspace);
        }
        return saveOverlay;
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
        editPage.flushPendingEdits();
        // 编辑页打开规则时按需取回来源视图；失败或超时只影响来源对照
        model.requestSourceView(id);
        editingId = id;
        mode = Mode.EDIT;
        // 页面切换：提示只对切换前的页面成立，连 INFO 一起清除
        setNotice(null, NoticeLevel.INFO);
        // 换规则时重建页面状态（候选与效果选择归零）
        editPage.openNew();
        editPage.rebuildEdit();
        rebuildFocus();
    }

    // 损坏规则显示原始视图与服务端问题，不创建会被表单改写的草稿。
    private void showRuleDetails(RuleSnapshotEntry entry) {
        UiModal modal = UiModal.create(THEME, font);
        modal.title(Component.translatable(UI + "detail.rule", entry.id().toString()));
        // 说明与原始内容同处一个只读代码块，避免弹窗正文与代码区分成两套排版
        Component note = Component.translatable(UI + (RuleSnapshotEntry.STATUS_INVALID.equals(entry.status())
                ? "detail.invalid" : "detail.unavailable"));
        // 核心条目视图仍是 JSON 对象：经草稿入口转为值视图后再美化输出
        String raw = RuleDraft.of(entry.toJson()).root().toPrettyJson();
        modal.contentWidget(new UiCodeView(THEME, font).setSections(List.of(new UiCodeView.Section(note, null, raw))),
                Math.clamp(height - 90, 36, 180));
        modal.cancel(Component.translatable(UI + "button.close"));
        modal.layoutCentered(width, height);
        modals.push(modal);
    }

    // 返回管理页：先结束交互并卸载页面，再刷新列表
    private void backToList() {
        if (saving()) {
            return;
        }
        editPage.unmount();
        mode = Mode.LIST;
        editingId = null;
        // 页面切换：提示只对切换前的页面成立
        setNotice(null, NoticeLevel.INFO);
        listPage.refreshList();
        rebuildFocus();
    }

    // 调试页入口条件：debug 模式开启、停在管理页、未在保存且没有弹层或类型选择器
    private boolean canEnterDebug() {
        return Services.PLATFORM.isDevelopmentEnvironment() && mode == Mode.LIST && !saving() && modals.isEmpty() && activeTypePicker() == null;
    }

    // 进入调试页：提示只对切换前的页面成立，与其它模式切换保持一致
    private void enterDebug() {
        mode = Mode.DEBUG;
        setNotice(null, NoticeLevel.INFO);
        debugPage.updateButtons();
        rebuildFocus();
    }

    // 草稿改动清单（管理页底排与编辑页底排共用）
    private void showChanges() {
        RuleChangesView changes = new RuleChangesView(font, model, this::labelText);
        modals.push(UiModal.create(THEME, font).title(Component.translatable(UI + "button.changes"))
                .contentWidget(changes, changes.preferredHeight(Math.clamp(height - CHANGES_MODAL_SCREEN_MARGIN,
                        RuleRecipeView.CONTENT_HEIGHT, CHANGES_MODAL_MAX_HEIGHT)))
                .preferredWidth(CHANGES_MODAL_WIDTH).cancel(Component.translatable(UI + "button.cancel"))
                .layoutCentered(width, height));
    }

    private void onDraftChanged() {
        listNeedsRefresh = true;
    }

    // ---- 焦点 / 事件 / 生命周期 ----

    private void rebuildFocus() {
        focus.clear();
        focus.beginUpdate();
        switch (mode) {
            case LIST -> listPage.addFocusTargets(focus);
            case EDIT -> editPage.addFocusTargets(focus);
            case DEBUG -> debugPage.addFocusTargets(focus);
        }
        focus.endUpdate();
        if (focus.focused() == null) {
            focus.focusFirst();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (saving()) {
            saveOverlay().mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (modals.isVisible()) {
            return modals.mouseClicked(mouseX, mouseY, button);
        }
        UiTypePicker picker = activeTypePicker();
        if (picker != null) return picker.mouseClicked(mouseX, mouseY, button);
        if (button == 0 && mode == Mode.LIST && listPage.openTagDetails(mouseX, mouseY)) {
            return true;
        }
        if (button == 0) {
            focusClicked(mouseX, mouseY);
        }
        if (mode == Mode.LIST) {
            return listPage.mouseClicked(mouseX, mouseY, button);
        }
        if (mode == Mode.DEBUG) {
            return debugPage.mouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
        }
        if (editPage.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (saving()) {
            saveOverlay().mouseReleased(mouseX, mouseY, button);
            return true;
        }
        if (modals.isVisible()) {
            return modals.mouseReleased(mouseX, mouseY, button);
        }
        UiTypePicker picker = activeTypePicker();
        if (picker != null) return picker.mouseReleased(mouseX, mouseY, button);
        boolean consumed = mode == Mode.DEBUG
                ? debugPage.mouseReleased(mouseX, mouseY, button)
                : editPage.mouseReleased(mouseX, mouseY, button);
        consumed |= listPage.mouseReleased(mouseX, mouseY, button);
        consumed |= editPage.footerMouseReleased(mouseX, mouseY, button);
        return consumed || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (saving()) {
            return true;
        }
        if (modals.isVisible()) {
            return modals.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        UiTypePicker picker = activeTypePicker();
        if (picker != null) return picker.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        if (mode == Mode.DEBUG) {
            return debugPage.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                    || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
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
        if (saving()) {
            return true;
        }
        if (modals.isVisible()) {
            return modals.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        UiTypePicker picker = activeTypePicker();
        if (picker != null) return picker.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        // 调试页入口：仅 debug 模式开启、当前在管理页且没有弹层 / 类型选择器时响应 Ctrl+F8。
        // F8 属于功能键，切换后仍返回 false 放行，避免阻断原版对该键的处理。
        if (keyCode == GLFW.GLFW_KEY_F8 && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && canEnterDebug()) {
            enterDebug();
            return false;
        }
        // F1–F12 属于原版全局热键（F2 截图、F3 调试、F11 全屏）。原版 KeyboardHandler 在
        // screen.keyPressed 返回 true 时会直接 return、不再把该键喂给 KeyMapping，因此本模组的
        // 任何界面都不得消费功能键；这里在最外层统一放行，弹层与页面都覆盖到。
        if (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F12) {
            return false;
        }
        if (saving()) {
            saveOverlay().keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (modals.isVisible()) {
            return modals.keyPressed(keyCode, scanCode, modifiers);
        }
        UiTypePicker picker = activeTypePicker();
        if (picker != null) return picker.keyPressed(keyCode, scanCode, modifiers);
        if (UiHistoryDispatcher.dispatch(keyCode, scanCode, modifiers, (key, scan, mods) -> {
            if (mode == Mode.LIST && listPage.searchKeyPressed(key, scan, mods)) return true;
            if (mode == Mode.EDIT && editPage.keyPressed(key, scan, mods)) return true;
            if (mode == Mode.DEBUG && debugPage.keyPressed(key, scan, mods)) return true;
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
            if (mode == Mode.EDIT || mode == Mode.DEBUG) {
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
        } else if (mode == Mode.EDIT) {
            if (editPage.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        } else if (debugPage.keyPressed(keyCode, scanCode, modifiers)) {
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
            if (target instanceof UiListView<?> list && !list.acceptsFocusAt(mouseX, mouseY)) {
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
        if (saving()) {
            return true;
        }
        UiTypePicker picker = activeTypePicker();
        if (!modals.isVisible() && picker != null) return picker.keyReleased(keyCode, scanCode, modifiers);
        if (mode == Mode.EDIT && editPage.keyReleased(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (saving()) {
            return true;
        }
        if (modals.isVisible()) {
            return modals.charTyped(codePoint, modifiers);
        }
        UiTypePicker picker = activeTypePicker();
        if (picker != null) return picker.charTyped(codePoint, modifiers);
        if (mode == Mode.LIST && listPage.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (mode == Mode.EDIT && editPage.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (mode == Mode.DEBUG && debugPage.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private @Nullable UiTypePicker activeTypePicker() {
        return mode == Mode.EDIT ? editPage.activeTypePicker() : null;
    }

    @Override
    public void tick() {
        modals.tick();
        // 模态打开：常驻提示描述的是被弹窗接管的页面状态，先清除避免两处同时给结论
        boolean modalsEmpty = modals.isEmpty();
        if (lastModalsEmpty && !modalsEmpty) {
            clearStickyNotice();
        }
        lastModalsEmpty = modalsEmpty;
        // F-A：快照是打开屏幕之后才到的（打开时只发出了请求），每帧消费一次；
        // refresh() 内部按快照实例去重，重复调用无副作用
        long applyStarted = DevelopmentDiagnostics.start();
        boolean applied = model.refresh();
        if (applied) {
            logSnapshotApplied(DevelopmentDiagnostics.micros(applyStarted));
            listPage.refreshList();
            editPage.rebuildEdit();
            rebuildFocus();
            // 快照修订变化会清空来源视图缓存：仍在编辑的规则按需重新取回，失败只影响来源对照
            if (mode == Mode.EDIT && editingId != null) {
                model.requestSourceView(editingId);
            }
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
        // 快照解析失败时首载会一直停在「加载中」：用常驻警告让玩家知道当前内容不可用
        if (workspace.snapshotFailure() == null) {
            snapshotFailureNotified = false;
        } else if (!snapshotFailureNotified) {
            snapshotFailureNotified = true;
            setNotice(Component.translatable(UI + "notice.snapshot_failed"), NoticeLevel.DANGER);
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
        // INFO 是操作回执：达到存活时长后自动清除，WARNING / DANGER 由替换或切页清除
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
        listPage.updateApplyButton();
        editPage.updateHistoryButtons();
        debugPage.updateButtons();
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
        // 保存期间的返回与关闭由保存模态统一拦截，其它冻结状态仍允许退出。
        listPage.setButtonsEnabled(editable);
        debugPage.setButtonsEnabled(editable);
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

    // 用户主动关闭：有未保存改动时提供「暂存到本地」「放弃」「取消」三个选择
    private void closeByUser() {
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
        modal.layoutCentered(this.width, this.height);
        modals.push(modal);
    }

    // 关闭编辑界面：先冲刷草稿再结束工作区会话——会话结束会立刻摘屏并触发 removed，
    // 之后再改页面就来不及写盘了
    private void closeEditor() {
        flushDrafts();
        workspace.close("screen_closed");
        this.onClose();
    }

    // 外部切屏或断线也会调用 removed：这条路径不走 onClose，未应用的修改必须在这里写盘。
    @Override
    public void removed() {
        flushDrafts();
        EntityPreviewIcons.clear();
        RulePreviewIcons.clear();
        BlockPreviewIcons.clear();
        super.removed();
    }

    // 只关屏，不动工作区会话（工作区由服务端租约与协议驱动）
    @Override
    public void onClose() {
        if (saving()) {
            return;
        }
        EntityPreviewIcons.clear();
        BlockPreviewIcons.clear();
        flushDrafts();
        super.onClose();
    }

    // 关屏前把未应用的表单输入并入草稿并立即写盘（断线 / 关游戏 / 关界面都能恢复）。
    // 保存中由服务端回执收尾，这里不碰页面状态；重复调用只生效一次。
    private void flushDrafts() {
        if (released || saving()) {
            return;
        }
        released = true;
        editPage.applyAllForms();
        editPage.close();
        model.persistNow();
    }

    // ---- 小工具 ----

    /**
     * 设置底部状态提示。
     *
     * @param message 提示文本；null 表示清除提示
     * @param level   提示级别，决定绘制形态与存活时长
     */
    public void setNotice(@Nullable Component message, NoticeLevel level) {
        this.notice = message;
        this.noticeLevel = level;
        // INFO 是操作回执，短暂显示后自动消失；WARNING / DANGER 需要玩家处理，常驻
        this.noticeExpiresAt = message != null && level == NoticeLevel.INFO ? Util.getMillis() + NOTICE_INFO_TTL_MS : 0L;
        this.noticeTruncated = false;
        this.noticeRect = null;
    }

    /**
     * 把提示级别折算为编辑页宿主桥接层（{@code RuleEditorPageSet.Host#notice(Component, int)}）的色值。
     *
     * <p>桥接层只接受颜色，本方法让页面调用点仍只表达级别，取色集中在屏幕层。</p>
     *
     * @param level 提示级别
     * @return 与该级别对应的调色板色值
     */
    public static int noticeColor(NoticeLevel level) {
        UiHostPalette palette = THEME.palette();
        return switch (level) {
            case WARNING -> palette.warning();
            case DANGER -> palette.danger();
            case INFO -> palette.textSecondary();
        };
    }

    // 只服务既有以颜色表达紧急度的桥接调用点（编辑页 / 列表页经 Host 上报），按调色板语义折算为提示级别
    private void setNotice(@Nullable Component message, int color) {
        UiHostPalette palette = THEME.palette();
        NoticeLevel level = color == palette.danger() ? NoticeLevel.DANGER
                : color == palette.warning() ? NoticeLevel.WARNING : NoticeLevel.INFO;
        setNotice(message, level);
    }

    // 只清除常驻提示：INFO 自身会超时；模态打开与页签切换各自触发一次清除
    private void clearStickyNotice() {
        if (notice != null && noticeLevel != NoticeLevel.INFO) {
            setNotice(null, NoticeLevel.INFO);
        }
    }

    // 条目显示名（空名或与 id 相同时回退到本地化占位）；与列表共用同一份展示快照
    private String labelText(String id) {
        return display.snapshot(id).title();
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
