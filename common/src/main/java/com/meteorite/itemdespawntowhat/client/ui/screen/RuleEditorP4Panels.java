package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorFieldType;
import com.meteorite.itemdespawntowhat.client.edit.EditorWorkspaceView;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleNaming;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiCyclicRange;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiNumberPolicy;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRangeEnd;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRangeValue;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.LeafPanel;
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
import com.meteorite.itemdespawntowhat.client.ui.widget.UiStructureDiagram;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalog;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogEntry;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * P4 宿主装配（主计划 §4/§7/§8）：目录图标面板接线、气候/昼夜/高度区间条、结构图解与叶子面板装配
 * （面板实现见 {@link LeafPanel}）。
 * <p>本类只做「把 kit 控件与目录链路接到规则草稿上」的装配，不复制数据层逻辑：
 * 写入一律经调用方给定的 {@code applyChange}（内部走 {@link EditSession#apply(String, Runnable)}，
 * 一次完整操作 = 一条撤销记录）。
 * <p>目录条目、图标与文本由宿主解析：{@link #catalogLabel(String)} 判定翻译键，
 * 图标分发见 {@link com.meteorite.itemdespawntowhat.client.ui.shared.CatalogPreviewIcons#iconFor(RuleCatalogType, String)}，
 * 其余目录类型回落文字槽。
 */
public final class RuleEditorP4Panels {

    // 宿主默认主题：屏幕从主题入口取样式后注入本类装配的控件
    private static final UiHostTheme THEME = UiHostThemes.defaultTheme();

    private static final String RANGE_MIN_FIELD = "min";
    private static final String RANGE_MAX_FIELD = "max";

    private static final String UI = TypeLabels.UI_PREFIX;
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

    private RuleEditorP4Panels() {
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
        bar.setFormatter(RuleEditorP4Panels::clockText);
        bar.setPresets(List.of(
                new UiCyclicTimeBar.Preset(Component.translatable(UI + "time.preset_all"), 0.0D, 23999.0D),
                new UiCyclicTimeBar.Preset(Component.translatable(UI + "time.preset_day"), 0.0D, 11000.0D),
                new UiCyclicTimeBar.Preset(Component.translatable(UI + "time.preset_night"), 12000.0D, 23000.0D),
                new UiCyclicTimeBar.Preset(Component.translatable(UI + "time.preset_noon"), 6000.0D, 6000.0D)));
        return bar;
    }

    // ---- 目录面板 ----

    public static UiCatalogGrid.Texts catalogTexts() {
        return catalogTexts(false);
    }

    private static UiCatalogGrid.Texts catalogTexts(boolean combined) {
        return new UiCatalogGrid.Texts(Component.translatable(UI + (combined ? "pick.search_combined" : "pick.search")),
                Component.translatable(UI + "pick.empty"), Component.translatable(UI + "pick.loading"),
                Component.translatable(UI + "pick.error"), Component.translatable(UI + "pick.prev"),
                Component.translatable(UI + "pick.next"), Component.translatable(UI + "pick.selected"),
                Component.translatable(UI + "pick.scope_item"), Component.translatable(UI + "pick.scope_tag"),
                Component.translatable(UI + "pick.scope_both"), Component.translatable(UI + "pick.clear"));
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
    public static UiModal catalogModal(Font font, EditorWorkspaceView workspace, RuleCatalogType type,
                                       boolean multiSelect, Component title, int screenWidth, int screenHeight,
                                       Consumer<List<String>> onPicked) {
        return catalogModal(font, workspace, type, multiSelect, title, screenWidth, screenHeight, onPicked, null);
    }

    // 带字段上下文的入口：标签归属和实体产出禁用类型由宿主静态检查。
    public static UiModal catalogModal(Font font, EditorWorkspaceView workspace, RuleCatalogType type,
                                       boolean multiSelect, Component title, int screenWidth, int screenHeight,
                                       Consumer<List<String>> onPicked, @Nullable EditorField field) {
        return catalogModal(font, workspace, type, multiSelect, title, screenWidth, screenHeight, onPicked, field, false);
    }

    // 注册表选择合并普通目录与同字段所属注册表的标签，沿用既有目录缓存和协议。
    public static UiModal catalogModal(Font font, EditorWorkspaceView workspace, RuleCatalogType type,
                                       boolean multiSelect, Component title, int screenWidth, int screenHeight,
                                       Consumer<List<String>> onPicked, @Nullable EditorField field, boolean combined) {
        boolean includeTags = combined && field != null && (field.type() == EditorFieldType.TAG || field.type() == EditorFieldType.TAG_LIST);
        workspace.refreshDirectory(type);
        if (includeTags) workspace.refreshDirectory(RuleCatalogType.TAG);
        // 默认排除规则与「物品 / 标签 / 一起」范围由同一个策略实例持有，页码与总数始终一致
        CatalogEntryPolicy policy = includeTags ? CatalogEntryPolicy.scoped(type) : CatalogEntryPolicy.fixed(type);
        CatalogMapper mapper = new CatalogMapper();
        mapper.field = field;
        mapper.includeTags = includeTags;
        mapper.policy = policy;
        UiCatalogGrid grid = new UiCatalogGrid(THEME, font, multiSelect, includeTags ? catalogTexts(true) : catalogTexts(), new UiCatalogGrid.Listener() {
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
        grid.setEntrySource(() -> {
            boolean active = workspace.active();
            if (active != mapper.active) {
                mapper.active = active;
                grid.setEnabled(active);
            }
            List<UiCatalogGrid.Entry> mapped = mapper.map(workspace, type);
            grid.setPage(mapper.page, mapper.pageCount);
            grid.setPageLimits(mapper.page > 0, mapper.hasNext);
            grid.setStatusNote(Component.translatable(UI + "pick.total", mapper.total));
            grid.setLoading(mapper.cached == null || !mapper.cached.lastPage()
                    || includeTags && (mapper.cachedTags == null || !mapper.cachedTags.lastPage()));
            grid.setError(workspace.directoryFailed(type) || includeTags && workspace.directoryFailed(RuleCatalogType.TAG)
                    ? Component.translatable(UI + "pick.retry_hint") : null);
            return mapped;
        });
        grid.setOnRetry(() -> {
            workspace.retryDirectory(type);
            if (includeTags) workspace.retryDirectory(RuleCatalogType.TAG);
        });
        grid.setTooltipSuffix(id -> {
            var tag = mapper.tagPreviews.get(id);
            if (tag != null) return tag.tooltip();
            return type == RuleCatalogType.ENTITY && EntityPreviewIcons.unavailable(id, 0)
                    ? Component.translatable(UI + "preview.unavailable") : null;
        });
        if (!workspace.active()) {
            grid.setEnabled(false);
            grid.setError(Component.translatable(UI + "pick.frozen"));
        }
        UiModal modal = UiModal.create(THEME, font);
        modal.title(title);
        modal.preferredWidth(pickerWidth(screenWidth));
        // 翻页与状态排到确认/取消同一行的左侧，省下独立一行的底部空间
        modal.inlineFooter();
        modal.contentWidget(grid, pickerBodyHeight(screenHeight));
        modal.confirm(Component.translatable(UI + "button.confirm"), () -> onPicked.accept(grid.selection()));
        modal.cancel(Component.translatable(UI + "button.cancel"));
        modal.layoutCentered(screenWidth, screenHeight);
        return modal;
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
        private @Nullable List<UiCatalogGrid.Entry> entries;
        private boolean active = true;
        private int page;
        private int pageCount;
        private int total;
        private boolean hasNext;
        private String filter = "";

        private Object language;
        private Object resources;
        private String mappedFilter = "";
        private int mappedPage = -1;
        private UiCatalogGrid.Scope mappedScope = UiCatalogGrid.Scope.BOTH;
        private List<UiCatalogGrid.Entry> full = List.of();
        private final java.util.Map<String, TagPreviewIcons.Tag> tagPreviews = new java.util.HashMap<>();

        @Nullable
        List<UiCatalogGrid.Entry> map(EditorWorkspaceView workspace, RuleCatalogType type) {
            Object nextResources = EntityPreviewIcons.generation();
            Object nextLanguage = net.minecraft.locale.Language.getInstance();
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
                java.util.Set<String> validTags = field != null && type == RuleCatalogType.TAG ? FieldCatalogChoices.tags(field) : null;
                for (RuleCatalogEntry entry : withItems ? catalog.entries() : List.<RuleCatalogEntry>of()) {
                    if (policy != null && policy.hidden(entry.id())) continue;
                    if (validTags != null && !validTags.contains(entry.id())) continue;
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
                    java.util.Set<String> allowed = FieldCatalogChoices.tags(field);
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

    // ---- 结构图解 ----

    // 槽位与连接：源物品 ->（催化剂）-> 候选 -> 效果；节点 id 由宿主映射到页面/候选/效果
    public static UiStructureDiagram structureDiagram(Font font, RuleValue rule, RuleNaming.NameSource source,
                                                      int candidateIndex, Consumer<String> navigate) {
        UiStructureDiagram diagram = new UiStructureDiagram(THEME, font, navigate::accept);
        diagram.setEmptyMessage(Component.translatable(UI + "diagram.empty"));
        diagram.setNodes(diagramNodes(rule, source, candidateIndex));
        diagram.setEdges(diagramEdges(rule));
        return diagram;
    }

    public static List<UiStructureDiagram.Node> diagramNodes(@Nullable RuleValue rule, RuleNaming.NameSource source,
                                                             int candidateIndex) {
        List<UiStructureDiagram.Node> nodes = new ArrayList<>();
        if (rule == null) {
            return nodes;
        }
        // sourceTitle 需要整条规则对象（内部按 rule.source.items 取值），不能传 source 子对象
        nodes.add(new UiStructureDiagram.Node("source", RuleNaming.sourceTitle(rule, source),
                Component.translatable(UI + "diagram.source")));
        boolean hasCatalyst = rule.has(RuleFields.CATALYST_COST);
        if (hasCatalyst) {
            nodes.add(new UiStructureDiagram.Node("catalyst",
                    Component.translatable(UI + "rule.catalyst_cost"), null));
        }
        int candidates = Math.max(1, ResultStructure.candidateCount(rule));
        for (int index = 0; index < candidates; index++) {
            RuleValue candidate = ResultStructure.candidateAt(rule, index);
            String listPath = ResultStructure.listPath(rule, index);
            int effects = ResultStructure.effectCount(rule, index);
            nodes.add(new UiStructureDiagram.Node("candidate:" + index,
                    RuleNaming.candidateTitle(candidate, index + 1, source),
                    Component.translatable(UI + "diagram.effects", effects)));
            for (int effect = 0; effect < effects; effect++) {
                nodes.add(new UiStructureDiagram.Node("effect:" + listPath + ":" + effect,
                        RuleNaming.effectTitle(effectAt(candidate, effect), source),
                        index == candidateIndex ? null
                                : Component.translatable(UI + "diagram.other_candidate", index + 1)));
            }
        }
        return nodes;
    }

    private static @Nullable RuleValue effectAt(@Nullable RuleValue candidate, int index) {
        if (candidate == null || !candidate.isMapping()) {
            return null;
        }
        RuleValue effects = candidate.child(RuleFields.CANDIDATE_EFFECTS);
        if (effects == null || !effects.isList() || index < 0 || index >= effects.size()) {
            return null;
        }
        RuleValue effect = effects.item(index);
        return effect != null && effect.isMapping() ? effect : null;
    }

    public static List<UiStructureDiagram.Edge> diagramEdges(@Nullable RuleValue rule) {
        List<UiStructureDiagram.Edge> edges = new ArrayList<>();
        if (rule == null) {
            return edges;
        }
        boolean hasCatalyst = rule.has(RuleFields.CATALYST_COST);
        if (hasCatalyst) {
            edges.add(new UiStructureDiagram.Edge("source", "catalyst"));
        }
        int candidates = Math.max(1, ResultStructure.candidateCount(rule));
        String previous = hasCatalyst ? "catalyst" : "source";
        for (int index = 0; index < candidates; index++) {
            String candidateId = "candidate:" + index;
            edges.add(new UiStructureDiagram.Edge(previous, candidateId));
            String listPath = ResultStructure.listPath(rule, index);
            int effects = ResultStructure.effectCount(rule, index);
            for (int effect = 0; effect < effects; effect++) {
                edges.add(new UiStructureDiagram.Edge(candidateId, "effect:" + listPath + ":" + effect));
            }
            previous = candidateId;
        }
        return edges;
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
                                      Consumer<UiModal> pushModal, EditorWorkspaceView workspace, int screenWidth,
                                      int screenHeight) {
        List<UiWidget> extras = new ArrayList<>();
        List<UiButton> buttons = new ArrayList<>();
        List<Runnable> reloadValues = new ArrayList<>();
        RuleDraft draft = session.draft();
        for (EditorField field : fields) {
            if (field.type() == EditorFieldType.CLIMATE_RANGE) {
                UiRangeBar bar = climateBar(font, draft, field, basePath, applyChange, onChange);
                extras.add(bar);
                reloadValues.add(() -> bar.core().setValue(readRange(draft, join(basePath, field.name()))));
            }
        }
        EditorField from = field(fields, "from");
        EditorField to = field(fields, "to");
        if (from != null && to != null && isIntegerLike(from) && isIntegerLike(to)
                && from.intMin(0) == TIME_MIN && from.intMax(TIME_MAX) == TIME_MAX) {
            UiCyclicTimeBar bar = timeBar(font, draft, from, to, basePath, applyChange, onChange);
            extras.add(bar);
            reloadValues.add(() -> bar.setRange(intAt(draft, join(basePath, from.name()), TIME_MIN),
                    intAt(draft, join(basePath, to.name()), DEFAULT_TIME_RANGE_END)));
        }
        EditorField min = field(fields, "min");
        EditorField max = field(fields, "max");
        if (min != null && max != null && isIntegerLike(min) && isIntegerLike(max)) {
            UiRangeBar bar = intRangeBar(font, draft, min, max, basePath, applyChange, onChange);
            extras.add(bar);
            reloadValues.add(() -> bar.core().setValue(readRange(draft, join(basePath, min.name()), join(basePath, max.name()))));
        }
        form.setCatalogOpener((field, tags, picked) -> pushModal.accept(catalogModal(font, workspace,
                tags ? RuleCatalogType.TAG : FieldCatalogTypes.catalogTypeOf(field), isListField(field),
                Component.translatable(field.labelKey()), screenWidth, screenHeight, picked, field, true)));
        LeafPanel panel = new LeafPanel(font, form, extras, buttons, reloadValues);
        int extraIndex = 0;
        for (EditorField field : fields) if (field.type() == EditorFieldType.CLIMATE_RANGE) {
            UiRangeBar bar = (UiRangeBar) extras.get(extraIndex++);
            bar.setPreciseEditor(end -> panel.focusField(join(join(basePath, field.name()),
                    end == UiRangeEnd.LOW ? RANGE_MIN_FIELD : RANGE_MAX_FIELD)));
        }
        if (from != null && to != null && isIntegerLike(from) && isIntegerLike(to)
                && from.intMin(0) == TIME_MIN && from.intMax(TIME_MAX) == TIME_MAX) {
            UiCyclicTimeBar bar = (UiCyclicTimeBar) extras.get(extraIndex++);
            bar.setPreciseEditor(end -> panel.focusField(join(basePath,
                    end == UiRangeEnd.LOW ? from.name() : to.name())));
        }
        if (min != null && max != null && isIntegerLike(min) && isIntegerLike(max)) {
            UiRangeBar bar = (UiRangeBar) extras.get(extraIndex);
            bar.setPreciseEditor(end -> panel.focusField(join(basePath,
                    end == UiRangeEnd.LOW ? min.name() : max.name())));
        }
        return panel;
    }

    private static boolean isIntegerLike(EditorField field) {
        return field.type() == EditorFieldType.INTEGER || field.type() == EditorFieldType.TICKS;
    }

    private static boolean isListField(EditorField field) {
        return switch (field.type()) {
            case TAG_LIST, RL_LIST, STRING_LIST -> true;
            default -> field.name().endsWith("s");
        };
    }

    private static @Nullable EditorField field(List<EditorField> fields, String name) {
        for (EditorField candidate : fields) {
            if (candidate.name().equals(name)) {
                return candidate;
            }
        }
        return null;
    }

    private static String join(String basePath, String name) {
        return basePath == null || basePath.isBlank() ? name : basePath + "." + name;
    }

    // 气候区间：字段自身是 {min,max} 对象，两端可省略；0.1 普通步长 / 0.01 Shift
    private static UiRangeBar climateBar(Font font, RuleDraft draft, EditorField field, String basePath,
                                         Consumer<Runnable> applyChange, Runnable onChange) {
        String path = join(basePath, field.name());
        UiRangeBar bar = new UiRangeBar(THEME, font,
                UiNumberPolicy.of(field.doubleMin(-1.0D), field.doubleMax(1.0D), 0.1D, 0.01D),
                readRange(draft, path), rangeTexts(), value -> {
                    applyChange.accept(() -> {
                        if (!value.lowPresent() && !value.highPresent()) {
                            // 两端都不约束：省略该维，不写空对象（fields §3）
                            draft.removeAt(path);
                            return;
                        }
                        RuleValue.Mapping body = RuleValue.mapping();
                        if (value.lowPresent()) {
                            body.put("min", RuleValue.decimal(value.low()));
                        }
                        if (value.highPresent()) {
                            body.put("max", RuleValue.decimal(value.high()));
                        }
                        draft.setAt(path, body.build());
                    });
                    onChange.run();
                });
        bar.setLabel(Component.translatable(field.labelKey()));
        return bar;
    }

    // 高度/光照区间：两个可省略的整数端点
    private static UiRangeBar intRangeBar(Font font, RuleDraft draft, EditorField minField, EditorField maxField,
                                          String basePath, Consumer<Runnable> applyChange, Runnable onChange) {
        String lowPath = join(basePath, minField.name());
        String highPath = join(basePath, maxField.name());
        UiRangeBar bar = new UiRangeBar(THEME, font,
                UiNumberPolicy.integer(minField.intMin(-2048), maxField.intMax(2048), 1, 1),
                readRange(draft, lowPath, highPath), rangeTexts(), value -> {
                    applyChange.accept(() -> {
                        writeNumber(draft, lowPath, value.lowPresent() ? value.low() : null);
                        writeNumber(draft, highPath, value.highPresent() ? value.high() : null);
                    });
                    onChange.run();
                });
        bar.setLabel(Component.translatable(minField.labelKey()));
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

    // 气候区间字段：对象内的端点键固定为 min / max
    private static UiRangeValue readRange(RuleDraft draft, String path) {
        RuleValue element = draft.valueAt(path);
        if (element == null || !element.isMapping()) {
            return UiRangeValue.EMPTY;
        }
        return readRange(element.child("min"), element.child("max"));
    }

    // 两个独立路径上的端点（高度/光照）
    private static UiRangeValue readRange(RuleDraft draft, String lowPath, String highPath) {
        return readRange(draft.valueAt(lowPath), draft.valueAt(highPath));
    }

    private static UiRangeValue readRange(@Nullable RuleValue low, @Nullable RuleValue high) {
        boolean lowPresent = low != null && low.isNumber();
        boolean highPresent = high != null && high.isNumber();
        if (!lowPresent && !highPresent) {
            return UiRangeValue.EMPTY;
        }
        return new UiRangeValue(lowPresent ? low.asDecimal(0.0D) : 0.0D,
                highPresent ? high.asDecimal(0.0D) : 0.0D, lowPresent, highPresent);
    }

    private static void writeNumber(RuleDraft draft, String path, @Nullable Double value) {
        if (value == null) {
            draft.removeAt(path);
        } else {
            draft.setAt(path, RuleValue.decimal(value));
        }
    }

}
