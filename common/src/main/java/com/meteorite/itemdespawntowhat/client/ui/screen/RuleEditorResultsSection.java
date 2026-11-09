package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.FieldPath;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointer;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.shared.PreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.RuleDisplayLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiScrollView;
import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDefaults;
import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDescriptors;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorFactories;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorRegistries;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleNaming;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.ResultStructure;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.net.EditorOpenRequest;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.text.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiAction;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiFlowLayout;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiLinkedValues;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.shared.EntityTraits;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostIcon;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiMarkIcons;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCheckBox;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiDescriptionTooltip;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiListView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTypePicker;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiWidget;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.RuleRuntimeLimits;
import com.meteorite.itemdespawntowhat.core.config.ServerConfig;
import com.meteorite.itemdespawntowhat.core.model.CombinationMode;
import com.meteorite.itemdespawntowhat.core.model.ConditionLimits;
import com.meteorite.itemdespawntowhat.core.model.OutcomeCandidate;
import com.meteorite.itemdespawntowhat.core.api.param.ParamTier;
import com.meteorite.itemdespawntowhat.core.type.effect.EntityProduct;
import com.meteorite.itemdespawntowhat.core.type.effect.PlaceBlockEffect;
import com.meteorite.itemdespawntowhat.core.type.effect.WeatherEffect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * 结果页：顶部管理方案，左侧选择执行动作，右侧编辑参数；窄屏在动作和参数之间导航。
 */
final class RuleEditorResultsSection {

    private final UiScrollView scroll;
    private boolean selectingEffect;
    private final RuleNaming.NameSource nameSource = RuleDisplayLabels.nameSource();
    final EditorSectionContent content;
    private final EditorShell shell;
    private final Font font;
    private final EditorFormFactory forms;
    private final EditorSectionActions commands;

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

    // 详情卡头部缓存：空态、会话版本、所选动作、语言与字体任一变化才重建。
    // 所选动作是草稿的实时视图，字段写入不会替换句柄，只靠身份比较会留旧标题与图标。
    private boolean headerCached;
    private boolean headerEmpty;
    private long headerRevision = -1;
    private @Nullable RuleValue headerAction;
    private @Nullable Object headerLanguage;
    private @Nullable EditorSectionContent.CardHeader headerValue;

    // 空态引导文案的折行结果：布局与绘制共用，宽度或语言变化才重算
    private int emptyHelpWidth = -1;
    private @Nullable Object emptyHelpLanguage;
    private List<FormattedCharSequence> emptyHelpLines = List.of();

    // 固定文案与组合方式最大宽：语言变化时统一刷新，避免每帧新建 Component 并测宽
    private @Nullable Object staticTextLanguage;
    private Component emptyTitleText = Component.empty();
    private Component actionParametersText = Component.empty();
    private Component productKindText = Component.empty();
    private Component readonlyUnknownText = Component.empty();
    private int combinationOptionWidth;

    // 一条效果行的绘制数据：文本已裁剪，图标与文本内缩一并缓存
    private record EffectRowText(String text, @Nullable UiIcon icon, int inset) {
    }

    // 主题：与容器共用宿主默认主题
    private final UiHostTheme THEME;
    private static final int NAVIGATION_MIN_WIDTH = 128;
    private static final int NAVIGATION_MAX_WIDTH = 184;
    private static final int DETAIL_MIN_WIDTH = 240;
    private static final int ACTION_ROW_HEIGHT = 28;
    private static final int ACTION_CONTENT_INSET = 4;
    private static final int ACTION_ICON_TEXT_GAP = 4;
    private static final int ACTION_REMOVE_SIZE = 14;
    private static final int ACTION_REMOVE_INSET = 2;
    // 左栏至少整装这么多条胶囊：条目未超出时按内容收紧，超出后列表占满该高度并滚动
    private static final int ACTION_LIST_MIN_ROWS = 4;
    private static final int TOOLBAR_HEIGHT = 24;
    private static final int DETAIL_HEADER_HEIGHT = 26;
    // 动作参数表的标签列宽：产物行复用同一列宽，轮询按钮才能与下面的表单控件左右对齐。
    // 取值与表单默认标签列宽一致，较长的英文字段标签（如「Duration (ticks)」）不必退化成省略号
    private static final int DETAIL_LABEL_WIDTH = 88;
    // 年龄图标在引用无法解析时的回落实体：鸡的幼年与成年预览差异最直观，且原版一定存在
    private static final String AGE_PREVIEW_FALLBACK = "minecraft:chicken";
    private static final int DETAIL_ICON_SIZE = 18;
    private static final int DETAIL_PADDING = 8;
    private static final int EMPTY_MESSAGE_GAP = 8;

    RuleEditorResultsSection(EditorShell shell, Font font, EditorFormFactory forms, EditorSectionActions actions, UiScrollView scroll) {
        this.THEME = shell.theme();
        this.shell = shell;
        this.font = font;
        this.forms = forms;
        this.commands = actions;
        this.content = new EditorSectionContent(font);
        this.scroll = scroll;

        typePicker = new UiTypePicker(shell.chromeTheme(), font,
                Component.translatable(RuleEditorPageSupport.UI + "pick.type_title"), type -> {
            addEffectOfType(type);
        });
    }

    private final UiTypePicker typePicker;

    @Nullable UiTypePicker activeTypePicker() {
        return typePicker.isOpen() ? typePicker : null;
    }

    void closeTypePicker() {
        typePicker.close();
    }

    void prepareReveal(int candidate, int effect) {
        resultStage = effect >= 0 ? ResultStage.DETAIL : ResultStage.ACTIONS;
        showAdvancedEffects = false;
        if (candidate >= 0) candidateIndex = candidate;
        if (effect >= 0) effectIndex = effect;
    }

