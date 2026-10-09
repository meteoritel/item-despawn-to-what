package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorFieldType;
import com.meteorite.itemdespawntowhat.client.edit.EditorWorkspaceView;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleNaming;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiCyclicRange;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiNumberPolicy;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRangeValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCatalogGrid;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCyclicTimeBar;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiNarration;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiRangeBar;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiStructureDiagram;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalog;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogEntry;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * P4 宿主装配（主计划 §4/§7/§8）：目录图标面板接线、气候/昼夜/高度区间条、结构图解与叶子编辑面板。
 * <p>本类只做「把 kit 控件与目录链路接到规则草稿上」的装配，不复制数据层逻辑：
 * 写入一律经调用方给定的 {@code applyChange}（内部走 {@link EditSession#apply(String, Runnable)}，
 * 一次完整操作 = 一条撤销记录）。
 * <p>目录条目、图标与文本由宿主解析：{@link #catalogLabel(String)} 判定翻译键，
 * {@link #iconFor(RuleCatalogType, String)} 提供物品/方块图标，其余目录类型回落文字槽。
 */
public final class RuleEditorP4Panels {

    private static final String UI = TypeLabels.UI_PREFIX;
    // 区间条 / 昼夜条高度（标签行 + 轨道；昼夜条另含预设行）
    private static final int RANGE_HEIGHT = 24;
    private static final int CYCLIC_HEIGHT = 40;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_GAP = 2;
    private static final int EXTRA_GAP = 2;
    // 一天 24000 刻；0 刻对应 Minecraft 06:00
    private static final double DAY_TICKS = 24000.0D;
    private static final int DAY_MINUTE_OFFSET = 360;
    private static final int TIME_MIN = 0;
    private static final int TIME_MAX = 23999;
    private static final int DEFAULT_TIME_RANGE_END = 12000;

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
        UiCyclicTimeBar bar = new UiCyclicTimeBar(font, cyclicTexts(), listener);
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
                Component.translatable(UI + "pick.next"), Component.translatable(UI + "pick.page"),
                Component.translatable(UI + "pick.selected"));
    }

    // 目录标签可能是翻译键或原文：无空格、无冒号且含点号时按翻译键解析
    public static Component catalogLabel(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return Component.empty();
        }
        boolean looksLikeKey = raw.indexOf(' ') < 0 && raw.indexOf(':') < 0 && raw.indexOf('.') >= 0;
        return looksLikeKey ? Component.translatable(raw) : Component.literal(raw);
    }

    // 物品、方块、实体和流体图标；其他目录类型回落文字槽。
    public static @Nullable UiIcon iconFor(@Nullable RuleCatalogType type, @Nullable String id) {
        if (type == null || id == null || id.isBlank()) {
            return null;
        }
        if (id.startsWith("#") && (type == RuleCatalogType.ITEM || type == RuleCatalogType.BLOCK
                || type == RuleCatalogType.ENTITY || type == RuleCatalogType.FLUID))
            return TagPreviewIcons.resolve(type, id).icon();
        String normalized = id.startsWith("#") ? id.substring(1) : id;
        ResourceLocation key = ResourceLocation.tryParse(
                normalized.indexOf(':') >= 0 ? normalized : "minecraft:" + normalized);
        if (key == null) {
            return null;
        }
        if (type == RuleCatalogType.ENTITY && !id.startsWith("#")) return EntityPreviewIcons.icon(key.toString(), 0);
        if (type == RuleCatalogType.BLOCK && !id.startsWith("#")) return BlockPreviewIcons.icon(key.toString());
        if (type == RuleCatalogType.FLUID && !id.startsWith("#")) return FluidPreviewIcons.icon(key.toString());
        ItemStack stack = switch (type) {
            case ITEM -> stackOf(BuiltInRegistries.ITEM.getOptional(key).orElse(null));
            case BLOCK -> BuiltInRegistries.BLOCK.getOptional(key).map(block -> stackOf(block.asItem()))
                    .orElse(ItemStack.EMPTY);
            default -> ItemStack.EMPTY;
        };
        return stack.isEmpty() ? null : new UiIcon.Item(stack);
    }

    private static ItemStack stackOf(@Nullable Item item) {
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    // 字段的注册表提示 -> 目录类型（只覆盖能落到目录面板的类型）
    public static @Nullable RuleCatalogType catalogTypeOf(EditorField field) {
        String registry = field.registry();
        if ((registry == null || registry.isBlank()) && field.domain() != null) {
            registry = field.domain();
        }
        if (registry != null && !registry.isBlank()) {
            RuleCatalogType byRegistry = registryType(registry);
            if (byRegistry != null) {
                return byRegistry;
            }
        }
        return switch (field.name()) {
            case "biomes", "biome" -> RuleCatalogType.BIOME;
            case "items", "item" -> RuleCatalogType.ITEM;
            case "blocks", "block" -> RuleCatalogType.BLOCK;
            case "entity_types", "entity" -> RuleCatalogType.ENTITY;
            case "fluids", "fluid" -> RuleCatalogType.FLUID;
            case "loot_table" -> RuleCatalogType.LOOT_TABLE;
            case "dimension" -> RuleCatalogType.DIMENSION;
            case "potion_effects", "effect", "mob_effect" -> RuleCatalogType.MOB_EFFECT;
            default -> null;
        };
    }

    private static @Nullable RuleCatalogType registryType(String registry) {
        String name = registry.toLowerCase(Locale.ROOT);
        if (name.equals("item") || name.equals("items") || name.endsWith(":item")) {
            return RuleCatalogType.ITEM;
        }
        if (name.equals("block") || name.equals("blocks") || name.endsWith(":block")) {
            return RuleCatalogType.BLOCK;
        }
        if (name.contains("entity_type") || name.equals("entity")) {
            return RuleCatalogType.ENTITY;
        }
        if (name.contains("fluid")) {
            return RuleCatalogType.FLUID;
        }
        if (name.contains("loot_table")) {
            return RuleCatalogType.LOOT_TABLE;
        }
        if (name.contains("biome")) {
            return RuleCatalogType.BIOME;
        }
        if (name.contains("dimension") || name.equals("level")) {
            return RuleCatalogType.DIMENSION;
        }
        if (name.contains("mob_effect") || name.contains("potion")) {
            return RuleCatalogType.MOB_EFFECT;
        }
        return null;
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

    // 输入页合并普通目录与同字段所属注册表的标签，沿用既有目录缓存和协议。
    public static UiModal catalogModal(Font font, EditorWorkspaceView workspace, RuleCatalogType type,
                                       boolean multiSelect, Component title, int screenWidth, int screenHeight,
                                       Consumer<List<String>> onPicked, @Nullable EditorField field, boolean combined) {
        boolean includeTags = combined && field != null && (field.type() == EditorFieldType.TAG || field.type() == EditorFieldType.TAG_LIST);
        workspace.refreshDirectory(type);
        if (includeTags) workspace.refreshDirectory(RuleCatalogType.TAG);
        CatalogMapper mapper = new CatalogMapper();
        mapper.field = field;
        mapper.includeTags = includeTags;
        UiCatalogGrid grid = new UiCatalogGrid(font, multiSelect, includeTags ? catalogTexts(true) : catalogTexts(), new UiCatalogGrid.Listener() {
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
        grid.setEntrySource(() -> {
            boolean active = workspace.active();
            if (active != mapper.active) {
                mapper.active = active;
                grid.setEnabled(active);
            }
            List<UiCatalogGrid.Entry> mapped = mapper.map(workspace, type);
            grid.setPage(mapper.page, mapper.pageCount);
            grid.setPageLimits(mapper.page > 0, mapper.hasNext);
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
        UiModal modal = UiModal.create(font);
        modal.title(title);
        modal.preferredWidth(combined ? 320 : 280);
        modal.contentWidget(grid, 150);
        modal.confirm(Component.translatable(UI + "button.confirm"), () -> onPicked.accept(grid.selection()));
        modal.cancel(Component.translatable(UI + "button.cancel"));
        modal.layoutCentered(screenWidth, screenHeight);
        return modal;
    }

    // 目录缓存 -> 面板条目；同一份缓存实例只转换一次，避免逐帧重建
    private static final class CatalogMapper {

        private RuleCatalog cached;
        private RuleCatalog cachedTags;
        private boolean includeTags;
        private @Nullable EditorField field;
        private @Nullable List<UiCatalogGrid.Entry> entries;
        private boolean active = true;
        private int page;
        private int pageCount;
        private boolean hasNext;
        private String filter = "";

        private Object language;
        private Object resources;
        private String mappedFilter = "";
        private int mappedPage = -1;
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
            if (changed) {
                tagPreviews.clear();
                cached = catalog;
                cachedTags = tags;
                if (catalog == null) { entries = null; hasNext = false; pageCount = 0; return null; }
                List<UiCatalogGrid.Entry> mapped = new ArrayList<>();
                java.util.Set<String> validTags = field != null && type == RuleCatalogType.TAG ? FieldCatalogChoices.tags(field) : null;
                for (RuleCatalogEntry entry : catalog.entries()) {
                    if (type == RuleCatalogType.FLUID && FluidPreviewIcons.isExcluded(entry.id())) continue;
                    if (validTags != null && !validTags.contains(entry.id())) continue;
                    if (type == RuleCatalogType.ENTITY && field != null && FieldCatalogChoices.isGenericProduct(field)
                            && (entry.id().equals("minecraft:item") || entry.id().equals("minecraft:experience_orb"))) continue;
                    String sub = validTags == null ? entry.subLabel() : Component.translatable(field.labelKey()).getString();
                    if (type == RuleCatalogType.TAG) {
                        RuleCatalogType memberType = field == null ? RuleCatalogType.ITEM : catalogTypeOf(field);
                        var tag = TagPreviewIcons.resolve(memberType == null ? RuleCatalogType.ITEM : memberType, entry.id());
                        tagPreviews.put(entry.id(), tag);
                        mapped.add(new UiCatalogGrid.Entry(entry.id(), tag.label(), null, tag.icon()));
                    } else {
                        ResourceLocation id = ResourceLocation.tryParse(entry.id());
                        Component fluidLabel = type == RuleCatalogType.FLUID && id != null ? FluidPreviewIcons.label(id) : null;
                        mapped.add(new UiCatalogGrid.Entry(entry.id(), fluidLabel == null ? catalogLabel(entry.label()) : fluidLabel,
                                sub == null || sub.isBlank() ? null : catalogLabel(sub), iconFor(type, entry.id())));
                    }
                }
                if (includeTags && tags != null && field != null) {
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
            if (changed || mappedPage != page || !mappedFilter.equals(filter)) {
                String query = filter.toLowerCase(Locale.ROOT).trim();
                boolean tagsOnly = includeTags && query.startsWith("#");
                String needle = tagsOnly ? query.substring(1) : query;
                List<UiCatalogGrid.Entry> filtered = full.stream().filter(entry -> !tagsOnly || entry.id().startsWith("#"))
                        .filter(entry -> entry.id().toLowerCase(Locale.ROOT).contains(needle)
                        || entry.label().getString().toLowerCase(Locale.ROOT).contains(needle)
                        || entry.subLabel() != null && entry.subLabel().getString().toLowerCase(Locale.ROOT).contains(needle)).toList();
                pageCount = Math.max(1, (filtered.size() + UiCatalogGrid.PAGE_SIZE - 1) / UiCatalogGrid.PAGE_SIZE);
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
    public static boolean climateAllUnbounded(@Nullable JsonObject rule) {
        if (rule == null || !rule.has(RuleFields.CONDITIONS)) {
            return false;
        }
        return climateAllUnbounded(rule.get(RuleFields.CONDITIONS));
    }

    private static final String[] CLIMATE_FIELDS = {"temperature", "humidity", "continentalness", "erosion", "depth",
            "weirdness"};

    private static boolean climateAllUnbounded(@Nullable JsonElement element) {
        if (element == null) {
            return false;
        }
        if (element.isJsonObject()) {
            JsonObject body = element.getAsJsonObject();
            if (isClimateCondition(body) && !hasBoundedClimate(body)) {
                return true;
            }
            for (String key : body.keySet()) {
                if (climateAllUnbounded(body.get(key))) {
                    return true;
                }
            }
            return false;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (climateAllUnbounded(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isClimateCondition(JsonObject body) {
        JsonElement type = body.get(RuleFields.TYPE);
        JsonElement mode = body.get("mode");
        return type != null && type.isJsonPrimitive() && type.getAsJsonPrimitive().isString()
                && type.getAsString().endsWith("biome")
                && mode != null && mode.isJsonPrimitive() && mode.getAsJsonPrimitive().isString()
                && "climate".equals(mode.getAsString());
    }

    // 至少一端有界才算有效约束，避免 "temperature": {} 被当作已填写
    private static boolean hasBoundedClimate(JsonObject body) {
        for (String name : CLIMATE_FIELDS) {
            JsonElement range = body.get(name);
            if (range == null || !range.isJsonObject()) {
                continue;
            }
            JsonObject bounds = range.getAsJsonObject();
            if (isNumber(bounds.get("min")) || isNumber(bounds.get("max"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNumber(@Nullable JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber();
    }

    // ---- 结构图解 ----

    // 槽位与连接：源物品 ->（催化剂）-> 候选 -> 效果；节点 id 由宿主映射到页面/候选/效果
    public static UiStructureDiagram structureDiagram(Font font, JsonObject rule, RuleNaming.NameSource source,
                                                      int candidateIndex, Consumer<String> navigate) {
        UiStructureDiagram diagram = new UiStructureDiagram(font, navigate::accept);
        diagram.setEmptyMessage(Component.translatable(UI + "diagram.empty"));
        diagram.setNodes(diagramNodes(rule, source, candidateIndex));
        diagram.setEdges(diagramEdges(rule));
        return diagram;
    }

    public static List<UiStructureDiagram.Node> diagramNodes(@Nullable JsonObject rule, RuleNaming.NameSource source,
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
            JsonObject candidate = ResultStructure.candidateAt(rule, index);
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

    private static @Nullable JsonObject effectAt(@Nullable JsonObject candidate, int index) {
        if (candidate == null || !candidate.has(RuleFields.CANDIDATE_EFFECTS)
                || !candidate.get(RuleFields.CANDIDATE_EFFECTS).isJsonArray()) {
            return null;
        }
        JsonArray effects = candidate.getAsJsonArray(RuleFields.CANDIDATE_EFFECTS);
        if (index < 0 || index >= effects.size() || !effects.get(index).isJsonObject()) {
            return null;
        }
        return effects.get(index).getAsJsonObject();
    }

    public static List<UiStructureDiagram.Edge> diagramEdges(@Nullable JsonObject rule) {
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
                tags ? RuleCatalogType.TAG : catalogTypeOf(field), isListField(field),
                Component.translatable(field.labelKey()), screenWidth, screenHeight, picked, field)));
        return new LeafPanel(font, form, extras, buttons, reloadValues);
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

    // 按钮宽度：先满足最小可点宽度，再压进可用宽度（可用宽度可能小于最小值）
    // 这里刻意不用 Math.clamp：可用宽度可能小于最小宽度，clamp 的 min>max 会抛异常
    @SuppressWarnings("ManualMinMaxCalculation")
    private static int fitWidth(int preferred, int available) {
        int atLeastMin = Math.max(preferred, 60);
        return available < atLeastMin ? available : atLeastMin;
    }

    // 气候区间：字段自身是 {min,max} 对象，两端可省略；0.1 普通步长 / 0.01 Shift
    private static UiRangeBar climateBar(Font font, RuleDraft draft, EditorField field, String basePath,
                                         Consumer<Runnable> applyChange, Runnable onChange) {
        String path = join(basePath, field.name());
        UiRangeBar bar = new UiRangeBar(font,
                UiNumberPolicy.of(field.doubleMin(-1.0D), field.doubleMax(1.0D), 0.1D, 0.01D),
                readRange(draft, path), rangeTexts(), value -> {
                    applyChange.accept(() -> {
                        if (!value.lowPresent() && !value.highPresent()) {
                            // 两端都不约束：省略该维，不写空对象（fields §3）
                            draft.removeAt(path);
                            return;
                        }
                        JsonObject body = new JsonObject();
                        if (value.lowPresent()) {
                            body.add("min", new JsonPrimitive(value.low()));
                        }
                        if (value.highPresent()) {
                            body.add("max", new JsonPrimitive(value.high()));
                        }
                        draft.setAt(path, body);
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
        UiRangeBar bar = new UiRangeBar(font,
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
                draft.setAt(fromPath, new JsonPrimitive((int) Math.round(fromTick)));
                draft.setAt(toPath, new JsonPrimitive((int) Math.round(toTick)));
            });
            onChange.run();
        });
        bar.setRange(intAt(draft, fromPath, TIME_MIN), intAt(draft, toPath, DEFAULT_TIME_RANGE_END));
        bar.setLabel(Component.translatable(from.labelKey()));
        return bar;
    }

    private static int intAt(RuleDraft draft, String path, int fallback) {
        JsonElement value = draft.getAt(path);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                ? value.getAsInt() : fallback;
    }

    // 气候区间字段：对象内的端点键固定为 min / max
    private static UiRangeValue readRange(RuleDraft draft, String path) {
        JsonElement element = draft.getAt(path);
        if (element == null || !element.isJsonObject()) {
            return UiRangeValue.EMPTY;
        }
        JsonObject body = element.getAsJsonObject();
        return readRange(body.get("min"), body.get("max"));
    }

    // 两个独立路径上的端点（高度/光照）
    private static UiRangeValue readRange(RuleDraft draft, String lowPath, String highPath) {
        return readRange(draft.getAt(lowPath), draft.getAt(highPath));
    }

    private static UiRangeValue readRange(@Nullable JsonElement low, @Nullable JsonElement high) {
        boolean lowPresent = low != null && low.isJsonPrimitive() && low.getAsJsonPrimitive().isNumber();
        boolean highPresent = high != null && high.isJsonPrimitive() && high.getAsJsonPrimitive().isNumber();
        if (!lowPresent && !highPresent) {
            return UiRangeValue.EMPTY;
        }
        return new UiRangeValue(lowPresent ? low.getAsDouble() : 0.0D, highPresent ? high.getAsDouble() : 0.0D,
                lowPresent, highPresent);
    }

    private static void writeNumber(RuleDraft draft, String path, @Nullable Double value) {
        if (value == null) {
            draft.removeAt(path);
        } else {
            draft.setAt(path, new JsonPrimitive(value));
        }
    }


    /**
     * 叶子面板：附加区间控件 + 目录按钮 + 表单，纵向排列。
     * <p>面板自身是模态内容控件，因此它负责把指针/键盘/字符事件转发给子控件，并在内部维护
     * Tab / Shift+Tab 的焦点顺序（附加控件 -> 目录按钮 -> 表单字段）。
     */
    public static final class LeafPanel implements UiWidget, UiFocusTarget {

        private final Font font;
        private final FormView form;
        private final List<UiWidget> extras;
        private final List<UiButton> buttons;
        private final List<Runnable> reloadValues;
        // 动态说明行（门槛留空时的「默认：N」等）：供应者返回 null 表示当前不画出该行
        private final List<Supplier<Component>> notes = new ArrayList<>();
        private final List<UiRect> noteRects = new ArrayList<>();
        // 说明行行高：比正文字高多 1 像素，避免与表单末行贴死
        private final int noteLineHeight;
        private UiRect bounds = new UiRect(0, 0, 0, 0);
        private boolean focused;
        private boolean enabled = true;
        private int focusIndex;

        LeafPanel(Font font, FormView form, List<UiWidget> extras, List<UiButton> buttons, List<Runnable> reloadValues) {
            this.font = font;
            this.noteLineHeight = font.lineHeight + 1;
            this.form = form;
            this.extras = extras;
            this.buttons = buttons;
            this.reloadValues = reloadValues;
        }

        /** 历史恢复后回填已接受的字段与区间值，未完成文本由表单保留。 */
        public void reload() {
            form.reload();
            reloadValues.forEach(Runnable::run);
        }

        // 追加一条动态说明行：高度在布局时预留，内容每帧重新求值
        public LeafPanel addNote(Supplier<Component> note) {
            notes.add(note);
            return this;
        }

        // 面板内容总高（模态据此申请高度）
        public int contentHeight() {
            int height = 0;
            for (UiWidget extra : extras) {
                height += extraHeight(extra) + EXTRA_GAP;
            }
            if (!buttons.isEmpty()) {
                height += BUTTON_HEIGHT + BUTTON_GAP;
            }
            height += form.contentHeight();
            height += notes.size() * noteLineHeight;
            return Math.max(24, height);
        }

        private static int extraHeight(UiWidget widget) {
            return widget instanceof UiCyclicTimeBar ? CYCLIC_HEIGHT : RANGE_HEIGHT;
        }

        @Override
        public UiRect bounds() {
            return bounds;
        }

        @Override
        public void setBounds(int x, int y, int width, int height) {
            this.bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
            layout();
        }

        private void layout() {
            int x = bounds.x();
            int width = bounds.width();
            int cursorY = bounds.y();
            for (UiWidget extra : extras) {
                int extraHeight = extraHeight(extra);
                extra.setBounds(x, cursorY, width, extraHeight);
                cursorY += extraHeight + EXTRA_GAP;
            }
            cursorY = layoutButtons(cursorY);
            int notesHeight = notes.size() * noteLineHeight;
            form.setBounds(x, cursorY, width, Math.max(12, bounds.bottom() - cursorY - notesHeight));
            // 说明行固定排在表单下方，供应者返回 null 时仅留白不画字
            noteRects.clear();
            int noteY = cursorY + Math.max(12, bounds.bottom() - cursorY - notesHeight);
            for (int index = 0; index < notes.size(); index++) {
                noteRects.add(new UiRect(x, noteY, width, noteLineHeight));
                noteY += noteLineHeight;
            }
        }

        private int layoutButtons(int cursorY) {
            if (buttons.isEmpty()) {
                return cursorY;
            }
            int x = bounds.x();
            int cursorX = x;
            for (UiButton button : buttons) {
                int buttonWidth = fitWidth(button.preferredWidth(4), bounds.width());
                if (cursorX > x && cursorX + buttonWidth > bounds.right()) {
                    cursorX = x;
                    cursorY += BUTTON_HEIGHT + BUTTON_GAP;
                }
                button.setBounds(cursorX, cursorY, buttonWidth, BUTTON_HEIGHT);
                cursorX += buttonWidth + BUTTON_GAP;
            }
            return cursorY + BUTTON_HEIGHT + BUTTON_GAP;
        }

        // 结束所有未完成交互（切页/关闭/失权）
        public void endInteractions() {
            for (UiWidget extra : extras) {
                if (extra instanceof UiRangeBar bar) {
                    bar.endInteraction(UiInputCapture.EndReason.FOCUS_SCOPE_CHANGED);
                } else if (extra instanceof UiCyclicTimeBar bar) {
                    bar.endInteraction(UiInputCapture.EndReason.FOCUS_SCOPE_CHANGED);
                }
            }
            form.onFocusScopeChanged();
        }

        public void unmount() {
            for (UiWidget extra : extras) {
                if (extra instanceof UiRangeBar bar) {
                    bar.unmount();
                } else if (extra instanceof UiCyclicTimeBar bar) {
                    bar.unmount();
                }
            }
            form.unmount();
        }

        public void onHostClosed() {
            for (UiWidget extra : extras) {
                if (extra instanceof UiRangeBar bar) {
                    bar.onHostClosed();
                } else if (extra instanceof UiCyclicTimeBar bar) {
                    bar.onHostClosed();
                }
            }
            form.onHostClosed();
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
            for (UiWidget extra : extras) {
                if (extra instanceof UiRangeBar bar) {
                    bar.setEnabled(enabled);
                } else if (extra instanceof UiCyclicTimeBar bar) {
                    bar.setEnabled(enabled);
                }
            }
            for (UiButton button : buttons) {
                button.setEnabled(enabled);
            }
            form.setEnabled(enabled);
        }

        // 面板内部焦点顺序：附加控件 -> 目录按钮 -> 表单字段
        public List<UiFocusTarget> focusTargets() {
            List<UiFocusTarget> targets = new ArrayList<>();
            for (UiWidget extra : extras) {
                if (extra instanceof UiFocusTarget target) {
                    targets.add(target);
                }
            }
            targets.addAll(buttons);
            targets.addAll(form.focusTargets());
            return targets;
        }

        @Override
        public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
            for (UiWidget extra : extras) {
                extra.render(graphics, renderFont, mouseX, mouseY);
            }
            for (UiButton button : buttons) {
                button.render(graphics, renderFont, mouseX, mouseY);
            }
            form.render(graphics, renderFont, mouseX, mouseY);
            for (int index = 0; index < notes.size() && index < noteRects.size(); index++) {
                Component note = notes.get(index).get();
                if (note == null) {
                    continue;
                }
                UiRect rect = noteRects.get(index);
                String text = TextScroll.trimToWidth(font, note.getString(), Math.max(1, rect.width()));
                graphics.drawString(font, text, rect.x(), rect.y(), UiPalette.TEXT_SECONDARY, false);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!enabled || !bounds.contains(mouseX, mouseY)) {
                return false;
            }
            List<UiFocusTarget> targets = focusTargets();
            if (button == 0) {
                for (int index = 0; index < targets.size(); index++) {
                    UiFocusTarget target = targets.get(index);
                    boolean hit = target.canFocus() && target.bounds().contains(mouseX, mouseY);
                    target.setFocused(hit);
                    if (hit) {
                        focusIndex = index;
                    }
                }
            }
            for (UiButton candidate : buttons) {
                if (candidate.isVisible() && candidate.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            for (UiWidget extra : extras) {
                if (extra.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            return form.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            boolean consumed = false;
            for (UiWidget extra : extras) {
                consumed |= extra.mouseReleased(mouseX, mouseY, button);
            }
            consumed |= form.mouseReleased(mouseX, mouseY, button);
            for (UiButton candidate : buttons) {
                consumed |= candidate.mouseReleased(mouseX, mouseY, button);
            }
            return consumed;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            boolean consumed = false;
            for (UiWidget extra : extras) {
                consumed |= extra.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            }
            consumed |= form.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            return consumed;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            return form.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_TAB) {
                moveFocus((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1);
                return true;
            }
            for (UiWidget extra : extras) {
                if (extra.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
            for (UiButton button : buttons) {
                if (button.isFocused() && button.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
            return form.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
            boolean consumed = false;
            for (UiWidget extra : extras) {
                consumed |= extra.keyReleased(keyCode, scanCode, modifiers);
            }
            consumed |= form.keyReleased(keyCode, scanCode, modifiers);
            return consumed;
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            for (UiWidget extra : extras) {
                if (extra.charTyped(codePoint, modifiers)) {
                    return true;
                }
            }
            return form.charTyped(codePoint, modifiers);
        }

        // 参数校验失败时，内部焦点与字段滚动保持一致。
        public void focusField(String path) {
            UiFocusTarget target = form.revealPath(path);
            List<UiFocusTarget> targets = focusTargets();
            for (int index = 0; index < targets.size(); index++) {
                boolean selected = targets.get(index) == target;
                targets.get(index).setFocused(selected);
                if (selected) {
                    focusIndex = index;
                }
            }
        }

        private void moveFocus(int delta) {
            List<UiFocusTarget> targets = focusTargets();
            if (targets.isEmpty()) {
                return;
            }
            int count = targets.size();
            focusIndex = ((focusIndex + delta) % count + count) % count;
            for (int index = 0; index < count; index++) {
                targets.get(index).setFocused(index == focusIndex);
            }
            focused = true;
            // 面板内部焦点切换同样播报（屏幕的焦点管理器看不到面板内部顺序）
            UiNarration.focus(targets.get(focusIndex));
        }

        @Override
        public boolean canFocus() {
            return enabled;
        }

        @Override
        public void setFocused(boolean focused) {
            this.focused = focused;
            List<UiFocusTarget> targets = focusTargets();
            if (!focused) {
                for (UiFocusTarget target : targets) {
                    target.setFocused(false);
                }
                return;
            }
            if (targets.isEmpty()) {
                return;
            }
            focusIndex = Math.clamp(focusIndex, 0, targets.size() - 1);
            targets.get(focusIndex).setFocused(true);
        }

        @Override
        public boolean isFocused() {
            return focused;
        }

        @Override
        public boolean activate() {
            List<UiFocusTarget> targets = focusTargets();
            if (targets.isEmpty()) {
                return false;
            }
            focusIndex = Math.clamp(focusIndex, 0, targets.size() - 1);
            return targets.get(focusIndex).activate();
        }

        @Override
        public Component accessibleName() {
            return Component.translatable(UI + "diagram.leaf").copy().append(" (" + extras.size() + ")");
        }
    }
}
