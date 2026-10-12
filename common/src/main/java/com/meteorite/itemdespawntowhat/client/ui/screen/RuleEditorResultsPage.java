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
 * 结果页：方案列表、效果列表、组合模式和动作参数，以及结构图解入口。
 */
final class RuleEditorResultsPage {

    // 所属编辑页容器：共享状态与跨页能力都经它访问
    private final RuleEditorEditPages pages;

    // 主题：与容器共用宿主默认主题
    private static final UiHostTheme THEME = RuleEditorEditPages.THEME;

    RuleEditorResultsPage(RuleEditorEditPages pages) {
        this.pages = pages;
    }

    // 清理本页持有的控件引用（重建/切页时由容器调用）
    void reset() {
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
    }

    int candidateIndex;

    int effectIndex;

    @Nullable UiListView<Integer> candidateList;

    @Nullable UiCheckBox safeSpawnBox;

    @Nullable UiCheckBox fillOriginBox;

    @Nullable UiSegmentedControl combinationControl;

    @Nullable UiRect combinationLabelRect;

    @Nullable UiListView<Integer> effectList;

    @Nullable FormView effectForm;

    @Nullable UiSegmentedControl variantControl;

    @Nullable FormView advancedEffectForm;

    @Nullable UiButton advancedButton;

    @Nullable UiButton resultBack;

    boolean showAdvancedEffects;

    boolean singlePlan;

    int resultStage;

    @Nullable UiRect productPreviewRect;

    @Nullable RuleValue selectedAction;

    @Nullable UiRect effectNoteRect;

    void buildResults(EditSession session) {
        RuleDraft draft = session.draft();
        RuleValue rule = draft.root();
        int candidates = Math.max(1, ResultStructure.candidateCount(rule));
        candidateIndex = Math.clamp(candidateIndex, 0, candidates - 1);
        // 单方案（顶层 effects）：结果页直达动作与产出参数，不渲染方案选择层
        singlePlan = candidates == 1;
        if (singlePlan) {
            resultStage = Math.max(1, resultStage);
        }

        candidateList = new UiListView<>(RuleEditorEditPages.THEME, pages.font, this::renderCandidateRow);
        candidateList.setRowHeight(RuleEditorPageSupport.ROW_H);
        candidateList.setEmptyMessage(Component.translatable(RuleEditorPageSupport.UI + "candidate.flat_note"));
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < candidates; i++) {
            indexes.add(i);
        }
        candidateList.setItems(indexes);
        pages.rebuilding = true;
        candidateList.setSelectedIndex(candidateIndex);
        pages.rebuilding = false;
        candidateList.setOnSelectionChanged(index -> {
            if (pages.rebuilding) {
                return;
            }
            if (pages.blockNavigation()) {
                pages.rebuilding = true;
                if (candidateList != null) {
                    candidateList.setSelectedIndex(candidateIndex);
                }
                pages.rebuilding = false;
                return;
            }
            candidateIndex = Math.max(0, index);
            effectIndex = 0;
            pages.requestRebuild();
        });
        candidateList.setOnActivate(index -> enterResultStage(1));
        if (!singlePlan) {
            // 单方案没有方案层：列表不登记为控件，不可见也不可聚焦
            pages.liveWidgets.add(candidateList);
        }
        resultBack = new UiButton(RuleEditorEditPages.THEME, pages.font, Component.translatable(RuleEditorPageSupport.UI + "button.result_plans"), UiButtonVariant.SECONDARY,
                () -> enterResultStage(resultStage == 2 ? 1 : 0));
        pages.liveWidgets.add(resultBack);
        pages.buttons.add(resultBack);
        if (!singlePlan && pages.host.screenWidth() < RuleEditorPageSupport.NARROW_WIDTH) pages.addCandidateButton("button.candidate_edit", () -> enterResultStage(1));

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
        effectList.setRowHeight(24);
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
        if (pages.host.screenWidth() < RuleEditorPageSupport.NARROW_WIDTH && effectCount > 0) pages.addEffectButton("button.effect_edit", () -> enterResultStage(2));