    @Nullable UiFocusTarget revealField(FieldPath path) {
        UiFocusTarget target = effectForm == null ? null : effectForm.revealPath(path);
        return target != null || advancedEffectForm == null ? target : advancedEffectForm.revealPath(path);
    }

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
        productButton = null;
        productLabelRect = null;
        advancedEffectForm = null;
        advancedButton = null;
        resultBack = null;
        detailHeaderRect = null;
        emptyDetailRect = null;
        actionParametersRect = null;
        selectedAction = null;
        effectNoteRect = null;
        openActionButton = null;
        addEffectButton = null;
    }

    private int candidateIndex;

    private int effectIndex;

    private @Nullable PlanPager planPager;

    @Nullable UiCheckBox safeSpawnBox;

    @Nullable UiCheckBox fillOriginBox;

    @Nullable UiButton combinationButton;
    private @Nullable UiButton deletePlanButton;
    private @Nullable UiButton movePlanUpButton;
    private @Nullable UiButton movePlanDownButton;

    @Nullable UiListView<Integer> effectList;

    @Nullable FormView effectForm;

    // 产物档位轮询按钮：档位与草稿 variant 一一对应，重建时按草稿重派，不进协议也不额外落盘
    @Nullable UiButton productButton;
    private EntityProductStop productStop = EntityProductStop.ITEM;
    private @Nullable UiRect productLabelRect;

    @Nullable FormView advancedEffectForm;

    @Nullable UiButton advancedButton;

    @Nullable UiButton resultBack;

    private boolean showAdvancedEffects;

    private enum ResultStage { ACTIONS, DETAIL }
    private ResultStage resultStage = ResultStage.ACTIONS;

    private @Nullable UiButton openActionButton;

    // 行为列表唯一的「添加动作」入口：方形加号恒定排在最后一条胶囊下方
    private @Nullable UiButton addEffectButton;

    private @Nullable UiRect detailHeaderRect;

    private @Nullable UiRect emptyDetailRect;

    private @Nullable UiRect actionParametersRect;

    // place_block 的 radius→limit 联动：记录上次绑定的动作路径与会话，用于判断何时清除手动标记
    private @Nullable FieldPath placeBlockLinkPath;
    private @Nullable EditSession placeBlockLinkSession;
    // 上限是否已被手动改过：同一动作重建表单后沿用，避免一次手改后又被半径联动覆盖
    private boolean placeBlockLimitManual;

    private boolean revealSelectedEffect;

    @Nullable RuleValue selectedAction;

    @Nullable UiRect effectNoteRect;

    void buildResults(EditSession session, @Nullable FieldPath revealPath) {
        RuleValue rule = session.root();
        int candidates = Math.max(1, ResultStructure.candidateCount(rule));
        candidateIndex = Math.clamp(candidateIndex, 0, candidates - 1);
        planPager = new PlanPager(candidates);
        content.widgets.add(planPager);
        content.widgets.add(planPager.previous);
        content.widgets.add(planPager.next);
        content.buttons.add(planPager.previous);
        content.buttons.add(planPager.next);
        resultBack = new UiButton(THEME, font,
                Component.translatable(RuleEditorPageSupport.UI + "button.result_actions"), UiButtonVariant.SECONDARY,
                () -> enterResultStage(ResultStage.ACTIONS)).setChipStyle(true);
        content.widgets.add(resultBack);
        content.buttons.add(resultBack);

        RuleValue candidate = ResultStructure.candidateAt(rule, candidateIndex);
        RuleValue actions = ResultStructure.effectsAt(rule, candidateIndex);
        int selectedIndex = Math.clamp(effectIndex, 0, Math.max(0, actions == null ? 0 : actions.size() - 1));
        RuleValue selected = actions == null ? null : actions.item(selectedIndex);
        ResourceLocation selectedType = selected == null ? null : ResourceLocation.tryParse(selected.text(RuleFields.TYPE, ""));
        FieldPath listPath = ResultStructure.listPath(rule, candidateIndex);
        FieldPath actionPath = listPath.index(selectedIndex);
        boolean spawnEntity = ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, "spawn_entity").equals(selectedType)
                && selected.isMapping();
        if (spawnEntity) {
            // 档位与草稿 variant 一一对应：重建时直接按草稿重派，撤销/重做也不会出现界面档位与草稿矛盾
            productStop = EntityProductStop.of(selected.text("variant", "item"));
        }
        boolean mobProduct = spawnEntity && productStop == EntityProductStop.MOB;
        // 安全生成只对生物档提供；其它实体档若显式开启 safe_spawn 仍显示勾选框，避免把生效值藏起来
        boolean showSafeSpawn = spawnEntity && (mobProduct
                || RuleValue.flag(candidate, RuleFields.SAFE_SPAWN, false));
        boolean blocks = ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, "place_block").equals(selectedType);
        if (showSafeSpawn) {
            safeSpawnBox = new UiCheckBox(THEME, font, Component.translatable(RuleEditorPageSupport.UI + "candidate.safe_spawn"), RuleValue.flag(candidate, RuleFields.SAFE_SPAWN, OutcomeCandidate.DEFAULT_SAFE_SPAWN));
            safeSpawnBox.setOnChanged(checked -> setCandidateStrategy(RuleFields.SAFE_SPAWN, checked));
            content.widgets.add(safeSpawnBox);
        }
        if (blocks) {
            fillOriginBox = new UiCheckBox(THEME, font, Component.translatable(RuleEditorPageSupport.UI + "candidate.fill_origin"), RuleValue.flag(candidate, RuleFields.FILL_ORIGIN, true));
            fillOriginBox.setOnChanged(checked -> setCandidateStrategy(RuleFields.FILL_ORIGIN, checked));
            content.widgets.add(fillOriginBox);
        }
        if (candidates > 1) {
            combinationButton = new UiButton(THEME, font, combinationLabel(rule), UiButtonVariant.SECONDARY,
                    this::cycleCombination)
                    .setCycleStyle(true)
                    .setFocusable(false);
            content.widgets.add(combinationButton);
            content.buttons.add(combinationButton);
        }

        int effectCount = ResultStructure.effectCount(rule, candidateIndex);
        effectIndex = Math.clamp(effectIndex, 0, Math.max(0, effectCount - 1));
        effectList = new UiListView<>(THEME, this::renderEffectRow);
        effectList.setRowHeight(ACTION_ROW_HEIGHT);
        effectList.setSelectionMarkerVisible(false);
        effectList.setChipRows(true);
        List<Integer> effects = new ArrayList<>();
        for (int i = 0; i < effectCount; i++) {
            effects.add(i);
        }
        effectList.setItems(effects);
        selectingEffect = true;
        effectList.setSelectedIndex(effectIndex);
        selectingEffect = false;
        effectList.setOnSelectionChanged(index -> {
            if (selectingEffect) {
                return;
            }
            commands.finishEditing();
            effectIndex = Math.max(0, index);
            commands.requestRebuild();
        });
        effectList.setOnActivate(index -> enterResultStage(ResultStage.DETAIL));
        content.widgets.add(effectList);
        // 与行为列表同属一栏：加号只在列表侧显示，窄屏 stage 2 的详情页不出现
        addEffectButton = new UiButton(THEME, font, Component.empty(), UiButtonVariant.SECONDARY, this::openEffectPicker)
                .setChipStyle(true)
                .setIcon(THEME.icon(UiHostIcon.ADD_CONDITION, false))
                .setAccessibleName(Component.translatable(RuleEditorPageSupport.UI + "button.effect_add"))
                .setTooltip(Component.translatable(RuleEditorPageSupport.UI + "button.effect_add"));
        content.widgets.add(addEffectButton);
        content.buttons.add(addEffectButton);
        revealSelectedEffect = true;
        if (effectCount > 0) {
            openActionButton = new UiButton(THEME, font,
                    Component.translatable(RuleEditorPageSupport.UI + "button.effect_edit"), UiButtonVariant.SECONDARY,
                    () -> enterResultStage(ResultStage.DETAIL)).setChipStyle(true);
            content.widgets.add(openActionButton);
            content.buttons.add(openActionButton);
        }

        RuleValue array = ResultStructure.effectsAt(rule, candidateIndex);
        if (array != null && effectIndex >= 0 && effectIndex < array.size()) {
            RuleValue element = array.item(effectIndex);
            String type = RuleValue.typeOf(element);
            ResourceLocation typeId = type == null ? null : ResourceLocation.tryParse(type);
            selectedAction = element != null && element.isMapping() ? element : null;
            if (typeId != null) {
                TypeEditorDescriptor descriptor = EditorRegistries.EFFECTS.descriptorFor(typeId);
                if (typeId.equals(ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, "spawn_entity"))
                        && element != null && element.isMapping()) {
                    descriptor = BuiltinEditorDescriptors.entityDescriptor(productStop.variant());
                    productButton = new UiButton(THEME, font, productLabel(productStop), UiButtonVariant.SECONDARY,
                            () -> cycleProduct(actionPath))
                            .setCycleStyle(true)
                            .setFocusable(false);
                    productButton.setTooltip(Component.translatable(RuleEditorPageSupport.UI + "results.product_switch"));
                    productButton.setAccessibleName(Component.translatable(RuleEditorPageSupport.UI + "results.product_switch"));
                    content.widgets.add(productButton);
                    content.buttons.add(productButton);
                }
                descriptor = BuiltinEditorDescriptors.withEffectLimits(descriptor, serverLimits());
                // 按动作顶层字段的声明层级分区，嵌套字段随所属顶层字段展示
                List<EditorField> editable = descriptor.fields();
                List<EditorField> primary = new ArrayList<>(editable.stream()
                        .filter(field -> field.tier() == ParamTier.PRIMARY)
                        .toList());
                List<EditorField> advanced = editable.stream()
                        .filter(field -> field.tier() == ParamTier.ADVANCED)
                        .toList();
                if (revealPath != null) {
                    String fieldName = actionPath.childName(revealPath);
                    showAdvancedEffects = advanced.stream().anyMatch(field -> field.name().equals(fieldName));
                }
                effectForm = forms.create(session, actionPath, new TypeEditorDescriptor(descriptor.id(), descriptor.label(), primary, descriptor.readOnly()));
                // 标签列固定为产物行共用的列宽：卡片页不跟随基本页的紧凑密度，产物轮的按钮才与表单控件左右对齐
                effectForm.setLabelWidth(DETAIL_LABEL_WIDTH);
                if (typeId.equals(PlaceBlockEffect.ID)) bindPlaceBlockVisibility(effectForm, session, actionPath);
                else if (typeId.equals(WeatherEffect.ID)) bindWeatherVisibility(effectForm, session, actionPath);
                else if (spawnEntity && (productStop == EntityProductStop.MOB || productStop == EntityProductStop.OTHER_ENTITY))
                    bindEntityProductUi(effectForm, actionPath);
                content.forms.add(effectForm);
                content.widgets.add(effectForm);
                if (!advanced.isEmpty()) {
                    advancedEffectForm = forms.create(session, actionPath, new TypeEditorDescriptor(descriptor.id(), descriptor.label(), advanced, false));
                    advancedEffectForm.setLabelWidth(DETAIL_LABEL_WIDTH);
                    content.forms.add(advancedEffectForm);
                    content.widgets.add(advancedEffectForm);
                    advancedButton = new UiButton(THEME, font, advancedLabel(), UiButtonVariant.SECONDARY, () -> {
                        commands.finishEditing();
                        showAdvancedEffects = !showAdvancedEffects;
                        commands.requestRebuild();
                    }).setChipStyle(true);
                    content.widgets.add(advancedButton);
                    content.buttons.add(advancedButton);
                }
            }
        }

        addPlanButton("results.plan_new", this::addCandidate);
        addPlanButton("results.plan_copy", this::duplicateCandidate);
        deletePlanButton = addPlanButton("results.plan_delete", this::removeCandidate);
        movePlanUpButton = addPlanButton("button.candidate_up", () -> moveCandidateBy(-1));
        movePlanDownButton = addPlanButton("button.candidate_down", () -> moveCandidateBy(1));
    }

    // 表单可见性按控件类型分发：本类只持有这几种控件，与布局开关一一对应
    private static void show(@Nullable UiWidget widget, boolean visible) {
        if (widget instanceof UiButton button) button.setVisible(visible);
        else if (widget instanceof UiListView<?> list) list.setVisible(visible);
        else if (widget instanceof FormView form) form.setVisible(visible);
        else if (widget instanceof UiCheckBox box) box.setVisible(visible);
    }

    /**
     * place_block 的字段联动：勾选「使用源物品的方块」后清除固定方块并隐藏该行，
     * 避免隐藏的固定方块仍按服务端优先级生效；shape 或 radius 变化时把 limit 的滑条窗口改为该形状在该半径下的容量，
     * 并把 limit 跟随到新的容量上限——limit 一旦被手动改过就不再跟随，直到切换到别的动作或规则。
     */
    private void bindPlaceBlockVisibility(FormView form, EditSession session, FieldPath actionPath) {
        FieldPath blockPath = actionPath.child(PlaceBlockEffect.BLOCK_FIELD);
        FieldPath useSourcePath = actionPath.child(PlaceBlockEffect.USE_SOURCE_BLOCK_FIELD);
        FieldPath shapePath = actionPath.child(PlaceBlockEffect.SHAPE_FIELD);
        FieldPath radiusPath = actionPath.child(PlaceBlockEffect.RADIUS_FIELD);
        FieldPath limitPath = actionPath.child(PlaceBlockEffect.LIMIT_FIELD);
        // 手工 JSON 可能同时写两个字段；固定方块仍生效时保留该行，避免把生效值藏起来
        form.field(PlaceBlockEffect.BLOCK_FIELD).visibleWhen(() -> !RuleValue.flag(
                session.valueAt(actionPath), PlaceBlockEffect.USE_SOURCE_BLOCK_FIELD, false)
                || session.valueAt(blockPath) != null);
        if (!actionPath.equals(placeBlockLinkPath) || placeBlockLinkSession != session) {
            placeBlockLinkPath = actionPath;
            placeBlockLinkSession = session;
            placeBlockLimitManual = false;
        }
        updatePlaceBlockWindows(form, session, actionPath);
        form.addFieldWritten((draft, path, before) -> {
            UiLinkedValues limitLink = placeBlockLimitLink(session, actionPath, draft);
            if (useSourcePath.equals(path)) {
                if (!RuleValue.flag(draft.valueAt(actionPath),
                        PlaceBlockEffect.USE_SOURCE_BLOCK_FIELD, false)) return;
                draft.removeAt(blockPath);
            } else if (shapePath.equals(path) || radiusPath.equals(path)) {
                // 形状或半径变化后上限跟随到新容量；手动上限保持原值。
                limitLink.driverChanged();
                placeBlockLimitManual = limitLink.manual();
            } else if (limitPath.equals(path)) {
                limitLink.followerChanged();
                placeBlockLimitManual = limitLink.manual();
            }
        });
        form.addFieldCommitted(path -> {
            if (useSourcePath.equals(path)) form.field(PlaceBlockEffect.BLOCK_FIELD).reload();
            if (shapePath.equals(path) || radiusPath.equals(path)) form.field(PlaceBlockEffect.LIMIT_FIELD).reload();
            updatePlaceBlockWindows(form, session, actionPath);
        });
    }

    /**
     * weather 的字段联动：mode=clear 时雷暴开关不生效，隐藏该行，避免露出一个改了也没有作用的勾选框。
     * <p>手工 JSON 可能已显式写 {@code thundering: true}，该值仍会参与执行，因此此时保留该行，不把生效值藏起来。
     *
     * @param form       weather 参数表单
     * @param session    当前编辑会话
     * @param actionPath 动作在草稿中的路径
     */
    private void bindWeatherVisibility(FormView form, EditSession session, FieldPath actionPath) {
        form.field(WeatherEffect.THUNDERING_FIELD).visibleWhen(() -> {
            RuleValue draft = session.valueAt(actionPath);
            // mode 的 JSON 取值由 EnumCodecs.lowerCase 派生；忽略大小写比较，与手写 JSON 的解析口径一致
            boolean clear = draft != null && draft.isMapping()
                    && WeatherEffect.Mode.CLEAR.name().equalsIgnoreCase(draft.text(WeatherEffect.MODE_FIELD, ""));
            return !clear || RuleValue.flag(draft, WeatherEffect.THUNDERING_FIELD, false);
        });
    }

    /**
     * 构造 place_block 的容量→limit 联动：驱动值是草稿当前形状与半径对应的容量，
     * 跟随值是草稿里的 limit，目标值就是容量本身，因此改形状与改半径都会让 limit 跟上。
     *
     * @param session    当前编辑会话
     * @param actionPath 动作在草稿中的路径
     * @return 联动绑定（带回上次记录的手动状态）
     */
    private UiLinkedValues placeBlockLimitLink(EditSession session, FieldPath actionPath, RuleDraft edits) {
        FieldPath limitPath = actionPath.child(PlaceBlockEffect.LIMIT_FIELD);
        UiLinkedValues.Accessor follower = new UiLinkedValues.Accessor() {
            @Override
            public double read() {
                RuleValue action = session.valueAt(actionPath);
                int fallback = placeBlockCapacity(session, actionPath);
                return action == null ? fallback : action.integer(PlaceBlockEffect.LIMIT_FIELD, fallback);
            }

            @Override
            public void write(double value) {
                edits.setAt(limitPath, RuleValue.integer((int) Math.round(value)));
            }
        };
        return new UiLinkedValues(UiLinkedValues.readOnly(() -> placeBlockCapacity(session, actionPath)),
                follower, capacity -> capacity, placeBlockLimitManual);
    }

    /**
     * 草稿里 place_block 当前形状与半径下的候选位置总数，也就是 limit 的容量上限。
     *
     * @param session    当前编辑会话
     * @param actionPath 动作在草稿中的路径
     * @return 当前形状在有效半径下的位置总数
     */
    private int placeBlockCapacity(EditSession session, FieldPath actionPath) {
        return PlaceBlockEffect.limitUpperBound(draftPlaceBlockShape(session, actionPath),
                draftPlaceBlockRadius(session, actionPath));
    }

    /**
     * 草稿里 place_block 的 shape：缺失或未知取值时按默认形状，保证容量计算始终有确定口径。
     *
     * @param session    当前编辑会话
     * @param actionPath 动作在草稿中的路径
     * @return 有效形状
     */
    private PlaceBlockEffect.Shape draftPlaceBlockShape(EditSession session, FieldPath actionPath) {
        RuleValue action = session.valueAt(actionPath);
        String value = action == null ? null : action.text(PlaceBlockEffect.SHAPE_FIELD, null);
        return PlaceBlockEffect.Shape.fromWire(value);
    }

    /**
     * 草稿里 place_block 的 radius：缺失或越界时按默认半径。
     *
     * @param session    当前编辑会话
     * @param actionPath 动作在草稿中的路径
     * @return 有效半径
     */
    private int draftPlaceBlockRadius(EditSession session, FieldPath actionPath) {
        RuleValue action = session.valueAt(actionPath);
        int radius = action == null ? PlaceBlockEffect.DEFAULT_RADIUS
                : action.integer(PlaceBlockEffect.RADIUS_FIELD, PlaceBlockEffect.DEFAULT_RADIUS);
        if (radius < PlaceBlockEffect.MIN_RADIUS || radius > serverMaxPlaceBlockRadius()) {
            return PlaceBlockEffect.DEFAULT_RADIUS;
        }
        return radius;
    }

    /**
     * 按草稿当前形状与 radius 设置 limit 行的滑条窗口上限（该形状覆盖的位置总数）；缺失或非法时按默认形状与半径计算。
     * 窗口只影响滑块手感，草稿里已有的 limit 值不随之夹取。
     *
     * @param form       place_block 参数表单
     * @param session    当前编辑会话
     * @param actionPath 动作在草稿中的路径
     */
    private void updatePlaceBlockWindows(FormView form, EditSession session, FieldPath actionPath) {
        form.field(PlaceBlockEffect.RADIUS_FIELD).sliderWindow(PlaceBlockEffect.MIN_RADIUS, serverMaxPlaceBlockRadius());
        form.field(PlaceBlockEffect.LIMIT_FIELD).sliderWindow(PlaceBlockEffect.MIN_LIMIT, placeBlockCapacity(session, actionPath));
    }

    /**
     * 服务端下发的单次放置半径上限：随开屏载荷到达，截图等没有会话载荷的工作区按配置默认值。
     *
     * @return radius 滑条的取值窗口上限
     */
    private int serverMaxPlaceBlockRadius() {
        return serverLimits().maxPlaceRadius();
    }

    private RuleRuntimeLimits serverLimits() {
        EditorOpenRequest request = shell.workspace().session().openRequest();
        return request == null ? ServerConfig.DEFAULT.runtimeLimits() : request.limits();
    }

    private UiButton addPlanButton(String key, UiAction action) {
        UiButton button = new UiButton(THEME, font, Component.translatable(RuleEditorPageSupport.UI + key),
                UiButtonVariant.SECONDARY, action).setChipStyle(true);
        content.candidateButtons.add(button);
        content.widgets.add(button);
        return button;
    }

    private Component combinationLabel(@Nullable RuleValue rule) {
        return Component.translatable(RuleEditorPageSupport.UI + "combination."
                + ResultStructure.combinationOf(rule));
    }

    void updateCombinationLabel(@Nullable RuleValue rule) {
        if (combinationButton != null) combinationButton.setLabel(combinationLabel(rule));
    }

    private void cycleCombination() {
        commands.finishEditing();
        String current = ResultStructure.combinationOf(shell.editingValue());
        String next = CombinationMode.ROUND_ROBIN.key().equals(current)
                ? CombinationMode.PRIORITY.key() : CombinationMode.ROUND_ROBIN.key();
        onCombinationChanged(next);
        updateCombinationLabel(shell.editingValue());
    }

    // 提示只表达级别：色值由屏幕层按级别取（编辑页宿主桥接层仍以颜色传递）
    private void notice(EditorShell.NoticeLevel level, Component message) {
        shell.setNotice(message, level);
    }

    void setCandidateStrategy(String field, boolean value) {
        EditSession session = shell.editingSession();
        if (session == null) {
            return;
        }
        RuleValue draft = session.root();
        boolean convert = !ResultStructure.hasOutcomes(draft);
        int index = Math.max(0, candidateIndex);
        boolean applied = commands.apply(convert ? EditSession.OP_CONVERT_STRUCTURE : EditSession.OP_EDIT_CANDIDATE, edits -> {
            if (convert) {
                edits.convertToOutcomes(EditorFactories.uniqueCandidateId(Set.of()));
            }
            edits.setAt(FieldPath.key(RuleFields.OUTCOMES).index(index).child(field),
                    RuleValue.flag(value));
        });
        if (applied && convert) {
            notice(EditorShell.NoticeLevel.INFO, Component.translatable(RuleEditorPageSupport.UI + "notice.converted"));
        }
    }

    void onCombinationChanged(String value) {
        EditSession session = shell.editingSession();
        if (session == null) {
            return;
        }
        commands.apply(EditSession.OP_SET_FIELD, edits -> edits.setAt(FieldPath.key(RuleFields.COMBINATION), RuleValue.text(value)));
    }

    void addCandidate() {
        EditSession session = shell.editingSession();
        if (session == null) {
            return;
        }
        RuleValue draft = session.root();
        if (ResultStructure.candidateCount(draft) >= ConditionLimits.MAX_EFFECTS) {
            notice(EditorShell.NoticeLevel.WARNING, Component.translatable(RuleEditorPageSupport.UI + "issue.candidate_limit", ConditionLimits.MAX_EFFECTS));
            return;
        }
        boolean convert = !ResultStructure.hasOutcomes(draft);
        if (!commands.apply(EditSession.OP_ADD_CANDIDATE, edits -> {
            if (convert) {
                edits.convertToOutcomes(EditorFactories.uniqueCandidateId(Set.of()));
            }
            RuleValue body = RuleValue.mapping()
                    .put(RuleFields.CANDIDATE_ID, RuleValue.text(EditorFactories.uniqueCandidateId(edits.candidateIds())))
                    .put(RuleFields.CANDIDATE_EFFECTS, RuleValue.list().build())
                    .build();
            edits.appendToList(FieldPath.key(RuleFields.OUTCOMES), body);
        })) {
            return;
        }
        candidateIndex = Math.max(0, ResultStructure.candidateCount(draft) - 1);
        effectIndex = 0;
        notice(EditorShell.NoticeLevel.INFO, Component.translatable(RuleEditorPageSupport.UI
                + (convert ? "notice.converted" : "notice.candidate_added")));
    }

    void duplicateCandidate() {
        commands.finishEditing();
        EditSession session = shell.editingSession();
        if (session == null) {
            return;
        }
        RuleValue draft = session.root();
        if (ResultStructure.candidateCount(draft) >= ConditionLimits.MAX_EFFECTS) {
            notice(EditorShell.NoticeLevel.WARNING, Component.translatable(RuleEditorPageSupport.UI + "issue.candidate_limit", ConditionLimits.MAX_EFFECTS));
            return;
        }
        boolean convert = !ResultStructure.hasOutcomes(draft);
        int index = Math.max(0, candidateIndex);
        if (!commands.apply(EditSession.OP_ADD_CANDIDATE, edits -> {
            if (convert) {
                edits.convertToOutcomes(EditorFactories.uniqueCandidateId(Set.of()));
            }
            int last = edits.outcomeCount() - 1;
            int from = index > last ? Math.max(0, last) : index;
            RuleValue source = edits.outcomeValueAt(from);
            if (source == null || !source.isMapping()) {
                return;
            }
            RuleValue body = RuleValue.mapping(source)
                    .put(RuleFields.CANDIDATE_ID, RuleValue.text(EditorFactories.uniqueCandidateId(edits.candidateIds())))
                    .build();
            edits.appendToList(FieldPath.key(RuleFields.OUTCOMES), body);
        })) {
            return;
        }
        candidateIndex = Math.max(0, ResultStructure.candidateCount(draft) - 1);
        effectIndex = 0;
        notice(EditorShell.NoticeLevel.INFO, Component.translatable(RuleEditorPageSupport.UI + "notice.candidate_added"));
    }

    void removeCandidate() {
        EditSession session = shell.editingSession();
        if (session == null) {
            return;
        }
        RuleValue draft = session.root();
        // 至少保留一个方案，与工具条的禁用边界一致。
        if (!ResultStructure.hasOutcomes(draft) || ResultStructure.outcomeCount(draft) <= 1) return;
        int index = Math.max(0, candidateIndex);
        if (!commands.applyStructure(EditSession.OP_REMOVE_CANDIDATE, edits -> edits.removeAt(FieldPath.key(RuleFields.OUTCOMES).index(index)))) {
            return;
        }
        candidateIndex = Math.max(0, index - 1);
        effectIndex = 0;
        notice(EditorShell.NoticeLevel.INFO, Component.translatable(RuleEditorPageSupport.UI + "notice.candidate_removed"));
    }

    void moveCandidateBy(int delta) {
        int target = candidateIndex + delta;
        if (delta == 0 || target < 0) {
            return;
        }
        EditSession session = shell.editingSession();
        if (session == null) {
            return;
        }
        RuleValue draft = session.root();
        if (target >= ResultStructure.outcomeCount(draft)) {
            return;
        }
        moveCandidate(candidateIndex, target);
    }

    void moveCandidate(int from, int to) {
        EditSession session = shell.editingSession();
        if (session == null || from == to) {
            return;
        }
        boolean[] moved = {false};
        if (!commands.apply(EditSession.OP_MOVE_CANDIDATE, edits -> moved[0] = edits.moveOutcome(from, to)) || !moved[0]) {
            return;
        }
        candidateIndex = to;
        effectIndex = 0;
    }

    void openEffectPicker() {
        commands.finishEditing();
        // 消耗类的编辑入口在「输入」页，三类消耗动作都不出现在这里，避免与说明行自相矛盾
        List<ResourceLocation> types = new ArrayList<>();
        for (TypeEditorDescriptor descriptor : EditorRegistries.EFFECTS.all()) {
            types.add(descriptor.id());
        }
        if (types.isEmpty()) {
            notice(EditorShell.NoticeLevel.WARNING, Component.translatable(RuleEditorPageSupport.UI + "notice.no_effect"));
            return;
        }
        if (scroll.viewport() == null) return;
        typePicker.open(types.stream().map(type -> new UiTypePicker.Option(type,
                EditorRegistries.EFFECTS.descriptorFor(type).label())).toList(), scroll.viewport());
    }

    void addEffectOfType(@Nullable ResourceLocation type) {
        EditSession session = shell.editingSession();
        if (session == null || type == null) {
            return;
        }
        RuleValue draft = session.root();
        if (ResultStructure.totalEffectCount(draft) >= ConditionLimits.MAX_EFFECTS) {
            notice(EditorShell.NoticeLevel.WARNING, Component.translatable(RuleEditorPageSupport.UI + "issue.candidate_limit", ConditionLimits.MAX_EFFECTS));
            return;
        }
        FieldPath listPath = ResultStructure.listPath(draft, candidateIndex);
        RuleValue body = BuiltinEditorDefaults.effectBody(type);
        // 服务端上限低于默认半径时，新建动作的半径与上限一并收进上限，避免一出生就非法
        int placeRadius = Math.min(PlaceBlockEffect.DEFAULT_RADIUS, serverMaxPlaceBlockRadius());
        if (!commands.applyStructure(EditSession.OP_ADD_EFFECT, edits -> {
            FieldPath effectPath = listPath.index(edits.effectCountAt(listPath));
            if (!edits.appendToList(listPath, body)) return;
            if (PlaceBlockEffect.ID.equals(type) && placeRadius < PlaceBlockEffect.DEFAULT_RADIUS) {
                edits.setAt(effectPath.child(PlaceBlockEffect.RADIUS_FIELD), RuleValue.integer(placeRadius));
                edits.setAt(effectPath.child(PlaceBlockEffect.LIMIT_FIELD),
                        RuleValue.integer(PlaceBlockEffect.limitUpperBound(PlaceBlockEffect.DEFAULT_SHAPE, placeRadius)));
            }
        })) {
            return;
        }
        effectIndex = Math.max(0, draft.listSizeAt(listPath) - 1);
    }

    private void removeEffect(int index) {
        EditSession session = shell.editingSession();
        if (session == null) {
            return;
        }
        RuleValue draft = session.root();
        FieldPath listPath = ResultStructure.listPath(draft, candidateIndex);
        if (index < 0 || index >= draft.listSizeAt(listPath)) {
            return;
        }
        if (!commands.applyStructure(EditSession.OP_REMOVE_EFFECT, edits -> edits.removeAt(listPath.index(index)))) {
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
        commands.finishEditing();
        removeEffect(index);
        return true;
    }

    void moveEffect(int from, int to) {
        EditSession session = shell.editingSession();
        if (session == null || from == to) {
            return;
        }
        RuleValue draft = session.root();
        FieldPath listPath = ResultStructure.listPath(draft, candidateIndex);
        boolean[] moved = {false};
        if (!commands.applyStructure(EditSession.OP_MOVE_EFFECT, edits -> moved[0] = edits.moveInList(listPath, from, to)) || !moved[0]) {
            return;
        }
        effectIndex = to;
    }

    void renderEffectRow(GuiGraphics graphics, Font renderFont, Integer item, int index, UiRect row, boolean selected, boolean hovered, boolean focused) {
        EditSession session = shell.editingSession();
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
            cached = effectRowText(shell.editingValue(), item, row);
            effectRows.put(item, cached);
        }
        if (cached.icon() != null) {
            cached.icon().render(graphics, row.x() + ACTION_CONTENT_INSET,
                    row.y() + (row.height() - DETAIL_ICON_SIZE) / 2, THEME);
        }
        UiRect remove = actionRemoveRect(row);
        graphics.drawString(font, cached.text(), row.x() + cached.inset(),
                row.y() + (row.height() - renderFont.lineHeight) / 2, THEME.palette().surfaces().text(), false);
        // 叉号与目录选中卡保持一致：悬停行才转成危险色，常亮红色会被读成「已有删除待确认」
        UiIcon close = UiMarkIcons.icon(UiMarkIcons.Mark.CLOSE,
                hovered ? THEME.palette().controls().danger() : THEME.palette().surfaces().secondaryText());
        close.render(graphics, remove.x() + (remove.width() - close.width()) / 2,
                remove.y() + (remove.height() - close.height()) / 2);
    }

    // 单行展示数据：标题文本、图标与文本内缩一起算好，裁剪宽度按行宽推出
    private EffectRowText effectRowText(@Nullable RuleValue view, int item, UiRect row) {
        RuleValue array = ResultStructure.effectsAt(view, candidateIndex);
        RuleValue element = array != null && item >= 0 && item < array.size() ? array.item(item) : null;
        RuleValue effect = element != null && element.isMapping() ? element : null;
        Component title = RuleNaming.effectTitle(effect, nameSource);
        String text = (item + 1) + ". " + title.getString();
        UiIcon icon = RulePreviewIcons.action(effect);
        int inset = ACTION_CONTENT_INSET + (icon == null ? 0 : DETAIL_ICON_SIZE + ACTION_ICON_TEXT_GAP);
        UiRect remove = actionRemoveRect(row);
        String trimmed = TextScroll.trimToWidth(font, text,
                Math.max(1, remove.x() - row.x() - inset - ACTION_REMOVE_INSET));
        return new EffectRowText(trimmed, icon, inset);
    }

    // 轮询产物档位：切换失败时保留原档位，避免界面档位与草稿 variant 不一致
    void cycleProduct(FieldPath actionPath) {
        EntityProductStop next = productStop.next();
        if (applyProductStop(actionPath, next)) {
            productStop = next;
            commands.requestRebuild();
        }
    }

    // 档位不落盘：只重写 variant 与该档位专属字段的默认值，其余字段按描述符重新生成
    private boolean applyProductStop(FieldPath path, EntityProductStop stop) {
        EditSession session = shell.editingSession();
        if (session == null) return false;
        commands.finishEditing();
        RuleValue old = session.valueAt(path);
        if (old == null || !old.isMapping()) return false;
        RuleValue.Mapping next = RuleValue.mapping(old);
        for (String field : List.of("item", "entity", "count", "age", "amount")) next.remove(field);
        next.put("variant", RuleValue.text(stop.variant()));
        if (stop == EntityProductStop.EXPERIENCE) {
            next.put("amount", RuleValue.integer(1));
        } else {
            next.put("count", RuleValue.integer(1));
            if (stop == EntityProductStop.MOB) next.put("age", RuleValue.integer(0));
        }
        return commands.applyStructure(EditSession.OP_SET_FIELD, edits -> edits.setAt(path, next.build()));
    }

    // 档位名直接写在轮询按钮上，不加箭头或图标
    private static Component productLabel(EntityProductStop stop) {
        return Component.translatable(RuleEditorPageSupport.UI + stop.labelKey());
    }

    // 目录条目按档位过滤：技术性/不可召唤类型两档都不出现；标签条目无法判定成员，两个实体档都保留
    private boolean catalogEntryAllowed(String id) {
        TaggedId reference = TaggedId.tryParse(id);
        if (reference == null || reference.tag()) return true;
        if (EntityTraits.isExcluded(id)) return false;
        boolean wantsMob = productStop == EntityProductStop.MOB;
        return wantsMob == EntityTraits.isMob(id);
    }

    // 选中实体里只要有一个可能吃年龄就保留年龄行，否则整行隐藏；年龄只属于生物档
    private boolean selectedEntityHasAge(FieldPath actionPath) {
        if (productStop != EntityProductStop.MOB) return false;
        List<String> ids = entitySelection(actionPath);
        return ids.isEmpty() || ids.stream().anyMatch(id -> {
            TaggedId reference = TaggedId.tryParse(id);
            return reference != null && (reference.tag() || EntityTraits.isAgeable(reference.id().toString()));
        });
    }

    // 草稿里的实体引用：字段可能是单个引用或引用列表
    private List<String> entitySelection(FieldPath actionPath) {
        EditSession session = shell.editingSession();
        if (session == null) return List.of();
        RuleValue element = session.valueAt(actionPath);
        RuleValue entity = element == null || !element.isMapping() ? null : element.child("entity");
        if (entity == null) return List.of();
        return entity.isList() ? entity.textItems() : List.of(entity.asText(""));
    }

    /**
     * 年龄轮询按钮的图标：当前选中实体在给定年龄下的预览。
     * <p>实体预览按 age 装载数据，幼年与成年因此是两张可区分的图；引用是标签、缺失或未注册时
     * 回落鸡，保证按钮在任何草稿状态下都能读出年龄差异。
     *
     * @param actionPath 动作在草稿中的路径
     * @param age        草稿里的年龄值；缺失或非数值按成年处理
     * @return 实体预览图标
     */
    private UiIcon ageToggleIcon(FieldPath actionPath, @Nullable RuleValue age) {
        int ticks = age == null || !age.isNumber()
                ? EntityProduct.Mob.ADULT_AGE : age.asInteger(EntityProduct.Mob.ADULT_AGE);
        return PreviewIcons.entity(agePreviewEntity(actionPath), ticks);
    }

    // 只有单个已注册实体适合做预览：标签成员可能混含不同年龄表现，回落到确定性更强的鸡
    private String agePreviewEntity(FieldPath actionPath) {
        for (String reference : entitySelection(actionPath)) {
            TaggedId parsed = TaggedId.tryParse(reference);
            if (parsed != null && !parsed.tag() && BuiltInRegistries.ENTITY_TYPE.containsKey(parsed.id())) {
                return parsed.id().toString();
            }
        }
        return AGE_PREVIEW_FALLBACK;
    }

    // 实体选择变化后决定年龄行是否出现；不再需要年龄时在同一条撤销里清掉该字段
    private void bindEntityProductUi(FormView form, FieldPath actionPath) {
        FieldPath entityPath = actionPath.child("entity");
        FieldPath agePath = actionPath.child("age");
        if (productStop == EntityProductStop.MOB) {
            form.field("age").visibleWhen(() -> selectedEntityHasAge(actionPath));
            // 年龄的两个预设合并成方形轮询按钮：图标直接给出幼年与成年的外观差异，
            // 比并排两个文字按钮省位，也不与右侧的目录「添加」按钮抢视觉权重
            form.field("age").presetToggle(age -> ageToggleIcon(actionPath, age),
                    Component.translatable(RuleEditorPageSupport.UI + "preset.age.toggle"),
                    Component.translatable(RuleEditorPageSupport.UI + "preset.age.toggle_hint"));
        }
        form.field("entity").catalogFilter(this::catalogEntryAllowed);
        form.addFieldWritten((draft, path, previous) -> {
            if (!entityPath.equals(path)) return;
            if (!selectedEntityHasAge(actionPath)) draft.removeAt(agePath);
        });
        if (productStop == EntityProductStop.MOB) form.addFieldCommitted(path -> {
            if (entityPath.equals(path)) form.field("age").reload();
        });
    }

    private Component advancedLabel() {
        return Component.translatable(RuleEditorPageSupport.UI
                + (showAdvancedEffects ? "results.advanced_open" : "results.advanced_closed"));
    }

    private void selectPlanBy(int delta) {
        commands.finishEditing();
        int count = Math.max(1, ResultStructure.candidateCount(shell.editingValue()));
        int target = candidateIndex + delta;
        if (target < 0 || target >= count || target == candidateIndex) return;
        candidateIndex = target;
        effectIndex = 0;
        showAdvancedEffects = false;
        enterResultStage(ResultStage.ACTIONS);
    }

    // 容器统一应用使能后恢复方案数量与位置边界，避免不可用操作被重新启用。
    void updatePlanButtons(boolean enabled) {
        int count = Math.max(1, ResultStructure.candidateCount(shell.editingValue()));
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

    private void enterResultStage(ResultStage stage) {
        commands.finishEditing();
        resultStage = stage;
        scroll.setOffset(0);
        commands.requestRebuild();
    }

    int layoutResults(UiRect area) {
        boolean split = area.width() >= NAVIGATION_MIN_WIDTH + DETAIL_MIN_WIDTH + RuleEditorPageSupport.CARD_GAP;
        boolean actions = split || resultStage == ResultStage.ACTIONS;
        boolean detail = split || resultStage == ResultStage.DETAIL;
        show(effectList, actions);
        show(addEffectButton, actions);
        show(openActionButton, !split && actions);
        show(resultBack, !split && detail);
        show(safeSpawnBox, detail);
        show(fillOriginBox, detail);
        show(effectForm, detail);
        show(productButton, detail);
        show(advancedButton, detail);
        show(advancedEffectForm, detail && showAdvancedEffects);
        detailHeaderRect = null;
        emptyDetailRect = null;
        actionParametersRect = null;
        effectNoteRect = null;
        productLabelRect = null;
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
        // 两栏共用一条下界：先由视口底与左栏「最少行数 + 加号」的下限取较大值，右栏再按自身内容抬高它。
        // 左栏比右栏更靠下时下界以左栏为准，右栏随之拉高，两卡底边始终一致。
        int sharedBottom = Math.max(area.bottom(), y + actionsFloorHeight());
        int detailBottom = layoutActionDetail(detailX, y, area.right() - detailX, sharedBottom - y);
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
            // 宽度按「最长组合方式文案」预留，再加轮询箭头：两者都取最长时切档不会重新换行
            sizes.add(Math.min(width, combinationOptionWidth + RuleEditorPageSupport.PAD * 2
                    + UiButton.cycleArrowWidth()));
        }
        for (UiButton button : content.candidateButtons) {
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
        content.cards.add(EditorSectionContent.PageCard.titled(new UiRect(x, top, width, bottom - top),
                Component.translatable(RuleEditorPageSupport.UI + "card.actions"), DETAIL_PADDING));
        return bottom;
    }

    /**
     * 摆放左侧一栏的动作列表、加号与底部按钮：列表按实际条目收紧，加号在列内水平居中并恒等于列表底边下移一个按钮间距。
     * <p>下界取「宿主给的高度」与「{@link #ACTION_LIST_MIN_ROWS} 条胶囊所需高」的较大值，所以列表至少能整装这么多条；
     * 条目更少时列表按内容收紧、加号紧随最后一条，条目更多时列表占满下界并滚动、加号停在下界的内边距之上。
     *
     * @param x               左缘
     * @param top             顶缘（卡片上边）
     * @param width           卡片宽
     * @param availableHeight 宿主给该卡片的高度
     * @return 卡片底边
     */
    private int layoutActionControls(int x, int top, int width, int availableHeight) {
        int contentX = x + DETAIL_PADDING;
        int contentWidth = Math.max(0, width - DETAIL_PADDING * 2);
        int listY = top + DETAIL_HEADER_HEIGHT + DETAIL_PADDING;
        // 先按 y=第一行 量一次 footer，拿到它的高度；最终位置由卡片底边反推
        List<UiButton> footer = new ArrayList<>();
        if (openActionButton != null && openActionButton.isVisible()) footer.addFirst(openActionButton);
        int footerHeight = EditorSectionContent.layoutButtonGroup(footer, contentX, listY, contentWidth);
        int addSize = effectList == null ? ACTION_ROW_HEIGHT : effectList.rowContentHeight();
        boolean hasAdd = addEffectButton != null && addEffectButton.isVisible();
        int addBlock = hasAdd ? addSize + RuleEditorPageSupport.BUTTON_GAP : 0;
        int footerBlock = footer.isEmpty() ? 0 : footerHeight + RuleEditorPageSupport.BUTTON_GAP;
        // 下界不低于「最少行数 + 加号 + 底部内边距」：宿主给的高度再小，列表也不会被压到最少行数以下
        int bottom = Math.max(top + availableHeight,
                listY + actionListFloorHeight() + addBlock + footerBlock + DETAIL_PADDING);
        int footerY = bottom - DETAIL_PADDING - footerHeight;
        // 底边确定后按同一宽度重摆 footer，组内换行结果与上面的量高完全一致
        EditorSectionContent.layoutButtonGroup(footer, contentX, footerY, contentWidth);
        int listLimit = Math.max(0, footerY - (footer.isEmpty() ? 0 : RuleEditorPageSupport.BUTTON_GAP) - listY);
        // 空列表的所需高按一行内容算：空态提示居中绘制在视口里，高度为 0 会把文字压到加号上
        int listRequired = effectList == null ? 0
                : effectList.size() == 0 ? effectList.rowContentHeight() : effectList.contentHeight();
        // 视口比外框矮两像素，外框要按内容高加上内缩，条目刚好占满时才不会冒出滚动条
        int listFit = listRequired + (effectList == null ? 0 : effectList.viewportInset());
        int listHeight = Math.clamp(listLimit - addBlock, 0, listFit);
        if (effectList != null) effectList.setBounds(contentX, listY, contentWidth, listHeight);
        // 方形加号：边长取胶囊行内容高，列内水平居中，与列表底边只隔一个按钮间距
        if (hasAdd) {
            // 加号跟随列表底边，但不越过下界：列表占满时它停在下界的内边距之上
            int addY = Math.min(listY + listHeight + RuleEditorPageSupport.BUTTON_GAP,
                    bottom - DETAIL_PADDING - addSize);
            addEffectButton.setBounds(contentX + (contentWidth - addSize) / 2, addY, addSize, addSize);
        }
        return bottom;
    }

    /**
     * 左栏整装 {@link #ACTION_LIST_MIN_ROWS} 条胶囊所需的外框高：行距按条目间距步进，末行只占内容高，再加视口内缩。
     *
     * @return 列表外框高；列表未建好时返回 0
     */
    private int actionListFloorHeight() {
        if (effectList == null) return 0;
        return (ACTION_LIST_MIN_ROWS - 1) * ACTION_ROW_HEIGHT
                + effectList.rowContentHeight() + effectList.viewportInset();
    }

    /**
     * 左栏在「最少行数 + 加号 + 底部内边距」下的内容高度（含卡片头）：分裂栏据此与右栏比较取较大值当共用下界。
     *
     * @return 左栏最少内容高度
     */
    private int actionsFloorHeight() {
        boolean hasAdd = addEffectButton != null && addEffectButton.isVisible() && effectList != null;
        int addBlock = hasAdd ? effectList.rowContentHeight() + RuleEditorPageSupport.BUTTON_GAP : 0;
        return DETAIL_HEADER_HEIGHT + DETAIL_PADDING + actionListFloorHeight() + addBlock + DETAIL_PADDING;
    }

    private int layoutActionDetail(int x, int top, int width, int availableHeight) {
        // 头部矩形与右栏卡片的 PageCard 走同一个派生函数，避免绘制与命中区各自计算
        detailHeaderRect = EditorSectionContent.cardHeaderRect(x, top, width, DETAIL_PADDING);
        int contentX = detailHeaderRect.x();
        int contentWidth = detailHeaderRect.width();
        int y = top + DETAIL_HEADER_HEIGHT + DETAIL_PADDING;
        boolean empty = effectList == null || effectList.size() == 0;
        if (!empty) {
            if (productButton != null) {
                // 产物行与下面的表单共用同一个标签列宽与行高：按固定像素另算一套，
                // 轮询按钮会相对实体、数量、年龄各行左右偏移，高度也高出一截
                int labelWidth = effectForm == null ? DETAIL_LABEL_WIDTH : effectForm.labelColumnWidth(contentWidth);
                int labelGap = effectForm == null ? RuleEditorPageSupport.PAD : effectForm.labelGap();
                int rowHeight = effectForm == null ? TOOLBAR_HEIGHT : effectForm.rowControlHeight();
                // 行间距跟随表单：产物行就是参数表的第一行，走另一套间距会与下面各行浮开
                int rowGap = effectForm == null ? RuleEditorPageSupport.PAD : effectForm.rowGap();
                if (labelWidth == 0) {
                    // 窄宽度下标签与表单一致地叠到控件上方，标签占一整行
                    productLabelRect = new UiRect(contentX, y, contentWidth, font.lineHeight);
                    y += font.lineHeight;
                    productButton.setBounds(contentX, y, contentWidth, rowHeight);
                } else {
                    productLabelRect = new UiRect(contentX, y + (rowHeight - font.lineHeight) / 2,
                            labelWidth, font.lineHeight);
                    productButton.setBounds(contentX + labelWidth + labelGap, y,
                            Math.max(0, contentWidth - labelWidth - labelGap), rowHeight);
                }
                y += rowHeight + rowGap;
            }
            if (readonlyEffectNote(shell.editingValue()) != null) {
                effectNoteRect = new UiRect(contentX, y, contentWidth, RuleEditorPageSupport.LINE_H);
                y += RuleEditorPageSupport.LINE_H + RuleEditorPageSupport.PAD;
            }
            // 方案级开关属于常规选项，不随行为高级参数的折叠状态隐藏；从方块起点填充放在参数之前
            if (fillOriginBox != null) {
                fillOriginBox.setBounds(contentX, y, contentWidth, RuleEditorPageSupport.ROW_H);
                y += RuleEditorPageSupport.ROW_H + RuleEditorPageSupport.PAD;
            }
            y = EditorSectionContent.layoutForm(effectForm, contentX, y, contentWidth);
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
                y = EditorSectionContent.layoutForm(advancedEffectForm, contentX, y, contentWidth);
            }
        } else {
            // 空态只留标题与引导句：添加入口已收敛到左侧列表的加号，右卡不再重复按钮
            y += font.lineHeight + EMPTY_MESSAGE_GAP
                    + emptyHelpLines(contentWidth).size() * (font.lineHeight + 2) + DETAIL_PADDING;
        }
        int bottom = Math.max(top + availableHeight, y + DETAIL_PADDING);
        if (empty) emptyDetailRect = new UiRect(contentX, top + DETAIL_HEADER_HEIGHT + DETAIL_PADDING,
                contentWidth, Math.max(0, bottom - top - DETAIL_HEADER_HEIGHT - DETAIL_PADDING * 2));
        content.cards.add(EditorSectionContent.PageCard.dynamic(new UiRect(x, top, width, bottom - top),
                this::detailHeader, DETAIL_PADDING));
        return bottom;
    }

    // 右栏头部：空态显示卡片标题，否则显示所选动作的图标与名称
    private EditorSectionContent.CardHeader detailHeader() {
        boolean empty = emptyDetailRect != null;
        Object language = Language.getInstance();
        EditSession session = shell.editingSession();
        long revision = session == null ? -1 : session.revision();
        if (headerCached && headerEmpty == empty && headerRevision == revision
                && headerAction == selectedAction && headerLanguage == language) {
            return headerValue;
        }
        headerCached = true;
        headerEmpty = empty;
        headerRevision = revision;
        headerAction = selectedAction;
        headerLanguage = language;
        EditorSectionContent.CardHeader next = empty
                ? new EditorSectionContent.CardHeader(
                Component.translatable(RuleEditorPageSupport.UI + "card.action_detail"), null)
                : new EditorSectionContent.CardHeader(RuleNaming.effectTitle(selectedAction, nameSource),
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
            content.drawText(graphics, new UiRect(emptyDetailRect.x(), y, emptyDetailRect.width(), font.lineHeight),
                    emptyTitleText, THEME.palette().surfaces().text());
            y += font.lineHeight + EMPTY_MESSAGE_GAP;
            for (var line : lines) {
                graphics.drawString(font, line, emptyDetailRect.x(), y, THEME.palette().surfaces().secondaryText(), false);
                y += font.lineHeight + 2;
            }
        }
        Component readonlyNote = readonlyEffectNote(shell.editingValue());
        if (readonlyNote != null) content.drawText(graphics, effectNoteRect, readonlyNote, THEME.palette().status().warning());
        content.drawText(graphics, productLabelRect, productKindText, THEME.palette().surfaces().secondaryText());
        content.drawText(graphics, actionParametersRect, actionParametersText, THEME.palette().surfaces().secondaryText());
    }

    // 悬停只补画面上读不到的信息：动作名、叉号、翻页箭头与产品分段都有文字或符号，不再重复解释。


    // 页面自绘文字的矩形停在文档坐标（绘制时由滚动位姿统一平移），命中判定要把指针换算回文档坐标
    private boolean hitsDocumentRect(UiRect rect, double mouseX, double mouseY) {
        return rect.contains(mouseX, mouseY + scroll.offset());
    }

    // 描述只挂在详情卡片的标题上：行内动作卡片已有名称与图标，悬停不再重复解释。
    UiPointerHit describeHit(@Nullable UiPointerHit hit, double x, double y) {
        if (typePicker.isOpen()) return hit;
        Component text = null;
        if (planPager != null && planPager.isLabelHovered(x, y))
            text = RuleNaming.candidateTitle(ResultStructure.candidateAt(shell.editingValue(), candidateIndex), candidateIndex + 1, nameSource);
        else if (hit != null && combinationButton != null && hit.owns(combinationButton))
            text = Component.translatable(RuleEditorPageSupport.UI + "results.combination_help");
        else if (hit != null && deletePlanButton != null && hit.owns(deletePlanButton))
            text = Component.translatable(RuleEditorPageSupport.UI + "results.remove_undo_hint");
        else if (hit == null && effectNoteRect != null && hitsDocumentRect(effectNoteRect, x, y))
            text = readonlyEffectNote(shell.editingValue());
        if (text != null) {
            if (hit == null) hit = UiPointerHit.leaf(scroll, x, y).preservingFocus();
            return hit.withTextTooltip(List.of(text), scroll.viewport());
        }
        if (hit == null && selectedAction != null && selectedAction.isMapping()
                && detailHeaderRect != null && hitsDocumentRect(detailHeaderRect, x, y)) {
            RuleValue action = selectedAction;
            return UiPointerHit.leaf(scroll, x, y).preservingFocus().withPresentation((graphics, ignoredFont, mx, my) ->
                    new UiDescriptionTooltip(THEME, font).render(graphics,
                            new UiRect(0, 0, shell.screenWidth(), shell.screenHeight()), ActionDescription.lines(action), mx, my));
        }
        return hit;
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
        productKindText = Component.translatable(RuleEditorPageSupport.UI + "results.product_kind");
        readonlyUnknownText = Component.translatable(RuleEditorPageSupport.UI + "effect.readonly_note_unknown");
        combinationOptionWidth = Arrays.stream(CombinationMode.values())
                .mapToInt(mode -> font.width(
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
        emptyHelpLines = font.split(
                Component.translatable(RuleEditorPageSupport.UI + "results.empty_help"), Math.max(1, width));
        return emptyHelpLines;
    }

    @Nullable Component readonlyEffectNote(@Nullable RuleValue view) {
        ensureStaticTexts();
        EditSession session = shell.editingSession();
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
        String type = RuleValue.typeOf(effects.item(effectIndex));
        ResourceLocation typeId = type == null ? null : ResourceLocation.tryParse(type);
        if (typeId == null) {
            readonlyNoteValue = readonlyUnknownText;
        } else if (EditorRegistries.EFFECTS.find(typeId) != null) {
            readonlyNoteValue = null;
        } else {
            readonlyNoteValue = Component.translatable(RuleEditorPageSupport.UI + "effect.readonly_note",
                    typeId.toString());
        }
        return readonlyNoteValue;
    }

    /**
     * 方案翻页组：只让左右按钮接收输入，页码与两箭头保持同一行。
     */
    private final class PlanPager implements UiWidget {
        private final Component label;
        private final UiButton previous;
        private final UiButton next;
        private UiRect bounds = new UiRect(0, 0, 0, 0);
        private UiRect labelRect = bounds;

        private PlanPager(int count) {
            label = Component.translatable(RuleEditorPageSupport.UI + "results.plan_page", candidateIndex + 1, count);
            previous = new UiButton(THEME, font, Component.empty(),
                    UiButtonVariant.SECONDARY, () -> selectPlanBy(-1)).setChipStyle(true);
            next = new UiButton(THEME, font, Component.empty(),
                    UiButtonVariant.SECONDARY, () -> selectPlanBy(1)).setChipStyle(true);
            previous.setAccessibleName(Component.translatable(RuleEditorPageSupport.UI + "results.previous_plan"));
            next.setAccessibleName(Component.translatable(RuleEditorPageSupport.UI + "results.next_plan"));
            setIconColors(candidateIndex > 0, candidateIndex + 1 < count);
        }

        // 标记贴图不带禁用态，只能按可用性换染色；启用色与胶囊文字色保持一致。
        private void setIconColors(boolean previousEnabled, boolean nextEnabled) {
            previous.setIcon(UiMarkIcons.icon(UiMarkIcons.Mark.PREVIOUS,
                    previousEnabled ? THEME.palette().surfaces().text() : THEME.palette().surfaces().disabledText()));
            next.setIcon(UiMarkIcons.icon(UiMarkIcons.Mark.NEXT,
                    nextEnabled ? THEME.palette().surfaces().text() : THEME.palette().surfaces().disabledText()));
        }

        private int preferredWidth() {
            return TOOLBAR_HEIGHT * 2 + font.width(label) + RuleEditorPageSupport.BUTTON_GAP * 2;
        }

        /**
         * @return 整组的屏幕矩形
         */
        @Override
        public UiRect bounds() {
            return bounds;
        }

        /**
         * @return 指针是否落在只读页码上：翻页箭头有图标说明方向，提示只讲当前方案。
         */
        private boolean isLabelHovered(double mouseX, double mouseY) {
            return labelRect.contains(mouseX, mouseY);
        }

        /**
         * 分配左右按钮与中间页码的同排几何。
         *
         * @param x      左缘
         * @param y      顶缘
         * @param width  可用宽度
         * @param height 行高
         */
        @Override
        public void setBounds(int x, int y, int width, int height) {
            bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
            int buttonWidth = Math.min(TOOLBAR_HEIGHT, bounds.width() / 2);
            previous.setBounds(x, y, buttonWidth, bounds.height());
            next.setBounds(bounds.right() - buttonWidth, y, buttonWidth, bounds.height());
            labelRect = new UiRect(x + buttonWidth, y, Math.max(0, bounds.width() - buttonWidth * 2), bounds.height());
        }

        /**
         * 居中绘制只读页码；按钮由页面统一绘制。
         *
         * @param graphics 绘制上下文
         * @param font     绘制字体
     * @param pointer 已解析的绘制指针与作用域门禁
         */
        @Override
        public void render(GuiGraphics graphics, Font font, UiPointer pointer) {
            String text = TextScroll.trimToWidth(font, label.getString(), labelRect.width());
            graphics.drawString(font, text, labelRect.x() + (labelRect.width() - font.width(text)) / 2,
                    labelRect.y() + (labelRect.height() - font.lineHeight) / 2, THEME.palette().surfaces().text(), false);
        }
    }

    void applyEnabled(boolean editable) {
        RuleValue view = shell.editingValue();
        if (combinationButton != null) {
            combinationButton.setEnabled(editable && ResultStructure.hasOutcomes(view));
        }
        if (safeSpawnBox != null) {
            safeSpawnBox.setEnabled(editable);
        }
        if (fillOriginBox != null) {
            fillOriginBox.setEnabled(editable);
        }
        updatePlanButtons(editable);
    }

    void restoreSwitches() {
        RuleValue view = shell.editingValue();
        if (view == null) return;
        updateCombinationLabel(view);
        RuleValue candidate = ResultStructure.candidateAt(view, candidateIndex);
        if (safeSpawnBox != null) {
            safeSpawnBox.setChecked(RuleValue.flag(candidate, RuleFields.SAFE_SPAWN, OutcomeCandidate.DEFAULT_SAFE_SPAWN));
        }
        if (fillOriginBox != null) {
            fillOriginBox.setChecked(RuleValue.flag(candidate, RuleFields.FILL_ORIGIN, true));
        }
    }
}
