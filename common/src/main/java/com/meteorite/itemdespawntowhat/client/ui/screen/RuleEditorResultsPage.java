package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDefaults;
import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDescriptors;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorFactories;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EffectEditorRegistry;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleNaming;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFlowLayout;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.NaturalSummary;
import com.meteorite.itemdespawntowhat.client.ui.shared.EntityPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCheckBox;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiListView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiSegmentedControl;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiStructureDiagram;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.CombinationMode;
import com.meteorite.itemdespawntowhat.core.model.ConditionLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 结果页：顶部管理方案，左侧选择执行动作，右侧编辑参数；窄屏在动作和参数之间导航。
 */
final class RuleEditorResultsPage {

    // 所属编辑页容器：共享状态与跨页能力都经它访问
    private final RuleEditorEditPages pages;

    // 主题：与容器共用宿主默认主题
    private static final UiHostTheme THEME = RuleEditorEditPages.THEME;
    private static final int NAVIGATION_MIN_WIDTH = 128;
    private static final int NAVIGATION_MAX_WIDTH = 184;
    private static final int DETAIL_MIN_WIDTH = 212;
    private static final int ACTION_ROW_HEIGHT = 28;
    private static final int ACTION_LIST_MIN_HEIGHT = ACTION_ROW_HEIGHT * 2 + 2;
    private static final int TOOLBAR_HEIGHT = 20;
    private static final int PLAN_SELECTOR_MIN_WIDTH = 80;
    private static final int PLAN_PICKER_WIDTH = 320;
    private static final int PLAN_PICKER_ROWS = 7;
    private static final int DETAIL_HEADER_HEIGHT = 26;
    private static final int DETAIL_ICON_SIZE = 18;
    private static final int DETAIL_PADDING = 6;
    private static final int EMPTY_MESSAGE_GAP = 8;

    RuleEditorResultsPage(RuleEditorEditPages pages) {
        this.pages = pages;
    }

    // 清理本页持有的控件引用（重建/切页时由容器调用）
    void reset() {
        planSelector = null;
        safeSpawnBox = null;
        fillOriginBox = null;
        combinationControl = null;
        effectList = null;
        effectForm = null;
        variantControl = null;
        advancedEffectForm = null;
        advancedButton = null;
        resultBack = null;
        detailHeaderRect = null;
        emptyDetailRect = null;
        planStrategyRect = null;
        selectedAction = null;
        effectNoteRect = null;
        openActionButton = null;
    }

    int candidateIndex;

    int effectIndex;

    @Nullable UiButton planSelector;

    @Nullable UiCheckBox safeSpawnBox;

    @Nullable UiCheckBox fillOriginBox;

    @Nullable UiSegmentedControl combinationControl;

    @Nullable UiListView<Integer> effectList;

    @Nullable FormView effectForm;

    @Nullable UiSegmentedControl variantControl;

    @Nullable FormView advancedEffectForm;

    @Nullable UiButton advancedButton;

    @Nullable UiButton resultBack;

    boolean showAdvancedEffects;

    int resultStage;

    private @Nullable UiButton openActionButton;

    private @Nullable UiRect detailHeaderRect;

    private @Nullable UiRect emptyDetailRect;

    private @Nullable UiRect planStrategyRect;

    private boolean revealSelectedEffect;

    @Nullable RuleValue selectedAction;

    @Nullable UiRect effectNoteRect;

