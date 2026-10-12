package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.EditorWorkspaceView;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.shared.CatalogPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCatalogGrid;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.Nullable;

/*** 黑名单仅从源标签的成员中添加具体物品，按标签分页；成员快照只在打开时解析。 */
final class SourceBlacklistPicker {

    // 宿主默认主题：屏幕从主题入口取样式后注入本类装配的控件
    private static final UiHostTheme THEME = UiHostThemes.defaultTheme();

    private static final String UI = "gui.itemdespawntowhat.edit.";

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
            if (raw.startsWith("#") && ResourceLocation.tryParse(raw.substring(1)) != null) tags.add(raw);
        }
        return List.copyOf(tags);
    }

    static UiModal modal(Font font, EditorWorkspaceView workspace, @Nullable RuleValue rule, int width, int height,
                         Consumer<List<String>> onPicked) {
        CatalogEntryPolicy policy = CatalogEntryPolicy.fixed(RuleCatalogType.ITEM);
        Pages pages = new Pages(sourceTags(rule), policy);
        UiCatalogGrid.Texts texts = RuleEditorP4Panels.catalogTexts()
                .withEmpty(Component.translatable(UI + "blacklist.empty"));
        UiCatalogGrid grid = new UiCatalogGrid(THEME, font, true, texts, new UiCatalogGrid.Listener() {
                    @Override public void onSearch(String filter) { pages.filter(filter); }
                    @Override public void onPageChange(int delta) { pages.index = Math.clamp(pages.index + delta, 0, Math.max(0, pages.filtered.size() - 1)); }
                });
        grid.setEntryPolicy(policy);
        grid.setEntrySource(() -> {
            grid.setEnabled(workspace.active());
            if (pages.filtered.isEmpty()) return List.of();
            Page page = pages.filtered.get(pages.index);
            grid.setPage(pages.index, pages.filtered.size());
            grid.setPageLimits(pages.index > 0, pages.index + 1 < pages.filtered.size());
            grid.setPageLabel(Component.translatable(UI + "blacklist.page", page.tag(), page.part() + 1, page.parts()));
            return page.items();
        });
        UiModal modal = UiModal.create(THEME, font).title(Component.translatable(UI + "rule.source.exclude"));
        modal.preferredWidth(RuleEditorP4Panels.pickerWidth(width));
        modal.inlineFooter();
        modal.contentWidget(grid, RuleEditorP4Panels.pickerBodyHeight(height));
        modal.confirm(Component.translatable(UI + "button.confirm"), () -> {
            if (workspace.active()) onPicked.accept(grid.selection());
        });
        modal.cancel(Component.translatable(UI + "button.cancel"));
        modal.layoutCentered(width, height);
        return modal;
    }

    /*** 一个标签的具体物品快照；标签自身不会成为可选条目。 */
    private record Group(String tag, List<UiCatalogGrid.Entry> items) { }

    /*** 大标签分为多个子页，每个子页仍只属于一个源标签。 */
    private record Page(String tag, int part, int parts, List<UiCatalogGrid.Entry> items) { }

    /*** 缓存成员和过滤后的页；搜索与翻页之外不重新解析成员。 */
    private static final class Pages {
        private final List<Group> groups = new ArrayList<>();
        private final CatalogEntryPolicy policy;
        private List<Page> filtered = List.of();
        private int index;

        Pages(List<String> tags, CatalogEntryPolicy policy) {
            this.policy = policy;
            for (String tag : tags) {
                ResourceLocation id = ResourceLocation.tryParse(tag.substring(1));
                if (id == null) continue;
                List<UiCatalogGrid.Entry> members = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id))
                        .map(set -> set.stream().map(holder -> {
                            String itemId = BuiltInRegistries.ITEM.getKey(holder.value()).toString();
                            return new UiCatalogGrid.Entry(itemId, holder.value().getDescription(), null,
                                    CatalogPreviewIcons.iconFor(RuleCatalogType.ITEM, itemId));
                        }).toList()).orElse(List.of());
                groups.add(new Group(tag, members));
            }
            filter("");
        }

        void filter(String text) {
            String needle = text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
            List<Page> result = new ArrayList<>();
            for (Group group : groups) {
                List<UiCatalogGrid.Entry> items = group.items().stream().filter(entry ->
                        !policy.hidden(entry.id())).filter(entry ->
                        entry.id().toLowerCase(Locale.ROOT).contains(needle)
                                || entry.label().getString().toLowerCase(Locale.ROOT).contains(needle)).toList();
                int parts = Math.max(1, (items.size() + UiCatalogGrid.PAGE_SIZE - 1) / UiCatalogGrid.PAGE_SIZE);
                for (int part = 0; part < parts; part++) {
                    int from = part * UiCatalogGrid.PAGE_SIZE;
                    result.add(new Page(group.tag(), part, parts, List.copyOf(items.subList(from, Math.min(items.size(), from + UiCatalogGrid.PAGE_SIZE)))));
                }
            }
            filtered = List.copyOf(result);
            index = 0;
        }
    }
}
