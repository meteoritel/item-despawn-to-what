package com.meteorite.itemdespawntowhat.condition.checker;

import com.meteorite.itemdespawntowhat.config.condition.type.BuiltinConditionParameters;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 检查当前维度的天气状态。
 */
public final class WeatherConditionChecker implements ConditionChecker {
    private final BuiltinConditionParameters.WeatherMode weather;

    private WeatherConditionChecker(BuiltinConditionParameters.WeatherMode weather) {
        this.weather = weather;
    }

    public static @Nullable WeatherConditionChecker create(BuiltinConditionParameters.WeatherMode weather) {
        return weather == null || weather == BuiltinConditionParameters.WeatherMode.ANY
                ? null : new WeatherConditionChecker(weather);
    }

    @Override
    public String debugName() {
        return "weather";
    }

    @Override
    public boolean checkCondition(ItemEntity itemEntity, ServerLevel level) {
        return switch (weather) {
            case CLEAR -> !level.isRaining() && !level.isThundering();
            case RAINING -> level.isRaining() && !level.isThundering();
            case THUNDERING -> level.isThundering();
            case ANY -> true;
        };
    }
}
