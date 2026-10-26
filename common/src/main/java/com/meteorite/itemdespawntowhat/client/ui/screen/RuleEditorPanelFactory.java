package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.DraftPaths;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorWorkspaceView;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiCyclicRange;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiNumberPolicy;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRangeEnd;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRangeValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderWindow;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.LeafPanel;
import com.meteorite.itemdespawntowhat.client.ui.shared.BlockPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.CatalogPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.EntityPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.FieldCatalogChoices;
import com.meteorite.itemdespawntowhat.client.ui.shared.FieldCatalogTypes;
import com.meteorite.itemdespawntowhat.client.ui.shared.FluidPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.TagPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCatalogGrid;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCyclicTimeBar;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiRangeBar;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalog;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogEntry;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
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
 * 写入一律经调用方给定的 {@code applyChange}（内部走 {@link EditSession#apply(String, Runnable)}，
 * 一次完整操作 = 一条撤销记录）。
 * <p>目录条目、图标与文本由宿主解析：{@link #catalogLabel(String)} 判定翻译键，
 * 图标分发见 {@link com.meteorite.itemdespawntowhat.client.ui.shared.CatalogPreviewIcons#iconFor(RuleCatalogType, String)}，
 * 其余目录类型回落文字槽。
 */
public final class RuleEditorPanelFactory {

    // 宿主默认主题：屏幕从主题入口取样式后注入本类装配的控件
    private static final UiHostTheme THEME = UiHostThemes.defaultTheme();

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
    // 弹窗除内容区外的固定高度：标题栏 + 三处内边距 + 一行按钮
    private static final int MODAL_CHROME_HEIGHT = THEME.headerHeight() + THEME.padding() * 3
            + UiModal.BUTTON_HEIGHT;

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

    // 全天 / 白天 / 夜间预设；点击即一次提交
    public static UiCyclicTimeBar dailyBar(Font font, UiCyclicTimeBar.Listener listener) {
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
        return Component.translatable(UI + switch (type) {
            case ITEM -> "pick.scope_item_label";
            case BLOCK -> "pick.scope_block_label";
            case FLUID -> "pick.scope_fluid_label";
            case ENTITY -> "pick.scope_entity_label";
            case BIOME -> "pick.scope_biome_label";
            case LOOT_TABLE -> "pick.scope_loot_table_label";
            case DIMENSION -> "pick.scope_dimension_label";
            case MOB_EFFECT -> "pick.scope_mob_effect_label";
            case TAG -> "pick.scope_tag_label";
        });
    }

    // 目录标签可能是翻译键或原文：无空格、无冒号且含点号时按翻译键解析
    public static Component catalogLabel(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return Component.empty();
        }
        boolean looksLikeKey = raw.indexOf(' ') < 0 && raw.indexOf(':') < 0 && raw.indexOf('.') >= 0;
        return looksLikeKey ? Component.translatable(raw) : Component.literal(raw);
    }

    // 目录面板弹窗：数据与请求全部走既有工作区链路，翻页/搜索只发意图
    // 字段路径来自实际发起选择的表单行，子表单和条件叶都以同一入口解析互斥范围。
    static UiModal fieldCatalogModal(Font font, EditorShell shell, EditorWorkspaceView workspace, RuleCatalogType type,
                                     boolean multiSelect, Component title, int screenWidth, int screenHeight,
                                     Consumer<List<String>> onPicked, EditorField field, RuleDraft draft,
                                     String path, List<String> selected) {
        return fieldCatalogModal(font, shell, workspace, type, multiSelect, title, screenWidth, screenHeight,
                onPicked, field, draft, path, selected, null);
    }

    /**
     * 打开字段目录，并只列出条目过滤器接受的资源引用。
     * @param entryFilter 条目过滤器，null 表示不限；只作用于资源条目，标签条目不受限制
     */
    static UiModal fieldCatalogModal(Font font, EditorShell shell, EditorWorkspaceView workspace, RuleCatalogType type,
                                     boolean multiSelect, Component title, int screenWidth, int screenHeight,
                                     Consumer<List<String>> onPicked, EditorField field, RuleDraft draft,
                                     String path, List<String> selected, @Nullable Predicate<String> entryFilter) {
        return catalogModal(font, shell, workspace, type, multiSelect, title, screenWidth, screenHeight,
                onPicked, field, selected, SourceCatalystSelectionPolicy.forField(draft, path), entryFilter);
    }

    private static UiModal catalogModal(Font font, EditorShell shell, EditorWorkspaceView workspace, RuleCatalogType type,
                                        boolean multiSelect, Component title, int screenWidth, int screenHeight,
                                        Consumer<List<String>> onPicked, @Nullable EditorField field,
                                        List<String> selected, Function<String, @Nullable Component> unavailableReason,
                                        @Nullable Predicate<String> entryFilter) {
        boolean includeTags = field != null && field.allowsTags();
        workspace.refreshDirectory(type);
        if (includeTags) workspace.refreshDirectory(RuleCatalogType.TAG);
        // 默认排除规则与「主分类 / 标签 / 一起」范围由同一个策略实例持有，页码与总数始终一致
        CatalogEntryPolicy policy = includeTags ? CatalogEntryPolicy.scoped(type) : CatalogEntryPolicy.fixed(type);
        CatalogMapper mapper = new CatalogMapper();
        mapper.field = field;
        mapper.includeTags = includeTags;
        mapper.policy = policy;
        mapper.entryFilter = entryFilter;
        UiModal modal = UiModal.create(THEME, font);
        // 确认按钮可用态由目录每帧刷新：值不变时不再重配按钮（重配会复制文本并重建命中目标）
        ConfirmGate confirmGate = new ConfirmGate();
        UiCatalogGrid grid = new UiCatalogGrid(THEME, font, multiSelect, catalogTexts(type, includeTags), new UiCatalogGrid.Listener() {
            @Override
            public void onSelectionChanged(List<String> selection) {
                refreshCatalogConfirm(modal, shell.editable(), selection, unavailableReason, confirmGate);
            }

            @Override
            public void onSearch(String filter) {
                mapper.page = 0;
                mapper.filter = filter == null ? "" : filter;
                
            }

            @Override
            public void onPageChange(int delta) {
                if (delta > 0 && !mapper.hasNext) {
                    return;
                }
                if (delta < 0 && mapper.page <= 0) {
                    return;
                }
                mapper.page = Math.max(0, mapper.page + delta);
                
            }
        });
        grid.setEntryPolicy(policy);
        grid.setSelection(selected);
        grid.setUnavailableReason(unavailableReason);
        grid.setEntrySource(() -> {
            boolean active = shell.editable();
            refreshCatalogConfirm(modal, active, grid.selection(), unavailableReason, confirmGate);
            if (active != mapper.active) {
                mapper.active = active;
                grid.setEnabled(active);
            }
            List<UiCatalogGrid.Entry> mapped = mapper.map(workspace, type);
            grid.setPage(mapper.page, mapper.pageCount);
            grid.setPageLimits(mapper.page > 0, mapper.hasNext);
            // 总数与语言不变时复用同一份状态文案；未知状态的加载/失败提示只在真值变化时下发
            Object language = Language.getInstance();
            if (mapper.notedTotal != mapper.total || mapper.notedLanguage != language) {
                mapper.notedTotal = mapper.total;
                mapper.notedLanguage = language;
                grid.setStatusNote(Component.translatable(UI + "pick.total", mapper.total));
            }
            grid.setLoading(mapper.cached == null || !mapper.cached.lastPage()
                    || includeTags && (mapper.cachedTags == null || !mapper.cachedTags.lastPage()));
            String errorState = workspace.directoryFailed(type)
                    || includeTags && workspace.directoryFailed(RuleCatalogType.TAG) ? "retry" : active ? "" : "frozen";
            if (!errorState.equals(mapper.errorState)) {
                mapper.errorState = errorState;
                grid.setError(switch (errorState) {
                    case "retry" -> Component.translatable(UI + "pick.retry_hint");
                    case "frozen" -> Component.translatable(UI + "pick.frozen");
                    default -> null;
                });
            }
            return mapped;
        });
        grid.setOnRetry(() -> {
            workspace.retryDirectory(type);
            if (includeTags) workspace.retryDirectory(RuleCatalogType.TAG);
        });
        grid.setTooltipSuffix(id -> {
            var tag = mapper.tagPreviews.get(id);
            if (tag != null) return tag.tooltip();
            // 给不出静态预览的条目在悬停里说明原因：实体没有可见渲染器、方块没有静态模型（告示牌等）
            if (type == RuleCatalogType.ENTITY && EntityPreviewIcons.unavailable(id, 0)) {
                return Component.translatable(UI + "preview.unavailable");
            }
            if (type == RuleCatalogType.BLOCK && BlockPreviewIcons.unavailable(id)) {
                return Component.translatable(UI + "preview.unavailable");
            }
            return null;
        });
        if (!shell.editable()) {
            grid.setEnabled(false);
            grid.setError(Component.translatable(UI + "pick.frozen"));
        }
        modal.title(title);
        modal.preferredWidth(pickerWidth(screenWidth));
        // 翻页与状态排到确认/取消同一行的左侧，省下独立一行的底部空间
        modal.inlineFooter();
        modal.contentWidget(grid, pickerBodyHeight(screenHeight));
        modal.confirm(Component.translatable(UI + "button.confirm"), () -> {
            List<String> selection = grid.selection();
            if (shell.editable() && selection.stream().noneMatch(id -> unavailableReason.apply(id) != null))
                onPicked.accept(selection);
        });
        refreshCatalogConfirm(modal, shell.editable(), grid.selection(), unavailableReason, confirmGate);
        modal.cancel(Component.translatable(UI + "button.cancel"));
        modal.layoutCentered(screenWidth, screenHeight);
        return modal;
    }

    // 确认按钮可用态缓存：目录每帧都刷新它，但按钮重配一次会复制文本、重算宽度并重建命中目标
    private static final class ConfirmGate {
        private @Nullable Boolean enabled;
    }

    private static void refreshCatalogConfirm(UiModal modal, boolean active, List<String> selection,
                                               Function<String, @Nullable Component> unavailableReason,
                                               ConfirmGate gate) {
        // 选择与宿主业务状态每帧都可能变，因此每次仍要算出结果，只在结果变化时才推给按钮
        boolean enabled = active && selection.stream().noneMatch(id -> unavailableReason.apply(id) != null);
        if (gate.enabled != null && gate.enabled == enabled) {
            return;
        }
        gate.enabled = enabled;
        if (!modal.buttons().isEmpty()) {
            modal.buttons().getFirst().setEnabled(enabled);
        }
    }

    // 选择页宽度：屏幕越宽弹窗越宽，但保留窗口感
    public static int pickerWidth(int screenWidth) {
        return Math.clamp(screenWidth - PICKER_MARGIN, PICKER_MIN_WIDTH, PICKER_MAX_WIDTH);
    }

    // 选择页内容区高度：扣掉弹窗固定部分，剩下的都留给列表
    public static int pickerBodyHeight(int screenHeight) {
        return Math.clamp(screenHeight - MODAL_CHROME_HEIGHT - PICKER_MARGIN, PICKER_MIN_BODY_HEIGHT,
                PICKER_MAX_BODY_HEIGHT);
    }

    // 目录缓存 -> 面板条目；同一份缓存实例只转换一次，避免逐帧重建
    private static final class CatalogMapper {

        private RuleCatalog cached;
        private RuleCatalog cachedTags;
        private boolean includeTags;
        private @Nullable EditorField field;
        private @Nullable CatalogEntryPolicy policy;
        // 条目可见范围：只作用于资源条目，标签条目不受限制
        private @Nullable Predicate<String> entryFilter;
        private @Nullable List<UiCatalogGrid.Entry> entries;
        private boolean active = true;
        private int page;
        private int pageCount;
        private int total;
        private boolean hasNext;
        private String filter = "";

        private Object language;
        private Object resources;
        // 状态文案与失败提示的已下发值：目录刷新按帧执行，值未变时不重复构造 Component
        private int notedTotal = -1;
        private Object notedLanguage;
        private String errorState = "";
        private String mappedFilter = "";
        private int mappedPage = -1;
        private UiCatalogGrid.Scope mappedScope = UiCatalogGrid.Scope.BOTH;
        private List<UiCatalogGrid.Entry> full = List.of();
        private final Map<String, TagPreviewIcons.Tag> tagPreviews = new HashMap<>();

        @Nullable
        List<UiCatalogGrid.Entry> map(EditorWorkspaceView workspace, RuleCatalogType type) {
            Object nextResources = EntityPreviewIcons.generation();
            Object nextLanguage = Language.getInstance();
            if (resources != null && (resources != nextResources || language != nextLanguage)) {
                workspace.invalidateDirectories(); cached = null;
            }
            resources = nextResources; language = nextLanguage;
            RuleCatalog catalog = workspace.directory(type);
            RuleCatalog tags = includeTags ? workspace.directory(RuleCatalogType.TAG) : null;
            boolean changed = catalog != cached || tags != cachedTags;
            // 范围变化同样要让映射结果失效，否则工具栏的范围按钮不会生效
            UiCatalogGrid.Scope scope = policy == null ? UiCatalogGrid.Scope.BOTH : policy.scope();
            if (changed || mappedScope != scope) {
                tagPreviews.clear();
                cached = catalog;
                cachedTags = tags;
                mappedScope = scope;
                mappedPage = -1;
                if (catalog == null) { entries = null; hasNext = false; pageCount = 0; total = 0; return null; }
                List<UiCatalogGrid.Entry> mapped = new ArrayList<>();
                boolean withItems = scope != UiCatalogGrid.Scope.TAG;
                boolean withTags = includeTags && scope != UiCatalogGrid.Scope.ITEM;
                Set<String> validTags = field != null && type == RuleCatalogType.TAG ? FieldCatalogChoices.tags(field) : null;
                for (RuleCatalogEntry entry : withItems ? catalog.entries() : List.<RuleCatalogEntry>of()) {
                    if (policy != null && policy.hidden(entry.id())) continue;
                    if (validTags != null && !validTags.contains(entry.id())) continue;
                    if (entryFilter != null && !entryFilter.test(entry.id())) continue;
                    if (type == RuleCatalogType.ENTITY && field != null && FieldCatalogChoices.isGenericProduct(field)
                            && (entry.id().equals("minecraft:item") || entry.id().equals("minecraft:experience_orb"))) continue;
                    String sub = validTags == null ? entry.subLabel() : Component.translatable(field.labelKey()).getString();
                    if (type == RuleCatalogType.TAG) {
                        RuleCatalogType memberType = field == null ? RuleCatalogType.ITEM : FieldCatalogTypes.catalogTypeOf(field);
                        var tag = TagPreviewIcons.resolve(memberType == null ? RuleCatalogType.ITEM : memberType, entry.id());
                        tagPreviews.put(entry.id(), tag);
                        mapped.add(new UiCatalogGrid.Entry(entry.id(), tag.label(), null, tag.icon()));
                    } else {
                        ResourceLocation id = ResourceLocation.tryParse(entry.id());
                        Component fluidLabel = type == RuleCatalogType.FLUID && id != null ? FluidPreviewIcons.label(id) : null;
                        mapped.add(new UiCatalogGrid.Entry(entry.id(), fluidLabel == null ? catalogLabel(entry.label()) : fluidLabel,
                                sub == null || sub.isBlank() ? null : catalogLabel(sub), CatalogPreviewIcons.iconFor(type, entry.id())));
                    }
                }
                if (withTags && tags != null && field != null) {
                    Set<String> allowed = FieldCatalogChoices.tags(field);
                    for (RuleCatalogEntry entry : tags.entries()) {
                        if (!allowed.contains(entry.id())) continue;
                        var tag = TagPreviewIcons.resolve(type, entry.id());
                        tagPreviews.put(entry.id(), tag);
                        mapped.add(new UiCatalogGrid.Entry(entry.id(), tag.label(), null, tag.icon()));
                    }
                }
                full = List.copyOf(mapped);
            }
            if (mappedPage != page || !mappedFilter.equals(filter)) {
                String query = filter.toLowerCase(Locale.ROOT).trim();
                boolean tagsOnly = includeTags && query.startsWith("#");
                String needle = tagsOnly ? query.substring(1) : query;
                List<UiCatalogGrid.Entry> filtered = full.stream().filter(entry -> !tagsOnly || entry.id().startsWith("#"))
                        .filter(entry -> entry.id().toLowerCase(Locale.ROOT).contains(needle)
                        || entry.label().getString().toLowerCase(Locale.ROOT).contains(needle)
                        || entry.subLabel() != null && entry.subLabel().getString().toLowerCase(Locale.ROOT).contains(needle)).toList();
                total = filtered.size();
                pageCount = Math.max(1, (total + UiCatalogGrid.PAGE_SIZE - 1) / UiCatalogGrid.PAGE_SIZE);
                page = Math.clamp(page, 0, pageCount - 1);
                int from = page * UiCatalogGrid.PAGE_SIZE;
                entries = List.copyOf(filtered.subList(from, Math.min(filtered.size(), from + UiCatalogGrid.PAGE_SIZE)));
                hasNext = page + 1 < pageCount;
                mappedPage = page; mappedFilter = filter;
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
        RuleValue type = body.child(RuleFields.TYPE);
        RuleValue mode = body.child("mode");
        return type != null && type.isText() && type.asText().endsWith("biome")
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
     * @param pushModal  推入目录弹窗
     */
    public static LeafPanel leafPanel(Font font, FormView form, EditSession session, List<EditorField> fields,
                                      String basePath, Consumer<Runnable> applyChange, Runnable onChange,
                                      Consumer<UiModal> pushModal, EditorShell shell, EditorWorkspaceView workspace, int screenWidth,
                                      int screenHeight) {
        List<UiWidget> extras = new ArrayList<>();
        List<UiButton> buttons = new ArrayList<>();
        List<Runnable> reloadValues = new ArrayList<>();
        RuleDraft draft = session.draft();
        // 参数框按卡片密度排：数量行因此让出标签列，滑条与其它字段的控件同列
        form.useCardSpacing();
        // 自带文字的行内控件不显示行标签：同一文案只出现一次，省下的宽度交给控件自身
        for (EditorField field : fields) {
            if (field.hidesRowLabel()) {
                form.hideRowLabel(join(basePath, field.name()));
            }
        }
        EditorField from = field(fields, "from");
        EditorField to = field(fields, "to");
        if (from != null && to != null && from.isIntegerLike() && to.isIntegerLike()
                && from.intMin(0) == TIME_MIN && from.intMax(TIME_MAX) == TIME_MAX) {
            UiCyclicTimeBar bar = timeBar(font, draft, from, to, basePath, applyChange, onChange);
            extras.add(bar);
            reloadValues.add(() -> bar.setRange(intAt(draft, join(basePath, from.name()), TIME_MIN),
                    intAt(draft, join(basePath, to.name()), DEFAULT_TIME_RANGE_END)));
        }
        EditorField min = field(fields, RANGE_MIN_FIELD);
        EditorField max = field(fields, RANGE_MAX_FIELD);
        RuleValue condition = draft.valueAt(basePath);
        boolean heightCondition = condition != null && Y_LEVEL_ID.toString()
                .equals(condition.text(RuleFields.TYPE, ""));
        if (min != null && max != null && min.isIntegerLike() && max.isIntegerLike()) {
            UiRangeBar bar = intRangeBar(font, draft, min, max, basePath, applyChange, onChange);
            if (heightCondition) {
                form.setRowVisible(join(basePath, min.name()), false);
                form.setRowVisible(join(basePath, max.name()), false);
                bar.setLabel(TypeLabels.conditionLabel(Y_LEVEL_ID));
            }
            extras.add(bar);
            reloadValues.add(() -> bar.core().setValue(integerRange(draft, min, max, basePath)));
        }
        form.setSelectionCatalogOpener((field, path, tags, selected, picked) -> pushModal.accept(fieldCatalogModal(font, shell, workspace,
                tags ? RuleCatalogType.TAG : FieldCatalogTypes.catalogTypeOf(field), field.isListShaped(),
                Component.translatable(field.labelKey()), screenWidth, screenHeight, picked, field, draft, path, selected)));
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
    static boolean carriesField(List<EditorField> fields, String conditionPath, String fieldPath,
                                Function<EditorField, List<String>> itemReferences) {
        if (fieldPath == null || fieldPath.isBlank()) {
            return false;
        }
        for (EditorField field : fields) {
            String fieldAt = join(conditionPath, field.name());
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
        }
        return false;
    }

    // 基路径归一：空白基路径按无字段处理；本层保留 isBlank 口径
    // （DraftPaths.child 只判 isEmpty，两者仅对「全空白基路径」不同，此处不合并）
    private static String join(String basePath, String name) {
        return basePath == null || basePath.isBlank() ? name : DraftPaths.child(basePath, name);
    }

    // 高度/光照区间：两个可省略的整数端点
    private static UiRangeBar intRangeBar(Font font, RuleDraft draft, EditorField minField, EditorField maxField,
                                          String basePath, Consumer<Runnable> applyChange, Runnable onChange) {
        String lowPath = join(basePath, minField.name());
        String highPath = join(basePath, maxField.name());
        UiRangeBar bar = new UiRangeBar(THEME, font,
                UiNumberPolicy.integer(minField.intMin(FALLBACK_RANGE_MIN), maxField.intMax(FALLBACK_RANGE_MAX), 1, 1),
                integerRange(draft, minField, maxField, basePath), rangeTexts(), value -> {
                    applyChange.accept(() -> {
                        writeNumber(draft, lowPath, value.lowPresent() ? value.low() : null);
                        writeNumber(draft, highPath, value.highPresent() ? value.high() : null);
                    });
                    onChange.run();
                });
        bar.setLabel(Component.translatable(minField.labelKey()));
        bar.core().setWindow(new UiSliderWindow(minField.sliderMin(minField.intMin(FALLBACK_RANGE_MIN)),
                maxField.sliderMax(maxField.intMax(FALLBACK_RANGE_MAX))));
        return bar;
    }

    // 昼夜时间条：0..23999 刻，from>to 跨零点，同刻单点
    private static UiCyclicTimeBar timeBar(Font font, RuleDraft draft, EditorField from, EditorField to,
                                           String basePath, Consumer<Runnable> applyChange, Runnable onChange) {
        String fromPath = join(basePath, from.name());
        String toPath = join(basePath, to.name());
        UiCyclicTimeBar bar = dailyBar(font, (fromTick, toTick) -> {
            applyChange.accept(() -> {
                draft.setAt(fromPath, RuleValue.integer((int) Math.round(fromTick)));
                draft.setAt(toPath, RuleValue.integer((int) Math.round(toTick)));
            });
            onChange.run();
        });
        bar.setRange(intAt(draft, fromPath, TIME_MIN), intAt(draft, toPath, DEFAULT_TIME_RANGE_END));
        bar.setLabel(Component.translatable(from.labelKey()));
        return bar;
    }

    private static int intAt(RuleDraft draft, String path, int fallback) {
        RuleValue value = draft.valueAt(path);
        return value != null && value.isNumber() ? value.asInteger(fallback) : fallback;
    }

    // 缺失端以滑条窗口边界显示，草稿仍保留缺失状态；首次拖动后才写入区间。
    private static UiRangeValue integerRange(RuleDraft draft, EditorField min, EditorField max, String basePath) {
        double low = intAt(draft, join(basePath, min.name()), (int) min.sliderMin(min.intMin(FALLBACK_RANGE_MIN)));
        double high = intAt(draft, join(basePath, max.name()), (int) max.sliderMax(max.intMax(FALLBACK_RANGE_MAX)));
        // 单端合法值可能超出常用窗口；缺失端的预览须包含它，不改变草稿中的无限端。
        if (draft.valueAt(join(basePath, min.name())) == null) low = Math.min(low, high);
        if (draft.valueAt(join(basePath, max.name())) == null) high = Math.max(low, high);
        // 倒置值由精确输入校验，不能让预览区间的构造异常阻断整个面板。
        return low <= high ? UiRangeValue.of(low, high)
                : UiRangeValue.of(min.intMin(FALLBACK_RANGE_MIN), max.intMax(FALLBACK_RANGE_MAX));
    }

    private static void writeNumber(RuleDraft draft, String path, @Nullable Double value) {
        if (value == null) {
            draft.removeAt(path);
        } else {
            draft.setAt(path, RuleValue.decimal(value));
        }
    }

}
