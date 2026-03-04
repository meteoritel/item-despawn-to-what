package com.meteorite.itemdespawntowhat.condition;

import com.meteorite.itemdespawntowhat.config.CatalystItems;
import com.meteorite.itemdespawntowhat.config.InnerFluid;
import com.meteorite.itemdespawntowhat.config.SurroundingBlocks;

// 记录所有需要解析的条件
public record ConditionContext(
        String dimension,
        boolean needOutdoor,
        SurroundingBlocks surroundingBlocks,
        CatalystItems catalystItems,
        InnerFluid innerFluid
) {}
