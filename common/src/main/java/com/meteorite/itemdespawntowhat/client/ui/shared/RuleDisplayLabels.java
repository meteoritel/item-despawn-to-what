package com.meteorite.itemdespawntowhat.client.ui.shared;

import com.meteorite.itemdespawntowhat.client.edit.ConditionEditorRegistry;
import com.meteorite.itemdespawntowhat.client.edit.EffectEditorRegistry;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/*** 客户端展示名称：直接读取已加载的注册表与语言，不触发目录请求。 */
public final class RuleDisplayLabels {
    private RuleDisplayLabels() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 名称查询均为注册表索引查找；未知资源交给调用方保留原始 ID。
    public static @Nullable Component label(ResourceLocation id) {
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            return BuiltInRegistries.ITEM.get(id).getDescription();
        }
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            return BuiltInRegistries.BLOCK.get(id).getName();
        }
        if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return BuiltInRegistries.ENTITY_TYPE.get(id).getDescription();
        }
        TypeEditorDescriptor descriptor = EffectEditorRegistry.find(id);
        if (descriptor == null) {
            descriptor = ConditionEditorRegistry.find(id);
        }
        return descriptor == null ? null : descriptor.label();
    }

    // 按目录类型查询，避免同名的物品与维度、群系等资源被显示成错误名称。
    public static @Nullable Component catalogLabel(RuleCatalogType type, ResourceLocation id) {
        return switch (type) {
            case ITEM -> BuiltInRegistries.ITEM.getOptional(id).map(Item::getDescription).orElse(null);
            case BLOCK -> BuiltInRegistries.BLOCK.getOptional(id).map(Block::getName).orElse(null);
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(EntityType::getDescription).orElse(null);
            case FLUID -> FluidPreviewIcons.label(id);
            case MOB_EFFECT -> BuiltInRegistries.MOB_EFFECT.getOptional(id).map(MobEffect::getDisplayName).orElse(null);
            case DIMENSION, BIOME -> {
                String key = type.name().toLowerCase(Locale.ROOT) + "." + id.getNamespace() + "."
                        + id.getPath().replace('/', '.');
                yield Language.getInstance().has(key) ? Component.translatable(key) : null;
            }
            default -> null;
        };
    }
}
