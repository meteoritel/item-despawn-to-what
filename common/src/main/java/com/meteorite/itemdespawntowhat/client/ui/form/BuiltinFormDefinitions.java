package com.meteorite.itemdespawntowhat.client.ui.form;

import com.meteorite.itemdespawntowhat.client.ui.support.SuggestionProvider;
import com.meteorite.itemdespawntowhat.client.ui.widget.ArrowPotionEffectsWidget;
import com.meteorite.itemdespawntowhat.client.ui.widget.CatalystItemsWidget;
import com.meteorite.itemdespawntowhat.client.ui.widget.InnerFluidWidget;
import com.meteorite.itemdespawntowhat.client.ui.widget.SurroundingBlocksWidget;
import com.meteorite.itemdespawntowhat.config.ConversionLimits;
import com.meteorite.itemdespawntowhat.config.WorldEffectType;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToBlockConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToExpOrbConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToItemConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToMobConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToWorldEffectConfig;
import com.meteorite.itemdespawntowhat.server.task.ExplosionTask;
import com.meteorite.itemdespawntowhat.server.task.PlaceBlockTask.BlockPlaceShape;
import com.meteorite.itemdespawntowhat.util.IdValidator;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * 使用统一字段原语声明五种内置转换配置的编辑表单。
 */
public final class BuiltinFormDefinitions {
    private BuiltinFormDefinitions() {
    }

    public static FormDefinition<ItemToItemConfig> itemToItem(FormFieldContext context) {
        FormDefinition.Builder<ItemToItemConfig> builder = FormDefinition.builder();
        addCommonFields(builder, context, resultField(context, IdValidator::isValidResultId,
                SuggestionProvider.ofRegistry(BuiltInRegistries.ITEM)));
        addPositiveInteger(builder, context, "result_limit", ItemToItemConfig::getResultLimit,
                (config, value) -> config.setResultLimit(value), 30, ConversionLimits.MAX_RESULT_LIMIT);
        return builder.build();
    }

    public static FormDefinition<ItemToMobConfig> itemToMob(FormFieldContext context) {
        FormDefinition.Builder<ItemToMobConfig> builder = FormDefinition.builder();
        addCommonFields(builder, context, resultField(context, IdValidator::isValidEntityId,
                SuggestionProvider.ofMobEntityTypes()));
        addPositiveInteger(builder, context, "result_limit", ItemToMobConfig::getResultLimit,
                (config, value) -> config.setResultLimit(value), 30, ConversionLimits.MAX_RESULT_LIMIT);

        EditBox ageBox = context.integerBox();
        builder.add(FormField.<ItemToMobConfig, String>builder(
                        "entity_age", context.label("result_age"), FormFieldInputs.text(ageBox),
                        config -> Integer.toString(config.getEntityAge()),
                        (config, value) -> config.setEntityAge(SafeParseUtil.parseInt(value, 0)))
                .build());
        return builder.build();
    }

    public static FormDefinition<ItemToExpOrbConfig> itemToExperience(FormFieldContext context) {
        FormDefinition.Builder<ItemToExpOrbConfig> builder = FormDefinition.builder();
        addCommonFields(builder, context, null);
        addPositiveInteger(builder, context, "xp_per_item", ItemToExpOrbConfig::getXpPerItem,
                (config, value) -> config.setXpPerItem(value), 1, ConversionLimits.MAX_XP_PER_ITEM);
        return builder.build();
    }

