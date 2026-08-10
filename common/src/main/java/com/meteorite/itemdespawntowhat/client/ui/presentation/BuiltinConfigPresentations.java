package com.meteorite.itemdespawntowhat.client.ui.presentation;

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

    public static ConfigPresentation worldEffect(ItemToWorldEffectConfig config, ItemStack sourceIcon) {
        WorldEffectType effect = config.getWorldEffect();
        if (effect == null) {
            return barrier(config, sourceIcon);
        }
        Item iconItem = switch (effect) {
            case RAIN -> Items.WATER_BUCKET;
            case CLEAR -> Items.SUNFLOWER;
            case LIGHTNING -> Items.LIGHTNING_ROD;
            case EXPLOSION -> Items.TNT;
            case ARROW_RAIN -> Items.ARROW;
        };
        Component resultName = Component.translatable(effect.getDescriptionId());
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

    public static void appendWorldEffectTooltip(ItemToWorldEffectConfig config, MutableComponent tooltip) {
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

    private static Component summary(BaseConversionConfig config, ItemStack sourceIcon, Component resultName) {
        return Component.translatable("gui.itemdespawntowhat.summary.conversion",
                displayName(sourceIcon), config.getSourceMultiple(), resultName, config.getResultMultiple());
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

    private static ConfigPresentation barrier(BaseConversionConfig config, ItemStack sourceIcon) {
        ItemStack icon = barrierStack();
        Component name = displayName(icon);
        return new ConfigPresentation(icon, name, null, summary(config, sourceIcon, name));
    }

    private static ItemStack barrierStack() {
        return new ItemStack(Items.BARRIER);
    }
}
