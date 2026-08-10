package com.meteorite.itemdespawntowhat.config.type;

import com.google.common.reflect.TypeToken;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.config.ConfigDirection;
import com.meteorite.itemdespawntowhat.config.catalogue.SurroundingBlocks;
import com.meteorite.itemdespawntowhat.config.conversion.*;
import com.meteorite.itemdespawntowhat.config.execution.*;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 可由第三方扩展的转换类型注册表。
 */
public final class ConversionTypeRegistry {
    private static final Map<ResourceLocation, ConversionType> TYPES = new LinkedHashMap<>();

    static {
        registerBuiltin("item_to_item",
                new TypeToken<List<ItemToItemConfig>>() { }.getType(), ConversionTypeRegistry::createItemToItemDefaults);
        registerBuiltin("item_to_mob",
                new TypeToken<List<ItemToMobConfig>>() { }.getType(), ConversionTypeRegistry::createItemToMobDefaults);
        registerBuiltin("item_to_block",
                new TypeToken<List<ItemToBlockConfig>>() { }.getType(), ConversionTypeRegistry::createItemToBlockDefaults);
        registerBuiltin("item_to_xp_orb",
                new TypeToken<List<ItemToExpOrbConfig>>() { }.getType(), Collections::emptyList);
        registerBuiltin("item_to_world_effect",
                new TypeToken<List<ItemToWorldEffectConfig>>() { }.getType(), Collections::emptyList);
    }

    private ConversionTypeRegistry() {
    }

    private static <T extends BaseConversionConfig> ConversionType registerBuiltin(String path, java.lang.reflect.Type listType,
                                                                                    java.util.function.Supplier<List<T>> defaults) {
        ConversionExecutorSelector selector = new ConversionExecutorSelector();
        return register(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path),
                new ConversionTypeDefinition<>(listType, defaults, selector.executor(path)));
    }

    private static final class ConversionExecutorSelector {
        @SuppressWarnings({"unchecked", "rawtypes"})
        <T extends BaseConversionConfig> ConversionExecutor<? super T> executor(String path) {
            return switch (path) {
                case "item_to_item" -> (ConversionExecutor) new ItemToItemExecutor();
                case "item_to_mob" -> (ConversionExecutor) new ItemToMobExecutor();
                case "item_to_block" -> (ConversionExecutor) new ItemToBlockExecutor();
                case "item_to_xp_orb" -> (ConversionExecutor) new ItemToExpOrbExecutor();
                case "item_to_world_effect" -> (ConversionExecutor) new ItemToWorldEffectExecutor();
                default -> throw new IllegalArgumentException("Unknown builtin conversion type: " + path);
            };
        }
    }

    public static synchronized <T extends BaseConversionConfig> ConversionType register(
            ResourceLocation id, ConversionTypeDefinition<T> definition) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(definition, "definition");
        if (TYPES.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate conversion type: " + id);
        }
        ConversionType type = new ConversionType(id, definition);
        definition.bindType(type);
        TYPES.put(id, type);
        return type;
    }

    public static synchronized List<ConversionType> all() {
        return List.copyOf(TYPES.values());
    }

    public static synchronized ConversionType byId(ResourceLocation id) {
        return TYPES.get(id);
    }

    public static ConversionType byId(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        return parsed == null ? null : byId(parsed);
    }

    public static ConversionType require(ResourceLocation id) {
        ConversionType type = byId(id);
        if (type == null) {
            throw new IllegalArgumentException("Unknown conversion type: " + id);
        }
        return type;
    }

    @SuppressWarnings("unchecked")
    public static <T extends BaseConversionConfig> ConversionTypeDefinition<T> get(ConversionType type) {
        if (type == null || type.definition() == null) {
            throw new IllegalArgumentException("Unknown conversion type: " + type);
        }
        return (ConversionTypeDefinition<T>) type.definition();
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
