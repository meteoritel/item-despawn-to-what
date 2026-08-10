package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.config.ConversionLimits;
import com.meteorite.itemdespawntowhat.config.type.ConversionType;

/**
 * 带结果数量限制字段的实体类转换配置基类。
 */
public abstract class BaseItemToEntityConfig extends BaseConversionConfig{

    @SerializedName("result_limit")
    protected int resultLimit = 30;

    protected BaseItemToEntityConfig(ConversionType type) {
        super(type);
    }

    protected BaseItemToEntityConfig(ConversionType type, String item, String result) {
        super(type, item, result);
    }

    @Override
    protected boolean additionalCheck() {
        if (resultLimit <= 0 || resultLimit > ConversionLimits.MAX_RESULT_LIMIT) {
            LOGGER.warn("resultLimit should be in range [1, {}], current is {}",
                    ConversionLimits.MAX_RESULT_LIMIT, resultLimit);
            return false;
        }
        return true;
    }

    // ========== setter & getter ========== //

    public int getResultLimit() {
        return resultLimit;
    }

    public void setResultLimit(int resultLimit) {
        this.resultLimit = resultLimit;
    }

}
