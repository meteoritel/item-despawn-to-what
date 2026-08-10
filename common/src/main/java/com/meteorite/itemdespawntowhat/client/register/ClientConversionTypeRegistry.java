package com.meteorite.itemdespawntowhat.client.register;

import com.google.common.reflect.TypeToken;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.client.ui.form.ConfigFormSection;
import com.meteorite.itemdespawntowhat.client.ui.form.section.ItemToBlockFormSection;
import com.meteorite.itemdespawntowhat.client.ui.form.section.ItemToWorldEffectFormSection;
import com.meteorite.itemdespawntowhat.client.ui.presentation.ConfigPresentation;
import com.meteorite.itemdespawntowhat.client.ui.presentation.ConfigPresenter;
import com.meteorite.itemdespawntowhat.client.ui.presentation.ConfigTooltipProvider;
import com.meteorite.itemdespawntowhat.client.ui.schema.BuiltinConfigSchemas;
import com.meteorite.itemdespawntowhat.client.ui.screen.BaseConfigEditScreen;
import com.meteorite.itemdespawntowhat.config.WorldEffectType;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToBlockConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToExpOrbConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToItemConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToMobConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToWorldEffectConfig;
import com.meteorite.itemdespawntowhat.server.task.ExplosionTask;
import com.meteorite.itemdespawntowhat.server.task.PlaceBlockTask.BlockPlaceShape;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

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
                ItemToItemConfig::new, BuiltinConfigSchemas::itemToItem,
                ClientConversionTypeRegistry::presentItem, ClientConversionTypeRegistry::noExtraTooltip);
        registerBuiltin(id("item_to_mob"), new TypeToken<List<ItemToMobConfig>>() { }.getType(),
                ItemToMobConfig::new, BuiltinConfigSchemas::itemToMob,
                ClientConversionTypeRegistry::presentMob, ClientConversionTypeRegistry::noExtraTooltip);
        registerBuiltin(id("item_to_block"), new TypeToken<List<ItemToBlockConfig>>() { }.getType(),
                ItemToBlockConfig::new, ItemToBlockFormSection::new,
                ClientConversionTypeRegistry::presentBlock, ClientConversionTypeRegistry::appendBlockTooltip);
        registerBuiltin(id("item_to_xp_orb"), new TypeToken<List<ItemToExpOrbConfig>>() { }.getType(),
                ItemToExpOrbConfig::new, BuiltinConfigSchemas::itemToExperience,
                ClientConversionTypeRegistry::presentExperience, ClientConversionTypeRegistry::noExtraTooltip);
        registerBuiltin(id("item_to_world_effect"), new TypeToken<List<ItemToWorldEffectConfig>>() { }.getType(),
                ItemToWorldEffectConfig::new, ItemToWorldEffectFormSection::new,
                ClientConversionTypeRegistry::presentWorldEffect, ClientConversionTypeRegistry::appendWorldEffectTooltip);
    }

    private ClientConversionTypeRegistry() {
    }

    public static synchronized <T extends BaseConversionConfig> void register(
            ResourceLocation id,
            Type listType,
            Supplier<T> configFactory,
            Supplier<? extends ConfigFormSection<T>> formSectionFactory,
            ConfigPresenter<T> presenter,
            ConfigTooltipProvider<T> tooltipProvider) {
        register(id, listType, configFactory, formSectionFactory,
                definition -> new BaseConfigEditScreen<>(definition), presenter, tooltipProvider);
    }

    public static synchronized <T extends BaseConversionConfig> void register(
            ResourceLocation id,
            Type listType,
            Supplier<T> configFactory,
            Supplier<? extends ConfigFormSection<T>> formSectionFactory,
            Function<ClientConversionTypeDefinition<T>, ? extends Screen> screenFactory,
            ConfigPresenter<T> presenter,
            ConfigTooltipProvider<T> tooltipProvider) {
        Objects.requireNonNull(id, "id");
        if (TYPES.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate client conversion type: " + id);
        }
        TYPES.put(id, new ClientConversionTypeDefinition<>(id, listType, configFactory, formSectionFactory,
                screenFactory, presenter, tooltipProvider));
    }

    private static <T extends BaseConversionConfig> void registerBuiltin(
            ResourceLocation id,
            Type listType,
            Supplier<T> configFactory,
            Supplier<? extends ConfigFormSection<T>> formSectionFactory,
            ConfigPresenter<T> presenter,
            ConfigTooltipProvider<T> tooltipProvider) {
        register(id, listType, configFactory, formSectionFactory, presenter, tooltipProvider);
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

    private static void noExtraTooltip(BaseConversionConfig config, MutableComponent tooltip) {
    }

    private static ConfigPresentation presentItem(ItemToItemConfig config, ItemStack sourceIcon) {
        Item item = findItem(config.getResultId());
        return new ConfigPresentation(item.getDefaultInstance(), Component.translatable(item.getDescriptionId()), null);
    }

    private static ConfigPresentation presentMob(ItemToMobConfig config, ItemStack sourceIcon) {
        EntityType<?> entityType = findEntityType(config.getResultId());
        if (entityType == null) {
            return barrier();
        }
        SpawnEggItem spawnEgg = SpawnEggItem.byId(entityType);
        ItemStack icon = spawnEgg == null ? new ItemStack(Items.BARRIER) : spawnEgg.getDefaultInstance();
        return new ConfigPresentation(icon, Component.translatable(entityType.getDescriptionId()), entityType);
    }

    private static ConfigPresentation presentBlock(ItemToBlockConfig config, ItemStack sourceIcon) {
        if (config.isEnableItemBlock()) {
            return new ConfigPresentation(sourceIcon, displayName(sourceIcon), null);
        }
        Block block = findBlock(config.getResultId());
        ItemStack icon = block.asItem().getDefaultInstance();
        return new ConfigPresentation(icon.isEmpty() ? new ItemStack(Items.BARRIER) : icon,
                Component.translatable(block.getDescriptionId()), null);
    }

    private static ConfigPresentation presentExperience(ItemToExpOrbConfig config, ItemStack sourceIcon) {
        return new ConfigPresentation(new ItemStack(Items.EXPERIENCE_BOTTLE),
                Component.translatable("entity.minecraft.experience_orb"), null);
    }

    private static ConfigPresentation presentWorldEffect(ItemToWorldEffectConfig config, ItemStack sourceIcon) {
        WorldEffectType effect = config.getWorldEffect();
        if (effect == null) {
            return barrier();
        }
        Item iconItem = switch (effect) {
            case RAIN -> Items.WATER_BUCKET;
            case CLEAR -> Items.SUNFLOWER;
            case LIGHTNING -> Items.LIGHTNING_ROD;
            case EXPLOSION -> Items.TNT;
            case ARROW_RAIN -> Items.ARROW;
        };
        return new ConfigPresentation(iconItem.getDefaultInstance(),
                Component.translatable(effect.getDescriptionId()), null);
    }

    private static void appendBlockTooltip(ItemToBlockConfig config, MutableComponent tooltip) {
        BlockPlaceShape shape = config.getBlockPlaceShape();
        tooltip.append(Component.literal("\n"))
                .append(Component.translatable("gui.itemdespawntowhat.tooltip.block_place_shape",
                        Component.translatable(shape.getDescriptionId())));
    }

    private static void appendWorldEffectTooltip(ItemToWorldEffectConfig config, MutableComponent tooltip) {
        if (config.getWorldEffect() == WorldEffectType.EXPLOSION) {
            ExplosionTask.DirectionType direction = config.getExplosionDirectionType();
            tooltip.append(Component.literal("\n"))
                    .append(Component.translatable("gui.itemdespawntowhat.tooltip.explosion_direction",
                            Component.translatable(direction.getDescriptionId())));
        }
    }

    public static Component displayName(ItemStack stack) {
        return stack.isEmpty() ? Component.empty() : Component.translatable(stack.getDescriptionId());
    }

    private static Item findItem(String id) {
        ResourceLocation location = SafeParseUtil.parseResourceLocation(id);
        return location == null ? Items.AIR : BuiltInRegistries.ITEM.get(location);
    }

    private static Block findBlock(String id) {
        ResourceLocation location = SafeParseUtil.parseResourceLocation(id);
        return location == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(location);
    }

    private static EntityType<?> findEntityType(String id) {
        ResourceLocation location = SafeParseUtil.parseResourceLocation(id);
        return location == null ? null : BuiltInRegistries.ENTITY_TYPE.get(location);
    }

    private static ConfigPresentation barrier() {
        ItemStack icon = new ItemStack(Items.BARRIER);
        return new ConfigPresentation(icon, displayName(icon), null);
    }
}
