package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.config.catalogue.PotionEffect;
import com.meteorite.itemdespawntowhat.config.WorldEffectType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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

    // 箭雨参数
    @SerializedName("arrow_pickup_status")
    private AbstractArrow.Pickup arrowPickupStatus = AbstractArrow.Pickup.DISALLOWED;

    @SerializedName("arrow_potion_effects")
    private @Nullable List<PotionEffect> arrowPotionEffects;

    public ItemToWorldEffectConfig() {
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
        if (explosionPower < 0) {
            LOGGER.warn("explosion_power must be >= 0, current={}", explosionPower);
            return false;
        }
        if (weatherDurationTicks <= 0) {
            LOGGER.warn("weather_duration_ticks must be > 0, current={}", weatherDurationTicks);
            return false;
        }

        if (arrowPickupStatus == null) {
            LOGGER.warn("arrow_pickup_status must be one of ALLOWED / DISALLOWED / CREATIVE_ONLY");
            return false;
        }

        return true;
    }

    // ========== 转化逻辑 ========== //
    @Override
    public void performConversion(ItemEntity itemEntity, ServerLevel serverLevel) {
        if (worldEffect == null) return;
        int originalStackSize = itemEntity.getItem().getCount();

        int rounds;
        int actualConvertCount;
        if (isWeatherType()) {
            // 天气类固定1轮，需要 sourceMultiple 个物品才能触发
            if (originalStackSize < getSourceMultiple()) {
                return;
            }
            rounds = 1;
            actualConvertCount = getSourceMultiple();
        } else {
            rounds = computeActualRounds(itemEntity, originalStackSize);
            if (rounds <= 0) {
                LOGGER.warn("No items can be converted for side effect {} (count=0)", worldEffect);
                return;
            }
            actualConvertCount = rounds * getSourceMultiple();
        }

        // 物品实体下一tick消失
        itemEntity.makeFakeItem();
        // 消耗催化剂与流体
        consumeAllOthers(itemEntity, actualConvertCount);
        // 剩余未转化的物品
        Runnable onFinishCallback = () ->
                addRemainingItems(itemEntity, serverLevel, originalStackSize - actualConvertCount);
        // 执行对应的转化
        worldEffect.getExecutor().execute(itemEntity, serverLevel, this, rounds, onFinishCallback);
    }

    // ========== 辅助方法 ========== //
    private boolean isWeatherType() {
        return worldEffect == WorldEffectType.RAIN || worldEffect == WorldEffectType.CLEAR;
    }

    // ========== GUI图标 ========== //
    @Override
    public String getResultDescriptionId() {
        return worldEffect != null ? worldEffect.getDescriptionId() : "effect.unknown";
    }

    @Override
    public ItemStack getResultIcon() {
        if (worldEffect == null) return new ItemStack(Items.BARRIER);
        return worldEffect.getIconSupplier().get();
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