    void buildResults(EditSession session) {
        RuleDraft draft = session.draft();
        RuleValue rule = draft.root();
        int candidates = Math.max(1, ResultStructure.candidateCount(rule));
        candidateIndex = Math.clamp(candidateIndex, 0, candidates - 1);
        resultStage = Math.clamp(resultStage, 1, 2);
        planSelector = new UiButton(THEME, pages.font,
                Component.translatable(RuleEditorPageSupport.UI + "results.plan_selector", candidateIndex + 1, candidates),
                UiButtonVariant.SECONDARY, this::openPlanPicker);
        planSelector.setTooltip(RuleNaming.candidateTitle(ResultStructure.candidateAt(rule, candidateIndex),
                candidateIndex + 1, pages.nameSource), Component.translatable(RuleEditorPageSupport.UI + "results.select_plan"));
        pages.liveWidgets.add(planSelector);
        pages.buttons.add(planSelector);
        resultBack = new UiButton(THEME, pages.font,
                Component.translatable(RuleEditorPageSupport.UI + "button.result_actions"), UiButtonVariant.SECONDARY,
                () -> enterResultStage(1));
        pages.liveWidgets.add(resultBack);
        pages.buttons.add(resultBack);

        RuleValue candidate = ResultStructure.candidateAt(rule, candidateIndex);
        RuleValue actions = ResultStructure.effectsAt(rule, candidateIndex);
        boolean generic = RuleEditorPageSupport.containsAction(actions, "spawn_entity", "entity");
        boolean blocks = RuleEditorPageSupport.containsAction(actions, "place_block", null);
        if (generic) {
            safeSpawnBox = new UiCheckBox(RuleEditorEditPages.THEME, pages.font, Component.translatable(RuleEditorPageSupport.UI + "candidate.safe_spawn"), RuleEditorPageSupport.boolOf(candidate, RuleFields.SAFE_SPAWN, false));
            safeSpawnBox.setOnChanged(checked -> setCandidateStrategy(RuleFields.SAFE_SPAWN, checked));
            pages.liveWidgets.add(safeSpawnBox);
        }
        if (blocks) {
            fillOriginBox = new UiCheckBox(RuleEditorEditPages.THEME, pages.font, Component.translatable(RuleEditorPageSupport.UI + "candidate.fill_origin"), RuleEditorPageSupport.boolOf(candidate, RuleFields.FILL_ORIGIN, true));
            fillOriginBox.setOnChanged(checked -> setCandidateStrategy(RuleFields.FILL_ORIGIN, checked));
            pages.liveWidgets.add(fillOriginBox);
        }
        if (candidates > 1) {
            combinationControl = new UiSegmentedControl(RuleEditorEditPages.THEME, pages.font, List.of(
                    new UiSegmentedControl.Option(CombinationMode.ROUND_ROBIN.key(), Component.translatable(RuleEditorPageSupport.UI + "combination.round_robin")),
                    new UiSegmentedControl.Option(CombinationMode.PRIORITY.key(), Component.translatable(RuleEditorPageSupport.UI + "combination.priority"))));
            combinationControl.setSelected(RuleEditorPageSupport.combinationOf(rule));
            combinationControl.setOnChanged(this::onCombinationChanged);
            pages.liveWidgets.add(combinationControl);
        }

        String listPath = ResultStructure.listPath(rule, candidateIndex);
        int effectCount = ResultStructure.effectCount(rule, candidateIndex);
        effectIndex = Math.clamp(effectIndex, 0, Math.max(0, effectCount - 1));
        effectList = new UiListView<>(RuleEditorEditPages.THEME, pages.font, this::renderEffectRow);
        effectList.setRowHeight(ACTION_ROW_HEIGHT);
        effectList.setEmptyMessage(Component.translatable(RuleEditorPageSupport.UI + "summary.no_effect"));
        List<Integer> effects = new ArrayList<>();
        for (int i = 0; i < effectCount; i++) {
            effects.add(i);
        }
        effectList.setItems(effects);
        pages.rebuilding = true;
        effectList.setSelectedIndex(effectIndex);
        pages.rebuilding = false;
        effectList.setOnSelectionChanged(index -> {
            if (pages.rebuilding) {
                return;
            }
            if (pages.blockNavigation()) {
                pages.rebuilding = true;
                if (effectList != null) {
                    effectList.setSelectedIndex(effectIndex);
                }
                pages.rebuilding = false;
                return;
            }
            effectIndex = Math.max(0, index);
            pages.requestRebuild();
        });
        effectList.setOnActivate(index -> enterResultStage(2));
        pages.liveWidgets.add(effectList);
        revealSelectedEffect = true;
        if (effectCount > 0) {
            openActionButton = new UiButton(THEME, pages.font,
                    Component.translatable(RuleEditorPageSupport.UI + "button.effect_edit"), UiButtonVariant.SECONDARY,
                    () -> enterResultStage(2));
            pages.liveWidgets.add(openActionButton);
            pages.buttons.add(openActionButton);
        }

        RuleValue array = ResultStructure.effectsAt(rule, candidateIndex);
        if (array != null && effectIndex >= 0 && effectIndex < array.size()) {
            RuleValue element = array.item(effectIndex);
            String type = RuleCostBinding.typeOf(element);
            ResourceLocation typeId = type == null ? null : ResourceLocation.tryParse(type);
            selectedAction = element != null && element.isMapping() ? element : null;
            if (typeId != null) {
                String actionPath = listPath + "[" + effectIndex + "]";
                TypeEditorDescriptor descriptor = EffectEditorRegistry.descriptorFor(typeId);
                if (typeId.equals(ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, "spawn_entity"))
                        && element.isMapping()) {
                    String variant = element.text("variant", "item");
                    descriptor = BuiltinEditorDescriptors.entityDescriptor(variant);
                    variantControl = new UiSegmentedControl(RuleEditorEditPages.THEME, pages.font, List.of(
                            new UiSegmentedControl.Option("item", Component.translatable(RuleEditorPageSupport.UI + "results.product.item")),
                            new UiSegmentedControl.Option("entity", Component.translatable(RuleEditorPageSupport.UI + "results.product.entity")),
                            new UiSegmentedControl.Option("experience", Component.translatable(RuleEditorPageSupport.UI + "results.product.experience"))));
                    variantControl.setSelected(variant);
                    variantControl.setOnChanged(value -> switchProduct(actionPath, value));
                    pages.liveWidgets.add(variantControl);
                }
                // 消耗参数的主编辑入口在输入页：结果页动作详情不重复提供类型/数量/半径
                List<EditorField> primary = new ArrayList<>(descriptor.fields().stream()
                        .filter(field -> !RuleEditorPageSupport.isCommonActionField(field))
                        .filter(field -> !RuleEditorPageSupport.isInputOwnedConsumptionField(typeId, field))
                        .toList());
                if (RuleEditorPageSupport.isConsumptionAction(typeId)) {
                    primary.addFirst(EditorField.note(RuleEditorPageSupport.UI + "effect.consume_params_on_input"));
                }
                List<EditorField> advanced = descriptor.fields().stream().filter(RuleEditorPageSupport::isCommonActionField).toList();
                effectForm = pages.newForm(session, actionPath, new TypeEditorDescriptor(descriptor.id(), descriptor.label(), primary, descriptor.readOnly()));
                effectForm.useCompactSpacing();
                pages.liveForms.add(effectForm);
                pages.liveWidgets.add(effectForm);
                if (!advanced.isEmpty()) {
                    advancedEffectForm = pages.newForm(session, actionPath, new TypeEditorDescriptor(descriptor.id(), descriptor.label(), advanced, false));
                    advancedEffectForm.useCompactSpacing();
                    pages.liveForms.add(advancedEffectForm); pages.liveWidgets.add(advancedEffectForm);
                    advancedButton = new UiButton(THEME, pages.font, advancedLabel(), UiButtonVariant.SECONDARY, () -> {
                        if (pages.blockNavigation()) return;
                        showAdvancedEffects = !showAdvancedEffects; pages.requestRebuild();
                    });
                    pages.liveWidgets.add(advancedButton); pages.buttons.add(advancedButton);
                }
            }
        }

