package com.meteorite.itemdespawntowhat.config.catalogue;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.util.IdValidator;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 描述箭矢携带的一项药水效果。
 */
public class PotionEffect {
    private static final Logger LOGGER = LogManager.getLogger();

    // 药水效果的注册表 ID，例如 "minecraft:poison"
    @SerializedName("effect")
    private String effectId;

    // 持续时间，默认 100t = 5s
    @SerializedName("duration")
    private int duration;

    // 效果等级（0 = I 级），默认 0
    @SerializedName("amplifier")
    private int amplifier;

    public PotionEffect(String effectId, int duration, int amplifier) {
        this.effectId = effectId;
        this.duration = duration;
        this.amplifier = amplifier;
    }

    public MobEffectInstance toInstance() {
        if (!isValid()) {
            LOGGER.warn("Invalid potion effect entry: effect={}, duration={}, amplifier={}",
                    effectId, duration, amplifier);
            return null;
        }
        ResourceLocation id = SafeParseUtil.parseResourceLocation(effectId);
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);
        return new MobEffectInstance(
                BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect),
                duration,
                amplifier
        );
    }

    public boolean hasPotionEffect() {
        return effectId != null && !effectId.isBlank();
    }

    public boolean isValid() {
        if (!IdValidator.isValidMobEffectId(effectId) || duration <= 0 || amplifier < 0) {
            return false;
        }
        return true;
    }

    public int getAmplifier() {
        return amplifier;
    }
    public int getDuration() {
        return duration;
    }
    public String getEffectId() {
        return effectId;
    }
}
