package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.EditorWorkspaceView;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.shared.CatalogPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCatalogGrid;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
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
    private static final UiHostTheme THEME = UiHostThemes.defaultTheme();

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
            if (raw.startsWith("#") && ResourceLocation.tryParse(raw.substring(1)) != null) tags.add(raw);
        }
        return List.copyOf(tags);
    }

    static UiModal modal(Font font, EditorShell shell, EditorWorkspaceView workspace, @Nullable RuleValue rule, int width, int height,
                         Consumer<List<String>> onPicked) {
        CatalogEntryPolicy policy = CatalogEntryPolicy.fixed(RuleCatalogType.ITEM);
        Pages pages = new Pages(sourceTags(rule), policy);
        UiCatalogGrid.Texts texts = RuleEditorPanelFactory.catalogTexts()
                .withEmpty(Component.translatable(UI + "blacklist.empty"));
        UiCatalogGrid grid = new UiCatalogGrid(THEME, font, true, texts, new UiCatalogGrid.Listener() {
                    @Override public void onSearch(String filter) { pages.filter(filter); }
                    @Override public void onPageChange(int delta) { pages.index = Math.clamp(pages.index + delta, 0, Math.max(0, pages.filtered.size() - 1)); }
                });
        grid.setEntryPolicy(policy);
        RuleValue source = rule == null ? null : rule.child(RuleFields.SOURCE);
        grid.setSelection(source == null ? List.of() : RuleValueReads.stringList(source.child(RuleFields.SOURCE_EXCLUDE)));
        grid.setEntrySource(() -> {
            grid.setEnabled(shell.editable());
            if (pages.filtered.isEmpty()) return List.of();
            Page page = pages.filtered.get(pages.index);
            grid.setPage(pages.index, pages.filtered.size());
            grid.setPageLimits(pages.index > 0, pages.index + 1 < pages.filtered.size());
            grid.setPageLabel(Component.translatable(UI + "blacklist.page", page.tag(), page.part() + 1, page.parts()));
            return page.items();
        });
        UiModal modal = UiModal.create(THEME, font).title(Component.translatable(UI + "rule.source.exclude"));
        modal.preferredWidth(RuleEditorPanelFactory.pickerWidth(width));
        modal.inlineFooter();
        modal.contentWidget(grid, RuleEditorPanelFactory.pickerBodyHeight(height));
        modal.confirm(Component.translatable(UI + "button.confirm"), () -> {
            if (shell.editable()) onPicked.accept(grid.selection());
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

        // 按当前搜索词过滤成员：空搜索只做 id 与策略判定，命中搜索时才创建条目读取显示名
        private List<String> visibleIds(String needle, CatalogEntryPolicy policy) {
            if (needle.isEmpty() && visibleAll != null) {
                return visibleAll;
            }
            List<String> visible = new ArrayList<>();
            for (String itemId : members().keySet()) {
                if (policy.hidden(itemId)) {
                    continue;
                }
                if (!needle.isEmpty()) {
                    UiCatalogGrid.Entry entry = entry(itemId);
                    if (!itemId.toLowerCase(Locale.ROOT).contains(needle)
                            && !entry.label().getString().toLowerCase(Locale.ROOT).contains(needle)) {
                        continue;
                    }
                }
                visible.add(itemId);
            }
            List<String> result = List.copyOf(visible);
            if (needle.isEmpty()) {
                visibleAll = result;
            }
            return result;
        }

        // 单个条目：名称、图标与预览只在该物品真的出现在某个子页时创建
        private UiCatalogGrid.Entry entry(String itemId) {
            return entries.computeIfAbsent(itemId, key -> {
                Item item = members().get(key);
                Component name = item == null ? Component.literal(key) : item.getDescription();
                return new UiCatalogGrid.Entry(key, name, null, CatalogPreviewIcons.iconFor(RuleCatalogType.ITEM, key));
            });
        }
    }

    /** 大标签分为多个子页，每个子页仍只属于一个源标签；条目列表在子页生命周期内复用同一实例。 */
    private static final class Page {
        private final Group group;
        private final List<String> visible;
        private final int part;
        private final int parts;
        private final int from;
        private final int to;
        private @Nullable List<UiCatalogGrid.Entry> items;

        private Page(Group group, List<String> visible, int part, int parts, int from, int to) {
            this.group = group;
            this.visible = visible;
            this.part = part;
            this.parts = parts;
            this.from = from;
            this.to = to;
        }

        private String tag() {
            return group.tag();
        }

        private int part() {
            return part;
        }

        private int parts() {
            return parts;
        }

        // 只展开当前子页的条目；UiCatalogGrid 用实例身份判断内容变化，因此必须返回同一个列表
        private List<UiCatalogGrid.Entry> items() {
            if (items == null) {
                List<UiCatalogGrid.Entry> built = new ArrayList<>(to - from);
                for (int index = from; index < to; index++) {
                    built.add(group.entry(visible.get(index)));
                }
                items = List.copyOf(built);
            }
            return items;
        }
    }

    /** 缓存标签成员与过滤后的页；搜索与翻页之外不重新解析成员，也不重建未显示的条目。 */
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
                groups.add(new Group(tag, id));
            }
            filter("");
        }

        void filter(String text) {
            String needle = text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
            List<Page> result = new ArrayList<>();
            for (Group group : groups) {
                List<String> visible = group.visibleIds(needle, policy);
                int parts = Math.max(1, (visible.size() + UiCatalogGrid.PAGE_SIZE - 1) / UiCatalogGrid.PAGE_SIZE);
                for (int part = 0; part < parts; part++) {
                    int from = part * UiCatalogGrid.PAGE_SIZE;
                    int to = Math.min(visible.size(), from + UiCatalogGrid.PAGE_SIZE);
                    result.add(new Page(group, visible, part, parts, from, to));
                }
            }
            filtered = List.copyOf(result);
            index = 0;
        }
    }
}
