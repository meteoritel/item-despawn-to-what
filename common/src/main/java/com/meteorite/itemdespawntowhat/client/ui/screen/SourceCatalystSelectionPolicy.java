package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.edit.DraftPaths;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.Nullable;

/** 源物品与催化剂选择的互斥快照，标签按客户端同步成员展开，源排除项优先。 */
final class SourceCatalystSelectionPolicy implements Function<String, @Nullable Component> {
    private static final String SOURCE_ITEMS_PATH = DraftPaths.child(RuleFields.SOURCE, RuleFields.SOURCE_ITEMS);
    private static final String SOURCE_EXCLUDE_PATH = DraftPaths.child(RuleFields.SOURCE, RuleFields.SOURCE_EXCLUDE);

    private final Map<String, Set<String>> members = new HashMap<>();
    private final Map<String, Boolean> conflicts = new HashMap<>();
    private final Set<String> excluded;
    private final Set<String> occupied;
    private final Component reason;

    private SourceCatalystSelectionPolicy(RuleDraft draft, boolean selectingSource) {
        excluded = expand(references(draft.valueAt(SOURCE_EXCLUDE_PATH)));
        if (selectingSource) {
            Set<String> catalysts = new HashSet<>();
            collectCatalysts(draft.root(), catalysts);
            occupied = expand(List.copyOf(catalysts));
        } else {
            occupied = expand(references(draft.valueAt(SOURCE_ITEMS_PATH)));
            occupied.removeAll(excluded);
        }
        reason = Component.translatable(TypeLabels.UI_PREFIX
                + (selectingSource ? "pick.catalyst_conflict" : "pick.source_conflict"));
    }

    static Function<String, @Nullable Component> forField(RuleDraft draft, String path) {
        boolean source = SOURCE_ITEMS_PATH.equals(path);
        int separator = path.lastIndexOf('.');
        if (!source && (separator < 0 || !RuleFields.CATALYST_ITEMS.equals(path.substring(separator + 1))))
            return ignored -> null;
        String ownerPath = path.substring(0, separator);
        if (!source && !isCatalystOwner(draft.valueAt(ownerPath), ownerPath)) return ignored -> null;
        return new SourceCatalystSelectionPolicy(draft, source);
    }

    @Override
    public @Nullable Component apply(String id) {
        // 每个引用只展开和比较一次；搜索、翻页和逐帧绘制复用同一快照。
        boolean conflict = conflicts.computeIfAbsent(id, reference -> members(reference).stream()
                .anyMatch(item -> !excluded.contains(item) && occupied.contains(item)));
        return conflict ? reason : null;
    }

    private static boolean isCatalystOwner(@Nullable RuleValue owner, String path) {
        if (owner == null || !owner.isMapping()) return false;
        String type = RuleEditorPageSupport.stringField(owner, RuleFields.TYPE);
        return RuleFields.CATALYST_COST.equals(path)
                || RuleEditorPageSupport.CATALYST_PRESENT_ID.toString().equals(type)
                || RuleEditorPageSupport.CONSUME_CATALYST_ID.toString().equals(type);
    }

    private static void collectCatalysts(RuleValue value, Set<String> out) {
        if (value.isMapping()) {
            if (isCatalystOwner(value, "")) out.addAll(references(value.child(RuleFields.CATALYST_ITEMS)));
            for (Map.Entry<String, RuleValue> entry : value.entries().entrySet()) {
                if (RuleFields.CATALYST_COST.equals(entry.getKey()))
                    out.addAll(references(entry.getValue().child(RuleFields.CATALYST_ITEMS)));
                collectCatalysts(entry.getValue(), out);
            }
        } else if (value.isList()) {
            for (RuleValue child : value.items()) collectCatalysts(child, out);
        }
    }

    private static List<String> references(@Nullable RuleValue value) {
        return value == null || !value.isList() ? List.of() : value.textItems();
    }

    private Set<String> expand(List<String> references) {
        Set<String> result = new HashSet<>();
        for (String reference : references) result.addAll(members(reference));
        return result;
    }

    private Set<String> members(String reference) {
        return members.computeIfAbsent(reference, raw -> {
            boolean tag = raw.startsWith("#");
            ResourceLocation id = ResourceLocation.tryParse(tag ? raw.substring(1) : raw);
            if (id == null) return Set.of();
            if (!tag) return Set.of(id.toString());
            return BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id))
                    .map(holders -> {
                        Set<String> ids = new HashSet<>();
                        holders.forEach(holder -> ids.add(BuiltInRegistries.ITEM.getKey(holder.value()).toString()));
                        return Set.copyOf(ids);
                    }).orElse(Set.of());
        });
    }
}
