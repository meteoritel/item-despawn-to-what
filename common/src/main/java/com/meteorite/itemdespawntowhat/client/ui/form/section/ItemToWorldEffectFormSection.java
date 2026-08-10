package com.meteorite.itemdespawntowhat.client.ui.form.section;

import com.meteorite.itemdespawntowhat.client.ui.form.ConfigFormContext;
import com.meteorite.itemdespawntowhat.client.ui.form.ConfigFormSection;
import com.meteorite.itemdespawntowhat.client.ui.support.SuggestionProvider;
import com.meteorite.itemdespawntowhat.client.ui.widget.ArrowPotionEffectsWidget;
import com.meteorite.itemdespawntowhat.config.WorldEffectType;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToWorldEffectConfig;
import com.meteorite.itemdespawntowhat.server.task.ExplosionTask;
import com.meteorite.itemdespawntowhat.util.IdValidator;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.projectile.AbstractArrow;

/**
 * 管理世界效果配置的动态表单字段。
 */
public final class ItemToWorldEffectFormSection implements ConfigFormSection<ItemToWorldEffectConfig> {
    private ConfigFormContext context;
    private CycleButton<WorldEffectType> worldEffectButton;
    private CycleButton<Boolean> visualOnlyButton;
    private EditBox weatherDurationInput;
    private CycleButton<Boolean> thunderingButton;
    private EditBox explosionPowerInput;
    private CycleButton<Boolean> explosionFireButton;
    private CycleButton<ExplosionTask.DirectionType> explosionDirectionButton;
    private CycleButton<AbstractArrow.Pickup> arrowPickupButton;
    private ArrowPotionEffectsWidget arrowPotionEffectsInput;

    @Override
    public void initialize(ConfigFormContext context) {
        this.context = context;
        worldEffectButton = CycleButton.<WorldEffectType>builder(
                        type -> Component.translatable(type.getDescriptionId()))
                .withValues(WorldEffectType.values())
                .withInitialValue(WorldEffectType.RAIN)
                .create(0, 0, context.boxWidth(), context.buttonHeight(), context.label("world_effect_type"),
                        (button, value) -> rebuildConditionalEntries());
        visualOnlyButton = booleanButton("visual_only");
        weatherDurationInput = context.positiveIntBox();
        thunderingButton = booleanButton("is_thundering");
        explosionPowerInput = context.positiveDecimalBox();
        explosionFireButton = booleanButton("explosion_fire");
        explosionDirectionButton = CycleButton.<ExplosionTask.DirectionType>builder(
                        type -> Component.translatable(type.getDescriptionId()))
                .withValues(ExplosionTask.DirectionType.values())
                .withInitialValue(ExplosionTask.DirectionType.FLAT)
                .create(0, 0, context.boxWidth(), context.buttonHeight(), context.label("explosion_direction_type"));
        arrowPickupButton = CycleButton.<AbstractArrow.Pickup>builder(
                        type -> context.label("arrow_pickup_status." + type.name().toLowerCase()))
                .withValues(AbstractArrow.Pickup.values())
                .withInitialValue(AbstractArrow.Pickup.DISALLOWED)
                .create(0, 0, context.boxWidth(), context.buttonHeight(), context.label("arrow_pickup_status"));
        arrowPotionEffectsInput = new ArrowPotionEffectsWidget(context.font(), 0, 0);

        context.add("world_effect_type", worldEffectButton);
        rebuildConditionalEntries();
    }

