package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.shared.CatalogSelectionBinding;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.shared.CatalogPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.CatalogDisplayQuery;
import com.meteorite.itemdespawntowhat.client.ui.shared.CatalogTypePresentation;
import com.meteorite.itemdespawntowhat.client.ui.shared.PreviewResources;
import net.minecraft.locale.Language;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCatalogGrid;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiModal;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalogType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/** 黑名单仅从源标签的成员中添加具体物品，按标签分页；成员条目只在真正显示时创建。 */
final class SourceBlacklistPicker {

    private static final String UI = TypeLabels.UI_PREFIX;

    private SourceBlacklistPicker() { }

    static List<String> sourceTags(@Nullable RuleValue rule) {
        if (rule == null || !rule.isMapping()) return List.of();
        RuleValue source = rule.child(RuleFields.SOURCE);
        if (source == null || !source.isMapping()) return List.of();
        RuleValue items = source.child(RuleFields.SOURCE_ITEMS);
        if (items == null || !items.isList()) return List.of();
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        // 仅收集形如 #命名空间:路径 的源标签，非文本元素自然被 textItems() 跳过
        for (String raw : items.textItems()) {
            TaggedId reference = TaggedId.tryParse(raw);
            if (reference != null && reference.tag()) tags.add(raw);
        }
        return List.copyOf(tags);
    }

    static UiModal modal(Font font, EditorShell shell, EditSession session, @Nullable RuleValue rule, int width, int height,
                         CatalogSelectionBinding binding) {
        return new Picker(font, shell, session, rule, width, height, binding).modal;
    }

    private static final class Picker implements UiCatalogGrid.Listener {
        private final EditorShell shell;
        private final EditSession session;
        private final CatalogSelectionBinding binding;
        private final Pages pages;
        private final UiModal modal;
        private final UiCatalogGrid grid;
        private List<String> selection;
        private UiCatalogGrid.View view;

        private Picker(Font font, EditorShell shell, EditSession session, @Nullable RuleValue rule,
                       int width, int height, CatalogSelectionBinding binding) {
            this.shell = shell;
            this.session = session;
            this.binding = binding;
            CatalogEntryPolicy policy = CatalogEntryPolicy.fixed(RuleCatalogType.ITEM);
            pages = new Pages(sourceTags(rule), policy);
            selection = List.copyOf(binding.mode().snapshot(binding.initialSelection()));
            UiCatalogGrid.Texts texts = RuleEditorPanelFactory.catalogTexts()
                    .withEmpty(Component.translatable(UI + "blacklist.empty"));
            modal = UiModal.create(shell.theme(), font).title(Component.translatable(UI + "rule.source.exclude"));
            grid = new UiCatalogGrid(shell.theme(), font, binding.mode(), texts, this);
            grid.configure(policy, ignored -> null);
            modal.preferredWidth(RuleEditorPanelFactory.pickerWidth(width)).inlineFooter();
            modal.contentWidget(grid, RuleEditorPanelFactory.pickerBodyHeight(shell.theme(), height));
            modal.confirm(Component.translatable(UI + "button.confirm"), () -> {
                if (active()) binding.confirm(selection);
                else shell.setNotice(Component.translatable(UI + "pick.target_changed"), EditorShell.NoticeLevel.WARNING);
            });
            modal.cancel(Component.translatable(UI + "button.cancel"));
            modal.onPrepare(this::prepare);
            prepare();
            modal.layoutCentered(width, height);
        }

        private boolean active() { return shell.editable() && shell.editingSession() == session; }

        private void prepare() {
            CatalogDisplayQuery.Page<Page> result = pages.current();
            Page page = result.content();
            UiCatalogGrid.Content content = view == null || view.content().generation() != pages.contentGeneration
                    || view.content().selection() != selection
                    ? new UiCatalogGrid.Content(pages.contentGeneration, page == null ? List.of() : page.items(), selection)
                    : view.content();
            view = new UiCatalogGrid.View(content,
                    new UiCatalogGrid.Navigation(UiCatalogGrid.Scope.BOTH, pages.query, result.index(), result.count(),
                            page == null ? null : page.label()), new UiCatalogGrid.Status(false, active(), null, null));
            grid.applyView(view);
            modal.buttons().getFirst().setEnabled(active());
        }

        /** {@inheritDoc} */
        @Override public void onSearch(String filter) { pages.filter(filter); prepare(); }
        /** {@inheritDoc} */
        @Override public void onPageChange(int delta) { pages.move(delta); prepare(); }
        /** {@inheritDoc} */
        @Override public void onSelectionChanged(List<String> next) {
            selection = List.copyOf(binding.mode().snapshot(next));
            prepare();
        }
        /** {@inheritDoc} */
        @Override public void onConfirm() { modal.buttons().getFirst().activate(); }
    }

