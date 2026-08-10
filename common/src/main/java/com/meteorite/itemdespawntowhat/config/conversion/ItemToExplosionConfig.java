package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.config.ConversionLimits;
import com.meteorite.itemdespawntowhat.config.type.BuiltinConversionTypes;
import com.meteorite.itemdespawntowhat.server.task.ExplosionTask;

/**
 * 物品转化为爆炸的配置数据。
 */
public final class ItemToExplosionConfig extends BaseWorldEffectConfig {
    @SerializedName("explosion_power")
    private float explosionPower = 1.0F;

    @SerializedName("explosion_fire")
    private boolean explosionFire;

    @SerializedName("explosion_direction_type")
    private ExplosionTask.DirectionType explosionDirectionType = ExplosionTask.DirectionType.FLAT;

    public ItemToExplosionConfig() {
        super(BuiltinConversionTypes.ITEM_TO_EXPLOSION);
    }

    @Override
    protected boolean validateTypeSpecificFields() {
        if (!Float.isFinite(explosionPower) || explosionPower < 0
                || explosionPower > ConversionLimits.MAX_EXPLOSION_POWER) {
            LOGGER.warn("explosion_power should be in range [0, {}], current={}",
                    ConversionLimits.MAX_EXPLOSION_POWER, explosionPower);
            return false;
        }
        if (explosionDirectionType == null) {
            LOGGER.warn("explosion_direction_type is required");
            return false;
        }
        return true;
    }

    public float getExplosionPower() {
        return explosionPower;
    }

    public void setExplosionPower(float explosionPower) {
        this.explosionPower = explosionPower;
    }

    public boolean isExplosionFire() {
        return explosionFire;
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
}
