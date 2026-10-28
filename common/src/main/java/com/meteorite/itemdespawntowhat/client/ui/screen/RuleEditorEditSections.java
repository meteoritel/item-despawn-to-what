package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.ResultStructure;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiControlRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiScrollView;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ConditionEditingSupport;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.CombinationMode;
import com.meteorite.itemdespawntowhat.core.model.ConditionLimits;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 规则编辑页签的组合视图。
 *
 * <p>四页共用同一个 {@link EditSession} 草稿，所有写入经 {@link EditSession#apply(String, java.util.function.Consumer)}
 * 记为一条撤销记录；自动标题、路径提示、只读高级区与摘要只读展示，不写数据。</p>
 *
 * <p>顶层 effects 以单一隐式候选展示：打开、改别名、改效果都不生成 outcomes。用户新增第二个候选
 * 或明确编辑候选专属策略时，才用 {@link RuleDraft#convertToOutcomes(String)} 原子转换一次，
 * 保留原效果对象，一次撤销可整体还原。</p>
 *
 * <p>输入页编辑规则源数量、催化剂门槛与实际消耗、共享半径及流体源消耗开关；结果页编辑产出和
 * 世界效果。输入支付统一由结算层执行。</p>
 */
final class RuleEditorEditSections {

    // 宿主默认主题：屏幕从主题入口取样式后注入本类装配的控件
    final UiHostTheme THEME;

    /**
     * 页签
     */
    enum EditSection {

        // 基本信息
        INFO("info"),
        // 输入
        INPUT("input"),
        // 触发与条件
        TRIGGER("trigger"),
        // 结果
        RESULTS("results");

        private final String value;

        EditSection(String value) {
            this.value = value;
        }

        // 页签标识（分段控件取值）
        public String value() {
            return value;
        }

        // 由标识解析，未知回落基本信息
        public static EditSection of(@Nullable String value) {
            for (EditSection page : values()) {
                if (page.value.equals(value)) {
                    return page;
                }
            }
            return INFO;
        }
    }

    // 四个页签的实现：容器只保留共享状态与调度，页面各自持有控件引用
    final RuleEditorInfoSection infoSection;
    final RuleEditorInputSection inputSection;
    final RuleEditorTriggerSection triggerSection;
    final RuleEditorResultsSection resultsSection;

    final EditorShell shell;
    final UiFocusManager focus;
    final Font font;
    EditorSectionContent content;
    final EditorSectionActions actions;

    final UiScrollView pageScroll = new UiScrollView();
    private final EnumMap<EditSection, Integer> scrollOffsets = new EnumMap<>(EditSection.class);
    private EditSection page = EditSection.INFO;
    @Nullable UiRect lastArea;
    // 页面布局缓存：只有页、宽度、行结构与控件高度等真实几何输入变化时才重排，渲染帧与纯滚动帧都不测量
    private boolean layoutDirty = true;
    // 已作用到控件上的滚动偏移：偏移变化时只平移已排好的控件
    private int appliedOffset;
    // 控件在文档坐标（未滚动）下的矩形：滚动帧据此重算屏幕坐标，复合控件不会因子控件同列平移两次
    private final IdentityHashMap<UiWidget, UiRect> documentBounds = new IdentityHashMap<>();

    // 输入事件分发与拖动状态
    private final RuleEditorPageInput input;

    RuleEditorEditSections(EditorShell shell, RuleEditorModel model, Font font, UiFocusManager focus, ConditionEditingSupport conditionEditingSupport, Runnable finishEditing) {
        this.THEME = shell.theme();
        this.shell = shell;
        this.font = font;
        this.focus = focus;
        this.actions = new EditorSectionActions(shell, finishEditing, this::rebuild, this::restoreSwitches);
        EditorConditionForms conditions = new EditorConditionForms(shell, font, conditionEditingSupport);
        EditorFormFactory forms = new EditorFormFactory(shell, font, conditions, this::invalidateLayout);
        this.infoSection = new RuleEditorInfoSection(shell, model, font, forms, actions);
        this.inputSection = new RuleEditorInputSection(shell, font, forms, actions);
        this.triggerSection = new RuleEditorTriggerSection(shell, font, forms, actions);
        this.resultsSection = new RuleEditorResultsSection(shell, font, forms, actions, pageScroll);
        this.content = infoSection.content;
        this.input = new RuleEditorPageInput(this);
    }

    // ---- 页签与重建 ----

    public EditSection page() {
        return page;
    }

    /**
     * 切页：先结束进行中的预览，再重建新页
     */
    public void setPage(@Nullable EditSection next) {
        EditSection target = next == null ? EditSection.INFO : next;
        if (target == page) {
            return;
        }
        scrollOffsets.put(page, pageScroll.offset());
        endInteractions();
        page = target;
        // 切页后旧提示失效：消息传 null 清除，色值沿用 INFO 级别的次要文本色
        shell.setNotice(null, EditorShell.NoticeLevel.INFO);
        resultsSection.effectIndex = 0;
        rebuild();
        pageScroll.setOffset(scrollOffsets.getOrDefault(page, 0));
        if (lastArea != null) {
            // 新页在 rebuild 时已排好，切页只改变滚动位置：平移即可，不再整页重排
            applyScroll(pageScroll.offset());
        }
    }

    /**
     * 重建当前页的全部控件（草稿结构或选择变化后调用）
     */
    public void rebuild() {
        rebuild(null);
    }

    // 定位结果字段时在装配内展开所属分区，布局和焦点树随同一次重建完成
    private void rebuild(@Nullable String resultRevealPath) {
        reset();
        content = switch (page) {
            case INFO -> infoSection.content;
            case INPUT -> inputSection.content;
            case TRIGGER -> triggerSection.content;
            case RESULTS -> resultsSection.content;
        };
        EditSession session = shell.editingSession();
        if (session == null) {
            return;
        }
        switch (page) {
            case INFO -> infoSection.buildInfo(session);
            case INPUT -> inputSection.buildInput(session);
            case TRIGGER -> triggerSection.buildTrigger(session);
            case RESULTS -> resultsSection.buildResults(session, resultRevealPath);
        }
        applyEnabled();
        if (lastArea != null) {
            layout(lastArea);
        }
        shell.rebuildFocus();
    }

    private void reset() {
        content.clear();
        documentBounds.clear();
        infoSection.reset();
        inputSection.reset();
        triggerSection.reset();
        resultsSection.reset();
        input.resetDrag();
    }

    // 基本页优先级与其它单行控件使用同一紧凑高度。
    static final int PRIORITY_ROW_HEIGHT = 18;

    // ---- 布局 ----

    /**
     * 页面布局失效：下一次渲染前重排一次；只在真实几何输入变化时调用。
     */
    void invalidateLayout() {
        layoutDirty = true;
    }

    /**
     * 布局按需入口：几何输入变化时才全量重排，纯滚动帧只平移已排好的控件。
     *
     * <p>布局与测量不再跟帧绑定：静止帧只复用缓存，滚动帧不测量文本；命中、提示与拖拽预览
     * 继续读控件自身的屏幕 bounds，与平移后的控件保持同一坐标系。</p>
     *
     * @param area 内容视口（屏幕坐标）
     */
    public void ensureLayout(UiRect area) {
        if (area == null || area.height() <= 0) {
            lastArea = area;
            return;
        }
        if (layoutDirty || lastArea == null || !lastArea.equals(area) || formGeometryChanged()) {
            layout(area);
            return;
        }
        int offset = pageScroll.offset();
        if (offset != appliedOffset) {
            applyScroll(offset);
        }
    }

    // 任一可见表单的测量缓存失效即视为页面几何需要重排
    private boolean formGeometryChanged() {
        for (FormView form : content.forms) {
            if (form.isVisible() && form.needsMeasure()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 全量布局：页、宽度、行结构与控件高度变化时执行；滚动偏移只决定控件最终落位。
     */
    public void layout(UiRect area) {
        lastArea = area;
        pageScroll.setViewport(area.x(), area.y(), area.width(), area.height());
        pageScroll.setStep(24);
        // 先按文档坐标（滚动偏移 0）排布，排完后统一平移到当前滚动位置
        appliedOffset = 0;
        int used = layoutPage(area) - area.y();
        int width = area.width();
        if (used > area.height()) {
            width = Math.max(0, width - UiScrollView.SCROLLBAR_WIDTH - 2);
            used = layoutPage(new UiRect(area.x(), area.y(), width, area.height())) - area.y();
        }
        captureDocumentBounds();
        layoutDirty = false;
        pageScroll.setContentHeight(used);
        pageScroll.setScrollbarVisible(used > area.height());
        applyScroll(pageScroll.offset());
    }

    // 滚动帧只平移已排好的控件：表单改视口原点，其余控件按文档坐标重算屏幕坐标。
    // 复合控件的 setBounds 会把子控件一起摆到文档位置，若子控件也在 content.widgets 里，
    // 再按增量平移就会叠加两次（方案翻页箭头曾因此与页码错位），因此这里一律用绝对坐标。
    private void applyScroll(int offset) {
        int delta = appliedOffset - offset;
        if (delta != 0) {
            // 不可见控件同样平移：否则它们下次变为可见时 bounds 仍停在文档坐标，命中会错位
            for (UiWidget widget : content.widgets) {
                if (widget instanceof FormView form) {
                    form.shiftViewportY(delta);
                    continue;
                }
                UiRect document = documentBounds.get(widget);
                if (document == null) {
                    UiRect bounds = widget.bounds();
                    widget.setBounds(bounds.x(), bounds.y() + delta, bounds.width(), bounds.height());
                } else {
                    widget.setBounds(document.x(), document.y() - offset, document.width(), document.height());
                }
            }
        }
        appliedOffset = offset;
    }

    // 记录文档坐标：布局收尾、施加滚动偏移之前调用，滚动帧据此重算屏幕坐标
    private void captureDocumentBounds() {
        documentBounds.clear();
        for (UiWidget widget : content.widgets) {
            documentBounds.put(widget, widget.bounds());
        }
    }

    /**
     * 已作用到控件上的滚动偏移：页面自绘文字的命中判定要把指针坐标换算回文档坐标。
     */
    private int layoutPage(UiRect area) {
        content.cards.clear();
        return switch (page) {
            case INFO -> infoSection.layoutInfo(area);
            case INPUT -> inputSection.layoutInput(area);
            case TRIGGER -> triggerSection.layoutTrigger(area);
            case RESULTS -> resultsSection.layoutResults(area);
        };
    }

    // 内容高度决定卡片高度；额外留白用于宽屏结果列表对齐。

    // 按钮组横向排布（超宽换行），返回占用高度

    // ---- 渲染 ----

    List<UiLayoutNode> layoutNodes() {
        List<UiLayoutNode> nodes = new ArrayList<>();
        IdentityHashMap<UiWidget, Boolean> seen = new IdentityHashMap<>();
        for (UiWidget widget : content.widgets) {
            if (seen.put(widget, Boolean.TRUE) != null || !(widget instanceof UiLayoutSource source)) {
                continue;
            }
            nodes.addAll(source.layoutNodes());
        }
        return nodes;
    }

    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        if (lastArea == null || lastArea.height() == 0) {
            return;
        }
        var pointer = UiPointer.gated(!pageScroll.isDragging(), mouseX, mouseY);
        mouseX = pointer.x();
        mouseY = pointer.y();
        // 卡片与页内文字按文档坐标绘制，滚动由 pose 平移体现：这两类不参与命中，无需坐标换算
        graphics.pose().pushPose();
        graphics.pose().translate(0, -appliedOffset, 0);
        graphics.enableScissor(lastArea.x(), lastArea.y(), lastArea.right(), lastArea.bottom());
        try {
            for (EditorSectionContent.PageCard card : content.cards) {
                THEME.drawCard(graphics, card.bounds());
                EditorSectionContent.CardHeader header = card.header();
                if (header != null) {
                    EditorSectionContent.drawCardHeader(THEME, graphics, font, card.headerRect(), header.icon(), header.title());
                }
            }
            switch (page) {
                case INFO -> infoSection.renderInfo(graphics);
                case INPUT -> inputSection.renderInput(graphics);
                case TRIGGER -> triggerSection.renderTrigger(graphics);
                case RESULTS -> resultsSection.renderResults(graphics);
            }
        } finally {
            graphics.disableScissor();
            graphics.pose().popPose();
        }
        // 控件保持屏幕坐标（滚动时已整体平移），命中、提示与拖拽预览因此无需换算
        graphics.enableScissor(lastArea.x(), lastArea.y(), lastArea.right(), lastArea.bottom());
        try {
            for (UiWidget widget : content.widgets) {
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

    // ---- 状态与提交 ----

    /**
     * 提交所有可见表单的待写字段；返回是否有改动
     */
    public boolean applyToDraft() {
        if (!shell.editable()) {
            return false;
        }
        // 存在条件的类型改写必须先落盘，才能与关联消耗配置的重指向合并为一次可撤销操作
        boolean changed = inputSection.flushPresenceTypes();
        for (FormView form : content.forms) {
            if (form.isVisible() && form.commitPendingInputs()) {
                changed = true;
            }
        }
        return changed;
    }

    /**
     * 收集所有可见表单的校验问题，再补页面级约束（触发来源、候选与效果上限、组合模式）
     */
    public List<FormIssue> issues() {
        List<FormIssue> issues = new ArrayList<>();
        for (FormView form : content.forms) {
            if (form.isVisible()) {
                issues.addAll(form.issues());
            }
        }
        RuleValue view = currentView();
        if (view != null) {
            addPageIssues(view, issues);
        }
        return issues;
    }

    // 页面级校验补充：候选与效果上限、气候条件全空、组合方式非法
    private static void addPageIssues(@Nullable RuleValue rule, List<FormIssue> issues) {
        int candidates = ResultStructure.candidateCount(rule);
        int effects = ResultStructure.totalEffectCount(rule);
        Component limit = Component.translatable(RuleEditorPageSupport.UI + "issue.candidate_limit", ConditionLimits.MAX_EFFECTS);
        if (candidates > ConditionLimits.MAX_EFFECTS) {
            issues.add(FormIssue.error(RuleFields.OUTCOMES, Component.translatable(RuleEditorPageSupport.UI + "candidate.section"), limit));
        } else if (effects > ConditionLimits.MAX_EFFECTS) {
            issues.add(FormIssue.error(RuleFields.EFFECTS, Component.translatable(RuleEditorPageSupport.UI + "candidate.section"), limit));
        }
        if (RuleEditorPanelFactory.climateAllUnbounded(rule)) {
            Component message = Component.translatable(RuleEditorPageSupport.UI + "issue.climate_empty");
            issues.add(FormIssue.error(RuleFields.CONDITIONS, Component.translatable(RuleEditorPageSupport.UI + "rule.conditions"), message));
        }
        RuleValue combination = rule == null ? null : rule.child(RuleFields.COMBINATION);
        if (combination != null && combination.isText() && !isKnownCombination(combination.asText())) {
            Component message = Component.translatable(RuleEditorPageSupport.UI + "issue.combination_invalid");
            issues.add(FormIssue.error(RuleFields.COMBINATION, Component.translatable(RuleEditorPageSupport.UI + "rule.combination"), message));
        }
    }

    private static boolean isKnownCombination(String raw) {
        if (raw == null) {
            return false;
        }
        String normalized = raw.trim();
        return CombinationMode.ROUND_ROBIN.key().equals(normalized) || CombinationMode.PRIORITY.key().equals(normalized);
    }

    // 从草稿路径里取某个数组段的序号（例如 outcomes[1].effects[2] 里的 outcomes 序号）
    private static int indexIn(String path, String name) {
        int at = path.indexOf(name + "[");
        if (at < 0) {
            return -1;
        }
        int start = at + name.length() + 1;
        int end = path.indexOf(']', start);
        if (end < 0) {
            return -1;
        }
        try {
            return Integer.parseInt(path.substring(start, end).trim());
        } catch (NumberFormatException error) {
            return -1;
        }
    }

    /**
     * 取消本页作用域的未完成交互，值恢复由当前会话目标执行。
     */
    public void endInteractions() {
        shell.editPage().inputScope().interaction().end(UiInteractionSession.EndReason.CANCEL);
    }

    /**
     * 宿主离开编辑页（返回管理页）：卸载表单的输入状态
     */
    public void unmount() {
        for (FormView form : content.forms) {
            form.unmount();
        }
    }

    /**
     * 键盘抬起：把滑杆方向键的按住过程合并为一次提交（屏幕在 EDIT 模式下转发）
     */
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        for (UiWidget widget : content.widgets) {
            if (widget.isVisible() && widget.keyReleased(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    /**
     * 聚焦目标（按视觉顺序）
     */
    public List<UiFocusTarget> focusTargets() {
        List<UiFocusTarget> targets = new ArrayList<>();
        for (UiWidget widget : content.widgets) {
            if (!widget.isVisible()) continue;
            if (widget instanceof FormView form) {
                targets.addAll(form.focusTargets());
            } else if (widget instanceof UiFocusTarget target && target.canFocus()) {
                targets.add(target);
            }
        }
        return targets;
    }

    /**
     * 冻结时禁用交互；只读内容不在这里禁用控件（沙盒可写）
     */
    void syncEditableState() {
        boolean editable = shell.editable();
        if (!editable) resultsSection.typePicker.close();
        applyEnabled();
    }

    void applyEnabled() {
        boolean editable = shell.editable();
        content.setEnabled(editable);
        inputSection.applyEnabled(editable);
        triggerSection.applyEnabled(editable);
        resultsSection.applyEnabled(editable);
        infoSection.applyServerState(editable);
    }

    // 只同步基本页的本服控件：内容字段变化可能改变生效状态（无覆盖时生效=默认启用）
    void refreshServerState() {
        infoSection.applyServerState(shell.editable());
    }

    public void tick() {
        triggerSection.updateLifespanWindow();
        for (FormView form : content.forms) {
            form.tick();
        }
        actions.flushRebuild();
    }

    /**
     * 绘制当前结果动作的自绘说明，保持与页面滚动一致的命中范围。
     *
     * @param graphics 绘制上下文
     * @param mouseX   鼠标屏幕 X 坐标
     * @param mouseY   鼠标屏幕 Y 坐标
     * @return 是否已绘制说明面板
     */
    public boolean renderDescriptionTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        return page == EditSection.RESULTS && !pageScroll.isDragging() && pageScroll.contains(mouseX, mouseY)
                && resultsSection.renderDescriptionTooltip(graphics, mouseX, mouseY);
    }

    public @Nullable Component tooltipAt(double mouseX, double mouseY) {
        if (pageScroll.isDragging()) return null;
        if (!pageScroll.contains(mouseX, mouseY)) {
            return null;
        }
        for (FormView form : content.forms) {
            if (!form.isVisible()) {
                continue;
            }
            Component tooltip = form.tooltipAt(mouseX, mouseY);
            if (tooltip != null) {
                return tooltip;
            }
        }
        return page == EditSection.RESULTS ? resultsSection.tooltipAt(mouseX, mouseY) : null;
    }

    /**
     * 按草稿路径切到对应页签、选中候选与效果，并返回应当聚焦的字段控件。
     * <p>用于本地校验与服务器问题定位（规则 → 页签 → 候选 → 效果 → 条件 → 字段）。
     */
    public @Nullable UiFocusTarget reveal(@Nullable String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        for (FormView form : content.forms) {
            if (!form.isVisible()) continue;
            UiFocusTarget target = form.revealPath(path);
            if (target != null) {
                ensureVisible(target);
                return target;
            }
        }
        if (ResultStructure.isResultPath(path)) {
            page = EditSection.RESULTS;
            resultsSection.resultStage = path.contains("[") ? 2 : 1;
            resultsSection.showAdvancedEffects = false;
            int candidate = indexIn(path, RuleFields.OUTCOMES);
            if (candidate >= 0) {
                resultsSection.candidateIndex = candidate;
            }
            int effect = indexIn(path, RuleFields.EFFECTS);
            if (effect >= 0) {
                resultsSection.effectIndex = effect;
            }
            rebuild(path);
            if (resultsSection.effectForm != null) {
                UiFocusTarget target = resultsSection.effectForm.revealPath(path);
                return target != null ? target : resultsSection.advancedEffectForm == null ? null
                        : resultsSection.advancedEffectForm.revealPath(path);
            }
            return null;
        }
        if (inField(path, RuleFields.CONDITIONS) || inField(path, RuleFields.TERMS)) {
            page = EditSection.TRIGGER;
            rebuild();
            return triggerSection.conditionsForm == null ? null : triggerSection.conditionsForm.revealPath(path);
        }
        if (path.startsWith(RuleFields.TRIGGERS) || path.startsWith(RuleFields.TRIGGER_AFTER_SECONDS)) {
            page = EditSection.TRIGGER;
            rebuild();
            // 触发来源问题直接聚焦第一个勾选框；延迟秒数问题落到延迟表单
            if (path.startsWith(RuleFields.TRIGGERS) && !triggerSection.triggerBoxes.isEmpty()) {
                return triggerSection.triggerBoxes.getFirst();
            }
            return triggerSection.delayForm == null ? null : triggerSection.delayForm.revealPath(path);
        }
        if (path.startsWith(RuleFields.COMBINATION)) {
            page = EditSection.RESULTS;
            rebuild();
            return resultsSection.combinationButton;
        }
        if (path.startsWith(RuleFields.SOURCE) || path.startsWith(RuleFields.CATALYST_COST)) {
            page = EditSection.INPUT;
            rebuild();
            FormView target = path.startsWith(RuleFields.CATALYST_COST) ? inputSection.catalystForm
                    : path.startsWith(RuleFields.SOURCE_COST) ? inputSection.sourceCostForm : inputSection.sourceForm;
            return target == null ? null : target.revealPath(path);
        }
        page = EditSection.INFO;
        rebuild();
        return infoSection.infoForm == null ? null : infoSection.infoForm.revealPath(path);
    }

    // 问题路径是否落在某字段内：等于字段本身，或字段后接「.」「[」的子项；
    // 仅字符串前缀相同（conditionsExtra）不算该字段
    private static boolean inField(String path, String field) {
        if (!path.startsWith(field)) {
            return false;
        }
        if (path.length() == field.length()) {
            return true;
        }
        char next = path.charAt(field.length());
        return next == '.' || next == '[';
    }

    // ---- 输入事件 ----

    public boolean mousePressed(UiInputContext context) {
        return input.mousePressed(context);
    }

    public boolean mouseReleased(UiInputContext context) {
        return input.mouseReleased(context);
    }

    public boolean mouseDragged(UiInputContext context, double dragX, double dragY) {
        return input.mouseDragged(context, dragX, dragY);
    }

    public boolean mouseScrolled(UiInputContext context, double scrollX, double scrollY) {
        return input.mouseScrolled(context, scrollX, scrollY);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return input.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return input.charTyped(codePoint, modifiers);
    }

    // ---- 内部工具 ----

    /**
     * 结算当前页的编辑状态：提交合法字段、保留非法文本、结束进行中的交互。
     *
     * <p>切页、切换候选/效果、结构变更、保存与撤销前调用。非法文本经
     * {@link EditSession#retainPendingInput(String, List, List)} 留在会话，重建表单后回填，
     * 因此本方法不阻塞后续操作；冻结（只读/会话失效）时不做任何写入。</p>
     */
    void retainPendingInputs() {
        for (FormView form : content.forms) form.retainPendingInputs();
    }

    void finishEditing() {
        if (!shell.editable()) {
            return;
        }
        endInteractions();
        retainPendingInputs();
        if (applyToDraft()) {
            shell.draftChanged();
        }
    }

    /**
     * Tab 和问题定位都需把焦点滚进外层页面视口。
     */
    public void ensureVisible(UiFocusTarget target) {
        if (lastArea == null || !focusTargets().contains(target)) {
            return;
        }
        UiRect rect = target.bounds();
        pageScroll.ensureVisible(new UiRect(rect.x() - lastArea.x(),
                rect.y() - lastArea.y() + pageScroll.offset(), rect.width(), rect.height()));
        // 只改变滚动位置：平移已排好的控件，不重新测量
        applyScroll(pageScroll.offset());
    }

    // 冻结或会话缺失时恢复开关显示，不重建输入控件。
    void restoreSwitches() {
        inputSection.restoreSwitches();
        triggerSection.restoreSwitches();
        resultsSection.restoreSwitches();
    }

    @Nullable RuleValue currentView() {
        EditSession session = shell.editingSession();
        return session == null ? null : session.root();
    }
}