    public static FormDefinition<ItemToBlockConfig> itemToBlock(FormFieldContext context) {
        FormDefinition.Builder<ItemToBlockConfig> builder = FormDefinition.builder();
        FormField<ItemToBlockConfig> result = resultField(context, IdValidator::isValidBlockId,
                SuggestionProvider.ofRegistry(BuiltInRegistries.BLOCK),
                definition -> !definition.<Boolean>value("block_of_item"), true);
        addCommonFields(builder, context, result);
        addPositiveInteger(builder, context, "radius_limit", ItemToBlockConfig::getRadius,
                (config, value) -> config.setRadius(value), 6, ConversionLimits.MAX_BLOCK_RADIUS);

        CycleButton<BlockPlaceShape> shapeButton = context.enumButton(
                "block_place_shape", List.of(BlockPlaceShape.values()), BlockPlaceShape.SQUARE,
                shape -> Component.translatable(shape.getDescriptionId()));
        builder.add(FormField.<ItemToBlockConfig, BlockPlaceShape>builder(
                        "block_place_shape", context.label("block_place_shape"),
                        FormFieldInputs.cycle(shapeButton, BlockPlaceShape.SQUARE),
                        ItemToBlockConfig::getBlockPlaceShape, ItemToBlockConfig::setBlockPlaceShape)
                .build());

        CycleButton<Boolean> itemBlockButton = context.booleanButton("block_of_item");
        builder.add(FormField.<ItemToBlockConfig, Boolean>builder(
                        "block_of_item", context.label("block_of_item"),
                        FormFieldInputs.cycle(itemBlockButton, false),
                        ItemToBlockConfig::isEnableItemBlock, ItemToBlockConfig::setEnableItemBlock)
                .build());
        return builder.build();
    }

    public static FormDefinition<ItemToWorldEffectConfig> itemToWorldEffect(FormFieldContext context) {
        FormDefinition.Builder<ItemToWorldEffectConfig> builder = FormDefinition.builder();
        addCommonFields(builder, context, null);

        CycleButton<WorldEffectType> effectButton = context.enumButton(
                "world_effect_type", List.of(WorldEffectType.values()), WorldEffectType.RAIN,
                type -> Component.translatable(type.getDescriptionId()));
        builder.add(FormField.<ItemToWorldEffectConfig, WorldEffectType>builder(
                        "world_effect_type", context.label("world_effect_type"),
                        FormFieldInputs.cycle(effectButton, WorldEffectType.RAIN),
                        config -> config.getWorldEffect() == null ? WorldEffectType.RAIN : config.getWorldEffect(),
                        ItemToWorldEffectConfig::setWorldEffect)
                .build());

        addWorldEffectFields(builder, context);
        return builder.build();
    }

