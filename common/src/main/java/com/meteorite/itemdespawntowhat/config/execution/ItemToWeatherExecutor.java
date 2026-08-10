package com.meteorite.itemdespawntowhat.config.execution;

import com.meteorite.itemdespawntowhat.config.conversion.ItemToWeatherConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * 执行物品到天气变化的转化。
 */
public final class ItemToWeatherExecutor extends AbstractWorldEffectExecutor<ItemToWeatherConfig> {
    @Override
    public boolean performConversion(ItemToWeatherConfig config, ItemEntity entity, ServerLevel level) {
        if (!canExecute(config, level)) {
            return false;
        }
        PreparedEffect prepared = prepare(config, entity, level, 1);
        if (prepared == null) {
            return false;
        }
        if (config.getWeatherMode() == ItemToWeatherConfig.WeatherMode.RAIN) {
            level.setWeatherParameters(0, config.getWeatherDurationTicks(), true, config.isThundering());
        } else {
            level.setWeatherParameters(config.getWeatherDurationTicks(), 0, false, false);
        }
        prepared.onFinish().run();
        return true;
    }

    private boolean canExecute(ItemToWeatherConfig config, ServerLevel level) {
        if (!level.dimensionType().hasSkyLight()) {
            return false;
        }
        return config.getWeatherMode() == ItemToWeatherConfig.WeatherMode.RAIN
                ? !level.isRaining()
                : level.isRaining() || level.isThundering();
    }
}
