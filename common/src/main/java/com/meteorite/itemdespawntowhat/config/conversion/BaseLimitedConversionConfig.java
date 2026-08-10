package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.config.ConversionLimits;
import com.meteorite.itemdespawntowhat.config.type.ConversionType;

/**
 * 需要按搜索半径限制邻近结果累积量的转化配置基类。
 */
public abstract class BaseLimitedConversionConfig extends BaseConversionConfig {
    @SerializedName("result_limit")
    protected int resultLimit = 30;

    @SerializedName("search_radius")
    protected int searchRadius = 6;

    protected BaseLimitedConversionConfig(ConversionType type) {
        super(type);
    }

    protected BaseLimitedConversionConfig(ConversionType type, String item, String result) {
        super(type, item, result);
    }

    @Override
    protected boolean validateTypeSpecificFields() {
        if (resultLimit <= 0 || resultLimit > ConversionLimits.MAX_RESULT_UNITS) {
            LOGGER.warn("result_limit should be in range [1, {}], current={}",
                    ConversionLimits.MAX_RESULT_UNITS, resultLimit);
            return false;
        }
        if (searchRadius < 0 || searchRadius > ConversionLimits.MAX_SEARCH_RADIUS) {
            LOGGER.warn("search_radius should be in range [0, {}], current={}",
                    ConversionLimits.MAX_SEARCH_RADIUS, searchRadius);
            return false;
        }
        return true;
    }

    public int getResultLimit() {
        return resultLimit;
    }

    public void setResultLimit(int resultLimit) {
        this.resultLimit = resultLimit;
    }

    public int getSearchRadius() {
        return searchRadius;
    }

    public void setSearchRadius(int searchRadius) {
        this.searchRadius = searchRadius;
    }
}
