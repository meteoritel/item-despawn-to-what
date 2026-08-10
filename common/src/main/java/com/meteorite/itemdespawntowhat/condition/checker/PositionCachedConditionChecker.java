package com.meteorite.itemdespawntowhat.condition.checker;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 为只依赖物品方块位置的条件提供位置缓存。
 */
public abstract class PositionCachedConditionChecker extends AbstractConditionChecker {
    private final Map<ItemEntity, CachedResult> cachedResults = new WeakHashMap<>();

    @Override
    public final boolean checkCondition(ItemEntity itemEntity, ServerLevel level) {
        BlockPos position = itemEntity.blockPosition();
        CachedResult cached = cachedResults.get(itemEntity);
        if (cached != null && cached.level() == level && cached.position().equals(position)) {
            return cached.passed();
        }

        boolean passed = checkAtPosition(position, level);
        cachedResults.put(itemEntity, new CachedResult(level, position.immutable(), passed));
        return passed;
    }

    protected abstract boolean checkAtPosition(BlockPos position, ServerLevel level);

    private record CachedResult(ServerLevel level, BlockPos position, boolean passed) {
    }
}
