package com.meteorite.itemdespawntowhat.condition.checker;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * 运行时条件检查接口。
 */
public interface ConditionChecker {
    default String debugName() {
        return getClass().getSimpleName();
    }

    boolean checkCondition(ItemEntity itemEntity, ServerLevel level);
}