    /** 一个源标签的成员：成员 id 第一次需要分页或搜索时才解析，条目与图标只在显示时创建。 */
    private static final class Group {
        private final String tag;
        private final ResourceLocation id;
        private @Nullable Map<String, Item> members;
        private @Nullable List<String> visibleAll;
        private final Map<String, UiCatalogGrid.Entry> entries = new HashMap<>();

        private Group(String tag, ResourceLocation id) {
            this.tag = tag;
            this.id = id;
        }

        private String tag() {
            return tag;
        }

        // 标签成员：只保存注册表物品引用，名称与图标留到条目真正显示时再取
        private Map<String, Item> members() {
            if (members == null) {
                Map<String, Item> collected = new LinkedHashMap<>();
                BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id)).ifPresent(set -> set.forEach(holder -> {
                    Item item = holder.value();
                    collected.put(BuiltInRegistries.ITEM.getKey(item).toString(), item);
                }));
                members = collected;
            }
            return members;
        }

        private List<String> visibleIds(CatalogEntryPolicy policy) {
            if (visibleAll == null) {
                visibleAll = members().keySet().stream().filter(itemId -> !policy.hidden(
                        new TaggedId(BuiltInRegistries.ITEM.getKey(members().get(itemId)), false))).toList();
            }
            return visibleAll;
        }

        private boolean matches(String itemId, CatalogDisplayQuery.Search query) {
            if (query.needle().isEmpty()) return true;
            UiCatalogGrid.Entry entry = entry(itemId);
            return query.matches(itemId, false, entry.label(), entry.subLabel());
        }

        private void invalidate() {
            members = null;
            visibleAll = null;
            entries.clear();
        }

        // 单个条目：名称、图标与预览只在该物品真的出现在某个子页时创建
        private UiCatalogGrid.Entry entry(String itemId) {
            return entries.computeIfAbsent(itemId, key -> {
                Item item = members().get(key);
                TaggedId reference = new TaggedId(BuiltInRegistries.ITEM.getKey(item), false);
                Component name = CatalogTypePresentation.ITEM.label(reference.id());
                return new UiCatalogGrid.Entry(key, reference, name, null, CatalogPreviewIcons.iconFor(RuleCatalogType.ITEM, reference));
            });
        }
    }

    /** 大标签分为多个子页，每个子页仍只属于一个源标签；当前子页缓存已展开的条目。 */
    private static final class Page {
        private final CatalogDisplayQuery.Part<Group, String> part;
        private @Nullable List<UiCatalogGrid.Entry> items;
        private @Nullable Component label;

        private Page(CatalogDisplayQuery.Part<Group, String> part) { this.part = part; }

        private Component label() {
            if (label == null) label = Component.translatable(UI + "blacklist.page", part.group().tag(), part.index() + 1, part.count());
            return label;
        }

        // 只展开当前子页，避免为未显示的组创建名称与图标
        private List<UiCatalogGrid.Entry> items() {
            if (items == null) {
                items = part.items().stream().map(part.group()::entry).toList();
            }
            return items;
        }
    }

    /** 缓存标签成员与过滤后的页；搜索与翻页之外不重新解析成员，也不重建未显示的条目。 */
    private static final class Pages {
        private final List<Group> groups = new ArrayList<>();
        private final CatalogEntryPolicy policy;
        private CatalogDisplayQuery.Pages<Page> filtered;
        private CatalogDisplayQuery.Page<Page> published;
        private String query = "";
        private Object generation;
        private Object language;
        private int index;
        private long contentGeneration;

        Pages(List<String> tags, CatalogEntryPolicy policy) {
            this.policy = policy;
            for (String tag : tags) {
                TaggedId reference = TaggedId.tryParse(tag);
                if (reference != null) groups.add(new Group(tag, reference.id()));
            }
            filter("");
        }

        void filter(String text) {
            if (filtered != null && query.equals(text) && generation == PreviewResources.generation()
                    && language == Language.getInstance()) return;
            query = text;
            CatalogDisplayQuery.Search search = CatalogDisplayQuery.search(text, false);
            var result = CatalogDisplayQuery.grouped(groups, group -> group.visibleIds(policy),
                    (group, id) -> group.matches(id, search));
            filtered = new CatalogDisplayQuery.Pages<>(result.contents().stream().map(Page::new).toList(), result.total());
            generation = PreviewResources.generation();
            language = Language.getInstance();
            index = 0;
            published = null;
            contentGeneration++;
        }

        private void move(int delta) {
            int next = filtered.page(index + delta).index();
            if (next == index) return;
            index = next;
            contentGeneration++;
        }

        private CatalogDisplayQuery.Page<Page> current() {
            if (generation != PreviewResources.generation() || language != Language.getInstance()) {
                groups.forEach(Group::invalidate);
                int requested = index;
                filter(query);
                index = requested;
            }
            if (published == null || published.index() != index) published = filtered.page(index);
            index = published.index();
            return published;
        }
    }
}
