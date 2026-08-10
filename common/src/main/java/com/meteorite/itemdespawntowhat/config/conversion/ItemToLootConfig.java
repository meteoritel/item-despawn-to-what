package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.config.type.BuiltinConversionTypes;

/**
 * 物品通过战利品表生成掉落物的配置数据。
 */
public final class ItemToLootConfig extends BaseItemToEntityConfig {
    @SerializedName("luck")
    private float luck;

    public ItemToLootConfig() {
        super(BuiltinConversionTypes.ITEM_TO_LOOT);
    }

    @Override
    protected boolean validateTypeSpecificFields() {
        if (!Float.isFinite(luck)) {
            LOGGER.warn("luck must be finite, current={}", luck);
            return false;
        }
        return super.validateTypeSpecificFields();
    }

    public float getLuck() {
        return luck;
    }

    public void setLuck(float luck) {
        this.luck = luck;
    }
}
