package com.meteorite.itemdespawntowhat.client.ui.shared;

import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalogType;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/** 字段到目录类型的映射：注册表提示优先，其后按字段名兜底，只覆盖能落到目录面板的类型。 */
public final class FieldCatalogTypes {

    private FieldCatalogTypes() { }

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
}
