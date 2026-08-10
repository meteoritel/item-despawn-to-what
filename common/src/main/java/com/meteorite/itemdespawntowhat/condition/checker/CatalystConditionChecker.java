package com.meteorite.itemdespawntowhat.condition.checker;

import com.meteorite.itemdespawntowhat.config.catalogue.CatalystItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import java.util.Map;

/**
 * 校验附近催化剂是否足以支持至少一轮转换。
 */
public final class CatalystConditionChecker implements ConditionChecker {
    private final CatalystItems catalystItems;
    private final int sourceMultiple;

    @Override
    public String debugName() {
        return "catalyst";
    }

    public CatalystConditionChecker(CatalystItems catalystItems, int sourceMultiple) {
        this.catalystItems = catalystItems;
        this.sourceMultiple = Math.max(1, sourceMultiple);
    }

    @Override
    public boolean checkCondition(ItemEntity itemEntity, ServerLevel level) {
        if (catalystItems == null || !catalystItems.hasAnyCatalyst()) {
            return true;
        }

        Map<Item, Integer> snapshot = CatalystItems.collectNearbyItemCounts(itemEntity);
        return catalystItems.checkCondition(snapshot, sourceMultiple);
    }
}
