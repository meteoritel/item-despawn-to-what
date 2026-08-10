package com.meteorite.itemdespawntowhat.client.register;

import com.google.common.reflect.TypeToken;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.client.ui.form.BuiltinFormDefinitions;
import com.meteorite.itemdespawntowhat.client.ui.form.FormDefinitionFactory;
import com.meteorite.itemdespawntowhat.client.ui.presentation.BuiltinConfigPresentations;
import com.meteorite.itemdespawntowhat.client.ui.presentation.ConfigPresenter;
import com.meteorite.itemdespawntowhat.client.ui.presentation.ConfigTooltipProvider;
import com.meteorite.itemdespawntowhat.client.ui.screen.BaseConfigEditScreen;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToBlockConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToExpOrbConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToItemConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToMobConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToWorldEffectConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 客户端转换类型注册表，独立于服务端转换注册表。
 */
public final class ClientConversionTypeRegistry {
    private static final Map<ResourceLocation, ClientConversionTypeDefinition<?>> TYPES = new LinkedHashMap<>();

    static {
        registerBuiltin(id("item_to_item"), new TypeToken<List<ItemToItemConfig>>() { }.getType(),
                ItemToItemConfig::new, BuiltinFormDefinitions::itemToItem,
                BuiltinConfigPresentations::item, BuiltinConfigPresentations::noExtraTooltip);
        registerBuiltin(id("item_to_mob"), new TypeToken<List<ItemToMobConfig>>() { }.getType(),
                ItemToMobConfig::new, BuiltinFormDefinitions::itemToMob,
                BuiltinConfigPresentations::mob, BuiltinConfigPresentations::noExtraTooltip);
        registerBuiltin(id("item_to_block"), new TypeToken<List<ItemToBlockConfig>>() { }.getType(),
                ItemToBlockConfig::new, BuiltinFormDefinitions::itemToBlock,
                BuiltinConfigPresentations::block, BuiltinConfigPresentations::appendBlockTooltip);
        registerBuiltin(id("item_to_xp_orb"), new TypeToken<List<ItemToExpOrbConfig>>() { }.getType(),
                ItemToExpOrbConfig::new, BuiltinFormDefinitions::itemToExperience,
                BuiltinConfigPresentations::experience, BuiltinConfigPresentations::noExtraTooltip);
        registerBuiltin(id("item_to_world_effect"), new TypeToken<List<ItemToWorldEffectConfig>>() { }.getType(),
                ItemToWorldEffectConfig::new, BuiltinFormDefinitions::itemToWorldEffect,
                BuiltinConfigPresentations::worldEffect, BuiltinConfigPresentations::appendWorldEffectTooltip);
    }

    private ClientConversionTypeRegistry() {
    }

    public static synchronized <T extends BaseConversionConfig> void register(
            ResourceLocation id,
            Type listType,
            Supplier<T> configFactory,
            FormDefinitionFactory<T> formDefinitionFactory,
            ConfigPresenter<T> presenter,
            ConfigTooltipProvider<T> tooltipProvider) {
        register(id, listType, configFactory, formDefinitionFactory,
                definition -> new BaseConfigEditScreen<>(definition), presenter, tooltipProvider);
    }

    public static synchronized <T extends BaseConversionConfig> void register(
            ResourceLocation id,
            Type listType,
            Supplier<T> configFactory,
            FormDefinitionFactory<T> formDefinitionFactory,
            Function<ClientConversionTypeDefinition<T>, ? extends Screen> screenFactory,
            ConfigPresenter<T> presenter,
            ConfigTooltipProvider<T> tooltipProvider) {
        Objects.requireNonNull(id, "id");
        if (TYPES.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate client conversion type: " + id);
        }
        TYPES.put(id, new ClientConversionTypeDefinition<>(id, listType, configFactory, formDefinitionFactory,
                screenFactory, presenter, tooltipProvider));
    }

    private static <T extends BaseConversionConfig> void registerBuiltin(
            ResourceLocation id,
            Type listType,
            Supplier<T> configFactory,
            FormDefinitionFactory<T> formDefinitionFactory,
            ConfigPresenter<T> presenter,
            ConfigTooltipProvider<T> tooltipProvider) {
        register(id, listType, configFactory, formDefinitionFactory, presenter, tooltipProvider);
    }

    public static synchronized ClientConversionTypeDefinition<?> byId(ResourceLocation id) {
        return TYPES.get(id);
    }

    public static synchronized boolean contains(ResourceLocation id) {
        return TYPES.containsKey(id);
    }

    public static synchronized List<ClientConversionTypeDefinition<?>> all() {
        return List.copyOf(TYPES.values());
    }

    @SuppressWarnings("unchecked")
    public static <T extends BaseConversionConfig> ConfigPresenter<T> presenter(ResourceLocation id) {
        ClientConversionTypeDefinition<?> definition = require(id);
        return (ConfigPresenter<T>) definition.presenter();
    }

    @SuppressWarnings("unchecked")
    public static <T extends BaseConversionConfig> void appendTooltip(
            ResourceLocation id, T config, MutableComponent tooltip) {
        ClientConversionTypeDefinition<T> definition = (ClientConversionTypeDefinition<T>) require(id);
        definition.tooltipProvider().append(config, tooltip);
    }

    public static Screen createScreen(ResourceLocation id) {
        return require(id).createScreen();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path);
    }

    private static ClientConversionTypeDefinition<?> require(ResourceLocation id) {
        ClientConversionTypeDefinition<?> definition = byId(id);
        if (definition == null) {
            throw new IllegalArgumentException("No client conversion type registered for: " + id);
        }
        return definition;
    }

}
