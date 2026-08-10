package com.meteorite.itemdespawntowhat.client.ui.presentation;

import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.WorldEffectType;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToBlockConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToExpOrbConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToItemConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToMobConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToWorldEffectConfig;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 保存各配置类型对应的客户端展示策略。
 */
public final class ConfigPresenterRegistry {
    private static final Map<ConfigType, ConfigPresenter<?>> PRESENTERS = createPresenters();

    private ConfigPresenterRegistry() {
        throw new UnsupportedOperationException("Utility class");
    }

    @SuppressWarnings("unchecked")
    public static <T extends BaseConversionConfig> ConfigPresenter<T> get(ConfigType type) {
        ConfigPresenter<?> presenter = PRESENTERS.get(type);
        if (presenter == null) {
            throw new IllegalArgumentException("No presenter registered for config type: " + type);
        }
        return (ConfigPresenter<T>) presenter;
    }

    private static Map<ConfigType, ConfigPresenter<?>> createPresenters() {
        Map<ConfigType, ConfigPresenter<?>> presenters = new EnumMap<>(ConfigType.class);
        register(presenters, ConfigType.ITEM_TO_ITEM, ConfigPresenterRegistry::presentItem);
        register(presenters, ConfigType.ITEM_TO_MOB, ConfigPresenterRegistry::presentMob);
        register(presenters, ConfigType.ITEM_TO_BLOCK, ConfigPresenterRegistry::presentBlock);
        register(presenters, ConfigType.ITEM_TO_XP_ORB, ConfigPresenterRegistry::presentExperience);
        register(presenters, ConfigType.ITEM_TO_WORLD_EFFECT, ConfigPresenterRegistry::presentWorldEffect);
        return Collections.unmodifiableMap(presenters);
    }

    private static <T extends BaseConversionConfig> void register(
            Map<ConfigType, ConfigPresenter<?>> presenters,
            ConfigType type,
            ConfigPresenter<T> presenter
    ) {
        presenters.put(type, presenter);
    }

    private static ConfigPresentation presentItem(ItemToItemConfig config, ItemStack sourceIcon) {
        Item item = findItem(config.getResultId());
        ItemStack icon = item.getDefaultInstance();
        return new ConfigPresentation(icon, Component.translatable(item.getDescriptionId()), null);
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
        if (icon.isEmpty()) {
            icon = new ItemStack(Items.BARRIER);
        }
        return new ConfigPresentation(icon, Component.translatable(block.getDescriptionId()), null);
    }

    private static ConfigPresentation presentExperience(ItemToExpOrbConfig config, ItemStack sourceIcon) {
        return new ConfigPresentation(
                new ItemStack(Items.EXPERIENCE_BOTTLE),
                Component.translatable("entity.minecraft.experience_orb"),
                null);
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
        return new ConfigPresentation(
                iconItem.getDefaultInstance(),
                Component.translatable(effect.getDescriptionId()),
                null);
    }

    public static Component displayName(ItemStack stack) {
        return stack.isEmpty() ? Component.empty() : Component.translatable(stack.getDescriptionId());
    }

    private static Item findItem(String id) {
        ResourceLocation resourceLocation = SafeParseUtil.parseResourceLocation(id);
        return resourceLocation == null ? Items.AIR : BuiltInRegistries.ITEM.get(resourceLocation);
    }

    private static Block findBlock(String id) {
        ResourceLocation resourceLocation = SafeParseUtil.parseResourceLocation(id);
        return resourceLocation == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(resourceLocation);
    }

    private static EntityType<?> findEntityType(String id) {
        ResourceLocation resourceLocation = SafeParseUtil.parseResourceLocation(id);
        return resourceLocation == null ? null : BuiltInRegistries.ENTITY_TYPE.get(resourceLocation);
    }

    private static ConfigPresentation barrier() {
        ItemStack icon = new ItemStack(Items.BARRIER);
        return new ConfigPresentation(icon, displayName(icon), null);
    }
}
