package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDefaults;
import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDescriptors;
import com.meteorite.itemdespawntowhat.client.edit.DraftPaths;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorFactories;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorFieldType;
import com.meteorite.itemdespawntowhat.client.edit.EffectEditorRegistry;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleNaming;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.net.EditorOpenRequest;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiAction;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFlowLayout;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLinkedValues;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiMarkIcons;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCheckBox;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiDescriptionTooltip;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiListView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiSegmentedControl;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTypePicker;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.config.ServerConfig;
import com.meteorite.itemdespawntowhat.core.model.CombinationMode;
import com.meteorite.itemdespawntowhat.core.model.ConditionLimits;
import com.meteorite.itemdespawntowhat.core.type.effect.PlaceBlockEffect;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * 结果页：顶部管理方案，左侧选择执行动作，右侧编辑参数；窄屏在动作和参数之间导航。
 */
final class RuleEditorResultsPage {

    // 所属编辑页容器：共享状态与跨页能力都经它访问
    private final RuleEditorEditPages pages;

    // 效果行展示缓存：会话版本、方案、语言与行宽任一变化才重算，绘制不再逐行解析 JSON、拼图标 key 与裁剪
    private @Nullable EditSession effectRowSession;
    private long effectRowRevision = -1;
    private int effectRowCandidate = -1;
    private @Nullable Object effectRowLanguage;
    private int effectRowWidth = -1;
    private final Map<Integer, EffectRowText> effectRows = new HashMap<>();

    // 只读提示缓存：同一会话版本里结果不变，只有版本/方案/选中动作/语言变化才重算
    private @Nullable EditSession readonlyNoteSession;
    private long readonlyNoteRevision = -1;
    private int readonlyNoteCandidate = -1;
    private int readonlyNoteEffect = -1;
    private @Nullable Object readonlyNoteLanguage;
    private boolean readonlyNoteCached;
    private @Nullable Component readonlyNoteValue;

    // 详情卡头部缓存：空态、所选动作、语言与字体任一变化才重建
    private boolean headerCached;
    private boolean headerEmpty;
    private @Nullable RuleValue headerAction;
    private @Nullable Object headerLanguage;
    private @Nullable RuleEditorEditPages.CardHeader headerValue;

    // 空态引导文案的折行结果：布局与绘制共用，宽度或语言变化才重算
    private int emptyHelpWidth = -1;
    private @Nullable Object emptyHelpLanguage;
    private List<FormattedCharSequence> emptyHelpLines = List.of();

    // 固定文案与组合方式最大宽：语言变化时统一刷新，避免每帧新建 Component 并测宽
    private @Nullable Object staticTextLanguage;
    private Component emptyTitleText = Component.empty();
    private Component actionParametersText = Component.empty();
    private Component readonlyUnknownText = Component.empty();
    private int combinationOptionWidth;

    // 一条效果行的绘制数据：文本已裁剪，图标与文本内缩一并缓存
    private record EffectRowText(String text, @Nullable UiIcon icon, int inset) {}

    // 主题：与容器共用宿主默认主题
    private static final UiHostTheme THEME = RuleEditorEditPages.THEME;
    private static final int NAVIGATION_MIN_WIDTH = 128;
    private static final int NAVIGATION_MAX_WIDTH = 184;
    private static final int DETAIL_MIN_WIDTH = 212;
    private static final int ACTION_ROW_HEIGHT = 28;
    private static final int ACTION_CONTENT_INSET = 4;
    private static final int ACTION_ICON_TEXT_GAP = 4;
    private static final int ACTION_REMOVE_SIZE = 14;
    private static final int ACTION_REMOVE_INSET = 2;
    private static final int ACTION_LIST_MIN_HEIGHT = ACTION_ROW_HEIGHT * 2 + 2;
    private static final int TOOLBAR_HEIGHT = 24;
    private static final int DETAIL_HEADER_HEIGHT = 26;
    private static final int DETAIL_ICON_SIZE = 18;
    private static final int DETAIL_PADDING = 6;
    private static final int EMPTY_MESSAGE_GAP = 8;
    RuleEditorResultsPage(RuleEditorEditPages pages) {
        this.pages = pages;
        typePicker = new UiTypePicker(UiHostThemes.flatTheme(), pages.font,
                Component.translatable(RuleEditorPageSupport.UI + "pick.type_title"), type -> {
                    addEffectOfType(type);
                    pages.flushRebuild();
                });
    }

    final UiTypePicker typePicker;

    // 清理本页持有的控件引用（重建/切页时由容器调用）
    void reset() {
        typePicker.close();
        planPager = null;
        safeSpawnBox = null;
        fillOriginBox = null;
        combinationButton = null;
        deletePlanButton = null;
        movePlanUpButton = null;
        movePlanDownButton = null;
        effectList = null;
        effectForm = null;
        variantControl = null;
        advancedEffectForm = null;
        advancedButton = null;
        resultBack = null;
        detailHeaderRect = null;
        emptyDetailRect = null;
        actionParametersRect = null;
        selectedAction = null;
        effectNoteRect = null;
        openActionButton = null;
        emptyAddButton = null;
    }

    int candidateIndex;

    int effectIndex;

    private @Nullable PlanPager planPager;

    @Nullable UiCheckBox safeSpawnBox;

    @Nullable UiCheckBox fillOriginBox;

    @Nullable UiButton combinationButton;
    private @Nullable UiButton deletePlanButton;
    private @Nullable UiButton movePlanUpButton;
    private @Nullable UiButton movePlanDownButton;

    @Nullable UiListView<Integer> effectList;

    @Nullable FormView effectForm;

    @Nullable UiSegmentedControl variantControl;

    @Nullable FormView advancedEffectForm;

    @Nullable UiButton advancedButton;

    @Nullable UiButton resultBack;

    boolean showAdvancedEffects;

    int resultStage;

    private @Nullable UiButton openActionButton;

    // 空态卡内的「添加动作」入口（仅在没有动作时创建）
    private @Nullable UiButton emptyAddButton;

