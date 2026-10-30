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
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCatalogGrid;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
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

    // 宿主默认主题：屏幕从主题入口取样式后注入本类装配的控件

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
        UiHostTheme THEME = shell.theme();
        CatalogEntryPolicy policy = CatalogEntryPolicy.fixed(RuleCatalogType.ITEM);
        Pages pages = new Pages(sourceTags(rule), policy);
        UiCatalogGrid.Texts texts = RuleEditorPanelFactory.catalogTexts()
                .withEmpty(Component.translatable(UI + "blacklist.empty"));
        UiModal modal = UiModal.create(THEME, font).title(Component.translatable(UI + "rule.source.exclude"));
        CatalogViewState viewState = new CatalogViewState(binding.initialSelection());
        UiCatalogGrid grid = new UiCatalogGrid(THEME, font, binding.mode(), texts, new UiCatalogGrid.Listener() {
                    @Override public void onSearch(String filter) { pages.filter(filter); }
                    @Override public void onPageChange(int delta) { pages.move(delta); }
                    @Override public void onSelectionChanged(List<String> selection) {
                        viewState.select(selection);
                    }
                    @Override public void onConfirm() {
                        if (!modal.buttons().isEmpty()) modal.buttons().getFirst().activate();
                    }
                });
        grid.setViewSource(() -> {
            CatalogDisplayQuery.Page<Page> result = pages.current();
            Page page = result.content();
            viewState.entries(page == null ? List.of() : page.items());
            viewState.navigation(UiCatalogGrid.Scope.BOTH, result.index(), result.count(),
                    page == null ? null : page.label());
            viewState.status(false, shell.editable() && shell.editingSession() == session, -1, null);
            return viewState.view();
        }, policy, ignored -> null);
        modal.preferredWidth(RuleEditorPanelFactory.pickerWidth(width));
        modal.inlineFooter();
        modal.contentWidget(grid, RuleEditorPanelFactory.pickerBodyHeight(THEME, height));
        modal.confirm(Component.translatable(UI + "button.confirm"), () -> {
            if (shell.editable() && shell.editingSession() == session) binding.confirm(viewState.selection());
            else shell.setNotice(Component.translatable(UI + "pick.target_changed"), EditorShell.NoticeLevel.WARNING);
        });
        modal.cancel(Component.translatable(UI + "button.cancel"));
        modal.layoutCentered(width, height);
        return modal;
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

    /** 大标签分为多个子页，每个子页仍只属于一个源标签；条目列表在子页生命周期内复用同一实例。 */
    private static final class Page {
        private final CatalogDisplayQuery.Part<Group, String> part;
        private @Nullable List<UiCatalogGrid.Entry> items;
        private @Nullable Component label;

        private Page(CatalogDisplayQuery.Part<Group, String> part) { this.part = part; }

        private Component label() {
            if (label == null) label = Component.translatable(UI + "blacklist.page", part.group().tag(), part.index() + 1, part.count());
            return label;
        }

        // 只展开当前子页的条目；UiCatalogGrid 用实例身份判断内容变化，因此必须返回同一个列表
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

        Pages(List<String> tags, CatalogEntryPolicy policy) {
            this.policy = policy;
            for (String tag : tags) {
                TaggedId reference = TaggedId.tryParse(tag);
                if (reference != null) groups.add(new Group(tag, reference.id()));
            }
            filter("");
        }

        void filter(String text) {
            query = text;
            CatalogDisplayQuery.Search search = CatalogDisplayQuery.search(text, false);
            var result = CatalogDisplayQuery.grouped(groups, group -> group.visibleIds(policy),
                    (group, id) -> group.matches(id, search));
            filtered = new CatalogDisplayQuery.Pages<>(result.contents().stream().map(Page::new).toList(), result.total());
            generation = PreviewResources.generation();
            language = Language.getInstance();
            index = 0;
            published = null;
        }

        private void move(int delta) { index = filtered.page(index + delta).index(); }

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
