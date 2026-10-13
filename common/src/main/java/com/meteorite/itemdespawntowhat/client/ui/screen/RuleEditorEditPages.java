package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.DraftListInputs;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorWorkspaceView;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleNaming;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiAction;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiControlRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFlowLayout;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiScrollView;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ConditionSupport;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.LeafPanel;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.SuggestionProvider;
import com.meteorite.itemdespawntowhat.client.ui.shared.FieldCatalogTypes;
import com.meteorite.itemdespawntowhat.client.ui.shared.RuleDisplayLabels;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModalStack;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 规则编辑页签的组合视图（四页契约见 docs/dev/internals/editor-pages-contract.md）。
 * <p>四页共用同一个 {@link EditSession} 草稿：所有写入都通过 {@link EditSession#apply(String, Runnable)}
 * 提交为一条撤销记录；只读展示（自动标题、路径提示、只读高级区、摘要）不写数据。
 * <p>顶层 effects 展示为单一隐式候选：打开、改别名、改效果都不会自动生成 outcomes；
 * 只有用户新增第二个候选或明确编辑候选专属策略时，才用 {@link RuleDraft#convertToOutcomes(String)}
 * 做一次原子转换（保留原效果对象、一次撤销可整体还原）。
 * <p>消耗类效果（consume_source / consume_catalyst）映射到「输入」页，只更新原效果路径，
 * 不会同时生成规则级固定成本字段；这两类效果不出现在效果选择器里。
 */
public final class RuleEditorEditPages {

    // 宿主默认主题：屏幕从主题入口取样式后注入本类装配的控件
    static final UiHostTheme THEME = UiHostThemes.defaultTheme();

    // 页签
    public enum Page {

        // 基本信息
        INFO("info"),
        // 输入
        INPUT("input"),
        // 触发与条件
        TRIGGER("trigger"),
        // 结果
        RESULTS("results");

        private final String value;

        Page(String value) {
            this.value = value;
        }

        // 页签标识（分段控件取值）
        public String value() {
            return value;
        }

        // 由标识解析，未知回落基本信息
        public static Page of(@Nullable String value) {
            for (Page page : values()) {
                if (page.value.equals(value)) {
                    return page;
                }
            }
            return INFO;
        }
    }

    /**
     * 宿主能力：页面只通过本接口访问屏幕、焦点、弹窗栈、建议与条件树支持，不反向持有屏幕类型。
     */
    public interface Host {

        // 字体
        Font font();

        // 当前编辑会话（未打开规则时为 null）
        @Nullable EditSession session();

        // 焦点管理器
        UiFocusManager focus();

        // 弹窗栈
        UiModalStack modals();

        // 草稿已变更（屏幕据此刷新列表）
        void onDraftChanged();

        // 控件重建后同步宿主的焦点目标。
        void onControlsChanged();

        // 提示条
        void notice(Component message, int color);

        // 输入建议
        SuggestionProvider suggestions();

        // 条件树支持
        ConditionSupport conditionSupport();

        // 编辑工作区只读视图（目录面板与只读状态查询）
        EditorWorkspaceView workspace();

        // 冻结（会话失效/只读）时拒绝写入
        boolean rejectWhenFrozen();

        // 屏幕宽高
        int screenWidth();

        int screenHeight();
    }

    // 四个页签的实现：容器只保留共享状态与调度，页面各自持有自己的控件引用
    final RuleEditorInfoPage ruleEditorInfoPage;
    final RuleEditorInputPage ruleEditorInputPage;
    final RuleEditorTriggerPage ruleEditorTriggerPage;
    final RuleEditorResultsPage ruleEditorResultsPage;

    final Host host;
    final Font font;
    final List<UiWidget> liveWidgets = new ArrayList<>();
    final List<FormView> liveForms = new ArrayList<>();
    final List<UiButton> buttons = new ArrayList<>();
    final List<UiButton> candidateButtons = new ArrayList<>();
    final List<UiButton> effectButtons = new ArrayList<>();
    // 已打开的叶子面板与所属模态：模态关闭后按栈内存在性清理引用
    final List<LeafEntry> leafPanels = new ArrayList<>();

    // 叶子面板条目：面板随模态一起关闭，模态销毁后引用必须移除
    record LeafEntry(UiModal modal, LeafPanel panel) {
    }

    // 名称来源：注册表标签优先，缺失回落 id 文本（由 RuleNaming 兜底）
    final RuleNaming.NameSource nameSource = RuleDisplayLabels::label;

    final UiScrollView pageScroll = new UiScrollView();
    private final EnumMap<Page, Integer> scrollOffsets = new EnumMap<>(Page.class);
    private Page page = Page.INFO;
    private boolean enabled = true;
    private boolean pendingRebuild;
    boolean rebuilding;
    @Nullable UiRect lastArea;
    final List<PageCard> cards = new ArrayList<>();

    /*** 页面分区的绘制数据，与控件共用滚动坐标。 */
    record PageCard(UiRect bounds, @Nullable Component title) { }

    // 输入事件分发与拖动状态
    private final RuleEditorPageInput input;

    public RuleEditorEditPages(Host host) {
        this.host = host;
        this.font = host.font();
        this.ruleEditorInfoPage = new RuleEditorInfoPage(this);
        this.ruleEditorInputPage = new RuleEditorInputPage(this);
        this.ruleEditorTriggerPage = new RuleEditorTriggerPage(this);
        this.ruleEditorResultsPage = new RuleEditorResultsPage(this);
        this.input = new RuleEditorPageInput(this);
    }

    // ---- 页签与重建 ----

    public Page page() {
        return page;
    }

    // 切页：先结束进行中的预览，再重建新页
    public void setPage(@Nullable Page next) {
        Page target = next == null ? Page.INFO : next;
        if (target == page) {
            return;
        }
        scrollOffsets.put(page, pageScroll.offset());
        endInteractions();
        page = target;
        ruleEditorResultsPage.effectIndex = 0;
        rebuild();
        pageScroll.setOffset(scrollOffsets.getOrDefault(page, 0));
        if (lastArea != null) {
            layout(lastArea);
        }
    }

    // 重建当前页的全部控件（草稿结构或选择变化后调用）
    public void rebuild() {
        reset();
        EditSession session = host.session();
        if (session == null) {
            return;
        }
        switch (page) {
            case INFO -> ruleEditorInfoPage.buildInfo(session);
            case INPUT -> ruleEditorInputPage.buildInput(session);
            case TRIGGER -> ruleEditorTriggerPage.buildTrigger(session);
            case RESULTS -> ruleEditorResultsPage.buildResults(session);
        }
        applyEnabled();
        if (lastArea != null) {
            layout(lastArea);
        }
        host.onControlsChanged();
    }

    // 清空当前页控件
    private void reset() {
        liveWidgets.clear();
        liveForms.clear();
        buttons.clear();
        candidateButtons.clear();
        effectButtons.clear();
        // 页面各自的控件引用由页面清理，容器只维护共享状态
        ruleEditorInfoPage.reset();
        ruleEditorInputPage.reset();
        ruleEditorTriggerPage.reset();
        ruleEditorResultsPage.reset();
        input.resetDrag();
    }

    // 表单工厂
    FormView newForm(EditSession session, String basePath, TypeEditorDescriptor descriptor) {
        FormView form = new FormView(font, session, basePath);
        form.useCatalogSelections();
        form.setConditionSupport(ruleEditorTriggerPage.pageConditionSupport(form));
        form.setSuggestionProvider(host.suggestions());
        form.setOnChanged(host::onDraftChanged);
        if (page == Page.INPUT) form.setOnChanged(() -> {
            form.applyToDraft();
            if (form == ruleEditorInputPage.sourceForm) {
                ruleEditorInputPage.updateBlacklistVisibility();
                ruleEditorInputPage.refreshQuantityReferences();
            }
            if (form == ruleEditorInputPage.catalystForm) {
                for (RuleEditorInputPage.PresenceRow row : ruleEditorInputPage.presenceRows) {
                    if (row.leaf.catalyst()) row.form.reload();
                }
            }
            host.onDraftChanged();
        });
        // 输入被长度上限拒绝时立刻显示一条可读提示（字段名：问题）
        form.setOnRejectedNotice(message -> host.notice(message, THEME.palette().danger()));
        form.setSelectionCatalogOpener((field, path, tags, selected, onPicked) -> {
            if (page == Page.INPUT && RuleFields.SOURCE.equals(basePath) && RuleFields.SOURCE_EXCLUDE.equals(field.name())) {
                if (!host.rejectWhenFrozen()) host.modals().push(SourceBlacklistPicker.modal(font, host.workspace(), session.draft().root(),
                        host.screenWidth(), host.screenHeight(), onPicked));
                return;
            }
            RuleCatalogType type = tags ? RuleCatalogType.TAG : FieldCatalogTypes.catalogTypeOf(field);
            if (type == null || host.rejectWhenFrozen()) return;
            boolean multi = field.type() == com.meteorite.itemdespawntowhat.client.edit.EditorFieldType.TAG_LIST
                    || field.type() == com.meteorite.itemdespawntowhat.client.edit.EditorFieldType.RL_LIST;
            host.modals().push(RuleEditorPanelFactory.fieldCatalogModal(font, host.workspace(), type, multi,
                    Component.translatable(field.labelKey()), host.screenWidth(), host.screenHeight(), onPicked,
                    field, session.draft(), path, selected));
        });
        form.setDescriptor(descriptor);
        form.reload();
        form.useCardSpacing();
        ruleEditorInputPage.configureQuantityFields(form, session, basePath, descriptor);
        return form;
    }

    // 基本页优先级与其它单行控件使用同一紧凑高度。
    static final int PRIORITY_ROW_HEIGHT = 18;

    // ---- 布局 ----

    // 外层滚动与控件命中使用同一份绝对坐标，不把表单撑进底栏。
    public void layout(UiRect area) {
        lastArea = area;
        pageScroll.setViewport(area.x(), area.y(), area.width(), area.height());
        pageScroll.setStep(24);
        int used = layoutPage(area) - area.y();
        int width = area.width();
        if (used > area.height()) {
            width = Math.max(0, width - UiScrollView.SCROLLBAR_WIDTH - 2);
            used = layoutPage(new UiRect(area.x(), area.y(), width, area.height())) - area.y();
        }
        pageScroll.setContentHeight(used);
        pageScroll.setScrollbarVisible(used > area.height());
        layoutPage(new UiRect(area.x(), area.y() - pageScroll.offset(), width, area.height()));
    }

    private int layoutPage(UiRect area) {
        cards.clear();
        return switch (page) {
            case INFO -> ruleEditorInfoPage.layoutInfo(area);
            case INPUT -> ruleEditorInputPage.layoutInput(area);
            case TRIGGER -> ruleEditorTriggerPage.layoutTrigger(area);
            case RESULTS -> ruleEditorResultsPage.layoutResults(area);
        };
    }

    int layoutForm(@Nullable FormView form, int x, int y, int width) {
        if (form == null) {
            return y;
        }
        int height = form.preferredHeight(width);
        form.layout(x, y, width, height);
        return y + height + RuleEditorPageSupport.PAD;
    }

    // 内容高度决定卡片高度，额外留白仅用于宽屏结果列表的对齐。
    int finishCard(int x, int top, int width, int contentBottom, String titleKey) {
        int bottom = contentBottom + RuleEditorPageSupport.CARD_PAD;
        cards.add(new PageCard(new UiRect(x, top, width, Math.max(RuleEditorPageSupport.CARD_HEADER_H + RuleEditorPageSupport.CARD_PAD * 2, bottom - top)),
                Component.translatable(RuleEditorPageSupport.UI + titleKey)));
        return bottom + RuleEditorPageSupport.CARD_GAP;
    }

    // 按钮组横向排布（超宽换行），返回占用高度
    int layoutButtonGroup(List<UiButton> group, int x, int y, int width) {
        if (group.isEmpty()) {
            return 0;
        }
        int limit = Math.max(0, width);
        int[] widths = new int[group.size()];
        for (int index = 0; index < group.size(); index++) {
            widths[index] = Math.clamp(group.get(index).preferredWidth(RuleEditorPageSupport.PAD), Math.min(24, limit), limit);
        }
        UiFlowLayout.Wrap flow = UiFlowLayout.wrap(widths, limit, RuleEditorPageSupport.BUTTON_GAP, RuleEditorPageSupport.BUTTON_H);
        for (int index = 0; index < group.size(); index++) {
            UiRect offset = flow.bounds(index);
            group.get(index).setBounds(x + offset.x(), y + offset.y(), offset.width(), offset.height());
        }
        return flow.height();
    }

    // ---- 渲染 ----

    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        if (lastArea == null || lastArea.height() == 0) {
            return;
        }
        var pointer = com.meteorite.itemdespawntowhat.client.ui.kit.UiPointer.gated(!pageScroll.isDragging(), mouseX, mouseY);
        mouseX = pointer.x();
        mouseY = pointer.y();
        graphics.enableScissor(lastArea.x(), lastArea.y(), lastArea.right(), lastArea.bottom());
        try {
            for (PageCard card : cards) {
                THEME.drawCard(graphics, card.bounds());
                UiRect rect = card.bounds();
                if (card.title() == null) continue;
                graphics.fill(rect.x() + RuleEditorPageSupport.CARD_PAD, rect.y() + RuleEditorPageSupport.CARD_PAD, rect.x() + RuleEditorPageSupport.CARD_PAD + 2,
                        rect.y() + RuleEditorPageSupport.CARD_PAD + font.lineHeight, THEME.palette().accent());
                var titleLines = font.split(card.title().copy().withStyle(net.minecraft.ChatFormatting.BOLD),
                        Math.max(1, rect.width() - RuleEditorPageSupport.CARD_PAD * 2 - 6));
                if (!titleLines.isEmpty()) graphics.drawString(font, titleLines.getFirst(),
                        rect.x() + RuleEditorPageSupport.CARD_PAD + 6, rect.y() + RuleEditorPageSupport.CARD_PAD, THEME.palette().textPrimary(), false);
                THEME.drawDivider(graphics, rect.x() + RuleEditorPageSupport.CARD_PAD, rect.y() + RuleEditorPageSupport.CARD_PAD + RuleEditorPageSupport.CARD_HEADER_H - 5,
                        Math.max(0, rect.width() - RuleEditorPageSupport.CARD_PAD * 2));
            }
            switch (page) {
                case INFO -> ruleEditorInfoPage.renderInfo(graphics);
                case INPUT -> ruleEditorInputPage.renderInput(graphics);
                case TRIGGER -> ruleEditorTriggerPage.renderTrigger(graphics);
                case RESULTS -> ruleEditorResultsPage.renderResults(graphics);
            }
            for (UiWidget widget : liveWidgets) {
                if (widget.isVisible()) {
                    widget.render(graphics, renderFont, mouseX, mouseY);
                }
            }
            input.renderDragPreview(graphics);
        } finally {
            graphics.disableScissor();
        }
        pageScroll.renderScrollbar(graphics, THEME.controlStyle(UiControlRole.SECONDARY));
    }

    void drawText(GuiGraphics graphics, @Nullable UiRect rect, Component text, int color) {
        if (rect == null) {
            return;
        }
        String trimmed = TextScroll.trimToWidth(font, text.getString(), Math.max(1, rect.width()));
        graphics.drawString(font, trimmed, rect.x(), rect.y(), color, false);
    }

    // ---- 状态与提交 ----

    // 提交所有可见表单的待写字段；返回是否有改动
    public boolean applyToDraft() {
        if (!enabled) {
            return false;
        }
        // 存在条件的类型改写必须先落盘，才能与关联消耗配置的重指向合并为一次可撤销操作
        boolean changed = ruleEditorInputPage.flushPresenceTypes();
        for (FormView form : liveForms) {
            if (form.isVisible() && form.commitPendingInputs()) {
                changed = true;
            }
        }
        return changed;
    }

    // 收集所有可见表单的校验问题，再补页面级约束（触发来源、候选与效果上限、组合模式）
    public List<FormIssue> issues() {
        List<FormIssue> issues = new ArrayList<>();
        for (FormView form : liveForms) {
            if (form.isVisible()) {
                issues.addAll(form.issues());
            }
        }
        RuleValue view = currentView();
        if (view != null) {
            RuleEditorPageSupport.addPageIssues(view, issues);
        }
        return issues;
    }

    // 结束进行中的数值/滑杆交互：提交有效预览、回退无效预览
    // （FormView.endInteractions 是包级方法，宿主只能走 onFocusScopeChanged 这个公开入口）
    public void endInteractions() {
        pageScroll.mouseReleased();
        for (FormView form : liveForms) {
            if (form.isVisible()) {
                form.onFocusScopeChanged();
            }
        }
        for (LeafEntry entry : leafPanels) {
            entry.panel().endInteractions();
        }
        leafPanels.clear();
    }

    // 宿主关闭屏幕：让表单释放全部捕获与输入状态
    public void onHostClosed() {
        for (FormView form : liveForms) {
            form.onHostClosed();
        }
        for (LeafEntry entry : leafPanels) {
            entry.panel().onHostClosed();
        }
        leafPanels.clear();
    }

    // 宿主离开编辑页（返回管理页）：卸载表单的输入状态
    public void unmount() {
        for (FormView form : liveForms) {
            form.unmount();
        }
        for (LeafEntry entry : leafPanels) {
            entry.panel().unmount();
        }
        leafPanels.clear();
    }

    // 键盘抬起：把滑杆方向键的按住过程合并为一次提交（屏幕在 EDIT 模式下转发）
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        boolean handled = false;
        for (FormView form : liveForms) {
            if (form.isVisible() && form.keyReleased(keyCode, scanCode, modifiers)) {
                handled = true;
            }
        }
        if (handled) {
            return true;
        }
        UiFocusTarget focused = host.focus().focused();
        if (focused instanceof UiWidget widget) {
            return widget.keyReleased(keyCode, scanCode, modifiers);
        }
        return false;
    }

    // 聚焦目标（按视觉顺序）
    public List<UiFocusTarget> focusTargets() {
        List<UiFocusTarget> targets = new ArrayList<>();
        for (UiWidget widget : liveWidgets) {
            if (!widget.isVisible()) continue;
            if (widget instanceof FormView form) {
                targets.addAll(form.focusTargets());
            } else if (widget instanceof UiFocusTarget target && target.canFocus()) {
                targets.add(target);
            }
        }
        return targets;
    }

    // 冻结/只读时禁用交互
    public void setEnabled(boolean next) {
        enabled = next;
        applyEnabled();
    }

    void applyEnabled() {
        RuleValue view = currentView();
        if (ruleEditorInputPage.sourceCostButton != null) {
            ruleEditorInputPage.sourceCostButton.setEnabled(enabled && RuleCostBinding.consumeSource(view) == null);
        }
        if (ruleEditorInputPage.catalystToggle != null) {
            EditSession session = host.session();
            boolean hasCost = view != null && (view.has(RuleFields.CATALYST_COST)
                    || RuleCostBinding.consumeCatalyst(view) != null);
            ruleEditorInputPage.catalystToggle.setEnabled(enabled && (hasCost || session != null
                    && RuleEditorPageSupport.selectedCatalystLeaf(session.draft()) != null));
        }
        if (ruleEditorInputPage.catalystPresenceToggle != null) {
            ruleEditorInputPage.catalystPresenceToggle.setEnabled(enabled);
        }
        if (ruleEditorInputPage.fluidPresenceToggle != null) {
            ruleEditorInputPage.fluidPresenceToggle.setEnabled(enabled);
        }
        ruleEditorTriggerPage.triggerBoxes.forEach(box -> box.setEnabled(enabled));
        if (ruleEditorResultsPage.variantControl != null) ruleEditorResultsPage.variantControl.setEnabled(enabled);
        if (ruleEditorResultsPage.combinationControl != null) {
            ruleEditorResultsPage.combinationControl.setEnabled(enabled && ResultStructure.hasOutcomes(view));
        }
        if (ruleEditorResultsPage.safeSpawnBox != null) {
            ruleEditorResultsPage.safeSpawnBox.setEnabled(enabled);
        }
        if (ruleEditorResultsPage.fillOriginBox != null) {
            ruleEditorResultsPage.fillOriginBox.setEnabled(enabled);
        }
        for (FormView form : liveForms) {
            form.setEnabled(enabled);
        }
        for (UiButton button : buttons) {
            button.setEnabled(enabled);
        }
        for (UiButton button : candidateButtons) {
            button.setEnabled(enabled);
        }
        for (UiButton button : effectButtons) {
            button.setEnabled(enabled);
        }
    }

    public void tick() {
        pruneLeafPanels();
        ruleEditorTriggerPage.updateLifespanWindow();
        for (FormView form : liveForms) {
            form.tick();
        }
        flushRebuild();
    }

    // 模态关闭后移除对应叶子面板引用，避免反复开关累积引用
    private void pruneLeafPanels() {
        if (leafPanels.isEmpty()) {
            return;
        }
        List<UiModal> open = host.modals().modals();
        leafPanels.removeIf(entry -> {
            if (open.contains(entry.modal())) {
                return false;
            }
            entry.panel().unmount();
            return true;
        });
    }

    public @Nullable Component tooltipAt(double mouseX, double mouseY) {
        if (pageScroll.isDragging()) return null;
        if (!pageScroll.contains(mouseX, mouseY)) {
            return null;
        }
        for (FormView form : liveForms) {
            if (!form.isVisible()) {
                continue;
            }
            Component tooltip = form.tooltipAt(mouseX, mouseY);
            if (tooltip != null) {
                return tooltip;
            }
        }
        return null;
    }

    /**
     * 按草稿路径切到对应页签、选中候选与效果，并返回应当聚焦的字段控件。
     * <p>用于本地校验与服务器问题定位（规则 → 页签 → 候选 → 效果 → 条件 → 字段）。
     */
    public @Nullable UiFocusTarget reveal(@Nullable String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        for (FormView form : liveForms) {
            if (!form.isVisible()) continue;
            UiFocusTarget target = form.revealPath(path);
            if (target != null) {
                ensureVisible(target);
                return target;
            }
        }
        if (path.startsWith(RuleFields.OUTCOMES) || path.startsWith(RuleFields.EFFECTS)) {
            page = Page.RESULTS;
            ruleEditorResultsPage.resultStage = path.contains("[") ? 2 : 1;
            ruleEditorResultsPage.showAdvancedEffects = path.contains("chance") || path.contains("delay_ticks")
                    || path.contains("conditions");
            int candidate = RuleEditorPageSupport.indexIn(path, RuleFields.OUTCOMES);
            if (candidate >= 0) {
                ruleEditorResultsPage.candidateIndex = candidate;
            }
            int effect = RuleEditorPageSupport.indexIn(path, RuleFields.EFFECTS);
            if (effect >= 0) {
                ruleEditorResultsPage.effectIndex = effect;
            }
            rebuild();
            if (ruleEditorResultsPage.effectForm != null) {
                UiFocusTarget target = ruleEditorResultsPage.effectForm.revealPath(path);
                return target != null ? target : ruleEditorResultsPage.advancedEffectForm == null ? null
                        : ruleEditorResultsPage.advancedEffectForm.revealPath(path);
            }
            return null;
        }
        if (path.startsWith(RuleFields.CONDITIONS) || path.startsWith(RuleFields.TERMS)) {
            page = Page.TRIGGER;
            rebuild();
            return ruleEditorTriggerPage.conditionsForm == null ? null : ruleEditorTriggerPage.conditionsForm.revealPath(path);
        }
        if (path.startsWith(RuleFields.TRIGGERS) || path.startsWith(RuleFields.TRIGGER_AFTER_SECONDS)) {
            page = Page.TRIGGER;
            rebuild();
            // 触发来源问题直接聚焦第一个勾选框；延迟秒数问题落到延迟表单
            if (path.startsWith(RuleFields.TRIGGERS) && !ruleEditorTriggerPage.triggerBoxes.isEmpty()) {
                return ruleEditorTriggerPage.triggerBoxes.getFirst();
            }
            return ruleEditorTriggerPage.delayForm == null ? null : ruleEditorTriggerPage.delayForm.revealPath(path);
        }
        if (path.startsWith(RuleFields.COMBINATION)) {
            page = Page.RESULTS;
            rebuild();
            return ruleEditorResultsPage.combinationControl;
        }
        if (path.startsWith(RuleFields.SOURCE) || path.startsWith(RuleFields.CATALYST_COST)) {
            page = Page.INPUT;
            rebuild();
            FormView target = path.startsWith(RuleFields.CATALYST_COST) ? ruleEditorInputPage.catalystForm
                    : path.startsWith(RuleFields.SOURCE_COST) ? ruleEditorInputPage.sourceCostForm : ruleEditorInputPage.sourceForm;
            return target == null ? null : target.revealPath(path);
        }
        page = Page.INFO;
        rebuild();
        return ruleEditorInfoPage.infoForm == null ? null : ruleEditorInfoPage.infoForm.revealPath(path);
    }

    // ---- 输入事件 ----

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return input.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return input.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return input.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return input.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return input.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return input.charTyped(codePoint, modifiers);
    }

    // ---- 内部工具 ----

    // 切换前提交合法字段，非法文本留在会话，重建表单后仍可继续填写。
    public boolean blockNavigation() {
        if (!enabled) return false;
        for (FormView form : liveForms) form.retainPendingInputs();
        endInteractions();
        if (applyToDraft()) host.onDraftChanged();
        return false;
    }

    // Tab 和问题定位都需把焦点滚进外层页面视口。
    public void ensureVisible(UiFocusTarget target) {
        if (lastArea == null || !focusTargets().contains(target)) {
            return;
        }
        UiRect rect = target.bounds();
        pageScroll.ensureVisible(new UiRect(rect.x() - lastArea.x(),
                rect.y() - lastArea.y() + pageScroll.offset(), rect.width(), rect.height()));
        layout(lastArea);
    }

    void requestRebuild() {
        pendingRebuild = true;
    }

    boolean rebuildPending() {
        return pendingRebuild;
    }

    void flushRebuild() {
        if (pendingRebuild) {
            pendingRebuild = false;
            rebuild();
        }
    }

    // 条件叶编辑入口：由触发页打开叶参数编辑器（宿主与条件树共用）
    public void editConditionLeaf(ConditionNode.Leaf leaf) {
        ruleEditorTriggerPage.editConditionLeaf(leaf);
    }

    boolean apply(String opKey, Runnable change) {
        EditSession session = host.session();
        if (session == null || host.rejectWhenFrozen() || blockNavigation()) {
            restoreSwitches();
            return false;
        }
        DraftListInputs inputs = DraftListInputs.capture(session.draft());
        session.apply(opKey, () -> {
            change.run();
            inputs.remap(session);
        });
        host.onDraftChanged();
        requestRebuild();
        return true;
    }

    // 无效文本阻止结构操作时，仅恢复开关显示，不重建输入控件。
    void restoreSwitches() {
        RuleValue view = currentView();
        if (view == null) {
            return;
        }
        if (ruleEditorInputPage.catalystToggle != null) {
            ruleEditorInputPage.catalystToggle.setChecked(view.has(RuleFields.CATALYST_COST)
                    || RuleCostBinding.consumeCatalyst(view) != null);
        }
        if (ruleEditorInputPage.catalystPresenceToggle != null) {
            ruleEditorInputPage.catalystPresenceToggle.setChecked(
                    RuleEditorPageSupport.hasRuleLevelPresence(view, RuleEditorPageSupport.CATALYST_PRESENT_ID));
        }
        if (ruleEditorInputPage.fluidPresenceToggle != null) {
            ruleEditorInputPage.fluidPresenceToggle.setChecked(
                    RuleEditorPageSupport.hasRuleLevelPresence(view, RuleEditorPageSupport.FLUID_PRESENT_ID));
        }
        List<String> triggers = RuleTriggers.effective(view);
        for (int index = 0; index < ruleEditorTriggerPage.triggerBoxes.size(); index++) {
            ruleEditorTriggerPage.triggerBoxes.get(index).setChecked(triggers.contains(RuleTriggers.known().get(index)));
        }
        if (ruleEditorResultsPage.combinationControl != null) {
            ruleEditorResultsPage.combinationControl.setSelected(RuleEditorPageSupport.combinationOf(view));
        }
        RuleValue candidate = ResultStructure.candidateAt(view, ruleEditorResultsPage.candidateIndex);
        if (ruleEditorResultsPage.safeSpawnBox != null) {
            ruleEditorResultsPage.safeSpawnBox.setChecked(RuleEditorPageSupport.boolOf(candidate, RuleFields.SAFE_SPAWN, false));
        }
        if (ruleEditorResultsPage.fillOriginBox != null) {
            ruleEditorResultsPage.fillOriginBox.setChecked(RuleEditorPageSupport.boolOf(candidate, RuleFields.FILL_ORIGIN, true));
        }
    }

    void addCandidateButton(String labelKey, UiAction action) {
        UiButton button = new UiButton(THEME, font, Component.translatable(RuleEditorPageSupport.UI + labelKey), UiButtonVariant.SECONDARY, action);
        candidateButtons.add(button);
        liveWidgets.add(button);
    }

    void addEffectButton(String labelKey, UiAction action) {
        UiButton button = new UiButton(THEME, font, Component.translatable(RuleEditorPageSupport.UI + labelKey), UiButtonVariant.SECONDARY, action);
        effectButtons.add(button);
        liveWidgets.add(button);
    }

    @Nullable RuleValue currentView() {
        EditSession session = host.session();
        return session == null ? null : session.draft().root();
    }
}
