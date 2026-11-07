package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.FieldPath;
import java.util.Objects;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.client.ui.shared.PreviewResources;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.core.api.param.SelectionIntent;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiCyclicRange;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiNumberPolicy;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiRangeEnd;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiRangeValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiSliderWindow;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.LeafPanel;
import com.meteorite.itemdespawntowhat.client.ui.shared.PreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.CatalogPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.CatalogTypePresentation;
import com.meteorite.itemdespawntowhat.client.ui.shared.CatalogDisplayQuery;
import com.meteorite.itemdespawntowhat.client.ui.shared.FieldCatalogChoices;
import com.meteorite.itemdespawntowhat.client.ui.shared.FieldCatalogTypes;
import com.meteorite.itemdespawntowhat.client.ui.shared.CatalogSelectionBinding;
import com.meteorite.itemdespawntowhat.client.ui.shared.TagPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCatalogGrid;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCyclicTimeBar;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiModal;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiRangeBar;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiWidget;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalog;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalogEntry;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalogType;
import com.meteorite.itemdespawntowhat.core.type.condition.BiomeCondition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 编辑页的面板装配工厂：目录选择页、昼夜/高度区间条与叶子面板装配。
 * （面板实现见 {@link LeafPanel}）。
 * <p>本类只做「把 kit 控件与目录链路接到规则草稿上」的装配，不复制数据层逻辑：
 * 写入一律经调用方给定的 {@code applyChange}（内部走 {@link EditSession#apply(String, java.util.function.Consumer)}，
 * 一次完整操作 = 一条撤销记录）。
 * <p>目录条目、图标与文本由宿主解析：{@link CatalogTypePresentation} 按类型解释名称，
 * 图标分发见 {@link com.meteorite.itemdespawntowhat.client.ui.shared.CatalogPreviewIcons#iconFor(RuleCatalogType, String)}，
 * 其余目录类型回落文字槽。
 */
public final class RuleEditorPanelFactory {

    // 宿主默认主题：屏幕从主题入口取样式后注入本类装配的控件

    private static final String RANGE_MIN_FIELD = "min";
    private static final String RANGE_MAX_FIELD = "max";
    private static final int FALLBACK_RANGE_MIN = -2048;
    private static final int FALLBACK_RANGE_MAX = 2048;

    private static final String UI = TypeLabels.UI_PREFIX;
    private static final ResourceLocation Y_LEVEL_ID = ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, "y_level");
    // 一天 24000 刻；0 刻对应 Minecraft 06:00
    private static final double DAY_TICKS = 24000.0D;
    private static final int DAY_MINUTE_OFFSET = 360;
    private static final int TIME_MIN = 0;
    private static final int TIME_MAX = 23999;
    private static final int DEFAULT_TIME_RANGE_END = 12000;

    // 选择页尺寸：占屏幕大部分，一屏能列出的条目足够多，同时保住复古网页的窗口比例
    private static final int PICKER_MIN_WIDTH = 280;
    private static final int PICKER_MAX_WIDTH = 440;
    private static final int PICKER_MIN_BODY_HEIGHT = 120;
    private static final int PICKER_MAX_BODY_HEIGHT = 300;
    private static final int PICKER_MARGIN = 32;

    private RuleEditorPanelFactory() {
    }

    // ---- 时间换算 ----

    // 刻 -> 时钟文本（0 刻 = 06:00）；输入先折返到 [0, 24000)
    public static Component clockText(double tick) {
        if (!Double.isFinite(tick)) {
            return Component.literal("--:--");
        }
        int wrapped = (int) Math.round(((tick % DAY_TICKS) + DAY_TICKS) % DAY_TICKS);
        int minuteOfDay = (wrapped * 1440 / 24000 + DAY_MINUTE_OFFSET) % 1440;
        return Component.literal(String.format(Locale.ROOT, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60));
    }

    // 24 个小时刻度：每 3 小时带文字，其余只有刻度线
    public static List<UiCyclicRange.Tick> dayTicks() {
        List<UiCyclicRange.Tick> ticks = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            double tick = hour * 1000.0D;
            ticks.add(new UiCyclicRange.Tick(tick, hour % 3 == 0 ? clockText(tick) : null));
        }
        return ticks;
    }

    public static UiCyclicTimeBar.Texts cyclicTexts() {
        return new UiCyclicTimeBar.Texts(Component.translatable(UI + "time.single"),
                Component.translatable(UI + "time.wrap"), Component.translatable(UI + "time.precise"));
    }

    public static UiRangeBar.Texts rangeTexts() {
        return new UiRangeBar.Texts(Component.translatable(UI + "range.low"),
                Component.translatable(UI + "range.high"), Component.translatable(UI + "range.absent"),
                Component.translatable(UI + "range.precise"));
    }

    /** 用调用方主题装配一天内循环时间条；预设点击提交一次。 */
    public static UiCyclicTimeBar dailyBar(UiHostTheme THEME, Font font, UiCyclicTimeBar.Listener listener) {
        UiCyclicTimeBar bar = new UiCyclicTimeBar(THEME, font, cyclicTexts(), listener);
        bar.setTicks(dayTicks());
        bar.setFormatter(RuleEditorPanelFactory::clockText);
        bar.setPresets(List.of(
                new UiCyclicTimeBar.Preset(Component.translatable(UI + "time.preset_all"), 0.0D, 23999.0D),
                new UiCyclicTimeBar.Preset(Component.translatable(UI + "time.preset_day"), 0.0D, 11000.0D),
                new UiCyclicTimeBar.Preset(Component.translatable(UI + "time.preset_night"), 12000.0D, 23000.0D),
                new UiCyclicTimeBar.Preset(Component.translatable(UI + "time.preset_noon"), 6000.0D, 6000.0D)));
        return bar;
    }

    // ---- 目录面板 ----

    // 只有物品来源的目录页（如源物品黑名单）；按目录类型取文案请走下面的重载
    public static UiCatalogGrid.Texts catalogTexts() {
        return catalogTexts(RuleCatalogType.ITEM, false);
    }

    // 范围按钮的短标签与悬停说明按当前目录的主分类生成，方块、流体、实体等选择窗不再共用「物品」
    private static UiCatalogGrid.Texts catalogTexts(RuleCatalogType type, boolean combined) {
        Component primary = catalogScopeLabel(type);
        return new UiCatalogGrid.Texts(Component.translatable(UI + (combined ? "pick.search_combined" : "pick.search")),
                Component.translatable(UI + "pick.empty"), Component.translatable(UI + "pick.loading"),
                Component.translatable(UI + "pick.error"), Component.translatable(UI + "pick.prev"),
                Component.translatable(UI + "pick.next"), Component.translatable(UI + "pick.selected"),
                Component.translatable(UI + "pick.scope_only", primary), Component.translatable(UI + "pick.scope_tag"),
                Component.translatable(UI + "pick.scope_with_tags", primary),
                primary,
                Component.translatable(UI + "pick.scope_tag_label"),
                Component.translatable(UI + "pick.scope_both_label"), Component.translatable(UI + "pick.clear"));
    }

    private static Component catalogScopeLabel(RuleCatalogType type) {
        return Component.translatable(UI + CatalogTypePresentation.of(type).scopeLabel());
    }

    /**
     * 打开字段目录，并只列出条目过滤器接受的资源引用。
     * @param entryFilter 条目过滤器，null 表示不限；只作用于资源条目，标签条目不受限制
     */
    static UiModal fieldCatalogModal(Font font, EditorShell shell, RuleEditClientWorkspace workspace, RuleCatalogType type,
                                     CatalogSelectionBinding binding, Component title, int screenWidth, int screenHeight,
                                     EditorField field, RuleValue draft,
                                     FieldPath path, @Nullable Predicate<String> entryFilter) {
        return catalogModal(font, shell, workspace, type, binding, title, screenWidth, screenHeight,
                field, SourceCatalystSelectionPolicy.forField(draft, path), entryFilter);
    }

    private static UiModal catalogModal(Font font, EditorShell shell, RuleEditClientWorkspace workspace, RuleCatalogType type,
                                        CatalogSelectionBinding binding, Component title, int screenWidth, int screenHeight,
                                        @Nullable EditorField field,
                                        Function<String, @Nullable Component> unavailableReason,
                                        @Nullable Predicate<String> entryFilter) {
        return new CatalogPicker(font, shell, workspace, type, binding, title, screenWidth, screenHeight,
                field, unavailableReason, entryFilter).modal;
    }

    // 选择页宽度：屏幕越宽弹窗越宽，但保留窗口感
    public static int pickerWidth(int screenWidth) {
        return Math.clamp(screenWidth - PICKER_MARGIN, PICKER_MIN_WIDTH, PICKER_MAX_WIDTH);
    }

    /** 依据注入主题扣除弹窗固定高度，计算目录列表可用高度。 */
    public static int pickerBodyHeight(UiHostTheme theme, int screenHeight) {
        return Math.clamp(screenHeight - theme.headerHeight() - theme.padding() * 3L - UiModal.BUTTON_HEIGHT - PICKER_MARGIN, PICKER_MIN_BODY_HEIGHT,
                PICKER_MAX_BODY_HEIGHT);
    }

    // 平铺目录宿主独占查询、选择与发布；目录映射和名称缓存留在查询实现内。
    private static final class CatalogPicker implements UiCatalogGrid.Listener {
        private final EditorShell shell;
        private final RuleEditClientWorkspace workspace;
        private final RuleCatalogType type;
        private final CatalogSelectionBinding binding;
        private final Function<String, @Nullable Component> unavailableReason;
        private final UiModal modal;
        private final UiCatalogGrid grid;
        private List<String> selection;
        private UiCatalogGrid.View view;
        private long contentGeneration;
        private int noteTotal = -1;
        private String errorKey;

        private CatalogPicker(Font font, EditorShell shell, RuleEditClientWorkspace workspace, RuleCatalogType type,
                              CatalogSelectionBinding binding, Component title, int width, int height,
                              @Nullable EditorField field, Function<String, @Nullable Component> unavailableReason,
                              @Nullable Predicate<String> entryFilter) {
            this.shell = shell;
            this.workspace = workspace;
            this.type = type;
            this.binding = binding;
            this.field = field;
            this.unavailableReason = unavailableReason;
            this.entryFilter = entryFilter;
            includeTags = field != null && field.allowsTags();
            policy = includeTags ? CatalogEntryPolicy.scoped(type) : CatalogEntryPolicy.fixed(type);
            selection = List.copyOf(binding.mode().snapshot(binding.initialSelection()));
            workspace.refreshDirectory(type);
            if (includeTags) workspace.refreshDirectory(RuleCatalogType.TAG);
            modal = UiModal.create(shell.theme(), font);
            grid = new UiCatalogGrid(shell.theme(), font, binding.mode(), catalogTexts(type, includeTags), this);
            grid.configure(policy, unavailableReason);
            grid.setOnRetry(() -> {
                workspace.retryDirectory(type);
                if (includeTags) workspace.retryDirectory(RuleCatalogType.TAG);
                prepare();
            });
            grid.setTooltipSuffix(id -> {
                var tag = tagPreviews.get(id);
                if (tag != null) return tag.tooltip();
                if (type == RuleCatalogType.ENTITY && PreviewIcons.entityUnavailable(id, 0)
                        || type == RuleCatalogType.BLOCK && PreviewIcons.blockUnavailable(id))
                    return Component.translatable(UI + "preview.unavailable");
                return null;
            });
            modal.title(title).preferredWidth(pickerWidth(width)).inlineFooter();
            modal.contentWidget(grid, pickerBodyHeight(shell.theme(), height));
            modal.confirm(Component.translatable(UI + "button.confirm"), () -> {
                if (shell.editable() && selection.stream().noneMatch(id -> unavailableReason.apply(id) != null))
                    binding.confirm(selection);
            });
            modal.cancel(Component.translatable(UI + "button.cancel"));
            modal.onPrepare(this::prepare);
            prepare();
            modal.layoutCentered(width, height);
        }

        private void prepare() {
            List<UiCatalogGrid.Entry> mapped = queryEntries();
            boolean active = shell.editable();
            boolean loading = cached == null || !cached.lastPage()
                    || includeTags && (cachedTags == null || !cachedTags.lastPage());
            String nextError = workspace.directory(type).failed()
                    || includeTags && workspace.directory(RuleCatalogType.TAG).failed() ? "pick.retry_hint"
                    : active ? null : "pick.frozen";
            UiCatalogGrid.Content content = view == null || view.content().generation() != contentGeneration
                    || view.content().selection() != selection
                    ? new UiCatalogGrid.Content(contentGeneration, mapped, selection) : view.content();
            UiCatalogGrid.Navigation navigation = new UiCatalogGrid.Navigation(scope, filter, page, pageCount, null);
            UiCatalogGrid.Status status = view == null ? null : view.status();
            if (status == null || status.loading() != loading || status.enabled() != active
                    || noteTotal != total || !Objects.equals(errorKey, nextError)) {
                status = new UiCatalogGrid.Status(loading, active, Component.translatable(UI + "pick.total", total),
                        nextError == null ? null : Component.translatable(UI + nextError));
                noteTotal = total;
                errorKey = nextError;
            }
            view = new UiCatalogGrid.View(content, navigation, status);
            grid.applyView(view);
            boolean canConfirm = active && selection.stream().noneMatch(id -> unavailableReason.apply(id) != null);
            modal.buttons().getFirst().setEnabled(canConfirm);
        }

        /** {@inheritDoc} */
        @Override public void onSelectionChanged(List<String> next) {
            selection = List.copyOf(binding.mode().snapshot(next));
            prepare();
        }
        /** {@inheritDoc} */
        @Override public void onSearch(String next) {
            page = 0;
            filter = next;
            prepare();
        }
        /** {@inheritDoc} */
        @Override public void onPageChange(int delta) {
            if (delta > 0 && !hasNext || delta < 0 && page == 0) return;
            page += delta;
            prepare();
        }
        /** {@inheritDoc} */
        @Override public void onScopeChanged(UiCatalogGrid.Scope next) {
            scope = next;
            page = 0;
            prepare();
        }
        /** {@inheritDoc} */
        @Override public void onConfirm() { modal.buttons().getFirst().activate(); }


        private RuleCatalog cached;
        private RuleCatalog cachedTags;
        private final boolean includeTags;
        private final @Nullable EditorField field;
        private final CatalogEntryPolicy policy;
        // 条目可见范围：只作用于资源条目，标签条目不受限制
        private final @Nullable Predicate<String> entryFilter;
        private List<UiCatalogGrid.Entry> entries = List.of();
        private int page;
        private int pageCount;
        private int total;
        private boolean hasNext;
        private String filter = "";
        private UiCatalogGrid.Scope scope = UiCatalogGrid.Scope.BOTH;

        private Object language;
        private Object resources;
        private String mappedFilter = "";
        private int mappedPage = -1;
        private UiCatalogGrid.Scope mappedScope = UiCatalogGrid.Scope.BOTH;
        private List<UiCatalogGrid.Entry> full = List.of();
        private CatalogDisplayQuery.Pages<List<UiCatalogGrid.Entry>> queryPages;
        private final Map<String, TagPreviewIcons.Tag> tagPreviews = new HashMap<>();

        private List<UiCatalogGrid.Entry> queryEntries() {
            Object nextResources = PreviewResources.generation();
            Object nextLanguage = Language.getInstance();
            if (resources != null && (resources != nextResources || language != nextLanguage)) {
                cached = null;
                mappedPage = -1;
            }
            resources = nextResources; language = nextLanguage;
            RuleCatalog catalog = workspace.directory(type).value();
            RuleCatalog tags = includeTags ? workspace.directory(RuleCatalogType.TAG).value() : null;
            boolean changed = catalog != cached || tags != cachedTags;
            // 范围变化同样要让映射结果失效，否则工具栏的范围按钮不会生效
            if (changed || mappedScope != scope) {
                tagPreviews.clear();
                cached = catalog;
                cachedTags = tags;
                mappedScope = scope;
                mappedPage = -1;
                if (catalog == null) {
                    entries = List.of(); full = List.of(); queryPages = null;
                    hasNext = false; pageCount = 0; total = 0;
                    contentGeneration++;
                    return entries;
                }
                List<UiCatalogGrid.Entry> mapped = new ArrayList<>();
                boolean withItems = scope != UiCatalogGrid.Scope.TAG;
                boolean withTags = includeTags && scope != UiCatalogGrid.Scope.ITEM;
                Set<String> validTags = field != null && type == RuleCatalogType.TAG ? FieldCatalogChoices.tags(field) : null;
                for (RuleCatalogEntry entry : withItems ? catalog.entries() : List.<RuleCatalogEntry>of()) {
                    TaggedId reference = Objects.requireNonNull(TaggedId.tryParse(entry.id()));
                    if (policy.hidden(reference)) continue;
                    if (validTags != null && !validTags.contains(entry.id())) continue;
                    if (entryFilter != null && !entryFilter.test(entry.id())) continue;
                    if (type == RuleCatalogType.ENTITY && field != null
                            && field.selectionIntent() == SelectionIntent.ENTITY_TYPES_EXCLUDING_PRODUCTS
                            && (entry.id().equals("minecraft:item") || entry.id().equals("minecraft:experience_orb"))) continue;
                    if (type == RuleCatalogType.TAG) {
                        RuleCatalogType memberType = field == null ? null : FieldCatalogTypes.catalogTypeOf(field);
                        if (memberType == null) continue;
                        var tag = TagPreviewIcons.resolve(memberType, reference);
                        tagPreviews.put(entry.id(), tag);
                        mapped.add(new UiCatalogGrid.Entry(entry.id(), reference, tag.label(), null, tag.icon()));
                    } else {
                        String sub = entry.subLabel();
                        mapped.add(new UiCatalogGrid.Entry(entry.id(), reference, CatalogTypePresentation.of(type).label(reference, entry.label()),
                                sub == null || sub.isBlank() ? null : Component.literal(sub), CatalogPreviewIcons.iconFor(type, reference)));
                    }
                }
                if (withTags && tags != null && field != null) {
                    Set<String> allowed = FieldCatalogChoices.tags(field);
                    for (RuleCatalogEntry entry : tags.entries()) {
                        if (!allowed.contains(entry.id())) continue;
                        TaggedId reference = Objects.requireNonNull(TaggedId.tryParse(entry.id()));
                        var tag = TagPreviewIcons.resolve(type, reference);
                        tagPreviews.put(entry.id(), tag);
                        mapped.add(new UiCatalogGrid.Entry(entry.id(), reference, tag.label(), null, tag.icon()));
                    }
                }
                full = List.copyOf(mapped);
                queryPages = null;
            }
            if (mappedPage != page || !mappedFilter.equals(filter)) {
                if (queryPages == null || !mappedFilter.equals(filter)) {
                    CatalogDisplayQuery.Search query = CatalogDisplayQuery.search(filter, includeTags);
                    queryPages = CatalogDisplayQuery.flat(full,
                            entry -> query.matches(entry.id(), entry.reference().tag(), entry.label(), entry.subLabel()));
                }
                CatalogDisplayQuery.Page<List<UiCatalogGrid.Entry>> result = queryPages.page(page);
                total = result.total();
                pageCount = result.count();
                page = result.index();
                entries = result.content();
                hasNext = result.hasNext();
                mappedPage = page; mappedFilter = filter;
                contentGeneration++;
            }
            return entries;
        }
    }

    // 气候模式且六维全部无约束：本地拦截，不放进待提交状态（服务端 BiomeCondition 同样会拒绝）
    public static boolean climateAllUnbounded(@Nullable RuleValue rule) {
        if (rule == null || !rule.has(RuleFields.CONDITIONS)) {
            return false;
        }
        return containsUnboundedClimate(rule.child(RuleFields.CONDITIONS));
    }

    private static final String[] CLIMATE_FIELDS = {"temperature", "humidity", "continentalness", "erosion", "depth",
            "weirdness"};

    private static boolean containsUnboundedClimate(@Nullable RuleValue element) {
        if (element == null) {
            return false;
        }
        if (element.isMapping()) {
            if (isClimateCondition(element) && !hasBoundedClimate(element)) {
                return true;
            }
            for (String key : element.keys()) {
                if (containsUnboundedClimate(element.child(key))) {
                    return true;
                }
            }
            return false;
        }
        if (element.isList()) {
            for (RuleValue child : element.items()) {
                if (containsUnboundedClimate(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isClimateCondition(RuleValue body) {
        String type = RuleValue.typeOf(body);
        RuleValue mode = body.child("mode");
        return BiomeCondition.ID.toString().equals(type)
                && mode != null && mode.isText() && "climate".equals(mode.asText());
    }

    // 至少一端有界才算有效约束，避免 "temperature": {} 被当作已填写
    private static boolean hasBoundedClimate(RuleValue body) {
        for (String name : CLIMATE_FIELDS) {
            RuleValue range = body.child(name);
            if (range == null || !range.isMapping()) {
                continue;
            }
            if (isNumber(range.child("min")) || isNumber(range.child("max"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNumber(@Nullable RuleValue element) {
        return element != null && element.isNumber();
    }

    // ---- 叶子编辑面板 ----

    /**
     * 组装叶子条件编辑的附加控件。
     *
     * @param session    当前编辑会话（读写草稿的唯一入口）
     * @param fields     描述符字段
     * @param basePath   叶子参数在草稿中的路径（FormView 的 basePath）
     * @param applyChange 提交一次变更（内部应走 EditSession.apply，形成一条撤销记录）
     * @param onChange   变更后刷新视图（表单重载/宿主刷新）
     */
    static LeafPanel leafPanel(Font font, FormView form, EditSession session, List<EditorField> fields,
                               FieldPath basePath, Consumer<Consumer<RuleDraft>> applyChange, Runnable onChange) {
        List<UiWidget> extras = new ArrayList<>();
        List<UiButton> buttons = new ArrayList<>();
        List<Runnable> reloadValues = new ArrayList<>();
        // 参数框按卡片密度排：数量行因此让出标签列，滑条与其它字段的控件同列
        form.useCardSpacing();
        // 自带文字的行内控件不显示行标签：同一文案只出现一次，省下的宽度交给控件自身
        for (var handle : form.fields()) {
            if (handle.descriptor().hidesRowLabel()) {
                handle.hideLabel();
            }
        }
        EditorField from = field(fields, "from");
        EditorField to = field(fields, "to");
        if (from != null && to != null && from.isIntegerLike() && to.isIntegerLike()
                && from.intMin(0) == TIME_MIN && from.intMax(TIME_MAX) == TIME_MAX) {
            UiCyclicTimeBar bar = timeBar(form.theme(), font, session, from, to, basePath, applyChange, onChange);
            extras.add(bar);
            reloadValues.add(() -> bar.setRange(intAt(session, join(basePath, from.name()), TIME_MIN),
                    intAt(session, join(basePath, to.name()), DEFAULT_TIME_RANGE_END)));
        }
        EditorField min = field(fields, RANGE_MIN_FIELD);
        EditorField max = field(fields, RANGE_MAX_FIELD);
        RuleValue condition = session.valueAt(basePath);
        boolean heightCondition = condition != null && Y_LEVEL_ID.toString()
                .equals(condition.text(RuleFields.TYPE, ""));
        if (min != null && max != null && min.isIntegerLike() && max.isIntegerLike()) {
            UiRangeBar bar = intRangeBar(form.theme(), font, session, min, max, basePath, applyChange, onChange);
            if (heightCondition) {
                form.field(min.name()).setVisible(false);
                form.field(max.name()).setVisible(false);
                bar.setLabel(TypeLabels.conditionLabel(Y_LEVEL_ID));
            }
            extras.add(bar);
            reloadValues.add(() -> bar.core().setValue(integerRange(session, min, max, basePath)));
        }
        LeafPanel panel = new LeafPanel(font, form, extras, buttons, reloadValues);
        int extraIndex = 0;
        if (from != null && to != null && from.isIntegerLike() && to.isIntegerLike()
                && from.intMin(0) == TIME_MIN && from.intMax(TIME_MAX) == TIME_MAX) {
            UiCyclicTimeBar bar = (UiCyclicTimeBar) extras.get(extraIndex++);
            bar.setPreciseEditor(end -> panel.focusField(join(basePath,
                    end == UiRangeEnd.LOW ? from.name() : to.name())));
        }
        if (!heightCondition && min != null && max != null && min.isIntegerLike() && max.isIntegerLike()) {
            UiRangeBar bar = (UiRangeBar) extras.get(extraIndex);
            bar.setPreciseEditor(end -> panel.focusField(join(basePath,
                    end == UiRangeEnd.LOW ? min.name() : max.name())));
        }
        return panel;
    }

    private static @Nullable EditorField field(List<EditorField> fields, String name) {
        for (EditorField candidate : fields) {
            if (candidate.name().equals(name)) {
                return candidate;
            }
        }
        return null;
    }

    // 参数表单是否承载某个参数字段路径：判定只读描述符与现役绑定，不建面板、不写草稿。
    // 描述符字段本身精确承载；气候区间字段额外承载其 min/max 端点；物品数量字段额外承载现役引用条目。
    // 更深后缀、不存在的子项与已失效引用一律不承载，避免宽前缀把失效位置当成有效位置；
    // 判定不看草稿值，因此有合法缺省值的字段（如催化剂门槛省略）仍算承载。
    static boolean carriesField(List<EditorField> fields, FieldPath conditionPath, FieldPath fieldPath,
                                Function<EditorField, List<String>> itemReferences) {
        if (fieldPath == null || fieldPath.isRoot()) {
            return false;
        }
        for (EditorField field : fields) {
            FieldPath fieldAt = join(conditionPath, field.name());
            if (fieldPath.equals(fieldAt)) {
                return true;
            }
            if (field.isClimateRange()
                    && (fieldPath.equals(join(fieldAt, RANGE_MIN_FIELD))
                    || fieldPath.equals(join(fieldAt, RANGE_MAX_FIELD)))) {
                return true;
            }
            if (field.isItemCounts()) {
                for (String reference : itemReferences.apply(field)) {
                    if (fieldPath.equals(join(fieldAt, reference))) {
                        return true;
                    }
                }
            }
            if (field.isObject() && carriesObjectField(field, fieldAt, fieldPath)) {
                return true;
            }
        }
        return false;
    }

    // 嵌套对象的内部字段路径也由所在表单承载，避免问题定位落到面板之外
    private static boolean carriesObjectField(EditorField field, FieldPath fieldAt, FieldPath fieldPath) {
        for (EditorField child : field.subFields()) {
            FieldPath childAt = join(fieldAt, child.name());
            if (fieldPath.equals(childAt)) {
                return true;
            }
            if (child.isObject() && carriesObjectField(child, childAt, fieldPath)) {
                return true;
            }
        }
        return false;
    }

    private static FieldPath join(FieldPath basePath, String name) {
        return basePath.child(name);
    }

    private static UiRangeBar intRangeBar(UiHostTheme THEME, Font font, EditSession session, EditorField minField, EditorField maxField,
                                          FieldPath basePath, Consumer<Consumer<RuleDraft>> applyChange, Runnable onChange) {
        FieldPath lowPath = join(basePath, minField.name());
        FieldPath highPath = join(basePath, maxField.name());
        UiRangeBar bar = new UiRangeBar(THEME, font,
                UiNumberPolicy.integer(minField.intMin(FALLBACK_RANGE_MIN), maxField.intMax(FALLBACK_RANGE_MAX), 1, 1),
                integerRange(session, minField, maxField, basePath), rangeTexts(), value -> {
                    applyChange.accept( edits -> {
                        writeNumber(edits, lowPath, value.lowPresent() ? value.low() : null);
                        writeNumber(edits, highPath, value.highPresent() ? value.high() : null);
                    });
                    onChange.run();
                });
        bar.setLabel(Component.translatable(minField.labelKey()));
        bar.core().setWindow(new UiSliderWindow(minField.sliderMin(minField.intMin(FALLBACK_RANGE_MIN)),
                maxField.sliderMax(maxField.intMax(FALLBACK_RANGE_MAX))));
        return bar;
    }

    // 昼夜时间条：0..23999 刻，from>to 跨零点，同刻单点
    private static UiCyclicTimeBar timeBar(UiHostTheme THEME, Font font, EditSession session, EditorField from, EditorField to,
                                           FieldPath basePath, Consumer<Consumer<RuleDraft>> applyChange, Runnable onChange) {
        FieldPath fromPath = join(basePath, from.name());
        FieldPath toPath = join(basePath, to.name());
        UiCyclicTimeBar bar = dailyBar(THEME, font, (fromTick, toTick) -> {
            applyChange.accept( edits -> {
                edits.setAt(fromPath, RuleValue.integer((int) Math.round(fromTick)));
                edits.setAt(toPath, RuleValue.integer((int) Math.round(toTick)));
            });
            onChange.run();
        });
        bar.setRange(intAt(session, fromPath, TIME_MIN), intAt(session, toPath, DEFAULT_TIME_RANGE_END));
        bar.setLabel(Component.translatable(from.labelKey()));
        return bar;
    }

    private static int intAt(EditSession session, FieldPath path, int fallback) {
        RuleValue value = session.valueAt(path);
        return value != null && value.isNumber() ? value.asInteger(fallback) : fallback;
    }

    // 缺失端以滑条窗口边界显示，草稿仍保留缺失状态；首次拖动后才写入区间。
    private static UiRangeValue integerRange(EditSession session, EditorField min, EditorField max, FieldPath basePath) {
        double low = intAt(session, join(basePath, min.name()), (int) min.sliderMin(min.intMin(FALLBACK_RANGE_MIN)));
        double high = intAt(session, join(basePath, max.name()), (int) max.sliderMax(max.intMax(FALLBACK_RANGE_MAX)));
        // 单端合法值可能超出常用窗口；缺失端的预览须包含它，不改变草稿中的无限端。
        if (session.valueAt(join(basePath, min.name())) == null) low = Math.min(low, high);
        if (session.valueAt(join(basePath, max.name())) == null) high = Math.max(low, high);
        // 倒置值由精确输入校验，不能让预览区间的构造异常阻断整个面板。
        return low <= high ? UiRangeValue.of(low, high)
                : UiRangeValue.of(min.intMin(FALLBACK_RANGE_MIN), max.intMax(FALLBACK_RANGE_MAX));
    }

    private static void writeNumber(RuleDraft draft, FieldPath path, @Nullable Double value) {
        if (value == null) {
            draft.removeAt(path);
        } else {
            draft.setAt(path, RuleValue.decimal(value));
        }
    }

}
