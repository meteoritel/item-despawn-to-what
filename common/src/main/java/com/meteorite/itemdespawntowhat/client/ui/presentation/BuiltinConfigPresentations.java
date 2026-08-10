package com.meteorite.itemdespawntowhat.client.ui.presentation;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToArrowRainConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToBlockConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToExpOrbConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToExplosionConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToItemConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToLightningConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToLootConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToMobConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToWeatherConfig;
import com.meteorite.itemdespawntowhat.server.task.ExplosionTask;
import com.meteorite.itemdespawntowhat.server.task.PlaceBlockTask.BlockPlaceShape;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import com.meteorite.itemdespawntowhat.util.TagResolver;
import net.minecraft.core.registries.Registries;
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

/**
 * 提供五种内置转换类型各自的列表展示、摘要和专属 tooltip。
 */
public final class BuiltinConfigPresentations {
    private BuiltinConfigPresentations() {
    }

    public static ConfigPresentation item(ItemToItemConfig config, ItemStack sourceIcon) {
        Item item = findItem(config.getResultId());
        ItemStack resultIcon = item.getDefaultInstance();
        Component resultName = displayName(resultIcon);
        return new ConfigPresentation(resultIcon.isEmpty() ? barrierStack() : resultIcon,
                resultName, null, summary(config, sourceIcon, resultName));
    }

    public static ConfigPresentation mob(ItemToMobConfig config, ItemStack sourceIcon) {
        EntityType<?> entityType = findEntityType(config.getResultId());
        if (entityType == null) {
            return barrier(config, sourceIcon);
        }
        SpawnEggItem spawnEgg = SpawnEggItem.byId(entityType);
        ItemStack icon = spawnEgg == null ? barrierStack() : spawnEgg.getDefaultInstance();
        Component resultName = Component.translatable(entityType.getDescriptionId());
        return new ConfigPresentation(icon, resultName, entityType,
                summary(config, sourceIcon, resultName));
    }

    public static ConfigPresentation block(ItemToBlockConfig config, ItemStack sourceIcon) {
        Component resultName;
        ItemStack resultIcon;
        if (config.isEnableItemBlock()) {
            resultIcon = sourceIcon;
            resultName = displayName(sourceIcon);
        } else {
            Block block = findBlock(config.getResultId());
            resultIcon = block.asItem().getDefaultInstance();
            resultName = Component.translatable(block.getDescriptionId());
        }
        return new ConfigPresentation(resultIcon.isEmpty() ? barrierStack() : resultIcon,
                resultName, null, summary(config, sourceIcon, resultName));
    }

    public static ConfigPresentation experience(ItemToExpOrbConfig config, ItemStack sourceIcon) {
        Component resultName = Component.translatable("entity.minecraft.experience_orb");
        Component summary = Component.translatable("gui.itemdespawntowhat.summary.experience",
                displayName(sourceIcon), config.getSourceMultiple(), config.getXpPerItem());
        return new ConfigPresentation(new ItemStack(Items.EXPERIENCE_BOTTLE), resultName, null, summary);
    }

    public static ConfigPresentation lightning(ItemToLightningConfig config, ItemStack sourceIcon) {
        return worldEffect(config, sourceIcon, Items.LIGHTNING_ROD,
                Component.translatable("effect.itemdespawntowhat.world_effect_type.lightning_bolt"));
    }

    public static ConfigPresentation explosion(ItemToExplosionConfig config, ItemStack sourceIcon) {
        return worldEffect(config, sourceIcon, Items.TNT,
                Component.translatable("effect.itemdespawntowhat.world_effect_type.explosion"));
    }

    public static ConfigPresentation arrowRain(ItemToArrowRainConfig config, ItemStack sourceIcon) {
        return worldEffect(config, sourceIcon, Items.ARROW,
                Component.translatable("effect.itemdespawntowhat.world_effect_type.arrow"));
    }

