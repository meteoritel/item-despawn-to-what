package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;

/*** 用客户端已同步的真实标签归属过滤字段选择，不依赖目录的首次命中说明。 */
public final class FieldCatalogChoices {
    private FieldCatalogChoices() { }

    public static Set<String> tags(EditorField field) {
        var type = RuleEditorP4Panels.catalogTypeOf(field);
        if (type == null) return Set.of();
        var level = Minecraft.getInstance().level;
        return switch (type) {
            case ITEM -> tags(BuiltInRegistries.ITEM, false);
            case BLOCK -> tags(BuiltInRegistries.BLOCK, false);
            case ENTITY -> tags(BuiltInRegistries.ENTITY_TYPE, isGenericProduct(field));
            case FLUID -> tags(BuiltInRegistries.FLUID, false);
            case BIOME -> level == null ? Set.of()
                    : tags(level.registryAccess().registryOrThrow(Registries.BIOME), false);
            default -> Set.of();
        };
    }

    public static boolean isGenericProduct(EditorField field) {
        return field.labelKey().equals("gui.itemdespawntowhat.edit.field.spawn_entity.entity");
    }

    private static <T> Set<String> tags(Registry<T> registry, boolean genericProduct) {
        return registry.getTagNames().filter(tag -> !genericProduct || registry.getTag(tag).map(holders ->
                        holders.stream().noneMatch(holder -> holder.value() == EntityType.ITEM || holder.value() == EntityType.EXPERIENCE_ORB))
                        .orElse(false)).map(tag -> "#" + tag.location()).collect(Collectors.toSet());
    }
}