    private static void addWorldEffectFields(
            FormDefinition.Builder<ItemToWorldEffectConfig> builder,
            FormFieldContext context) {
        EditBox weatherBox = context.positiveIntegerBox();
        builder.add(FormField.<ItemToWorldEffectConfig, String>builder(
                        "weather_duration_ticks", context.label("weather_duration_ticks"),
                        FormFieldInputs.text(weatherBox),
                        config -> Integer.toString(config.getWeatherDurationTicks()),
                        (config, value) -> config.setWeatherDurationTicks(SafeParseUtil.parseInt(value, 6000)))
                .validateWith(value -> positiveIntegerError(value,
                        ConversionLimits.MAX_WEATHER_DURATION_TICKS, context.label("weather_duration_ticks")))
                .visibleWhen(effectIs(WorldEffectType.RAIN, WorldEffectType.CLEAR))
                .build());

        addBoolean(builder, context, "is_thundering", ItemToWorldEffectConfig::isThundering,
                ItemToWorldEffectConfig::setThundering, effectIs(WorldEffectType.RAIN));
        addBoolean(builder, context, "visual_only", ItemToWorldEffectConfig::isVisualOnly,
                ItemToWorldEffectConfig::setVisualOnly, effectIs(WorldEffectType.LIGHTNING));

        EditBox explosionPowerBox = context.positiveDecimalBox();
        builder.add(FormField.<ItemToWorldEffectConfig, String>builder(
                        "explosion_power", context.label("explosion_power"),
                        FormFieldInputs.text(explosionPowerBox),
                        config -> Float.toString(config.getExplosionPower()),
                        (config, value) -> config.setExplosionPower(SafeParseUtil.parseFloat(value, 1.0f)))
                .validateWith(value -> decimalRangeError(value, 0, ConversionLimits.MAX_EXPLOSION_POWER,
                        context.label("explosion_power")))
                .visibleWhen(effectIs(WorldEffectType.EXPLOSION))
                .build());
        addBoolean(builder, context, "explosion_fire", ItemToWorldEffectConfig::isExplosionFire,
                ItemToWorldEffectConfig::setExplosionFire, effectIs(WorldEffectType.EXPLOSION));

        CycleButton<ExplosionTask.DirectionType> directionButton = context.enumButton(
                "explosion_direction_type", List.of(ExplosionTask.DirectionType.values()),
                ExplosionTask.DirectionType.FLAT,
                direction -> Component.translatable(direction.getDescriptionId()));
        builder.add(FormField.<ItemToWorldEffectConfig, ExplosionTask.DirectionType>builder(
                        "explosion_direction_type", context.label("explosion_direction_type"),
                        FormFieldInputs.cycle(directionButton, ExplosionTask.DirectionType.FLAT),
                        ItemToWorldEffectConfig::getExplosionDirectionType,
                        ItemToWorldEffectConfig::setExplosionDirectionType)
                .visibleWhen(effectIs(WorldEffectType.EXPLOSION))
                .build());

        CycleButton<AbstractArrow.Pickup> pickupButton = context.enumButton(
                "arrow_pickup_status", List.of(AbstractArrow.Pickup.values()),
                AbstractArrow.Pickup.DISALLOWED,
                pickup -> context.label("arrow_pickup_status." + pickup.name().toLowerCase()));
        builder.add(FormField.<ItemToWorldEffectConfig, AbstractArrow.Pickup>builder(
                        "arrow_pickup_status", context.label("arrow_pickup_status"),
                        FormFieldInputs.cycle(pickupButton, AbstractArrow.Pickup.DISALLOWED),
                        ItemToWorldEffectConfig::getArrowPickupStatus,
                        ItemToWorldEffectConfig::setArrowPickupStatus)
                .visibleWhen(effectIs(WorldEffectType.ARROW_RAIN))
                .build());

        ArrowPotionEffectsWidget potionWidget = new ArrowPotionEffectsWidget(context.font(), 0, 0);
        EditBox effectIdBox = potionWidget.getEffectBox();
        builder.add(FormField.<ItemToWorldEffectConfig, List<com.meteorite.itemdespawntowhat.config.catalogue.PotionEffect>>builder(
                        "arrow_potion_effects", context.label("arrow_potion_effects"),
                        FormFieldInputs.composite(potionWidget, potionWidget::getValue,
                                potionWidget::setValue, potionWidget::clear),
                        ItemToWorldEffectConfig::getRawArrowPotionEffects,
                        ItemToWorldEffectConfig::setArrowPotionEffects)
                .validateWith(value -> effectIdBox.getValue().isBlank()
                        || IdValidator.isValidCommaSeparatedMobEffectId(effectIdBox.getValue())
                        ? null : invalid(context.label("arrow_potion_effects")))
                .suggestWith(effectIdBox, SuggestionProvider.ofRegistry(BuiltInRegistries.MOB_EFFECT), true)
                .visibleWhen(effectIs(WorldEffectType.ARROW_RAIN))
                .build());
    }