        RuleValue array = ResultStructure.effectsAt(rule, candidateIndex);
        if (array != null && effectIndex >= 0 && effectIndex < array.size()) {
            RuleValue element = array.item(effectIndex);
            String type = RuleCostBinding.typeOf(element);
            ResourceLocation typeId = type == null ? null : ResourceLocation.tryParse(type);
            if (typeId != null) {
                String actionPath = listPath + "[" + effectIndex + "]";
                TypeEditorDescriptor descriptor = EffectEditorRegistry.descriptorFor(typeId);
                if (typeId.equals(ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, "spawn_entity"))
                        && element.isMapping()) {
                    String variant = element.text("variant", "item");
                    descriptor = BuiltinEditorDescriptors.entityDescriptor(variant);
                    variantControl = new UiSegmentedControl(RuleEditorEditPages.THEME, pages.font, List.of(
                            new UiSegmentedControl.Option("item", Component.translatable(RuleEditorPageSupport.UI + "product.item")),
                            new UiSegmentedControl.Option("entity", Component.translatable(RuleEditorPageSupport.UI + "product.entity")),
                            new UiSegmentedControl.Option("experience", Component.translatable(RuleEditorPageSupport.UI + "product.experience"))));
                    variantControl.setSelected(variant);
                    variantControl.setOnChanged(value -> switchProduct(actionPath, value));
                    pages.liveWidgets.add(variantControl);
                }
                selectedAction = element.isMapping() ? element : null;
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
                pages.liveForms.add(effectForm);
                pages.liveWidgets.add(effectForm);
                if (!advanced.isEmpty()) {
                    advancedEffectForm = pages.newForm(session, actionPath, new TypeEditorDescriptor(descriptor.id(), descriptor.label(), advanced, false));
                    pages.liveForms.add(advancedEffectForm); pages.liveWidgets.add(advancedEffectForm);
                    advancedButton = new UiButton(RuleEditorEditPages.THEME, pages.font, Component.translatable(RuleEditorPageSupport.UI + "button.advanced"), UiButtonVariant.SECONDARY, () -> {
                        if (pages.blockNavigation()) return;
                        showAdvancedEffects = !showAdvancedEffects; pages.requestRebuild();
                    });
                    pages.liveWidgets.add(advancedButton); pages.buttons.add(advancedButton);
                }
            }
        }

        pages.addCandidateButton("button.candidate_add", this::addCandidate);
        pages.addCandidateButton("button.more", () -> openResultMore(true));
        pages.addEffectButton("button.effect_add", this::openEffectPicker);
        if (effectCount > 0) pages.addEffectButton("button.more", () -> openResultMore(false));
    }

    record MenuAction(Component label, Runnable action) { }

    void openResultMore(boolean candidate) {
        if (pages.blockNavigation()) return;
        List<MenuAction> actions = new ArrayList<>();
        if (candidate) {
            actions.add(new MenuAction(Component.translatable(RuleEditorPageSupport.UI + "button.diagram"), this::openDiagram));
            actions.add(new MenuAction(Component.translatable(RuleEditorPageSupport.UI + "button.candidate_duplicate"), this::duplicateCandidate));
            actions.add(new MenuAction(Component.translatable(RuleEditorPageSupport.UI + "button.candidate_up"), () -> moveCandidateBy(-1)));
            actions.add(new MenuAction(Component.translatable(RuleEditorPageSupport.UI + "button.candidate_down"), () -> moveCandidateBy(1)));
            if (ResultStructure.hasOutcomes(pages.currentView())) actions.add(new MenuAction(Component.translatable(RuleEditorPageSupport.UI + "button.candidate_remove"), this::removeCandidate));
        } else {
            actions.add(new MenuAction(Component.translatable(RuleEditorPageSupport.UI + "button.effect_duplicate"), this::duplicateEffect));
            actions.add(new MenuAction(Component.translatable(RuleEditorPageSupport.UI + "button.effect_up"), () -> moveEffectBy(-1)));
            actions.add(new MenuAction(Component.translatable(RuleEditorPageSupport.UI + "button.effect_down"), () -> moveEffectBy(1)));
            actions.add(new MenuAction(Component.translatable(RuleEditorPageSupport.UI + "button.effect_remove"), this::removeEffect));
        }
        UiListView<MenuAction> list = new UiListView<>(RuleEditorEditPages.THEME, pages.font, (graphics, rowFont, item, index, row, selected, hovered, focused) ->
                graphics.drawString(rowFont, item.label(), row.x() + 2, row.y() + 2, THEME.palette().textPrimary(), false));
        list.setItems(actions);
        list.setActivateOnSingleClick(true);
        UiModal modal = UiModal.create(RuleEditorEditPages.THEME, pages.font).title(Component.translatable(RuleEditorPageSupport.UI + "button.more"));
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
        if (selected) {
            THEME.drawSelection(graphics, row);
        }
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
        if (selected) {
            THEME.drawSelection(graphics, row);
        }
        RuleValue view = pages.currentView();
        RuleValue candidate = ResultStructure.candidateAt(view, item);
        // 方案标题统一走 RuleNaming（「方案 %s：%s」），不再出现「隐式结果」这类结构术语
        Component title = RuleNaming.candidateTitle(candidate, item + 1, pages.nameSource);
        String text = TextScroll.trimToWidth(pages.font, title.getString(), Math.max(1, row.width() - 4));
        graphics.drawString(pages.font, text, row.x() + 2, row.y() + 2, focused ? THEME.palette().textPrimary() : THEME.palette().textSecondary(), false);
    }

    void renderEffectRow(GuiGraphics graphics, Font renderFont, Integer item, int index, UiRect row, boolean selected, boolean hovered, boolean focused) {
        if (selected) {
            THEME.drawSelection(graphics, row);
        }
        RuleValue view = pages.currentView();
        RuleValue array = ResultStructure.effectsAt(view, candidateIndex);
        RuleValue element = array != null && item >= 0 && item < array.size() ? array.item(item) : null;
        RuleValue effect = element != null && element.isMapping() ? element : null;
        Component title = RuleNaming.effectTitle(effect, pages.nameSource);
        String text = (item + 1) + ". " + title.getString();
        var icon = RulePreviewIcons.action(effect);
        int inset = icon == null ? 2 : 24;
        if (icon != null) icon.render(graphics, row.x() + 2, row.y() + 2);
        String trimmed = TextScroll.trimToWidth(pages.font, text, Math.max(1, row.width() - inset - 2));
        graphics.drawString(pages.font, trimmed, row.x() + inset, row.y() + 2, THEME.palette().textPrimary(), false);
        if (element != null) {
            int summaryWidth = row.width() - inset - 4;
            if (summaryWidth > 12) {
                String summary = TextScroll.trimToWidth(pages.font, NaturalSummary.effect(element).getString(), summaryWidth);
                graphics.drawString(pages.font, summary, row.x() + inset, row.y() + 13, THEME.palette().textSecondary(), false);
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

    void enterResultStage(int stage) {
        if (pages.blockNavigation()) return;
        // 单方案没有方案层，最小层级是动作列表
        resultStage = singlePlan ? Math.clamp(stage, 1, 2) : Math.clamp(stage, 0, 2);
        pages.pageScroll.setOffset(0);
        pages.requestRebuild();
    }

    int layoutResults(UiRect area) {
        boolean wide = area.width() >= RuleEditorPageSupport.NARROW_WIDTH;
        // 单方案没有方案层：窄屏最低层级是动作列表，宽屏左列直接是动作列表
        boolean plans = !singlePlan && (wide || resultStage == 0);
        boolean actions = wide || resultStage == 1;
        boolean detail = wide || resultStage == 2;
        boolean backVisible = !wide && resultStage > (singlePlan ? 1 : 0);
        RuleEditorPageSupport.show(candidateList, plans);
        pages.candidateButtons.forEach(button -> RuleEditorPageSupport.show(button, plans || (singlePlan && actions)));
        RuleEditorPageSupport.show(combinationControl, plans);
        RuleEditorPageSupport.show(effectList, actions);
        pages.effectButtons.forEach(button -> RuleEditorPageSupport.show(button, actions));
        RuleEditorPageSupport.show(safeSpawnBox, detail && showAdvancedEffects); RuleEditorPageSupport.show(fillOriginBox, detail && showAdvancedEffects);
        RuleEditorPageSupport.show(effectForm, detail); RuleEditorPageSupport.show(variantControl, detail); RuleEditorPageSupport.show(advancedButton, detail);
        RuleEditorPageSupport.show(advancedEffectForm, detail && showAdvancedEffects);
        RuleEditorPageSupport.show(resultBack, backVisible);
        combinationLabelRect = null; productPreviewRect = null; effectNoteRect = null;
        int x = area.x(), y = area.y();
        if (backVisible && resultBack != null) {
            resultBack.setLabel(Component.translatable(RuleEditorPageSupport.UI + (resultStage == 2 ? "button.result_actions" : "button.result_plans")));
            resultBack.setBounds(x, y, area.width(), RuleEditorPageSupport.BUTTON_H); y += RuleEditorPageSupport.BUTTON_H + RuleEditorPageSupport.PAD;
        }
        if (!wide) {
            if (resultStage == 0) return layoutPlans(x, y, area.width());
            if (resultStage == 1) return layoutActions(x, y, area.width(), singlePlan);
            return layoutActionDetail(x, y, area.width());
        }
        int navigationWidth = Math.clamp(area.width() * 36L / 100, 120, 200);
        int navY = y;
        if (!singlePlan) {
            navY = layoutPlans(x, navY, navigationWidth) + RuleEditorPageSupport.PAD;
        }
        navY = layoutActions(x, navY, navigationWidth, singlePlan);
        int detailX = x + navigationWidth + RuleEditorPageSupport.CARD_GAP;
        return Math.max(navY, layoutActionDetail(detailX, y, area.right() - detailX));
    }

    int layoutPlans(int x, int y, int width) {
        int cardX = x, top = y, cardWidth = width;
        x += RuleEditorPageSupport.CARD_PAD;
        y += RuleEditorPageSupport.CARD_PAD + RuleEditorPageSupport.CARD_HEADER_H;
        width = Math.max(0, width - RuleEditorPageSupport.CARD_PAD * 2);
        if (combinationControl != null) {
            combinationLabelRect = new UiRect(x, y, width, RuleEditorPageSupport.LINE_H); y += RuleEditorPageSupport.LINE_H;
            combinationControl.setBounds(x, y, width, RuleEditorPageSupport.ROW_H); y += RuleEditorPageSupport.ROW_H + RuleEditorPageSupport.PAD;
        }
        int listHeight = Math.clamp(candidateList == null ? 1 : candidateList.size(), 1, RuleEditorPageSupport.LIST_ROWS) * RuleEditorPageSupport.ROW_H + 2;
        if (candidateList != null) candidateList.setBounds(x, y, width, listHeight);
        y += listHeight + RuleEditorPageSupport.BUTTON_GAP;
        y += pages.layoutButtonGroup(pages.candidateButtons, x, y, width);
        return pages.finishCard(cardX, top, cardWidth, y, "card.plans");
    }

    int layoutActions(int x, int y, int width, boolean withCandidateButtons) {
        int cardX = x, top = y, cardWidth = width;
        x += RuleEditorPageSupport.CARD_PAD;
        y += RuleEditorPageSupport.CARD_PAD + RuleEditorPageSupport.CARD_HEADER_H;
        width = Math.max(0, width - RuleEditorPageSupport.CARD_PAD * 2);
        int listHeight = Math.max(74, Math.clamp(effectList == null ? 1 : effectList.size(), 1, RuleEditorPageSupport.LIST_ROWS) * 24 + 2);
        if (effectList != null) effectList.setBounds(x, y, width, listHeight);
        y += listHeight + RuleEditorPageSupport.BUTTON_GAP;
        y += pages.layoutButtonGroup(pages.effectButtons, x, y, width);
        if (withCandidateButtons) {
            y += pages.layoutButtonGroup(pages.candidateButtons, x, y, width) + RuleEditorPageSupport.BUTTON_GAP;
        }
        return pages.finishCard(cardX, top, cardWidth, y, "card.actions");
    }

    int layoutActionDetail(int x, int y, int width) {
        int cardX = x, top = y, cardWidth = width;
        x += RuleEditorPageSupport.CARD_PAD;
        y += RuleEditorPageSupport.CARD_PAD + RuleEditorPageSupport.CARD_HEADER_H;
        width = Math.max(0, width - RuleEditorPageSupport.CARD_PAD * 2);
        if (variantControl != null) { variantControl.setBounds(x, y, width, RuleEditorPageSupport.ROW_H); y += RuleEditorPageSupport.ROW_H + RuleEditorPageSupport.PAD; }
        if (selectedAction != null && RulePreviewIcons.action(selectedAction) != null) {
            boolean entityPreview = RulePreviewIcons.action(selectedAction) instanceof com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon.Rendered;
            int previewHeight = entityPreview ? 60 : 24;
            productPreviewRect = new UiRect(x, y, Math.min(entityPreview ? 72 : 24, width), previewHeight);
            y += previewHeight + RuleEditorPageSupport.PAD;
        }
        if (pages.currentView() != null && readonlyEffectNote(pages.currentView()) != null) { effectNoteRect = new UiRect(x, y, width, RuleEditorPageSupport.LINE_H); y += RuleEditorPageSupport.LINE_H + RuleEditorPageSupport.PAD; }
        y = pages.layoutForm(effectForm, x, y, width);
        if (showAdvancedEffects && safeSpawnBox != null) { safeSpawnBox.setBounds(x, y, width, RuleEditorPageSupport.ROW_H); y += RuleEditorPageSupport.ROW_H + RuleEditorPageSupport.PAD; }
        if (showAdvancedEffects && fillOriginBox != null) { fillOriginBox.setBounds(x, y, width, RuleEditorPageSupport.ROW_H); y += RuleEditorPageSupport.ROW_H + RuleEditorPageSupport.PAD; }
        if (advancedButton != null) { advancedButton.setBounds(x, y, width, RuleEditorPageSupport.BUTTON_H); y += RuleEditorPageSupport.BUTTON_H + RuleEditorPageSupport.PAD; }
        if (showAdvancedEffects) y = pages.layoutForm(advancedEffectForm, x, y, width);
        return pages.finishCard(cardX, top, cardWidth, y, "card.action_detail");
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
            pages.setPage(RuleEditorEditPages.Page.RESULTS);
        }
    }

    void renderResults(GuiGraphics graphics) {
        if (productPreviewRect != null && selectedAction != null && pages.lastArea != null) {
            THEME.drawInset(graphics, productPreviewRect);
            var icon = RulePreviewIcons.action(selectedAction);
            if (icon instanceof com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon.Rendered rendered) {
                rendered.painter().render(graphics, productPreviewRect);
            } else if (icon != null) icon.render(graphics, productPreviewRect.x() + (productPreviewRect.width() - 16) / 2,
                    productPreviewRect.y() + (productPreviewRect.height() - 16) / 2);
            int labelX = productPreviewRect.right() + RuleEditorPageSupport.PAD;
            int labelWidth = Math.max(0, pages.cards.getLast().bounds().right() - RuleEditorPageSupport.CARD_PAD - labelX);
            pages.drawText(graphics, new UiRect(labelX, productPreviewRect.y() + 4, labelWidth, RuleEditorPageSupport.LINE_H),
                    RuleNaming.effectTitle(selectedAction, pages.nameSource), THEME.palette().textPrimary());
            if (selectedAction.has("entity")) {
                RuleValue entity = selectedAction.child("entity");
                String id = entity == null ? "" : entity.asText();
                int age = selectedAction.integer("age", 0);
                if (EntityPreviewIcons.unavailable(id, age)) pages.drawText(graphics,
                        new UiRect(productPreviewRect.right() + RuleEditorPageSupport.PAD, productPreviewRect.y(), Math.max(0, pages.lastArea.right() - productPreviewRect.right() - RuleEditorPageSupport.PAD), 20),
                        Component.translatable(RuleEditorPageSupport.UI + "preview.unavailable"), THEME.palette().textSecondary());
            }
        }
        pages.drawText(graphics, combinationLabelRect, Component.translatable(RuleEditorPageSupport.UI + "rule.combination"), THEME.palette().textSecondary());
        RuleValue view = pages.currentView();
        if (view == null) {
            return;
        }
        // 不再用「本规则使用顶层 effects」这类结构说明；单方案直接进入动作层
        Component readonlyNote = readonlyEffectNote(view);
        if (readonlyNote != null) {
            pages.drawText(graphics, effectNoteRect, readonlyNote, THEME.palette().warning());
        }
    }

    @Nullable Component readonlyEffectNote(RuleValue view) {
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
