package com.meteorite.itemdespawntowhat.client.ui.shared;

import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.core.api.param.SelectionIntent;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Registry;

/** 用客户端已同步的真实标签归属过滤字段选择，不依赖目录的首次命中说明。 */
public final class FieldCatalogChoices {
    private FieldCatalogChoices() { }

    public static Set<String> tags(EditorField field) {
        var type = FieldCatalogTypes.catalogTypeOf(field);
        if (type == null) return Set.of();
        Registry<?> registry = CatalogTypePresentation.of(type).tagRegistry();
        if (registry == null) return Set.of();
        return tags(registry, field.selectionIntent() == SelectionIntent.ENTITY_TYPES_EXCLUDING_PRODUCTS);
    }

    private static <T> Set<String> tags(Registry<T> registry, boolean excludeProducts) {
        return registry.getTagNames().filter(tag -> !excludeProducts || registry.getTag(tag).map(holders ->
                        holders.stream().noneMatch(holder -> holder.value() == net.minecraft.world.entity.EntityType.ITEM
                                || holder.value() == net.minecraft.world.entity.EntityType.EXPERIENCE_ORB))
                        .orElse(false)).map(tag -> "#" + tag.location()).collect(Collectors.toSet());
    }
}
