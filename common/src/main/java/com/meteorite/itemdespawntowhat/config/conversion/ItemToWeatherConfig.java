package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.config.ConversionLimits;
import com.meteorite.itemdespawntowhat.config.type.BuiltinConversionTypes;

/**
 * 物品转化为天气变化的配置数据。
 */
public final class ItemToWeatherConfig extends BaseWorldEffectConfig {
    @SerializedName("weather_mode")
    private WeatherMode weatherMode = WeatherMode.RAIN;

    @SerializedName("weather_duration_ticks")
    private int weatherDurationTicks = 6000;

    @SerializedName("is_thundering")
    private boolean thundering;

    public ItemToWeatherConfig() {
        super(BuiltinConversionTypes.ITEM_TO_WEATHER);
    }

    @Override
    protected boolean validateTypeSpecificFields() {
        if (weatherMode == null) {
            LOGGER.warn("weather_mode is required");
            return false;
        }
        if (weatherDurationTicks <= 0
                || weatherDurationTicks > ConversionLimits.MAX_WEATHER_DURATION_TICKS) {
            LOGGER.warn("weather_duration_ticks should be in range [1, {}], current={}",
                    ConversionLimits.MAX_WEATHER_DURATION_TICKS, weatherDurationTicks);
            return false;
        }
        return true;
    }

    public WeatherMode getWeatherMode() {
        return weatherMode;
    }

    public void setWeatherMode(WeatherMode weatherMode) {
        this.weatherMode = weatherMode;
    }

    public int getWeatherDurationTicks() {
        return weatherDurationTicks;
    }

    public void setWeatherDurationTicks(int weatherDurationTicks) {
        this.weatherDurationTicks = weatherDurationTicks;
    }

    public boolean isThundering() {
        return thundering;
    }

    public void setThundering(boolean thundering) {
        this.thundering = thundering;
    }

    /** 天气变化目标。 */
    public enum WeatherMode {
        RAIN("effect.itemdespawntowhat.weather.rain"),
        CLEAR("effect.itemdespawntowhat.weather.clear");

        private final String descriptionId;

        WeatherMode(String descriptionId) {
            this.descriptionId = descriptionId;
        }

        public String getDescriptionId() {
            return descriptionId;
        }
    }
}
