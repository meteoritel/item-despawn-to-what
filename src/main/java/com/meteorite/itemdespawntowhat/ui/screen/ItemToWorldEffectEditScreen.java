package com.meteorite.itemdespawntowhat.ui.screen;

import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.WorldEffectType;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToWorldEffectConfig;
import com.meteorite.itemdespawntowhat.ui.SuggestionProvider;
import com.meteorite.itemdespawntowhat.ui.panel.FormListPanel;
import com.meteorite.itemdespawntowhat.ui.widget.ArrowPotionEffectsWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.projectile.AbstractArrow;

public class ItemToWorldEffectEditScreen extends BaseConfigEditScreen<ItemToWorldEffectConfig> {

    // 枚举切换按钮
    private CycleButton<WorldEffectType> worldEffectButton;
    // 闪电组件
    private CycleButton<Boolean> visualOnlyButton;
    // 天气组件
    private EditBox weatherDurationInput;
    private CycleButton<Boolean> thunderingButton;
    // 爆炸组件
    private EditBox explosionPowerInput;
    private CycleButton<Boolean> explosionFireButton;
    // 箭雨组件
    private CycleButton<AbstractArrow.Pickup> arrowPickupButton;
    private ArrowPotionEffectsWidget arrowPotionEffectsInput;

    public ItemToWorldEffectEditScreen() {
        super(ConfigType.ITEM_TO_WORLD_EFFECT);
    }

    // 隐藏resultId，用不着
    @Override
    protected boolean shouldShowResultId() {
        return false;
    }

    // ========== 字段构建 ========== //
    @Override
    protected void addCustomEntries(FormListPanel formList) {
        // 创建所有子字段组件实例（不立即添加到 formList）
        worldEffectButton = CycleButton.<WorldEffectType>builder(
                        type -> Component.translatable(type.getDescriptionId()))
                .withValues(WorldEffectType.values())
                .withInitialValue(WorldEffectType.RAIN)
                .create(0, 0, BOX_WIDTH, BUTTON_HEIGHT,
                        Component.translatable(LABEL_PREFIX + "world_effect_type"),
                        (btn, value) -> rebuildConditionalEntries());

        visualOnlyButton = CycleButton.booleanBuilder(
                        Component.translatable(LABEL_PREFIX + "on"),
                        Component.translatable(LABEL_PREFIX + "off"))
                .withInitialValue(false)
                .create(0, 0, BOX_WIDTH, BUTTON_HEIGHT,
                        Component.translatable(LABEL_PREFIX + "visual_only"));

        weatherDurationInput = numericBox();

        thunderingButton = CycleButton.booleanBuilder(
                Component.translatable(LABEL_PREFIX + "on"),
                Component.translatable(LABEL_PREFIX + "off"))
                .withInitialValue(false)
                .create(0,0, BOX_WIDTH, BUTTON_HEIGHT,
                        Component.translatable(LABEL_PREFIX + "is_thundering"));

        explosionPowerInput = decimalBox();
        explosionPowerInput.setValue("1.0");

        explosionFireButton = CycleButton.booleanBuilder(
                        Component.translatable(LABEL_PREFIX + "on"),
                        Component.translatable(LABEL_PREFIX + "off"))
                .withInitialValue(false)
                .create(0, 0, BOX_WIDTH, BUTTON_HEIGHT,
                        Component.translatable(LABEL_PREFIX + "explosion_fire"));

        arrowPickupButton = CycleButton.<AbstractArrow.Pickup>builder(
                type -> Component.translatable(LABEL_PREFIX + "arrow_pickup_status." + type.name().toLowerCase()))
                .withValues(AbstractArrow.Pickup.values())
                .withInitialValue(AbstractArrow.Pickup.DISALLOWED)
                .create(0,0,BOX_WIDTH,BUTTON_HEIGHT,
                        Component.translatable(LABEL_PREFIX + "arrow_pickup_status"));

        arrowPotionEffectsInput = new ArrowPotionEffectsWidget(font, 0, 0);

        // 先添加枚举切换按钮，再根据值填充条件行
        formList.add(Component.translatable(LABEL_PREFIX + "world_effect_type"), worldEffectButton);
        rebuildConditionalEntries();
    }

