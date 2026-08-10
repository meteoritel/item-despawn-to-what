package com.meteorite.itemdespawntowhat.client.ui.form;

import com.meteorite.itemdespawntowhat.client.ui.support.SuggestionProvider;
import com.meteorite.itemdespawntowhat.client.ui.widget.ArrowPotionEffectsWidget;
import com.meteorite.itemdespawntowhat.client.ui.widget.ConsumptionDirectiveField;
import com.meteorite.itemdespawntowhat.config.ConversionLimits;
import com.meteorite.itemdespawntowhat.config.conversion.*;
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
        addCommonFields(builder, context, resultField(context,
                value -> IdValidator.isValidResultId(value) || IdValidator.isValidTagId(value),
                SuggestionProvider.ofRegistryWithTags(BuiltInRegistries.ITEM, Registries.ITEM)));
        addPositiveInteger(builder, context, "result_limit", ItemToItemConfig::getResultLimit,
                BaseLimitedConversionConfig::setResultLimit, 30, ConversionLimits.MAX_RESULT_UNITS);
        addNonNegativeInteger(builder, context, "search_radius", ItemToItemConfig::getSearchRadius,
                BaseLimitedConversionConfig::setSearchRadius, 6, ConversionLimits.MAX_SEARCH_RADIUS);
        return builder.build();
    }

    public static FormDefinition<ItemToMobConfig> itemToMob(FormFieldContext context) {
        FormDefinition.Builder<ItemToMobConfig> builder = FormDefinition.builder();
        addCommonFields(builder, context, resultField(context,
                value -> IdValidator.isValidEntityId(value) || IdValidator.isValidTagId(value),
                SuggestionProvider.ofRegistryWithTags(BuiltInRegistries.ENTITY_TYPE, Registries.ENTITY_TYPE)));
        addPositiveInteger(builder, context, "result_limit", ItemToMobConfig::getResultLimit,
                BaseLimitedConversionConfig::setResultLimit, 30, ConversionLimits.MAX_RESULT_UNITS);
        addNonNegativeInteger(builder, context, "search_radius", ItemToMobConfig::getSearchRadius,
                BaseLimitedConversionConfig::setSearchRadius, 6, ConversionLimits.MAX_SEARCH_RADIUS);

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
                ItemToExpOrbConfig::setXpPerItem, 1, ConversionLimits.MAX_XP_PER_ITEM);
        return builder.build();
    }

    public static FormDefinition<ItemToBlockConfig> itemToBlock(FormFieldContext context) {
        FormDefinition.Builder<ItemToBlockConfig> builder = FormDefinition.builder();
        FormField<ItemToBlockConfig> result = resultField(context, IdValidator::isValidBlockId,
                SuggestionProvider.ofRegistryWithTags(BuiltInRegistries.BLOCK, Registries.BLOCK),
                definition -> !definition.<Boolean>value("block_of_item"), true);
        addCommonFields(builder, context, result);
        addPositiveInteger(builder, context, "result_limit", ItemToBlockConfig::getResultLimit,
                BaseLimitedConversionConfig::setResultLimit, 30, ConversionLimits.MAX_RESULT_UNITS);
        addNonNegativeInteger(builder, context, "search_radius", ItemToBlockConfig::getSearchRadius,
                BaseLimitedConversionConfig::setSearchRadius, 6, ConversionLimits.MAX_SEARCH_RADIUS);
        addPositiveInteger(builder, context, "radius_limit", ItemToBlockConfig::getRadius,
                ItemToBlockConfig::setRadius, 6, ConversionLimits.MAX_BLOCK_RADIUS);

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

    public static FormDefinition<ItemToLightningConfig> itemToLightning(FormFieldContext context) {
        FormDefinition.Builder<ItemToLightningConfig> builder = FormDefinition.builder();
        addCommonFields(builder, context, null);
        CycleButton<Boolean> visualOnlyButton = context.booleanButton("visual_only");
        builder.add(FormField.<ItemToLightningConfig, Boolean>builder(
                        "visual_only", context.label("visual_only"),
                        FormFieldInputs.cycle(visualOnlyButton, false),
                        ItemToLightningConfig::isVisualOnly, ItemToLightningConfig::setVisualOnly)
                .build());
        return builder.build();
    }

    public static FormDefinition<ItemToExplosionConfig> itemToExplosion(FormFieldContext context) {
        FormDefinition.Builder<ItemToExplosionConfig> builder = FormDefinition.builder();
        addCommonFields(builder, context, null);
        EditBox explosionPowerBox = context.positiveDecimalBox();
        builder.add(FormField.<ItemToExplosionConfig, String>builder(
                        "explosion_power", context.label("explosion_power"),
                        FormFieldInputs.text(explosionPowerBox),
                        config -> Float.toString(config.getExplosionPower()),
                        (config, value) -> config.setExplosionPower(SafeParseUtil.parseFloat(value, 1.0f)))
                .validateWith(value -> decimalRangeError(value, 0, ConversionLimits.MAX_EXPLOSION_POWER,
                        context.label("explosion_power")))
                .build());
        CycleButton<Boolean> fireButton = context.booleanButton("explosion_fire");
        builder.add(FormField.<ItemToExplosionConfig, Boolean>builder(
                        "explosion_fire", context.label("explosion_fire"),
                        FormFieldInputs.cycle(fireButton, false),
                        ItemToExplosionConfig::isExplosionFire, ItemToExplosionConfig::setExplosionFire)
                .build());

        CycleButton<ExplosionTask.DirectionType> directionButton = context.enumButton(
                "explosion_direction_type", List.of(ExplosionTask.DirectionType.values()),
                ExplosionTask.DirectionType.FLAT,
                direction -> Component.translatable(direction.getDescriptionId()));
        builder.add(FormField.<ItemToExplosionConfig, ExplosionTask.DirectionType>builder(
                        "explosion_direction_type", context.label("explosion_direction_type"),
                        FormFieldInputs.cycle(directionButton, ExplosionTask.DirectionType.FLAT),
                        ItemToExplosionConfig::getExplosionDirectionType,
                        ItemToExplosionConfig::setExplosionDirectionType)
                .build());
        return builder.build();
    }

    public static FormDefinition<ItemToArrowRainConfig> itemToArrowRain(FormFieldContext context) {
        FormDefinition.Builder<ItemToArrowRainConfig> builder = FormDefinition.builder();
        addCommonFields(builder, context, null);
        CycleButton<AbstractArrow.Pickup> pickupButton = context.enumButton(
                "arrow_pickup_status", List.of(AbstractArrow.Pickup.values()),
                AbstractArrow.Pickup.DISALLOWED,
                pickup -> context.label("arrow_pickup_status." + pickup.name().toLowerCase()));
        builder.add(FormField.<ItemToArrowRainConfig, AbstractArrow.Pickup>builder(
                        "arrow_pickup_status", context.label("arrow_pickup_status"),
                        FormFieldInputs.cycle(pickupButton, AbstractArrow.Pickup.DISALLOWED),
                        ItemToArrowRainConfig::getArrowPickupStatus,
                        ItemToArrowRainConfig::setArrowPickupStatus)
                .build());

        ArrowPotionEffectsWidget potionWidget = new ArrowPotionEffectsWidget(context.font(), 0, 0);
        EditBox effectIdBox = potionWidget.getEffectBox();
        builder.add(FormField.<ItemToArrowRainConfig, List<com.meteorite.itemdespawntowhat.config.catalogue.PotionEffect>>builder(
                        "arrow_potion_effects", context.label("arrow_potion_effects"),
                        FormFieldInputs.composite(potionWidget, potionWidget::getValue,
                                potionWidget::setValue, potionWidget::clear),
                        ItemToArrowRainConfig::getRawArrowPotionEffects,
                        ItemToArrowRainConfig::setArrowPotionEffects)
                .validateWith(value -> effectIdBox.getValue().isBlank()
                        || IdValidator.isValidCommaSeparatedMobEffectId(effectIdBox.getValue())
                        ? null : invalid(context.label("arrow_potion_effects")))
                .suggestWith(effectIdBox, SuggestionProvider.ofRegistry(BuiltInRegistries.MOB_EFFECT), true)
                .build());
        return builder.build();
    }

    public static FormDefinition<ItemToWeatherConfig> itemToWeather(FormFieldContext context) {
        FormDefinition.Builder<ItemToWeatherConfig> builder = FormDefinition.builder();
        addCommonFields(builder, context, null);
        CycleButton<ItemToWeatherConfig.WeatherMode> modeButton = context.enumButton(
                "weather_mode", List.of(ItemToWeatherConfig.WeatherMode.values()),
                ItemToWeatherConfig.WeatherMode.RAIN,
                mode -> Component.translatable(mode.getDescriptionId()));
        builder.add(FormField.<ItemToWeatherConfig, ItemToWeatherConfig.WeatherMode>builder(
                        "weather_mode", context.label("weather_mode"),
                        FormFieldInputs.cycle(modeButton, ItemToWeatherConfig.WeatherMode.RAIN),
                        ItemToWeatherConfig::getWeatherMode, ItemToWeatherConfig::setWeatherMode)
                .build());
        addPositiveInteger(builder, context, "weather_duration_ticks",
                ItemToWeatherConfig::getWeatherDurationTicks, ItemToWeatherConfig::setWeatherDurationTicks,
                6000, ConversionLimits.MAX_WEATHER_DURATION_TICKS);
        CycleButton<Boolean> thunderingButton = context.booleanButton("is_thundering");
        builder.add(FormField.<ItemToWeatherConfig, Boolean>builder(
                        "is_thundering", context.label("is_thundering"),
                        FormFieldInputs.cycle(thunderingButton, false),
                        ItemToWeatherConfig::isThundering, ItemToWeatherConfig::setThundering)
                .build());
        return builder.build();
    }

    public static FormDefinition<ItemToLootConfig> itemToLoot(FormFieldContext context) {
        FormDefinition.Builder<ItemToLootConfig> builder = FormDefinition.builder();
        addCommonFields(builder, context, resultField(context, IdValidator::isValidResultId,
                SuggestionProvider.ofLootTables()));
        addPositiveInteger(builder, context, "result_limit", ItemToLootConfig::getResultLimit,
                BaseLimitedConversionConfig::setResultLimit, 30, ConversionLimits.MAX_RESULT_UNITS);
        addNonNegativeInteger(builder, context, "search_radius", ItemToLootConfig::getSearchRadius,
                BaseLimitedConversionConfig::setSearchRadius, 6, ConversionLimits.MAX_SEARCH_RADIUS);
        EditBox luckBox = context.integerBox();
        luckBox.setFilter(value -> value.matches("-?\\d*\\.?\\d*"));
        builder.add(FormField.<ItemToLootConfig, String>builder(
                        "luck", context.label("luck"), FormFieldInputs.text(luckBox),
                        config -> Float.toString(config.getLuck()),
                        (config, value) -> config.setLuck(SafeParseUtil.parseFloat(value, 0)))
                .validateWith(value -> Float.isFinite(SafeParseUtil.parseFloat(value, Float.NaN))
                        ? null : invalid(context.label("luck")))
                .build());
        return builder.build();
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

        ConditionSummaryField conditionField = new ConditionSummaryField();
        builder.add(FormField.<T, com.meteorite.itemdespawntowhat.config.condition.ConditionExpression>builder(
                        "conditions", context.label("conditions"), conditionField,
                        BaseConversionConfig::getConditionExpression,
                        BaseConversionConfig::setConditionExpression)
                .build());

        CycleButton<Boolean> enabledButton = context.booleanButton("enabled");
        builder.add(FormField.<T, Boolean>builder(
                        "enabled", context.label("enabled"), FormFieldInputs.cycle(enabledButton, true),
                        BaseConversionConfig::isEnabled, BaseConversionConfig::setEnabled)
                .build());

        EditBox notesBox = context.textBox();
        builder.add(FormField.<T, String>builder(
                        "notes", context.label("notes"), FormFieldInputs.text(notesBox),
                        config -> nullToEmpty(config.getNotes()), BaseConversionConfig::setNotes)
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

        EditBox priorityBox = context.integerBox();
        builder.add(FormField.<T, String>builder(
                        "priority", context.label("priority"), FormFieldInputs.text(priorityBox),
                        config -> Integer.toString(config.getPriority()),
                        (config, value) -> config.setPriority(SafeParseUtil.parseInt(value, 0)))
                .validateWith(value -> validInteger(value) ? null : invalid(context.label("priority")))
                .build());

        ConsumptionDirectiveField consumptionField = new ConsumptionDirectiveField(context.font());
        builder.add(FormField.<T, com.meteorite.itemdespawntowhat.config.consumption.ConsumptionDirective>builder(
                        "consumption", context.label("consumption"), consumptionField,
                        BaseConversionConfig::getConsumptionDirective,
                        BaseConversionConfig::setConsumptionDirective)
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

    private static <T extends BaseConversionConfig> void addNonNegativeInteger(
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
                .validateWith(value -> {
                    int parsed = SafeParseUtil.parseInt(value, -1);
                    return parsed >= 0 && parsed <= maximum ? null : invalid(context.label(key));
                })
                .build());
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

    private static boolean validInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
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