    private static <T extends BaseConversionConfig> void addCommonFields(
            FormDefinition.Builder<T> builder,
            FormFieldContext context,
            @Nullable FormField<T> resultField) {
        EditBox itemBox = context.textBox();
        builder.add(FormField.<T, String>builder(
                        "item_id", context.label("item_id"), FormFieldInputs.text(itemBox),
                        config -> nullToEmpty(config.getItemId()), BaseConversionConfig::setItemId)
                .validateWith(value -> IdValidator.isValidItemId(value) ? null : invalid(context.label("item_id")))
                .suggestWith(itemBox, SuggestionProvider.ofRegistryWithTags(BuiltInRegistries.ITEM, Registries.ITEM))
                .build());

        if (resultField != null) {
            builder.add(resultField);
        }

        addPositiveInteger(builder, context, "source_multiple", BaseConversionConfig::getSourceMultiple,
                BaseConversionConfig::setSourceMultiple, 1, ConversionLimits.MAX_SOURCE_MULTIPLE);
        addPositiveInteger(builder, context, "result_multiple", BaseConversionConfig::getResultMultiple,
                BaseConversionConfig::setResultMultiple, 1, ConversionLimits.MAX_RESULT_MULTIPLE);
        addPositiveInteger(builder, context, "conversion_time", BaseConversionConfig::getConversionTime,
                BaseConversionConfig::setConversionTime, 300, Integer.MAX_VALUE);

        EditBox dimensionBox = context.textBox();
        builder.add(FormField.<T, String>builder(
                        "dimension", context.label("dimension"), FormFieldInputs.text(dimensionBox),
                        config -> nullToEmpty(config.getDimension()),
                        (config, value) -> config.setDimension(emptyToNull(value)))
                .suggestWith(dimensionBox, SuggestionProvider.ofDimensions())
                .build());

        CycleButton<Boolean> outdoorButton = context.booleanButton("need_outdoor");
        builder.add(FormField.<T, Boolean>builder(
                        "need_outdoor", context.label("need_outdoor"),
                        FormFieldInputs.cycle(outdoorButton, false),
                        BaseConversionConfig::isNeedOutdoor, BaseConversionConfig::setNeedOutdoor)
                .build());

        addSurroundingBlocks(builder, context);
        addCatalystItems(builder, context);
        addInnerFluid(builder, context);
    }

    private static <T extends BaseConversionConfig> void addSurroundingBlocks(
            FormDefinition.Builder<T> builder, FormFieldContext context) {
        SurroundingBlocksWidget widget = new SurroundingBlocksWidget(context.font(), 0, 0);
        FormField.Builder<T, com.meteorite.itemdespawntowhat.config.catalogue.SurroundingBlocks> field =
                FormField.<T, com.meteorite.itemdespawntowhat.config.catalogue.SurroundingBlocks>builder(
                                "surrounding_blocks", context.label("surrounding_blocks"),
                                FormFieldInputs.composite(widget, widget::getValue, widget::setValue, widget::clear),
                                BaseConversionConfig::getSurroundingBlocks,
                                BaseConversionConfig::setSurroundingBlocks)
                        .validateWith(value -> widget.getBoxes().values().stream()
                                .map(EditBox::getValue)
                                .filter(text -> !text.isBlank())
                                .allMatch(IdValidator::isValidBlockId)
                                ? null : invalid(context.label("surrounding_blocks")));
        widget.getBoxes().values().forEach(box -> field.suggestWith(box,
                SuggestionProvider.ofRegistryWithTags(BuiltInRegistries.BLOCK, Registries.BLOCK)));
        builder.add(field.build());
    }

    private static <T extends BaseConversionConfig> void addCatalystItems(
            FormDefinition.Builder<T> builder, FormFieldContext context) {
        CatalystItemsWidget widget = new CatalystItemsWidget(context.font(), 0, 0);
        EditBox itemBox = widget.getItemBox();
        builder.add(FormField.<T, com.meteorite.itemdespawntowhat.config.catalogue.CatalystItems>builder(
                        "catalyst_items", context.label("catalyst_items"),
                        FormFieldInputs.composite(widget, widget::getValue, widget::setValue, widget::clear),
                        BaseConversionConfig::getCatalystItems, BaseConversionConfig::setCatalystItems)
                .validateWith(value -> itemBox.getValue().isBlank()
                        || IdValidator.isValidCommaSeparatedItemId(itemBox.getValue())
                        ? null : invalid(context.label("catalyst_items")))
                .suggestWith(itemBox,
                        SuggestionProvider.ofRegistryWithTags(BuiltInRegistries.ITEM, Registries.ITEM), true)
                .build());
    }

