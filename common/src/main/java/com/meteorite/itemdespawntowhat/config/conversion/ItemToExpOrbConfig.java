package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.config.ConversionLimits;
import com.meteorite.itemdespawntowhat.config.type.BuiltinConversionTypes;

/**
 * 物品到经验值转换的配置数据。
 */
public class ItemToExpOrbConfig extends BaseConversionConfig{
    private static final String XP_ORB_ID = "minecraft:experience_orb";

    // 每个物品转化为几点经验值
    @SerializedName("xp_per_item")
    private int xpPerItem = 1;

    public ItemToExpOrbConfig() {
        super(BuiltinConversionTypes.ITEM_TO_XP_ORB);
        this.resultId = XP_ORB_ID;
    }

    // 允许结果字段为空，因为经验球没有变体
    @Override
    protected boolean isResultIdRequired() {
        return false;
    }

    @Override
    protected boolean additionalCheck() {
        if (xpPerItem <= 0 || xpPerItem > ConversionLimits.MAX_XP_PER_ITEM) {
            LOGGER.warn("xpPerItem should be in range [1, {}], current is {}",
                    ConversionLimits.MAX_XP_PER_ITEM, xpPerItem);
            return false;
        }
        long unitsPerRound = (long) xpPerItem * getResultMultiple();
        if (unitsPerRound > ConversionLimits.MAX_RESULT_UNITS) {
            LOGGER.warn("xp result units per round exceed {}, current={}",
                    ConversionLimits.MAX_RESULT_UNITS, unitsPerRound);
            return false;
        }

        return true;
    }

    public int getXpPerItem() {
        return xpPerItem;
    }

    public void setXpPerItem(int xpPerItem) {
        this.xpPerItem = xpPerItem;
    }
}
