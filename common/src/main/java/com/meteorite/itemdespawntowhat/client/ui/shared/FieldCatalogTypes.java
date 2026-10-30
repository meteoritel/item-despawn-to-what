package com.meteorite.itemdespawntowhat.client.ui.shared;

import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalogType;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** 按字段显式声明的 registry 精确解析目录类型。 */
public final class FieldCatalogTypes {
    private static final Map<String, RuleCatalogType> REGISTRIES = Map.ofEntries(
            Map.entry("minecraft:item", RuleCatalogType.ITEM),
            Map.entry("item", RuleCatalogType.ITEM),
            Map.entry("items", RuleCatalogType.ITEM),
            Map.entry("minecraft:block", RuleCatalogType.BLOCK),
            Map.entry("block", RuleCatalogType.BLOCK),
            Map.entry("blocks", RuleCatalogType.BLOCK),
            Map.entry("minecraft:entity_type", RuleCatalogType.ENTITY),
            Map.entry("entity", RuleCatalogType.ENTITY),
            Map.entry("minecraft:loot_table", RuleCatalogType.LOOT_TABLE),
            Map.entry("loot_table", RuleCatalogType.LOOT_TABLE),
            Map.entry("minecraft:worldgen/biome", RuleCatalogType.BIOME),
            Map.entry("biome", RuleCatalogType.BIOME),
            Map.entry("minecraft:dimension", RuleCatalogType.DIMENSION),
            Map.entry("dimension", RuleCatalogType.DIMENSION),
            Map.entry("level", RuleCatalogType.DIMENSION),
            Map.entry("minecraft:fluid", RuleCatalogType.FLUID),
            Map.entry("fluid", RuleCatalogType.FLUID),
            Map.entry("minecraft:mob_effect", RuleCatalogType.MOB_EFFECT),
            Map.entry("mob_effect", RuleCatalogType.MOB_EFFECT),
            Map.entry("potion", RuleCatalogType.MOB_EFFECT));

    private FieldCatalogTypes() { }

    /** 返回字段声明的目录类型；未知或未声明 registry 时返回 null。 */
    public static @Nullable RuleCatalogType catalogTypeOf(EditorField field) {
        String registry = field.registry();
        return registry == null ? null : REGISTRIES.get(registry);
    }
}
