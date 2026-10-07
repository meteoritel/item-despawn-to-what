package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDefaults;
import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDescriptors;
import com.meteorite.itemdespawntowhat.client.edit.ConditionEditorRegistry;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorFactories;
import com.meteorite.itemdespawntowhat.client.edit.EditorWorkspaceView;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EffectEditorRegistry;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleNaming;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiAction;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiScrollView;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ConditionSupport;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormIssue;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.NaturalSummary;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.SuggestionProvider;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCheckBox;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiConditionTreeEditor;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiListView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModalStack;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiSegmentedControl;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiStructureDiagram;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.model.CombinationMode;
import com.meteorite.itemdespawntowhat.core.model.ConditionLimits;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 规则编辑页签的组合视图（主计划 §4 页面与导航、§5 现行结构与 JSON 编辑、§7 输入草稿与会话）。
 * <p>四页共用同一个 {@link EditSession} 草稿：所有写入都通过 {@link EditSession#apply(String, Runnable)}
 * 提交为一条撤销记录；只读展示（自动标题、路径提示、只读高级区、摘要）不写数据。
 * <p>顶层 effects 展示为单一隐式候选：打开、改别名、改效果都不会自动生成 outcomes；
 * 只有用户新增第二个候选或明确编辑候选专属策略时，才用 {@link RuleDraft#convertToOutcomes(String)}
 * 做一次原子转换（保留原效果对象、一次撤销可整体还原）。
 * <p>消耗类效果（consume_source / consume_catalyst）映射到「输入与成本」页，只更新原效果路径，
 * 不会同时生成规则级固定成本字段；这两类效果不出现在效果选择器里。
 */
public final class RuleEditorEditPages {

    // 页签
    public enum Page {

        // 基本信息
        INFO("info"),
        // 输入与成本
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

    // 布局常量
    private static final int PAD = 4;
    private static final int ROW_H = 12;
    private static final int BUTTON_H = 16;
    private static final int BUTTON_GAP = 2;
    private static final int LINE_H = 10;
    private static final int LIST_ROWS = 4;
    private static final int NARROW_WIDTH = 430;

    // 本地化前缀
    private static final String UI = TypeLabels.UI_PREFIX;

    // 描述符 id（仅界面按名取用）
    private static final ResourceLocation INFO_PAGE = ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, "rule_info_page");
    private static final ResourceLocation DELAY_PAGE = ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, "rule_delay_page");
    private static final ResourceLocation CONDITIONS_PAGE = ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, "conditions_page");
    private static final ResourceLocation CONSUME_SOURCE_ID = ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, "consume_source");
    private static final ResourceLocation CONSUME_CATALYST_ID = ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, "consume_catalyst");

    // 源成本模式
    private static final String COST_DERIVED = "derived";
    private static final String COST_EXPLICIT = "explicit";

    // 拖动目标
    private enum DragKind {
        NONE,
        CANDIDATE,
        EFFECT
    }

    private final Host host;
    private final Font font;
    private final List<UiWidget> liveWidgets = new ArrayList<>();
    private final List<FormView> liveForms = new ArrayList<>();
    private final List<UiButton> buttons = new ArrayList<>();
    private final List<UiButton> candidateButtons = new ArrayList<>();
    private final List<UiButton> effectButtons = new ArrayList<>();
    // 已打开的叶子面板与所属模态：模态关闭后按栈内存在性清理引用
    private final List<LeafEntry> leafPanels = new ArrayList<>();

    // 叶子面板条目：面板随模态一起关闭，模态销毁后引用必须移除
    private record LeafEntry(UiModal modal, RuleEditorP4Panels.LeafPanel panel) {
    }

    // 名称来源：注册表标签优先，缺失回落 id 文本（由 RuleNaming 兜底）
    private final RuleNaming.NameSource nameSource = RuleDisplayLabels::label;

    private final UiScrollView pageScroll = new UiScrollView();
    private final EnumMap<Page, Integer> scrollOffsets = new EnumMap<>(Page.class);
    private Page page = Page.INFO;
    private boolean enabled = true;
    private boolean pendingRebuild;
    private boolean rebuilding;
    private @Nullable UiRect lastArea;
    private int candidateIndex;
    private int effectIndex;

    // 基本信息
    private @Nullable FormView infoForm;
    private @Nullable UiButton restoreNameButton;
    private @Nullable UiRect autoNameRect;
    private @Nullable UiRect advancedRect;

    // 输入与成本
    private @Nullable FormView sourceForm;
    // 源物品的目录选择入口（图标网格面板）
    private @Nullable UiSegmentedControl sourceCostMode;
    private @Nullable FormView sourceCostForm;
    private @Nullable FormView catalystForm;
    private @Nullable UiRect costModeRect;
    private @Nullable UiRect costNoteRect;
    private @Nullable Component costNote;
    private @Nullable UiCheckBox catalystToggle;
    private @Nullable UiRect catalystNoteRect;
    private @Nullable Component catalystNote;

    // 触发与条件
    private final List<UiCheckBox> triggerBoxes = new ArrayList<>();
    private @Nullable UiRect triggerNoteRect;
    private @Nullable FormView delayForm;
    private @Nullable UiRect delayHintRect;
    private @Nullable FormView conditionsForm;

    // 结果
    private @Nullable UiListView<Integer> candidateList;
    private @Nullable UiCheckBox safeSpawnBox;
    private @Nullable UiCheckBox fillOriginBox;
    private @Nullable UiSegmentedControl combinationControl;
    private @Nullable UiRect combinationLabelRect;
    private @Nullable UiListView<Integer> effectList;
    private @Nullable FormView effectForm;
    private @Nullable UiSegmentedControl variantControl;
    private @Nullable FormView advancedEffectForm;
    private @Nullable UiButton advancedButton;
    private @Nullable UiButton resultBack;
    private boolean showAdvancedEffects;
    private int resultStage;
    private @Nullable UiRect productPreviewRect;
    private @Nullable JsonObject selectedAction;
    private @Nullable UiRect flatNoteRect;
    private @Nullable UiRect convertHintRect;
    // 未注册效果类型的只读说明行
    private @Nullable UiRect effectNoteRect;

    // 拖动状态：局部预览、不每帧写数据，释放时一次提交
    private DragKind dragKind = DragKind.NONE;
    private int dragFrom = -1;
    private int dragTo = -1;
    private boolean dragActive;
    private double dragStartY;

    public RuleEditorEditPages(Host host) {
        this.host = host;
        this.font = host.font();
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
        effectIndex = 0;
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
            case INFO -> buildInfo(session);
            case INPUT -> buildInput(session);
            case TRIGGER -> buildTrigger(session);
            case RESULTS -> buildResults(session);
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
        infoForm = null;
        restoreNameButton = null;
        autoNameRect = null;
        advancedRect = null;
        sourceForm = null;
        sourceCostMode = null;
        sourceCostForm = null;
        catalystForm = null;
        costModeRect = null;
        costNoteRect = null;
        costNote = null;
        catalystToggle = null;
        catalystNoteRect = null;
        catalystNote = null;
        triggerBoxes.clear();
        triggerNoteRect = null;
        delayForm = null;
        delayHintRect = null;
        conditionsForm = null;
        candidateList = null;
        safeSpawnBox = null;
        fillOriginBox = null;
        combinationControl = null;
        combinationLabelRect = null;
        effectList = null;
        effectForm = null;
        variantControl = null;
        advancedEffectForm = null;
        advancedButton = null;
        resultBack = null;
        productPreviewRect = null;
        selectedAction = null;
        flatNoteRect = null;
        convertHintRect = null;
        effectNoteRect = null;
        dragKind = DragKind.NONE;
        dragActive = false;
        dragFrom = -1;
        dragTo = -1;
    }

    // 表单工厂
    private FormView newForm(EditSession session, String basePath, TypeEditorDescriptor descriptor) {
        FormView form = new FormView(font, session, basePath);
        form.setConditionSupport(pageConditionSupport(form));
        form.setSuggestionProvider(host.suggestions());
        form.setOnChanged(host::onDraftChanged);
        form.setCatalogOpener((field, tags, onPicked) -> {
            RuleCatalogType type = tags ? RuleCatalogType.TAG : RuleEditorP4Panels.catalogTypeOf(field);
            if (type == null || host.rejectWhenFrozen()) return;
            boolean multi = field.type() == com.meteorite.itemdespawntowhat.client.edit.EditorFieldType.TAG_LIST
                    || field.type() == com.meteorite.itemdespawntowhat.client.edit.EditorFieldType.RL_LIST;
            host.modals().push(RuleEditorP4Panels.catalogModal(font, host.workspace(), type, multi,
                    Component.translatable(field.labelKey()), host.screenWidth(), host.screenHeight(), onPicked, field));
        });
        form.setDescriptor(descriptor);
        form.reload();
        return form;
    }

    // ---- 基本信息 ----

    private void buildInfo(EditSession session) {
        JsonObject rule = session.draft().view();
        TypeEditorDescriptor descriptor = TypeEditorDescriptor.of(INFO_PAGE, Component.translatable(UI + "tab.info"), List.of(
                EditorField.bool(RuleFields.ENABLED, UI + "rule.enabled"),
                EditorField.integerSlider(RuleFields.PRIORITY, UI + "rule.priority", Integer.MIN_VALUE, Integer.MAX_VALUE, -100, 100).optional(),
                EditorField.optionalText(RuleFields.DISPLAY_NAME, UI + "rule.display_name"),
                EditorField.longText(RuleFields.NOTES, UI + "rule.notes")));
        infoForm = newForm(session, "", descriptor);
        restoreNameButton = new UiButton(font, Component.translatable(UI + "button.restore_name"), UiButtonVariant.SECONDARY, this::restoreAutoName);
        restoreNameButton.setEnabled(rule.has(RuleFields.DISPLAY_NAME));
        liveForms.add(infoForm);
        liveWidgets.add(infoForm);
        liveWidgets.add(restoreNameButton);
        buttons.add(restoreNameButton);
    }

    // 恢复自动命名：清空 display_name，一次撤销可还原
    private void restoreAutoName() {
        EditSession session = host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        if (!apply(EditSession.OP_SET_FIELD, () -> draft.remove(RuleFields.DISPLAY_NAME))) {
            return;
        }
        host.notice(Component.translatable(UI + "notice.name_restored"), UiPalette.TEXT_SECONDARY);
    }

    // ---- 输入与成本 ----

    private void buildInput(EditSession session) {
        JsonObject rule = session.draft().view();
        sourceForm = newForm(session, RuleFields.SOURCE, BuiltinEditorDescriptors.sourceDescriptor());
        liveForms.add(sourceForm);
        liveWidgets.add(sourceForm);

        RuleCostBinding.Ref sourceRef = RuleCostBinding.consumeSource(rule);
        sourceCostMode = new UiSegmentedControl(font, costModeOptions());
        if (sourceRef != null) {
            sourceCostMode.setSelected(COST_EXPLICIT);
            sourceCostMode.setEnabled(false);
            costNote = Component.translatable(UI + "cost.mode.from_effect").append(" ")
                    .append(Component.translatable(UI + "cost.from_effect_path", Component.literal(sourceRef.path())));
            sourceCostForm = newForm(session, sourceRef.path(), ownFieldsOnly(EffectEditorRegistry.descriptorFor(CONSUME_SOURCE_ID)));
        } else {
            boolean custom = rule.has(RuleFields.SOURCE_COST);
            sourceCostMode.setSelected(custom ? COST_EXPLICIT : COST_DERIVED);
            sourceCostMode.setOnChanged(this::onSourceCostMode);
            sourceCostForm = custom ? newForm(session, "", BuiltinEditorDescriptors.sourceCostDescriptor()) : null;
        }
        liveWidgets.add(sourceCostMode);
        if (sourceCostForm != null) {
            liveForms.add(sourceCostForm);
            liveWidgets.add(sourceCostForm);
        }

        RuleCostBinding.Ref catalystRef = RuleCostBinding.consumeCatalyst(rule);
        boolean catalystOn = rule.has(RuleFields.CATALYST_COST);
        catalystToggle = new UiCheckBox(font, Component.translatable(UI + "rule.catalyst_cost"), catalystRef != null || catalystOn);
        if (catalystRef != null) {
            catalystToggle.setEnabled(false);
            catalystNote = Component.translatable(UI + "cost.from_effect_path", Component.literal(catalystRef.path()));
            catalystForm = newForm(session, catalystRef.path(), ownFieldsOnly(EffectEditorRegistry.descriptorFor(CONSUME_CATALYST_ID)));
        } else {
            catalystToggle.setOnChanged(this::onCatalystToggle);
            catalystForm = catalystOn ? newForm(session, RuleFields.CATALYST_COST, BuiltinEditorDescriptors.catalystCostDescriptor()) : null;
        }
        liveWidgets.add(catalystToggle);
        if (catalystForm != null) {
            liveForms.add(catalystForm);
            liveWidgets.add(catalystForm);
        }
    }

    // 成本模式分段控件选项
    private List<UiSegmentedControl.Option> costModeOptions() {
        return List.of(
                new UiSegmentedControl.Option(COST_DERIVED, Component.translatable(UI + "cost.mode.derived")),
                new UiSegmentedControl.Option(COST_EXPLICIT, Component.translatable(UI + "cost.mode.custom")));
    }

    // 只保留效果自身字段：成本区不重复暴露 delay_ticks / chance / 条件
    private static TypeEditorDescriptor ownFieldsOnly(TypeEditorDescriptor descriptor) {
        List<EditorField> fields = new ArrayList<>();
        for (EditorField field : descriptor.fields()) {
            if (RuleFields.DELAY_TICKS.equals(field.name()) || RuleFields.CHANCE.equals(field.name()) || RuleFields.CONDITIONS.equals(field.name())) {
                continue;
            }
            fields.add(field);
        }
        return TypeEditorDescriptor.of(descriptor.id(), descriptor.label(), fields);
    }

    // 切换「按规则推导 / 自定义」：推导移除 source_cost，自定义写新建默认 1
    private void onSourceCostMode(String value) {
        EditSession session = host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        apply(EditSession.OP_SET_FIELD, () -> {
            if (COST_DERIVED.equals(value)) {
                draft.remove(RuleFields.SOURCE_COST);
            } else {
                draft.setInt(RuleFields.SOURCE_COST, 1);
            }
        });
    }

    // 催化剂成本开关：开启写空 items 对象（count / radius 保持省略），关闭移除字段
    private void onCatalystToggle(boolean checked) {
        EditSession session = host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        apply(EditSession.OP_SET_FIELD, () -> {
            if (checked) {
                JsonObject body = new JsonObject();
                body.add(RuleFields.CATALYST_ITEMS, new JsonArray());
                draft.setAt(RuleFields.CATALYST_COST, body);
            } else {
                draft.remove(RuleFields.CATALYST_COST);
            }
        });
    }

    // ---- 触发与条件 ----

    private void buildTrigger(EditSession session) {
        JsonObject rule = session.draft().view();
        List<String> effective = RuleTriggers.effective(rule);
        for (String value : RuleTriggers.known()) {
            UiCheckBox box = new UiCheckBox(font, Component.translatable(UI + "trigger." + value), effective.contains(value));
            box.setOnChanged(checked -> onTriggerToggled());
            triggerBoxes.add(box);
            liveWidgets.add(box);
        }
        if (RuleTriggers.allowsDelay(rule)) {
            delayForm = newForm(session, "", TypeEditorDescriptor.of(DELAY_PAGE, Component.translatable(UI + "rule.trigger_after_seconds"), List.of(
                    EditorField.integerSlider(RuleFields.TRIGGER_AFTER_SECONDS, UI + "rule.trigger_after_seconds", 0, Integer.MAX_VALUE, 0, 600).optional())));
            liveForms.add(delayForm);
            liveWidgets.add(delayForm);
        }
        TypeEditorDescriptor conditions = TypeEditorDescriptor.of(CONDITIONS_PAGE, Component.translatable(UI + "tab.trigger"), List.of(
                EditorField.conditionTree(RuleFields.CONDITIONS, UI + "rule.conditions")));
        conditionsForm = newForm(session, "", conditions);
        conditionsForm.setLabelWidth(90);
        liveForms.add(conditionsForm);
        liveWidgets.add(conditionsForm);
    }

    // 触发原因勾选：按四个勾选框的当前状态写显式数组，未知取值原样保留
    private void onTriggerToggled() {
        EditSession session = host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        List<String> values = new ArrayList<>();
        for (int i = 0; i < triggerBoxes.size(); i++) {
            if (triggerBoxes.get(i).isChecked()) {
                values.add(RuleTriggers.known().get(i));
            }
        }
        for (String value : RuleTriggers.effective(draft.view())) {
            if (!RuleTriggers.known().contains(value) && !values.contains(value)) {
                values.add(value);
            }
        }
        apply(EditSession.OP_SET_FIELD, () -> draft.setAt(RuleFields.TRIGGERS, RuleTriggers.toArray(values)));
    }

    // 条件树支持：叶参数编辑由本页负责（弹窗绑定发起编辑的那个表单）
    private ConditionSupport pageConditionSupport(FormView owner) {
        ConditionSupport base = host.conditionSupport();
        return new ConditionSupport() {
            @Override
            public List<UiConditionTreeEditor.TypeOption> typeOptions() {
                return base.typeOptions();
            }

            @Override
            public UiConditionTreeEditor.LeafFactory leafFactory() {
                return base.leafFactory();
            }

            @Override
            public Consumer<ConditionNode.Leaf> onEditLeaf() {
                return leaf -> openLeafEditor(owner, leaf);
            }

            @Override
            public TypeRegistry<ConditionType<?>> registry() {
                return base.registry();
            }
        };
    }

    // 条件叶编辑入口（屏幕的 ConditionSupport 转发到此）：先找到承载该叶子的表单再开弹窗
    public void editConditionLeaf(ConditionNode.Leaf leaf) {
        for (FormView form : liveForms) {
            if (findTree(form, leaf) != null) {
                openLeafEditor(form, leaf);
                return;
            }
        }
    }

    // 条件叶参数弹窗：确认后把参数写回草稿并刷新发起编辑的表单
    private void openLeafEditor(FormView owner, ConditionNode.Leaf leaf) {
        EditSession session = host.session();
        if (session == null) {
            return;
        }
        UiConditionTreeEditor tree = findTree(owner, leaf);
        String base = tree != null && tree.selectedPath() != null ? tree.selectedPath() : RuleFields.CONDITIONS;
        String path = owner.draftPath(base.endsWith("." + RuleFields.CONDITION) ? base : base + "." + RuleFields.CONDITION);
        // 先写入新建的树节点，叶参数表单才有可读取的草稿路径。
        owner.applyToDraft();
        TypeEditorDescriptor descriptor = ConditionEditorRegistry.descriptorFor(leaf.condition().type());
        FormView form = new FormView(font, session, path);
        form.setConditionSupport(pageConditionSupport(form));
        form.setSuggestionProvider(host.suggestions());
        form.setOnChanged(host::onDraftChanged);
        form.setCatalogOpener((field, tags, onPicked) -> {
            RuleCatalogType type = tags ? RuleCatalogType.TAG : RuleEditorP4Panels.catalogTypeOf(field);
            if (type == null || host.rejectWhenFrozen()) return;
            boolean multi = field.type() == com.meteorite.itemdespawntowhat.client.edit.EditorFieldType.TAG_LIST
                    || field.type() == com.meteorite.itemdespawntowhat.client.edit.EditorFieldType.RL_LIST;
            host.modals().push(RuleEditorP4Panels.catalogModal(font, host.workspace(), type, multi,
                    Component.translatable(field.labelKey()), host.screenWidth(), host.screenHeight(), onPicked, field));
        });
        form.setDescriptor(descriptor);
        form.reload();
        UiModal modal = UiModal.create(font);
        modal.title(descriptor.label());
        // 已知条件类型附带区间条（气候/昼夜/高度光照）与注册表字段的目录按钮；
        // 面板自身负责子控件的事件转发与 Tab 顺序，普通数值字段仍由表单承载
        RuleEditorP4Panels.LeafPanel panel = RuleEditorP4Panels.leafPanel(font, form, session, descriptor.fields(),
                path, change -> apply(EditSession.OP_SET_FIELD, change), () -> {
                    form.reload();
                    host.onDraftChanged();
                }, host.modals()::push, host.workspace(), host.screenWidth(), host.screenHeight());
        int fieldHeight = descriptor.fields().size() * ROW_H + 8;
        int contentHeight = Math.clamp(Math.max(fieldHeight, panel.contentHeight() + 8), 36, 220);
        modal.contentWidget(panel, contentHeight);
        leafPanels.add(new LeafEntry(modal, panel));
        modal.retainOnConfirm(true);
        modal.confirm(Component.translatable(UI + "button.confirm"), () -> {
            form.pendingInputIssue();
            FormIssue issue = form.issues().stream().filter(FormIssue::blocking).findFirst().orElse(null);
            if (issue != null) {
                Component message = Component.translatable(UI + "notice.issue", issue.label(), issue.message());
                modal.message(message);
                modal.layoutCentered(host.screenWidth(), host.screenHeight());
                panel.focusField(issue.path());
                return;
            }
            form.applyToDraft();
            owner.reload();
            host.onDraftChanged();
            host.modals().closeTop();
        });
        modal.cancel(Component.translatable(UI + "button.cancel"));
        modal.layoutCentered(host.screenWidth(), host.screenHeight());
        host.modals().push(modal);
    }

    // 找到发起编辑的条件树
    private static @Nullable UiConditionTreeEditor findTree(FormView owner, ConditionNode.Leaf leaf) {
        for (UiFocusTarget target : owner.focusTargets()) {
            if (target instanceof UiConditionTreeEditor tree && tree.selectedNode() == leaf) {
                return tree;
            }
        }
        for (UiFocusTarget target : owner.focusTargets()) {
            if (target instanceof UiConditionTreeEditor tree) {
                return tree;
            }
        }
        return null;
    }

    // ---- 结果 ----

    private void buildResults(EditSession session) {
        RuleDraft draft = session.draft();
        JsonObject rule = draft.view();
        int candidates = Math.max(1, ResultStructure.candidateCount(rule));
        candidateIndex = Math.clamp(candidateIndex, 0, candidates - 1);



        candidateList = new UiListView<>(font, this::renderCandidateRow);
        candidateList.setRowHeight(ROW_H);
        candidateList.setEmptyMessage(Component.translatable(UI + "candidate.flat_note"));
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < candidates; i++) {
            indexes.add(i);
        }
        candidateList.setItems(indexes);
        rebuilding = true;
        candidateList.setSelectedIndex(candidateIndex);
        rebuilding = false;
        candidateList.setOnSelectionChanged(index -> {
            if (rebuilding) {
                return;
            }
            if (blockNavigation()) {
                rebuilding = true;
                if (candidateList != null) {
                    candidateList.setSelectedIndex(candidateIndex);
                }
                rebuilding = false;
                return;
            }
            candidateIndex = Math.max(0, index);
            effectIndex = 0;
            requestRebuild();
        });
        candidateList.setOnActivate(index -> enterResultStage(1));
        liveWidgets.add(candidateList);
        resultBack = new UiButton(font, Component.translatable(UI + "button.result_plans"), UiButtonVariant.SECONDARY,
                () -> enterResultStage(resultStage == 2 ? 1 : 0));
        liveWidgets.add(resultBack);
        buttons.add(resultBack);
        if (host.screenWidth() < NARROW_WIDTH) addCandidateButton("button.candidate_edit", () -> enterResultStage(1));

        JsonObject candidate = ResultStructure.candidateAt(rule, candidateIndex);
        JsonArray actions = ResultStructure.effectsAt(rule, candidateIndex);
        boolean generic = containsAction(actions, "spawn_entity", "entity");
        boolean blocks = containsAction(actions, "place_block", null);
        if (generic) {
            safeSpawnBox = new UiCheckBox(font, Component.translatable(UI + "candidate.safe_spawn"), boolOf(candidate, RuleFields.SAFE_SPAWN, false));
            safeSpawnBox.setOnChanged(checked -> setCandidateStrategy(RuleFields.SAFE_SPAWN, checked));
            liveWidgets.add(safeSpawnBox);
        }
        if (blocks) {
            fillOriginBox = new UiCheckBox(font, Component.translatable(UI + "candidate.fill_origin"), boolOf(candidate, RuleFields.FILL_ORIGIN, true));
            fillOriginBox.setOnChanged(checked -> setCandidateStrategy(RuleFields.FILL_ORIGIN, checked));
            liveWidgets.add(fillOriginBox);
        }
        if (candidates > 1) {
            combinationControl = new UiSegmentedControl(font, List.of(
                    new UiSegmentedControl.Option(CombinationMode.ROUND_ROBIN.key(), Component.translatable(UI + "combination.round_robin")),
                    new UiSegmentedControl.Option(CombinationMode.PRIORITY.key(), Component.translatable(UI + "combination.priority"))));
            combinationControl.setSelected(combinationOf(rule));
            combinationControl.setOnChanged(this::onCombinationChanged);
            liveWidgets.add(combinationControl);
        }

        String listPath = ResultStructure.listPath(rule, candidateIndex);
        int effectCount = ResultStructure.effectCount(rule, candidateIndex);
        effectIndex = Math.clamp(effectIndex, 0, Math.max(0, effectCount - 1));
        effectList = new UiListView<>(font, this::renderEffectRow);
        effectList.setRowHeight(24);
        effectList.setEmptyMessage(Component.translatable(UI + "summary.no_effect"));
        List<Integer> effects = new ArrayList<>();
        for (int i = 0; i < effectCount; i++) {
            effects.add(i);
        }
        effectList.setItems(effects);
        rebuilding = true;
        effectList.setSelectedIndex(effectIndex);
        rebuilding = false;
        effectList.setOnSelectionChanged(index -> {
            if (rebuilding) {
                return;
            }
            if (blockNavigation()) {
                rebuilding = true;
                if (effectList != null) {
                    effectList.setSelectedIndex(effectIndex);
                }
                rebuilding = false;
                return;
            }
            effectIndex = Math.max(0, index);
            requestRebuild();
        });
        effectList.setOnActivate(index -> enterResultStage(2));
        liveWidgets.add(effectList);
        if (host.screenWidth() < NARROW_WIDTH && effectCount > 0) addEffectButton("button.effect_edit", () -> enterResultStage(2));

        JsonArray array = ResultStructure.effectsAt(rule, candidateIndex);
        if (array != null && effectIndex >= 0 && effectIndex < array.size()) {
            JsonElement element = array.get(effectIndex);
            String type = RuleCostBinding.typeOf(element);
            ResourceLocation typeId = type == null ? null : ResourceLocation.tryParse(type);
            if (typeId != null) {
                String actionPath = listPath + "[" + effectIndex + "]";
                TypeEditorDescriptor descriptor = EffectEditorRegistry.descriptorFor(typeId);
                if (typeId.equals(ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, "spawn_entity"))
                        && element.isJsonObject()) {
                    JsonObject action = element.getAsJsonObject();
                    String variant = action.has("variant") ? action.get("variant").getAsString() : "item";
                    descriptor = BuiltinEditorDescriptors.entityDescriptor(variant);
                    variantControl = new UiSegmentedControl(font, List.of(
                            new UiSegmentedControl.Option("item", Component.translatable(UI + "product.item")),
                            new UiSegmentedControl.Option("entity", Component.translatable(UI + "product.entity")),
                            new UiSegmentedControl.Option("experience", Component.translatable(UI + "product.experience"))));
                    variantControl.setSelected(variant);
                    variantControl.setOnChanged(value -> switchProduct(actionPath, value));
                    liveWidgets.add(variantControl);
                }
                selectedAction = element.isJsonObject() ? element.getAsJsonObject() : null;
                List<EditorField> primary = descriptor.fields().stream().filter(field -> !isCommonActionField(field)).toList();
                List<EditorField> advanced = descriptor.fields().stream().filter(RuleEditorEditPages::isCommonActionField).toList();
                effectForm = newForm(session, actionPath, new TypeEditorDescriptor(descriptor.id(), descriptor.label(), primary, descriptor.readOnly()));
                liveForms.add(effectForm);
                liveWidgets.add(effectForm);
                if (!advanced.isEmpty()) {
                    advancedEffectForm = newForm(session, actionPath, new TypeEditorDescriptor(descriptor.id(), descriptor.label(), advanced, false));
                    liveForms.add(advancedEffectForm); liveWidgets.add(advancedEffectForm);
                    advancedButton = new UiButton(font, Component.translatable(UI + "button.advanced"), UiButtonVariant.SECONDARY, () -> {
                        if (blockNavigation()) return;
                        showAdvancedEffects = !showAdvancedEffects; requestRebuild();
                    });
                    liveWidgets.add(advancedButton); buttons.add(advancedButton);
                }
            }
        }

        addCandidateButton("button.candidate_add", this::addCandidate);
        addCandidateButton("button.more", () -> openResultMore(true));
        addEffectButton("button.effect_add", this::openEffectPicker);
        if (effectCount > 0) addEffectButton("button.more", () -> openResultMore(false));
    }

    /*** 操作菜单只保存显示文本和调用入口，不承载规则数据。 */
    private record MenuAction(Component label, Runnable action) { }

    private void openResultMore(boolean candidate) {
        if (blockNavigation()) return;
        List<MenuAction> actions = new ArrayList<>();
        if (candidate) {
            actions.add(new MenuAction(Component.translatable(UI + "button.diagram"), this::openDiagram));
            actions.add(new MenuAction(Component.translatable(UI + "button.candidate_duplicate"), this::duplicateCandidate));
            actions.add(new MenuAction(Component.translatable(UI + "button.candidate_up"), () -> moveCandidateBy(-1)));
            actions.add(new MenuAction(Component.translatable(UI + "button.candidate_down"), () -> moveCandidateBy(1)));
            if (ResultStructure.hasOutcomes(currentView())) actions.add(new MenuAction(Component.translatable(UI + "button.candidate_remove"), this::removeCandidate));
        } else {
            actions.add(new MenuAction(Component.translatable(UI + "button.effect_duplicate"), this::duplicateEffect));
            actions.add(new MenuAction(Component.translatable(UI + "button.effect_up"), () -> moveEffectBy(-1)));
            actions.add(new MenuAction(Component.translatable(UI + "button.effect_down"), () -> moveEffectBy(1)));
            actions.add(new MenuAction(Component.translatable(UI + "button.effect_remove"), this::removeEffect));
        }
        UiListView<MenuAction> list = new UiListView<>(font, (graphics, rowFont, item, index, row, selected, hovered, focused) ->
                graphics.drawString(rowFont, item.label(), row.x() + 2, row.y() + 2, UiPalette.TEXT_PRIMARY, false));
        list.setItems(actions);
        list.setActivateOnSingleClick(true);
        UiModal modal = UiModal.create(font).title(Component.translatable(UI + "button.more"));
        list.setOnActivate(item -> { host.modals().close(modal); item.action().run(); flushRebuild(); });
        modal.contentWidget(list, actions.size() * 12 + 4).cancel(Component.translatable(UI + "button.cancel"));
        host.modals().push(modal.layoutCentered(host.screenWidth(), host.screenHeight()));
    }

    // 候选专属策略：顶层 effects 结构下先原子转换为 outcomes，再写字段（一次撤销）
    private void setCandidateStrategy(String field, boolean value) {
        EditSession session = host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        boolean convert = !draft.hasOutcomes();
        int index = Math.max(0, candidateIndex);
        boolean applied = apply(convert ? EditSession.OP_CONVERT_STRUCTURE : EditSession.OP_EDIT_CANDIDATE, () -> {
            if (convert) {
                draft.convertToOutcomes(EditorFactories.uniqueCandidateId(Set.of()));
            }
            draft.setAt(RuleFields.OUTCOMES + "[" + index + "]." + field, new JsonPrimitive(value));
        });
        if (applied && convert) {
            host.notice(Component.translatable(UI + "notice.converted"), UiPalette.TEXT_SECONDARY);
        }
    }

    // 组合模式：缺省 round_robin
    private static String combinationOf(@Nullable JsonObject rule) {
        if (rule == null) {
            return CombinationMode.ROUND_ROBIN.key();
        }
        JsonElement element = rule.get(RuleFields.COMBINATION);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return CombinationMode.ROUND_ROBIN.key();
        }
        return element.getAsString();
    }

    private void onCombinationChanged(String value) {
        EditSession session = host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        apply(EditSession.OP_SET_FIELD, () -> draft.setString(RuleFields.COMBINATION, value));
    }

    // 新增候选：第二候选出现即触发结构转换
    private void addCandidate() {
        EditSession session = host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        if (ResultStructure.candidateCount(draft.view()) >= ConditionLimits.MAX_EFFECTS) {
            host.notice(Component.translatable(UI + "issue.candidate_limit"), UiPalette.WARNING);
            return;
        }
        boolean convert = !draft.hasOutcomes();
        if (!apply(EditSession.OP_ADD_CANDIDATE, () -> {
            if (convert) {
                draft.convertToOutcomes(EditorFactories.uniqueCandidateId(Set.of()));
            }
            JsonObject body = new JsonObject();
            body.addProperty(RuleFields.CANDIDATE_ID, EditorFactories.uniqueCandidateId(draft.candidateIds()));
            body.add(RuleFields.CANDIDATE_EFFECTS, new JsonArray());
            draft.addOutcome(body);
        })) {
            return;
        }
        candidateIndex = Math.max(0, ResultStructure.candidateCount(draft.view()) - 1);
        effectIndex = 0;
        host.notice(Component.translatable(UI + (convert ? "notice.converted" : "notice.candidate_added")), UiPalette.TEXT_SECONDARY);
    }

    // 复制候选（含策略与效果，换新 id）
    private void duplicateCandidate() {
        if (blockNavigation()) {
            return;
        }
        EditSession session = host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        if (ResultStructure.candidateCount(draft.view()) >= ConditionLimits.MAX_EFFECTS) {
            host.notice(Component.translatable(UI + "issue.candidate_limit"), UiPalette.WARNING);
            return;
        }
        boolean convert = !draft.hasOutcomes();
        int index = Math.max(0, candidateIndex);
        if (!apply(EditSession.OP_ADD_CANDIDATE, () -> {
            if (convert) {
                draft.convertToOutcomes(EditorFactories.uniqueCandidateId(Set.of()));
            }
            int last = draft.outcomeCount() - 1;
            int from = index > last ? Math.max(0, last) : index;
            JsonObject body = draft.outcomeAt(from);
            if (body == null) {
                return;
            }
            body.addProperty(RuleFields.CANDIDATE_ID, EditorFactories.uniqueCandidateId(draft.candidateIds()));
            draft.addOutcome(body);
        })) {
            return;
        }
        candidateIndex = Math.max(0, ResultStructure.candidateCount(draft.view()) - 1);
        effectIndex = 0;
        host.notice(Component.translatable(UI + "notice.candidate_added"), UiPalette.TEXT_SECONDARY);
    }

    // 删除候选：至少保留一个（空候选由本地校验拦截）
    private void removeCandidate() {
        EditSession session = host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        if (!draft.hasOutcomes() || draft.outcomeCount() <= 1) {
            host.notice(Component.translatable(UI + "notice.no_selection"), UiPalette.TEXT_DISABLED);
            return;
        }
        int index = Math.max(0, candidateIndex);
        if (!apply(EditSession.OP_REMOVE_CANDIDATE, () -> draft.removeOutcome(index))) {
            return;
        }
        candidateIndex = Math.max(0, index - 1);
        effectIndex = 0;
        host.notice(Component.translatable(UI + "notice.candidate_removed"), UiPalette.TEXT_SECONDARY);
    }

    private void moveCandidateBy(int delta) {
        int target = candidateIndex + delta;
        if (delta == 0 || target < 0) {
            return;
        }
        EditSession session = host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        if (target >= draft.outcomeCount()) {
            return;
        }
        moveCandidate(candidateIndex, target);
    }

    // 候选重排：一次排序一次撤销
    private void moveCandidate(int from, int to) {
        EditSession session = host.session();
        if (session == null || from == to) {
            return;
        }
        RuleDraft draft = session.draft();
        if (!apply(EditSession.OP_MOVE_CANDIDATE, () -> draft.moveOutcome(from, to))) {
            return;
        }
        candidateIndex = to;
        effectIndex = 0;
    }

    // 效果选择器：隐藏消耗类效果（它们由成本区表达）
    private void openEffectPicker() {
        if (blockNavigation()) {
            return;
        }
        List<ResourceLocation> types = new ArrayList<>();
        for (TypeEditorDescriptor descriptor : EffectEditorRegistry.all()) {
            if (CONSUME_SOURCE_ID.equals(descriptor.id()) || CONSUME_CATALYST_ID.equals(descriptor.id())) {
                continue;
            }
            types.add(descriptor.id());
        }
        if (types.isEmpty()) {
            host.notice(Component.translatable(UI + "notice.no_effect"), UiPalette.TEXT_DISABLED);
            return;
        }
        UiListView<ResourceLocation> list = new UiListView<>(font, this::renderTypeRow);
        list.setItems(types);
        list.setSelectedIndex(0);
        UiModal modal = UiModal.create(font);
        modal.title(Component.translatable(UI + "pick.type_title"));
        modal.contentWidget(list, Math.min(types.size(), 8) * ROW_H + 4);
        modal.preferredWidth(200);
        modal.confirm(Component.translatable(UI + "button.confirm"), () -> addEffectOfType(list.selectedItem()));
        modal.cancel(Component.translatable(UI + "button.cancel"));
        modal.layoutCentered(host.screenWidth(), host.screenHeight());
        host.modals().push(modal);
        host.notice(Component.translatable(UI + "pick.consume_hint"), UiPalette.TEXT_DISABLED);
    }

    // 效果选择器行：选中底色 + 本地化类型名
    private void renderTypeRow(GuiGraphics graphics, Font renderFont, ResourceLocation item, int index, UiRect row, boolean selected, boolean hovered, boolean focused) {
        if (selected) {
            UiTheme.drawSelection(graphics, row);
        }
        TypeEditorDescriptor descriptor = EffectEditorRegistry.find(item);
        Component label = descriptor == null ? TypeLabels.effectLabel(item) : descriptor.label();
        String text = TextScroll.trimToWidth(font, label.getString(), Math.max(1, row.width() - 4));
        graphics.drawString(font, text, row.x() + 2, row.y() + 2, UiPalette.TEXT_PRIMARY, false);
    }

    // 新增效果：只写 type 与描述符默认值，不改结构
    private void addEffectOfType(@Nullable ResourceLocation type) {
        EditSession session = host.session();
        if (session == null || type == null) {
            return;
        }
        RuleDraft draft = session.draft();
        if (ResultStructure.totalEffectCount(draft.view()) >= ConditionLimits.MAX_EFFECTS) {
            host.notice(Component.translatable(UI + "issue.candidate_limit"), UiPalette.WARNING);
            return;
        }
        String listPath = ResultStructure.listPath(draft.view(), candidateIndex);
        JsonObject body = BuiltinEditorDefaults.effectBody(type);
        if (!apply(EditSession.OP_ADD_EFFECT, () -> draft.addEffectAt(listPath, body))) {
            return;
        }
        effectIndex = Math.max(0, draft.effectCountAt(listPath) - 1);
    }

    // 复制选中效果
    private void duplicateEffect() {
        if (blockNavigation()) {
            return;
        }
        EditSession session = host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        if (ResultStructure.totalEffectCount(draft.view()) >= ConditionLimits.MAX_EFFECTS) {
            host.notice(Component.translatable(UI + "issue.candidate_limit"), UiPalette.WARNING);
            return;
        }
        String listPath = ResultStructure.listPath(draft.view(), candidateIndex);
        List<JsonObject> effects = draft.effectsAt(listPath);
        if (effectIndex < 0 || effectIndex >= effects.size()) {
            return;
        }
        JsonObject copy = effects.get(effectIndex);
        if (!apply(EditSession.OP_ADD_EFFECT, () -> draft.addEffectAt(listPath, copy))) {
            return;
        }
        effectIndex = Math.max(0, draft.effectCountAt(listPath) - 1);
    }

    // 删除选中效果
    private void removeEffect() {
        EditSession session = host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        String listPath = ResultStructure.listPath(draft.view(), candidateIndex);
        int index = effectIndex;
        if (index < 0 || index >= draft.effectCountAt(listPath)) {
            return;
        }
        if (!apply(EditSession.OP_REMOVE_EFFECT, () -> draft.removeEffectAt(listPath, index))) {
            return;
        }
        effectIndex = Math.max(0, index - 1);
    }

    private void moveEffectBy(int delta) {
        int target = effectIndex + delta;
        if (delta == 0 || target < 0) {
            return;
        }
        EditSession session = host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        String listPath = ResultStructure.listPath(draft.view(), candidateIndex);
        if (target >= draft.effectCountAt(listPath)) {
            return;
        }
        moveEffect(effectIndex, target);
    }

    // 效果重排：一次排序一次撤销
    private void moveEffect(int from, int to) {
        EditSession session = host.session();
        if (session == null || from == to) {
            return;
        }
        RuleDraft draft = session.draft();
        String listPath = ResultStructure.listPath(draft.view(), candidateIndex);
        if (!apply(EditSession.OP_MOVE_EFFECT, () -> draft.moveInList(listPath, from, to))) {
            return;
        }
        effectIndex = to;
    }

    // ---- 行渲染 ----

    private void renderCandidateRow(GuiGraphics graphics, Font renderFont, Integer item, int index, UiRect row, boolean selected, boolean hovered, boolean focused) {
        if (selected) {
            UiTheme.drawSelection(graphics, row);
        }
        JsonObject view = currentView();
        JsonObject candidate = ResultStructure.candidateAt(view, item);
        Component title = candidate == null
                ? Component.translatable(UI + "candidate.implicit")
                : RuleNaming.candidateTitle(candidate, item + 1, nameSource);
        String text = TextScroll.trimToWidth(font, title.getString(), Math.max(1, row.width() - 4));
        graphics.drawString(font, text, row.x() + 2, row.y() + 2, focused ? UiPalette.TEXT_PRIMARY : UiPalette.TEXT_SECONDARY, false);
    }

    private void renderEffectRow(GuiGraphics graphics, Font renderFont, Integer item, int index, UiRect row, boolean selected, boolean hovered, boolean focused) {
        if (selected) {
            UiTheme.drawSelection(graphics, row);
        }
        JsonObject view = currentView();
        JsonArray array = ResultStructure.effectsAt(view, candidateIndex);
        JsonElement element = array != null && item >= 0 && item < array.size() ? array.get(item) : null;
        JsonObject effect = element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        Component title = RuleNaming.effectTitle(effect, nameSource);
        String text = (item + 1) + ". " + title.getString();
        var icon = RulePreviewIcons.action(effect);
        int inset = icon == null ? 2 : 24;
        if (icon != null) icon.render(graphics, row.x() + 2, row.y() + 2);
        String trimmed = TextScroll.trimToWidth(font, text, Math.max(1, row.width() - inset - 2));
        graphics.drawString(font, trimmed, row.x() + inset, row.y() + 2, UiPalette.TEXT_PRIMARY, false);
        if (element != null) {
            int summaryWidth = row.width() - inset - 4;
            if (summaryWidth > 12) {
                String summary = TextScroll.trimToWidth(font, NaturalSummary.effect(element).getString(), summaryWidth);
                graphics.drawString(font, summary, row.x() + inset, row.y() + 13, UiPalette.TEXT_SECONDARY, false);
            }
        }
    }

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
        return switch (page) {
            case INFO -> layoutInfo(area);
            case INPUT -> layoutInput(area);
            case TRIGGER -> layoutTrigger(area);
            case RESULTS -> layoutResults(area);
        };
    }

    private int layoutForm(@Nullable FormView form, int x, int y, int width) {
        if (form == null) {
            return y;
        }
        int height = form.preferredHeight(width);
        form.layout(x, y, width, height);
        return y + height + PAD;
    }

    private int layoutInfo(UiRect area) {
        int x = area.x();
        int y = area.y();
        autoNameRect = new UiRect(x, y, area.width(), LINE_H);
        y += LINE_H + 2;
        if (restoreNameButton != null) {
            int width = fitWidth(restoreNameButton.preferredWidth(PAD), 60, area.width());
            restoreNameButton.setBounds(x, y, width, BUTTON_H);
            y += BUTTON_H + PAD;
        }
        y = layoutForm(infoForm, x, y, area.width());
        advancedRect = new UiRect(x, y, area.width(), LINE_H * 3);
        return advancedRect.bottom();
    }

    private int layoutInput(UiRect area) {
        int x = area.x();
        int y = layoutForm(sourceForm, x, area.y(), area.width());
        costModeRect = new UiRect(x, y, area.width(), LINE_H);
        y += LINE_H + 2;
        if (sourceCostMode != null) {
            sourceCostMode.setBounds(x, y, fitWidth(sourceCostMode.preferredWidth(PAD), 100, area.width()), ROW_H);
            y += ROW_H + PAD;
        }
        if (costNote != null) {
            costNoteRect = new UiRect(x, y, area.width(), LINE_H);
            y += LINE_H + PAD;
        }
        y = layoutForm(sourceCostForm, x, y, area.width());
        if (catalystToggle != null) {
            catalystToggle.setBounds(x, y, area.width(), ROW_H);
            y += ROW_H + PAD;
        }
        if (catalystNote != null) {
            catalystNoteRect = new UiRect(x, y, area.width(), LINE_H);
            y += LINE_H + PAD;
        }
        return layoutForm(catalystForm, x, y, area.width());
    }

    private int layoutTrigger(UiRect area) {
        int x = area.x();
        int y = area.y();
        for (UiCheckBox box : triggerBoxes) {
            box.setBounds(x, y, area.width(), ROW_H);
            y += ROW_H;
        }
        y += PAD;
        triggerNoteRect = new UiRect(x, y, area.width(), LINE_H);
        y += LINE_H + PAD;
        y = layoutForm(delayForm, x, y, area.width());
        if (delayForm != null) {
            delayHintRect = new UiRect(x, y, area.width(), LINE_H);
            y += LINE_H + PAD;
        }
        return layoutForm(conditionsForm, x, y, area.width());
    }

    // 同一方案的上下文控制只对相关产出显示。
    private static boolean containsAction(@Nullable JsonArray actions, String type, @Nullable String variant) {
        if (actions == null) return false;
        for (JsonElement entry : actions) {
            if (!entry.isJsonObject()) continue;
            JsonObject action = entry.getAsJsonObject();
            if (!(TypeLabels.OWN_NAMESPACE + ":" + type).equals(RuleCostBinding.typeOf(action))) continue;
            if (variant == null || action.has("variant") && variant.equals(action.get("variant").getAsString())) return true;
        }
        return false;
    }

    // 切换子类保留公共执行参数，重置专属参数，仅写入一次撤销记录。
    private void switchProduct(String path, String variant) {
        EditSession session = host.session();
        if (session == null || blockNavigation()) return;
        JsonElement old = session.draft().getAt(path);
        if (old == null || !old.isJsonObject()) return;
        JsonObject next = old.getAsJsonObject().deepCopy();
        for (String field : List.of("item", "entity", "count", "age", "amount", "per_source_item")) next.remove(field);
        next.addProperty("variant", variant);
        if ("experience".equals(variant)) {
            next.addProperty("amount", 1);
            next.addProperty("per_source_item", false);
        } else {
            next.addProperty("count", 1);
            if ("entity".equals(variant)) next.addProperty("age", 0);
        }
        if (apply(EditSession.OP_SET_FIELD, () -> session.draft().setAt(path, next))) requestRebuild();
    }

    private static boolean isCommonActionField(EditorField field) {
        return List.of("chance", "delay_ticks", "conditions").contains(field.name());
    }

    private void enterResultStage(int stage) {
        if (blockNavigation()) return;
        resultStage = stage;
        pageScroll.setOffset(0);
        requestRebuild();
    }

    private static void show(@Nullable UiWidget widget, boolean visible) {
        if (widget instanceof UiButton button) button.setVisible(visible);
        else if (widget instanceof UiListView<?> list) list.setVisible(visible);
        else if (widget instanceof FormView form) form.setVisible(visible);
        else if (widget instanceof UiSegmentedControl segments) segments.setVisible(visible);
        else if (widget instanceof UiCheckBox box) box.setVisible(visible);
    }

    private int layoutResults(UiRect area) {
        boolean wide = area.width() >= NARROW_WIDTH;
        boolean plans = wide || resultStage == 0;
        boolean actions = wide || resultStage == 1;
        boolean detail = wide || resultStage == 2;
        show(candidateList, plans);
        candidateButtons.forEach(button -> show(button, plans));
        show(combinationControl, plans);
        show(effectList, actions);
        effectButtons.forEach(button -> show(button, actions));
        show(safeSpawnBox, detail && showAdvancedEffects); show(fillOriginBox, detail && showAdvancedEffects);
        show(effectForm, detail); show(variantControl, detail); show(advancedButton, detail);
        show(advancedEffectForm, detail && showAdvancedEffects);
        show(resultBack, !wide && resultStage > 0);
        combinationLabelRect = null; flatNoteRect = null; convertHintRect = null; productPreviewRect = null; effectNoteRect = null;
        int x = area.x(), y = area.y();
        if (!wide && resultStage > 0 && resultBack != null) {
            resultBack.setLabel(Component.translatable(UI + (resultStage == 2 ? "button.result_actions" : "button.result_plans")));
            resultBack.setBounds(x, y, area.width(), BUTTON_H); y += BUTTON_H + PAD;
        }
        if (!wide) {
            if (resultStage == 0) return layoutPlans(x, y, area.width());
            if (resultStage == 1) return layoutActions(x, y, area.width());
            return layoutActionDetail(x, y, area.width());
        }
        int navigationWidth = Math.clamp(area.width() * 36L / 100, 120, 200);
        int navY = layoutPlans(x, y, navigationWidth) + PAD;
        navY = layoutActions(x, navY, navigationWidth);
        int detailX = x + navigationWidth + PAD;
        return Math.max(navY, layoutActionDetail(detailX, y, area.right() - detailX));
    }

    private int layoutPlans(int x, int y, int width) {
        if (combinationControl != null) {
            combinationLabelRect = new UiRect(x, y, width, LINE_H); y += LINE_H;
            combinationControl.setBounds(x, y, width, ROW_H); y += ROW_H + PAD;
        }
        int listHeight = Math.clamp(candidateList == null ? 1 : candidateList.size(), 1, LIST_ROWS) * ROW_H + 2;
        if (candidateList != null) candidateList.setBounds(x, y, width, listHeight);
        y += listHeight + BUTTON_GAP;
        return y + layoutButtonGroup(candidateButtons, x, y, width);
    }

    private int layoutActions(int x, int y, int width) {
        int listHeight = Math.clamp(effectList == null ? 1 : effectList.size(), 1, LIST_ROWS) * 24 + 2;
        if (effectList != null) effectList.setBounds(x, y, width, listHeight);
        y += listHeight + BUTTON_GAP;
        return y + layoutButtonGroup(effectButtons, x, y, width);
    }

    private int layoutActionDetail(int x, int y, int width) {
        if (variantControl != null) { variantControl.setBounds(x, y, width, ROW_H); y += ROW_H + PAD; }
        if (selectedAction != null && RulePreviewIcons.action(selectedAction) != null) {
            productPreviewRect = new UiRect(x, y, Math.min(72, width), 60); y += 64;
        }
        if (currentView() != null && readonlyEffectNote(currentView()) != null) { effectNoteRect = new UiRect(x, y, width, LINE_H); y += LINE_H + PAD; }
        y = layoutForm(effectForm, x, y, width);
        if (showAdvancedEffects && safeSpawnBox != null) { safeSpawnBox.setBounds(x, y, width, ROW_H); y += ROW_H + PAD; }
        if (showAdvancedEffects && fillOriginBox != null) { fillOriginBox.setBounds(x, y, width, ROW_H); y += ROW_H + PAD; }
        if (advancedButton != null) { advancedButton.setBounds(x, y, width, BUTTON_H); y += BUTTON_H + PAD; }
        if (showAdvancedEffects) y = layoutForm(advancedEffectForm, x, y, width);
        return y;
    }

    // 控件宽度：先满足最小宽度，再压进可用宽度（可用宽度可能小于最小值，不能用 Math.clamp）
    private static int fitWidth(int preferred, int min, int available) {
        int atLeastMin = Math.max(preferred, min);
        return Math.min(atLeastMin, available);
    }

    // 按钮组横向排布（超宽换行），返回占用高度
    private int layoutButtonGroup(List<UiButton> group, int x, int y, int width) {
        if (group.isEmpty()) {
            return 0;
        }
        int cursorX = x;
        int cursorY = y;
        for (UiButton button : group) {
            int buttonWidth = fitWidth(button.preferredWidth(PAD), 24, width);
            if (cursorX > x && cursorX + buttonWidth > x + width) {
                cursorX = x;
                cursorY += BUTTON_H + BUTTON_GAP;
            }
            button.setBounds(cursorX, cursorY, buttonWidth, BUTTON_H);
            cursorX += buttonWidth + BUTTON_GAP;
        }
        return cursorY - y + BUTTON_H;
    }

    // ---- 目录面板与结构图解（P4） ----

    // 结构图解：只表达槽位与连接，节点点击导航到对应页/候选/效果
    private void openDiagram() {
        if (blockNavigation()) {
            return;
        }
        EditSession session = host.session();
        if (session == null) {
            return;
        }
        UiStructureDiagram diagram = RuleEditorP4Panels.structureDiagram(font, session.draft().view(), nameSource,
                candidateIndex, this::navigateDiagram);
        UiModal modal = UiModal.create(font);
        modal.title(Component.translatable(UI + "button.diagram"));
        modal.message(Component.translatable(UI + "diagram.note"));
        modal.preferredWidth(Math.clamp(host.screenWidth() - 16, 200, 320));
        modal.contentWidget(diagram, 160);
        modal.cancel(Component.translatable(UI + "button.cancel"));
        modal.layoutCentered(host.screenWidth(), host.screenHeight());
        host.modals().push(modal);
    }

    private void navigateDiagram(String nodeId) {
        if (nodeId == null) {
            return;
        }
        if ("source".equals(nodeId) || "catalyst".equals(nodeId)) {
            setPage(Page.INPUT);
            return;
        }
        if (nodeId.startsWith("candidate:")) {
            candidateIndex = intOr(nodeId.substring("candidate:".length()), candidateIndex);
            setPage(Page.RESULTS);
            return;
        }
        if (nodeId.startsWith("effect:")) {
            String rest = nodeId.substring("effect:".length());
            int colon = rest.lastIndexOf(':');
            if (colon > 0) {
                int candidate = indexIn(rest.substring(0, colon), RuleFields.OUTCOMES);
                if (candidate >= 0) {
                    candidateIndex = candidate;
                }
                effectIndex = intOr(rest.substring(colon + 1), effectIndex);
            }
            setPage(Page.RESULTS);
        }
    }

    private static int intOr(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    // ---- 渲染 ----

    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        if (lastArea == null || lastArea.height() == 0) {
            return;
        }
        graphics.enableScissor(lastArea.x(), lastArea.y(), lastArea.right(), lastArea.bottom());
        try {
            switch (page) {
                case INFO -> renderInfo(graphics);
                case INPUT -> renderInput(graphics);
                case TRIGGER -> renderTrigger(graphics);
                case RESULTS -> renderResults(graphics);
            }
            for (UiWidget widget : liveWidgets) {
                if (widget.isVisible()) {
                    widget.render(graphics, renderFont, mouseX, mouseY);
                }
            }
            if (dragActive) {
                drawDragPreview(graphics);
            }
        } finally {
            graphics.disableScissor();
        }
        pageScroll.renderScrollbar(graphics, UiTheme.secondaryStyle());
    }

    private void renderInfo(GuiGraphics graphics) {
        JsonObject view = currentView();
        if (view != null) {
            Component title = RuleNaming.ruleTitle(view, nameSource);
            drawText(graphics, autoNameRect, Component.translatable(UI + "info.auto_name", title), UiPalette.TEXT_SECONDARY);
        }
        if (advancedRect != null && view != null) {
            drawText(graphics, new UiRect(advancedRect.x(), advancedRect.y(), advancedRect.width(), LINE_H),
                    Component.translatable(UI + "info.advanced"), UiPalette.TEXT_DISABLED);
            drawText(graphics, new UiRect(advancedRect.x(), advancedRect.y() + LINE_H, advancedRect.width(), LINE_H),
                    Component.literal("id: " + stringOf(view)), UiPalette.TEXT_DISABLED);
            drawText(graphics, new UiRect(advancedRect.x(), advancedRect.y() + LINE_H * 2, advancedRect.width(), LINE_H),
                    Component.translatable(UI + "info.schema_version").append(": ").append(schemaVersionOf(view)), UiPalette.TEXT_DISABLED);
        }
    }

    private void renderInput(GuiGraphics graphics) {
        drawText(graphics, costModeRect, Component.translatable(UI + "rule.source_cost"), UiPalette.TEXT_SECONDARY);
        if (costNote != null) {
            drawText(graphics, costNoteRect, costNote, UiPalette.TEXT_DISABLED);
        }
        if (catalystNote != null) {
            drawText(graphics, catalystNoteRect, catalystNote, UiPalette.TEXT_DISABLED);
        }
    }

    private void renderTrigger(GuiGraphics graphics) {
        drawText(graphics, triggerNoteRect, Component.translatable(UI + "trigger.note"), UiPalette.TEXT_DISABLED);
        drawText(graphics, delayHintRect, Component.translatable(UI + "trigger.delay_hint"), UiPalette.TEXT_DISABLED);
    }

    private void renderResults(GuiGraphics graphics) {
        if (productPreviewRect != null && selectedAction != null && lastArea != null) {
            UiTheme.drawInset(graphics, productPreviewRect);
            var icon = RulePreviewIcons.action(selectedAction);
            if (icon instanceof com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon.Rendered rendered) {
                rendered.painter().render(graphics, productPreviewRect);
            } else if (icon != null) icon.render(graphics, productPreviewRect.x() + (productPreviewRect.width() - 16) / 2, productPreviewRect.y() + 20);
            if (selectedAction.has("entity")) {
                String id = selectedAction.get("entity").getAsString();
                int age = selectedAction.has("age") ? selectedAction.get("age").getAsInt() : 0;
                if (EntityPreviewIcons.unavailable(id, age)) drawText(graphics,
                        new UiRect(productPreviewRect.right() + PAD, productPreviewRect.y(), Math.max(0, lastArea.right() - productPreviewRect.right() - PAD), 20),
                        Component.translatable(UI + "preview.unavailable"), UiPalette.TEXT_SECONDARY);
            }
        }
        drawText(graphics, combinationLabelRect, Component.translatable(UI + "rule.combination"), UiPalette.TEXT_SECONDARY);
        JsonObject view = currentView();
        if (view == null) {
            return;
        }
        if (!ResultStructure.hasOutcomes(view)) {
            drawText(graphics, flatNoteRect, Component.translatable(UI + "candidate.flat_note"), UiPalette.TEXT_DISABLED);
            drawText(graphics, convertHintRect, Component.translatable(UI + "structure.convert_hint"), UiPalette.TEXT_DISABLED);
        }
        Component readonlyNote = readonlyEffectNote(view);
        if (readonlyNote != null) {
            drawText(graphics, effectNoteRect, readonlyNote, UiPalette.WARNING);
        }
    }

    // 未注册/无法识别的效果类型：只读保留并说明，不猜私有字段、不改写 JSON（主计划 §4 结果）
    private @Nullable Component readonlyEffectNote(JsonObject view) {
        JsonArray effects = ResultStructure.effectsAt(view, candidateIndex);
        if (effects == null || effectIndex < 0 || effectIndex >= effects.size()) {
            return null;
        }
        String type = RuleCostBinding.typeOf(effects.get(effectIndex));
        ResourceLocation typeId = type == null ? null : ResourceLocation.tryParse(type);
        if (typeId == null) {
            return Component.translatable(UI + "effect.readonly_note_unknown");
        }
        if (EffectEditorRegistry.find(typeId) != null) {
            return null;
        }
        return Component.translatable(UI + "effect.readonly_note", typeId.toString());
    }

    // 拖动预览：高亮目标行
    private void drawDragPreview(GuiGraphics graphics) {
        UiListView<?> list = dragKind == DragKind.CANDIDATE ? candidateList : effectList;
        UiRect rect = list == null ? null : list.visibleRowBounds(dragTo);
        if (rect != null) {
            UiTheme.drawSelection(graphics, rect);
        }
    }

    private void drawText(GuiGraphics graphics, @Nullable UiRect rect, Component text, int color) {
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
        boolean changed = false;
        for (FormView form : liveForms) {
            if (form.isVisible() && form.applyToDraft()) {
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
        JsonObject view = currentView();
        if (view != null) {
            addPageIssues(view, issues);
        }
        return issues;
    }

    // 页面级校验：只拦本地可证非法的取值，未知的第三方取值原样保留、交给服务端
    private static void addPageIssues(JsonObject rule, List<FormIssue> issues) {
        if (RuleTriggers.isExplicitlyEmpty(rule)) {
            Component message = Component.translatable(UI + "issue.trigger_empty");
            issues.add(FormIssue.error(RuleFields.TRIGGERS, Component.translatable(UI + "rule.triggers"), message));
        }
        int candidates = ResultStructure.candidateCount(rule);
        int effects = ResultStructure.totalEffectCount(rule);
        Component limit = Component.translatable(UI + "issue.candidate_limit");
        if (candidates > ConditionLimits.MAX_EFFECTS) {
            issues.add(FormIssue.error(RuleFields.OUTCOMES, Component.translatable(UI + "candidate.section"), limit));
        } else if (effects > ConditionLimits.MAX_EFFECTS) {
            issues.add(FormIssue.error(RuleFields.EFFECTS, Component.translatable(UI + "candidate.section"), limit));
        }
        if (RuleEditorP4Panels.climateAllUnbounded(rule)) {
            Component message = Component.translatable(UI + "issue.climate_empty");
            issues.add(FormIssue.error(RuleFields.CONDITIONS, Component.translatable(UI + "rule.conditions"), message));
        }
        JsonElement combination = rule.get(RuleFields.COMBINATION);
        if (combination != null && combination.isJsonPrimitive() && combination.getAsJsonPrimitive().isString()
                && !isKnownCombination(combination.getAsString())) {
            Component message = Component.translatable(UI + "issue.combination_invalid");
            issues.add(FormIssue.error(RuleFields.COMBINATION, Component.translatable(UI + "rule.combination"), message));
        }
    }

    // 组合模式已知取值：组合控件只会写入这两种
    private static boolean isKnownCombination(String raw) {
        if (raw == null) {
            return false;
        }
        String normalized = raw.trim();
        return CombinationMode.ROUND_ROBIN.key().equals(normalized) || CombinationMode.PRIORITY.key().equals(normalized);
    }

    // 结束进行中的数值/滑杆交互：提交有效预览、回退无效预览
    // （FormView.endInteractions 是包级方法，宿主只能走 onFocusScopeChanged 这个公开入口）
    public void endInteractions() {
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

    private void applyEnabled() {
        JsonObject view = currentView();
        if (sourceCostMode != null) {
            sourceCostMode.setEnabled(enabled && RuleCostBinding.consumeSource(view) == null);
        }
        if (catalystToggle != null) {
            catalystToggle.setEnabled(enabled && RuleCostBinding.consumeCatalyst(view) == null);
        }
        triggerBoxes.forEach(box -> box.setEnabled(enabled));
        if (variantControl != null) variantControl.setEnabled(enabled);
        if (combinationControl != null) {
            combinationControl.setEnabled(enabled && ResultStructure.hasOutcomes(view));
        }
        if (safeSpawnBox != null) {
            safeSpawnBox.setEnabled(enabled);
        }
        if (fillOriginBox != null) {
            fillOriginBox.setEnabled(enabled);
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
            entry.panel().endInteractions();
            return true;
        });
    }

    public @Nullable Component tooltipAt(double mouseX, double mouseY) {
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
            resultStage = path.contains("[") ? 2 : 1;
            showAdvancedEffects = path.contains("chance") || path.contains("delay_ticks") || path.contains("conditions");
            int candidate = indexIn(path, RuleFields.OUTCOMES);
            if (candidate >= 0) {
                candidateIndex = candidate;
            }
            int effect = indexIn(path, RuleFields.EFFECTS);
            if (effect >= 0) {
                effectIndex = effect;
            }
            rebuild();
            if (effectForm != null) {
                UiFocusTarget target = effectForm.revealPath(path);
                return target != null ? target : advancedEffectForm == null ? null : advancedEffectForm.revealPath(path);
            }
            return null;
        }
        if (path.startsWith(RuleFields.CONDITIONS) || path.startsWith(RuleFields.TERMS)) {
            page = Page.TRIGGER;
            rebuild();
            return conditionsForm == null ? null : conditionsForm.revealPath(path);
        }
        if (path.startsWith(RuleFields.TRIGGERS) || path.startsWith(RuleFields.TRIGGER_AFTER_SECONDS)) {
            page = Page.TRIGGER;
            rebuild();
            // 触发来源问题直接聚焦第一个勾选框；延迟秒数问题落到延迟表单
            if (path.startsWith(RuleFields.TRIGGERS) && !triggerBoxes.isEmpty()) {
                return triggerBoxes.getFirst();
            }
            return delayForm == null ? null : delayForm.revealPath(path);
        }
        if (path.startsWith(RuleFields.COMBINATION)) {
            page = Page.RESULTS;
            rebuild();
            return combinationControl;
        }
        if (path.startsWith(RuleFields.SOURCE) || path.startsWith(RuleFields.CATALYST_COST)) {
            page = Page.INPUT;
            rebuild();
            FormView target = path.startsWith(RuleFields.CATALYST_COST) ? catalystForm
                    : path.startsWith(RuleFields.SOURCE_COST) ? sourceCostForm : sourceForm;
            return target == null ? null : target.revealPath(path);
        }
        page = Page.INFO;
        rebuild();
        return infoForm == null ? null : infoForm.revealPath(path);
    }

    // ---- 输入事件 ----

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!pageScroll.contains(mouseX, mouseY)) {
            return false;
        }
        if (pageScroll.mousePressed(mouseX, mouseY, button)) {
            return true;
        }
        boolean handled = false;
        for (UiWidget widget : liveWidgets) {
            if (widget.isVisible() && widget.mouseClicked(mouseX, mouseY, button)) {
                handled = true;
                break;
            }
        }
        if (lastArea != null) {
            layout(lastArea);
        }
        for (UiFocusTarget target : focusTargets()) {
            if (target instanceof UiConditionTreeEditor tree && tree.isPickerOpen()) {
                host.focus().focusOn(tree);
                ensureVisible(tree);
                break;
            }
        }
        if (button == 0 && !pendingRebuild) {
            captureDrag(mouseX, mouseY);
        }
        flushRebuild();
        return handled;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        pageScroll.mouseReleased();
        if (dragKind != DragKind.NONE) {
            DragKind kind = dragKind;
            int from = dragFrom;
            int to = dragTo;
            boolean active = dragActive;
            dragKind = DragKind.NONE;
            dragActive = false;
            dragFrom = -1;
            dragTo = -1;
            if (active && from >= 0 && to >= 0 && from != to) {
                if (kind == DragKind.CANDIDATE) {
                    moveCandidate(from, to);
                } else {
                    moveEffect(from, to);
                }
                flushRebuild();
                return true;
            }
        }
        boolean handled = false;
        for (UiWidget widget : liveWidgets) {
            if (widget.isVisible() && widget.mouseReleased(mouseX, mouseY, button)) {
                handled = true;
                break;
            }
        }
        flushRebuild();
        return handled;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (pageScroll.isDragging()) {
            pageScroll.mouseDragged(mouseY);
            if (lastArea != null) {
                layout(lastArea);
            }
            return true;
        }
        if (dragKind != DragKind.NONE) {
            if (Math.abs(mouseY - dragStartY) >= 3) {
                dragActive = true;
                dragTo = rowIndexAt(dragKind, mouseY);
            }
            if (dragActive) {
                return true;
            }
        }
        for (UiWidget widget : liveWidgets) {
            if (widget.isVisible() && widget.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!pageScroll.contains(mouseX, mouseY)) {
            return false;
        }
        for (UiWidget widget : liveWidgets) {
            if (widget.isVisible() && widget.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        boolean changed = pageScroll.scrollBy(scrollY);
        if (changed && lastArea != null) {
            layout(lastArea);
        }
        return changed;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (UiWidget widget : liveWidgets) {
            if (widget instanceof UiFocusTarget target && !target.isFocused()) {
                continue;
            }
            if (widget.isVisible() && widget.keyPressed(keyCode, scanCode, modifiers)) {
                flushRebuild();
                return true;
            }
        }
        flushRebuild();
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        for (UiWidget widget : liveWidgets) {
            if (widget.isVisible() && widget.charTyped(codePoint, modifiers)) {
                flushRebuild();
                return true;
            }
        }
        flushRebuild();
        return false;
    }

    // ---- 内部工具 ----

    // 记录潜在拖动源（点击列表行后开始跟踪）
    private void captureDrag(double mouseX, double mouseY) {
        dragKind = DragKind.NONE;
        dragFrom = -1;
        dragTo = -1;
        dragActive = false;
        UiListView<?> list = null;
        DragKind kind = DragKind.NONE;
        if (candidateList != null && candidateList.isVisible() && candidateList.bounds().contains(mouseX, mouseY)) {
            list = candidateList;
            kind = DragKind.CANDIDATE;
        } else if (effectList != null && effectList.isVisible() && effectList.bounds().contains(mouseX, mouseY)) {
            list = effectList;
            kind = DragKind.EFFECT;
        }
        if (list == null || list.itemIndexAt(mouseX, mouseY) < 0) {
            return;
        }
        dragKind = kind;
        dragFrom = list.selectedIndex();
        dragStartY = mouseY;
    }

    // 指针位置对应的行下标
    private int rowIndexAt(DragKind kind, double mouseY) {
        UiListView<?> list = kind == DragKind.CANDIDATE ? candidateList : effectList;
        if (list == null || list.size() == 0) {
            return -1;
        }
        return list.dragIndexAt(mouseY);
    }

    // 切换或结构修改前先校验文本缓冲，再提交旧表单，避免重建丢失输入。
    public boolean blockNavigation() {
        if (!enabled) {
            return false;
        }
        for (FormView form : liveForms) {
            FormIssue issue = form.pendingInputIssue();
            if (issue != null) {
                host.notice(Component.translatable(UI + "notice.issue", issue.label(), issue.message()), UiPalette.DANGER);
                UiFocusTarget target = form.revealPath(issue.path());
                if (target != null) {
                    host.focus().focusOn(target);
                    ensureVisible(target);
                }
                return true;
            }
        }
        endInteractions();
        if (enabled && applyToDraft()) {
            host.onDraftChanged();
        }
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

    private void requestRebuild() {
        pendingRebuild = true;
    }

    private void flushRebuild() {
        if (pendingRebuild) {
            pendingRebuild = false;
            rebuild();
        }
    }

    private boolean apply(String opKey, Runnable change) {
        EditSession session = host.session();
        if (session == null || host.rejectWhenFrozen() || blockNavigation()) {
            restoreSwitches();
            return false;
        }
        session.apply(opKey, change);
        host.onDraftChanged();
        requestRebuild();
        return true;
    }

    // 无效文本阻止结构操作时，仅恢复开关显示，不重建输入控件。
    private void restoreSwitches() {
        JsonObject view = currentView();
        if (view == null) {
            return;
        }
        if (sourceCostMode != null) {
            sourceCostMode.setSelected(view.has(RuleFields.SOURCE_COST) || RuleCostBinding.consumeSource(view) != null
                    ? COST_EXPLICIT : COST_DERIVED);
        }
        if (catalystToggle != null) {
            catalystToggle.setChecked(view.has(RuleFields.CATALYST_COST) || RuleCostBinding.consumeCatalyst(view) != null);
        }
        List<String> triggers = RuleTriggers.effective(view);
        for (int index = 0; index < triggerBoxes.size(); index++) {
            triggerBoxes.get(index).setChecked(triggers.contains(RuleTriggers.known().get(index)));
        }
        if (combinationControl != null) {
            combinationControl.setSelected(combinationOf(view));
        }
        JsonObject candidate = ResultStructure.candidateAt(view, candidateIndex);
        if (safeSpawnBox != null) {
            safeSpawnBox.setChecked(boolOf(candidate, RuleFields.SAFE_SPAWN, false));
        }
        if (fillOriginBox != null) {
            fillOriginBox.setChecked(boolOf(candidate, RuleFields.FILL_ORIGIN, true));
        }
    }

    private void addCandidateButton(String labelKey, UiAction action) {
        UiButton button = new UiButton(font, Component.translatable(UI + labelKey), UiButtonVariant.SECONDARY, action);
        candidateButtons.add(button);
        liveWidgets.add(button);
    }

    private void addEffectButton(String labelKey, UiAction action) {
        UiButton button = new UiButton(font, Component.translatable(UI + labelKey), UiButtonVariant.SECONDARY, action);
        effectButtons.add(button);
        liveWidgets.add(button);
    }

    private @Nullable JsonObject currentView() {
        EditSession session = host.session();
        return session == null ? null : session.draft().view();
    }

    // 路径中 name[index] 的下标
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

    private static boolean boolOf(@Nullable JsonObject owner, String name, boolean fallback) {
        if (owner == null) {
            return fallback;
        }
        JsonElement element = owner.get(name);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            return fallback;
        }
        return element.getAsBoolean();
    }

    // 读取 id 字符串字段（缺失或非字符串都按空串处理）
    private static String stringOf(@Nullable JsonObject owner) {
        if (owner == null) {
            return "";
        }
        JsonElement element = owner.get(RuleFields.ID);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return "";
        }
        return element.getAsString();
    }

    private static Component schemaVersionOf(JsonObject view) {
        JsonElement element = view.get(RuleFields.SCHEMA_VERSION);
        if (element == null || !element.isJsonPrimitive()) {
            return Component.literal("-");
        }
        return Component.literal(element.getAsString());
    }
}