    public static ConfigPresentation weather(ItemToWeatherConfig config, ItemStack sourceIcon) {
        boolean rain = config.getWeatherMode() == ItemToWeatherConfig.WeatherMode.RAIN;
        return worldEffect(config, sourceIcon, rain ? Items.WATER_BUCKET : Items.SUNFLOWER,
                Component.translatable(config.getWeatherMode().getDescriptionId()));
    }

    public static ConfigPresentation loot(ItemToLootConfig config, ItemStack sourceIcon) {
        Component resultName = Component.literal(config.getResultId());
        return new ConfigPresentation(Items.CHEST.getDefaultInstance(), resultName, null,
                summary(config, sourceIcon, resultName));
    }

    private static ConfigPresentation worldEffect(
            BaseConversionConfig config, ItemStack sourceIcon, Item iconItem, Component resultName) {
        Component summary = Component.translatable("gui.itemdespawntowhat.summary.world_effect",
                displayName(sourceIcon), config.getSourceMultiple(), resultName);
        return new ConfigPresentation(iconItem.getDefaultInstance(), resultName, null, summary);
    }

    public static void noExtraTooltip(BaseConversionConfig config, MutableComponent tooltip) {
    }

    public static void appendBlockTooltip(ItemToBlockConfig config, MutableComponent tooltip) {
        BlockPlaceShape shape = config.getBlockPlaceShape();
        tooltip.append(Component.literal("\n"))
                .append(Component.translatable("gui.itemdespawntowhat.tooltip.block_place_shape",
                        Component.translatable(shape.getDescriptionId())));
    }

    public static void appendExplosionTooltip(ItemToExplosionConfig config, MutableComponent tooltip) {
        ExplosionTask.DirectionType direction = config.getExplosionDirectionType();
        tooltip.append(Component.literal("\n"))
                .append(Component.translatable("gui.itemdespawntowhat.tooltip.explosion_direction",
                        Component.translatable(direction.getDescriptionId())));
    }

    public static Component displayName(ItemStack stack) {
        return stack.isEmpty() ? Component.empty() : Component.translatable(stack.getDescriptionId());
    }

    private static Component summary(BaseConversionConfig config, ItemStack sourceIcon, Component resultName) {
        return Component.translatable("gui.itemdespawntowhat.summary.conversion",
                displayName(sourceIcon), config.getSourceMultiple(), resultName, config.getResultMultiple());
    }

    private static Item findItem(String id) {
        if (TagResolver.isTagId(id)) {
            return TagResolver.resolveTagItems(BuiltInRegistries.ITEM, Registries.ITEM, id)
                    .stream().findFirst().orElse(Items.AIR);
        }
        ResourceLocation location = SafeParseUtil.parseResourceLocation(id);
        return location == null ? Items.AIR : BuiltInRegistries.ITEM.get(location);
    }

    private static Block findBlock(String id) {
        if (TagResolver.isTagId(id)) {
            return TagResolver.resolveTagItems(BuiltInRegistries.BLOCK, Registries.BLOCK, id)
                    .stream().findFirst().orElse(Blocks.AIR);
        }
        ResourceLocation location = SafeParseUtil.parseResourceLocation(id);
        return location == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(location);
    }

    private static EntityType<?> findEntityType(String id) {
        if (TagResolver.isTagId(id)) {
            return TagResolver.resolveTagItems(BuiltInRegistries.ENTITY_TYPE, Registries.ENTITY_TYPE, id)
                    .stream().findFirst().orElse(null);
        }
        ResourceLocation location = SafeParseUtil.parseResourceLocation(id);
        return location == null ? null : BuiltInRegistries.ENTITY_TYPE.get(location);
    }

    private static ConfigPresentation barrier(BaseConversionConfig config, ItemStack sourceIcon) {
        ItemStack icon = barrierStack();
        Component name = displayName(icon);
        return new ConfigPresentation(icon, name, null, summary(config, sourceIcon, name));
    }

    private static ItemStack barrierStack() {
        return new ItemStack(Items.BARRIER);
    }
}
