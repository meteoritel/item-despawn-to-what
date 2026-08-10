package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.config.ConversionLimits;
import com.meteorite.itemdespawntowhat.config.type.BuiltinConversionTypes;
import com.meteorite.itemdespawntowhat.config.catalogue.PotionEffect;
import com.meteorite.itemdespawntowhat.config.WorldEffectType;
import com.meteorite.itemdespawntowhat.server.task.ExplosionTask;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品到天气、闪电、爆炸或箭雨效果的配置数据。
 */
public class ItemToWorldEffectConfig extends BaseConversionConfig implements WorldEffectType.SideEffectConfig {

    @SerializedName("side_effect")
    private WorldEffectType worldEffect;

    // ========== 现象参数 ========== //
    // 闪电字段
    @SerializedName("visual_only")
    private boolean visualOnly = false;

    // 天气参数
    @SerializedName("weather_duration_ticks")
    private int weatherDurationTicks = 6000;

    @SerializedName("is_thundering")
    private boolean thundering = false;

    // 爆炸参数
    @SerializedName("explosion_power")
    private float explosionPower = 1f;

    @SerializedName("explosion_fire")
    private boolean explosionFire = false;

    @SerializedName("explosion_direction_type")
    private ExplosionTask.DirectionType explosionDirectionType = ExplosionTask.DirectionType.FLAT;

    // 箭雨参数
    @SerializedName("arrow_pickup_status")
    private AbstractArrow.Pickup arrowPickupStatus = AbstractArrow.Pickup.DISALLOWED;

    @SerializedName("arrow_potion_effects")
    private @Nullable List<PotionEffect> arrowPotionEffects = new ArrayList<>();

    public ItemToWorldEffectConfig() {
        super(BuiltinConversionTypes.ITEM_TO_WORLD_EFFECT);
    }

    // ========== 校验 ========== //
    @Override
    protected boolean isResultIdRequired() {
        return false;
    }

    @Override
    protected boolean additionalCheck() {
        if (worldEffect == null) {
            LOGGER.warn("side_effect field is required for ItemToSideEffectConfig, item={}", itemId);
            return false;
        }
        if (!Float.isFinite(explosionPower)
                || explosionPower < 0
                || explosionPower > ConversionLimits.MAX_EXPLOSION_POWER) {
            LOGGER.warn("explosion_power should be in range [0, {}], current={}",
                    ConversionLimits.MAX_EXPLOSION_POWER, explosionPower);
            return false;
        }
        if (weatherDurationTicks <= 0
                || weatherDurationTicks > ConversionLimits.MAX_WEATHER_DURATION_TICKS) {
            LOGGER.warn("weather_duration_ticks should be in range [1, {}], current={}",
                    ConversionLimits.MAX_WEATHER_DURATION_TICKS, weatherDurationTicks);
            return false;
        }

        if (arrowPickupStatus == null) {
            LOGGER.warn("arrow_pickup_status must be one of ALLOWED / DISALLOWED / CREATIVE_ONLY");
            return false;
        }
        if (explosionDirectionType == null) {
            LOGGER.warn("explosion_direction_type is required for ItemToWorldEffectConfig");
            return false;
        }

        return true;
    }

    // ========== 接口实现 ========== //
    @Override
    public float getExplosionPower() {
        return explosionPower;
    }

    @Override
    public int getWeatherDurationTicks() {
        return weatherDurationTicks;
    }

    @Override
    public boolean isExplosionFire() {
        return explosionFire;
    }

    @Override
    public boolean isThundering() {
        return thundering;
    }

    @Override
    public boolean isVisualOnly() {
        return visualOnly;
    }

    @Override
    public AbstractArrow.Pickup getArrowPickupStatus() {
        return arrowPickupStatus;
    }

    @Override
    public @Nullable List<MobEffectInstance> getArrowPotionEffects() {
        if (arrowPotionEffects == null || arrowPotionEffects.isEmpty()) {
            return List.of();
        }

        List<MobEffectInstance> result = new ArrayList<>();
        for (PotionEffect entry : arrowPotionEffects) {
            MobEffectInstance instance = entry.toInstance();
            if (instance != null) {
                result.add(instance);
            }
        }
        return result;
    }

    // ========== 间隔时间 (from Constants, injected by platform at startup) ========== //
    @Override
    public int getLightningIntervalTicks() {
        return Constants.lightningIntervalTicks;
    }

    @Override
    public int getExplosionIntervalTicks() {
        return Constants.explosionIntervalTicks;
    }

    @Override
    public int getArrowIntervalTicks() {
        return Constants.arrowIntervalTicks;
    }

    // ========== getter & setter ========== //
    public void setArrowPickupStatus(AbstractArrow.Pickup arrowPickupStatus) {
        this.arrowPickupStatus = arrowPickupStatus;
    }

    public List<PotionEffect> getRawArrowPotionEffects() {
        return arrowPotionEffects;
    }

    public void setArrowPotionEffects(@Nullable List<PotionEffect> arrowPotionEffects) {
        this.arrowPotionEffects = arrowPotionEffects;
    }

    public void setExplosionFire(boolean explosionFire) {
        this.explosionFire = explosionFire;
    }

    public ExplosionTask.DirectionType getExplosionDirectionType() {
        return explosionDirectionType;
    }

    public void setExplosionDirectionType(ExplosionTask.DirectionType explosionDirectionType) {
        this.explosionDirectionType = explosionDirectionType;
    }

    public void setExplosionPower(float explosionPower) {
        this.explosionPower = explosionPower;
    }

    public WorldEffectType getWorldEffect() {
        return worldEffect;
    }

    public void setWorldEffect(WorldEffectType worldEffect) {
        this.worldEffect = worldEffect;
    }

    public void setThundering(boolean thundering) {
        this.thundering = thundering;
    }

    public void setVisualOnly(boolean visualOnly) {
        this.visualOnly = visualOnly;
    }

    public void setWeatherDurationTicks(int weatherDurationTicks) {
        this.weatherDurationTicks = weatherDurationTicks;
    }
}
