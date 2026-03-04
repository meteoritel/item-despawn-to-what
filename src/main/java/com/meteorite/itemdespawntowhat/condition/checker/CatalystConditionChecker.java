package com.meteorite.itemdespawntowhat.condition.checker;

import com.meteorite.itemdespawntowhat.condition.ConditionContext;
import com.meteorite.itemdespawntowhat.config.CatalystItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
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
        return ctx.catalystItems() != null;
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

        Map<Item, Integer> nearbyItemCounts = collectNearbyItems(itemEntity, level);
        List<CatalystItems.CatalystEntry> entries = catalystItems.getCatalystList();

        return hasAtLeastOneCompleteSet(entries, nearbyItemCounts);
    }

    // 检查周围是否存在至少一套完整的催化剂（每种催化剂至少有entry.count数量）
    private boolean hasAtLeastOneCompleteSet(
            List<CatalystItems.CatalystEntry> entries,
            Map<Item, Integer> nearbyCounts) {

        for (CatalystItems.CatalystEntry entry : entries) {
            Item requiredItem = BuiltInRegistries.ITEM.get(entry.getItemId());
            int required = entry.getCount();
            int available = nearbyCounts.getOrDefault(requiredItem, 0);

            if (available < required) {
                LOGGER.debug("Catalyst check failed: {} needs {} but has {}",
                        entry.getItemId(), required, available);
                return false;
            }
        }
        return true;
    }


    // 统计起始物品所在一格的物品掉落物
    private Map<Item, Integer> collectNearbyItems(ItemEntity startEntity, ServerLevel level) {
        Map<Item, Integer> counts = new HashMap<>();
        BlockPos blockPos = startEntity.blockPosition();
        AABB searchBox = AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(blockPos));

        List<ItemEntity> entitiesInBlock = level.getEntitiesOfClass(ItemEntity.class, searchBox,
                entity -> entity != startEntity && !entity.isRemoved());

        for (ItemEntity entity : entitiesInBlock) {
            ItemStack stack = entity.getItem();
            if (!stack.isEmpty()) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    public CatalystItems getCatalystItems() {
        return catalystItems;
    }
}
