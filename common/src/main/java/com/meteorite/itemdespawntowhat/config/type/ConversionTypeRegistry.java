package com.meteorite.itemdespawntowhat.config.type;

import com.google.common.reflect.TypeToken;
import com.meteorite.itemdespawntowhat.config.ConfigDirection;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.catalogue.SurroundingBlocks;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToBlockConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToExpOrbConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToItemConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToMobConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToWorldEffectConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 保存所有服务端配置类型定义的唯一注册表。
 */
public final class ConversionTypeRegistry {
    private static final Map<ConfigType, ConversionTypeDefinition<?>> DEFINITIONS = createDefinitions();

    private ConversionTypeRegistry() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static List<ConversionTypeDefinition<?>> all() {
        return List.copyOf(DEFINITIONS.values());
    }

    @SuppressWarnings("unchecked")
    public static <T extends BaseConversionConfig> ConversionTypeDefinition<T> get(ConfigType type) {
        ConversionTypeDefinition<?> definition = DEFINITIONS.get(type);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown config type: " + type);
        }
        return (ConversionTypeDefinition<T>) definition;
    }

    private static Map<ConfigType, ConversionTypeDefinition<?>> createDefinitions() {
        Map<ConfigType, ConversionTypeDefinition<?>> definitions = new EnumMap<>(ConfigType.class);
        register(definitions, new ConversionTypeDefinition<>(
                ConfigType.ITEM_TO_ITEM,
                new TypeToken<List<ItemToItemConfig>>() { }.getType(),
                ConversionTypeRegistry::createItemToItemDefaults));
        register(definitions, new ConversionTypeDefinition<>(
                ConfigType.ITEM_TO_MOB,
                new TypeToken<List<ItemToMobConfig>>() { }.getType(),
                ConversionTypeRegistry::createItemToMobDefaults));
        register(definitions, new ConversionTypeDefinition<>(
                ConfigType.ITEM_TO_BLOCK,
                new TypeToken<List<ItemToBlockConfig>>() { }.getType(),
                ConversionTypeRegistry::createItemToBlockDefaults));
        register(definitions, new ConversionTypeDefinition<>(
                ConfigType.ITEM_TO_XP_ORB,
                new TypeToken<List<ItemToExpOrbConfig>>() { }.getType(),
                Collections::emptyList));
        register(definitions, new ConversionTypeDefinition<>(
                ConfigType.ITEM_TO_WORLD_EFFECT,
                new TypeToken<List<ItemToWorldEffectConfig>>() { }.getType(),
                Collections::emptyList));
        return Collections.unmodifiableMap(definitions);
    }

    private static void register(Map<ConfigType, ConversionTypeDefinition<?>> definitions,
                                 ConversionTypeDefinition<?> definition) {
        if (definitions.put(definition.type(), definition) != null) {
            throw new IllegalStateException("Duplicate config type: " + definition.type());
        }
    }

    private static List<ItemToItemConfig> createItemToItemDefaults() {
        ItemToItemConfig entry = new ItemToItemConfig("minecraft:chicken", "minecraft:rotten_flesh");
        entry.setConversionTime(200);
        return new ArrayList<>(List.of(entry));
    }

    private static List<ItemToMobConfig> createItemToMobDefaults() {
        ItemToMobConfig entry = new ItemToMobConfig("minecraft:egg", "minecraft:chicken");
        SurroundingBlocks blocks = new SurroundingBlocks();
        blocks.set(ConfigDirection.DOWN, "minecraft:hay_block");
        entry.setEntityAge(-24000);
        entry.setSurroundingBlocks(blocks);
        return new ArrayList<>(List.of(entry));
    }

    private static List<ItemToBlockConfig> createItemToBlockDefaults() {
        ItemToBlockConfig entry = new ItemToBlockConfig();
        entry.setItemId("#minecraft:saplings");
        entry.setEnableItemBlock(true);
        SurroundingBlocks blocks = new SurroundingBlocks();
        blocks.set(ConfigDirection.DOWN, "#minecraft:dirt");
        entry.setSurroundingBlocks(blocks);
        return new ArrayList<>(List.of(entry));
    }
}
