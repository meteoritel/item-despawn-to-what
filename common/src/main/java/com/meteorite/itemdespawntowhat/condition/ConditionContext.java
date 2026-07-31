package com.meteorite.itemdespawntowhat.condition;

import com.meteorite.itemdespawntowhat.config.catalogue.CatalystItems;
import com.meteorite.itemdespawntowhat.config.catalogue.InnerFluid;
import com.meteorite.itemdespawntowhat.config.catalogue.SurroundingBlocks;

/**
 * 记录构建组合条件检查器所需的配置上下文。
 */
public record ConditionContext(
        String dimension,
        boolean needOutdoor,
        SurroundingBlocks surroundingBlocks,
        CatalystItems catalystItems,
        int sourceMultiple,
        InnerFluid innerFluid
) {}
