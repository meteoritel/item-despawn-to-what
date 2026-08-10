package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.config.type.BuiltinConversionTypes;

/**
 * 物品转化为闪电的配置数据。
 */
public final class ItemToLightningConfig extends BaseWorldEffectConfig {
    @SerializedName("visual_only")
    private boolean visualOnly;

    public ItemToLightningConfig() {
        super(BuiltinConversionTypes.ITEM_TO_LIGHTNING);
    }

    public boolean isVisualOnly() {
        return visualOnly;
    }

    public void setVisualOnly(boolean visualOnly) {
        this.visualOnly = visualOnly;
    }
}
