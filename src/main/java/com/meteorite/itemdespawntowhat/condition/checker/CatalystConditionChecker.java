package com.meteorite.itemdespawntowhat.condition.checker;

import com.meteorite.itemdespawntowhat.condition.ConditionContext;
import com.meteorite.itemdespawntowhat.config.CatalystItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class CatalystConditionChecker extends AbstractConditionChecker {
    private static final Logger LOGGER = LogManager.getLogger();
    public static final String KEY = "catalyst";
    private CatalystItems catalystItems;

    public CatalystConditionChecker() {}
    public CatalystConditionChecker(CatalystItems catalystItems) {
        this.catalystItems = catalystItems;
    }

    @Override
    public String getConditionKey() {
        return KEY;
    }

    @Override
    public boolean shouldApply(ConditionContext ctx) {
        return ctx.catalystItems() != null && ctx.catalystItems().hasAnyCatalyst();
    }

    @Override
    public void applyCondition(Map<String, String> conditions, ConditionContext ctx) {
        ctx.catalystItems().toConditionMap(conditions, getConditionKey());
    }

    @Override
    public AbstractConditionChecker parse(Map<String, String> conditions) {
        try {
            CatalystItems parsed = new CatalystItems().fromConditionMap(conditions, getConditionKey());
            return parsed != null ? new CatalystConditionChecker(parsed) : null;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse CatalystItems from condition map: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean checkCondition(ItemEntity itemEntity, ServerLevel level) {
        if (catalystItems == null || !catalystItems.hasAnyCatalyst()) {
            return true;
        }

        Map<Item, Integer> snapshot = CatalystItems.collectNearbyItemCounts(itemEntity);
        return catalystItems.checkCondition(snapshot);
    }
}
