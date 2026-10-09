package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDefaults;
import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDescriptors;
import com.meteorite.itemdespawntowhat.client.edit.ClientTypeRegistries;
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
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ConditionTreeOverlay;
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
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.model.CatalystCost;
import com.meteorite.itemdespawntowhat.core.model.CombinationMode;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionLimits;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import com.meteorite.itemdespawntowhat.core.runtime.CatalystThresholdProjection;
import com.meteorite.itemdespawntowhat.core.type.condition.CatalystPresentCondition;
import com.meteorite.itemdespawntowhat.core.type.condition.FluidPresentCondition;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
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
    private static final int TREE_OVERLAY_MARGIN = 8;
    private static final int ROW_H = 20;
    private static final int BUTTON_H = 20;
    private static final int BUTTON_GAP = 4;
    private static final int CARD_PAD = 8;
    private static final int CARD_GAP = 8;
    private static final int CARD_HEADER_H = 20;
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
    private static final ResourceLocation CONSUME_FLUID_ID = ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, "consume_fluid");

    // 源成本模式

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
    private final List<PageCard> cards = new ArrayList<>();

    /*** 页面分区的绘制数据，与控件共用滚动坐标。 */
    private record PageCard(UiRect bounds, @Nullable Component title) { }
    private int candidateIndex;
    private int effectIndex;

    // 本次按下命中的页面控件：释放只派发给它，避免无关控件吞掉释放
    private @Nullable UiWidget pressedWidget;

    // 存在条件的类型 id：条件叶是催化剂 / 流体类型的唯一事实源
    private static final ResourceLocation CATALYST_PRESENT_ID = CatalystPresentCondition.ID;
    private static final ResourceLocation FLUID_PRESENT_ID = FluidPresentCondition.ID;

    // 输入页的存在条件开关：勾选状态由草稿规则级条件里是否存在该类型的叶决定
    private @Nullable UiCheckBox catalystPresenceToggle;
    private @Nullable UiCheckBox fluidPresenceToggle;
    // 存在条件的类型表单行（含标题行矩形），逐项按确切叶路径绑定
    private final List<PresenceRow> presenceRows = new ArrayList<>();

    // 一个存在条件叶在草稿中的位置与作用域：输入页与触发页共用同一份扫描结果
    private record PresenceLeaf(String path, String conditionPath, ResourceLocation type, int ordinal,
                                Component scopeLabel, @Nullable String effectPath, int candidateIndex, int effectIndex) {

        // 催化剂叶的类型字段是物品引用列表，流体叶是单个流体引用
        boolean catalyst() {
            return CATALYST_PRESENT_ID.equals(type);
        }

        // 类型字段名
        String fieldName() {
            return catalyst() ? RuleFields.CATALYST_ITEMS : RuleFields.FLUIDS;
        }

        // 类型字段的草稿路径
        String fieldPath() {
            return conditionPath + "." + fieldName();
        }

        // 规则级条件叶（非动作局部）
        boolean ruleLevel() {
            return effectPath == null;
        }
    }

    // 输入页的一行存在条件：叶、物品与门槛表单及标题行矩形
    private static final class PresenceRow {
        private final PresenceLeaf leaf;
        private final FormView form;
        private @Nullable UiRect headerRect;

        PresenceRow(PresenceLeaf leaf, FormView form) {
            this.leaf = leaf;
            this.form = form;
        }
    }

    // 基本信息
    private @Nullable FormView infoForm;
    private @Nullable UiButton restoreNameButton;
    // 基本页第一行的只读规则 ID
    private @Nullable UiRect infoIdRect;

    // 输入与成本
    private @Nullable FormView sourceForm;
    // 源物品的目录选择入口（图标网格面板）
    private @Nullable UiButton sourceCostButton;
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
    // 单方案（顶层 effects）时结果页直达动作，不渲染方案选择层
    private boolean singlePlan;
    private int resultStage;
    private @Nullable UiRect productPreviewRect;
    private @Nullable JsonObject selectedAction;
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
        infoIdRect = null;
        sourceForm = null;
        sourceCostButton = null;
        sourceCostForm = null;
        catalystForm = null;
        costModeRect = null;
        costNoteRect = null;
        costNote = null;
        catalystToggle = null;
        catalystNoteRect = null;
        catalystNote = null;
        catalystPresenceToggle = null;
        fluidPresenceToggle = null;
        presenceRows.clear();
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
        effectNoteRect = null;
        singlePlan = false;
        dragKind = DragKind.NONE;
        dragActive = false;
        dragFrom = -1;
        dragTo = -1;
    }

    // 表单工厂
    private FormView newForm(EditSession session, String basePath, TypeEditorDescriptor descriptor) {
        FormView form = new FormView(font, session, basePath);
        if (page == Page.INPUT) form.useCatalogSelections();
        form.setConditionSupport(pageConditionSupport(form));
        form.setSuggestionProvider(host.suggestions());
        form.setOnChanged(host::onDraftChanged);
        if (page == Page.INPUT) form.setOnChanged(() -> {
            form.applyToDraft();
            if (form == sourceForm) {
                updateBlacklistVisibility();
                refreshQuantityReferences();
            }
            if (form == catalystForm) {
                for (PresenceRow row : presenceRows) if (row.leaf.catalyst()) row.form.reload();
            }
            host.onDraftChanged();
        });
        // 输入被长度上限拒绝时立刻显示一条可读提示（字段名：问题）
        form.setOnRejectedNotice(message -> host.notice(message, UiPalette.DANGER));
        form.setCatalogOpener((field, tags, onPicked) -> {
            if (page == Page.INPUT && RuleFields.SOURCE.equals(basePath) && RuleFields.SOURCE_EXCLUDE.equals(field.name())) {
                if (!host.rejectWhenFrozen()) host.modals().push(SourceBlacklistPicker.modal(font, host.workspace(), session.draft().view(),
                        host.screenWidth(), host.screenHeight(), onPicked));
                return;
            }
            RuleCatalogType type = tags ? RuleCatalogType.TAG : RuleEditorP4Panels.catalogTypeOf(field);
            if (type == null || host.rejectWhenFrozen()) return;
            boolean multi = field.type() == com.meteorite.itemdespawntowhat.client.edit.EditorFieldType.TAG_LIST
                    || field.type() == com.meteorite.itemdespawntowhat.client.edit.EditorFieldType.RL_LIST;
            host.modals().push(RuleEditorP4Panels.catalogModal(font, host.workspace(), type, multi,
                    Component.translatable(field.labelKey()), host.screenWidth(), host.screenHeight(), onPicked, field, page == Page.INPUT));
        });
        form.setDescriptor(descriptor);
        form.reload();
        form.useCardSpacing();
        configureQuantityFields(form, session, basePath, descriptor);
        return form;
    }

    private void configureQuantityFields(FormView form, EditSession session, String basePath,
                                         TypeEditorDescriptor descriptor) {
        JsonElement element = basePath.isEmpty() ? session.draft().view() : session.draft().getAt(basePath);
        JsonObject owner = element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        for (EditorField field : descriptor.fields()) {
            if (field.type() != com.meteorite.itemdespawntowhat.client.edit.EditorFieldType.ITEM_COUNTS) continue;
            boolean source = RuleFields.SOURCE_COST.equals(field.name())
                    || CONSUME_SOURCE_ID.toString().equals(stringField(owner, RuleFields.TYPE));
            String path = basePath.isEmpty() ? field.name() : basePath + "." + field.name();
            List<String> references = source ? stringList(session.draft().getAt(RuleFields.SOURCE + "." + RuleFields.SOURCE_ITEMS))
                    : stringList(owner.get(RuleFields.CATALYST_ITEMS));
            form.setItemCountReferences(path, references);
            form.setRowDefaultValue(path, () -> quantityDefaults(session, basePath, source));
        }
    }

    private JsonObject quantityDefaults(EditSession session, String basePath, boolean source) {
        JsonElement raw = basePath.isEmpty() ? session.draft().view() : session.draft().getAt(basePath);
        JsonObject owner = raw instanceof JsonObject object ? object : new JsonObject();
        List<String> references = source ? stringList(session.draft().getAt(RuleFields.SOURCE + "." + RuleFields.SOURCE_ITEMS))
                : stringList(owner.get(RuleFields.CATALYST_ITEMS));
        JsonObject values = defaultQuantities(owner, references);
        if (!CATALYST_PRESENT_ID.toString().equals(stringField(owner, RuleFields.TYPE)) || owner.has(RuleFields.CATALYST_COUNT)) return values;
        Rule rule = RuleCodecs.codec(ClientTypeRegistries.effects(), ClientTypeRegistries.conditions())
                .parse(JsonOps.INSTANCE, session.draft().view()).result().orElse(null);
        if (rule != null) {
            List<TaggedId> items = taggedItems(basePath);
            for (TaggedId item : items) values.addProperty(item.serialized(), CatalystThresholdProjection.resolveThreshold(
                    rule, items, scopeEffectOf(rule, basePath), item));
        }
        return values;
    }

    private static JsonObject defaultQuantities(JsonObject owner, List<String> references) {
        JsonObject values = new JsonObject();
        JsonElement count = owner.get(RuleFields.CATALYST_COUNT);
        int fallback = count != null && count.isJsonPrimitive() && count.getAsJsonPrimitive().isNumber()
                ? count.getAsInt() : CatalystCost.DEFAULT_COUNT;
        references.forEach(reference -> values.addProperty(reference, fallback));
        return values;
    }

    private void refreshQuantityReferences() {
        EditSession session = host.session();
        if (session == null) return;
        JsonObject rule = session.draft().view();
        if (sourceCostForm != null) {
            RuleCostBinding.Ref ref = RuleCostBinding.consumeSource(rule);
            String path = ref == null ? RuleFields.SOURCE_COST : ref.path() + "." + RuleFields.ITEM_COUNTS;
            sourceCostForm.setItemCountReferences(path,
                    stringList(session.draft().getAt(RuleFields.SOURCE + "." + RuleFields.SOURCE_ITEMS)));
            sourceCostForm.reload();
        }
        for (PresenceRow row : presenceRows) if (row.leaf.catalyst()) {
            row.form.setItemCountReferences(row.leaf.conditionPath() + "." + RuleFields.ITEM_COUNTS,
                    stringList(session.draft().getAt(row.leaf.fieldPath())));
            row.form.reload();
        }
        if (catalystForm != null) {
            RuleCostBinding.Ref ref = RuleCostBinding.consumeCatalyst(rule);
            String base = ref == null ? RuleFields.CATALYST_COST : ref.path();
            catalystForm.setItemCountReferences(base + "." + RuleFields.ITEM_COUNTS,
                    stringList(session.draft().getAt(base + "." + RuleFields.CATALYST_ITEMS)));
            catalystForm.reload();
        }
        host.onControlsChanged();
    }

    // ---- 基本信息 ----

    // 基本页优先级与其它单行控件使用同一紧凑高度。
    private static final int PRIORITY_ROW_HEIGHT = 18;

    private void buildInfo(EditSession session) {
        JsonObject rule = session.draft().view();
        // 自上而下：只读规则 ID、显示名与恢复按钮、启用、加高的优先级控件、多行备注；
        // 页头只保留当前实际生效名称，页内不再重复说明文案
        TypeEditorDescriptor descriptor = TypeEditorDescriptor.of(INFO_PAGE, Component.translatable(UI + "tab.info"), List.of(
                EditorField.optionalText(RuleFields.DISPLAY_NAME, UI + "rule.display_name").withHint(UI + "rule.display_name_hint"),
                EditorField.bool(RuleFields.ENABLED, UI + "rule.enabled"),
                EditorField.integerSlider(RuleFields.PRIORITY, UI + "rule.priority", Integer.MIN_VALUE, Integer.MAX_VALUE, -100, 100).optional().withHint(UI + "rule.priority.hint"),
                EditorField.longText(RuleFields.NOTES, UI + "rule.notes").withHint(UI + "rule.notes_hint")));
        infoForm = newForm(session, "", descriptor);
        infoForm.useCompactSpacing();
        // 滑条与数字输入同高，备注保留三行可见高度。
        infoForm.setRowHeight(RuleFields.PRIORITY, PRIORITY_ROW_HEIGHT);
        infoForm.setRowHeight(RuleFields.NOTES, 36);
        JsonObject automatic = rule.deepCopy();
        automatic.remove(RuleFields.DISPLAY_NAME);
        infoForm.setRowDefaultText(RuleFields.DISPLAY_NAME, RuleNaming.ruleTitle(automatic, nameSource));
        infoForm.suppressRowTooltip(RuleFields.ENABLED);
        restoreNameButton = new UiButton(font, Component.translatable(UI + "button.restore_name"), UiButtonVariant.SECONDARY, this::restoreAutoName);
        restoreNameButton.setEnabled(canRestoreName(rule));
        // 恢复按钮挂在显示名行尾：与它作用的字段同排，且随表单一起禁用
        infoForm.setRowAction(RuleFields.DISPLAY_NAME, restoreNameButton);
        liveForms.add(infoForm);
        liveWidgets.add(infoForm);
    }

    // 自定义文本尚未提交时也允许恢复默认，默认展示基线仍不算自定义名称。
    private boolean canRestoreName(@Nullable JsonObject rule) {
        if (rule != null && rule.has(RuleFields.DISPLAY_NAME)) return true;
        JsonElement pending = infoForm == null ? null : infoForm.pendingValue(RuleFields.DISPLAY_NAME);
        return pending != null && pending.isJsonPrimitive() && pending.getAsJsonPrimitive().isString() && !pending.getAsString().isBlank();
    }

    // 恢复默认：删除 display_name、刷新实际默认名称并清空旧控件焦点；撤销可还原自定义名称。
    private void restoreAutoName() {
        EditSession session = host.session();
        if (session == null) {
            return;
        }
        JsonObject view = session.draft().view();
        if (!canRestoreName(view)) {
            return;
        }
        RuleDraft draft = session.draft();
        // 当前缓冲值可能尚未落盘；与删除放在同一个编辑步内，撤销一次即回到操作前的显示名
        JsonElement pending = infoForm == null ? null : infoForm.pendingValue(RuleFields.DISPLAY_NAME);
        if (!apply(EditSession.OP_SET_FIELD, () -> {
            if (pending != null) {
                draft.setAt(RuleFields.DISPLAY_NAME, pending.deepCopy());
            }
            draft.remove(RuleFields.DISPLAY_NAME);
        })) {
            return;
        }
        // 旧控件即将被重建，显式清空焦点，不让焦点轮廓停在已卸载的输入框上
        host.focus().clearFocus();
        host.notice(Component.translatable(UI + "notice.name_restored"), UiPalette.TEXT_SECONDARY);
    }

    // ---- 输入与成本 ----

    // 无标签时隐藏黑名单入口；保留已有 JSON，避免仅浏览界面改写旧规则。
    private void updateBlacklistVisibility() {
        if (sourceForm != null) sourceForm.setRowVisible(RuleFields.SOURCE + "." + RuleFields.SOURCE_EXCLUDE,
                !SourceBlacklistPicker.sourceTags(currentView()).isEmpty());
    }

    private void buildInput(EditSession session) {
        JsonObject rule = session.draft().view();
        sourceForm = newForm(session, RuleFields.SOURCE, BuiltinEditorDescriptors.sourceDescriptor());
        sourceForm.hideRowLabel(RuleFields.SOURCE + ".items");
        sourceForm.setOnFieldWritten((path, before) -> {
            if (!path.equals(RuleFields.SOURCE + "." + RuleFields.SOURCE_ITEMS)) return;
            List<String> references = stringList(session.draft().getAt(path));
            JsonObject body = session.draft().view();
            if (body.has(RuleFields.SOURCE_COST)) reconcileQuantities(body, RuleFields.SOURCE_COST, references, CatalystCost.DEFAULT_COUNT);
            RuleCostBinding.Ref ref = RuleCostBinding.consumeSource(body);
            if (ref != null && session.draft().getAt(ref.path()) instanceof JsonObject effect) {
                reconcileQuantities(effect, RuleFields.ITEM_COUNTS, references, countField(effect));
            }
        });
        updateBlacklistVisibility();
        liveForms.add(sourceForm);
        liveWidgets.add(sourceForm);

        RuleCostBinding.Ref sourceRef = RuleCostBinding.consumeSource(rule);
        sourceCostButton = new UiButton(font, Component.translatable(UI + "cost.mode.custom"),
                UiButtonVariant.SECONDARY, this::specifySourceCost);
        if (sourceRef != null) {
            sourceCostButton.setEnabled(false);
            costNote = Component.translatable(UI + "cost.mode.from_effect");
            sourceCostForm = newForm(session, sourceRef.path(), ownFieldsOnly(EffectEditorRegistry.descriptorFor(CONSUME_SOURCE_ID)));
        } else {
            boolean custom = rule.has(RuleFields.SOURCE_COST);
            sourceCostButton.setVisible(!custom);
            sourceCostForm = custom ? newForm(session, "", BuiltinEditorDescriptors.sourceCostDescriptor()) : null;
            if (!custom) {
                boolean consumesOther = RuleCostBinding.consumeCatalyst(rule) != null
                        || RuleCostBinding.find(rule, TypeLabels.OWN_NAMESPACE + ":consume_fluid") != null;
                costNote = Component.translatable(UI + (consumesOther ? "cost.auto.zero" : "cost.auto.one"));
            }
        }
        liveWidgets.add(sourceCostButton);
        if (sourceCostForm != null) {
            liveForms.add(sourceCostForm);
            liveWidgets.add(sourceCostForm);
        }

        buildPresenceSection(session, CATALYST_PRESENT_ID, UI + "rule.presence.add_catalyst");
        RuleCostBinding.Ref catalystRef = RuleCostBinding.consumeCatalyst(rule);
        boolean catalystOn = rule.has(RuleFields.CATALYST_COST);
        boolean catalystEnabled = hasRuleLevelPresence(rule, CATALYST_PRESENT_ID);
        TypeEditorDescriptor catalystDescriptor = BuiltinEditorDescriptors.catalystCostDescriptor();
        // 已有独立消耗规则没有存在叶时，仍保留其物品编辑入口。
        if (!catalystEnabled) {
            List<EditorField> fields = new ArrayList<>();
            fields.add(EditorField.tagList(RuleFields.CATALYST_ITEMS, UI + "rule.catalyst_cost.items",
                    "minecraft:item").asRequired());
            fields.addAll(catalystDescriptor.fields());
            catalystDescriptor = TypeEditorDescriptor.of(catalystDescriptor.id(), catalystDescriptor.label(), fields);
        }
        catalystToggle = new UiCheckBox(font, Component.translatable(UI + "rule.catalyst_consume"), catalystRef != null || catalystOn);
        catalystToggle.setVisible(catalystEnabled || catalystRef != null || catalystOn);
        catalystToggle.setOnChanged(this::onCatalystToggle);
        if (catalystRef != null) {
            catalystNote = Component.translatable(UI + "cost.from_effect_path", Component.literal(catalystRef.path()));
            catalystForm = newForm(session, catalystRef.path(), catalystDescriptor);
        } else {
            catalystForm = catalystOn ? newForm(session, RuleFields.CATALYST_COST, catalystDescriptor) : null;
        }
        liveWidgets.add(catalystToggle);
        if (catalystForm != null) {
            liveForms.add(catalystForm);
            liveWidgets.add(catalystForm);
        }
        buildPresenceSection(session, FLUID_PRESENT_ID, UI + "rule.presence.add_fluid");
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

    // 为每个已选源物品建立每轮消耗设置，初始数量为 1。
    private void specifySourceCost() {
        EditSession session = host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        apply(EditSession.OP_SET_FIELD, () -> draft.setAt(RuleFields.SOURCE_COST, defaultQuantities(new JsonObject(),
                stringList(draft.getAt(RuleFields.SOURCE + "." + RuleFields.SOURCE_ITEMS)))));
    }

    // 开启消耗时用当前门槛初始化每轮成本；关闭同时移除既有消耗效果，避免残留扣除。
    private void onCatalystToggle(boolean checked) {
        EditSession session = host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        apply(EditSession.OP_SET_FIELD, () -> {
            if (checked) {
                PresenceLeaf leaf = selectedCatalystLeaf(draft);
                if (leaf == null) return;
                List<String> items = ruleLevelCatalystItems(draft);
                JsonObject counts = new JsonObject();
                for (PresenceLeaf entry : ruleLevelPresence(draft.view(), CATALYST_PRESENT_ID)) {
                    JsonObject condition = java.util.Objects.requireNonNull(draft.getAt(entry.conditionPath())).getAsJsonObject();
                    reconcileQuantities(condition, RuleFields.ITEM_COUNTS,
                            stringList(condition.get(RuleFields.CATALYST_ITEMS)), catalystThreshold(entry));
                    for (var amount : condition.getAsJsonObject(RuleFields.ITEM_COUNTS).entrySet()) {
                        int previous = counts.has(amount.getKey()) ? counts.get(amount.getKey()).getAsInt() : 0;
                        counts.addProperty(amount.getKey(), Math.max(previous, amount.getValue().getAsInt()));
                    }
                }
                JsonObject cost = new JsonObject();
                JsonArray references = new JsonArray();
                items.forEach(references::add);
                cost.add(RuleFields.CATALYST_ITEMS, references);
                cost.add(RuleFields.ITEM_COUNTS, counts);
                draft.setAt(RuleFields.CATALYST_COST, cost);
            } else {
                removeCatalystConsumption(draft);
            }
        });
    }

    private static @Nullable PresenceLeaf selectedCatalystLeaf(RuleDraft draft) {
        for (PresenceLeaf leaf : ruleLevelPresence(draft.view(), CATALYST_PRESENT_ID)) {
            if (!stringList(draft.getAt(leaf.fieldPath())).isEmpty()) return leaf;
        }
        return null;
    }

    // 读取显式门槛；省略时沿用同运行期的有效默认值。
    private int catalystThreshold(PresenceLeaf leaf) {
        EditSession session = host.session();
        JsonElement count = session == null ? null
                : session.draft().getAt(leaf.conditionPath() + "." + RuleFields.CATALYST_COUNT);
        return count != null && count.isJsonPrimitive() && count.getAsJsonPrimitive().isNumber()
                ? count.getAsInt() : effectiveThreshold(leaf.conditionPath());
    }

    private static void removeCatalystConsumption(RuleDraft draft) {
        draft.remove(RuleFields.CATALYST_COST);
        RuleCostBinding.Ref ref;
        while ((ref = RuleCostBinding.consumeCatalyst(draft.view())) != null) {
            draft.removeAt(ref.path());
        }
    }

    // ---- 输入页的存在条件（催化剂 / 流体）与门槛提示 ----

    // 追加一类存在条件的开关与类型表单：未勾选时不渲染类型编辑字段
    private void buildPresenceSection(EditSession session, ResourceLocation type, String toggleKey) {
        List<PresenceLeaf> ruleLevel = ruleLevelPresence(session.draft().view(), type);
        UiCheckBox toggle = new UiCheckBox(font, Component.translatable(toggleKey), !ruleLevel.isEmpty());
        toggle.setOnChanged(checked -> onPresenceToggle(type, checked));
        liveWidgets.add(toggle);
        if (CATALYST_PRESENT_ID.equals(type)) {
            catalystPresenceToggle = toggle;
        } else {
            fluidPresenceToggle = toggle;
        }
        for (PresenceLeaf leaf : ruleLevel) {
            FormView form = presenceForm(session, leaf);
            presenceRows.add(new PresenceRow(leaf, form));
            liveForms.add(form);
            liveWidgets.add(form);
        }
    }

    // 存在条件表单绑定到确切叶路径，催化剂额外呈现门槛。
    private FormView presenceForm(EditSession session, PresenceLeaf leaf) {
        FormView form = newForm(session, leaf.conditionPath(), presenceDescriptor(leaf));
        form.hideRowLabel(leaf.fieldPath());
        configurePresenceWrites(form, session, leaf);
        // 类型改写立即落盘（类型 + 关联消耗配置重指向 = 同一次可撤销操作）；此处不重建页面，
        // 目录选择可连续多次回调，重建会让后续选择写进已卸载的控件
        form.setOnChanged(() -> {
            EditSession current = host.session();
            if (current == null || host.rejectWhenFrozen()) {
                return;
            }
            form.applyToDraft();
            refreshQuantityReferences();
            applyEnabled();
            // 目录选择已在同一次字段写入中提交，仍需通知宿主刷新规则摘要。
            host.onDraftChanged();
        });
        // 选择卡片自带逐项移除；多个同类型叶保留移除整条条件的入口。
        if (ruleLevelPresence(session.draft().view(), leaf.type()).size() > 1) {
            form.setRowAction(leaf.fieldPath(), new UiButton(font, Component.translatable(UI + "rule.presence.remove"),
                    UiButtonVariant.SECONDARY, () -> removePresenceLeaf(leaf)));
        }
        return form;
    }

    private void configurePresenceWrites(FormView form, EditSession session, PresenceLeaf leaf) {
        form.setOnFieldWritten((path, before) -> {
            if (!leaf.fieldPath().equals(path)) return;
            if (leaf.catalyst()) {
                JsonObject condition = java.util.Objects.requireNonNull(session.draft().getAt(leaf.conditionPath())).getAsJsonObject();
                reconcileQuantities(condition, RuleFields.ITEM_COUNTS, stringList(condition.get(RuleFields.CATALYST_ITEMS)),
                        catalystThreshold(leaf));
                retargetCatalystItems(session.draft(), stringList(before),
                        stringList(session.draft().getAt(path)));
            } else {
                var selected = stringList(session.draft().getAt(path));
                List<String> previous = stringList(before);
                if (selected.size() == 1) retargetFluidActions(session.draft(),
                        previous.size() == 1 ? previous.getFirst() : "", selected.getFirst());
            }
        });
    }

    // 催化剂先选择物品，再设置门槛；组合关系仍由触发页管理。
    private TypeEditorDescriptor presenceDescriptor(PresenceLeaf leaf) {
        List<EditorField> fields = leaf.catalyst()
                ? List.of(EditorField.tagList(RuleFields.CATALYST_ITEMS, UI + "field.catalyst_present.items", "minecraft:item")
                        .asRequired(), EditorField.itemCounts(RuleFields.ITEM_COUNTS,
                        UI + "rule.catalyst_min_count", CatalystCost.MIN_COUNT, CatalystCost.MAX_COUNT),
                        EditorField.note(UI + "rule.catalyst_all_required"))
                : List.of(EditorField.tagList(RuleFields.FLUIDS, UI + "field.fluid_present.fluid", "minecraft:fluid").optional(),
                        EditorField.note(UI + "field.fluid_present.fluid.hint"));
        return TypeEditorDescriptor.of(leaf.type(), TypeLabels.conditionLabel(leaf.type()), fields);
    }

    // 关闭催化剂时同时关闭消耗；流体继续只管理存在条件。
    private void onPresenceToggle(ResourceLocation type, boolean checked) {
        EditSession session = host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        apply(EditSession.OP_SET_FIELD, () -> {
            if (checked) {
                addRuleLevelPresence(draft, type);
            } else {
                if (CATALYST_PRESENT_ID.equals(type)) removeCatalystConsumption(draft);
                removeRuleLevelPresence(draft, type);
            }
        });
    }

    // 逐项移除一个存在叶（父组变空顺手整理），一次撤销可整体还原
    private void removePresenceLeaf(PresenceLeaf leaf) {
        EditSession session = host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        apply(EditSession.OP_SET_FIELD, () -> removeLeafAt(draft, leaf.path()));
    }

    // 结构新增：空树直接加叶；已有根不是 all_of 时把完整旧根与新叶一起放进新的 all_of
    private static void addRuleLevelPresence(RuleDraft draft, ResourceLocation type) {
        JsonObject leaf = presenceLeafJson(type);
        if (CATALYST_PRESENT_ID.equals(type)) {
            leaf.getAsJsonObject(RuleFields.CONDITION).addProperty(RuleFields.CATALYST_COUNT,
                    CatalystPresentCondition.DEFAULT_COUNT);
        }
        JsonElement current = draft.getAt(RuleFields.CONDITIONS);
        if (current == null || current.isJsonNull()) {
            draft.setAt(RuleFields.CONDITIONS, leaf);
            return;
        }
        if (current.isJsonObject() && RuleFields.OP_ALL_OF.equals(stringField(current.getAsJsonObject(), RuleFields.OP))) {
            JsonObject root = current.getAsJsonObject();
            JsonElement terms = root.get(RuleFields.TERMS);
            JsonArray array = terms != null && terms.isJsonArray() ? terms.getAsJsonArray() : new JsonArray();
            array.add(leaf);
            root.add(RuleFields.TERMS, array);
            draft.setAt(RuleFields.CONDITIONS, root);
            return;
        }
        // 保留旧根整体语义：包一层 all_of，而不是把叶插进当前选中的节点
        JsonObject group = new JsonObject();
        group.addProperty(RuleFields.OP, RuleFields.OP_ALL_OF);
        JsonArray terms = new JsonArray();
        terms.add(current.deepCopy());
        terms.add(leaf);
        group.add(RuleFields.TERMS, terms);
        draft.setAt(RuleFields.CONDITIONS, group);
    }

    // 删除规则级条件里指定类型的全部存在叶（逐个删并整理空父组）
    private static void removeRuleLevelPresence(RuleDraft draft, ResourceLocation type) {
        while (true) {
            PresenceLeaf target = null;
            for (PresenceLeaf leaf : ruleLevelPresence(draft.view(), type)) {
                target = leaf;
                break;
            }
            if (target == null) {
                return;
            }
            int remaining = ruleLevelPresence(draft.view(), type).size();
            removeLeafAt(draft, target.path());
            if (ruleLevelPresence(draft.view(), type).size() >= remaining) {
                // 删除没有生效时停手，避免死循环
                return;
            }
        }
    }

    // 删除一个节点并自下而上清理变空的父组；不改变 any_of / inverted 的结构语义
    private static void removeLeafAt(RuleDraft draft, String path) {
        String current = path;
        while (current != null) {
            draft.removeAt(current);
            String parent = parentPath(current);
            if (parent == null) {
                return;
            }
            JsonElement parentNode = draft.getAt(parent);
            if (parentNode == null || !parentNode.isJsonObject() || !isEmptyGroup(parentNode.getAsJsonObject())) {
                return;
            }
            current = parent;
        }
    }

    // 路径的父节点：conditions.terms[1] -> conditions；conditions.term -> conditions
    private static @Nullable String parentPath(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? null : path.substring(0, dot);
    }

    // 组合节点是否已经没有子项：all_of / any_of 看 terms，inverted 看唯一的 term
    private static boolean isEmptyGroup(JsonObject node) {
        String op = stringField(node, RuleFields.OP);
        if (RuleFields.OP_INVERTED.equals(op)) {
            JsonElement term = node.get(RuleFields.TERM);
            return term == null || term.isJsonNull();
        }
        if (RuleFields.OP_ALL_OF.equals(op) || RuleFields.OP_ANY_OF.equals(op)) {
            JsonElement terms = node.get(RuleFields.TERMS);
            return terms == null || !terms.isJsonArray() || terms.getAsJsonArray().isEmpty();
        }
        return false;
    }

    // 新建存在条件叶的 JSON：催化剂保留 items 为空的待选状态，但不预写 count=1
    private static JsonObject presenceLeafJson(ResourceLocation type) {
        JsonObject condition = new JsonObject();
        condition.addProperty(RuleFields.TYPE, type.toString());
        if (CATALYST_PRESENT_ID.equals(type)) {
            condition.add(RuleFields.CATALYST_ITEMS, new JsonArray());
        }
        JsonObject leaf = new JsonObject();
        leaf.addProperty(RuleFields.OP, RuleFields.OP_LEAF);
        leaf.add(RuleFields.CONDITION, condition);
        return leaf;
    }

    // 按类型新建条件叶节点：通用工厂会给催化剂预写 count=1，这里改走同一份 JSON
    private static @Nullable ConditionNode.Leaf presenceLeafNode(ResourceLocation type) {
        ConditionExpression expression = RuleCodecs.conditionExpressionCodec(ClientTypeRegistries.conditions())
                .parse(JsonOps.INSTANCE, presenceLeafJson(type))
                .result()
                .orElse(null);
        return expression != null && expression.root() instanceof ConditionNode.Leaf leaf ? leaf : null;
    }

    // 扫描草稿中的全部存在条件叶（规则级 + 动作局部），按固定顺序编号
    private static List<PresenceLeaf> scanPresenceLeaves(@Nullable JsonObject view) {
        List<PresenceLeaf> leaves = new ArrayList<>();
        if (view == null) {
            return leaves;
        }
        Map<String, Integer> counters = new HashMap<>();
        collectPresence(view.get(RuleFields.CONDITIONS), RuleFields.CONDITIONS,
                Component.translatable(UI + "rule.presence.scope_rule"), null, -1, -1, leaves, counters);
        JsonElement effects = view.get(RuleFields.EFFECTS);
        if (effects != null && effects.isJsonArray()) {
            JsonArray array = effects.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                if (!array.get(index).isJsonObject()) {
                    continue;
                }
                String effectPath = RuleFields.EFFECTS + "[" + index + "]";
                collectPresence(array.get(index).getAsJsonObject().get(RuleFields.CONDITIONS),
                        effectPath + "." + RuleFields.CONDITIONS,
                        Component.translatable(UI + "rule.presence.scope_action", index + 1), effectPath, -1, index,
                        leaves, counters);
            }
        }
        JsonElement outcomes = view.get(RuleFields.OUTCOMES);
        if (outcomes != null && outcomes.isJsonArray()) {
            JsonArray candidates = outcomes.getAsJsonArray();
            for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
                if (!candidates.get(candidateIndex).isJsonObject()) {
                    continue;
                }
                JsonElement candidateEffects = candidates.get(candidateIndex).getAsJsonObject().get(RuleFields.EFFECTS);
                if (candidateEffects == null || !candidateEffects.isJsonArray()) {
                    continue;
                }
                JsonArray inner = candidateEffects.getAsJsonArray();
                for (int effectIndex = 0; effectIndex < inner.size(); effectIndex++) {
                    if (!inner.get(effectIndex).isJsonObject()) {
                        continue;
                    }
                    String effectPath = RuleFields.OUTCOMES + "[" + candidateIndex + "]." + RuleFields.EFFECTS
                            + "[" + effectIndex + "]";
                    collectPresence(inner.get(effectIndex).getAsJsonObject().get(RuleFields.CONDITIONS),
                            effectPath + "." + RuleFields.CONDITIONS,
                            Component.translatable(UI + "rule.presence.scope_effect", candidateIndex + 1, effectIndex + 1),
                            effectPath, candidateIndex, effectIndex, leaves, counters);
                }
            }
        }
        return leaves;
    }

    // 递归收集一棵条件树里的存在条件叶，路径语法与草稿一致（terms[i] / term / condition）
    private static void collectPresence(@Nullable JsonElement node, String path, Component scope,
                                        @Nullable String effectPath, int candidateIndex, int effectIndex,
                                        List<PresenceLeaf> out, Map<String, Integer> counters) {
        if (node == null || !node.isJsonObject()) {
            return;
        }
        JsonObject object = node.getAsJsonObject();
        if (RuleFields.OP_LEAF.equals(stringField(object, RuleFields.OP))) {
            JsonElement condition = object.get(RuleFields.CONDITION);
            if (condition == null || !condition.isJsonObject()) {
                return;
            }
            ResourceLocation type = ResourceLocation.tryParse(stringField(condition.getAsJsonObject(), RuleFields.TYPE));
            if (type == null || !isPresenceType(type)) {
                return;
            }
            int ordinal = counters.merge(type.toString(), 1, Integer::sum);
            out.add(new PresenceLeaf(path, path + "." + RuleFields.CONDITION, type, ordinal, scope,
                    effectPath, candidateIndex, effectIndex));
            return;
        }
        JsonElement terms = object.get(RuleFields.TERMS);
        if (terms != null && terms.isJsonArray()) {
            JsonArray array = terms.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                collectPresence(array.get(index), path + "." + RuleFields.TERMS + "[" + index + "]", scope,
                        effectPath, candidateIndex, effectIndex, out, counters);
            }
        }
        JsonElement term = object.get(RuleFields.TERM);
        if (term != null && !term.isJsonNull()) {
            collectPresence(term, path + "." + RuleFields.TERM, scope, effectPath, candidateIndex, effectIndex,
                    out, counters);
        }
    }

    // 规则级条件里指定类型的存在叶（输入页只呈现规则级叶）
    private static List<PresenceLeaf> ruleLevelPresence(@Nullable JsonObject view, ResourceLocation type) {
        List<PresenceLeaf> result = new ArrayList<>();
        for (PresenceLeaf leaf : scanPresenceLeaves(view)) {
            if (leaf.ruleLevel() && leaf.type().equals(type)) {
                result.add(leaf);
            }
        }
        return result;
    }

    // 开关状态：规则级条件里是否已有该类型的存在叶
    private static boolean hasRuleLevelPresence(@Nullable JsonObject view, ResourceLocation type) {
        return !ruleLevelPresence(view, type).isEmpty();
    }

    // 按确切叶路径取出扫描结果（编号、作用域与门槛作用域判定共用）
    private @Nullable PresenceLeaf findPresenceLeaf(String conditionPath) {
        for (PresenceLeaf leaf : scanPresenceLeaves(currentView())) {
            if (leaf.conditionPath().equals(conditionPath)) {
                return leaf;
            }
        }
        return null;
    }

    // 规则级条件里第一个已选物品的催化剂叶引用（勾选固定成本时同步类型用）
    private static List<String> ruleLevelCatalystItems(RuleDraft draft) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        for (PresenceLeaf leaf : ruleLevelPresence(draft.view(), CATALYST_PRESENT_ID)) {
            result.addAll(stringList(draft.getAt(leaf.fieldPath())));
        }
        return List.copyOf(result);
    }

    private static boolean isPresenceType(ResourceLocation type) {
        return CATALYST_PRESENT_ID.equals(type) || FLUID_PRESENT_ID.equals(type);
    }

    // 存在条件的类型缓冲在切页 / 提交流程里也必须走「类型 + 关联重指向」这同一条路径
    private boolean flushPresenceTypes() {
        EditSession session = host.session();
        if (session == null) {
            return false;
        }
        boolean changed = false;
        for (PresenceRow row : presenceRows) {
            if (row.form.isVisible() && flushPresenceType(session, row.leaf, row.form)) {
                changed = true;
            }
        }
        return changed;
    }

    // 把存在叶的类型缓冲写回草稿：类型改写与关联消耗配置重指向合并为一次可撤销操作
    private static boolean flushPresenceType(EditSession session, PresenceLeaf leaf, FormView form) {
        RuleDraft draft = session.draft();
        // 叶可能已被条件树删除或改类型：路径不再指向同类叶时不写回，避免复活已删除的条件
        JsonElement leafNode = draft.getAt(leaf.path());
        if (!(leafNode instanceof JsonObject leafObject)) {
            return false;
        }
        JsonElement leafCondition = leafObject.get(RuleFields.CONDITION);
        if (!(leafCondition instanceof JsonObject condition)
                || !leaf.type().toString().equals(stringField(condition, RuleFields.TYPE))) {
            return false;
        }
        return form.applyToDraft();
    }

    // 流体类型改写后同步移除动作：fluid 与旧类型一致、或尚未指定类型的 consume_fluid 跟随新类型
    private static void retargetFluidActions(RuleDraft draft, String before, String after) {
        if (after.isEmpty()) {
            // 类型被清空属于「任意流体」的中间态，不连带清空移除动作
            return;
        }
        retargetFluidAction(draft.getAt(RuleFields.EFFECTS), before, after);
        JsonElement outcomes = draft.getAt(RuleFields.OUTCOMES);
        if (outcomes != null && outcomes.isJsonArray()) {
            for (JsonElement candidate : outcomes.getAsJsonArray()) {
                if (candidate.isJsonObject()) {
                    retargetFluidAction(candidate.getAsJsonObject().get(RuleFields.EFFECTS), before, after);
                }
            }
        }
    }

    private static void retargetFluidAction(@Nullable JsonElement list, String before, String after) {
        if (list == null || !list.isJsonArray()) {
            return;
        }
        for (JsonElement entry : list.getAsJsonArray()) {
            if (!entry.isJsonObject()) {
                continue;
            }
            JsonObject object = entry.getAsJsonObject();
            if (!CONSUME_FLUID_ID.toString().equals(stringField(object, RuleFields.TYPE))) {
                continue;
            }
            String existing = primitiveString(object.get("fluid"));
            if (!existing.isEmpty() && !existing.equals(before)) {
                continue;
            }
            object.addProperty("fluid", after);
        }
    }

    // 字符串型 JSON 值（缺失或非字符串时返回空串）
    private static String primitiveString(@Nullable JsonElement element) {
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    // 类型改写后同步关联消耗配置：items 与旧类型一致、或尚未指定类型的消耗配置跟随新类型
    private static void retargetCatalystItems(RuleDraft draft, List<String> before, List<String> after) {
        if (draft.getAt(RuleFields.CATALYST_COST) instanceof JsonObject cost) {
            List<String> references = ruleLevelCatalystItems(draft);
            JsonArray items = new JsonArray();
            references.forEach(items::add);
            cost.add(RuleFields.CATALYST_ITEMS, items);
            reconcileQuantities(cost, RuleFields.ITEM_COUNTS, references, CatalystCost.DEFAULT_COUNT);
        }
        retargetConsumptionList(draft.getAt(RuleFields.EFFECTS), before, after);
        JsonElement outcomes = draft.getAt(RuleFields.OUTCOMES);
        if (outcomes != null && outcomes.isJsonArray()) {
            for (JsonElement candidate : outcomes.getAsJsonArray()) {
                if (!candidate.isJsonObject()) {
                    continue;
                }
                retargetConsumptionList(candidate.getAsJsonObject().get(RuleFields.EFFECTS), before, after);
            }
        }
    }

    private static void retargetConsumptionList(@Nullable JsonElement list, List<String> before, List<String> after) {
        if (list == null || !list.isJsonArray()) {
            return;
        }
        for (JsonElement entry : list.getAsJsonArray()) {
            retargetConsumptionObject(entry, before, after);
        }
    }

    // requireConsumeType：动作要先确认自己是消耗催化剂；规则级 catalyst_cost 本身就是该配置
    private static void retargetConsumptionObject(@Nullable JsonElement element,
                                                 List<String> before, List<String> after) {
        if (element == null || !element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        if (!RuleCostBinding.CONSUME_CATALYST.equals(stringField(object, RuleFields.TYPE))) {
            return;
        }
        List<String> existing = stringList(object.get(RuleFields.CATALYST_ITEMS));
        if (!existing.isEmpty() && referencesDiffer(existing, before)) {
            return;
        }
        JsonArray next = new JsonArray();
        for (String reference : after) {
            next.add(reference);
        }
        object.add(RuleFields.CATALYST_ITEMS, next);
        reconcileQuantities(object, RuleFields.ITEM_COUNTS, after, countField(object));
    }

    private static void reconcileQuantities(JsonObject owner, String field, List<String> references, int fallback) {
        JsonObject previous = owner.get(field) instanceof JsonObject object ? object : new JsonObject();
        JsonObject next = new JsonObject();
        for (String reference : references) {
            next.add(reference, previous.has(reference) ? previous.get(reference).deepCopy()
                    : new JsonPrimitive(Math.max(CatalystCost.DEFAULT_COUNT, fallback)));
        }
        owner.add(field, next);
    }

    private static int countField(JsonObject owner) {
        JsonElement value = owner.get(RuleFields.CATALYST_COUNT);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                ? value.getAsInt() : CatalystCost.DEFAULT_COUNT;
    }

    // 引用列表是否不是同一组（集合语义、顺序无关）：任一侧为空都不算相同
    private static boolean referencesDiffer(List<String> left, List<String> right) {
        return left.isEmpty() || right.isEmpty() || !new HashSet<>(left).equals(new HashSet<>(right));
    }

    // 存在条件标题行：类型 + 同类型编号 + 作用域
    private static Component presenceHeader(PresenceLeaf leaf) {
        return Component.translatable(UI + "rule.presence.title", TypeLabels.conditionLabel(leaf.type()),
                leaf.ordinal(), leaf.scopeLabel());
    }

    // 一类存在条件的版面：开关一行，随后每个规则级叶一个标题行 + 类型表单
    private int layoutPresenceSection(ResourceLocation type, int x, int y, int width) {
        int cursor = y;
        UiCheckBox toggle = CATALYST_PRESENT_ID.equals(type) ? catalystPresenceToggle : fluidPresenceToggle;
        if (toggle != null) {
            toggle.setBounds(x, cursor, width, ROW_H);
            cursor += ROW_H + PAD;
        }
        for (PresenceRow row : presenceRows) {
            if (!row.leaf.ruleLevel() || !row.leaf.type().equals(type)) {
                continue;
            }
            boolean showHeader = ruleLevelPresence(currentView(), type).size() > 1;
            row.headerRect = showHeader ? new UiRect(x, cursor, width, LINE_H) : null;
            if (showHeader) cursor += LINE_H;
            cursor = layoutForm(row.form, x, cursor, width);
        }
        return cursor;
    }

    // 催化剂门槛字段对外统一称「最低触发数量」（原标签是「数量」）
    private List<EditorField> relabelCatalystThreshold(List<EditorField> fields) {
        List<EditorField> relabeled = new ArrayList<>();
        for (EditorField field : fields) {
            relabeled.add(RuleFields.ITEM_COUNTS.equals(field.name()) ? relabelCatalystCountField(field) : field);
        }
        return relabeled;
    }

    // 复制字段并替换标签 key（EditorField 没有 withLabelKey）
    private static EditorField relabelCatalystCountField(EditorField field) {
        return new EditorField(field.name(), UI + "rule.catalyst_min_count", field.type(), field.domain(),
                field.nullable(), field.required(), field.registry(), field.enumGroup(), field.hintKey(),
                field.subFields(), field.presets(), field.numbers(), field.displayPrecision());
    }

    // 有效门槛：与运行期完全同源 —— 草稿解码成 Rule 后调用 core 的 resolveThreshold；
    // 草稿还不完整（解码失败）时才退化为同作用域消耗配置的 count
    private int effectiveThreshold(String conditionPath) {
        JsonObject view = currentView();
        if (view == null) {
            return CatalystPresentCondition.DEFAULT_COUNT;
        }
        Rule rule = RuleCodecs.codec(ClientTypeRegistries.effects(), ClientTypeRegistries.conditions())
                .parse(JsonOps.INSTANCE, view)
                .result()
                .orElse(null);
        if (rule == null) {
            return fallbackThreshold(conditionPath);
        }
        return CatalystThresholdProjection.resolveThreshold(rule, taggedItems(conditionPath),
                scopeEffectOf(rule, conditionPath));
    }

    // 条件叶声明的候选物品引用（item id / #tag）
    private List<TaggedId> taggedItems(String conditionPath) {
        EditSession session = host.session();
        List<TaggedId> items = new ArrayList<>();
        if (session == null) {
            return items;
        }
        for (String reference : stringList(session.draft().getAt(conditionPath + "." + RuleFields.CATALYST_ITEMS))) {
            TaggedId.parse(reference).result().ifPresent(items::add);
        }
        return items;
    }

    // 承载条件叶的作用域对象路径：规则级叶对应固定成本，动作局部叶对应它所在的动作
    private static String scopePathOf(String conditionPath) {
        int marker = conditionPath.indexOf("." + RuleFields.CONDITIONS);
        return marker < 0 ? RuleFields.CATALYST_COST : conditionPath.substring(0, marker);
    }

    // 条件叶所属的动作实例（null 表示规则级条件）
    private static @Nullable Effect scopeEffectOf(Rule rule, String conditionPath) {
        String scope = scopePathOf(conditionPath);
        if (RuleFields.CATALYST_COST.equals(scope)) {
            return null;
        }
        int effect = indexIn(scope, RuleFields.EFFECTS);
        if (effect < 0) {
            return null;
        }
        int candidate = indexIn(scope, RuleFields.OUTCOMES);
        if (candidate < 0) {
            return effect < rule.effects().size() ? rule.effects().get(effect) : null;
        }
        if (candidate >= rule.outcomes().size()) {
            return null;
        }
        List<Effect> effects = rule.outcomes().get(candidate).effects();
        return effect < effects.size() ? effects.get(effect) : null;
    }

    // 解码失败时的退路（规范允许）：同作用域消耗配置的 count，没有则用默认门槛
    private int fallbackThreshold(String conditionPath) {
        EditSession session = host.session();
        if (session == null) {
            return CatalystPresentCondition.DEFAULT_COUNT;
        }
        RuleDraft draft = session.draft();
        List<String> items = stringList(draft.getAt(conditionPath + "." + RuleFields.CATALYST_ITEMS));
        String scopePath = scopePathOf(conditionPath);
        JsonElement scope = draft.getAt(scopePath);
        if (scope == null || !scope.isJsonObject()) {
            return CatalystPresentCondition.DEFAULT_COUNT;
        }
        JsonObject object = scope.getAsJsonObject();
        boolean consumption = RuleFields.CATALYST_COST.equals(scopePath)
                || RuleCostBinding.CONSUME_CATALYST.equals(stringField(object, RuleFields.TYPE));
        if (!consumption) {
            return CatalystPresentCondition.DEFAULT_COUNT;
        }
        List<String> scopeItems = stringList(object.get(RuleFields.CATALYST_ITEMS));
        if (!scopeItems.isEmpty() && !items.isEmpty() && referencesDiffer(scopeItems, items)) {
            return CatalystPresentCondition.DEFAULT_COUNT;
        }
        return Math.max(CatalystPresentCondition.DEFAULT_COUNT,
                intAt(scopePath + "." + RuleFields.CATALYST_COUNT));
    }

    // 草稿路径上的整数（缺失或非数字时用兜底门槛）
    private int intAt(String path) {
        EditSession session = host.session();
        if (session == null) {
            return CatalystPresentCondition.DEFAULT_COUNT;
        }
        JsonElement value = session.draft().getAt(path);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return CatalystPresentCondition.DEFAULT_COUNT;
        }
        return value.getAsInt();
    }

    // 字符串数组字段（缺失或非数组时返回空列表）
    private static List<String> stringList(@Nullable JsonElement element) {
        List<String> values = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return values;
        }
        for (JsonElement entry : element.getAsJsonArray()) {
            if (entry.isJsonPrimitive()) {
                values.add(entry.getAsString());
            }
        }
        return values;
    }

    // 对象字段的字符串值（缺失或非字符串时返回空串）
    private static String stringField(@Nullable JsonObject object, String name) {
        if (object == null) {
            return "";
        }
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
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
                // 新建催化剂存在条件不预写门槛：门槛留空才能显示「默认：N」
                return type -> CATALYST_PRESENT_ID.equals(type) ? presenceLeafNode(type) : base.leafFactory().create(type);
            }

            @Override
            public Consumer<ConditionNode.Leaf> onEditLeaf() {
                return leaf -> openLeafEditor(owner, leaf);
            }

            @Override
            public void revealLeaf(ConditionNode.Leaf leaf, String fieldPath) {
                openLeafEditor(owner, leaf, fieldPath);
            }

            @Override
            public boolean openTree(UiConditionTreeEditor tree, @Nullable String fieldPath) {
                return openConditionTree(owner, fieldPath);
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
        openLeafEditor(owner, leaf, null);
    }

    private void openLeafEditor(FormView owner, ConditionNode.Leaf leaf, @Nullable String fieldPath) {
        EditSession session = host.session();
        if (session == null) {
            return;
        }
        UiConditionTreeEditor tree = findTree(owner, leaf);
        String base = tree != null && tree.selectedPath() != null ? tree.selectedPath() : RuleFields.CONDITIONS;
        String path = owner.draftPath(base.endsWith("." + RuleFields.CONDITION) ? base : base + "." + RuleFields.CONDITION);
        // 先写入新建的树节点，叶参数表单才有可读取的草稿路径。
        owner.applyToDraft();
        ConditionTreeOverlay.Parameters parameters = createLeafParameters(session, path, leaf);
        FormView form = parameters.form();
        RuleEditorP4Panels.LeafPanel panel = parameters.panel();
        UiModal modal = UiModal.create(font).title(parameters.title());
        int contentHeight = Math.clamp(panel.contentHeight() + 8, 36, 220);
        modal.contentWidget(panel, contentHeight);
        modal.onClosed(() -> {
            JsonElement current = session.draft().getAt(path);
            if (current instanceof JsonObject object
                    && leaf.condition().type().toString().equals(stringField(object, RuleFields.TYPE))) {
                form.commitPendingInputs();
                host.onDraftChanged();
            }
            panel.unmount();
        });
        modal.onHistoryChanged(() -> {
            JsonElement current = session.draft().getAt(path);
            if (current instanceof JsonObject object
                    && leaf.condition().type().toString().equals(stringField(object, RuleFields.TYPE))) {
                panel.reload();
            } else {
                host.modals().close(modal);
            }
        });
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
            form.commitPendingInputs();
            owner.reload();
            host.onDraftChanged();
            host.modals().closeTop();
        });
        modal.cancel(Component.translatable(UI + "button.cancel"));
        modal.layoutCentered(host.screenWidth(), host.screenHeight());
        host.modals().push(modal);
        if (fieldPath != null) panel.focusField(fieldPath);
    }

    // 叶弹窗与条件叠加页共用同一套参数、目录、区间条和关联写入。
    private ConditionTreeOverlay.Parameters createLeafParameters(EditSession session, String path, ConditionNode.Leaf leaf) {
        TypeEditorDescriptor descriptor = ConditionEditorRegistry.descriptorFor(leaf.condition().type());
        boolean catalystLeaf = CATALYST_PRESENT_ID.equals(leaf.condition().type());
        // 催化剂门槛字段对外统一称「最低触发数量」，留空时才给出「默认：N」
        if (catalystLeaf) {
            descriptor = new TypeEditorDescriptor(descriptor.id(), descriptor.label(),
                    relabelCatalystThreshold(descriptor.fields()), descriptor.readOnly());
        }
        PresenceLeaf presence = catalystLeaf ? findPresenceLeaf(path) : null;
        FormView form = new FormView(font, session, path);
        form.setConditionSupport(pageConditionSupport(form));
        form.setSuggestionProvider(host.suggestions());
        form.setOnChanged(() -> {
            form.applyToDraft();
            host.onDraftChanged();
        });
        // 输入被长度上限拒绝时立刻显示一条可读提示（字段名：问题）
        form.setOnRejectedNotice(message -> host.notice(message, UiPalette.DANGER));
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
        configureQuantityFields(form, session, path, descriptor);
        PresenceLeaf linked = findPresenceLeaf(path);
        if (linked != null && linked.ruleLevel()) configurePresenceWrites(form, session, linked);
        // 已知条件类型附带区间条（气候/昼夜/高度光照）与注册表字段的目录按钮；
        // 面板自身负责子控件的事件转发与 Tab 顺序，普通数值字段仍由表单承载
        RuleEditorP4Panels.LeafPanel content = RuleEditorP4Panels.leafPanel(font, form, session, descriptor.fields(),
                path, change -> {
                    form.commitPendingInputs();
                    session.apply(EditSession.OP_SET_FIELD, change);
                    host.onDraftChanged();
                }, () -> {
                    form.reload();
                    host.onDraftChanged();
                }, host.modals()::push, host.workspace(), host.screenWidth(), host.screenHeight());
        String noteKey = catalystLeaf ? UI + "rule.catalyst_all_required"
                : FLUID_PRESENT_ID.equals(leaf.condition().type()) ? UI + "field.fluid_present.fluid.hint" : null;
        RuleEditorP4Panels.LeafPanel panel = noteKey == null ? content : content.addNote(() -> Component.translatable(noteKey));
        Component title = presence == null ? descriptor.label() : Component.translatable(UI + "rule.presence.title",
                descriptor.label(), presence.ordinal(), presence.scopeLabel());
        return new ConditionTreeOverlay.Parameters(form, panel, title);
    }

    // 大屏编辑只绑定当前条件范围，不要求整条规则已填完整。
    private boolean openConditionTree(FormView owner, @Nullable String fieldPath) {
        EditSession session = host.session();
        if (session == null || host.rejectWhenFrozen()) return false;
        owner.commitPendingInputs();
        String scope = owner.draftPath(RuleFields.CONDITIONS);
        ConditionTreeOverlay overlay = new ConditionTreeOverlay(font, session, scope, pageConditionSupport(owner),
                (path, leaf) -> createLeafParameters(session, path, leaf), host::onDraftChanged);
        int chromeReserve = UiTheme.HEADER_HEIGHT + UiTheme.PADDING * 3 + UiModal.BUTTON_HEIGHT;
        UiModal modal = UiModal.create(font).title(Component.translatable(UI + "tree.title"))
                .preferredWidth(Math.max(UiModal.MIN_WIDTH, host.screenWidth() - TREE_OVERLAY_MARGIN))
                .contentWidget(overlay, Math.max(0, host.screenHeight() - TREE_OVERLAY_MARGIN - chromeReserve));
        modal.onClosed(() -> {
            overlay.unmount();
            owner.reload();
            host.onDraftChanged();
        });
        modal.onHistoryChanged(() -> {
            String ownerPath = scope.substring(0, Math.max(0, scope.length() - RuleFields.CONDITIONS.length() - 1));
            if (!ownerPath.isEmpty() && session.draft().getAt(ownerPath) == null) host.modals().close(modal);
            else overlay.historyChanged();
        });
        modal.cancel(Component.translatable(UI + "button.back"));
        modal.layoutCentered(host.screenWidth(), host.screenHeight());
        host.modals().push(modal);
        if (fieldPath != null) overlay.reveal(fieldPath);
        return true;
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
        // 单方案（顶层 effects）：结果页直达动作与产出参数，不渲染方案选择层
        singlePlan = candidates == 1;
        if (singlePlan) {
            resultStage = Math.max(1, resultStage);
        }

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
        if (!singlePlan) {
            // 单方案没有方案层：列表不登记为控件，不可见也不可聚焦
            liveWidgets.add(candidateList);
        }
        resultBack = new UiButton(font, Component.translatable(UI + "button.result_plans"), UiButtonVariant.SECONDARY,
                () -> enterResultStage(resultStage == 2 ? 1 : 0));
        liveWidgets.add(resultBack);
        buttons.add(resultBack);
        if (!singlePlan && host.screenWidth() < NARROW_WIDTH) addCandidateButton("button.candidate_edit", () -> enterResultStage(1));

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
                // 消耗参数的主编辑入口在输入页：结果页动作详情不重复提供类型/数量/半径
                List<EditorField> primary = new ArrayList<>(descriptor.fields().stream()
                        .filter(field -> !isCommonActionField(field))
                        .filter(field -> !isInputOwnedConsumptionField(typeId, field))
                        .toList());
                if (isConsumptionAction(typeId)) {
                    primary.addFirst(EditorField.note(UI + "effect.consume_params_on_input"));
                }
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
        // 方案标题统一走 RuleNaming（「方案 %s：%s」），不再出现「隐式结果」这类结构术语
        Component title = RuleNaming.candidateTitle(candidate, item + 1, nameSource);
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
        cards.clear();
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
        int padding = 6;
        int x = area.x() + padding;
        int width = Math.max(0, area.width() - padding * 2);
        int y = area.y() + padding;
        int idLines = Math.max(1, idTextLines(width).size());
        infoIdRect = new UiRect(x, y, width, (int) Math.ceil(idLines * (font.lineHeight + 1) * 0.8));
        y = layoutForm(infoForm, x, infoIdRect.bottom() + 4, width);
        int bottom = y + padding;
        cards.add(new PageCard(new UiRect(area.x(), area.y(), area.width(), bottom - area.y()), null));
        return bottom;
    }

    // 内容高度决定卡片高度，额外留白仅用于宽屏结果列表的对齐。
    private int finishCard(int x, int top, int width, int contentBottom, String titleKey) {
        int bottom = contentBottom + CARD_PAD;
        cards.add(new PageCard(new UiRect(x, top, width, Math.max(CARD_HEADER_H + CARD_PAD * 2, bottom - top)),
                Component.translatable(UI + titleKey)));
        return bottom + CARD_GAP;
    }

    // 只读规则 ID 行：按可用宽度折行，完整显示不截断
    private List<FormattedCharSequence> idTextLines(int width) {
        JsonObject view = currentView();
        String id = view == null ? "" : stringOf(view);
        return font.split(Component.literal(id), Math.max(8, (int) (width / 0.8)));
    }

    private int layoutInput(UiRect area) {
        int x = area.x() + CARD_PAD;
        int width = Math.max(0, area.width() - CARD_PAD * 2);
        int top = area.y();
        int y = layoutForm(sourceForm, x, top + CARD_PAD + CARD_HEADER_H, width);
        costModeRect = new UiRect(x, y, width, LINE_H);
        y += LINE_H + 2;
        if (sourceCostButton != null && sourceCostButton.isVisible()) {
            sourceCostButton.setBounds(x, y, Math.min(width, sourceCostButton.preferredWidth(PAD)), ROW_H);
            y += ROW_H + PAD;
        }
        if (costNote != null) {
            costNoteRect = new UiRect(x, y, width, LINE_H);
            y += LINE_H + PAD;
        }
        y = layoutForm(sourceCostForm, x, y, width);
        top = finishCard(area.x(), top, area.width(), y, "card.source");
        y = layoutPresenceSection(CATALYST_PRESENT_ID, x, top + CARD_PAD + CARD_HEADER_H, width);
        if (catalystToggle != null && catalystToggle.isVisible()) {
            catalystToggle.setBounds(x, y, width, ROW_H);
            y += ROW_H + PAD;
        }
        if (catalystNote != null) {
            catalystNoteRect = new UiRect(x, y, width, LINE_H);
            y += LINE_H + PAD;
        }
        y = layoutForm(catalystForm, x, y, width);
        top = finishCard(area.x(), top, area.width(), y, "card.catalyst");
        y = layoutPresenceSection(FLUID_PRESENT_ID, x, top + CARD_PAD + CARD_HEADER_H, width);
        return finishCard(area.x(), top, area.width(), y, "card.fluid");
    }

    private int layoutTrigger(UiRect area) {
        int x = area.x() + CARD_PAD;
        int width = Math.max(0, area.width() - CARD_PAD * 2);
        int top = area.y();
        int y = top + CARD_PAD + CARD_HEADER_H;
        int cursorX = x;
        for (UiCheckBox box : triggerBoxes) {
            int boxWidth = Math.min(width, box.preferredWidth(4));
            if (cursorX > x && cursorX + boxWidth > x + width) {
                cursorX = x;
                y += ROW_H + PAD;
            }
            box.setBounds(cursorX, y, boxWidth, ROW_H);
            cursorX += boxWidth + CARD_GAP;
        }
        y += ROW_H + PAD;
        triggerNoteRect = null;
        delayHintRect = null;
        if (RuleTriggers.isExplicitlyEmpty(currentView())) {
            triggerNoteRect = new UiRect(x, y, width, LINE_H);
            y += LINE_H + PAD;
        }
        y = layoutForm(delayForm, x, y, width);
        if (delayForm != null) {
            delayHintRect = new UiRect(x, y, width, LINE_H);
            y += LINE_H + PAD;
        }
        top = finishCard(area.x(), top, area.width(), y, "card.trigger");
        y = layoutForm(conditionsForm, x, top + CARD_PAD + CARD_HEADER_H, width);
        return finishCard(area.x(), top, area.width(), y, "card.conditions");
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

    // 消耗类动作：消耗参数主入口在输入页，结果页只保留概率 / 延迟 / 局部条件
    private static boolean isConsumptionAction(ResourceLocation type) {
        return CONSUME_SOURCE_ID.equals(type) || CONSUME_CATALYST_ID.equals(type) || CONSUME_FLUID_ID.equals(type);
    }

    // 结果页不重复提供的消耗参数字段：类型与数量/半径都归输入页
    private static boolean isInputOwnedConsumptionField(ResourceLocation type, EditorField field) {
        String name = field.name();
        if (CONSUME_SOURCE_ID.equals(type)) {
            return RuleFields.CATALYST_COUNT.equals(name) || RuleFields.ITEM_COUNTS.equals(name);
        }
        if (CONSUME_CATALYST_ID.equals(type)) {
            return RuleFields.CATALYST_ITEMS.equals(name) || RuleFields.CATALYST_COUNT.equals(name)
                    || RuleFields.ITEM_COUNTS.equals(name) || RuleFields.CATALYST_RADIUS.equals(name);
        }
        if (CONSUME_FLUID_ID.equals(type)) {
            // 流体类型由输入页的存在叶声明；require_source 是移除动作自身的匹配行为，仍可在此编辑
            return RuleFields.FLUID.equals(name);
        }
        return false;
    }

    private void enterResultStage(int stage) {
        if (blockNavigation()) return;
        // 单方案没有方案层，最小层级是动作列表
        resultStage = singlePlan ? Math.clamp(stage, 1, 2) : Math.clamp(stage, 0, 2);
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
        // 单方案没有方案层：窄屏最低层级是动作列表，宽屏左列直接是动作列表
        boolean plans = !singlePlan && (wide || resultStage == 0);
        boolean actions = wide || resultStage == 1;
        boolean detail = wide || resultStage == 2;
        boolean backVisible = !wide && resultStage > (singlePlan ? 1 : 0);
        show(candidateList, plans);
        candidateButtons.forEach(button -> show(button, plans || (singlePlan && actions)));
        show(combinationControl, plans);
        show(effectList, actions);
        effectButtons.forEach(button -> show(button, actions));
        show(safeSpawnBox, detail && showAdvancedEffects); show(fillOriginBox, detail && showAdvancedEffects);
        show(effectForm, detail); show(variantControl, detail); show(advancedButton, detail);
        show(advancedEffectForm, detail && showAdvancedEffects);
        show(resultBack, backVisible);
        combinationLabelRect = null; productPreviewRect = null; effectNoteRect = null;
        int x = area.x(), y = area.y();
        if (backVisible && resultBack != null) {
            resultBack.setLabel(Component.translatable(UI + (resultStage == 2 ? "button.result_actions" : "button.result_plans")));
            resultBack.setBounds(x, y, area.width(), BUTTON_H); y += BUTTON_H + PAD;
        }
        if (!wide) {
            if (resultStage == 0) return layoutPlans(x, y, area.width());
            if (resultStage == 1) return layoutActions(x, y, area.width(), singlePlan);
            return layoutActionDetail(x, y, area.width());
        }
        int navigationWidth = Math.clamp(area.width() * 36L / 100, 120, 200);
        int navY = y;
        if (!singlePlan) {
            navY = layoutPlans(x, navY, navigationWidth) + PAD;
        }
        navY = layoutActions(x, navY, navigationWidth, singlePlan);
        int detailX = x + navigationWidth + CARD_GAP;
        return Math.max(navY, layoutActionDetail(detailX, y, area.right() - detailX));
    }

    private int layoutPlans(int x, int y, int width) {
        int cardX = x, top = y, cardWidth = width;
        x += CARD_PAD;
        y += CARD_PAD + CARD_HEADER_H;
        width = Math.max(0, width - CARD_PAD * 2);
        if (combinationControl != null) {
            combinationLabelRect = new UiRect(x, y, width, LINE_H); y += LINE_H;
            combinationControl.setBounds(x, y, width, ROW_H); y += ROW_H + PAD;
        }
        int listHeight = Math.clamp(candidateList == null ? 1 : candidateList.size(), 1, LIST_ROWS) * ROW_H + 2;
        if (candidateList != null) candidateList.setBounds(x, y, width, listHeight);
        y += listHeight + BUTTON_GAP;
        y += layoutButtonGroup(candidateButtons, x, y, width);
        return finishCard(cardX, top, cardWidth, y, "card.plans");
    }

    // 动作列表；单方案时「添加结果」等按钮与动作按钮同列（没有独立的方案层）
    private int layoutActions(int x, int y, int width, boolean withCandidateButtons) {
        int cardX = x, top = y, cardWidth = width;
        x += CARD_PAD;
        y += CARD_PAD + CARD_HEADER_H;
        width = Math.max(0, width - CARD_PAD * 2);
        int listHeight = Math.max(74, Math.clamp(effectList == null ? 1 : effectList.size(), 1, LIST_ROWS) * 24 + 2);
        if (effectList != null) effectList.setBounds(x, y, width, listHeight);
        y += listHeight + BUTTON_GAP;
        y += layoutButtonGroup(effectButtons, x, y, width);
        if (withCandidateButtons) {
            y += layoutButtonGroup(candidateButtons, x, y, width) + BUTTON_GAP;
        }
        return finishCard(cardX, top, cardWidth, y, "card.actions");
    }

    private int layoutActionDetail(int x, int y, int width) {
        int cardX = x, top = y, cardWidth = width;
        x += CARD_PAD;
        y += CARD_PAD + CARD_HEADER_H;
        width = Math.max(0, width - CARD_PAD * 2);
        if (variantControl != null) { variantControl.setBounds(x, y, width, ROW_H); y += ROW_H + PAD; }
        if (selectedAction != null && RulePreviewIcons.action(selectedAction) != null) {
            boolean entityPreview = RulePreviewIcons.action(selectedAction) instanceof com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon.Rendered;
            int previewHeight = entityPreview ? 60 : 24;
            productPreviewRect = new UiRect(x, y, Math.min(entityPreview ? 72 : 24, width), previewHeight);
            y += previewHeight + PAD;
        }
        if (currentView() != null && readonlyEffectNote(currentView()) != null) { effectNoteRect = new UiRect(x, y, width, LINE_H); y += LINE_H + PAD; }
        y = layoutForm(effectForm, x, y, width);
        if (showAdvancedEffects && safeSpawnBox != null) { safeSpawnBox.setBounds(x, y, width, ROW_H); y += ROW_H + PAD; }
        if (showAdvancedEffects && fillOriginBox != null) { fillOriginBox.setBounds(x, y, width, ROW_H); y += ROW_H + PAD; }
        if (advancedButton != null) { advancedButton.setBounds(x, y, width, BUTTON_H); y += BUTTON_H + PAD; }
        if (showAdvancedEffects) y = layoutForm(advancedEffectForm, x, y, width);
        return finishCard(cardX, top, cardWidth, y, "card.action_detail");
    }

    // 按钮组横向排布（超宽换行），返回占用高度
    private int layoutButtonGroup(List<UiButton> group, int x, int y, int width) {
        if (group.isEmpty()) {
            return 0;
        }
        int cursorX = x;
        int cursorY = y;
        for (UiButton button : group) {
            int buttonWidth = Math.clamp(button.preferredWidth(PAD), Math.min(24, width), width);
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
        var pointer = com.meteorite.itemdespawntowhat.client.ui.kit.UiPointer.gated(!pageScroll.isDragging(), mouseX, mouseY);
        mouseX = pointer.x();
        mouseY = pointer.y();
        graphics.enableScissor(lastArea.x(), lastArea.y(), lastArea.right(), lastArea.bottom());
        try {
            for (PageCard card : cards) {
                UiTheme.drawCard(graphics, card.bounds());
                UiRect rect = card.bounds();
                if (card.title() == null) continue;
                graphics.fill(rect.x() + CARD_PAD, rect.y() + CARD_PAD, rect.x() + CARD_PAD + 2,
                        rect.y() + CARD_PAD + font.lineHeight, UiPalette.ACCENT);
                var titleLines = font.split(card.title().copy().withStyle(net.minecraft.ChatFormatting.BOLD),
                        Math.max(1, rect.width() - CARD_PAD * 2 - 6));
                if (!titleLines.isEmpty()) graphics.drawString(font, titleLines.getFirst(),
                        rect.x() + CARD_PAD + 6, rect.y() + CARD_PAD, UiPalette.TEXT_PRIMARY, false);
                UiTheme.drawDivider(graphics, rect.x() + CARD_PAD, rect.y() + CARD_PAD + CARD_HEADER_H - 5,
                        Math.max(0, rect.width() - CARD_PAD * 2));
            }
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

    // 基本页第一行：只读规则 ID（完整显示，不截断）
    private void renderInfo(GuiGraphics graphics) {
        if (infoIdRect == null || currentView() == null) {
            return;
        }
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(infoIdRect.x(), infoIdRect.y(), 0);
            graphics.pose().scale(0.8F, 0.8F, 1);
            int lineY = 0;
            for (FormattedCharSequence line : idTextLines(infoIdRect.width())) {
                graphics.drawString(font, line, 0, lineY, UiPalette.TEXT_SECONDARY, false);
                lineY += font.lineHeight + 1;
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    private void renderInput(GuiGraphics graphics) {
        drawText(graphics, costModeRect, Component.translatable(UI + "cost.source_title"), UiPalette.TEXT_SECONDARY);
        if (costNote != null) {
            drawText(graphics, costNoteRect, costNote, UiPalette.TEXT_DISABLED);
        }
        if (catalystNote != null) {
            drawText(graphics, catalystNoteRect, catalystNote, UiPalette.TEXT_DISABLED);
        }
        // 存在条件标题行：类型 + 同类型编号 + 作用域，多个同类叶也能逐项辨识
        for (PresenceRow row : presenceRows) {
            drawText(graphics, row.headerRect, presenceHeader(row.leaf), UiPalette.TEXT_SECONDARY);
        }
    }

    private void renderTrigger(GuiGraphics graphics) {
        JsonObject view = currentView();
        // 提示只在「显式选择空触发」这一确实缺失的状态出现，有效触发不再恒显
        if (RuleTriggers.isExplicitlyEmpty(view)) {
            drawText(graphics, triggerNoteRect, Component.translatable(UI + "trigger.note"), UiPalette.TEXT_DISABLED);
        }
        // 等待时长只对自然触发生效，其他触发组合下不显示该说明
        if (RuleTriggers.allowsDelay(view)) {
            drawText(graphics, delayHintRect, Component.translatable(UI + "trigger.delay_hint"), UiPalette.TEXT_DISABLED);
        }
    }

    private void renderResults(GuiGraphics graphics) {
        if (productPreviewRect != null && selectedAction != null && lastArea != null) {
            UiTheme.drawInset(graphics, productPreviewRect);
            var icon = RulePreviewIcons.action(selectedAction);
            if (icon instanceof com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon.Rendered rendered) {
                rendered.painter().render(graphics, productPreviewRect);
            } else if (icon != null) icon.render(graphics, productPreviewRect.x() + (productPreviewRect.width() - 16) / 2,
                    productPreviewRect.y() + (productPreviewRect.height() - 16) / 2);
            int labelX = productPreviewRect.right() + PAD;
            int labelWidth = Math.max(0, cards.getLast().bounds().right() - CARD_PAD - labelX);
            drawText(graphics, new UiRect(labelX, productPreviewRect.y() + 4, labelWidth, LINE_H),
                    RuleNaming.effectTitle(selectedAction, nameSource), UiPalette.TEXT_PRIMARY);
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
        // 不再用「本规则使用顶层 effects」这类结构说明；单方案直接进入动作层
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
        // 存在条件的类型改写必须先落盘，才能与关联消耗配置的重指向合并为一次可撤销操作
        boolean changed = flushPresenceTypes();
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

    private void applyEnabled() {
        JsonObject view = currentView();
        if (sourceCostButton != null) {
            sourceCostButton.setEnabled(enabled && RuleCostBinding.consumeSource(view) == null);
        }
        if (catalystToggle != null) {
            EditSession session = host.session();
            boolean hasCost = view != null && (view.has(RuleFields.CATALYST_COST)
                    || RuleCostBinding.consumeCatalyst(view) != null);
            catalystToggle.setEnabled(enabled && (hasCost || session != null
                    && selectedCatalystLeaf(session.draft()) != null));
        }
        if (catalystPresenceToggle != null) {
            catalystPresenceToggle.setEnabled(enabled);
        }
        if (fluidPresenceToggle != null) {
            fluidPresenceToggle.setEnabled(enabled);
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
        // 恢复默认同时检查尚未提交的自定义名称。
        if (restoreNameButton != null) {
            restoreNameButton.setEnabled(enabled && canRestoreName(view));
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
        if (restoreNameButton != null) restoreNameButton.setEnabled(enabled && canRestoreName(currentView()));
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
                // 记录按下归属：随后的释放与拖动只发给这个控件
                pressedWidget = widget;
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
        // 释放只派发给承接按下的控件；没有归属时不派发，避免残留按下态吞掉释放
        UiWidget owner = pressedWidget;
        pressedWidget = null;
        boolean handled = owner != null && owner.isVisible() && owner.mouseReleased(mouseX, mouseY, button);
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
        if (catalystToggle != null) {
            catalystToggle.setChecked(view.has(RuleFields.CATALYST_COST) || RuleCostBinding.consumeCatalyst(view) != null);
        }
        if (catalystPresenceToggle != null) {
            catalystPresenceToggle.setChecked(hasRuleLevelPresence(view, CATALYST_PRESENT_ID));
        }
        if (fluidPresenceToggle != null) {
            fluidPresenceToggle.setChecked(hasRuleLevelPresence(view, FLUID_PRESENT_ID));
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
}