    @Override
    public void writeTo(ItemToWorldEffectConfig config) {
        WorldEffectType type = worldEffectButton.getValue();
        config.setWorldEffect(type);
        switch (type) {
            case RAIN -> {
                config.setWeatherDurationTicks(context.parseInt(weatherDurationInput.getValue(), 6000));
                config.setThundering(thunderingButton.getValue());
            }
            case CLEAR -> config.setWeatherDurationTicks(context.parseInt(weatherDurationInput.getValue(), 6000));
            case LIGHTNING -> config.setVisualOnly(visualOnlyButton.getValue());
            case EXPLOSION -> {
                config.setExplosionPower(context.parseFloat(explosionPowerInput.getValue()));
                config.setExplosionFire(explosionFireButton.getValue());
                config.setExplosionDirectionType(explosionDirectionButton.getValue());
            }
            case ARROW_RAIN -> {
                config.setArrowPickupStatus(arrowPickupButton.getValue());
                config.setArrowPotionEffects(arrowPotionEffectsInput.getValue());
            }
        }
    }

    @Override
    public void readFrom(ItemToWorldEffectConfig config) {
        worldEffectButton.setValue(config.getWorldEffect() == null ? WorldEffectType.RAIN : config.getWorldEffect());
        weatherDurationInput.setValue(String.valueOf(config.getWeatherDurationTicks()));
        thunderingButton.setValue(config.isThundering());
        visualOnlyButton.setValue(config.isVisualOnly());
        explosionPowerInput.setValue(String.valueOf(config.getExplosionPower()));
        explosionFireButton.setValue(config.isExplosionFire());
        explosionDirectionButton.setValue(config.getExplosionDirectionType());
        arrowPickupButton.setValue(config.getArrowPickupStatus());
        arrowPotionEffectsInput.setValue(config.getRawArrowPotionEffects());
        rebuildConditionalEntries();
    }

    @Override
    public void clear() {
        worldEffectButton.setValue(WorldEffectType.RAIN);
        weatherDurationInput.setValue("6000");
        thunderingButton.setValue(false);
        visualOnlyButton.setValue(false);
        explosionPowerInput.setValue("1.0");
        explosionFireButton.setValue(false);
        explosionDirectionButton.setValue(ExplosionTask.DirectionType.FLAT);
        arrowPickupButton.setValue(AbstractArrow.Pickup.DISALLOWED);
        arrowPotionEffectsInput.clear();
        rebuildConditionalEntries();
    }

    @Override
    public boolean showsResultId() {
        return false;
    }

    @Override
    public void registerValidators(ConfigFormContext context) {
        EditBox effectBox = arrowPotionEffectsInput.getEffectBox();
        context.registerValidator(effectBox,
                () -> !effectBox.getValue().isBlank(), IdValidator::isValidCommaSeparatedMobEffectId);
    }

    @Override
    public void registerSuggestions(ConfigFormContext context) {
        context.registerCommaSeparatedSuggestion(
                arrowPotionEffectsInput.getEffectBox(), SuggestionProvider.ofRegistry(BuiltInRegistries.MOB_EFFECT));
    }

    @Override
    public void registerFocus(ConfigFormContext context) {
        arrowPotionEffectsInput.setFocusDelegate(context::focus);
    }

    private CycleButton<Boolean> booleanButton(String labelSuffix) {
        return CycleButton.booleanBuilder(context.label("on"), context.label("off"))
                .withInitialValue(false)
                .create(0, 0, context.boxWidth(), context.buttonHeight(), context.label(labelSuffix));
    }

    private void rebuildConditionalEntries() {
        context.rebuildConditional(() -> {
            switch (worldEffectButton.getValue()) {
                case RAIN -> {
                    context.addConditional("weather_duration_ticks", weatherDurationInput);
                    context.addConditional("is_thundering", thunderingButton);
                }
                case CLEAR -> context.addConditional("weather_duration_ticks", weatherDurationInput);
                case EXPLOSION -> {
                    context.addConditional("explosion_power", explosionPowerInput);
                    context.addConditional("explosion_fire", explosionFireButton);
                    context.addConditional("explosion_direction_type", explosionDirectionButton);
                }
                case LIGHTNING -> context.addConditional("visual_only", visualOnlyButton);
                case ARROW_RAIN -> {
                    context.addConditional("arrow_pickup_status", arrowPickupButton);
                    context.addConditional("arrow_potion_effects", arrowPotionEffectsInput);
                }
            }
        });
    }
}
