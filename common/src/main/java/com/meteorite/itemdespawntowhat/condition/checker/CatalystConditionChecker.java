package com.meteorite.itemdespawntowhat.condition.checker;

import com.meteorite.itemdespawntowhat.condition.ConditionContext;
import com.meteorite.itemdespawntowhat.config.catalogue.CatalystItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

/**
 * 校验附近催化剂是否足以支持至少一轮转换。
 */
public class CatalystConditionChecker extends AbstractConditionChecker {
    private static final Logger LOGGER = LogManager.getLogger();
    public static final String KEY = "catalyst";
    private static final String SOURCE_MULTIPLE_KEY = "catalyst_source_multiple";
    private CatalystItems catalystItems;
    private int sourceMultiple = 1;

    public CatalystConditionChecker() {}
    public CatalystConditionChecker(CatalystItems catalystItems, int sourceMultiple) {
        this.catalystItems = catalystItems;
        this.sourceMultiple = Math.max(1, sourceMultiple);
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
        conditions.put(SOURCE_MULTIPLE_KEY, Integer.toString(Math.max(1, ctx.sourceMultiple())));
    }

    @Override
    public AbstractConditionChecker parse(Map<String, String> conditions) {
        try {
            CatalystItems parsed = new CatalystItems().fromConditionMap(conditions, getConditionKey());
            int parsedSourceMultiple = Integer.parseInt(conditions.getOrDefault(SOURCE_MULTIPLE_KEY, "1"));
            return parsed != null ? new CatalystConditionChecker(parsed, parsedSourceMultiple) : null;
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
        return catalystItems.checkCondition(snapshot, sourceMultiple);
    }
}
