package com.meteorite.itemdespawntowhat.condition;

import com.meteorite.itemdespawntowhat.config.catalogue.CatalystItems;
import com.meteorite.itemdespawntowhat.config.catalogue.InnerFluid;
import com.meteorite.itemdespawntowhat.config.catalogue.SurroundingBlocks;

// 记录所有需要解析的条件
public record ConditionContext(
        String dimension,
        boolean needOutdoor,
        SurroundingBlocks surroundingBlocks,
        CatalystItems catalystItems,
        InnerFluid innerFluid
) {}
