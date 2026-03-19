package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.config.PotionEffect;
import com.meteorite.itemdespawntowhat.config.SideEffectType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class ItemToSideEffectConfig extends BaseConversionConfig implements SideEffectType.SideEffectConfig {

    @SerializedName("side_effect")
    private SideEffectType sideEffect;

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
    private String arrowPickupStatusString = "DISALLOWED";

    @SerializedName("arrow_potion_effects")
    private List<PotionEffect> arrowPotionEffects = new ArrayList<>();

    public ItemToSideEffectConfig() {
    }

    // ========== 校验 ========== //
    @Override
    protected boolean isResultIdRequired() {
        return false;
    }

    // 用来给GUI校验用
    @Override
    public boolean hasResult() {
        return false;
    }

    @Override
    protected boolean additionalCheck() {
        if (sideEffect == null) {
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

        if (parseArrowPickupStatus() == null) {
            LOGGER.warn("arrow_pickup_status must be one of ALLOWED / DISALLOWED / CREATIVE_ONLY, current={}",
                    arrowPickupStatusString);
            return false;
        }

        return true;
    }

    // ========== 转化逻辑 ========== //
    @Override
    public void performConversion(ItemEntity itemEntity, ServerLevel serverLevel) {
        if (sideEffect == null) return;
        int originalStackSize = itemEntity.getItem().getCount();

        // 天气类：只消耗1个物品（由 executor 内部再判断当前天气条件）
        int actualConvertCount = isWeatherType()
                ? Math.min(1, originalStackSize)
                : computeActualConvertCount(itemEntity, originalStackSize);

        if (actualConvertCount <= 0) {
            LOGGER.warn("No items can be converted for side effect {} (count=0)", sideEffect);
            return;
        }

        // 物品实体下一tick消失
        itemEntity.makeFakeItem();
        // 消耗催化剂与流体
        consumeAllOthers(itemEntity, actualConvertCount);
        // 剩余未转化的物品
        Runnable onFinishCallback = () ->
                addRemainingItems(itemEntity, serverLevel, originalStackSize - actualConvertCount);
        // 执行对应的转化
        sideEffect.getExecutor().execute(itemEntity, serverLevel, this, actualConvertCount, onFinishCallback);
    }

    // ========== 辅助方法 ========== //
    private boolean isWeatherType() {
        return sideEffect == SideEffectType.RAIN || sideEffect == SideEffectType.CLEAR;
    }

    private Arrow.Pickup parseArrowPickupStatus() {
        if (arrowPickupStatusString == null) return Arrow.Pickup.DISALLOWED;
        return switch (arrowPickupStatusString.toUpperCase()) {
            case "ALLOWED" -> Arrow.Pickup.ALLOWED;
            case "DISALLOWED" -> Arrow.Pickup.DISALLOWED;
            case "CREATIVE_ONLY" -> Arrow.Pickup.CREATIVE_ONLY;
            default -> null;
        };
    }
    // ========== GUI图标 ========== //
    @Override
    public String getResultDescriptionId() {
        return sideEffect != null ? sideEffect.getDescriptionId() : "effect.unknown";
    }

    @Override
    public ItemStack getResultIcon() {
        if (sideEffect == null) return new ItemStack(Items.BARRIER);
        return sideEffect.getIconSupplier().get();
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
    public Arrow.Pickup getArrowPickupStatus() {
        Arrow.Pickup parsed = parseArrowPickupStatus();
        return parsed != null ? parsed : Arrow.Pickup.DISALLOWED;
    }

    @Override
    public List<MobEffectInstance> getArrowPotionEffects() {
        if (arrowPotionEffects == null || arrowPotionEffects.isEmpty()) return List.of();

        List<MobEffectInstance> result = new ArrayList<>();
        for (PotionEffect entry : arrowPotionEffects) {
            MobEffectInstance instance = entry.toInstance();
            if (instance != null) result.add(instance);
        }
        return result;
    }

    // ========== getter & setter ========== //

    public String getArrowPickupStatusString() {
        return arrowPickupStatusString;
    }

    public void setArrowPickupStatusString(String arrowPickupStatusString) {
        this.arrowPickupStatusString = arrowPickupStatusString;
    }

    public void setArrowPotionEffects(List<PotionEffect> arrowPotionEffects) {
        this.arrowPotionEffects = arrowPotionEffects;
    }

    public void setExplosionFire(boolean explosionFire) {
        this.explosionFire = explosionFire;
    }

    public void setExplosionPower(float explosionPower) {
        this.explosionPower = explosionPower;
    }

    public SideEffectType getSideEffect() {
        return sideEffect;
    }

    public void setSideEffect(SideEffectType sideEffect) {
        this.sideEffect = sideEffect;
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