    // 根据当前worldEffectButton 的值，清空并重建 formList 中的条件字段行
    private void rebuildConditionalEntries() {
        formList.removeConditionalEntries();
        WorldEffectType current = worldEffectButton.getValue();
        switch(current) {
            case RAIN -> {
                formList.addConditional(
                        Component.translatable(LABEL_PREFIX + "weather_duration_ticks"), weatherDurationInput);
                formList.addConditional(
                        Component.translatable(LABEL_PREFIX + "is_thundering"), thunderingButton);
            }

            case CLEAR -> formList.addConditional(Component.translatable(
                    LABEL_PREFIX + "weather_duration_ticks"), weatherDurationInput);

            case EXPLOSION -> {
                formList.addConditional(
                        Component.translatable(LABEL_PREFIX + "explosion_power"), explosionPowerInput);
                formList.addConditional(
                        Component.translatable(LABEL_PREFIX + "explosion_fire"), explosionFireButton);
            }

            case LIGHTNING -> formList.addConditional(
                    Component.translatable(LABEL_PREFIX + "visual_only"), visualOnlyButton);

            case ARROW_RAIN -> {
                formList.addConditional(
                        Component.translatable(LABEL_PREFIX + "arrow_pickup_status"), arrowPickupButton);
                formList.addConditional(
                        Component.translatable(LABEL_PREFIX + "arrow_potion_effects"),arrowPotionEffectsInput);
            }
        }
    }

    // ========== 构建配置对象 ========== //
    @Override
    protected ItemToWorldEffectConfig createConfigFromFields() {
        ItemToWorldEffectConfig config = new ItemToWorldEffectConfig();
        populateCommonFields(config);
        populateCustomFields(config);
        return config;
    }

    @Override
    protected void populateCustomFields(ItemToWorldEffectConfig config) {
        WorldEffectType current = worldEffectButton.getValue();
        config.setWorldEffect(current);

        switch (current) {
            case RAIN -> {
                config.setWeatherDurationTicks(parseInt(weatherDurationInput.getValue(), 6000));
                config.setThundering(thunderingButton.getValue());
            }
            case CLEAR ->
                    config.setWeatherDurationTicks(parseInt(weatherDurationInput.getValue(), 6000));
            case LIGHTNING ->
                    config.setVisualOnly(visualOnlyButton.getValue());
            case EXPLOSION -> {
                config.setExplosionPower(parseFloat(explosionPowerInput.getValue(), (float) 1.0));
                config.setExplosionFire(explosionFireButton.getValue());
            }
            case ARROW_RAIN -> {
                config.setArrowPickupStatus(arrowPickupButton.getValue());
                config.setArrowPotionEffects(arrowPotionEffectsInput.getValue());
            }
        }
    }

    @Override
    protected void clearCustomFields() {
        worldEffectButton.setValue(WorldEffectType.RAIN);
        weatherDurationInput.setValue("6000");
        thunderingButton.setValue(false);
        visualOnlyButton.setValue(false);
        explosionPowerInput.setValue("1.0");
        explosionFireButton.setValue(false);
        arrowPickupButton.setValue(AbstractArrow.Pickup.DISALLOWED);
        arrowPotionEffectsInput.clear();
        rebuildConditionalEntries();
    }

    @Override
    protected void refillCustomFields(ItemToWorldEffectConfig config) {
        WorldEffectType type = config.getWorldEffect();
        if (type == null) {
            type = WorldEffectType.RAIN;
        }
        worldEffectButton.setValue(type);

        // 回填所有子字段（无论当前显示哪种，保证数据不丢失）
        weatherDurationInput.setValue(String.valueOf(config.getWeatherDurationTicks()));
        thunderingButton.setValue(config.isThundering());
        visualOnlyButton.setValue(config.isVisualOnly());
        explosionPowerInput.setValue(String.valueOf(config.getExplosionPower()));
        explosionFireButton.setValue(config.isExplosionFire());
        arrowPickupButton.setValue(config.getArrowPickupStatus());
        arrowPotionEffectsInput.setValue(config.getRawArrowPotionEffects());

        // 根据回填的枚举值重建条件行
        rebuildConditionalEntries();
    }

    @Override
    protected void addCustomSuggestion() {
        registerSuggestion(arrowPotionEffectsInput.getEffectBox(),
                SuggestionProvider.ofRegistry(BuiltInRegistries.MOB_EFFECT));
    }
}