        pages.addCandidateButton("button.candidate_add", this::addCandidate);
        pages.addCandidateButton("results.plan_more", () -> openResultMore(true));
        pages.addEffectButton("button.effect_add", UiButtonVariant.PRIMARY, this::openEffectPicker);
        if (effectCount > 0) pages.addEffectButton("results.action_more", UiButtonVariant.SECONDARY, () -> openResultMore(false));
    }

    record MenuAction(Component label, Runnable action) { }

    void openResultMore(boolean candidate) {
        if (pages.blockNavigation()) return;
        List<MenuAction> actions = new ArrayList<>();
        if (candidate) {
            actions.add(new MenuAction(Component.translatable(RuleEditorPageSupport.UI + "button.diagram"), this::openDiagram));
            actions.add(new MenuAction(Component.translatable(RuleEditorPageSupport.UI + "button.candidate_duplicate"), this::duplicateCandidate));
            int count = ResultStructure.candidateCount(pages.currentView());
            if (candidateIndex > 0) actions.add(new MenuAction(Component.translatable(RuleEditorPageSupport.UI + "button.candidate_up"), () -> moveCandidateBy(-1)));
            if (candidateIndex + 1 < count) actions.add(new MenuAction(Component.translatable(RuleEditorPageSupport.UI + "button.candidate_down"), () -> moveCandidateBy(1)));
            if (count > 1) actions.add(new MenuAction(Component.translatable(RuleEditorPageSupport.UI + "button.candidate_remove"), this::removeCandidate));
        } else {
            actions.add(new MenuAction(Component.translatable(RuleEditorPageSupport.UI + "button.effect_duplicate"), this::duplicateEffect));
            if (effectIndex > 0) actions.add(new MenuAction(Component.translatable(RuleEditorPageSupport.UI + "button.effect_up"), () -> moveEffectBy(-1)));
            if (effectIndex + 1 < ResultStructure.effectCount(pages.currentView(), candidateIndex))
                actions.add(new MenuAction(Component.translatable(RuleEditorPageSupport.UI + "button.effect_down"), () -> moveEffectBy(1)));
            actions.add(new MenuAction(Component.translatable(RuleEditorPageSupport.UI + "button.effect_remove"), this::removeEffect));
        }
        UiListView<MenuAction> list = new UiListView<>(RuleEditorEditPages.THEME, pages.font, (graphics, rowFont, item, index, row, selected, hovered, focused) ->
                graphics.drawString(rowFont, item.label(), row.x() + 2, row.y() + 2, THEME.palette().textPrimary(), false));
        list.setItems(actions);
        list.setActivateOnSingleClick(true);
        UiModal modal = UiModal.create(THEME, pages.font).title(Component.translatable(RuleEditorPageSupport.UI
                + (candidate ? "results.plan_more" : "results.action_more_title")));
        list.setOnActivate(item -> { pages.host.modals().close(modal); item.action().run(); pages.flushRebuild(); });
        modal.contentWidget(list, actions.size() * 12 + 4).cancel(Component.translatable(RuleEditorPageSupport.UI + "button.cancel"));
        pages.host.modals().push(modal.layoutCentered(pages.host.screenWidth(), pages.host.screenHeight()));
    }

    void setCandidateStrategy(String field, boolean value) {
        EditSession session = pages.host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        boolean convert = !draft.hasOutcomes();
        int index = Math.max(0, candidateIndex);
        boolean applied = pages.apply(convert ? EditSession.OP_CONVERT_STRUCTURE : EditSession.OP_EDIT_CANDIDATE, () -> {
            if (convert) {
                draft.convertToOutcomes(EditorFactories.uniqueCandidateId(Set.of()));
            }
            draft.setAt(RuleFields.OUTCOMES + "[" + index + "]." + field, RuleValue.flag(value));
        });
        if (applied && convert) {
            pages.host.notice(Component.translatable(RuleEditorPageSupport.UI + "notice.converted"), THEME.palette().textSecondary());
        }
    }

    void onCombinationChanged(String value) {
        EditSession session = pages.host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        pages.apply(EditSession.OP_SET_FIELD, () -> draft.setString(RuleFields.COMBINATION, value));
    }

    void addCandidate() {
        EditSession session = pages.host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        if (ResultStructure.candidateCount(draft.root()) >= ConditionLimits.MAX_EFFECTS) {
            pages.host.notice(Component.translatable(RuleEditorPageSupport.UI + "issue.candidate_limit"), THEME.palette().warning());
            return;
        }
        boolean convert = !draft.hasOutcomes();
        if (!pages.apply(EditSession.OP_ADD_CANDIDATE, () -> {
            if (convert) {
                draft.convertToOutcomes(EditorFactories.uniqueCandidateId(Set.of()));
            }
            RuleValue body = RuleValue.mapping()
                    .put(RuleFields.CANDIDATE_ID, RuleValue.text(EditorFactories.uniqueCandidateId(draft.candidateIds())))
                    .put(RuleFields.CANDIDATE_EFFECTS, RuleValue.list().build())
                    .build();
            draft.setAt(RuleFields.OUTCOMES + "[" + draft.outcomeCount() + "]", body);
        })) {
            return;
        }
        candidateIndex = Math.max(0, ResultStructure.candidateCount(draft.root()) - 1);
        effectIndex = 0;
        pages.host.notice(Component.translatable(RuleEditorPageSupport.UI + (convert ? "notice.converted" : "notice.candidate_added")), THEME.palette().textSecondary());
    }

    void duplicateCandidate() {
        if (pages.blockNavigation()) {
            return;
        }
        EditSession session = pages.host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        if (ResultStructure.candidateCount(draft.root()) >= ConditionLimits.MAX_EFFECTS) {
            pages.host.notice(Component.translatable(RuleEditorPageSupport.UI + "issue.candidate_limit"), THEME.palette().warning());
            return;
        }
        boolean convert = !draft.hasOutcomes();
        int index = Math.max(0, candidateIndex);
        if (!pages.apply(EditSession.OP_ADD_CANDIDATE, () -> {
            if (convert) {
                draft.convertToOutcomes(EditorFactories.uniqueCandidateId(Set.of()));
            }
            int last = draft.outcomeCount() - 1;
            int from = index > last ? Math.max(0, last) : index;
            RuleValue source = draft.outcomeValueAt(from);
            if (source == null || !source.isMapping()) {
                return;
            }
            RuleValue body = RuleValue.mapping(source)
                    .put(RuleFields.CANDIDATE_ID, RuleValue.text(EditorFactories.uniqueCandidateId(draft.candidateIds())))
                    .build();
            draft.setAt(RuleFields.OUTCOMES + "[" + draft.outcomeCount() + "]", body);
        })) {
            return;
        }
        candidateIndex = Math.max(0, ResultStructure.candidateCount(draft.root()) - 1);
        effectIndex = 0;
        pages.host.notice(Component.translatable(RuleEditorPageSupport.UI + "notice.candidate_added"), THEME.palette().textSecondary());
    }

    void removeCandidate() {
        EditSession session = pages.host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        if (!draft.hasOutcomes() || draft.outcomeCount() <= 1) {
            pages.host.notice(Component.translatable(RuleEditorPageSupport.UI + "notice.no_selection"), THEME.palette().textDisabled());
            return;
        }
        int index = Math.max(0, candidateIndex);
        if (!pages.apply(EditSession.OP_REMOVE_CANDIDATE, () -> draft.removeAt(RuleFields.OUTCOMES + "[" + index + "]"))) {
            return;
        }
        candidateIndex = Math.max(0, index - 1);
        effectIndex = 0;
        pages.host.notice(Component.translatable(RuleEditorPageSupport.UI + "notice.candidate_removed"), THEME.palette().textSecondary());
    }

    void moveCandidateBy(int delta) {
        int target = candidateIndex + delta;
        if (delta == 0 || target < 0) {
            return;
        }
        EditSession session = pages.host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        if (target >= draft.outcomeCount()) {
            return;
        }
        moveCandidate(candidateIndex, target);
    }

    void moveCandidate(int from, int to) {
        EditSession session = pages.host.session();
        if (session == null || from == to) {
            return;
        }
        RuleDraft draft = session.draft();
        boolean[] moved = {false};
        if (!pages.apply(EditSession.OP_MOVE_CANDIDATE, () -> moved[0] = draft.moveOutcome(from, to)) || !moved[0]) {
            return;
        }
        candidateIndex = to;
        effectIndex = 0;
    }

    void openEffectPicker() {
        if (pages.blockNavigation()) {
            return;
        }
        List<ResourceLocation> types = new ArrayList<>();
        for (TypeEditorDescriptor descriptor : EffectEditorRegistry.all()) {
            if (RuleEditorPageSupport.CONSUME_SOURCE_ID.equals(descriptor.id()) || RuleEditorPageSupport.CONSUME_CATALYST_ID.equals(descriptor.id())) {
                continue;
            }
            types.add(descriptor.id());
        }
        if (types.isEmpty()) {
            pages.host.notice(Component.translatable(RuleEditorPageSupport.UI + "notice.no_effect"), THEME.palette().textDisabled());
            return;
        }
        UiListView<ResourceLocation> list = new UiListView<>(RuleEditorEditPages.THEME, pages.font, this::renderTypeRow);
        list.setItems(types);
        list.setSelectedIndex(0);
        UiModal modal = UiModal.create(RuleEditorEditPages.THEME, pages.font);
        modal.title(Component.translatable(RuleEditorPageSupport.UI + "pick.type_title"));
        modal.contentWidget(list, Math.min(types.size(), 8) * RuleEditorPageSupport.ROW_H + 4);
        modal.preferredWidth(200);
        modal.confirm(Component.translatable(RuleEditorPageSupport.UI + "button.confirm"), () -> addEffectOfType(list.selectedItem()));
        modal.cancel(Component.translatable(RuleEditorPageSupport.UI + "button.cancel"));
        modal.layoutCentered(pages.host.screenWidth(), pages.host.screenHeight());
        pages.host.modals().push(modal);
        pages.host.notice(Component.translatable(RuleEditorPageSupport.UI + "pick.consume_hint"), THEME.palette().textDisabled());
    }

    void renderTypeRow(GuiGraphics graphics, Font renderFont, ResourceLocation item, int index, UiRect row, boolean selected, boolean hovered, boolean focused) {
        TypeEditorDescriptor descriptor = EffectEditorRegistry.find(item);
        Component label = descriptor == null ? TypeLabels.effectLabel(item) : descriptor.label();
        String text = TextScroll.trimToWidth(pages.font, label.getString(), Math.max(1, row.width() - 4));
        graphics.drawString(pages.font, text, row.x() + 2, row.y() + 2, THEME.palette().textPrimary(), false);
    }

    void addEffectOfType(@Nullable ResourceLocation type) {
        EditSession session = pages.host.session();
        if (session == null || type == null) {
            return;
        }
        RuleDraft draft = session.draft();
        if (ResultStructure.totalEffectCount(draft.root()) >= ConditionLimits.MAX_EFFECTS) {
            pages.host.notice(Component.translatable(RuleEditorPageSupport.UI + "issue.candidate_limit"), THEME.palette().warning());
            return;
        }
        String listPath = ResultStructure.listPath(draft.root(), candidateIndex);
        RuleValue body = BuiltinEditorDefaults.effectBody(type);
        if (!pages.apply(EditSession.OP_ADD_EFFECT, () -> draft.setAt(listPath + "[" + draft.effectCountAt(listPath) + "]", body))) {
            return;
        }
        effectIndex = Math.max(0, draft.effectCountAt(listPath) - 1);
    }

    void duplicateEffect() {
        if (pages.blockNavigation()) {
            return;
        }
        EditSession session = pages.host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        if (ResultStructure.totalEffectCount(draft.root()) >= ConditionLimits.MAX_EFFECTS) {
            pages.host.notice(Component.translatable(RuleEditorPageSupport.UI + "issue.candidate_limit"), THEME.palette().warning());
            return;
        }
        String listPath = ResultStructure.listPath(draft.root(), candidateIndex);
        List<RuleValue> effects = draft.effectValuesAt(listPath);
        if (effectIndex < 0 || effectIndex >= effects.size()) {
            return;
        }
        RuleValue copy = effects.get(effectIndex);
        if (!pages.apply(EditSession.OP_ADD_EFFECT, () -> draft.setAt(listPath + "[" + draft.effectCountAt(listPath) + "]", copy))) {
            return;
        }
        effectIndex = Math.max(0, draft.effectCountAt(listPath) - 1);
    }

    void removeEffect() {
        EditSession session = pages.host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        String listPath = ResultStructure.listPath(draft.root(), candidateIndex);
        int index = effectIndex;
        if (index < 0 || index >= draft.effectCountAt(listPath)) {
            return;
        }
        if (!pages.apply(EditSession.OP_REMOVE_EFFECT, () -> draft.removeAt(listPath + "[" + index + "]"))) {
            return;
        }
        effectIndex = Math.max(0, index - 1);
    }

    void moveEffectBy(int delta) {
        int target = effectIndex + delta;
        if (delta == 0 || target < 0) {
            return;
        }
        EditSession session = pages.host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        String listPath = ResultStructure.listPath(draft.root(), candidateIndex);
        if (target >= draft.effectCountAt(listPath)) {
            return;
        }
        moveEffect(effectIndex, target);
    }

    void moveEffect(int from, int to) {
        EditSession session = pages.host.session();
        if (session == null || from == to) {
            return;
        }
        RuleDraft draft = session.draft();
        String listPath = ResultStructure.listPath(draft.root(), candidateIndex);
        boolean[] moved = {false};
        if (!pages.apply(EditSession.OP_MOVE_EFFECT, () -> moved[0] = draft.moveInList(listPath, from, to)) || !moved[0]) {
            return;
        }
        effectIndex = to;
    }

    void renderCandidateRow(GuiGraphics graphics, Font renderFont, Integer item, int index, UiRect row, boolean selected, boolean hovered, boolean focused) {
        RuleValue view = pages.currentView();
        RuleValue candidate = ResultStructure.candidateAt(view, item);
        // 方案标题统一走 RuleNaming（「方案 %s：%s」），不再出现「隐式结果」这类结构术语
        Component title = RuleNaming.candidateTitle(candidate, item + 1, pages.nameSource);
        String text = TextScroll.trimToWidth(pages.font, title.getString(), Math.max(1, row.width() - 4));
        graphics.drawString(renderFont, text, row.x() + 2, row.y() + (row.height() - renderFont.lineHeight) / 2,
                THEME.palette().textPrimary(), false);
    }

    void renderEffectRow(GuiGraphics graphics, Font renderFont, Integer item, int index, UiRect row, boolean selected, boolean hovered, boolean focused) {
        RuleValue view = pages.currentView();
        RuleValue array = ResultStructure.effectsAt(view, candidateIndex);
        RuleValue element = array != null && item >= 0 && item < array.size() ? array.item(item) : null;
        RuleValue effect = element != null && element.isMapping() ? element : null;
        Component title = RuleNaming.effectTitle(effect, pages.nameSource);
        String text = (item + 1) + ". " + title.getString();
        var icon = RulePreviewIcons.action(effect);
        int inset = icon == null ? 2 : 24;
        if (icon != null) icon.render(graphics, row.x() + 2, row.y() + (row.height() - DETAIL_ICON_SIZE) / 2);
        String trimmed = TextScroll.trimToWidth(pages.font, text, Math.max(1, row.width() - inset - 2));
        graphics.drawString(pages.font, trimmed, row.x() + inset, row.y() + 4, THEME.palette().textPrimary(), false);
        if (element != null) {
            int summaryWidth = row.width() - inset - 4;
            if (summaryWidth > 12) {
                String summary = TextScroll.trimToWidth(pages.font, NaturalSummary.effect(element).getString(), summaryWidth);
                graphics.drawString(pages.font, summary, row.x() + inset, row.y() + 16, THEME.palette().textSecondary(), false);
            }
        }
    }

    void switchProduct(String path, String variant) {
        EditSession session = pages.host.session();
        if (session == null || pages.blockNavigation()) return;
        RuleValue old = session.draft().valueAt(path);
        if (old == null || !old.isMapping()) return;
        RuleValue.Mapping next = RuleValue.mapping(old);
        for (String field : List.of("item", "entity", "count", "age", "amount", "per_source_item")) next.remove(field);
        next.put("variant", RuleValue.text(variant));
        if ("experience".equals(variant)) {
            next.put("amount", RuleValue.integer(1));
            next.put("per_source_item", RuleValue.flag(false));
        } else {
            next.put("count", RuleValue.integer(1));
            if ("entity".equals(variant)) next.put("age", RuleValue.integer(0));
        }
        if (pages.apply(EditSession.OP_SET_FIELD, () -> session.draft().setAt(path, next.build()))) pages.requestRebuild();
    }

    private Component advancedLabel() {
        return Component.translatable(RuleEditorPageSupport.UI
                + (showAdvancedEffects ? "results.advanced_open" : "results.advanced_closed"));
    }

    private void openPlanPicker() {
        if (pages.blockNavigation()) return;
        int count = Math.max(1, ResultStructure.candidateCount(pages.currentView()));
        UiListView<Integer> list = new UiListView<>(THEME, pages.font, this::renderCandidateRow);
        list.setRowHeight(ACTION_ROW_HEIGHT);
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < count; index++) indexes.add(index);
        list.setItems(indexes);
        list.setSelectedIndex(candidateIndex);
        list.setActivateOnSingleClick(true);
        UiModal modal = UiModal.create(THEME, pages.font)
                .title(Component.translatable(RuleEditorPageSupport.UI + "results.select_plan"));
        list.setOnActivate(index -> {
            if (pages.blockNavigation()) return;
            pages.host.modals().close(modal);
            if (candidateIndex != index) {
                candidateIndex = index;
                effectIndex = 0;
                showAdvancedEffects = false;
            }
            enterResultStage(1);
            pages.flushRebuild();
        });
        modal.contentWidget(list, Math.min(count, PLAN_PICKER_ROWS) * ACTION_ROW_HEIGHT + 2)
                .preferredWidth(PLAN_PICKER_WIDTH).cancel(Component.translatable(RuleEditorPageSupport.UI + "button.cancel"))
                .layoutCentered(pages.host.screenWidth(), pages.host.screenHeight());
        list.ensureVisible(candidateIndex);
        pages.host.modals().push(modal);
    }

    void enterResultStage(int stage) {
        if (pages.blockNavigation()) return;
        resultStage = Math.clamp(stage, 1, 2);
        pages.pageScroll.setOffset(0);
        pages.requestRebuild();
    }

    int layoutResults(UiRect area) {
        boolean split = area.width() >= NAVIGATION_MIN_WIDTH + DETAIL_MIN_WIDTH + RuleEditorPageSupport.CARD_GAP;
        boolean actions = split || resultStage == 1;
        boolean detail = split || resultStage == 2;
        RuleEditorPageSupport.show(effectList, actions);
        pages.effectButtons.forEach(button -> RuleEditorPageSupport.show(button, actions));
        RuleEditorPageSupport.show(openActionButton, !split && actions);
        RuleEditorPageSupport.show(resultBack, !split && detail);
        RuleEditorPageSupport.show(safeSpawnBox, detail && showAdvancedEffects);
        RuleEditorPageSupport.show(fillOriginBox, detail && showAdvancedEffects);
        RuleEditorPageSupport.show(effectForm, detail);
        RuleEditorPageSupport.show(variantControl, detail);
        RuleEditorPageSupport.show(advancedButton, detail);
        RuleEditorPageSupport.show(advancedEffectForm, detail && showAdvancedEffects);
        detailHeaderRect = null;
        emptyDetailRect = null;
        planStrategyRect = null;
        effectNoteRect = null;
        int y = layoutToolbar(area.x(), area.y(), area.width());
        if (!split) {
            if (detail && resultBack != null) {
                resultBack.setBounds(area.x(), y, area.width(), TOOLBAR_HEIGHT);
                y += TOOLBAR_HEIGHT + RuleEditorPageSupport.PAD;
            }
            int availableHeight = Math.max(0, area.bottom() - y);
            return actions ? layoutActions(area.x(), y, area.width(), availableHeight)
                    : layoutActionDetail(area.x(), y, area.width(), availableHeight);
        }
        int navigationWidth = Math.clamp(area.width() / 3, NAVIGATION_MIN_WIDTH, NAVIGATION_MAX_WIDTH);
        int detailX = area.x() + navigationWidth + RuleEditorPageSupport.CARD_GAP;
        int availableHeight = Math.max(0, area.bottom() - y);
        int navigationBottom = layoutActions(area.x(), y, navigationWidth, availableHeight);
        int detailBottom = layoutActionDetail(detailX, y, area.right() - detailX, availableHeight);
        return Math.max(navigationBottom, detailBottom);
    }

    // 方案管理始终在页首；工具条按实际文案换行，英文与小窗口不挤占动作参数宽度。
    private int layoutToolbar(int x, int y, int width) {
        List<UiWidget> controls = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        if (planSelector != null) {
            controls.add(planSelector);
            sizes.add(Math.clamp(planSelector.preferredWidth(RuleEditorPageSupport.PAD), Math.min(width, PLAN_SELECTOR_MIN_WIDTH), width));
        }
        if (combinationControl != null) {
            controls.add(combinationControl);
            int optionWidth = combinationControl.options().stream()
                    .mapToInt(option -> pages.font.width(option.label())).max().orElse(0);
            sizes.add(Math.min(width, (optionWidth + RuleEditorPageSupport.PAD * 2) * combinationControl.options().size()));
        }
        for (UiButton button : pages.candidateButtons) {
            controls.add(button);
            sizes.add(Math.min(width, button.preferredWidth(RuleEditorPageSupport.PAD)));
        }
        UiFlowLayout.Wrap flow = UiFlowLayout.wrap(sizes.stream().mapToInt(Integer::intValue).toArray(), width,
                RuleEditorPageSupport.BUTTON_GAP, TOOLBAR_HEIGHT);
        for (int index = 0; index < controls.size(); index++) {
            UiRect offset = flow.bounds(index);
            controls.get(index).setBounds(x + offset.x(), y + offset.y(), offset.width(), offset.height());
        }
        return y + flow.height() + RuleEditorPageSupport.CARD_GAP;
    }

    private int layoutActions(int x, int top, int width, int availableHeight) {
        int contentX = x + DETAIL_PADDING;
        int contentWidth = Math.max(0, width - DETAIL_PADDING * 2);
        int listY = top + DETAIL_HEADER_HEIGHT + DETAIL_PADDING;
        List<UiButton> footer = new ArrayList<>(pages.effectButtons);
        if (openActionButton != null && openActionButton.isVisible()) footer.addFirst(openActionButton);
        int footerHeight = pages.layoutButtonGroup(footer, contentX, listY, contentWidth);
        int bottom = Math.max(top + availableHeight, listY + ACTION_LIST_MIN_HEIGHT
                + RuleEditorPageSupport.BUTTON_GAP + footerHeight + DETAIL_PADDING);
        int footerY = bottom - DETAIL_PADDING - footerHeight;
        pages.layoutButtonGroup(footer, contentX, footerY, contentWidth);
        if (effectList != null) {
            effectList.setBounds(contentX, listY, contentWidth,
                    Math.max(0, footerY - RuleEditorPageSupport.BUTTON_GAP - listY));
            if (revealSelectedEffect) {
                effectList.ensureVisible(effectIndex);
                revealSelectedEffect = false;
            }
        }
        pages.cards.add(new RuleEditorEditPages.PageCard(new UiRect(x, top, width, bottom - top),
                Component.translatable(RuleEditorPageSupport.UI + "card.actions")));
        return bottom;
    }

    private int layoutActionDetail(int x, int top, int width, int availableHeight) {
        detailHeaderRect = new UiRect(x + DETAIL_PADDING, top + DETAIL_PADDING,
                Math.max(0, width - DETAIL_PADDING * 2), DETAIL_HEADER_HEIGHT - DETAIL_PADDING);
        int contentX = x + DETAIL_PADDING;
        int contentWidth = detailHeaderRect.width();
        int y = top + DETAIL_HEADER_HEIGHT + DETAIL_PADDING;
        boolean empty = effectList == null || effectList.size() == 0;
        if (!empty) {
            if (variantControl != null) {
                variantControl.setBounds(contentX, y, contentWidth, TOOLBAR_HEIGHT);
                y += TOOLBAR_HEIGHT + RuleEditorPageSupport.PAD;
            }
            if (readonlyEffectNote(pages.currentView()) != null) {
                effectNoteRect = new UiRect(contentX, y, contentWidth, RuleEditorPageSupport.LINE_H);
                y += RuleEditorPageSupport.LINE_H + RuleEditorPageSupport.PAD;
            }
            y = pages.layoutForm(effectForm, contentX, y, contentWidth);
            if (advancedButton != null) {
                advancedButton.setBounds(contentX, y, contentWidth, TOOLBAR_HEIGHT);
                y += TOOLBAR_HEIGHT + RuleEditorPageSupport.PAD;
            }
            if (showAdvancedEffects) {
                if (safeSpawnBox != null || fillOriginBox != null) {
                    planStrategyRect = new UiRect(contentX, y, contentWidth, RuleEditorPageSupport.LINE_H);
                    y += RuleEditorPageSupport.LINE_H + RuleEditorPageSupport.PAD;
                }
                if (safeSpawnBox != null) {
                    safeSpawnBox.setBounds(contentX, y, contentWidth, RuleEditorPageSupport.ROW_H);
                    y += RuleEditorPageSupport.ROW_H + RuleEditorPageSupport.PAD;
                }
                if (fillOriginBox != null) {
                    fillOriginBox.setBounds(contentX, y, contentWidth, RuleEditorPageSupport.ROW_H);
                    y += RuleEditorPageSupport.ROW_H + RuleEditorPageSupport.PAD;
                }
                y = pages.layoutForm(advancedEffectForm, contentX, y, contentWidth);
            }
        } else {
            int messageHeight = pages.font.lineHeight + EMPTY_MESSAGE_GAP + pages.font.split(
                    Component.translatable(RuleEditorPageSupport.UI + "results.empty_help"), Math.max(1, contentWidth)).size()
                    * (pages.font.lineHeight + 2);
            y += messageHeight + DETAIL_PADDING;
        }
        int bottom = Math.max(top + availableHeight, y + DETAIL_PADDING);
        if (empty) emptyDetailRect = new UiRect(contentX, top + DETAIL_HEADER_HEIGHT + DETAIL_PADDING,
                contentWidth, Math.max(0, bottom - top - DETAIL_HEADER_HEIGHT - DETAIL_PADDING * 2));
        pages.cards.add(new RuleEditorEditPages.PageCard(new UiRect(x, top, width, bottom - top), null));
        return bottom;
    }

    void openDiagram() {
        if (pages.blockNavigation()) {
            return;
        }
        EditSession session = pages.host.session();
        if (session == null) {
            return;
        }
        UiStructureDiagram diagram = RuleEditorPanelFactory.structureDiagram(pages.font, session.draft().root(), pages.nameSource,
                candidateIndex, this::navigateDiagram);
        UiModal modal = UiModal.create(RuleEditorEditPages.THEME, pages.font);
        modal.title(Component.translatable(RuleEditorPageSupport.UI + "button.diagram"));
        modal.message(Component.translatable(RuleEditorPageSupport.UI + "diagram.note"));
        modal.preferredWidth(Math.clamp(pages.host.screenWidth() - 16, 200, 320));
        modal.contentWidget(diagram, 160);
        modal.cancel(Component.translatable(RuleEditorPageSupport.UI + "button.cancel"));
        modal.layoutCentered(pages.host.screenWidth(), pages.host.screenHeight());
        pages.host.modals().push(modal);
    }

    void navigateDiagram(String nodeId) {
        if (nodeId == null) {
            return;
        }
        if ("source".equals(nodeId) || "catalyst".equals(nodeId)) {
            pages.setPage(RuleEditorEditPages.Page.INPUT);
            return;
        }
        if (nodeId.startsWith("candidate:")) {
            candidateIndex = RuleEditorPageSupport.intOr(nodeId.substring("candidate:".length()), candidateIndex);
            resultStage = 1;
            pages.setPage(RuleEditorEditPages.Page.RESULTS);
            return;
        }
        if (nodeId.startsWith("effect:")) {
            String rest = nodeId.substring("effect:".length());
            int colon = rest.lastIndexOf(':');
            if (colon > 0) {
                int candidate = RuleEditorPageSupport.indexIn(rest.substring(0, colon), RuleFields.OUTCOMES);
                if (candidate >= 0) {
                    candidateIndex = candidate;
                }
                effectIndex = RuleEditorPageSupport.intOr(rest.substring(colon + 1), effectIndex);
            }
            resultStage = 2;
            pages.setPage(RuleEditorEditPages.Page.RESULTS);
        }
    }

    void renderResults(GuiGraphics graphics) {
        if (detailHeaderRect != null) {
            UiIcon icon = selectedAction == null ? null : RulePreviewIcons.action(selectedAction);
            int titleX = detailHeaderRect.x();
            if (icon != null) {
                icon.render(graphics, titleX, detailHeaderRect.y());
                titleX += DETAIL_ICON_SIZE + RuleEditorPageSupport.PAD;
            }
            Component title = emptyDetailRect != null ? Component.translatable(RuleEditorPageSupport.UI + "card.action_detail")
                    : RuleNaming.effectTitle(selectedAction, pages.nameSource);
            var titleLines = pages.font.split(title.copy().withStyle(net.minecraft.ChatFormatting.BOLD),
                    Math.max(1, detailHeaderRect.right() - titleX));
            if (!titleLines.isEmpty()) graphics.drawString(pages.font, titleLines.getFirst(), titleX,
                    detailHeaderRect.y() + RuleEditorPageSupport.PAD, THEME.palette().textPrimary(), false);
            THEME.drawDivider(graphics, detailHeaderRect.x(), detailHeaderRect.bottom(), detailHeaderRect.width());
        }
        if (emptyDetailRect != null) {
            var lines = pages.font.split(Component.translatable(RuleEditorPageSupport.UI + "results.empty_help"),
                    Math.max(1, emptyDetailRect.width()));
            int textHeight = pages.font.lineHeight + EMPTY_MESSAGE_GAP + lines.size() * (pages.font.lineHeight + 2);
            int y = emptyDetailRect.y() + Math.max(0, (emptyDetailRect.height() - textHeight) / 2);
            pages.drawText(graphics, new UiRect(emptyDetailRect.x(), y, emptyDetailRect.width(), pages.font.lineHeight),
                    Component.translatable(RuleEditorPageSupport.UI + "results.empty_title"), THEME.palette().textPrimary());
            y += pages.font.lineHeight + EMPTY_MESSAGE_GAP;
            for (var line : lines) {
                graphics.drawString(pages.font, line, emptyDetailRect.x(), y, THEME.palette().textSecondary(), false);
                y += pages.font.lineHeight + 2;
            }
        }
        Component readonlyNote = readonlyEffectNote(pages.currentView());
        if (readonlyNote != null) pages.drawText(graphics, effectNoteRect, readonlyNote, THEME.palette().warning());
        pages.drawText(graphics, planStrategyRect, Component.translatable(RuleEditorPageSupport.UI + "results.plan_strategy"),
                THEME.palette().textSecondary());
    }

    @Nullable Component tooltipAt(double mouseX, double mouseY) {
        if (effectList != null && effectList.isVisible()) {
            int index = effectList.itemIndexAt(mouseX, mouseY);
            RuleValue effects = ResultStructure.effectsAt(pages.currentView(), candidateIndex);
            if (effects != null && index >= 0 && index < effects.size()) {
                RuleValue effect = effects.item(index);
                return actionTooltip(effect);
            }
        }
        if (detailHeaderRect != null && detailHeaderRect.contains(mouseX, mouseY) && selectedAction != null)
            return actionTooltip(selectedAction);
        if (planSelector != null && planSelector.bounds().contains(mouseX, mouseY))
            return RuleNaming.candidateTitle(ResultStructure.candidateAt(pages.currentView(), candidateIndex),
                    candidateIndex + 1, pages.nameSource).copy().append("\n")
                    .append(Component.translatable(RuleEditorPageSupport.UI + "results.select_plan"));
        if (combinationControl != null && combinationControl.bounds().contains(mouseX, mouseY))
            return Component.translatable(RuleEditorPageSupport.UI + "results.combination_help");
        if (variantControl != null && variantControl.isVisible() && variantControl.bounds().contains(mouseX, mouseY))
            return Component.translatable(RuleEditorPageSupport.UI + "results.product_help");
        if (effectNoteRect != null && effectNoteRect.contains(mouseX, mouseY)) return readonlyEffectNote(pages.currentView());
        return null;
    }

    private Component actionTooltip(@Nullable RuleValue effect) {
        var tooltip = RuleNaming.effectTitle(effect, pages.nameSource).copy().append("\n").append(NaturalSummary.effect(effect));
        if (effect != null && effect.has("entity")
                && EntityPreviewIcons.unavailable(effect.text("entity", ""), effect.integer("age", 0)))
            tooltip.append("\n").append(Component.translatable(RuleEditorPageSupport.UI + "preview.unavailable"));
        return tooltip;
    }

    @Nullable Component readonlyEffectNote(@Nullable RuleValue view) {
        RuleValue effects = ResultStructure.effectsAt(view, candidateIndex);
        if (effects == null || effectIndex < 0 || effectIndex >= effects.size()) {
            return null;
        }
        String type = RuleCostBinding.typeOf(effects.item(effectIndex));
        ResourceLocation typeId = type == null ? null : ResourceLocation.tryParse(type);
        if (typeId == null) {
            return Component.translatable(RuleEditorPageSupport.UI + "effect.readonly_note_unknown");
        }
        if (EffectEditorRegistry.find(typeId) != null) {
            return null;
        }
        return Component.translatable(RuleEditorPageSupport.UI + "effect.readonly_note", typeId.toString());
    }
}
