package com.meteorite.itemdespawntowhat.config.catalogue;

import com.google.gson.annotations.SerializedName;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PotionEffect {
    private static final Logger LOGGER = LogManager.getLogger();

    // 药水效果的注册表 ID，例如 "minecraft:poison"
    @SerializedName("effect")
    private String effectId;

    // 持续时间，默认 100t = 5s
    @SerializedName("duration")
    private int duration = 100;

    // 效果等级（0 = I 级），默认 0
    @SerializedName("amplifier")
    private int amplifier = 0;

    public PotionEffect() {}

    public PotionEffect(String effectId, int duration, int amplifier) {
        this.effectId = effectId;
        this.duration = duration;
        this.amplifier = amplifier;
    }

    public MobEffectInstance toInstance() {
        if (effectId == null || effectId.isBlank()) {
            LOGGER.warn("PotionEffectEntry: effect id is null or blank, skipping");
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(effectId);
        if (id == null || id.getPath().isEmpty()) {
            LOGGER.warn("PotionEffectEntry: invalid effect id '{}', skipping", effectId);
            return null;
        }
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);
        if (effect == null) {
            LOGGER.warn("PotionEffectEntry: effect '{}' not found in registry, skipping", effectId);
            return null;
        }
        return new MobEffectInstance(
                BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect),
                duration,
                amplifier
        );
    }

    public boolean hasPotionEffect() {
        return effectId != null && !effectId.isBlank();
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