    private static <T extends BaseConversionConfig> void addInnerFluid(
            FormDefinition.Builder<T> builder, FormFieldContext context) {
        InnerFluidWidget widget = new InnerFluidWidget(context.font(), 0, 0);
        EditBox fluidBox = widget.getFluidBox();
        builder.add(FormField.<T, com.meteorite.itemdespawntowhat.config.catalogue.InnerFluid>builder(
                        "inner_fluid", context.label("inner_fluid"),
                        FormFieldInputs.composite(widget, widget::getValue, widget::setValue, widget::clear),
                        BaseConversionConfig::getInnerFluid, BaseConversionConfig::setInnerFluid)
                .validateWith(value -> fluidBox.getValue().isBlank()
                        || IdValidator.isValidFluidId(fluidBox.getValue())
                        ? null : invalid(context.label("inner_fluid")))
                .suggestWith(fluidBox, SuggestionProvider.ofRegistry(BuiltInRegistries.FLUID,
                        fluid -> !BuiltInRegistries.FLUID.getKey(fluid)
                                .equals(ResourceLocation.withDefaultNamespace("empty"))))
                .build());
    }

    private static <T extends BaseConversionConfig> FormField<T> resultField(
            FormFieldContext context,
            Predicate<String> validator,
            SuggestionProvider suggestionProvider) {
        return resultField(context, validator, suggestionProvider, definition -> true, false);
    }

    private static <T extends BaseConversionConfig> FormField<T> resultField(
            FormFieldContext context,
            Predicate<String> validator,
            SuggestionProvider suggestionProvider,
            Predicate<FormDefinition<T>> visibility,
            boolean clearWhenHidden) {
        EditBox resultBox = context.textBox();
        FormField.Builder<T, String> builder = FormField.<T, String>builder(
                        "result_id", context.label("result_id"), FormFieldInputs.text(resultBox),
                        config -> nullToEmpty(config.getResultId()), BaseConversionConfig::setResultId)
                .validateWith(value -> validator.test(value) ? null : invalid(context.label("result_id")))
                .suggestWith(resultBox, suggestionProvider)
                .visibleWhen(visibility);
        if (clearWhenHidden) {
            builder.clearWhenHidden();
        }
        return builder.build();
    }

    private static <T extends BaseConversionConfig> void addPositiveInteger(
            FormDefinition.Builder<T> builder,
            FormFieldContext context,
            String key,
            java.util.function.ToIntFunction<T> getter,
            java.util.function.ObjIntConsumer<T> setter,
            int defaultValue,
            int maximum) {
        EditBox box = context.positiveIntegerBox();
        builder.add(FormField.<T, String>builder(
                        key, context.label(key), FormFieldInputs.text(box),
                        config -> Integer.toString(getter.applyAsInt(config)),
                        (config, value) -> setter.accept(config, SafeParseUtil.parseInt(value, defaultValue)))
                .validateWith(value -> positiveIntegerError(value, maximum, context.label(key)))
                .build());
    }

    private static void addBoolean(
            FormDefinition.Builder<ItemToWorldEffectConfig> builder,
            FormFieldContext context,
            String key,
            Predicate<ItemToWorldEffectConfig> getter,
            java.util.function.BiConsumer<ItemToWorldEffectConfig, Boolean> setter,
            Predicate<FormDefinition<ItemToWorldEffectConfig>> visibility) {
        CycleButton<Boolean> button = context.booleanButton(key);
        builder.add(FormField.<ItemToWorldEffectConfig, Boolean>builder(
                        key, context.label(key), FormFieldInputs.cycle(button, false),
                        getter::test, setter)
                .visibleWhen(visibility)
                .build());
    }

    private static Predicate<FormDefinition<ItemToWorldEffectConfig>> effectIs(WorldEffectType... types) {
        List<WorldEffectType> accepted = List.of(types);
        return definition -> accepted.contains(definition.<WorldEffectType>value("world_effect_type"));
    }

    private static @Nullable Component positiveIntegerError(String value, int maximum, Component label) {
        int parsed = SafeParseUtil.parseInt(value, -1);
        return parsed >= 1 && parsed <= maximum ? null : invalid(label);
    }

    private static @Nullable Component decimalRangeError(
            String value, float minimum, float maximum, Component label) {
        float parsed = SafeParseUtil.parseFloat(value, Float.NaN);
        return Float.isFinite(parsed) && parsed >= minimum && parsed <= maximum ? null : invalid(label);
    }

    private static Component invalid(Component label) {
        return Component.translatable("gui.itemdespawntowhat.edit.validation.invalid", label);
    }

    private static String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    private static @Nullable String emptyToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
