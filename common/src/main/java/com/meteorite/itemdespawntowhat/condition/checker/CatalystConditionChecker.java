package com.meteorite.itemdespawntowhat.condition.checker;

import com.meteorite.itemdespawntowhat.condition.ConditionContext;
import com.meteorite.itemdespawntowhat.config.catalogue.CatalystItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import java.util.Map;

/**
 * 校验附近催化剂是否足以支持至少一轮转换。
 */
public class CatalystConditionChecker extends AbstractConditionChecker {
    private CatalystItems catalystItems;
    private int sourceMultiple = 1;

    @Override
    public String debugName() {
        return "catalyst";
    }

    @Override
    public AbstractConditionChecker createChecker(ConditionContext ctx) {
        return new CatalystConditionChecker(ctx.catalystItems(), ctx.sourceMultiple());
    }

    public CatalystConditionChecker() {}
    public CatalystConditionChecker(CatalystItems catalystItems, int sourceMultiple) {
        this.catalystItems = catalystItems;
        this.sourceMultiple = Math.max(1, sourceMultiple);
    }

    @Override
    public boolean shouldApply(ConditionContext ctx) {
        return ctx.catalystItems() != null && ctx.catalystItems().hasAnyCatalyst();
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
