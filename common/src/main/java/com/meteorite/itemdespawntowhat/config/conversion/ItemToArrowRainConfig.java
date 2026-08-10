package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.config.catalogue.PotionEffect;
import com.meteorite.itemdespawntowhat.config.type.BuiltinConversionTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品转化为箭雨的配置数据。
 */
public final class ItemToArrowRainConfig extends BaseWorldEffectConfig {
    @SerializedName("arrow_pickup_status")
    private AbstractArrow.Pickup arrowPickupStatus = AbstractArrow.Pickup.DISALLOWED;

    @SerializedName("arrow_potion_effects")
    private @Nullable List<PotionEffect> arrowPotionEffects = new ArrayList<>();

    public ItemToArrowRainConfig() {
        super(BuiltinConversionTypes.ITEM_TO_ARROW_RAIN);
    }

    @Override
    protected boolean additionalCheck() {
        if (arrowPickupStatus == null) {
            LOGGER.warn("arrow_pickup_status must be a valid pickup mode");
            return false;
        }
        return true;
    }

    public AbstractArrow.Pickup getArrowPickupStatus() {
        return arrowPickupStatus;
    }

    public void setArrowPickupStatus(AbstractArrow.Pickup arrowPickupStatus) {
        this.arrowPickupStatus = arrowPickupStatus;
    }

    public List<PotionEffect> getRawArrowPotionEffects() {
        return arrowPotionEffects == null ? List.of() : arrowPotionEffects;
    }

    public void setArrowPotionEffects(@Nullable List<PotionEffect> arrowPotionEffects) {
        this.arrowPotionEffects = arrowPotionEffects;
    }

    public List<MobEffectInstance> getArrowPotionEffects() {
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
        return List.copyOf(result);
    }
}