    private @Nullable UiRect detailHeaderRect;

    private @Nullable UiRect emptyDetailRect;

    private @Nullable UiRect actionParametersRect;

    // place_block 的 radius→limit 联动：记录上次绑定的动作路径与会话，用于判断何时清除手动标记
    private @Nullable String placeBlockLinkPath;
    private @Nullable EditSession placeBlockLinkSession;
    // 上限是否已被手动改过：同一动作重建表单后沿用，避免一次手改后又被半径联动覆盖
    private boolean placeBlockLimitManual;

    private boolean revealSelectedEffect;

    @Nullable RuleValue selectedAction;

    @Nullable UiRect effectNoteRect;

    void buildResults(EditSession session) {
        RuleDraft draft = session.draft();
        RuleValue rule = draft.root();
        int candidates = Math.max(1, ResultStructure.candidateCount(rule));
        candidateIndex = Math.clamp(candidateIndex, 0, candidates - 1);
        resultStage = Math.clamp(resultStage, 1, 2);
        planPager = new PlanPager(candidates);
        pages.liveWidgets.add(planPager);
        pages.liveWidgets.add(planPager.previous);
        pages.liveWidgets.add(planPager.next);
        pages.buttons.add(planPager.previous);
        pages.buttons.add(planPager.next);
        resultBack = new UiButton(THEME, pages.font,
                Component.translatable(RuleEditorPageSupport.UI + "button.result_actions"), UiButtonVariant.SECONDARY,
                () -> enterResultStage(1)).setChipStyle(true);
        pages.liveWidgets.add(resultBack);
        pages.buttons.add(resultBack);

        RuleValue candidate = ResultStructure.candidateAt(rule, candidateIndex);
        RuleValue actions = ResultStructure.effectsAt(rule, candidateIndex);
        int selectedIndex = Math.clamp(effectIndex, 0, Math.max(0, actions == null ? 0 : actions.size() - 1));
        RuleValue selected = actions == null ? null : actions.item(selectedIndex);
        ResourceLocation selectedType = selected == null ? null : ResourceLocation.tryParse(selected.text(RuleFields.TYPE, ""));
        boolean generic = ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, "spawn_entity").equals(selectedType)
                && "entity".equals(selected.text("variant", "item"));
        boolean blocks = ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, "place_block").equals(selectedType);
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
            combinationButton = new UiButton(THEME, pages.font, combinationLabel(rule), UiButtonVariant.SECONDARY,
                    this::cycleCombination).setChipStyle(true);
            pages.liveWidgets.add(combinationButton);
            pages.buttons.add(combinationButton);
        }

        String listPath = ResultStructure.listPath(rule, candidateIndex);
        int effectCount = ResultStructure.effectCount(rule, candidateIndex);
        effectIndex = Math.clamp(effectIndex, 0, Math.max(0, effectCount - 1));
        effectList = new UiListView<>(RuleEditorEditPages.THEME, pages.font, this::renderEffectRow);
        effectList.setRowHeight(ACTION_ROW_HEIGHT);
        effectList.setSelectionMarkerVisible(false);
        effectList.setChipRows(true);
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
            pages.flushPendingEdits();
            effectIndex = Math.max(0, index);
            pages.requestRebuild();
        });
        effectList.setOnActivate(index -> enterResultStage(2));
        pages.liveWidgets.add(effectList);
        revealSelectedEffect = true;
        if (effectCount > 0) {
            openActionButton = new UiButton(THEME, pages.font,
                    Component.translatable(RuleEditorPageSupport.UI + "button.effect_edit"), UiButtonVariant.SECONDARY,
                    () -> enterResultStage(2)).setChipStyle(true);
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
                String actionPath = DraftPaths.indexPath(listPath, effectIndex);
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
                // 消耗参数的主编辑入口在输入页，条件树在触发页：结果页只编辑动作自身的参数与共用补充参数
                List<EditorField> editable = descriptor.fields().stream()
                        .filter(field -> field.type() != EditorFieldType.CONDITION_TREE)
                        .toList();
                List<EditorField> primary = new ArrayList<>(editable.stream()
                        .filter(field -> !RuleEditorPageSupport.isCommonActionField(field))
                        .toList());
                List<EditorField> advanced = editable.stream().filter(RuleEditorPageSupport::isCommonActionField).toList();
                effectForm = pages.newForm(session, actionPath, new TypeEditorDescriptor(descriptor.id(), descriptor.label(), primary, descriptor.readOnly()));
                effectForm.useCompactSpacing();
                if (typeId.equals(PlaceBlockEffect.ID)) bindPlaceBlockVisibility(effectForm, session, actionPath);
                pages.liveForms.add(effectForm);
                pages.liveWidgets.add(effectForm);
                if (!advanced.isEmpty()) {
                    advancedEffectForm = pages.newForm(session, actionPath, new TypeEditorDescriptor(descriptor.id(), descriptor.label(), advanced, false));
                    advancedEffectForm.useCompactSpacing();
                    pages.liveForms.add(advancedEffectForm); pages.liveWidgets.add(advancedEffectForm);
                    advancedButton = new UiButton(THEME, pages.font, advancedLabel(), UiButtonVariant.SECONDARY, () -> {
                        pages.flushPendingEdits();
                        showAdvancedEffects = !showAdvancedEffects; pages.requestRebuild();
                    }).setChipStyle(true);
                    pages.liveWidgets.add(advancedButton); pages.buttons.add(advancedButton);
                }
            }
        }

        if (effectCount == 0) {
            // 无动作时入口收敛到右卡：工具条不再挂同名按钮，同屏只有一个「添加动作」
            emptyAddButton = new UiButton(THEME, pages.font,
                    Component.translatable(RuleEditorPageSupport.UI + "button.effect_add"),
                    UiButtonVariant.SECONDARY, this::openEffectPicker).setChipStyle(true);
            pages.liveWidgets.add(emptyAddButton);
            pages.buttons.add(emptyAddButton);
        }
        addPlanButton("results.plan_new", this::addCandidate);
        addPlanButton("results.plan_copy", this::duplicateCandidate);
        deletePlanButton = addPlanButton("results.plan_delete", this::removeCandidate);
        movePlanUpButton = addPlanButton("button.candidate_up", () -> moveCandidateBy(-1));
        movePlanDownButton = addPlanButton("button.candidate_down", () -> moveCandidateBy(1));
        if (effectCount > 0) {
            pages.addEffectButton(this::openEffectPicker);
        }
    }

    /**
     * place_block 的字段联动：勾选「使用源物品的方块」后清除固定方块并隐藏该行，
     * 避免隐藏的固定方块仍按服务端优先级生效；radius 变化时把 limit 的滑条窗口改为该半径方框的容量，
     * 并把 limit 跟随到新的容量上限——limit 一旦被手动改过就不再跟随，直到切换到别的动作或规则。
     */
    private void bindPlaceBlockVisibility(FormView form, EditSession session, String actionPath) {
        String blockPath = DraftPaths.child(actionPath, PlaceBlockEffect.BLOCK_FIELD);
        String useSourcePath = DraftPaths.child(actionPath, PlaceBlockEffect.USE_SOURCE_BLOCK_FIELD);
        String radiusPath = DraftPaths.child(actionPath, PlaceBlockEffect.RADIUS_FIELD);
        String limitPath = DraftPaths.child(actionPath, PlaceBlockEffect.LIMIT_FIELD);
        // 手工 JSON 可能同时写两个字段；固定方块仍生效时保留该行，避免把生效值藏起来
        form.setRowVisibility(blockPath, () -> !RuleEditorPageSupport.boolOf(
                session.draft().valueAt(actionPath), PlaceBlockEffect.USE_SOURCE_BLOCK_FIELD, false)
                || session.draft().valueAt(blockPath) != null);
        if (!actionPath.equals(placeBlockLinkPath) || placeBlockLinkSession != session) {
            placeBlockLinkPath = actionPath;
            placeBlockLinkSession = session;
            placeBlockLimitManual = false;
        }
        updatePlaceBlockWindows(form, session, actionPath);
        UiLinkedValues limitLink = placeBlockLimitLink(session, actionPath);
        form.setOnFieldWritten((path, before) -> {
            if (useSourcePath.equals(path)) {
                if (!RuleEditorPageSupport.boolOf(session.draft().valueAt(actionPath),
                        PlaceBlockEffect.USE_SOURCE_BLOCK_FIELD, false)) return;
                session.draft().removeAt(blockPath);
                form.reload();
                // 重载会按描述符初值重建控件，radius 的窗口需要再按当前草稿补一次
                updatePlaceBlockWindows(form, session, actionPath);
            } else if (radiusPath.equals(path)) {
                updatePlaceBlockWindows(form, session, actionPath);
                // 半径变化后上限跟随到新方框的容量；上限被手动改过则不再覆盖
                if (limitLink.driverChanged()) form.reload();
                placeBlockLimitManual = limitLink.manual();
            } else if (limitPath.equals(path)) {
                limitLink.followerChanged();
                placeBlockLimitManual = limitLink.manual();
            }
        });
    }

    /**
     * 构造 place_block 的 radius→limit 联动：驱动值是草稿里的 radius，跟随值是草稿里的 limit，
     * 目标值是 radius 方框内的位置总数；radius 缺失或越界时不联动。
     *
     * @param session 当前编辑会话
     * @param actionPath 动作在草稿中的路径
     * @return 联动绑定（带回上次记录的手动状态）
     */
    private UiLinkedValues placeBlockLimitLink(EditSession session, String actionPath) {
        String limitPath = DraftPaths.child(actionPath, PlaceBlockEffect.LIMIT_FIELD);
        UiLinkedValues.Accessor follower = new UiLinkedValues.Accessor() {
            @Override
            public double read() {
                RuleValue action = session.draft().valueAt(actionPath);
                int fallback = PlaceBlockEffect.limitUpperBound(draftPlaceBlockRadius(session, actionPath));
                return action == null ? fallback : action.integer(PlaceBlockEffect.LIMIT_FIELD, fallback);
            }

            @Override
            public void write(double value) {
                session.draft().setAt(limitPath, RuleValue.integer((int) Math.round(value)));
            }
        };
        return new UiLinkedValues(UiLinkedValues.readOnly(() -> draftPlaceBlockRadius(session, actionPath)),
                follower, radius -> PlaceBlockEffect.limitUpperBound((int) Math.round(radius)),
                placeBlockLimitManual)
                .followWhen(radius -> radius >= PlaceBlockEffect.MIN_RADIUS
                        && radius <= serverMaxPlaceBlockRadius());
    }

    /**
     * 草稿里 place_block 的 radius：缺失或越界时按默认半径。
     *
     * @param session 当前编辑会话
     * @param actionPath 动作在草稿中的路径
     * @return 有效半径
     */
    private int draftPlaceBlockRadius(EditSession session, String actionPath) {
        RuleValue action = session.draft().valueAt(actionPath);
        int radius = action == null ? PlaceBlockEffect.DEFAULT_RADIUS
                : action.integer(PlaceBlockEffect.RADIUS_FIELD, PlaceBlockEffect.DEFAULT_RADIUS);
        if (radius < PlaceBlockEffect.MIN_RADIUS || radius > serverMaxPlaceBlockRadius()) {
            return PlaceBlockEffect.DEFAULT_RADIUS;
        }
        return radius;
    }

    /**
     * 按草稿当前 radius 设置 limit 行的滑条窗口上限（方框内位置总数）；radius 缺失或非法时按默认半径计算。
     * 窗口只影响滑块手感，草稿里已有的 limit 值不随之夹取。
     *
     * @param form place_block 参数表单
     * @param session 当前编辑会话
     * @param actionPath 动作在草稿中的路径
     */
    private void updatePlaceBlockWindows(FormView form, EditSession session, String actionPath) {
        form.setRowSliderWindow(DraftPaths.child(actionPath, PlaceBlockEffect.RADIUS_FIELD),
                PlaceBlockEffect.MIN_RADIUS, serverMaxPlaceBlockRadius());
        form.setRowSliderWindow(DraftPaths.child(actionPath, PlaceBlockEffect.LIMIT_FIELD),
                PlaceBlockEffect.MIN_LIMIT, PlaceBlockEffect.limitUpperBound(draftPlaceBlockRadius(session, actionPath)));
    }

    /**
     * 服务端下发的单次放置半径上限：随开屏载荷到达，截图等没有会话载荷的工作区按配置默认值。
     *
     * @return radius 滑条的取值窗口上限
     */
    private int serverMaxPlaceBlockRadius() {
        EditorOpenRequest request = pages.host.workspace().openRequest();
        return request == null ? ServerConfig.DEFAULT.maxPlaceBlockRadius() : request.maxPlaceBlockRadius();
    }

    private UiButton addPlanButton(String key, UiAction action) {
        UiButton button = new UiButton(THEME, pages.font, Component.translatable(RuleEditorPageSupport.UI + key),
                UiButtonVariant.SECONDARY, action).setChipStyle(true);
        pages.candidateButtons.add(button);
        pages.liveWidgets.add(button);
        return button;
    }

    private Component combinationLabel(@Nullable RuleValue rule) {
        return Component.translatable(RuleEditorPageSupport.UI + "combination."
                + RuleEditorPageSupport.combinationOf(rule));
    }

    void updateCombinationLabel(@Nullable RuleValue rule) {
        if (combinationButton != null) combinationButton.setLabel(combinationLabel(rule));
    }

    private void cycleCombination() {
        pages.flushPendingEdits();
        String current = RuleEditorPageSupport.combinationOf(pages.currentView());
        String next = CombinationMode.ROUND_ROBIN.key().equals(current)
                ? CombinationMode.PRIORITY.key() : CombinationMode.ROUND_ROBIN.key();
        onCombinationChanged(next);
        updateCombinationLabel(pages.currentView());
    }

    // 提示只表达级别：色值由屏幕层按级别取（编辑页宿主桥接层仍以颜色传递）
    private void notice(RuleEditorScreen.NoticeLevel level, Component message) {
        pages.host.notice(message, RuleEditorScreen.noticeColor(level));
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
            draft.setAt(DraftPaths.child(DraftPaths.indexPath(RuleFields.OUTCOMES, index), field),
                    RuleValue.flag(value));
        });
        if (applied && convert) {
            notice(RuleEditorScreen.NoticeLevel.INFO, Component.translatable(RuleEditorPageSupport.UI + "notice.converted"));
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
            notice(RuleEditorScreen.NoticeLevel.WARNING, Component.translatable(RuleEditorPageSupport.UI + "issue.candidate_limit", ConditionLimits.MAX_EFFECTS));
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
            draft.setAt(DraftPaths.indexPath(RuleFields.OUTCOMES, draft.outcomeCount()), body);
        })) {
            return;
        }
        candidateIndex = Math.max(0, ResultStructure.candidateCount(draft.root()) - 1);
        effectIndex = 0;
        notice(RuleEditorScreen.NoticeLevel.INFO, Component.translatable(RuleEditorPageSupport.UI
                + (convert ? "notice.converted" : "notice.candidate_added")));
    }

    void duplicateCandidate() {
        pages.flushPendingEdits();
        EditSession session = pages.host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        if (ResultStructure.candidateCount(draft.root()) >= ConditionLimits.MAX_EFFECTS) {
            notice(RuleEditorScreen.NoticeLevel.WARNING, Component.translatable(RuleEditorPageSupport.UI + "issue.candidate_limit", ConditionLimits.MAX_EFFECTS));
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
            draft.setAt(DraftPaths.indexPath(RuleFields.OUTCOMES, draft.outcomeCount()), body);
        })) {
            return;
        }
        candidateIndex = Math.max(0, ResultStructure.candidateCount(draft.root()) - 1);
        effectIndex = 0;
        notice(RuleEditorScreen.NoticeLevel.INFO, Component.translatable(RuleEditorPageSupport.UI + "notice.candidate_added"));
    }

    void removeCandidate() {
        EditSession session = pages.host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        // 至少保留一个方案，与工具条的禁用边界一致。
        if (!draft.hasOutcomes() || draft.outcomeCount() <= 1) return;
        int index = Math.max(0, candidateIndex);
        if (!pages.applyStructure(EditSession.OP_REMOVE_CANDIDATE,
                () -> draft.removeAt(DraftPaths.indexPath(RuleFields.OUTCOMES, index)))) {
            return;
        }
        candidateIndex = Math.max(0, index - 1);
        effectIndex = 0;
        notice(RuleEditorScreen.NoticeLevel.INFO, Component.translatable(RuleEditorPageSupport.UI + "notice.candidate_removed"));
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
        pages.flushPendingEdits();
        // 消耗类的编辑入口在「输入」页，三类消耗动作都不出现在这里，避免与说明行自相矛盾
        List<ResourceLocation> types = new ArrayList<>();
        for (TypeEditorDescriptor descriptor : EffectEditorRegistry.all()) {
            types.add(descriptor.id());
        }
        if (types.isEmpty()) {
            notice(RuleEditorScreen.NoticeLevel.WARNING, Component.translatable(RuleEditorPageSupport.UI + "notice.no_effect"));
            return;
        }
        if (pages.lastArea == null) return;
        typePicker.open(types.stream().map(type -> new UiTypePicker.Option(type,
                EffectEditorRegistry.descriptorFor(type).label())).toList(), pages.lastArea);
    }

    void addEffectOfType(@Nullable ResourceLocation type) {
        EditSession session = pages.host.session();
        if (session == null || type == null) {
            return;
        }
        RuleDraft draft = session.draft();
        if (ResultStructure.totalEffectCount(draft.root()) >= ConditionLimits.MAX_EFFECTS) {
            notice(RuleEditorScreen.NoticeLevel.WARNING, Component.translatable(RuleEditorPageSupport.UI + "issue.candidate_limit", ConditionLimits.MAX_EFFECTS));
            return;
        }
        String listPath = ResultStructure.listPath(draft.root(), candidateIndex);
        RuleValue body = BuiltinEditorDefaults.effectBody(type);
        // 服务端上限低于默认半径时，新建动作的半径与上限一并收进上限，避免一出生就非法
        int placeRadius = Math.min(PlaceBlockEffect.DEFAULT_RADIUS, serverMaxPlaceBlockRadius());
        if (!pages.applyStructure(EditSession.OP_ADD_EFFECT,
                () -> {
                    String effectPath = DraftPaths.indexPath(listPath, draft.effectCountAt(listPath));
                    draft.setAt(effectPath, body);
                    if (PlaceBlockEffect.ID.equals(type) && placeRadius < PlaceBlockEffect.DEFAULT_RADIUS) {
                        draft.setAt(DraftPaths.child(effectPath, PlaceBlockEffect.RADIUS_FIELD), RuleValue.integer(placeRadius));
                        draft.setAt(DraftPaths.child(effectPath, PlaceBlockEffect.LIMIT_FIELD),
                                RuleValue.integer(PlaceBlockEffect.limitUpperBound(placeRadius)));
                    }
                })) {
            return;
        }
        effectIndex = Math.max(0, draft.effectCountAt(listPath) - 1);
    }

    private void removeEffect(int index) {
        EditSession session = pages.host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        String listPath = ResultStructure.listPath(draft.root(), candidateIndex);
        if (index < 0 || index >= draft.effectCountAt(listPath)) {
            return;
        }
        if (!pages.applyStructure(EditSession.OP_REMOVE_EFFECT, () -> draft.removeAt(DraftPaths.indexPath(listPath, index)))) {
            return;
        }
        // 删除非选中行时保留当前动作；删掉当前行时选中前一行。
        if (index <= effectIndex) effectIndex = Math.max(0, effectIndex - 1);
    }

    private UiRect actionRemoveRect(UiRect row) {
        return new UiRect(row.right() - ACTION_REMOVE_INSET - ACTION_REMOVE_SIZE,
                row.y() + (row.height() - ACTION_REMOVE_SIZE) / 2, ACTION_REMOVE_SIZE, ACTION_REMOVE_SIZE);
    }

    private int removeIndexAt(double mouseX, double mouseY) {
        if (effectList == null || !effectList.isVisible()) return -1;
        int index = effectList.itemIndexAt(mouseX, mouseY);
        UiRect row = effectList.rowBounds(index);
        return row != null && actionRemoveRect(row).contains(mouseX, mouseY) ? index : -1;
    }

    boolean clickRemove(double mouseX, double mouseY, int button) {
        int index = removeIndexAt(mouseX, mouseY);
        if (button != 0 || index < 0) return false;
        pages.flushPendingEdits();
        removeEffect(index);
        return true;
    }

    void moveEffect(int from, int to) {
        EditSession session = pages.host.session();
        if (session == null || from == to) {
            return;
        }
        RuleDraft draft = session.draft();
        String listPath = ResultStructure.listPath(draft.root(), candidateIndex);
        boolean[] moved = {false};
        if (!pages.applyStructure(EditSession.OP_MOVE_EFFECT, () -> moved[0] = draft.moveInList(listPath, from, to)) || !moved[0]) {
            return;
        }
        effectIndex = to;
    }

    void renderEffectRow(GuiGraphics graphics, Font renderFont, Integer item, int index, UiRect row, boolean selected, boolean hovered, boolean focused) {
        EditSession session = pages.host.session();
        Object language = Language.getInstance();
        long revision = session == null ? -1 : session.revision();
        if (effectRowSession != session || effectRowRevision != revision || effectRowCandidate != candidateIndex
                || effectRowLanguage != language || effectRowWidth != row.width()) {
            effectRows.clear();
            effectRowSession = session;
            effectRowRevision = revision;
            effectRowCandidate = candidateIndex;
            effectRowLanguage = language;
            effectRowWidth = row.width();
        }
        EffectRowText cached = effectRows.get(item);
        if (cached == null) {
            cached = effectRowText(pages.currentView(), item, row);
            effectRows.put(item, cached);
        }
        if (cached.icon() != null) {
            cached.icon().render(graphics, row.x() + ACTION_CONTENT_INSET,
                    row.y() + (row.height() - DETAIL_ICON_SIZE) / 2);
        }
        UiRect remove = actionRemoveRect(row);
        graphics.drawString(pages.font, cached.text(), row.x() + cached.inset(),
                row.y() + (row.height() - renderFont.lineHeight) / 2, THEME.palette().textPrimary(), false);
        // 叉号与目录选中卡保持一致：悬停行才转成危险色，常亮红色会被读成「已有删除待确认」
        UiIcon close = UiMarkIcons.icon(UiMarkIcons.Mark.CLOSE,
                hovered ? THEME.palette().danger() : THEME.palette().textSecondary());
        close.render(graphics, remove.x() + (remove.width() - close.width()) / 2,
                remove.y() + (remove.height() - close.height()) / 2);
    }

    // 单行展示数据：标题文本、图标与文本内缩一起算好，裁剪宽度按行宽推出
    private EffectRowText effectRowText(@Nullable RuleValue view, int item, UiRect row) {
        RuleValue array = ResultStructure.effectsAt(view, candidateIndex);
        RuleValue element = array != null && item >= 0 && item < array.size() ? array.item(item) : null;
        RuleValue effect = element != null && element.isMapping() ? element : null;
        Component title = RuleNaming.effectTitle(effect, pages.nameSource);
        String text = (item + 1) + ". " + title.getString();
        UiIcon icon = RulePreviewIcons.action(effect);
        int inset = ACTION_CONTENT_INSET + (icon == null ? 0 : DETAIL_ICON_SIZE + ACTION_ICON_TEXT_GAP);
        UiRect remove = actionRemoveRect(row);
        String trimmed = TextScroll.trimToWidth(pages.font, text,
                Math.max(1, remove.x() - row.x() - inset - ACTION_REMOVE_INSET));
        return new EffectRowText(trimmed, icon, inset);
    }

    void switchProduct(String path, String variant) {
        EditSession session = pages.host.session();
        if (session == null) return;
        pages.flushPendingEdits();
        RuleValue old = session.draft().valueAt(path);
        if (old == null || !old.isMapping()) return;
        RuleValue.Mapping next = RuleValue.mapping(old);
        for (String field : List.of("item", "entity", "count", "age", "amount")) next.remove(field);
        next.put("variant", RuleValue.text(variant));
        if ("experience".equals(variant)) {
            next.put("amount", RuleValue.integer(1));
        } else {
            next.put("count", RuleValue.integer(1));
            if ("entity".equals(variant)) next.put("age", RuleValue.integer(0));
        }
        if (pages.applyStructure(EditSession.OP_SET_FIELD, () -> session.draft().setAt(path, next.build()))) pages.requestRebuild();
    }

    private Component advancedLabel() {
        return Component.translatable(RuleEditorPageSupport.UI
                + (showAdvancedEffects ? "results.advanced_open" : "results.advanced_closed"));
    }

    private void selectPlanBy(int delta) {
        pages.flushPendingEdits();
        int count = Math.max(1, ResultStructure.candidateCount(pages.currentView()));
        int target = candidateIndex + delta;
        if (target < 0 || target >= count || target == candidateIndex) return;
        candidateIndex = target;
        effectIndex = 0;
        showAdvancedEffects = false;
        enterResultStage(1);
    }

    // 容器统一应用使能后恢复方案数量与位置边界，避免不可用操作被重新启用。
    void updatePlanButtons(boolean enabled) {
        int count = Math.max(1, ResultStructure.candidateCount(pages.currentView()));
        boolean previousEnabled = enabled && candidateIndex > 0;
        boolean nextEnabled = enabled && candidateIndex + 1 < count;
        if (planPager != null) {
            planPager.setIconColors(previousEnabled, nextEnabled);
            planPager.previous.setEnabled(previousEnabled);
            planPager.next.setEnabled(nextEnabled);
        }
        if (deletePlanButton != null) deletePlanButton.setEnabled(enabled && count > 1);
        if (movePlanUpButton != null) movePlanUpButton.setEnabled(enabled && candidateIndex > 0);
        if (movePlanDownButton != null) movePlanDownButton.setEnabled(enabled && candidateIndex + 1 < count);
    }

    void enterResultStage(int stage) {
        pages.flushPendingEdits();
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
        RuleEditorPageSupport.show(safeSpawnBox, detail);
        RuleEditorPageSupport.show(fillOriginBox, detail);
        RuleEditorPageSupport.show(effectForm, detail);
        RuleEditorPageSupport.show(variantControl, detail);
        RuleEditorPageSupport.show(advancedButton, detail);
        RuleEditorPageSupport.show(advancedEffectForm, detail && showAdvancedEffects);
        RuleEditorPageSupport.show(emptyAddButton, detail);
        detailHeaderRect = null;
        emptyDetailRect = null;
        actionParametersRect = null;
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
        // 先取得左栏列表与按钮的高度下限，再让右栏内容决定共用底边。
        // 最终摆放左栏时复用此底边，切换行为和展开高级区均会同步伸缩。
        int navigationBottom = layoutActionControls(area.x(), y, navigationWidth, availableHeight);
        int detailBottom = layoutActionDetail(detailX, y, area.right() - detailX, navigationBottom - y);
        return layoutActions(area.x(), y, navigationWidth, detailBottom - y);
    }

    // 方案管理始终在页首；工具条按实际文案换行，英文与小窗口不挤占动作参数宽度。
    private int layoutToolbar(int x, int y, int width) {
        List<UiWidget> controls = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        if (planPager != null) {
            controls.add(planPager);
            sizes.add(Math.min(width, planPager.preferredWidth()));
        }
        if (combinationButton != null) {
            controls.add(combinationButton);
            ensureStaticTexts();
            sizes.add(Math.min(width, combinationOptionWidth + RuleEditorPageSupport.PAD * 2));
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
        int bottom = layoutActionControls(x, top, width, availableHeight);
        if (effectList != null && revealSelectedEffect) {
            effectList.ensureVisible(effectIndex);
            revealSelectedEffect = false;
        }
        pages.cards.add(RuleEditorEditPages.PageCard.titled(new UiRect(x, top, width, bottom - top),
                Component.translatable(RuleEditorPageSupport.UI + "card.actions"), DETAIL_PADDING));
        return bottom;
    }

    private int layoutActionControls(int x, int top, int width, int availableHeight) {
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
        }
        return bottom;
    }

    private int layoutActionDetail(int x, int top, int width, int availableHeight) {
        // 头部矩形与右栏卡片的 PageCard 走同一个派生函数，避免绘制与命中区各自计算
        detailHeaderRect = RuleEditorEditPages.cardHeaderRect(x, top, width, DETAIL_PADDING);
        int contentX = detailHeaderRect.x();
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
            // 方案级开关属于常规选项，不随行为高级参数的折叠状态隐藏；从方块起点填充放在参数之前
            if (fillOriginBox != null) {
                fillOriginBox.setBounds(contentX, y, contentWidth, RuleEditorPageSupport.ROW_H);
                y += RuleEditorPageSupport.ROW_H + RuleEditorPageSupport.PAD;
            }
            y = pages.layoutForm(effectForm, contentX, y, contentWidth);
            if (safeSpawnBox != null) {
                safeSpawnBox.setBounds(contentX, y, contentWidth, RuleEditorPageSupport.ROW_H);
                y += RuleEditorPageSupport.ROW_H + RuleEditorPageSupport.PAD;
            }
            if (advancedButton != null) {
                advancedButton.setBounds(contentX, y, contentWidth, TOOLBAR_HEIGHT);
                y += TOOLBAR_HEIGHT + RuleEditorPageSupport.PAD;
            }
            if (showAdvancedEffects) {
                actionParametersRect = new UiRect(contentX, y, contentWidth, RuleEditorPageSupport.LINE_H);
                y += RuleEditorPageSupport.LINE_H + RuleEditorPageSupport.PAD;
                y = pages.layoutForm(advancedEffectForm, contentX, y, contentWidth);
            }
        } else {
            int messageHeight = pages.font.lineHeight + EMPTY_MESSAGE_GAP
                    + emptyHelpLines(contentWidth).size() * (pages.font.lineHeight + 2);
            y += messageHeight + DETAIL_PADDING;
            if (emptyAddButton != null) {
                emptyAddButton.setBounds(contentX, y,
                        Math.min(contentWidth, emptyAddButton.preferredWidth(RuleEditorPageSupport.PAD)),
                        RuleEditorPageSupport.BUTTON_H);
                y += RuleEditorPageSupport.BUTTON_H + DETAIL_PADDING;
            }
        }
        int bottom = Math.max(top + availableHeight, y + DETAIL_PADDING);
        if (empty) emptyDetailRect = new UiRect(contentX, top + DETAIL_HEADER_HEIGHT + DETAIL_PADDING,
                contentWidth, Math.max(0, bottom - top - DETAIL_HEADER_HEIGHT - DETAIL_PADDING * 2));
        pages.cards.add(RuleEditorEditPages.PageCard.dynamic(new UiRect(x, top, width, bottom - top),
                this::detailHeader, DETAIL_PADDING));
        return bottom;
    }

    // 右栏头部：空态显示卡片标题，否则显示所选动作的图标与名称
    private RuleEditorEditPages.CardHeader detailHeader() {
        boolean empty = emptyDetailRect != null;
        Object language = Language.getInstance();
        if (headerCached && headerEmpty == empty && headerAction == selectedAction && headerLanguage == language) {
            return headerValue;
        }
        headerCached = true;
        headerEmpty = empty;
        headerAction = selectedAction;
        headerLanguage = language;
        RuleEditorEditPages.CardHeader next = empty
                ? new RuleEditorEditPages.CardHeader(
                        Component.translatable(RuleEditorPageSupport.UI + "card.action_detail"), null)
                : new RuleEditorEditPages.CardHeader(RuleNaming.effectTitle(selectedAction, pages.nameSource),
                        selectedAction == null ? null : RulePreviewIcons.action(selectedAction));
        headerValue = next;
        return next;
    }

    void renderResults(GuiGraphics graphics) {
        ensureStaticTexts();
        if (emptyDetailRect != null) {
            var lines = emptyHelpLines(emptyDetailRect.width());
            // 空态左上对齐：标题 + 引导句 + 卡内「添加动作」，不再垂直居中留下整块空白
            int y = emptyDetailRect.y();
            pages.drawText(graphics, new UiRect(emptyDetailRect.x(), y, emptyDetailRect.width(), pages.font.lineHeight),
                    emptyTitleText, THEME.palette().textPrimary());
            y += pages.font.lineHeight + EMPTY_MESSAGE_GAP;
            for (var line : lines) {
                graphics.drawString(pages.font, line, emptyDetailRect.x(), y, THEME.palette().textSecondary(), false);
                y += pages.font.lineHeight + 2;
            }
        }
        Component readonlyNote = readonlyEffectNote(pages.currentView());
        if (readonlyNote != null) pages.drawText(graphics, effectNoteRect, readonlyNote, THEME.palette().warning());
        pages.drawText(graphics, actionParametersRect, actionParametersText, THEME.palette().textSecondary());
    }

    // 悬停只补画面上读不到的信息：动作名、叉号、翻页箭头与产品分段都有文字或符号，不再重复解释。
    @Nullable Component tooltipAt(double mouseX, double mouseY) {
        if (typePicker.isOpen()) return null;
        if (planPager != null && planPager.isLabelHovered(mouseX, mouseY))
            return RuleNaming.candidateTitle(ResultStructure.candidateAt(pages.currentView(), candidateIndex),
                    candidateIndex + 1, pages.nameSource);
        if (combinationButton != null && combinationButton.bounds().contains(mouseX, mouseY))
            return Component.translatable(RuleEditorPageSupport.UI + "results.combination_help");
        if (deletePlanButton != null && deletePlanButton.bounds().contains(mouseX, mouseY))
            return Component.translatable(RuleEditorPageSupport.UI + "results.remove_undo_hint");
        if (effectNoteRect != null && effectNoteRect.contains(mouseX, mouseY)) return readonlyEffectNote(pages.currentView());
        return null;
    }

    // 描述只挂在详情卡片的标题上：行内动作卡片已有名称与图标，悬停不再重复解释。
    boolean renderDescriptionTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (typePicker.isOpen() || selectedAction == null || !selectedAction.isMapping()) return false;
        if (detailHeaderRect == null || !detailHeaderRect.contains(mouseX, mouseY)) return false;
        new UiDescriptionTooltip(THEME, pages.font).render(graphics,
                new UiRect(0, 0, pages.host.screenWidth(), pages.host.screenHeight()),
                ActionDescription.lines(selectedAction), mouseX, mouseY);
        return true;
    }

    // 固定文案与组合方式按钮最大宽度：语言切换后一次性刷新，绘制与布局都复用
    private void ensureStaticTexts() {
        Object language = Language.getInstance();
        if (staticTextLanguage == language) {
            return;
        }
        staticTextLanguage = language;
        emptyTitleText = Component.translatable(RuleEditorPageSupport.UI + "results.empty_title");
        actionParametersText = Component.translatable(RuleEditorPageSupport.UI + "results.action_parameters");
        readonlyUnknownText = Component.translatable(RuleEditorPageSupport.UI + "effect.readonly_note_unknown");
        combinationOptionWidth = java.util.Arrays.stream(CombinationMode.values())
                .mapToInt(mode -> pages.font.width(
                        Component.translatable(RuleEditorPageSupport.UI + "combination." + mode.key())))
                .max().orElse(0);
    }

    // 空态引导文案按内容宽折行：只有宽度或语言变化才重新 split
    private List<FormattedCharSequence> emptyHelpLines(int width) {
        Object language = Language.getInstance();
        if (emptyHelpWidth == width && emptyHelpLanguage == language) {
            return emptyHelpLines;
        }
        emptyHelpWidth = width;
        emptyHelpLanguage = language;
        emptyHelpLines = pages.font.split(
                Component.translatable(RuleEditorPageSupport.UI + "results.empty_help"), Math.max(1, width));
        return emptyHelpLines;
    }

    @Nullable Component readonlyEffectNote(@Nullable RuleValue view) {
        ensureStaticTexts();
        EditSession session = pages.host.session();
        Object language = Language.getInstance();
        long revision = session == null ? -1 : session.revision();
        if (readonlyNoteCached && readonlyNoteSession == session && readonlyNoteRevision == revision
                && readonlyNoteCandidate == candidateIndex && readonlyNoteEffect == effectIndex
                && readonlyNoteLanguage == language) {
            return readonlyNoteValue;
        }
        readonlyNoteCached = true;
        readonlyNoteSession = session;
        readonlyNoteRevision = revision;
        readonlyNoteCandidate = candidateIndex;
        readonlyNoteEffect = effectIndex;
        readonlyNoteLanguage = language;
        RuleValue effects = ResultStructure.effectsAt(view, candidateIndex);
        if (effects == null || effectIndex < 0 || effectIndex >= effects.size()) {
            readonlyNoteValue = null;
            return null;
        }
        String type = RuleCostBinding.typeOf(effects.item(effectIndex));
        ResourceLocation typeId = type == null ? null : ResourceLocation.tryParse(type);
        if (typeId == null) {
            readonlyNoteValue = readonlyUnknownText;
        } else if (EffectEditorRegistry.find(typeId) != null) {
            readonlyNoteValue = null;
        } else {
            readonlyNoteValue = Component.translatable(RuleEditorPageSupport.UI + "effect.readonly_note",
                    typeId.toString());
        }
        return readonlyNoteValue;
    }

    /** 方案翻页组：只让左右按钮接收输入，页码与两箭头保持同一行。 */
    private final class PlanPager implements UiWidget {
        private final Component label;
        private final UiButton previous;
        private final UiButton next;
        private UiRect bounds = new UiRect(0, 0, 0, 0);
        private UiRect labelRect = bounds;

        private PlanPager(int count) {
            label = Component.translatable(RuleEditorPageSupport.UI + "results.plan_page", candidateIndex + 1, count);
            previous = new UiButton(THEME, pages.font, Component.empty(),
                    UiButtonVariant.SECONDARY, () -> selectPlanBy(-1)).setChipStyle(true);
            next = new UiButton(THEME, pages.font, Component.empty(),
                    UiButtonVariant.SECONDARY, () -> selectPlanBy(1)).setChipStyle(true);
            previous.setAccessibleName(Component.translatable(RuleEditorPageSupport.UI + "results.previous_plan"));
            next.setAccessibleName(Component.translatable(RuleEditorPageSupport.UI + "results.next_plan"));
            setIconColors(candidateIndex > 0, candidateIndex + 1 < count);
        }

        // 标记贴图不带禁用态，只能按可用性换染色；启用色与胶囊文字色保持一致。
        private void setIconColors(boolean previousEnabled, boolean nextEnabled) {
            previous.setIcon(UiMarkIcons.icon(UiMarkIcons.Mark.PREVIOUS,
                    previousEnabled ? THEME.palette().textPrimary() : THEME.palette().textDisabled()));
            next.setIcon(UiMarkIcons.icon(UiMarkIcons.Mark.NEXT,
                    nextEnabled ? THEME.palette().textPrimary() : THEME.palette().textDisabled()));
        }

        private int preferredWidth() {
            return TOOLBAR_HEIGHT * 2 + pages.font.width(label) + RuleEditorPageSupport.BUTTON_GAP * 2;
        }

        /** @return 整组的屏幕矩形 */
        @Override public UiRect bounds() { return bounds; }

        /** @return 指针是否落在只读页码上：翻页箭头有图标说明方向，提示只讲当前方案。 */
        private boolean isLabelHovered(double mouseX, double mouseY) {
            return labelRect.contains(mouseX, mouseY);
        }

        /**
         * 分配左右按钮与中间页码的同排几何。
         * @param x 左缘
         * @param y 顶缘
         * @param width 可用宽度
         * @param height 行高
         */
        @Override public void setBounds(int x, int y, int width, int height) {
            bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
            int buttonWidth = Math.min(TOOLBAR_HEIGHT, bounds.width() / 2);
            previous.setBounds(x, y, buttonWidth, bounds.height());
            next.setBounds(bounds.right() - buttonWidth, y, buttonWidth, bounds.height());
            labelRect = new UiRect(x + buttonWidth, y, Math.max(0, bounds.width() - buttonWidth * 2), bounds.height());
        }

        /**
         * 居中绘制只读页码；按钮由页面统一绘制。
         * @param graphics 绘制上下文
         * @param font 绘制字体
         * @param mouseX 鼠标屏幕 X 坐标
         * @param mouseY 鼠标屏幕 Y 坐标
         */
        @Override public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
            String text = TextScroll.trimToWidth(font, label.getString(), labelRect.width());
            graphics.drawString(font, text, labelRect.x() + (labelRect.width() - font.width(text)) / 2,
                    labelRect.y() + (labelRect.height() - font.lineHeight) / 2, THEME.palette().textPrimary(), false);
        }
    }

}
